# Cue for Tinder web

The desktop half of [Cue](../README.md). Reads the conversation out of the page,
drafts three replies in the side panel, and never types into Tinder.

**Tinder only.** Hinge has no desktop version — mobile app only, no browser access
— so desktop coverage is Tinder by definition (§3.4). That is not a gap to close.

---

## Install

Unpacked, in developer mode. There is no Chrome Web Store listing, which avoids
the $5 developer fee and keeps the dependency list at zero cost (§3.1).

1. `chrome://extensions` → enable **Developer mode**
2. **Load unpacked** → select this `extension/` directory
3. Open a Tinder conversation, click the Cue icon

The panel will tell you it has no model. That is expected and usable — see below.

## Two manual steps, then it is offline forever

**Your voice profile.** In the Android app: Settings → *Export for browser* →
Copy JSON. In the panel: *Your voice profile* → paste → Import. No backend means
no sync (§3.4); the profile changes slowly, so manual is fine.

Without it, drafts use the generic-casual baseline and read like anybody's
messages. With the profile but without the corpus, they read like your
orthography and somebody else's phrasing — §4.3 is explicit that the retrieved
examples do most of the work, so the phone will stay better than the browser
until the export carries a corpus too. The panel says which state it is in.

**The model.** WebLLM is not vendored here. Its normal install is a CDN script
tag, the extension's CSP forbids remote script, and bundling it would mean adding
a build toolchain to a project whose whole dependency list is meant to stay free
and offline (§3.1). So it is one manual download:

```sh
# From a machine with npm, once:
npm pack @mlc-ai/web-llm
tar -xzf mlc-ai-web-llm-*.tgz
mkdir -p extension/vendor
cp package/lib/index.js extension/vendor/web-llm.js
```

Until that file exists, `createEngine` returns null and the panel degrades to
reading the conversation and telling you why it cannot draft — which is §13's
specified behaviour for a browser without WebGPU, reused.

## What it does and does not do

| | |
|---|---|
| Reads the open conversation | Yes — the DOM directly, no OCR (§5.3) |
| Writes into the message box | **No.** No permission for it, and the content script is scanned to prove it |
| Sends anything to a server | No. There is nothing to send to |
| Stores her messages | No. They go from the page to the panel and are gone when it closes |
| Stores your voice profile | Yes, in `chrome.storage.local` |

`verifyContentScriptReadOnly` in the root Gradle build fails on `execCommand`,
`dispatchEvent`, `KeyboardEvent`, `InputEvent`, `innerHTML`, `.click(` or
`.focus(` appearing in `src/content/`. The content script is the only code with
access to the page, so that is the only place a write could come from (§2.1).

## Tests

```sh
node --test "test/*.test.js"    # 41 tests, no dependencies
```

`src/lib/` is a hand port of `:core:voice` and `:core:draft`, so it can drift from
the Kotlin. `test/voice.test.js` asserts the same behaviours as
`VoiceCompilerTest.kt` and `GatesTest.kt` against the same inputs — that file is
where drift shows up. `GENERIC_COMMON` in `draft.js` is generated from the Kotlin
table rather than retyped; regenerate it instead of editing it.

## What is not verified

The extension has never been loaded into a browser. The selectors in
`read-conversation.js` are written against Tinder's markup conventions rather
than against a live page, and Tinder's class names are generated — so the first
real session will need the selector ladder adjusted. Everything the selectors
feed *into* is covered by the test suite; everything about the page itself is not.
