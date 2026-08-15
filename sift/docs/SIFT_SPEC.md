# Sift — Production Specification v3

**Platform:** Android (Samsung), sideload, single user
**Posture:** 100% on-device. No accounts, no network, no cost.
**Quality bar:** Every processed frame treated on its own merits, to a standard that survives 100% inspection.
**Status:** Ready to build

---

## 0. How to read this document

This spec is written for implementation by Claude Code. Sections 2 and 6 are the ones that determine whether the output looks professional or looks like a filter. Everything else is scaffolding around them.

Three rules govern the whole document:

1. **No parameter in the imaging pipeline is a constant.** Every value is derived from measurements of the specific frame being processed. Constants exist only as *targets* and *bounds*. If you find yourself writing a magic number that applies to all images, you've made an error.
2. **Never overwrite a source file.** Every operation produces a new file.
3. **When a quality gate fails, ship the original.** A degraded photo is worse than an unprocessed one.

---

## 1. Product definition

A swipe-based triage deck over the camera roll that batches deletions into a single confirmation, then puts keepers through a per-image adaptive grading and upscaling pipeline, exporting to a watched folder.

**Done when:** a day's shooting — 200 photos — goes from camera roll to reviewed, approved exports in under five minutes of attention, in airplane mode, with output that holds up at 100% zoom.

**Originals policy (decided).** Every keeper is auto-graded. The original is retained until you explicitly approve the graded result; only then is it trashed. This makes unattended batch grading safe and makes every deletion reversible up to the moment of confirmation. See §9.

**Cloud note.** Google Photos has no delete API (the April 2025 scope removal locked the Library API to app-created content), so Sift cannot touch cloud copies. Local trash frees device storage; cloud copies persist. Graded exports reach the cloud for free via Google Photos → Settings → Backup → Back up device folders → `Pictures/Sift`. Sift therefore ships with **no `INTERNET` permission**.

---

## 2. Non-negotiable quality principles

These are the difference between output that looks professional and output that looks processed. Violating any one of them produces visible artifacts that no amount of good grading logic can compensate for.

### 2.1 Work in 32-bit float, unbounded

The Portrait profile iterates up to six times through LAB. In 8-bit, each round trip quantizes, and six rounds of accumulated quantization error produces visible banding in skies, skin gradients, and out-of-focus backgrounds.

**Working format: `CV_32FC3`, linear sRGB, unbounded.** Values may exceed 1.0 during processing — highlight recovery needs the headroom, and clamping mid-pipeline destroys recoverable detail. Quantize to 8-bit exactly once, at encode.

### 2.2 Know which operations belong in which space

Getting this backwards is the most common source of muddy, halo-ridden output.

| Operation | Space | Why |
|---|---|---|
| Resize, blur, blend, upscale | **Linear light** | These are physical light operations; doing them in gamma space darkens edges and creates halos |
| Tone curves, levels, gamma | **Gamma-encoded** | These are perceptual; linear-space curves feel wrong and crush midtones |
| LAB skin/color measurement and correction | **LAB (D65)** | Perceptually uniform, which is the entire point |
| Sharpening, local contrast | **L channel only** | Sharpening chroma produces color fringing on every edge |
| Saturation, vibrance | **HSV or LAB chroma** | |

Convert deliberately at each boundary. Log the conversions in debug builds so you can audit them.

### 2.3 Dither on the final quantize

Rounding 32-bit float to 8-bit produces banding in smooth gradients — skies, studio backdrops, blurred backgrounds. Add triangular probability density noise of ±0.5 LSB before rounding. It costs nothing and eliminates the single most recognizable "amateur digital" artifact.

### 2.4 Chroma subsampling 4:4:4 on export

Default JPEG encoders use 4:2:0, which halves color resolution. On saturated edges — red fabric, neon signage, foliage against sky — this is visible as color bleed. Set `IMWRITE_JPEG_SAMPLING_FACTOR_444` and `IMWRITE_JPEG_OPTIMIZE`. File size rises maybe 15%. Worth it.

### 2.5 Preserve metadata

Naive encoding strips EXIF. Losing capture date, lens, exposure, and GPS is unprofessional and unrecoverable. Copy all EXIF from source to output, then override exactly three fields: `TAG_ORIENTATION = 1` (orientation was baked into pixels), `TAG_SOFTWARE = "Sift"`, and updated dimensions.

### 2.6 Denoise before you amplify, sharpen after you resize

Shadow lifting amplifies whatever noise is in the shadows. Sharpening amplifies whatever noise survived. Correct order is denoise → tone → resize → sharpen, and output sharpening must be sized to the *output* resolution — a radius appropriate for a 6000px master is invisible at 1080px, and a radius appropriate for 1080px is crunchy at 6000px.

### 2.7 Never invent detail the source doesn't support

4× upscaling a soft frame produces confident, plausible, fictional texture. That is the opposite of professional. Effective upscale factor is capped by measured source sharpness (§6.6).

---

## 3. Constraints

**Free forever.** No dependency that requires an API key, phones home, or has a usage tier — regardless of how generous the free tier appears.

| Dependency | License |
|---|---|
| OpenCV Android SDK | Apache 2.0 |
| ONNX Runtime Mobile | MIT |
| Real-ESRGAN weights | BSD-3 |
| Compose, Room, WorkManager, Hilt, Coil 3 | Apache 2.0 |
| androidx.exifinterface | Apache 2.0 |

**APK size.** OpenCV across all ABIs is ~40MB. Restrict to `arm64-v8a` (`ndk { abiFilters += "arm64-v8a" }`) → ~15MB. It's the only ABI the target device needs.

**Permissions — complete list:**
- `READ_MEDIA_IMAGES`
- `ACCESS_MEDIA_LOCATION` — required for unredacted EXIF; without it MediaStore silently strips GPS
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`

No `INTERNET`. No write permission — scoped storage covers `MediaStore` inserts into `Pictures/Sift`.

---

## 4. Architecture

### 4.1 Modules

```
:app                  Compose UI, navigation, ViewModels
:core:model           Shared immutable types, no dependencies
:core:data            Room, MediaStore repository, DataStore settings
:core:imaging         The pipeline. OpenCV. No Android UI dependencies.
:core:ml              ONNX Runtime wrapper, tiling, model lifecycle
:core:testing         Golden images, fixtures, test utilities
```

`:core:imaging` must be independently testable via instrumented tests without launching UI. This is the module that determines output quality; it needs to be exercisable in isolation.

### 4.2 Patterns

- **DI:** Hilt.
- **Async:** Coroutines + Flow. `Dispatchers.Default` for imaging, never `Main`.
- **State:** Unidirectional. ViewModel exposes a single immutable `UiState`; UI emits events upward.
- **Persistence:** Room with explicit migrations from schema v1. Export schemas to `app/schemas/` and commit them.
- **Background:** WorkManager with a foreground service for batch processing. Assume process death at any point; every job is resumable from Room state.

### 4.3 Threading and memory

Imaging runs on a dedicated dispatcher with parallelism capped at 2 — a 12MP float Mat is ~144MB, and three concurrent jobs will OOM on most devices.

```kotlin
val imagingDispatcher = Dispatchers.Default.limitedParallelism(2)
```

Every `Mat` must be released explicitly. Wrap in a `use {}` extension; leaking native memory in a batch of 200 photos will crash the app in a way that's painful to debug.

---

## 5. Data model

```kotlin
@Entity
data class MediaAsset(
    @PrimaryKey val id: Long,              // MediaStore._ID
    val uri: String,
    val dateTaken: Long,
    val width: Int, val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val dHash: Long,                       // 64-bit perceptual hash
    val clusterId: String?,
    val analysisJson: String?,             // serialized FrameAnalysis, §6.3
    val contentClass: ContentClass?,       // PORTRAIT, SCENE, NON_PHOTOGRAPHIC
    val lifecycleState: LifecycleState,    // §9.1 — single source of truth
    val seenAt: Long?                      // null = never triaged
)

@Entity
data class TriageDecision(
    @PrimaryKey val assetId: Long,
    val verdict: Verdict,                  // KEEP, TOSS, SKIP
    val decidedAt: Long,
    val committed: Boolean
)

@Entity
data class EditJob(
    @PrimaryKey val id: String,
    val sourceAssetId: Long,
    val outputUri: String?,
    val profile: GradeProfile,
    val profileWasManual: Boolean,
    val derivedParamsJson: String,         // every parameter used, §6.3
    val upscaleFactor: Float,              // 1.0 = none
    val gateResultsJson: String,           // §6.12
    val fellBackToOriginal: Boolean,
    val processingMs: Long,
    val state: JobState,

    // Review & approval — §9
    val approvedAt: Long?,
    val rejectedAt: Long?,
    val rejectionReason: RejectionReason?,  // §9.5 — the only tuning signal you get
    val originalTrashedAt: Long?
)

enum class LifecycleState {
    UNTRIAGED, TRASHED_AT_TRIAGE, QUEUED_FOR_GRADE, GRADING,
    PENDING_REVIEW, APPROVED, ORIGINAL_TRASHED, REJECTED, DO_NOT_GRADE
}

enum class RejectionReason {
    TOO_WARM, TOO_COOL, TOO_CONTRASTY, TOO_FLAT,
    LOST_DETAIL, SKIN_WRONG, PREFER_ORIGINAL
}
```

`derivedParamsJson` and `gateResultsJson` are not optional. When a photo comes out wrong you need to know exactly what the pipeline decided and why, without re-running it.

---

## 6. The imaging pipeline

### 6.1 Canonical order of operations

Deviating from this order produces specific, predictable artifacts. Each step notes what breaks if it moves.

```
 1. Decode                        → 8/16-bit source
 2. Bake EXIF orientation         → move it later and every crop is wrong
 3. Color space normalize         → to linear sRGB float
 4. Promote to CV_32FC3           → move it later and you quantize repeatedly
 5. ANALYZE (§6.3)                → one measurement pass, drives everything after
 6. Content routing (§6.4)        → portrait / scene / skip
 7. Denoise (§6.5)                → after tone lift and you're amplifying noise
 8. Upscale (§6.6)                → after grading and the model sees data it wasn't trained on
 9. Grade (§6.7 or §6.8)          → the profile-specific work
10. Local contrast (§6.9)         → before grading and the tone curve fights it
11. Resize to output (§6.10)      → in linear light, always
12. Output sharpen (§6.10)        → before resize and it's the wrong radius
13. Quantize + dither (§2.3)      → exactly once, here
14. Encode + metadata (§6.11)     → 4:4:4, EXIF copied
15. Quality gates (§6.12)         → verify, fall back if failed
```

### 6.2 Precision and color management

**Decode.** Use `ImageDecoder` with `setTargetColorSpace`. Handle JPEG and HEIF (Samsung shoots HEIF when configured). Read the source color space via `Bitmap.getColorSpace()`.

**Source gamut.** Samsung shoots sRGB or Display P3 depending on settings. If P3, convert to sRGB using a **perceptual soft-clip**, not a hard clamp — hard clamping saturated reds and greens flattens them into featureless blocks. Soft-clip compresses the out-of-gamut region smoothly over the top 10% of chroma.

**Working space:** linear sRGB, `CV_32FC3`, unbounded.

**LAB conversion:** linear sRGB → XYZ (D65) → CIELAB.

> **Porting trap — read this twice.** OpenCV's 8-bit LAB uses scaled ranges: `L ∈ [0,255]`, `a,b ∈ [0,255]` offset by 128. OpenCV's **float** LAB uses true CIELAB: `L ∈ [0,100]`, `a,b ∈ [-127,127]` centered on zero. Your existing Python measured 8-bit LAB and subtracted 128. The target values in §6.7 are stated in **true CIELAB units** and map directly to float LAB with no offset. Porting the Python's `-128` arithmetic into a float pipeline will silently produce garbage that still looks like a plausible image. The golden-image test (§14.1) is the only thing that catches this.

**Export:** tag sRGB. P3 export is a documented future option (§17).

### 6.3 The analysis pass — the "unique treatment" engine

This is the architectural answer to per-image treatment. **One measurement pass produces one struct. Every downstream parameter is a function of that struct.** No downstream stage may introduce a constant that isn't a target or a bound.

```kotlin
data class FrameAnalysis(
    // Exposure & tone
    val medianL: Float,                    // CIELAB L*
    val clippedHighlightFraction: Float,   // L* > 98
    val crushedShadowFraction: Float,      // L* < 2
    val blackPointL: Float,                // 0.1 percentile
    val whitePointL: Float,                // 99.9 percentile
    val dynamicRange: Float,               // white - black
    val histogramEntropy: Float,           // tonal distribution health

    // Per-channel clipping — drives highlight reconstruction
    val channelClipFractions: FloatArray,  // R, G, B independently

    // Color
    val greyWorldCastAB: Pair<Float, Float>,   // measured a*, b* drift, mid-luma band
    val meanChroma: Float,
    val chromaP95: Float,                  // already-saturated content

    // Skin
    val skinFraction: Float,
    val largestSkinRegionFraction: Float,
    val skinMedianLab: Triple<Float, Float, Float>?,

    // Detail & noise
    val laplacianVariance: Float,          // global sharpness
    val laplacianVarianceP90: Float,       // sharpness of the sharpest regions
    val noiseSigmaLuma: Float,             // MAD in flat regions
    val noiseSigmaChroma: Float,
    val flatRegionFraction: Float,

    // Content
    val faceCount: Int,
    val faceBoxes: List<Rect>,
    val isLikelyScreenshot: Boolean,
    val isLikelyDocument: Boolean,
    val edgeDensity: Float
)
```

**Measurement notes:**

- **Noise sigma** — measure median absolute deviation of the Laplacian, restricted to flat regions (local variance below the 25th percentile). Measuring noise globally conflates texture with noise and will make you denoise a photo of grass into plastic.
- **Sharpness at P90, not just mean** — a photo with a sharp subject and a blurred background has low mean sharpness but is perfectly sharp where it matters. Mean alone will wrongly trigger the "too soft to upscale" gate.
- **Screenshot detection** — dimensions exactly match a known device resolution, AND no `TAG_F_NUMBER`/`TAG_EXPOSURE_TIME` in EXIF. Both conditions, not either.
- **Document detection** — high edge density, low chroma, bimodal luminance histogram.
- **Faces** — ML Kit face detection is free and offline, but adds a dependency. Alternative: OpenCV's bundled YuNet ONNX detector, already in the stack. Prefer YuNet.

Serialize the whole struct to `MediaAsset.analysisJson`. Serialize every derived parameter to `EditJob.derivedParamsJson`. When a photo comes out wrong six weeks from now, this is how you find out why.

### 6.4 Content routing

```
isLikelyScreenshot || isLikelyDocument     → NON_PHOTOGRAPHIC → skip grading entirely
skinFraction > 0.02
  && largestSkinRegionFraction > 0.01      → PORTRAIT
  && (faceCount > 0 || skinFraction > 0.08)
otherwise                                  → SCENE
```

The contiguity check exists because brick, sand, wood, and terracotta walls all clear a raw 2% skin threshold. The face-count term is a second guard on the same failure.

Non-photographic content is a large fraction of any real camera roll and grading it is nonsense. Route it out, and surface it in triage as a likely-toss cluster.

Always display the chosen profile. Always allow one-tap override. Persist overrides in `profileWasManual` so router accuracy can be audited against real decisions.

### 6.5 Denoise

**Skip unless warranted.** You verified this before: measured noise below the visible threshold means denoising costs sharpness and returns nothing.

```
if (noiseSigmaLuma < 2.0 && noiseSigmaChroma < 3.0) skip
```

When it runs:
- **Chroma more aggressively than luma.** Chroma noise reads as ugly colored mottling; luma noise reads as film grain and is often desirable. Strength ratio roughly 3:1 chroma:luma.
- Strength derived from measured sigma, not fixed: `h_luma = clamp(noiseSigmaLuma * 1.5, 1, 10)`.
- Use `fastNlMeansDenoisingColored` on the 8-bit path, or a bilateral/guided filter on the float path. Preserve the float pipeline — round-tripping to 8-bit for denoise defeats §2.1.
- **Never denoise flat-region-only.** Region-selective denoise creates visible texture boundaries.

### 6.6 Upscale

**Gate.** Run only when the source genuinely lacks resolution:

```
targetLongEdge = max(requested output long edges)
run if:  sourceLongEdge < targetLongEdge * 0.9
     ||  postCropPixels < 2_000_000
     ||  explicit user request
```

**Cap the factor by measured sharpness.** This is §2.7 made concrete:

```
if (laplacianVarianceP90 < 50)   maxFactor = 1.0   // too soft; SR would invent detail
if (laplacianVarianceP90 < 150)  maxFactor = 2.0
else                             maxFactor = 4.0

effectiveFactor = min(maxFactor, ceil(needed), 4.0)
```

A soft source upscaled 4× produces confident fictional texture. Refuse.

**Model:** Real-ESRGAN `general-x4v3`, ONNX, via ONNX Runtime Mobile with NNAPI EP and XNNPACK fallback. The companion `wdn` variant exists specifically to control denoise strength — blend between the two models' outputs using the measured `noiseSigmaLuma` rather than picking one.

**Detail-preserving blend — this is what separates professional from plasticky.** Real-ESRGAN over-smooths skin and foliage, producing the waxy look that makes AI upscaling recognizable. Counter it:

1. Produce the SR output.
2. Produce a Lanczos upsample of the same source at the same factor.
3. High-pass the Lanczos result (subtract a Gaussian blur of itself).
4. Add 12–18% of that high-pass back onto the SR output.

This restores micro-texture and natural grain structure that the model smoothed away. Tune the percentage once against your own photos and record the value in the repo.

**Faces get conservative treatment.** SR models produce uncanny results on small faces. For any `faceBox` smaller than 5% of frame area, blend SR toward Lanczos at 50% within that region, feathered over 16px. Preserves plausibility over invented sharpness.

**Memory — this WILL OOM if handled naively.** A 4000×3000 source at 4× is 16000×12000 = 192MP; as `ARGB_8888` that's **768MB in one Bitmap**.

- Tile at 512×512 with 32px overlap; feather-blend seams with a cosine window (linear blending leaves visible seams in gradients).
- Never materialize the full output as a Bitmap. Stream tiles to a file-backed intermediate.
- Cap output long edge at **6000px**.
- Foreground service with progress notification. Never on the UI thread.

**Before writing any ONNX code: A/B it.** Build a Lanczos + unsharp baseline. Compare on ten of your real photos at 100%. If the difference doesn't justify 30 seconds per image and a native dependency, ship the baseline and delete this section.

### 6.7 Portrait profile — skin-anchored

Port of your validated pipeline, in float.

**Reference target (healthy fair skin), true CIELAB units:**
```
L* = 68.0
a* = 12.5
b* = 17.0
```

**Guard rail:** final skin `b*` must land in **15–22**. Below ~10 the subject reads ill and grey. Outside range → gate failure → ship original (§6.12).

**Skin mask — measurement only:**
```
(R > G + 8) AND (G > B) AND
(Cr > 135) AND (Cr < 178) AND
(Cb > 85)  AND (Cb < 128)
```

**Algorithm:**
1. Build the skin mask. **Measure only.** Take median L\*, a\*, b\* inside it.
2. Compute delta from measured to target.
3. **Apply the delta globally to the entire frame.** Never through the mask.
4. Re-measure. Iterate up to **6** times. Stop when all three channels are within **0.5** of target.
5. If not converged after 6 iterations, accept the closest result and record `iterations = 6` — do not loop further.

**Adaptive damping.** Applying the full measured delta each iteration can oscillate on frames with mixed lighting. Apply `delta * 0.7` per iteration. Converges in 3–4 passes instead of oscillating, and the result is stable.

**Then apply exposure correction if warranted** — derived from `medianL`, targeting 50, clamped to ±15 so a deliberately low-key portrait isn't flattened into a snapshot.

**What Portrait mode must never do:**

- **No global white balance.** The phone's AWB already corrected, often overcorrected against warm walls. Adding neutralization on top, plus a red-pull on skin, is the triple-cooling failure that drove b\* to ~7 and produced the ill, grey result. One correction, derived from skin, applied globally. This is the single most important constraint in the document.
- **No masked application.** The mask is imperfect. Applying through it blotches wherever it misclassifies. Measuring through it and applying globally means mask errors nudge the estimate rather than leaving artifacts.

### 6.8 Scene profile — tone-anchored

For everything without people. Every parameter derived from `FrameAnalysis`.

1. **Per-channel highlight reconstruction.** When one channel clips but others don't, the clipped channel can be reconstructed from the ratio of the unclipped ones. Check `channelClipFractions` independently. This recovers detail in blown skies and light sources that a luminance-only highlight pull cannot.
2. **Highlight rolloff.** If `clippedHighlightFraction > 0.005`, apply a smooth shoulder mapping 0.5%–5% clipping onto −20 to −60. Shoulder, not a hard curve, or you get a visible tonal step.
3. **Shadow lift.** If `crushedShadowFraction > 0.01`, lift shadows proportionally, bounded so the frame doesn't go milky. Cap the lift so `blackPointL` never rises above 3.
4. **Conservative auto-levels.** Black at the 0.1 percentile, white at 99.9. Never 0/100 — that clips.
5. **Midtone gamma** toward `medianL` ≈ 50, damped by `histogramEntropy`: a deliberately high-key or low-key frame has low entropy and should be moved less.
6. **Contrast S-curve**, amplitude inversely proportional to existing `dynamicRange`. A flat frame gets more; a contrasty frame gets almost none.
7. **Vibrance, not saturation.** Per-pixel boost weighted by `(1 - localSaturation)`, so muted areas gain and vivid areas don't blow out. Base amount +20, scaled down by `chromaP95` — an already-vivid frame needs less.
8. **Clamped grey-world white balance.** Sample the mid-luminance band (L\* 30–70) only. **Clamp correction to ±5 in a\* and b\*.** Without this clamp, grey-world neutralization turns golden hour and blue hour into grey, destroying exactly the frames most worth keeping.

Note the deliberate asymmetry with Portrait: Scene does white balance, Portrait does not. In Portrait the skin measurement *is* the white balance, and a second correction double-counts. In Scene there's no such anchor, so a clamped neutral pass earns its place.

### 6.9 Local contrast

What makes a photo read as "professionally finished" rather than merely correct. Large-radius, low-amount unsharp mask on the **L channel only**.

- Radius: `sourceLongEdge / 60`, roughly 60–100px on a 12MP frame.
- Amount: 0.15–0.35, scaled inversely by `edgeDensity` — a busy frame needs less or it turns crunchy.
- **Halo suppression is mandatory.** Clamp the local adjustment so no pixel moves more than 8 L\* units. Without this you get the grey halos around horizons and rooflines that are the signature of overcooked HDR.

### 6.10 Resize and output sharpen

**Resize in linear light.** Downsampling gamma-encoded data darkens the result — the classic mistake, and it's visible as muddy midtones on any high-frequency content.

- Downsample: `INTER_AREA` (correct box-average behavior).
- Upsample: `INTER_LANCZOS4`.

**Sharpen after resize, sized to output.** Unsharp mask on the L channel only:

- Radius: `outputLongEdge / 1200` — roughly 0.9px for a 1080px story, 5px for a 6000px master.
- Amount: derived from post-resize `laplacianVariance`, targeting a consistent apparent sharpness rather than a fixed strength.
- **Threshold:** skip pixels whose local contrast is below `noiseSigmaLuma * 2`. Without a threshold you sharpen noise in skies into visible speckle.

### 6.11 Quantize, encode, metadata

1. Convert working float back to gamma-encoded sRGB.
2. Add triangular dither (±0.5 LSB), then round to 8-bit (§2.3).
3. Encode: `IMWRITE_JPEG_QUALITY` per preset, `IMWRITE_JPEG_SAMPLING_FACTOR_444`, `IMWRITE_JPEG_OPTIMIZE = 1`.
4. Copy all EXIF from source via `androidx.exifinterface`. Override `TAG_ORIENTATION = 1`, `TAG_SOFTWARE = "Sift"`, and dimensions.
5. Insert via `MediaStore.Images` with `RELATIVE_PATH = "Pictures/Sift"`, using `IS_PENDING = 1` during write and clearing it on completion so half-written files never appear in the gallery.

### 6.12 Quality gates

Every output is verified before it's kept. **This is what "professional standard" actually requires** — not just good processing, but automated refusal to ship bad results.

| Gate | Condition | On failure |
|---|---|---|
| Skin range | Portrait only: final skin b\* ∈ [15, 22] | Retry with damping 0.4; if still failing, ship original |
| No new clipping | `clippedHighlightFraction` did not increase by > 0.002 | Reduce contrast/exposure terms 50%, retry once |
| No shadow crush | `crushedShadowFraction` did not increase by > 0.002 | Same |
| Sharpness preserved | Post `laplacianVarianceP90` ≥ 85% of pre | Reduce denoise strength 50%, retry once |
| No banding introduced | Gradient smoothness metric in flat regions within tolerance | Verify dither is applied; ship original if it recurs |
| Chroma sanity | `meanChroma` did not change by more than 40% | Ship original |

Record all gate results in `EditJob.gateResultsJson`. Set `fellBackToOriginal` when any gate ultimately fails. Surface fallbacks in the UI — a silent fallback teaches you nothing.

---

## 7. Ingest and clustering

**Ingest.** Paginated `MediaStore.Images` query, newest first, 200-row pages. Project only needed columns; never pull `DATA`. `ContentObserver` for new captures.

Compute `dHash` and lightweight analysis lazily as items scroll into view. Full-library processing on first launch is a multi-minute stall; run it as a low-priority `WorkManager` job.

**dHash:** downsample to 9×8 grayscale, compare each pixel to its right neighbour, pack 64 bits.

**Clustering.** Two photos cluster when both hold:
- `abs(dateTaken_a - dateTaken_b) < 10_000` ms
- Hamming distance ≤ 8

Cluster greedily in timestamp order. Within a cluster, rank by `laplacianVarianceP90` and pre-select the sharpest as suggested keeper.

---

## 8. Triage UI

Swipe deck. Right = keep, left = toss, up = skip. Clusters show the suggested keeper large with the rest as a filmstrip; tapping promotes.

**Required for this to feel finished rather than prototypal:**

- **Undo**, last 10 decisions. A swipe deck without undo is hostile.
- **Volume-key bindings** — vol-down toss, vol-up keep. Faster than swiping and lets the thumb rest.
- **Session resume** with a "142 of 380" readout.
- **Long-press for 1:1 zoom.** Sharpness uncertainty is the most common reason to hesitate; make it resolvable in one gesture.
- **Non-photographic auto-cluster.** Screenshots and documents grouped for one-swipe bulk rejection.
- **Empty state offers the next batch** rather than showing a blank screen.

**Deletion is batched.** Decisions write to Room only. On exit or Commit, collect every `TOSS` URI and fire one `MediaStore.createTrashRequest(resolver, uris, true)`.

**Trash, never delete.** 30-day recovery. Handle the `IntentSender` result properly — a cancelled dialog must leave decisions intact in Room, not silently discard them.

**This is deletion batch 1 of 2.** Triage rejects are trashed here. Originals of approved keepers are trashed separately in §9. Never merge the two into one `createTrashRequest` — different intent, different risk, different confirmation copy.

---

## 9. Review and approval

Auto-grading without review means silently shipping whatever the pipeline decided. Deferred original-deletion without review means trusting it enough to destroy the source. This stage is what makes both safe.

### 9.1 Asset lifecycle

```
UNTRIAGED
   ├─ toss ──→ TRASHED_AT_TRIAGE                    (deletion batch 1, immediate)
   └─ keep ──→ QUEUED_FOR_GRADE
                  │ auto, §9.2
                  ↓
               GRADING ──→ PENDING_REVIEW
                              ├─ approve ─→ APPROVED ─→ ORIGINAL_TRASHED  (batch 2)
                              ├─ reject  ─→ REJECTED   (graded discarded, original kept)
                              └─ regrade ─→ QUEUED_FOR_GRADE
```

`MediaAsset.lifecycleState` is the single source of truth. Every transition is written to Room before any filesystem operation, so process death mid-transition is recoverable.

### 9.2 When grading runs

Automatic, but battery-aware. Grading 200 keepers is roughly 8 minutes of sustained CPU before upscaling.

- Battery > 30% **or** charging → start immediately on triage commit
- Otherwise → defer with `WorkManager` constraint `requiresCharging`
- Manual **Grade now** override always available
- Foreground service with progress; cancellable and resumable

### 9.3 Safety invariants

An original may be trashed only when **all five** hold:

1. `EditJob.state == DONE`
2. `EditJob.fellBackToOriginal == false`
3. Output URI resolves **and decodes** — re-verify at approval time, not at write time
4. Output file size > 0 and dimensions match the expected preset
5. User explicitly approved this specific asset

**Invariant 2 is the one that will bite you.** When a quality gate fails and the pipeline ships the original (§6.12), the "graded" export is just a re-encode of the source. Trashing the original leaves a generation-loss JPEG as your only master. Assets with `fellBackToOriginal == true` must never be offered for original-trashing — disable the control and state the reason in the UI.

**Invariant 3 exists because write-time success is not read-time success.** Storage can fill, the write can be interrupted, MediaStore can leave an `IS_PENDING` row behind. Decode the output immediately before issuing the trash request for its source. It costs 200ms and prevents the one unrecoverable failure in the app.

### 9.4 Review UI

- **Press-and-hold to compare.** Hold shows the original, release returns to graded — Lightroom's `\` key adapted to touch. Do **not** use a split-screen slider; a global color shift is nearly impossible to judge when half the frame is the other version.
- **Pinch to 1:1**, and compare must persist at zoom. Upscale artifacts and oversharpening are only visible here.
- **Verdict strip** beneath the frame: profile used, iterations to converge, final skin b\* (portrait), which gates passed, whether it fell back.
- **Swipe to approve/reject**, same gestures as triage so muscle memory carries.
- **Approve all**, with individually flagged items held back. The common case is 38 of 40 fine and two worth a second look.
- **Storage readout** on the approve action: "Approving frees 1.4 GB."

### 9.5 Rejection is the only tuning signal you get

On reject, capture a one-tap reason (`RejectionReason`). Store it on the `EditJob`.

This is the sole feedback loop into §6.7's targets. After 50 rejections, Settings can show the distribution — a run of `TOO_WARM` means the b\* target of 17 is high for your typical lighting, and you adjust one number instead of guessing. Without this, a systematically wrong target is invisible; you'd just quietly reject more photos over time.

Three inline recovery actions:
- **Regrade with the other profile** (portrait ↔ scene) — fixes router misclassification
- **Regrade at reduced strength** — all adaptive amounts × 0.5
- **Keep original, mark `DO_NOT_GRADE`** — some frames are right as shot

### 9.6 Storage pressure

Holding original + graded + exports during the pending-review window roughly triples footprint.

- Check free space before a batch; refuse below 2 GB with a clear message
- Cap the pending-review backlog at 300 assets; prompt to review before grading more
- Surface pending-review storage in Settings

---

## 10. Export presets

All derived from the graded master.

| Preset | Dimensions | Quality | Sharpen radius |
|---|---|---|---|
| Story | 1080×1920 | 92 | 0.9px |
| Feed 4:5 | 1080×1350 | 92 | 0.9px |
| Feed 1:1 | 1080×1080 | 92 | 0.9px |
| Master | source or upscaled, ≤6000px | 95 | scaled |

Centre-weighted crop with manual adjust. When `faceBoxes` is non-empty, bias the crop to keep faces inside the frame and off the exact centre — rule-of-thirds placement on the dominant face.

---

## 11. Settings

- Grade profile: auto-route / force portrait / force scene / off
- Auto-grade on commit, or grade on demand
- Upscale: off / gated / always
- **Portrait target L\*/a\*/b\*, editable.** You will want to tune these for different lighting. Hardcoding means editing Kotlin to change a number.
- Detail-blend percentage for upscale (§6.6)
- Export presets enabled
- Debug: dump `FrameAnalysis` and derived parameters alongside output
- Reset to validated defaults

---

## 12. Error handling

| Failure | Behavior |
|---|---|
| Decode failure | Mark asset `UNREADABLE`, skip, continue batch |
| OOM during processing | Catch, release Mats, retry once at half resolution, then fail the job |
| ONNX session init failure | Disable upscale for the session, log, continue with Lanczos |
| Quality gate failure | Fallback chain per §6.12 |
| Trash request cancelled | Decisions remain uncommitted in Room |
| Output missing/undecodable at approval | Block original-trashing, re-queue the grade job, notify (§9.3) |
| Free space below 2 GB before batch | Refuse to start, prompt to review pending backlog (§9.6) |
| Process death mid-batch | WorkManager resumes from Room; completed jobs are not redone |
| Corrupt EXIF | Process without it; never fail the frame over metadata |

Never fail an entire batch because of one bad frame. Log failures locally — no network, so write to a rotating file in app storage and expose it in Settings.

---

## 13. Performance budgets

| Operation | Budget |
|---|---|
| Grid scroll, 5000 items | 60fps sustained |
| Thumbnail from cache | < 100ms |
| dHash | < 30ms |
| Full `FrameAnalysis`, 12MP | < 400ms |
| Portrait grade, 12MP, float, 4 iterations | < 2.5s |
| Scene grade, 12MP, float | < 1.5s |
| Batch grade 50 keepers | < 3 min, foreground service |
| Upscale, per image | 10–60s |
| Cold start to usable deck | < 1.5s |

A miss means stop and fix, not carry on.

---

## 14. Testing

**14.1 Golden-image parity (gates M3).** Three portraits with Python outputs committed to `:core:testing`. Assert per-channel mean delta < 1.0. This is the only test that catches the 8-bit/float LAB scaling trap in §6.2.

**14.2 Guard rail.** Across a 20-portrait set, every graded output has skin b\* ∈ [15,22], or is flagged and passed through ungraded. Zero silent violations.

**14.3 Scene no-regression.** On already well-exposed frames, Scene output differs only slightly from input. A large delta means adaptive terms are overreaching.

**14.4 Router accuracy.** 100 hand-labeled photos. Target > 90% precision on PORTRAIT, > 95% on NON_PHOTOGRAPHIC.

**14.5 Clustering.** Hand-labeled burst set; measure precision and recall against the 10s + Hamming-8 rule.

**14.6 Banding.** Synthetic smooth gradient through the full pipeline. Assert no visible steps. Run with dither disabled to confirm the test actually detects banding.

**14.7 Memory.** Upscale the largest source file. Assert no OOM, output within 6000px cap, all Mats released (check native heap before/after).

**14.8 Cancelled dialog.** Cancel the trash dialog mid-commit; verify every decision survives.

**14.9 Gate fallback.** Inject a frame engineered to fail each gate; verify correct fallback and that the original ships.

**14.10 Original-retention safety.** The most important test in the suite — its failure mode is permanent photo loss.
- Assert an original with `fellBackToOriginal == true` can never be trashed, through any code path.
- Delete the output file behind the app's back, then attempt approval; assert the trash request is refused and the grade is re-queued.
- Kill the process between `APPROVED` and `ORIGINAL_TRASHED`; assert relaunch resumes correctly and never double-trashes.
- Assert triage rejects and approved-originals never appear in the same `createTrashRequest`.

**14.11 Lifecycle state machine.** Property test over `LifecycleState`: no transition reaches `ORIGINAL_TRASHED` without passing all five §9.3 invariants.

---

## 15. Build order

| M | Deliverable | Gate |
|---|---|---|
| **M0** | Permissions, MediaStore, thumbnail grid | 60fps over 5000 items |
| **M1** | Swipe deck, Room, undo, batched trash | 200 photos triaged, one dialog, correct items trashed |
| **M2** | dHash, clustering, screenshot detection | Bursts collapse; screenshots auto-cluster |
| **M3** | Float pipeline + `FrameAnalysis` + Portrait grade | §14.1 parity passes |
| **M4** | Scene grade + router | §14.4 accuracy met |
| **M5** | Quality gates + fallback | §14.9 passes |
| **M6** | Export presets, encode, EXIF, settings | Exports at exact dimensions with EXIF intact |
| **M7** | Review UI, lifecycle, deferred original-trashing | §14.10 and §14.11 pass |
| **M8** | Upscale — only if it survives the §6.6 A/B | §14.7 passes |

**M0–M2 is the app you'd use daily.** M3–M6 is the quality. **M7 is what makes M3–M6 safe to trust** — until it exists, auto-grading is writing files nobody has looked at, so keep original-trashing disabled entirely before M7 lands. M8 is optional and may be deleted by its own A/B.

---

## 16. Traps

1. **8-bit vs float LAB scaling.** §6.2. Silently produces plausible garbage. The golden test is the only defense.
2. **EXIF orientation baked late.** Every crop wrong. This has bitten you before.
3. **Resize in gamma space.** Muddy midtones, visibly wrong on high-frequency content.
4. **Sharpening chroma.** Color fringing on every edge.
5. **8-bit intermediates.** Banding after six LAB round trips.
6. **Missing dither.** Banding in every sky.
7. **4:2:0 subsampling.** Color bleed on saturated edges.
8. **Native Mat leaks.** Batch of 200 crashes in a way that's miserable to debug.
9. **Unclamped grey-world.** Grey sunsets.
10. **Global noise measurement.** Denoises grass into plastic.
11. **Mean sharpness for the upscale gate.** Shallow-depth-of-field portraits wrongly rejected. Use P90.
12. **`ACCESS_MEDIA_LOCATION` omitted.** GPS silently stripped, invisible for months.
13. **Skin router on terracotta.** The contiguity + face-count guards exist for this.
14. **Trashing an original whose grade fell back.** §9.3 invariant 2. Leaves a generation-loss re-encode as your only master. The one genuinely unrecoverable bug in the app.
15. **Trusting write-time success at approval time.** §9.3 invariant 3. Re-decode the output before trashing its source.
16. **Merging the two deletion batches.** Triage rejects and approved-originals carry different risk and need different confirmation copy.

---

## 17. Non-goals and deferred

**Non-goals:** cloud manipulation (impossible), network access of any kind, video, iOS, accounts, generative editing, face recognition/tagging, Play Store distribution.

**Deferred, with reasoning:**

- **RAW/DNG processing.** If you shoot Samsung Expert RAW, processing the DNG would give dramatically more highlight and shadow latitude than a JPEG ever can — it's the single largest available quality gain beyond this spec. It's also a substantial subsystem (demosaic, camera color profile, per-sensor calibration). Worth doing only after v1 proves itself.
- **Display P3 export.** Wider gamut on capable displays. Deferred because it complicates the color path and most destinations still assume sRGB.
- **Perceptual crop scoring.** Auto-selecting the best crop by saliency rather than face position.

---

## 18. Decisions made

1. **Originals policy — settled.** Auto-grade every keeper, retain the original, trash it only on explicit approval of the graded result. Implemented in §9.
2. **Auto-grade — settled.** Automatic on triage commit, battery-gated, with review required before any original is destroyed. §9.2.

### Still open

3. **Does upscale survive the §6.6 A/B?** Answer before writing ONNX code — it can delete M8 entirely.
4. **Detail-blend percentage** (§6.6) — tune once against your own photos, then commit the value to the repo.
5. **Pending-review backlog cap.** 300 is a guess. Revisit once you know your real shooting volume per session.
