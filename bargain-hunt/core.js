/* The Bargain Hunt — shared core.
 *
 * The app is a reader. It does not call the Anthropic API and holds no API
 * key: a scheduled Claude Code session runs the hunt each morning (see
 * hunt/PROMPT.md) and publishes brief.json, and the phone just renders it.
 * That keeps the running cost at zero beyond the Claude subscription that
 * session already uses, and leaves no secret on the device at all.
 *
 * Loaded by the page as a plain script and by the service worker via
 * importScripts(), so state lives in IndexedDB rather than localStorage —
 * a service worker cannot read localStorage.
 */
(() => {
  "use strict";

  const DB_NAME = "bargain-hunt";
  const DB_VERSION = 1;
  const STORE = "kv";

  const K_SETTINGS = "settings";
  const K_BRIEF = "brief";

  // Where the daily brief is published. raw.githubusercontent.com serves it
  // with access-control-allow-origin:*, and the orphan data branch keeps the
  // daily commits out of main's history — this repo is also a portfolio.
  const BRIEF_URL =
    "https://raw.githubusercontent.com/brettadams0/brettadams0.github.io/" +
    "bargain-hunt-data/brief.json";

  // Where to edit the tickers checked each morning. GitHub's file editor
  // works fine on a phone.
  const WATCHLIST_EDIT_URL =
    "https://github.com/brettadams0/brettadams0.github.io/edit/main/" +
    "bargain-hunt/hunt/watchlist.json";

  const FETCH_TIMEOUT_MS = 20000;
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
    M500: { label: "$500 million", short: "$500m", usd: 5e8 },
    B1: { label: "$1 billion", short: "$1bn", usd: 1e9 },
    B5: { label: "$5 billion", short: "$5bn", usd: 5e9 },
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

  async function getBrief() {
    const c = await idbGet(K_BRIEF, null);
    return c && c.brief && Array.isArray(c.brief.candidates) ? c : null;
  }

  /* -------------------------------------------------------- fetch + cache */

  class BriefError extends Error {
    constructor(message, status) {
      super(message);
      this.name = "BriefError";
      this.status = status;
    }
  }

  // Fetch the brief the scheduled session published. No key, no cost.
  async function fetchPublished() {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);

    let res;
    try {
      res = await fetch(BRIEF_URL, { signal: controller.signal, cache: "no-cache" });
    } catch (err) {
      clearTimeout(timer);
      if (err && err.name === "AbortError")
        throw new BriefError("That took too long. Try again.", 0);
      throw new BriefError("Couldn't reach the brief. Check your connection.", 0);
    }
    clearTimeout(timer);

    if (res.status === 404)
      throw new BriefError(
        "No brief has been published yet. The first one arrives after tomorrow morning's run.",
        404
      );
    if (!res.ok) throw new BriefError(`Couldn't load the brief (HTTP ${res.status}).`, res.status);

    let payload;
    try {
      payload = await res.json();
    } catch {
      throw new BriefError("The published brief was unreadable. Your last one is unchanged.", 0);
    }

    const brief = payload && payload.brief;
    if (!brief || !Array.isArray(brief.candidates))
      throw new BriefError("The published brief was unreadable. Your last one is unchanged.", 0);

    const ts = Date.parse(payload.generatedAt);
    return { brief, generatedAtEpochMs: Number.isFinite(ts) ? ts : Date.now() };
  }

  // Cache it only if it is actually newer. A failed sync throws and leaves
  // the cached brief exactly as it was.
  async function syncPublished() {
    const fresh = await fetchPublished();
    const cached = await getBrief();
    if (cached && cached.generatedAtEpochMs >= fresh.generatedAtEpochMs)
      return { changed: false, cached };

    await idbSet(K_BRIEF, {
      brief: fresh.brief,
      generatedAtEpochMs: fresh.generatedAtEpochMs,
      seen: false,
    });
    return { changed: true, cached: await getBrief() };
  }

  /* ------------------------------------------------------- local filtering */

  // The morning run screens broadly on purpose; his personal thresholds are
  // applied here, on the phone, so changing a setting takes effect instantly
  // instead of waiting for tomorrow's run.
  //
  // An unknown value is never silently filtered out — only a value that
  // definitely fails the test is. "Never invent a figure" cuts both ways: a
  // figure the model could not confirm must not quietly hide a candidate.
  function filterCandidates(candidates, s) {
    if (!Array.isArray(candidates)) return [];
    const capUsd = (CAPS[s.minMarketCap] || CAPS.M500).usd;
    const excluded = (s.excludedSectors || []).map((x) => x.toLowerCase());

    const kept = candidates.filter((c) => {
      if (typeof c.dropPct === "number" && Math.abs(c.dropPct) < s.dropThreshold) return false;
      if (typeof c.marketCapUsd === "number" && c.marketCapUsd < capUsd) return false;
      if (s.maxPe && typeof c.fwdPe === "number" && c.fwdPe > s.maxPe) return false;
      if (s.dividendPayersOnly && c.paysDividend === false) return false;
      if (excluded.length && c.sector) {
        const sector = String(c.sector).toLowerCase();
        if (excluded.some((x) => sector.includes(x) || x.includes(sector))) return false;
      }
      return true;
    });

    return kept.slice(0, s.candidateCount);
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

  /* ---------------------------------------------------- background refresh */

  // Used by the service worker's periodic sync. This is a single small GET of
  // an already-published file, so an unpredictable firing costs nothing.
  async function refreshBrief() {
    const settings = await getSettings();
    const result = await syncPublished();
    if (!result.changed) return { ok: false, reason: "unchanged" };

    const brief = result.cached.brief;

    // Count what he will actually see, not what the broad screen returned.
    const shown = filterCandidates(brief.candidates, settings);

    // The highest-signal event the app can produce: a name he already watches
    // has taken a real hit.
    const watchlistHit = (Array.isArray(brief.watch) ? brief.watch : []).find(
      (c) => typeof c.dropPct === "number" && Math.abs(c.dropPct) >= settings.dropThreshold
    );

    return { ok: true, brief, shown, settings, watchlistHit: watchlistHit || null };
  }

  const BH = {
    STALE_AFTER_MS, SECTORS, CAPS, DISCLAIMER, DEFAULTS,
    BRIEF_URL, WATCHLIST_EDIT_URL, K_BRIEF,
    idbGet, idbSet,
    getSettings, putSettings, getBrief,
    fetchPublished, syncPublished, filterCandidates, previewLine,
    refreshBrief, BriefError,
  };

  if (typeof self !== "undefined") self.BH = BH;
})();
