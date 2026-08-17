# Cue

A drafting assistant for Hinge and Tinder that captures conversation context,
writes replies in your actual voice, and **never sends anything**.

Fully on-device. No backend, no API, no network, $0 forever. The Android app
ships with **no `INTERNET` permission at all** — not a setting, a manifest fact.

Built to [`CUE_SPEC.md`](docs/CUE_SPEC.md) v2.

---

## Status

**173 tests pass. Nothing has run on a phone.**

That split is the honest summary. Everything that decides what a draft says and
how it sounds is pure Kotlin with a test suite; everything that touches a device
is written and unverified, because this machine has no Android SDK.

```sh
gradle test                          # 132 tests, JVM only — no Android SDK needed
gradle verifyArchitecturalInvariants # §2.1 and §2.3, enforced not reviewed
node --test "extension/test/*.test.js"  # 41 tests for the browser port
```

| M | Deliverable | State |
|---|---|---|
| **M0** | Share-sheet intake, OCR, bounding-box attribution | Attribution **done and tested**; OCR bridge written, never run |
| **M1** | Voice profile extraction + onboarding confirmation | Extraction **done and tested**; the confirmation screen is written, never run |
| **M2** | **Voice compiler + template opener path — no model** | **Done and tested.** §14.2 passes |
| **M3** | MediaPipe, tiering, Gemma 3n, single draft | Written. Never compiled, never loaded |
| **M4** | Three variants + distinctness + grounding gate | **Done and tested** against a scripted model. §14.4 at 100%, §14.5 passes |
| **M5** | Outcome loop feeding the retrieval index | **Done and tested** |
| **M6** | Conversation health + ball-in-your-court | **Done and tested** |
| **M7** | Chrome extension + WebLLM | Ported and unit-tested; never loaded in a browser |
| **M8** | AccessibilityService | **Deliberately not built** — see below |

§15 puts M2 before M3 on purpose, and that ordering is why this repository is
useful in its current state: the voice compiler and the template path give a
working app with no model at all. If the generated drafts at M4 turn out to be no
better than the templates at M2, that is now a cheap thing to discover.

---

## The idea, in one paragraph

Chat already writes dating messages. What it cannot do is stop you re-supplying
context every single time — her profile, the conversation, how you talk, what
stage this is at. Cue removes that, and it does the sounding-like-you part
differently: **voice is compiled, not generated.** The model produces content;
your voice is applied afterwards by deterministic transforms measured from your
own sent messages. That is what makes a 2–4B on-device model viable, and it would
be the right architecture with a frontier model too.

```
screenshot ─▶ OCR ─▶ bounding-box attribution ─▶ stitch ─▶ CapturedContext
                                                              │
                          BM25 over your own corpus ──────────┤
                                                              ▼
                                          3 × (prompt ─▶ model ─▶ compiler ─▶ gates)
                                                              │
                                                              ▼
                                              three drafts ─▶ clipboard ─▶ you
```

The clipboard is the last box. There is no arrow out of it.

---

## Draft-only, enforced by the build

§2.1 calls draft-only "an architectural property, not a policy", and §14.6 asks
for a static check. Both exist:

```
$ gradle verifyDraftOnly
> Cue is draft-only by construction (§2.1). These call sites would act on the screen:
  core/model/src/main/kotlin/dev/cue/model/TempProbe.kt:6: performAction — node.performAction(16)

  Cue reads to draft. It never operates the account. Copy-to-clipboard is
  terminal — there is no supported way to add a send path.
```

The ban list is wider than the spec's single name, because `performAction` is not
the only way to drive a UI: `performGlobalAction`, `dispatchGesture`,
`ACTION_SET_TEXT`, `ACTION_PASTE`, `ACTION_CLICK` and `ACTION_IME_ENTER` fail the
build too. Mentions in comments do not — the rule bans calls, and the
architecture is worth explaining in prose.

Two more invariants are checked the same way:

- **`verifyNoInternetPermission`** — no source manifest may *grant* `INTERNET`.
  `:app`'s manifest names it deliberately with `tools:node="remove"`, and
  `verifyDebugManifestOffline` checks the **merged** manifest, because ML Kit and
  MediaPipe both pull in libraries that declare it and manifest merging is silent.
- **`verifyContentScriptReadOnly`** — the extension's content script is the only
  code with page access, so `execCommand`, `dispatchEvent`, `KeyboardEvent`,
  `InputEvent`, `innerHTML`, `.click(` and `.focus(` fail the build there.

Both checks were tested by breaking them on purpose.

---

## What the tests actually cover

§14 asks for nine things. Seven are covered, two need inputs that do not exist
yet.

| §14 | What | State |
|---|---|---|
| 1 | Attribution accuracy > 98% | **21 capture tests**, on 50 *synthetic* screens — see the caveat below |
| 2 | Voice compiler correctness, per transform | **Done.** One test per §4.4 row, plus the stronger claim below |
| 3 | Voice profile stability within 15% | **Done**, over two disjoint halves of a 160-message corpus |
| 4 | Fact grounding, 100% rejection | **Done.** Ten invented details, zero survivors, both in Kotlin and JS |
| 5 | Distinctness ≤ 0.6 Jaccard | **Done** |
| 6 | Draft-only static check | **Done**, and verified to fail when violated |
| 7 | Memory ceiling over 50 generations | **Not done.** Needs a device |
| 8 | Tier fallback on simulated OOM | Demotion logic tested; the OOM path itself needs a device |
| 9 | Retrieval quality, top-5 over 20 situations | **Done.** 20/20 in the top five, 16+ ranked first |

**§7.1's claim is asserted rather than trusted.** The spec says the compiler
"should make most of these pass by construction". `compilerOutputPassesItsOwnVerifier`
asserts the stronger version — on the kinds of output a small model actually
produces, the compiler leaves the verifier *nothing* to find. That is why the
off-voice badge is currently unreachable in practice, and knowing that is the
point of having the test.

---

## Departures from the spec, and why

Each one is argued at its call site; this is the index.

**§6.3's rule order.** The spec lists `messageCount < 6 → EARLY_RAPPORT` above the
silence rules. Read as first-match-wins, a four-message thread she abandoned five
days ago classifies as early rapport, and the app offers three ways to build on a
conversation that is over. Silence is checked first. Same thresholds, different
order.

**§7.2's vocabulary exemption needed a floor.** "Drop stopwords and your own known
vocabulary" is a hole big enough to drive §0 rule 3 through: say "dog" once, ever,
and every future draft may tell a stranger about her dog. The exemption now needs
three uses — the same floor §4.1 puts on a characteristic token, for the same
reason.

**Two carve-outs in the grounding gate.** A word like "answer" is a content noun
that asserts nothing, and §6.1's ask variants have to be able to name a plan —
without `GENERIC_ABSTRACT` and `PROPOSAL_TERMS`, "coffee on thursday" fails
grounding and the gate silently deletes the stage §6.3 calls the most valuable
output in the app. Both lists are narrow: the test for membership is "could a
stranger reading this word learn something about her?" `dog` fails it and is not
there.

**§4.1 gained a `vocabulary` map.** Two rules are unimplementable without it —
§4.4's conditional forbidden tokens ("lol *if absent from your corpus*") and
§7.2's "drop your own known vocabulary". Counts rather than a set, because §4.4's
abbreviation transform needs to know whether you write "u" *more* than "you".

**`terminalPunctuationRate` excludes questions.** §4.4 only ever strips the
optional punctuation ("never `?`"), so counting question marks as evidence of
punctuating pushes a lowercase writer who asks a lot of questions above the 0.3
threshold and leaves every full stop in place.

**ML Kit lines, not blocks.** ML Kit's `TextBlock` groups by proximity and
sometimes fuses two bubbles from different senders — and a block is the unit §4.2
attributes, so a fused block is a coin flip on the highest-stakes decision in the
app. Lines are never larger than a bubble, and the stitcher reassembles them.

**The extension is a hand port, not a shared runtime.** Compiling Kotlin/JS would
add a toolchain to a dependency list §3.1 keeps at "free, offline, unlimited".
`extension/test/voice.test.js` asserts the same behaviours against the same
inputs, which is where drift will show up. The common-English table is generated
from the Kotlin rather than retyped.

**M8 is not built, and neither is the AccessibilityService.** §15 gates it on the
share sheet proving annoying enough to justify a permission that disqualifies
Play Store distribution and breaks whenever either app reorganises its view tree.
That evidence does not exist yet. `verifyDraftOnly` is in place for the day it
does.

---

## What is not proven

Everything above the `:core:*` line.

- **Nothing has been compiled for Android.** No SDK on the build machine. The
  `settings.gradle.kts` guard means `gradle test` works anyway, and it also means
  `:app`, `:core:data` and `:core:inference` have never seen a compiler. Expect
  the first `assembleDebug` to be a bug-fixing session, not a build.
- **§14.1's 50 screenshots are synthetic.** They are generated from the geometry
  both apps use — a bubble hugging one margin, a wrapped message arriving as
  several same-extent lines, headers, timestamps, a composer — with the sender
  recorded as ground truth. That catches a reversed axis, a broken chrome filter
  and every regression in those. It cannot tell you the 15% margin is right,
  because the margin and the fixture came from the same head. **§14.1 is
  exercised, not validated.**
- **No model has ever been loaded.** MediaPipe's session options, the CPU-vs-GPU
  benchmark §3.3 insists on (trap 6 says the answer is counterintuitive), and
  every §12 budget are all unmeasured.
- **The database has never been opened.** SQLCipher's native library load, the
  Keystore-wrapped passphrase, and Room's schema export are written and untried.
- **The extension has never been loaded into a browser**, and Tinder's class names
  are generated, so the selector ladder in `read-conversation.js` will need real
  adjustment on first contact.
- **The attribution-confirmation prompt is missing from the capture path.**
  `CaptureIngest` returns `needsAttributionConfirmation` and the drafts screen
  currently drops it. Onboarding has the swap control (§4.2's gate, which is the
  one that matters); §13's mid-use version does not exist yet.

---

## §18's open decisions

1. **The quality escape hatch** — undecided, correctly. §18 says to decide after
   M4 with real output in front of you, and there is no real output yet.
2. **Device tier** — `ModelFiles.tierForThisDevice()` measures `availMem` rather
   than `totalMem`, so the decision is deferred to the phone. Nothing is
   hardcoded (trap 8).
3. **SMS onboarding** — not built. A heavy permission for a different register of
   writing; the screenshot path is tedious and honest.
4. **Draft count** — still three. `OutcomeLoop.stats` records how often the third
   is used, which is what §18 asks to measure before keeping it.

---

## Layout

```
core/model      pure Kotlin  domain types (§4.1, §5.4, §11)
core/voice      pure Kotlin  profiler + compiler + verifier (§4)
core/capture    pure Kotlin  bounding-box attribution, stitching, profile parsing (§4.2, §5)
core/draft      pure Kotlin  BM25, stages, prompts, gates, pipeline (§4.3, §6, §7, §8, §9)
core/testing    pure Kotlin  synthetic corpora
core/data       Android      Room + SQLCipher (§10, §11)
core/inference  Android      MediaPipe + RAM tiering (§3.2, §3.3)
app             Android      Compose, share-sheet intake, onboarding
extension       JavaScript   Tinder web, MV3, WebLLM (§5.3)
```

The pure-Kotlin modules hold everything that decides draft quality. That is not
tidiness — §16 lists thirteen traps and nine of them are logic bugs a JVM test
catches in milliseconds.

## Privacy

| Data | Handling |
|---|---|
| Screenshots | OCR'd on-device, bitmap recycled before the function returns, never stored |
| Her photos | Never processed beyond text visible in them |
| Conversation text | Never leaves the device. There is no network permission to leave by |
| Local DB | Room + SQLCipher, passphrase wrapped by a non-exportable Keystore key |
| Backups | Disabled, and explicitly excluded from cloud backup and device transfer |
| Telemetry | None. No analytics, no crash reporting, no backend |

She never consented to any of this. On-device inference is the only version of
this app that is defensible, and it happens to also be the free one.

## Licence

MIT — see [LICENSE](LICENSE).
