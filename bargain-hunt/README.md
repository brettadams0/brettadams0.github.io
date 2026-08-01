# The Bargain Hunt

A daily screen for stocks and ETFs that just took a double-digit hit, checked
against value criteria, with a short verdict and the reasoning underneath.

Live at **https://brettadams0.github.io/bargain-hunt/**

---

## Read this first: this is the PWA, not the Android app

The specification asked for a signed, sideloadable Android APK, and §21 said
that if the session could not get a working JDK + Android SDK + Gradle, it
should build a PWA instead and say clearly that the daily refresh is degraded.
That is what happened.

The build environment has JDK 21 and Gradle 8.14.3, but **`dl.google.com` is
blocked by the network egress policy** (HTTP 403 at the proxy). That host
serves both the Android SDK command-line tools and Google's Maven repository —
`maven.google.com` is only a 301 redirect to it. Without them there is no
Android SDK, no AGP, no androidx, and no Compose, so `assembleRelease` cannot
run and no APK can be produced. Rather than hand over an Android source tree
that has never once been compiled, this is the PWA fallback.

**The Kotlin app remains the better long-term answer**, for the reason §3 gave:
`WorkManager` gives a genuinely reliable daily job and this does not. If you
want it, the spec is unchanged and the work needs a machine that can reach
`dl.google.com`.

### What is degraded, specifically

**The daily refresh is best-effort, and that is the real cost of this path.**

The app registers a `periodicsync` handler for the daily hunt. Chrome gates
`periodicsync` behind its own engagement heuristics and decides the actual
cadence itself — the 6:30 am you pick in Settings is a target, not a schedule.
In practice the brief will usually be waiting for you and sometimes will not.
It never fires at all unless the app is installed to the home screen, and iOS
Safari does not implement `periodicsync` in any form.

When background refresh has not run, opening the app shows the last brief with
its real timestamp, plus a "this brief is from Wednesday" bar once it is more
than 36 hours old. Pull down to run it live; that takes under a minute. Nothing
silently pretends to be fresh.

If a guaranteed 6:30 am brief matters more than everything else, it needs
either the native Android app or a server-side cron job. There is no third
option that is honest.

### What is not degraded

Everything else in the spec is here: the data contract, the prompt, the
tearsheet, the 52-week range bar and its degradation case, the cache-first
brief screen, all the empty / error / stale states, the watchlist, the
settings-to-prompt wiring with the live preview line, notifications, dark
mode, and font scaling to 200%.

---

## What this costs, and why it is not your Claude Pro plan

**A Claude Pro subscription does not pay for this.** Pro covers claude.ai and
Claude Code. This app calls the Anthropic **API**, which is a separate product
with separate billing: you need an `sk-ant-` key from
[console.anthropic.com](https://console.anthropic.com) with credit on it. API
usage never draws down Pro plan quota, and a Pro subscription grants no API
credits. If the account has no credits, the first run fails with "that key was
rejected".

Rough cost of one daily run on Opus 5, at published rates ($5/M input,
$25/M output, and $10 per 1,000 web searches):

| | per run | per month, daily |
|---|---|---|
| Web searches (~10–15) | $0.10–0.15 | $3–5 |
| Input tokens (search results dominate) | ~$0.12 | ~$4 |
| Output tokens incl. thinking | ~$0.10 | ~$3 |
| **Total** | **~$0.30–0.40** | **~$10–12** |

That is on top of Pro, not included in it. Costs scale with how many searches a
run needs, so a volatile week costs more than a quiet one. Checking a watchlist
ticker is a smaller version of the same call, a few cents each.

**Set a spend limit.** It is the only thing standing between a bug and the
card on file.

## Setup

1. Open the link on the phone in Chrome.
2. Chrome will offer **Add to Home screen** — accept it. This installs it as a
   WebAPK with a real launcher icon, and it is also what makes background
   refresh possible at all.
3. Open it from the home screen. It starts on **Settings** because it cannot do
   anything without a key.
4. Paste an Anthropic API key and tap **Save**. It runs the first hunt straight
   away, so you see the app work within a minute of setup.
5. **Set a spend limit** in the Anthropic console under Settings → Limits.
   There is a link on the Settings screen. One run is a few cents; a bug should
   not be able to run away with your account.

Notifications are requested after the first successful brief, not on first
launch — a permission prompt before anything has happened just gets denied.

## Where the API key lives, and what that does not protect

The key is stored in **IndexedDB**, scoped to the `brettadams0.github.io`
origin. It is sent only to `api.anthropic.com`, over HTTPS, with the
`anthropic-dangerous-direct-browser-access` header that Anthropic requires for
browser-origin calls. It is never logged and never shown in an error message.

**This is weaker than the Android build would have been.** The spec's §15 put
the key in `EncryptedSharedPreferences`, backed by the Android Keystore, in an
app with `allowBackup="false"`. IndexedDB has none of that: it is not encrypted
at rest by the app, and anyone with your unlocked phone — or any script running
on that origin — can read it.

Two things follow from that, and they matter:

- **The spend limit is the real backstop.** Set it.
- **This origin hosts a public personal site.** The app is the only script on
  `/bargain-hunt/`, but the key is readable by anything served from the same
  origin, so treat a key used here as scoped to this purpose and rotate it if
  you ever have doubts. **Forget key** on the Settings screen wipes it.

## Updating

Nothing to do. The service worker re-fetches the shell on each visit and the
new version activates on next launch. This is the one place the PWA is
genuinely better than the sideloaded APK, which would have needed the
`version.json` check in §18 or manual reinstalls twice a year.

## Deviations from the spec, and why

All in the API call (§8), all deliberate:

| Spec | Built | Why |
|---|---|---|
| `claude-sonnet-5` | `claude-opus-5` | Chosen explicitly after weighing cost. The one thing this app exists to do is judge whether a fall is an overreaction or a real impairment, and Opus 5 is markedly better at that call. About $3/month more than Sonnet 5 at a daily run. |
| `web_search_20250305` | `web_search_20260318`, `response_inclusion: "excluded"` | The current search tool. Dynamic filtering means the model filters result pages in code before they reach the context window, which matters when it reads a lot of market pages per run. This is a one-shot request that never sends results back for a second turn, so `excluded` drops raw result blocks the parser would discard anyway, and stops us paying output tokens to echo them. |
| `max_tokens: 3000` | `max_tokens: 16000` | Opus 5 runs adaptive thinking **by default**, and `max_tokens` caps thinking and response text together. At 3000, with search results in context, the JSON truncates mid-object — the malformed-JSON path on nearly every run. Unused headroom costs nothing. |
| 150s call timeout | 240s | Opus 5 at the default `high` effort thinks and searches for longer than Sonnet 5 did. 150s was too tight to be safe. |
| *(not in spec)* | `fallbacks: "default"` | Opus 5's safety classifiers can decline a request, returning HTTP **200** with `stop_reason: "refusal"` and empty content. Server-side fallbacks re-serve a declined request on Anthropic's recommended fallback model inside the same call, so a rare false positive becomes a slightly different brief instead of an error at 6:30am. A refusal that survives the fallback is reported plainly and **not** retried — a retry would be declined too. |
| Canvas for the range bar | CSS/DOM | The web equivalent of hand-drawing on Canvas. It reflows with the container and with system font scaling for free, which a fixed-size canvas does not. Same geometry, same degradation rule. |

The request also downgrades itself rather than dead-ending. If the API returns
a 400 — which would mean it rejected the request *shape*, not the key or the
prompt — the app retries once without the optional extras (`fallbacks`,
`response_inclusion`, and on the older `web_search_20260209`) and remembers
that choice for the rest of the session. This exists because the request shape
could not be validated against the live API from the build environment, and a
rejected optional parameter should never be what stops the brief.

Two smaller ones. Swipe-to-dismiss on the watchlist is a remove button with the
same undo snackbar — swipe has no discoverable affordance on the web and no
platform convention behind it. And shared state lives in IndexedDB rather than
`localStorage`, because a service worker cannot read `localStorage`, and
without shared state the background refresh could not run at all.

## Layout

```
bargain-hunt/
├── index.html     shell, bottom nav, non-render-blocking font load
├── core.js        state (IndexedDB), the prompt, the API call — shared with the worker
├── app.js         the three screens, tearsheet, range bar
├── sw.js          offline shell cache + periodicsync + notifications
├── styles.css     design system (§12), light and dark
└── manifest.json  standalone display, 192/512/maskable icons
```

`core.js` is loaded by the page as a plain script and by the service worker via
`importScripts()`, so the background hunt runs exactly the same prompt and
parser as the foreground one.

## Testing

Verified in headless Chromium at 412×915 against a mocked API, covering the
spec's §23 acceptance criteria that apply to this build: cache-first render
(54 ms cold), the null-`low52` degradation case, `n/a` metrics rendering as
dashes, the settings-to-prompt wiring, bad-key and malformed-JSON handling with
the previous brief left intact, exactly one retry on bad JSON, no retry on 429,
refusals reported without a retry, the outgoing request shape, the 400
downgrade path and that it is remembered, offline reads, the stale banner, dark
mode, and 200% font scaling with no clipping or horizontal overflow.

Three things could not be tested here and are the ones to watch on the phone:

- **The live request shape.** No API key was available in the build
  environment, so the request was never sent to the real API. The downgrade
  path above exists to absorb this, but the first real run is the actual proof.
- **Background refresh surviving a reboot.** Chrome's decision, not the app's.
- **Real end-to-end latency** of an Opus 5 run with web search. The 240s
  timeout and the pacing of the progress labels are estimates.
