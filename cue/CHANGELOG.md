# Changelog

## 0.1.0 — unreleased

First build against [`CUE_SPEC.md`](docs/CUE_SPEC.md) v2. **173 tests pass and
nothing has run on a phone** — see the [status table](README.md#status).

### The voice engine (§4)

- `VoiceProfiler` measures §4.1's features from a corpus, weighted so §8's
  corrections count double without being stored twice.
- `VoiceCompiler` applies §4.4's transforms deterministically, in an order that
  matters: forbidden phrases before anything measures length, capitalisation last
  so nothing re-capitalises what was lowered.
- `VoicePolicy` holds the thresholds §4.4 and §7.1 must agree on. They were
  briefly duplicated, and the copies disagreed — §7.1 flagged drafts §4.4 had
  deliberately produced, retried twice, and badged a correct draft.
- Forbidden interjections are conditional on your corpus, generalising §4.4's
  "lol (if absent from your corpus)": deleting a word you genuinely use makes the
  draft sound less like you.

### Capture (§4.2, §5)

- `BubbleAttribution` implements §4.2's absolute edge rule plus a clustering pass
  that only ever *raises* confidence, so there is no axis to reverse.
- `ConversationStitcher` reassembles wrapped bubbles and deduplicates overlapping
  screenshots by suffix alignment rather than a hash set, so a genuinely repeated
  message survives.
- `ProfileParser` pairs Hinge's fixed prompt set with her answers, and refuses to
  invent keys for the unlabelled attribute chips OCR cannot distinguish.

### Drafting (§6, §7)

- BM25 over your own corpus, indexed on her message plus your reply so retrieval
  matches situation rather than vocabulary. 20/20 in the top five on §14.9's
  labelled situations.
- Rule-based stage classification, with silence checked before message count.
- Three strategic variants in separate calls with separate seeds, distinctness
  enforced at 0.6 Jaccard, and both hard gates.
- Grounding is zero-tolerance and rejects all ten of §14.4's injected details.
  The vocabulary exemption needs three uses of a word before it counts as your
  idiom — membership alone would let one use of "dog" license every future draft.

### Structure (§2)

- `verifyDraftOnly` fails the build on `performAction` and six other ways to drive
  the UI. Verified by breaking it.
- `verifyNoInternetPermission` on the sources, `verifyDebugManifestOffline` on the
  merged manifest, because a dependency can add the permission silently.
- `verifyContentScriptReadOnly` covers the extension's only page-facing code.

### Written, unverified

`:app`, `:core:data` and `:core:inference` compile in CI — GitHub's runners have
an Android SDK, so Room's KSP processor, Hilt's, and the schema export all run
there. Nothing past the type system is checked: no APK installed, no model
loaded, no database opened, and every §12 budget plus §3.3's CPU-vs-GPU question
still open. The extension has never been loaded into a browser.
