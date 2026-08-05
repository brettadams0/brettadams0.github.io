# The hunt

This is the product and the whole job. It is kept in plain English on purpose
so it can be read and edited without touching code. The scheduled session reads
**this file only** — there is nothing else to open.

---

## Work in two passes. Do not skip to pass 2.

**Pass 1 — two broad searches.** Find the day's biggest decliners with a single
query such as *"biggest stock decliners today"* or *"stocks down 10% today
earnings"*. Then spend one more on the Canadian market — *"TSX biggest decliners
today"* — because US decliner lists almost never surface Toronto listings, and
without asking separately the TSX half of his account never gets screened. Take
the headline names and drops. **Do not research anyone yet.**

**Pass 2 — research only the shortlist.** From pass 1, pick the **6** most
interesting that have fallen **10% or more in a single day or over one week,
within the last 7 days**, favouring those also cheap on the numbers — low
forward P/E, low price-to-book.

**Only the NYSE, the Nasdaq and the TSX count.** Those are the three exchanges
he can actually trade on, so a name listed anywhere else is not a candidate —
it is a frustration. Discard it however good it looks, and do not substitute an
over-the-counter or foreign-listed line in a company whose real listing is
elsewhere. If a company trades on one of the three *and* over the counter, use
the proper listing.

Before researching, also discard: anything under $1 a share, anything below $500
million market cap, shell companies, anything listed within the last year, and
biotechs whose value rests on a single drug trial.

Screen broadly and apply **no tighter filters than those**. The phone narrows
the list to his personal thresholds locally, so returning more than he sees is
deliberate — it means changing a setting takes effect instantly rather than
tomorrow.

Then also look up every ticker in `watchlist.json`, whatever its size, sector or
price action. Do not filter those out and do not substitute a different company:
if he explicitly asked about a stock, answer about that stock.

## Searching efficiently

Search results are the expensive part of this job. Keep it to roughly **12–16
searches total**, and never more than 25.

- **One page with many fields beats many pages with one field each.** A single
  quote or profile page usually carries price, previous close, 52-week range,
  market cap, P/E, sector and dividend together. Prefer those over a separate
  query per number.
- **Budget about two searches per name**: one for the numbers, one for the
  cause. Only spend a third if the first two genuinely conflict.
- **Stop when you have enough.** Do not keep searching to raise confidence in a
  figure you have already confirmed once from a credible source.
- Do not re-read files you have already read, and do not re-verify your own
  output by reading it back.

## Judging

For each name, decide whether the fall is an overreaction or a genuine
impairment. The distinction that matters is whether the cause is a **one-off**
event, a **fixable** problem with a credible timeline, or a **permanent** change
to the business.

## Accuracy — the one rule that matters

Market data more than a day old is worse than useless, so search for it rather
than answering from memory.

**Never invent a figure.** Use `null` for any price you cannot confirm and
`"n/a"` for any metric you cannot confirm. A missing number is fine; a wrong one
is not. If two sources disagree materially and a third would cost another
search, publish `null` — the app renders it as a dash and that is the correct
outcome, not a failure.

---

## Output

Write **directly to `brief.json`** — do not print the JSON into the reply first,
that doubles the cost for no benefit. One object, nothing else:

```json
{
  "generatedAt": "2026-08-04T10:35:00Z",
  "brief": {
    "asOf": "4 August 2026",
    "note": "<=20 words on what is driving drops right now",
    "candidates": [ ... ],
    "watch": [ ... ]
  }
}
```

`candidates` is the screen, `watch` is the watchlist. Both are arrays of:

```json
{
  "ticker": "LKQ",
  "name": "LKQ Corporation",
  "exchange": "NASDAQ",
  "dropPct": -15.8,
  "dropWindow": "one day",
  "price": 22.73,
  "prevClose": 26.39,
  "low52": 21.90,
  "high52": 45.20,
  "verdict": "real bargain",
  "business": "<=14 words on what it actually does",
  "cause": "<=30 words on exactly what caused the fall",
  "causeType": "one-off",
  "sector": "Consumer discretionary",
  "marketCapUsd": 5900000000,
  "fwdPe": 8.3,
  "paysDividend": true,
  "metrics": [
    {"label": "Fwd P/E", "value": "8.3x"},
    {"label": "P/B", "value": "1.1x"},
    {"label": "Yield", "value": "2.4%"},
    {"label": "Free cash flow", "value": "Negative"}
  ],
  "right": "<=18 words on the one thing that must go right",
  "wrong": "<=18 words on the one thing that could go wrong"
}
```

Rules:

- `verdict` is exactly one of `"real bargain"`, `"wait and see"`, `"value trap"`.
- `causeType` is exactly one of `"one-off"`, `"fixable"`, `"permanent"`.
- `exchange` is exactly one of `"NYSE"`, `"NASDAQ"`, `"TSX"` — never blank, and
  never anything else. Writing it down is what makes the listing rule checkable
  rather than a promise, so state the one you actually confirmed.
- **TSX names are quoted in Canadian dollars.** Add `"currency": "CAD"` to those
  entries and the app labels the price `C$`; leave it off for New York, where
  the default is US dollars. `price`, `prevClose`, `low52` and `high52` all stay
  in the listing's own currency — do not convert them. `marketCapUsd` is the one
  exception: it is the field his size filter compares against every other
  candidate, so it must be in **US dollars**. Convert it, or leave it `null` if
  you cannot; never put Canadian dollars in it.
- Prices, `marketCapUsd` and `fwdPe` are plain numbers, never strings.
- **`sector`, `marketCapUsd`, `fwdPe` and `paysDividend` are load-bearing.**
  They are the four fields his settings filter on, so a `null` there does not
  just lose a number — it makes his drop-threshold, market-cap, P/E and sector
  controls do nothing for that candidate, because an unknown value is never
  filtered out. Spend one of your searches getting these if you must: a single
  quote or profile page normally carries all four at once. Only use `null` when
  a page you actually looked at did not have it.
- Use one of `Biotech`, `Crypto & digital assets`, `China-domiciled`, `Airlines`,
  `Energy`, `Banks` for `sector` when one fits, since those are exactly what he
  can exclude. Otherwise give the real sector, never a blank.
- `generatedAt` is the real UTC time of the run, ISO 8601.
- If prices are a previous session's close (a Monday run, say), say so in `note`.

## Publishing

Validate before writing: it parses, `candidates` is non-empty, and every entry
has an allowed `verdict` and `causeType`.

Then commit `brief.json` and push. **If anything fails, publish nothing and
stop** — the app keeps showing the previous brief with its real date and a
"this brief is from Saturday" bar, which degrades honestly. A stale brief
clearly labelled stale beats a wrong one.

Finish with two or three sentences: how many candidates, and anything that went
wrong. No summary of the brief itself — he reads that in the app.
