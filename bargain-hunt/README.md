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

Three, all in the API call (§8), all deliberate:

| Spec | Built | Why |
|---|---|---|
| `web_search_20250305` | `web_search_20260209` | The dynamic-filtering search variant, supported on Sonnet 5. It filters result pages before they reach the context window, which matters when the model reads a lot of market pages per run. The pinned version still works; this one works better. |
| `max_tokens: 3000` | `max_tokens: 8000` | Sonnet 5 runs adaptive thinking **by default**, and `max_tokens` caps thinking and response text together. At 3000, with search results in context, the JSON gets truncated mid-object — which shows up as the malformed-JSON path on nearly every run. |
| Canvas for the range bar | CSS/DOM | The web equivalent of hand-drawing on Canvas. It reflows with the container and with system font scaling for free, which a fixed-size canvas does not. Same geometry, same degradation rule. |

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
(52 ms cold), the null-`low52` degradation case, `n/a` metrics rendering as
dashes, the settings-to-prompt wiring, bad-key and malformed-JSON handling with
the previous brief left intact, exactly one retry on bad JSON, no retry on 429,
offline reads, the stale banner, dark mode, and 200% font scaling with no
clipping or horizontal overflow.

Two criteria could not be tested here and are the ones to watch on the actual
phone: whether background refresh survives a reboot (Chrome's decision, not the
app's), and the real end-to-end latency of a live run with web search.
