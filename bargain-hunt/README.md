# The Bargain Hunt

A daily screen for stocks and ETFs that just took a double-digit hit, checked
against value criteria, with a short verdict and the reasoning underneath.

Live at **https://brettadams0.github.io/bargain-hunt/**

---

## How it works

The hunt does not run on the phone. It runs once each morning as a **scheduled
Claude Code session**, which searches the web, writes the brief, and publishes
it. The app is a **reader**: it fetches that file, caches it, and renders it.

```
  Scheduled Claude Code session (Tue-Sat, 06:30 ET)
        │  reads hunt/PROMPT.md + hunt/watchlist.json
        │  searches the web, judges each candidate
        ▼
  brief.json ──push──▶ branch: bargain-hunt-data
        │
        │  raw.githubusercontent.com (CORS: *)
        ▼
  The PWA  ──▶ IndexedDB cache ──▶ tearsheets
```

Three things follow from that, and they are the whole reason for this shape:

- **It costs nothing to run.** No API key, no per-token billing. The morning
  session uses the Claude subscription it already runs under.
- **There is no secret on the phone.** Nothing to leak, nothing to rotate.
  This is stronger than the original design, which put an API key in Android's
  Keystore.
- **The daily refresh is actually reliable.** It is a real scheduled job, not
  the browser's `periodicsync`, which Chrome gates behind engagement
  heuristics and paces however it likes.

## Why this is a PWA and not the Android app

The spec asked for a signed, sideloadable APK, with §21 as the fallback if the
toolchain was unavailable. It was: **`dl.google.com` is blocked by the network
egress policy** (403 at the proxy), and it serves both the Android SDK
command-line tools and Google's Maven repo — `maven.google.com` only 301s to
it. No SDK, no AGP, no Compose, so `assembleRelease` cannot run.

The original §21 warning was that a PWA's daily refresh would be degraded.
Publishing the brief from a scheduled job removes that problem: the brief is
produced on a real schedule regardless of what the phone or browser is doing.
`periodicsync` is still registered, but now it only pulls an already-published
file, so it is a nice-to-have rather than the mechanism.

## Setup

**1. Create the data branch.** An orphan branch holding only `brief.json`, so
the daily commits stay out of `main`'s history:

```sh
git checkout --orphan bargain-hunt-data
git rm -rf .
echo '{}' > brief.json && git add brief.json
git commit -m "Start the data branch"
git push -u origin bargain-hunt-data
git checkout main
```

**2. Schedule the morning run.** A Claude Code Routine, **Tuesday to Saturday
at 06:30 Eastern** (`30 10 * * 2-6` UTC). Each run reports the previous trading
session's close, so five runs cover all five sessions with nothing duplicated —
a Sunday or Monday run would only re-report Friday's close.

The Routine fetches `hunt/PROMPT.md` and `hunt/watchlist.json` over HTTPS rather
than cloning this repo, and clones only the single-file data branch to publish.

**3. Install it on the phone.** Open the link in Chrome, accept **Add to Home
screen**, and launch it from the icon. There is nothing to configure — no key,
no sign-in.

## Changing what it looks for

**Screen settings live on the phone** (Settings tab): drop threshold, minimum
market cap, max forward P/E, dividend payers only, excluded sectors, how many
to show. There is a live preview line so the combination is never a guess.

These apply **instantly**, because the morning run deliberately screens wider
than he needs — 6 candidates at a 10% threshold — and the phone narrows that
to his thresholds locally. Tightening a filter re-filters the list immediately
instead of waiting for tomorrow.

One rule matters in that filter: **an unknown value is never filtered out.**
Only a value that definitely fails a test removes a candidate. "Never invent a
figure" cuts both ways — a P/E the model could not confirm must not quietly
hide a company.

**The watchlist lives on the phone.** He adds and removes tickers in the Watch
tab — no account, no sign-in, and it persists in IndexedDB like the settings do.
Each morning his tickers are matched against the day's screen: any that fell get
the full write-up, and any that did not are marked "no double-digit fall today"
rather than left looking broken.

An earlier version kept the watchlist in `hunt/watchlist.json` and linked to
GitHub's editor. That was wrong — **he does not have a GitHub account**, so the
one control that was supposed to be his was the one he could not touch.

`hunt/watchlist.json` still exists and is still read by the morning run, but it
is now the optional *deep* list: names researched by ticker every morning
whatever the price did, for anyone maintaining the repo. Entries from it appear
in the Watch tab without a remove button, because the phone does not own them.

There is deliberately no write-back from phone to repo. That would need a
credential in a public web app, and GitHub revokes tokens it finds in public
source — so it would break, and it would deserve to.

## What is degraded compared to the spec

- **The stale marker fires at 60 hours, not §10.1's 36.** With a Tuesday-to-
  Saturday schedule there is a deliberate ~60-hour gap from Saturday's run to
  Tuesday's, and over a weekend Friday's close *is* the freshest data there is.
  At 36 hours the warning would cry wolf every Sunday and Monday; at 60 it still
  means "a run was actually missed".
- **No on-demand single-ticker check.** §10.2 had tapping a watchlist row run
  a live check. Without an API key there is no live call, so the Watch tab
  shows that morning's reading instead. Given §24 — his bottleneck is gathering,
  not judging — pre-gathered each morning still solves the actual problem. The
  cost is that a watched name which did not fall gets no tearsheet that day
  unless it is on the deep list.
- **Settings shape the output, not the prompt.** §9 wanted every setting to
  change the prompt. Here the broad screen is fixed and settings filter the
  result. The trade is deliberate: settings became instant instead of
  next-morning.

## Deviations from the spec, and why

| Spec | Built | Why |
|---|---|---|
| App calls the API with an on-device key (§3, §8, §15) | App reads a published `brief.json`; the hunt runs in a scheduled Claude Code session | The requirement was zero additional cost. A Claude subscription does not include API credits — those are billed separately per token — so an on-device key meant a real monthly bill. This removes the cost *and* the secret, and makes the daily refresh reliable. |
| `EncryptedSharedPreferences` for the key (§15) | No key at all | Nothing to protect. |
| Canvas for the range bar (§11) | CSS/DOM | The web equivalent of hand-drawing on Canvas, and it reflows with the container and with system font scaling for free. Same geometry, same degradation rule. |
| `refreshHour` / `refreshMinute` setting (§9) | Removed | The schedule belongs to the scheduled job now, not the phone. Leaving a time picker that changed nothing would be a lie. |

Everything else is as specified: the tearsheet, the 52-week range bar and its
degradation case, the cache-first brief screen, the empty / error / stale
states, the verdict-first layout, the design system, notifications, dark mode,
and font scaling to 200%.

## Layout

```
bargain-hunt/
├── index.html          shell, bottom nav, non-render-blocking font load
├── core.js             IndexedDB, brief fetch, local filtering — shared with the worker
├── app.js              the three screens, tearsheet, range bar
├── sw.js               offline shell cache + background sync + notifications
├── styles.css          design system (§12), light and dark
├── manifest.json       standalone display, 192/512/maskable icons
└── hunt/
    ├── PROMPT.md       the whole job, in plain English — this is the product
    └── watchlist.json  optional deep list, researched by name every morning
```

`hunt/PROMPT.md` is deliberately prose, not code. It is the part worth editing
and it should never require touching a source file. It is also the **only** file
the morning run reads, which is as much about cost as about clarity.

## Keeping the morning run cheap

The run costs quota on the Claude subscription, and search results are by far
the largest part of it — every result page becomes input tokens. `PROMPT.md`
therefore builds in:

- **Two passes.** One broad search for the day's decliners, *then* research only
  the shortlist. Without this the model deep-dives names it later discards.
- **A search budget.** Roughly 12-16 searches, hard ceiling 25, with an explicit
  preference for one quote page carrying many fields over a separate query per
  number. This was learned the hard way: researching field-by-field produced
  contradictory figures *and* burned the budget.
- **Stop-when-confirmed.** No re-verifying a figure already confirmed once.
- **Write, don't print.** The JSON goes straight to the file; printing it into
  the reply first doubles the output cost for no benefit.
- **No repo clone.** Two `curl`s for the instructions, and a `--depth 1
  --single-branch` clone of the data branch, which holds one file.
- **Five runs, not seven.** Tuesday to Saturday covers all five trading
  sessions; Sunday and Monday runs would only re-report Friday's close.

## Testing

Verified in headless Chromium at 412×915 against a mocked publish endpoint:
cache-first render (53 ms cold), the null-`low52` degradation case, `n/a`
metrics rendering as dashes, each filter dimension (threshold, market cap,
sector) narrowing the list, unknown values surviving a filter, the
filtered-to-zero state explaining itself, the watchlist view and its edit link,
a 404 before the first publish, a failed sync leaving the cached brief intact,
the stale banner, dark mode, offline reads, and 200% font scaling with no
clipping or horizontal overflow. A guard asserts the app never calls the
Anthropic API.

Two things could not be tested here and are worth watching on the first real
run: the quality of an actual morning brief, and whether the scheduled session
reliably publishes at the hour you set.
