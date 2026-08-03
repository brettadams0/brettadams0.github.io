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

  const FETCH_TIMEOUT_MS = 20000;

  // The morning run is Tuesday to Saturday, so each brief covers the previous
  // trading session and there is a deliberate ~60-hour gap from Saturday's run
  // to Tuesday's. Over a weekend, Friday's close IS the freshest data there is,
  // so the spec's 36 hours would raise a false alarm every Sunday and Monday.
  // 60 hours keeps the warning meaning "a run was actually missed".
  const STALE_AFTER_MS = 60 * 60 * 60 * 1000;

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
    // His own tickers, kept on the phone. No account, no sign-in, and he can
    // change them himself — the previous design needed a GitHub login he
    // does not have.
    watchlist: [],
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

    // The data branch starts with an explicit null placeholder. That is "not
    // published yet", which is a different thing from a corrupt file.
    if (!brief)
      throw new BriefError(
        "No brief has been published yet. The first one arrives after tomorrow morning's run.",
        404
      );

    if (!Array.isArray(brief.candidates))
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

  // What the current settings actually do to the brief in hand, and which
  // limits cannot bite because the figure is missing. Without this, a setting
  // that changes nothing is indistinguishable from a setting that is broken.
  function filterStats(candidates, s) {
    const all = Array.isArray(candidates) ? candidates : [];
    const missing = { fwdPe: 0, marketCapUsd: 0, sector: 0 };
    for (const c of all) {
      if (typeof c.fwdPe !== "number") missing.fwdPe += 1;
      if (typeof c.marketCapUsd !== "number") missing.marketCapUsd += 1;
      if (!c.sector) missing.sector += 1;
    }
    return { shown: filterCandidates(all, s).length, total: all.length, missing };
  }

  // Only mention a gap when the matching limit is actually switched on —
  // otherwise it is noise about a filter he is not using.
  function blindSpots(stats, s) {
    const out = [];
    if (s.maxPe && stats.missing.fwdPe)
      out.push(`${stats.missing.fwdPe} with no P/E on file`);
    if (s.minMarketCap !== "M500" && stats.missing.marketCapUsd)
      out.push(`${stats.missing.marketCapUsd} with no market cap`);
    if (s.excludedSectors.length && stats.missing.sector)
      out.push(`${stats.missing.sector} with no sector`);
    return out;
  }

  // What the Watch tab shows: one row per ticker he is watching, carrying
  // today's tearsheet when the morning run covered it.
  //
  // Two sources feed it. `brief.watch` is the deep list the morning run
  // researches by name every day, whatever the price did. The day's screen
  // covers everything else — so a ticker he added on the phone gets a full
  // tearsheet on any morning it actually fell, and is honestly marked quiet
  // otherwise. That keeps the tab useful without needing the phone to write
  // anywhere, which is what would have required an account.
  function watchEntries(brief, s) {
    const byTicker = new Map();
    const add = (c, deep) => {
      const t = String(c && c.ticker ? c.ticker : "").toUpperCase();
      if (!t || byTicker.has(t)) return;
      byTicker.set(t, { ticker: t, candidate: c, deep });
    };

    for (const c of Array.isArray(brief && brief.watch) ? brief.watch : []) add(c, true);

    const screen = new Map();
    for (const c of Array.isArray(brief && brief.candidates) ? brief.candidates : []) {
      screen.set(String(c.ticker || "").toUpperCase(), c);
    }

    for (const raw of s.watchlist || []) {
      const t = String(raw).toUpperCase();
      if (byTicker.has(t)) continue;
      const hit = screen.get(t);
      byTicker.set(t, { ticker: t, candidate: hit || null, deep: false });
    }

    return Array.from(byTicker.values());
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
    // has taken a real hit. Covers both the deep list and anything on his own
    // phone list that turned up in today's screen.
    const watched = new Set((settings.watchlist || []).map((t) => String(t).toUpperCase()));
    const watchlistHit = []
      .concat(Array.isArray(brief.watch) ? brief.watch : [])
      .concat(brief.candidates.filter((c) => watched.has(String(c.ticker || "").toUpperCase())))
      .find((c) => typeof c.dropPct === "number" && Math.abs(c.dropPct) >= settings.dropThreshold);

    return { ok: true, brief, shown, settings, watchlistHit: watchlistHit || null };
  }

  const BH = {
    STALE_AFTER_MS, SECTORS, CAPS, DISCLAIMER, DEFAULTS,
    BRIEF_URL, K_BRIEF,
    idbGet, idbSet,
    getSettings, putSettings, getBrief,
    fetchPublished, syncPublished, filterCandidates, previewLine,
    filterStats, blindSpots, watchEntries,
    refreshBrief, BriefError,
  };

  if (typeof self !== "undefined") self.BH = BH;
})();
