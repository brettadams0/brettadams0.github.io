# Cue — Technical Specification v2

**What it is:** A drafting assistant for Hinge and Tinder that captures conversation context, writes replies in your actual voice, and never sends anything.
**Platforms:** Android app (Hinge + Tinder), Chrome extension (Tinder web only).
**Posture:** Draft-only, structurally. Fully on-device inference. No backend, no API, **no network, $0 forever.**
**Status:** Ready to build

---

## 0. How to read this

Section 4 is the product. Everything else is plumbing.

Four rules govern the build:

1. **Voice is compiled, not generated.** The model produces *content*; your voice is applied afterward by deterministic transforms. This is what makes a 2–4B on-device model viable, and it would be the right architecture even with a frontier model. §4.4.
2. **Draft-only is an architectural property, not a policy.** No accessibility action calls, no auto-fill, no send automation. Copy-to-clipboard is terminal. §2.1.
3. **Never assert a fact absent from the captured context.** Inventing that she likes climbing is unrecoverable in a live conversation. Small models hallucinate more than large ones, so this gate matters *more* here than it would with an API. §7.2.
4. **Zero cost is a hard constraint, not a preference.** Every dependency free, offline, and unlimited. No free tiers that could be revoked or repriced. §3.1.

---

## 1. Product definition

Capture a profile or conversation → get three strategically distinct drafts in your voice → pick one, edit it, send it yourself.

**Done when:** you stop opening a chat window to write dating messages, because the drafts land closer than what you'd have typed and take ten seconds instead of five.

**The value isn't "writes for you."** Chat already does that. The value is eliminating the re-supply of context every single time — her profile, the conversation, how you talk, what stage this is at. That's the friction, and it's the only part an app can remove that a chat window cannot.

---

## 2. Non-negotiables

### 2.1 Draft-only, enforced structurally

Match Group owns both apps and prohibits automated access and bot behavior. Reading to draft is defensible; sending is not, and dating-app bans are effectively permanent and often device-linked.

- The `AccessibilityService` (§5.2) declares `canRetrieveWindowContent="true"` and **never calls `performAction()`**. Enforce with a build-failing static check, not a code review habit.
- The Chrome extension holds no permission to write into page inputs. It reads DOM and renders in a side panel.
- No clipboard auto-paste, no simulated taps, no send-button coordinates. Anywhere.

Cue is a writing tool that happens to read your screen. It is never an agent operating your account.

### 2.2 Your voice is measured, then applied mechanically

"Casual tone" as a prompt instruction produces LinkedIn-casual — and a small model obeys tone instructions even more loosely than a large one. So your voice is extracted from real sent messages as numbers, injected as few-shot examples, and then **enforced by deterministic post-processing**. §4.

### 2.3 Nothing leaves the device, at all

Not a privacy posture bolted on — a structural fact. OCR is on-device, inference is on-device, storage is local and encrypted. The Android app ships **without the `INTERNET` permission** (model download happens once via a separate flow, §3.3).

She never consented to any of this. On-device inference is the only version of this app that's defensible, and it happens to also be the free one.

---

## 3. Zero-cost architecture

### 3.1 Why not a free API tier

Google AI Studio, Groq, and OpenRouter all offer free tiers that would produce better drafts than a 4B local model. Rejected for two reasons:

- **They retain and train on submitted data.** You'd be uploading a stranger's private messages to train someone's model. That's a real problem, not a compliance checkbox.
- **Free tiers get revoked, rate-limited, or repriced.** "Free forever" and "free tier" are different claims. A local model is the only one that can't be taken away.

If you ever decide the quality gap is intolerable, §18.1 documents the escape hatch and what you'd be trading.

### 3.2 The full dependency list

| Component | Choice | Cost |
|---|---|---|
| Inference (Android) | MediaPipe LLM Inference API + Gemma 3n | Free, Apache 2.0 / Gemma terms |
| Inference (Desktop) | WebLLM (MLC) via WebGPU in the extension side panel | Free, Apache 2.0 |
| OCR | ML Kit Text Recognition v2, on-device | Free |
| Retrieval | BM25 over your own message corpus, hand-rolled | Free, no model |
| DB | Room + SQLCipher | Free |
| UI | Compose / TypeScript | Free |
| Distribution | Sideloaded APK, unpacked extension in developer mode | Free — avoids the $25 Play fee and the $5 Chrome Web Store fee |

No account, no key, no quota, no expiry.

### 3.3 Model tiering — do not hardcode one model

Pick at install time based on measured available RAM. Your device tier determines what's realistic, and guessing wrong means either an OOM crash or leaving quality on the table.

| Available RAM | Model | Size | Notes |
|---|---|---|---|
| ≥ 6 GB free | Gemma 3n E4B (int4) | ~3–4 GB | Best drafts; target if the device allows |
| 3–6 GB free | Gemma 3n E2B (int4) | ~2–3 GB | The realistic default on most devices |
| < 3 GB free | Gemma 3 1B (int4) | ~529 MB | Weak but usable with heavy few-shot; template path (§6.5) carries more weight |

Models are `.task` / `.litertlm` files from the HuggingFace `litert-community` org. Download once during onboarding over Wi-Fi, then the app operates fully offline. Gate the `INTERNET` permission to a separate downloader module or a manual file-drop into app storage if you want a truly network-free main app.

**Two implementation notes from the field:**
- Initialize the inference session in `Application.onCreate()`. Lazy init produces a multi-second stall on first use that reads as a broken app.
- **Benchmark CPU against GPU backend before committing.** Reports on Gemma 3n show CPU inference beating GPU by roughly 20%, especially on cold start. This is counterintuitive; measure on your actual device rather than assuming GPU wins.

### 3.4 Platform split

| | Android | Chrome extension |
|---|---|---|
| Covers | Hinge + Tinder | Tinder web only |
| Capture | Share-sheet screenshot + on-device OCR (v1); AccessibilityService (v2) | DOM read — clean text, no OCR |
| Inference | MediaPipe + Gemma 3n | WebLLM via WebGPU |
| Voice profile | Source of truth | Imported JSON |

**Hinge has no desktop version** — mobile only, no browser access. Desktop coverage is Tinder-only by definition. Don't design around fixing it.

No backend means no sync: export the voice profile as JSON from Android, import into the extension. It changes slowly; manual is fine.

---

## 4. The voice engine

### 4.1 What gets measured

```kotlin
data class VoiceProfile(
    val sampleCount: Int,

    // Length
    val medianWords: Float,
    val p90Words: Float,

    // Orthography — where "AI voice" leaks in worst
    val capitalizationRate: Float,         // % of messages starting capitalized
    val lowercaseIRate: Float,             // % of standalone "I" written "i"
    val terminalPunctuationRate: Float,
    val ellipsisRate: Float,
    val commaRate: Float,                  // per 100 words

    // Register
    val emojiRate: Float,
    val topEmoji: List<String>,
    val abbreviations: Map<String, Int>,   // u, ur, rn, tbh, ngl, lmao
    val contractionRate: Float,
    val profanityRate: Float,

    // Behavior
    val questionRate: Float,
    val burstRate: Float,                  // sent within 60s of your previous
    val characteristicTokens: List<String> // top TF-IDF vs. generic English
)
```

### 4.2 Bootstrapping

Onboarding: screenshot 15–20 of your own past conversations. OCR them, extract **only your messages**.

**Attribution by bounding box, not content.** ML Kit returns text blocks with `Rect` bounds. In both apps your messages are right-aligned, hers left-aligned. Cluster block centroids by x-position; the right cluster is you. A block whose right edge is within 15% of screen width is yours.

> **Highest-stakes logic in the app.** Reverse it and you build a voice profile from *her* messages, then generate drafts that sound like the person you're talking to — subtly wrong, hard to diagnose, and it poisons everything downstream. Display the extracted messages during onboarding and require explicit confirmation before writing the profile.

**Minimum 50 messages before the profile is trusted.** Below that, use a generic-casual baseline and show a persistent "calibrating" banner. Small corpora overfit brutally: three messages ending in "lol" become a law.

### 4.3 Retrieval — how a small model learns your voice

Instruction-following degrades fast at 2–4B. **Example-following doesn't.** So the voice isn't described to the model, it's demonstrated.

For every draft request, retrieve the **5 most contextually similar messages you've actually sent** and include them verbatim as few-shot examples.

Retrieval is **BM25 over your own message corpus** — no embedding model, no extra memory, ~20 lines of code, and entirely adequate for "find messages I sent in a situation like this." Index on the preceding message from her plus your reply, so retrieval matches on *situation*, not just vocabulary.

This is the single highest-leverage technique in the spec for small-model quality. A 2B model given five real examples of how you write outperforms a 4B model given a paragraph describing how you write.

### 4.4 The voice compiler — deterministic post-processing

The model's only job is content. Voice is applied afterward, in code, with no inference involved:

| Transform | Rule | Trigger |
|---|---|---|
| Lowercase | Downcase the leading character | `capitalizationRate < 0.3` |
| De-punctuate | Strip trailing `.` (never `?`) | `terminalPunctuationRate < 0.3` |
| Lowercase-I | `\bI\b` → `i` | `lowercaseIRate > 0.7` |
| Emoji trim | Remove emoji beyond profile rate; substitute from `topEmoji` | Always |
| Comma thinning | Drop serial commas | `commaRate` well below draft's |
| Contraction | "do not" → "don't" | `contractionRate > 0.8` |
| Forbidden tokens | Regex delete or regenerate | Always |
| Length | Truncate at last clause boundary under `p90Words + 3` | Always |

**Why this matters more than it looks:** every one of these is a place a small model reliably fails and a regex reliably succeeds. Spending inference budget on capitalization is waste; spend it on relevance and grounding instead. The compiler is also instant, deterministic, and testable — three things inference is not.

The forbidden-token list is the highest-value entry. Models default to a specific cheerful register; deleting the tells does more than any positive instruction:

```
haha, lol (if absent from your corpus), totally, for sure, absolutely,
I'd love to, that sounds amazing, can't wait, definitely, honestly,
em dashes, semicolons, "Let me know!", exclamation marks beyond your rate
```

---

## 5. Capture

### 5.1 Share sheet (v1 — build this first)

Screenshot the chat or profile → share → Cue. ML Kit OCR on-device, bounding-box attribution per §4.2.

Three taps, no special permissions, identical across both apps, immune to redesigns. Ugly and unbreakable. **Ship this before anything smarter** — it proves whether you use the app at all, which is the real risk.

Multi-screenshot stitching: order by timestamp, deduplicate overlapping messages by text hash.

### 5.2 AccessibilityService (v2 — only if v1 proves itself)

Reads the live view hierarchy. No screenshots, no OCR, true ordering and timestamps. Costs: broad permission, disqualifies Play Store distribution entirely (fine — sideload), and breaks whenever either app reorganizes its view tree.

Scope narrowly: `packageNames` limited to the two apps, `canRetrieveWindowContent` only, **`performAction` never called**.

### 5.3 Chrome extension (Tinder web)

Content script reads the DOM directly — real text, real ordering, real sender attribution, no OCR guesswork. By far the cleanest capture path in the system. Side panel renders drafts; no write access to the page.

### 5.4 Profile capture

```kotlin
data class MatchProfile(
    val displayName: String?,
    val age: Int?,
    val bio: String?,
    val prompts: List<PromptAnswer>,       // Hinge: question + answer pairs
    val attributes: Map<String, String>,   // job, school, location, intent
    val photoCaptions: List<String>,       // OCR'd text visible in photos only
    val capturedAt: Long
)
```

Hinge prompt answers are the highest-signal field in the app — volunteered, specific, and literally designed to be responded to. Weight them heavily.

---

## 6. Draft generation

### 6.1 Strategic variants, not tonal variants

Three drafts pursuing **different outcomes**, each labeled with intent. Three rewordings of one message is a worthless choice.

| Stage | A | B | C |
|---|---|---|---|
| Opener | Specific callback to one prompt | Playful challenge | Shared-interest bridge |
| Early rapport | Build on her thread | Redirect to new topic | Light escalation |
| Established | Deepen current thread | Introduce logistics | Callback to earlier |
| Ready to ask | Direct ask, specific plan | Soft ask, floated idea | Availability probe |
| Stalling | Low-stakes revival | Direct re-engage | (offer to let it go) |

**Generate them in separate calls, not one call producing three.** A small model asked for three options produces three near-identical sentences. Separate calls with different strategy instructions and different seeds produce genuinely different moves.

### 6.2 Prompt structure for a small model

Order matters more at 2–4B than at frontier scale. Put constraints last — small models weight recent tokens heavily.

```
[Her profile — structured, terse]
[Last 6 messages, labeled HER / ME]
[5 retrieved examples of how you replied in similar spots]  ← §4.3
[Strategy for this variant: one sentence]
[Hard constraints: max N words. Only reference facts listed above. One idea.]
```

**Keep total context under ~1,500 tokens.** Small models degrade sharply with long context, and prefill dominates latency. Six messages of history is plenty; the whole conversation is not better.

### 6.3 Stage classification without a model

Don't spend inference on this. Classify with rules over measurable signals:

```
messageCount == 0                                    → OPENER
messageCount < 6                                     → EARLY_RAPPORT
daysSinceHerLast > 3                                 → STALLING
daysSinceHerLast > 7                                 → DEAD
herMedianWords declining >40% over last 4 messages   → STALLING
messageCount >= 8 && herQuestionRate > 0.3
  && logisticsMentioned == false                     → READY_TO_ASK
otherwise                                            → ESTABLISHED
```

Free, instant, deterministic, debuggable. **`READY_TO_ASK` is the most valuable output in the app** — most matches die because nobody moves, not because the messages were bad. Surface it as a banner, not a subtle variant label.

### 6.4 Distinctness enforcement

After generating all three, compute pairwise Jaccard similarity on content words. If any pair exceeds **0.6**, regenerate the later one at higher temperature with an explicit instruction to take a different angle. Cap at one retry.

Without this, small-model variants collapse into synonyms of each other and the whole three-option premise is fake.

### 6.5 The no-inference path

For openers specifically, a template path needs no model at all: take one of your historical opener patterns, slot in a specific detail from her profile, run it through the voice compiler.

Free, instant, and frequently as good as generated output — openers are mostly "notice one specific thing and ask about it," which is a template. Always offer this as a fourth option, and make it the primary path on the 1B tier where generation quality is weakest.

---

## 7. Quality gates

### 7.1 Style verification

After the voice compiler runs, re-measure §4.1 features on the output and compare to profile. The compiler should make most of these pass by construction; the check exists to catch cases it missed.

Remaining failures (length, forbidden phrasing that survived) trigger regeneration. **Cap at 2 retries, then ship the best candidate with a visible off-voice badge.** An uncapped loop on a 4B model is minutes of spinning, not cents of spend.

### 7.2 Fact-grounding — zero tolerance

Extract every proper noun and specific claim from the draft. Verify each appears in the captured context.

Do this **with string matching plus a stopword list, not another model call.** Tokenize the draft, drop stopwords and your own known vocabulary, and check every remaining content noun against the context. Cheap, deterministic, no inference.

Any ungrounded specific fails the draft outright. Regenerate with the offending term explicitly forbidden; on second failure, ship no draft for that variant. **A missing option is strictly better than a hallucinated one** — you can't unsend a message about a dog she doesn't have, and it signals you weren't reading.

This gate matters more here than it would with a frontier model. Small models invent plausible details at meaningfully higher rates.

### 7.3 Escalation sanity

Reject drafts proposing to meet before `ESTABLISHED`, or referencing anything sexual before she has. Not a moral filter — a calibration one. Mistimed escalation is the most common way a good conversation dies.

---

## 8. The outcome loop

```kotlin
data class DraftOutcome(
    val draftId: String,
    val variantStrategy: Strategy,
    val action: Action,            // SENT_CLEAN, SENT_EDITED, DISCARDED
    val editDistance: Int?,
    val finalText: String?,        // your edit — the best training signal available
    val gotReply: Boolean?,        // resolved on next capture of that conversation
    val replyLatencyMs: Long?
)
```

Two payoffs:

1. **Edited drafts are your best voice-profile input.** The delta between generated and sent is a direct correction. Fold `finalText` into the profile at double weight, and into the BM25 retrieval index immediately — your corrections become future few-shot examples. This is how a small model gets better at sounding like you over time without any fine-tuning.
2. **Strategy win rates.** After 100 drafts you know whether playful openers or specific-callback openers actually get replies *for you*. Reorder variants by measured reply rate.

---

## 9. Conversation health

Passive, free, no inference:

- **Cooling detection** — her median message length dropping, reply latency rising, question reciprocity going one-sided. Flag before it's dead.
- **Ball-in-your-court list** — conversations where she replied last, sorted by elapsed time. Cheapest high-value screen in the app.
- **Stale cutoff** — past 7 days with no reply, archive and suggest closure over a fourth revival.

---

## 10. Privacy and data

| Data | Handling |
|---|---|
| Screenshots | OCR'd on-device, image discarded immediately, never stored |
| Her photos | Never processed beyond visible-text OCR |
| Conversation text | **Never leaves the device.** No API, no network |
| Local DB | Room + SQLCipher, key in Android Keystore |
| Telemetry | None. No analytics, no crash reporting, no backend |

Ship a **"What leaves your device"** screen that says: *nothing*. With on-device inference this is literally true, which is the version worth having.

Per-conversation **exclude** toggle that halts all capture and processing for that match.

---

## 11. Data model

```kotlin
@Entity data class Conversation(
    @PrimaryKey val id: String,
    val platform: Platform,              // HINGE, TINDER
    val matchPseudonym: String,
    val profileJson: String?,
    val stage: ConversationStage,
    val lastCapturedAt: Long,
    val lastTheirMessageAt: Long?,
    val lastMyMessageAt: Long?,
    val excluded: Boolean
)

@Entity data class Message(
    @PrimaryKey val id: String,
    val conversationId: String,
    val sender: Sender,                  // ME, THEM
    val text: String,
    val sentAt: Long?,                   // often unknown from OCR
    val sequence: Int,                   // reliable when timestamps aren't
    val attributionConfidence: Float     // < 0.8 excluded from voice profile
)

@Entity data class Draft(
    @PrimaryKey val id: String,
    val conversationId: String,
    val strategy: Strategy,
    val rawModelOutput: String,          // pre-compiler — for debugging voice issues
    val text: String,                    // post-compiler
    val transformsApplied: String,
    val gateResultsJson: String,
    val modelTier: ModelTier,
    val inferenceMs: Long,
    val createdAt: Long
)
```

Store `rawModelOutput` alongside the compiled text. When a draft sounds wrong you need to know whether the model or the compiler caused it.

---

## 12. Resource budget

No dollar cost. The budgets that matter are time, memory, and battery.

| Operation | Budget |
|---|---|
| Model init (Application.onCreate) | < 3s, off the main thread |
| Resident memory, E2B tier | < 3 GB |
| OCR per screenshot | < 500 ms |
| BM25 retrieval over 5k messages | < 50 ms |
| Prefill, ~1,500 tokens | < 2s |
| Generation, ~40 tokens × 3 variants | < 8s total |
| Voice compiler + gates | < 50 ms |
| **Screenshot → three drafts** | **< 12s** |

Twelve seconds is acceptable here — you're composing, not gaming. But show streaming output so it doesn't feel frozen.

**Battery:** sustained inference is thermally expensive. Cap at 20 generations per hour and surface a gentle warning past that; a hot phone that dies at 6pm is a worse outcome than a missed draft.

---

## 13. Error handling

| Failure | Behavior |
|---|---|
| Model file missing / corrupt | Re-download prompt; fall back to template path (§6.5) |
| OOM during inference | Drop one model tier permanently, notify, retry |
| OCR returns no text | Ask for a clearer screenshot; never guess |
| Attribution ambiguous | Show both interpretations, ask which is you, remember per platform |
| Style gate fails twice | Ship best candidate with off-voice badge |
| Fact-grounding fails twice | Ship no draft for that variant |
| Voice profile < 50 messages | Generic baseline + persistent calibrating banner |
| Accessibility tree unrecognized | Auto-fall back to share-sheet capture, notify |
| WebGPU unavailable in browser | Extension degrades to template-only path |

---

## 14. Testing

1. **Attribution accuracy.** 50 hand-labeled screenshots, both apps. Target > 98% — poisons everything downstream.
2. **Voice compiler correctness.** Unit tests per transform in §4.4. Pure functions, trivially testable, and they carry most of the voice quality.
3. **Voice profile stability.** Build from two disjoint halves of your corpus; features should agree within 15%.
4. **Fact-grounding.** Inject drafts referencing invented details. **100% rejection required** — zero tolerance.
5. **Distinctness.** Assert no two shipped variants exceed 0.6 Jaccard.
6. **Draft-only enforcement.** Static check that fails the build if `performAction` appears anywhere.
7. **Memory ceiling.** Run 50 consecutive generations; assert no OOM and no monotonic memory growth.
8. **Tier fallback.** Simulate OOM on E4B; assert clean demotion to E2B with no data loss.
9. **Retrieval quality.** Hand-label 20 situations with the "right" past message; assert BM25 surfaces it in the top 5.

---

## 15. Build order

| M | Deliverable | Gate |
|---|---|---|
| **M0** | Android shell, share-sheet intake, ML Kit OCR, bounding-box attribution | §14.1 > 98% |
| **M1** | Voice profile extraction + onboarding confirmation | §14.3 passes |
| **M2** | **Voice compiler + template opener path (§6.5) — no model yet** | §14.2 passes; usable openers with zero inference |
| **M3** | MediaPipe integration, tiering, Gemma 3n, single draft | Generation under budget on your device |
| **M4** | Three strategic variants + distinctness + grounding gate | §14.4 at 100%, §14.5 passes |
| **M5** | Outcome loop feeding retrieval index + stats | Corrections appear as future few-shot examples |
| **M6** | Conversation health + ball-in-your-court | |
| **M7** | Chrome extension + WebLLM, Tinder web | Parity with mobile |
| **M8** | AccessibilityService — only if the share sheet proves annoying enough | §14.6 passes |

**M2 before M3 is deliberate.** The voice compiler and template path give you a working, useful app with no model at all. If the drafts at M4 aren't better than the templates at M2, you've learned something important cheaply instead of expensively.

---

## 16. Traps

1. **Reversed bubble attribution.** §4.2. Voice profile built from her messages. Subtle, poisonous, hard to diagnose.
2. **Describing your voice instead of demonstrating it.** At 2–4B, five real examples beat any paragraph of instruction. §4.3.
3. **Asking for three variants in one call.** Produces three synonyms. Separate calls, separate strategies, separate seeds.
4. **Spending inference on what a regex does better.** Capitalization, punctuation, length, banned tokens — all compiler work. §4.4.
5. **Long context.** Small models degrade sharply past ~1,500 tokens and prefill dominates latency. Six messages, not the whole thread.
6. **Assuming GPU beats CPU.** Reported to be backwards on Gemma 3n. Benchmark on your actual device.
7. **Lazy model init.** Multi-second stall on first use reads as a broken app. Init in `Application.onCreate`.
8. **Hardcoding one model.** Tier by measured RAM or you either OOM or leave quality unused. §3.3.
9. **Overfitting a small corpus.** 50-message minimum.
10. **Hallucinated specifics.** §7.2 is zero-tolerance — the one failure you cannot take back.
11. **Any `performAction` call.** §2.1. Turns a drafting tool into a bannable bot.
12. **Trusting OCR timestamps.** Both apps show relative times that OCR poorly. Order by `sequence`.
13. **Thermal throttling.** Sustained generation heats the device and slows subsequent runs. Rate-limit.

---

## 17. Non-goals

- Auto-sending, auto-swiping, auto-liking — anything operating the account
- Any network call at inference time
- Any paid service, free tier, or account
- Profile optimization, photo ranking
- Play Store distribution (accessibility permission alone disqualifies it)
- Hinge on desktop — no web version exists
- **Being a different person.** The voice engine exists to sound like you at your most articulate. A persona that doesn't survive the first date is a cost, not a feature — which is exactly why §4 measures fidelity to *your* corpus rather than to some notion of good messaging.

---

## 18. Open decisions

1. **The quality escape hatch.** If local drafts prove too weak, the options are a free-tier API (fast, better, but uploads her messages to be trained on) or accepting the template path as primary. Decide only after M4, with real output in front of you — not now, in the abstract.
2. **Your actual device tier.** Confirm free RAM before committing to E4B. If it's a mid-tier or older device, E2B is the ceiling and §6.5's template path carries proportionally more of the product.
3. **Onboarding corpus.** Screenshotting 15–20 conversations is tedious. A one-time SMS read would give a far larger corpus, but it's a heavy permission and texts are a different register than dating apps. Probably not — decide deliberately.
4. **Draft count.** Three is a guess, and each costs real seconds on-device. Measure how often you pick the third before keeping it.
