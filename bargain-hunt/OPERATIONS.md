# Running the hunt

This is the operator's note — for Brett, not for the scheduled session. The
scheduled session reads `hunt/PROMPT.md` and nothing else.

## How the daily brief gets published

There is no server and no API key anywhere. The app is a pure reader: it
fetches one file over HTTPS and renders it.

```
Claude Code Routine (Tue–Sat, 10:30 UTC)
  └─ reads bargain-hunt/hunt/PROMPT.md
     └─ researches, writes brief.json
        └─ pushes to the `bargain-hunt-data` branch
           └─ the phone fetches it from raw.githubusercontent.com
```

`bargain-hunt-data` is an orphan branch. It shares no history with `main`, so
the daily commits never clutter the portfolio's history.

## If the brief stops updating

The app degrades honestly on its own: after 60 hours without a fresh brief it
shows a bar saying which day the current one is from, and keeps rendering it.
Nothing breaks, and nothing is silently presented as today's news. So a failed
run is not an emergency — it just needs picking up.

**The 60-hour threshold is deliberate.** The run is Tuesday to Saturday, so
each brief covers the previous trading session and there is a real ~72-hour gap
from Saturday's run to Tuesday's. A shorter threshold would cry stale every
Sunday, when Friday's close genuinely is the freshest data that exists.

### Publishing by hand

Any Claude Code session with this repo checked out can do the whole job:

> Read `bargain-hunt/hunt/PROMPT.md` and do exactly what it says. Publish
> `brief.json` to the `bargain-hunt-data` branch.

That is the entire fallback. The Routine has no capability the manual path
lacks — it is only a way of not having to remember.

To publish from a clone of the data branch specifically:

```sh
git clone --depth 1 --single-branch --branch bargain-hunt-data \
  "$(git remote get-url origin)" /tmp/bh
```

Using the existing checkout's own remote matters: it carries the session's git
credentials, which a bare `https://github.com/...` URL does not.

## Known issue: the Routine has never published

As of 4 August 2026 the scheduled Routine has fired four times and published
nothing on every one of them, including a deliberate plumbing-only run that
skipped the research entirely and tried to push a one-line marker file.

What is known:

- It is not the git credentials *in this kind of session*. A direct test proved
  a push to `bargain-hunt-data` succeeds — `github.com` URLs are transparently
  rewritten to an authenticated local proxy.
- It was firing in self-bind mode, meaning each run tried to resume an existing
  conversation rather than start its own. That was certainly wrong and is now
  fixed (`create_new_session_on_fire`), but fixing it did not make the plumbing
  check publish, so it was not the whole story.

The leading remaining hypothesis is that a session spawned by a Routine starts
without the repository checkout — and therefore without the authenticated
remote — that an interactive session is given. If so, no prompt wording can fix
it, because the credential simply is not there to use.

**The obstacle to confirming this is observability, not effort.** Nothing
exposes a fired session's transcript, so every diagnosis so far has been
black-box guessing. Completion notifications are now switched on (push and
email), which routes the next run's own account of itself to a human. Read that
before theorising further — it will say more in one line than another blind
probe will.
