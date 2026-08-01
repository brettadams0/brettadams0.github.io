/* The Bargain Hunt — shared core.
 *
 * Loaded by the page as a plain script and by the service worker via
 * importScripts(), so state lives in IndexedDB rather than localStorage:
 * a service worker cannot read localStorage, and without shared state the
 * background refresh could not run.
 */
(() => {
  "use strict";

  const DB_NAME = "bargain-hunt";
  const DB_VERSION = 1;
  const STORE = "kv";

  const K_SETTINGS = "settings";
  const K_KEY = "apiKey";
  const K_BRIEF = "brief";
  const K_WATCH_META = "watchMeta";

  const API_URL = "https://api.anthropic.com/v1/messages";
  const MODEL = "claude-sonnet-5";
  // Dynamic-filtering web search. Supported on Sonnet 5, and it filters
  // result pages before they reach the context window — which matters when
  // the model reads a lot of market pages in one run.
  const SEARCH_TOOL = "web_search_20260209";
  const CALL_TIMEOUT_MS = 150000;
  const STALE_AFTER_MS = 36 * 60 * 60 * 1000;

  const SECTORS = [
    "Biotech",
    "Crypto & digital assets",
    "China-domiciled",
    "Airlines",
    "Energy",
    "Banks",
  ];

  const CAPS = {
    M500: { label: "$500 million", short: "$500m" },
    B1: { label: "$1 billion", short: "$1bn" },
    B5: { label: "$5 billion", short: "$5bn" },
  };

  const DISCLAIMER =
    "Research organized for you — not investment advice. Every verdict is a " +
    "starting point for your own judgment, and figures come from live web " +
    "sources that can be delayed or wrong. Check anything you'd act on.";

  const DEFAULTS = {
    dropThreshold: 10,
    minMarketCap: "M500",
    maxPe: null,
    dividendPayersOnly: false,
    excludedSectors: [],
    candidateCount: 3,
    watchlist: [],
    refreshHour: 6,
    refreshMinute: 30,
    notifyOnNewBrief: true,
    notifyOnWatchlistDrop: true,
  };

  /* ------------------------------------------------------------ IndexedDB */

  let dbPromise = null;

  function openDb() {
    if (dbPromise) return dbPromise;
    dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
    return dbPromise;
  }

  async function idbGet(key, fallback) {
    try {
      const db = await openDb();
      return await new Promise((resolve, reject) => {
        const tx = db.transaction(STORE, "readonly");
        const req = tx.objectStore(STORE).get(key);
        req.onsuccess = () => resolve(req.result === undefined ? fallback : req.result);
        req.onerror = () => reject(req.error);
      });
    } catch {
      return fallback;
    }
  }

  async function idbSet(key, value) {
    try {
      const db = await openDb();
      await new Promise((resolve, reject) => {
        const tx = db.transaction(STORE, "readwrite");
        tx.objectStore(STORE).put(value, key);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
      });
    } catch {
      /* private mode / quota — the session still works in memory */
    }
  }

  const getSettings = async () => Object.assign({}, DEFAULTS, await idbGet(K_SETTINGS, {}));
  const putSettings = (s) => idbSet(K_SETTINGS, s);
  const getApiKey = async () => (await idbGet(K_KEY, "")) || "";
  const putApiKey = (k) => idbSet(K_KEY, k || "");
  const getWatchMeta = () => idbGet(K_WATCH_META, {});
  const putWatchMeta = (m) => idbSet(K_WATCH_META, m);

  async function getBrief() {
    const c = await idbGet(K_BRIEF, null);
    return c && c.brief && Array.isArray(c.brief.candidates) ? c : null;
  }

  function putBrief(brief, seen) {
    return idbSet(K_BRIEF, {
      brief,
      generatedAtEpochMs: Date.now(),
      seen: !!seen,
    });
  }

  /* ------------------------------------------------- the prompt (spec §7) */

  const JSON_CONTRACT = `Reply with ONLY a JSON object. No preamble, no explanation, no markdown code fences.

{"asOf":"today's date","note":"<=20 words on what is driving drops right now",
"candidates":[{
"ticker":"","name":"","dropPct":-15.8,"dropWindow":"one day",
"price":22.73,"prevClose":26.39,"low52":21.90,"high52":45.20,
"verdict":"real bargain",
"business":"<=14 words on what it actually does",
"cause":"<=30 words on exactly what caused the fall",
"causeType":"one-off",
"metrics":[{"label":"Fwd P/E","value":"8.3x"},{"label":"P/B","value":"1.1x"},
           {"label":"Yield","value":"2.4%"},{"label":"Free cash flow","value":"Negative"}],
"right":"<=18 words on the one thing that must go right",
"wrong":"<=18 words on the one thing that could go wrong"}]}

verdict must be exactly one of: "real bargain", "wait and see", "value trap".
causeType must be exactly one of: "one-off", "fixable", "permanent".
Prices are plain numbers, never strings. Use null for any price you cannot confirm
and "n/a" for any metric you cannot confirm. Never invent a figure.`;

  function buildHuntPrompt(s) {
    const extras = [
      s.excludedSectors.length ? `Skip anything in these sectors: ${s.excludedSectors.join(", ")}.` : "",
      s.dividendPayersOnly ? "Only include companies that currently pay a dividend." : "",
      s.maxPe ? `Only include companies with a forward P/E under ${s.maxPe}.` : "",
    ]
      .filter(Boolean)
      .join("\n");

    return `Find bargain candidates. Search the web for current data — do not answer from memory.

Look for stocks or ETFs that have fallen ${s.dropThreshold}% or more in a single day
or over one week, within the last 7 days.

Then discard the junk. Skip anything trading under $1 a share, anything below
${CAPS[s.minMarketCap].label} market cap, shell companies, companies listed within the last
year, and biotechs whose value rests on a single drug trial.
${extras}

From what survives, return the ${s.candidateCount} most interesting, favouring those
that are also cheap on the numbers — low forward P/E and low price-to-book.

For each one, judge whether the fall is an overreaction or a genuine impairment. The
distinction that matters is whether the cause is a one-off event, a fixable problem
with a credible timeline, or a permanent change to the business.

${JSON_CONTRACT}`;
  }

  // Deliberately does NOT apply the exclusion filters — if he explicitly asks
  // about a stock, answer about that stock (spec §7).
  function buildCheckPrompt(ticker) {
    return `Look up ${ticker} right now. Search the web for current data — do not answer from memory.

Report on ${ticker} whatever its size, sector, or recent price action. Do not filter it out
and do not substitute a different company.

Judge whether its current price reflects an overreaction or a genuine impairment. The
distinction that matters is whether the cause is a one-off event, a fixable problem
with a credible timeline, or a permanent change to the business.

Return exactly one candidate.

${JSON_CONTRACT}`;
  }

  function previewLine(s) {
    const bits = [`falls of ${s.dropThreshold}%+ in a day or week`];
    bits.push(`above ${CAPS[s.minMarketCap].short} market cap`);
    if (s.maxPe) bits.push(`forward P/E under ${s.maxPe}`);
    if (s.dividendPayersOnly) bits.push("dividend payers only");
    if (s.excludedSectors.length)
      bits.push(`excluding ${s.excludedSectors.map((x) => x.toLowerCase()).join(", ")}`);
    return `${bits.join(", ")}. Top ${s.candidateCount}.`;
  }

  /* ------------------------------------------------- the API call (spec §8) */

  class ApiError extends Error {
    constructor(message, status) {
      super(message);
      this.name = "ApiError";
      this.status = status;
    }
  }

  async function callAnthropic(prompt, apiKey) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), CALL_TIMEOUT_MS);

    let res;
    try {
      res = await fetch(API_URL, {
        method: "POST",
        signal: controller.signal,
        headers: {
          "content-type": "application/json",
          "x-api-key": apiKey,
          "anthropic-version": "2023-06-01",
          "anthropic-dangerous-direct-browser-access": "true",
        },
        body: JSON.stringify({
          model: MODEL,
          // Sonnet 5 runs adaptive thinking by default, and max_tokens caps
          // thinking and response text together — 3000 truncates the JSON
          // once search results are in context.
          max_tokens: 8000,
          messages: [{ role: "user", content: prompt }],
          tools: [{ type: SEARCH_TOOL, name: "web_search" }],
        }),
      });
    } catch (err) {
      clearTimeout(timer);
      if (err && err.name === "AbortError")
        throw new ApiError("That took too long and timed out. Try again.", 0);
      throw new ApiError("Couldn't reach the network. Check your connection.", 0);
    }
    clearTimeout(timer);

    if (!res.ok) {
      // Never surface the raw body — it can echo request material back.
      if (res.status === 401 || res.status === 403)
        throw new ApiError("That key was rejected. Check it in the Anthropic console.", res.status);
      if (res.status === 429)
        throw new ApiError("Rate limited by the API. Try again in a few minutes.", 429);
      if (res.status >= 500)
        throw new ApiError("The API had a server error. Try again shortly.", res.status);
      throw new ApiError(`The API refused the request (HTTP ${res.status}).`, res.status);
    }
    return res.json();
  }

  // With web search on, `content` is a mixed array containing server_tool_use
  // and web_search_tool_result blocks alongside text. Never index positionally.
  function extractText(body) {
    const blocks = body && Array.isArray(body.content) ? body.content : [];
    return blocks
      .filter((b) => b && b.type === "text")
      .map((b) => b.text || "")
      .join("\n")
      .trim();
  }

  // The model occasionally wraps the object in fences despite instructions.
  // Slice, don't regex.
  function parseBrief(text) {
    const start = text.indexOf("{");
    const end = text.lastIndexOf("}");
    if (start === -1 || end === -1 || end <= start) throw new Error("no JSON object in reply");
    const data = JSON.parse(text.slice(start, end + 1));
    if (!data || !Array.isArray(data.candidates)) throw new Error("missing candidates array");
    return data;
  }

  async function runPrompt(prompt, apiKey) {
    const body = await callAnthropic(prompt, apiKey);
    try {
      return parseBrief(extractText(body));
    } catch {
      // One retry on malformed JSON, then surface the error state. Do not
      // retry on 401 or 429 — callAnthropic throws those before we get here.
      const retry = await callAnthropic(
        prompt + "\n\nYour previous reply was not valid JSON. Reply with the JSON object only.",
        apiKey
      );
      try {
        return parseBrief(extractText(retry));
      } catch {
        throw new ApiError(
          "The reply came back in a shape the app couldn't read. Your last brief is unchanged.",
          0
        );
      }
    }
  }

  /* ---------------------------------------------------- background refresh */

  // Shared by the periodic-sync handler in the service worker. Never clears a
  // good cached brief because a refresh failed.
  async function refreshBrief() {
    const apiKey = await getApiKey();
    if (!apiKey) return { ok: false, reason: "no-key" };

    const settings = await getSettings();
    const brief = await runPrompt(buildHuntPrompt(settings), apiKey);
    await putBrief(brief, false);

    const watched = (settings.watchlist || []).map((t) => t.toUpperCase());
    const hit = brief.candidates.find((c) =>
      watched.includes(String(c.ticker || "").toUpperCase())
    );

    return { ok: true, brief, settings, watchlistHit: hit || null };
  }

  const BH = {
    DB_NAME, STORE, MODEL, SEARCH_TOOL, STALE_AFTER_MS,
    SECTORS, CAPS, DISCLAIMER, DEFAULTS,
    getSettings, putSettings,
    getApiKey, putApiKey,
    getBrief, putBrief, idbSet, idbGet, K_BRIEF,
    getWatchMeta, putWatchMeta,
    buildHuntPrompt, buildCheckPrompt, previewLine,
    runPrompt, refreshBrief, ApiError,
  };

  if (typeof self !== "undefined") self.BH = BH;
})();
