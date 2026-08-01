# Daily run — instructions for the scheduled session

This is what the scheduled Claude Code session does each morning. It is written
for that session to follow directly.

## Steps

1. Read `bargain-hunt/hunt/PROMPT.md` and `bargain-hunt/hunt/watchlist.json`
   from the `main` branch of `brettadams0/brettadams0.github.io`.

2. Do the work described in `PROMPT.md`, using **web search** for every figure.
   Do not answer from memory. This is the whole job — the value is in having
   already done the reading before he picks up the phone.

3. Build the JSON object exactly as specified at the end of `PROMPT.md`.
   Validate it before publishing:
   - it parses as JSON
   - `brief.candidates` is a non-empty array
   - every candidate has `ticker`, `verdict`, and `causeType`, and each
     `verdict` / `causeType` is one of the allowed values
   - no figure was invented — anything unconfirmed is `null` or `"n/a"`

4. Publish it to the **`bargain-hunt-data`** branch as `brief.json` at the
   repository root. That branch is an orphan holding only this file, so the
   daily commits stay out of `main`'s history:

   ```sh
   git fetch origin bargain-hunt-data
   git checkout bargain-hunt-data      # or: git checkout --orphan bargain-hunt-data && git rm -rf .
   # write brief.json
   git add brief.json
   git commit -m "Brief for $(date -u +%Y-%m-%d)"
   git push origin bargain-hunt-data
   ```

5. **If anything fails, publish nothing and stop.** A stale brief that is
   clearly labelled as stale is much better than a wrong or empty one. The app
   shows the previous brief with its real date and a "this brief is from
   Wednesday" bar, so doing nothing degrades honestly.

## Notes

- The screen is deliberately broad (8 candidates, 10% threshold, $500m floor).
  The phone narrows it to his personal settings locally, so do not pre-filter.
- Keep `note` to 20 words. It is the one line he reads before the tearsheets.
- If the watchlist is empty, omit `watch` or set it to `[]`. That is normal.
