# The hunt prompt

This is the product. It is kept in plain English on purpose so it can be read
and edited without touching any code. The scheduled Claude Code session reads
this file each morning and follows it.

Two things it produces: the daily screen, and a check on each watchlist ticker.

---

## Part 1 — the screen

Search the web for current data. Do not answer from memory.

Look for stocks or ETFs that have fallen **10% or more in a single day or over
one week, within the last 7 days**.

Then discard the junk. Skip anything trading under $1 a share, anything below
$500 million market cap, shell companies, companies listed within the last
year, and biotechs whose value rests on a single drug trial.

From what survives, return the **8 most interesting**, favouring those that are
also cheap on the numbers — low forward P/E and low price-to-book.

Screen broadly here and do not apply any tighter filters. The phone narrows
this list down to his personal thresholds locally, so returning more than he
will see is deliberate — it means changing a setting takes effect instantly
instead of waiting for tomorrow's run.

For each one, judge whether the fall is an overreaction or a genuine
impairment. The distinction that matters is whether the cause is a one-off
event, a fixable problem with a credible timeline, or a permanent change to the
business.

## Part 2 — the watchlist

Read `watchlist.json` in this folder. For **each** ticker listed there, look it
up and produce the same tearsheet, whatever its size, sector, or recent price
action. Do not filter these out and do not substitute a different company —
if he explicitly asked about a stock, answer about that stock.

---

## Accuracy

- Market data more than a day old is worse than useless here. Search for it.
- If you cannot confirm a number, say so. Use `null` for any price you cannot
  confirm and `"n/a"` for any metric you cannot confirm. **Never invent a
  figure.** A wrong P/E is a much worse failure than a missing one.

---

## Output

Write the result to `brief.json` as a single JSON object, nothing else:

```json
{
  "generatedAt": "2026-08-02T10:30:00Z",
  "brief": {
    "asOf": "2 August 2026",
    "note": "<=20 words on what is driving drops right now",
    "candidates": [ ... ],
    "watch": [ ... ]
  }
}
```

`candidates` is Part 1, `watch` is Part 2. Both are arrays of the same shape:

```json
{
  "ticker": "LKQ",
  "name": "LKQ Corporation",
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

- `verdict` must be exactly one of: `"real bargain"`, `"wait and see"`,
  `"value trap"`.
- `causeType` must be exactly one of: `"one-off"`, `"fixable"`, `"permanent"`.
- Prices, `marketCapUsd` and `fwdPe` are plain numbers, never strings.
- `sector`, `marketCapUsd`, `fwdPe` and `paysDividend` are what the phone
  filters on. Get them right or use `null` — a `null` is treated as "unknown"
  and is never silently filtered out.
- `sector` should be one of `Biotech`, `Crypto & digital assets`,
  `China-domiciled`, `Airlines`, `Energy`, `Banks` when one of those fits,
  since those are the sectors he can exclude. Otherwise use a plain
  description.
- `generatedAt` is the actual UTC time of the run, in ISO 8601.
