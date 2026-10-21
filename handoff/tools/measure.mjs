#!/usr/bin/env node
/*
 * measure.mjs: screenshots, axe-core, performance budget and GA4 event checks for
 * brettadams0.github.io (works on any URL or local build). Later redesign PRs re-run it
 * and compare with handoff/research/baseline/.
 *
 * Requirements
 *   Node 20+ and the globally installed `playwright` module with its bundled Chromium.
 *   It never needs `playwright install`, writes no package.json and installs no node_modules.
 *   If HTTPS_PROXY is set, Chromium is launched with it (localhost bypassed).
 *
 * axe-core
 *   `--axe <file.js>` injects a local copy. Otherwise axe-core 4.10.2 is downloaded once from
 *   https://cdnjs.cloudflare.com/ajax/libs/axe-core/4.10.2/axe.min.js (Node fetch, falling back
 *   to `curl -fsSL`, which reads HTTPS_PROXY), checked against the SHA-256 pinned below and cached
 *   in os.tmpdir(). It is injected with page.addScriptTag({ content }) in a context created with
 *   bypassCSP: true, so a page's Content-Security-Policy can't block it.
 *
 * Google Analytics
 *   By default every request to googletagmanager.com, google-analytics.com and
 *   analytics.google.com is aborted (context.route), so no hits ever reach Brett's GA4 property.
 *   The inline gtag snippet still defines gtag() and queues calls into window.dataLayer, which is
 *   what --analytics reads. `--allow-ga` lets those requests through; avoid it on the live site.
 *
 * Usage
 *   node handoff/tools/measure.mjs <url> | --site <dir>  [--path /p/]...  <modes>  [options]
 *
 *   Modes (combine freely; at least one):
 *     --shots              PNG screenshots at 390x844, 768x1024, 1440x900: fold + full page
 *     --axe [file.js]      axe-core at 390 and 1440; violations grouped by impact
 *     --perf               throttled mobile (390x844, DPR 2, 150 ms RTT, 1.6 Mbps down,
 *                          750 kbps up, 4x CPU) and unthrottled desktop (1440x900)
 *     --budget [file.json] --perf plus a budget check; exits 1 on failure (default budget below)
 *     --analytics          slow scroll, click every [data-project] a, [data-contact] link and
 *                          résumé link (navigation prevented), print every GA event queued
 *     --dnt                navigator.doNotTrack = '1'; asserts no custom events fire (exit 1 if any)
 *   Options:
 *     --out <dir>          screenshot folder (default: <os.tmpdir()>/measure-shots)
 *     --json <file>        write the full JSON report
 *     --runs <n>           perf runs per profile; the run with the median LCP is reported (default 1)
 *     --expect-sections a,b   section_id values that must fire (default for "/": experience,
 *                          projects, skills, contact)
 *     --allow-ga           do not block Google Analytics requests
 *
 *   Budget file: { "<path>" | "*": { "total_kb", "js_kb", "css_kb", "font_kb", "image_kb",
 *   "requests", "lcp_ms", "cls", "no_framework" } }, compared with the throttled mobile run
 *   (kb = 1024 bytes, JS excludes GA hosts). Built-in default:
 *     { "/": { "js_kb": 15, "lcp_ms": 2000, "cls": 0.05, "no_framework": true } }
 *
 * Examples
 *   # Live site: screenshots, axe and performance for the four main pages
 *   node handoff/tools/measure.mjs https://brettadams0.github.io/ --path / --path /mcp/ \
 *     --path /neuronet-arena/ --path /mcp-surface/ --shots --axe --perf --out /tmp/shots --json /tmp/live.json
 *   # Local build (served gzipped on a random localhost port, like Pages)
 *   jekyll build && node handoff/tools/measure.mjs --site _site --path / --path /mcp/ --shots --axe --perf
 *   # Budget check for a PR (non-zero exit when over budget)
 *   node handoff/tools/measure.mjs --site _site --path / --path /mcp/ --budget --runs 3
 *   # Analytics and Do Not Track check
 *   node handoff/tools/measure.mjs --site _site --path / --analytics --dnt --json /tmp/ga.json
 */
import { createRequire } from 'node:module';
import { execSync, execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import zlib from 'node:zlib';

const require = createRequire(import.meta.url);
let playwright;
try { playwright = require('playwright'); }
catch { playwright = require(path.join(execSync('npm root -g').toString().trim(), 'playwright')); }
const { chromium } = playwright;

const AXE_VERSION = '4.10.2';
const AXE_URL = `https://cdnjs.cloudflare.com/ajax/libs/axe-core/${AXE_VERSION}/axe.min.js`;
const AXE_SHA256 = 'b511cd9dec01c76f4b2ad1723b66b6db37d4c2eb4ed199076e1829d9ee7b75e3';
const GA_HOSTS = /^https?:\/\/([a-z0-9-]+\.)*(googletagmanager\.com|google-analytics\.com|analytics\.google\.com)(\/|:|$)/i;
const RESUME_RE = /Resume_BrettAdams\.pdf$/;
const SHOT_SIZES = [[390, 844], [768, 1024], [1440, 900]];
const MOBILE_UA = 'Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36';
const DEFAULT_BUDGET = { '/': { js_kb: 15, lcp_ms: 2000, cls: 0.05, no_framework: true } };
const DEFAULT_SECTIONS = { '/': ['experience', 'projects', 'skills', 'contact'] };
const KB = 1024;

// ---------- arguments ----------

const argv = process.argv.slice(2);
const opt = { paths: [], runs: 1, out: path.join(os.tmpdir(), 'measure-shots') };
for (let i = 0; i < argv.length; i++) {
  const a = argv[i], next = argv[i + 1];
  const takes = (re) => next && !next.startsWith('--') && re.test(next) ? argv[++i] : null;
  if (/^https?:\/\//.test(a)) opt.url = a;
  else if (a === '--site') opt.site = argv[++i];
  else if (a === '--path') opt.paths.push(argv[++i]);
  else if (a === '--shots') opt.shots = true;
  else if (a === '--axe') { opt.axe = true; opt.axeFile = takes(/\.js$/); }
  else if (a === '--perf') opt.perf = true;
  else if (a === '--budget') { opt.budget = true; opt.perf = true; opt.budgetFile = takes(/\.json$/); }
  else if (a === '--analytics') opt.analytics = true;
  else if (a === '--dnt') opt.dnt = true;
  else if (a === '--allow-ga') opt.allowGa = true;
  else if (a === '--out') opt.out = argv[++i];
  else if (a === '--json') opt.json = argv[++i];
  else if (a === '--runs') opt.runs = Math.max(1, parseInt(argv[++i], 10) || 1);
  else if (a === '--expect-sections') opt.expectSections = argv[++i].split(',').filter(Boolean);
  else if (a === '-h' || a === '--help') usage(0);
  else { console.error(`Unknown argument: ${a}`); usage(2); }
}
if ((!opt.url && !opt.site) || !(opt.shots || opt.axe || opt.perf || opt.analytics || opt.dnt)) usage(2);

function usage(code) {
  const src = fs.readFileSync(new URL(import.meta.url), 'utf8');
  console.log(src.slice(src.indexOf(' * Usage'), src.indexOf(' */')).replace(/^ \* ?/gm, ''));
  process.exit(code);
}

// ---------- helpers ----------

const slugFor = (p) => p.replace(/\.html?$/, '').replace(/^\/+|\/+$/g, '').replace(/\//g, '-') || 'home';
const kb = (b) => b == null ? '–' : `${(b / KB).toFixed(1)} KB`;
const median = (xs) => [...xs].sort((a, b) => a - b)[Math.floor((xs.length - 1) / 2)];

async function loadAxe(file) {
  if (file) return fs.readFileSync(file, 'utf8');
  const cache = path.join(os.tmpdir(), `axe-core-${AXE_VERSION}.min.js`);
  const ok = (s) => s && createHash('sha256').update(s).digest('hex') === AXE_SHA256;
  if (fs.existsSync(cache)) { const s = fs.readFileSync(cache, 'utf8'); if (ok(s)) return s; }
  let src = null;
  try { const r = await fetch(AXE_URL, { signal: AbortSignal.timeout(20000) }); if (r.ok) src = await r.text(); }
  catch { /* Node's fetch may ignore HTTPS_PROXY; curl below reads it */ }
  if (!ok(src)) src = execFileSync('curl', ['-fsSL', '--max-time', '90', AXE_URL], { maxBuffer: 16e6 }).toString('utf8');
  if (!ok(src)) throw new Error(`axe-core download did not match the pinned SHA-256 (${AXE_URL})`);
  fs.writeFileSync(cache, src);
  return src;
}

// Tiny static server for a local build: directory -> index.html, /x -> /x.html, 404.html on
// misses, gzip for text types so transfer sizes resemble GitHub Pages.
const TYPES = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml', '.xml': 'application/xml',
  '.txt': 'text/plain; charset=utf-8', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp',
  '.avif': 'image/avif', '.gif': 'image/gif', '.ico': 'image/x-icon', '.pdf': 'application/pdf', '.woff2': 'font/woff2',
  '.woff': 'font/woff', '.wasm': 'application/wasm' };
function serve(dir) {
  const root = path.resolve(dir);
  const stat = (f) => { try { return fs.statSync(f); } catch { return null; } };
  const server = http.createServer((req, res) => {
    const p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    let file = path.join(root, p), status = 200, st = stat(file);
    if (!file.startsWith(root)) return res.writeHead(403).end();
    if (st?.isDirectory()) {
      if (!p.endsWith('/')) return res.writeHead(301, { Location: p + '/' }).end();
      file = path.join(file, 'index.html'); st = stat(file);
    }
    if (!st && stat(file + '.html')) { file += '.html'; st = stat(file); }
    if (!st) { status = 404; file = path.join(root, '404.html'); if (!stat(file)) return res.writeHead(404).end('Not found'); }
    const type = TYPES[path.extname(file).toLowerCase()] || 'application/octet-stream';
    let body = fs.readFileSync(file);
    const headers = { 'Content-Type': type, 'Cache-Control': 'max-age=600' };
    if (/^(text\/|application\/(json|xml)|image\/svg)/.test(type) && /\bgzip\b/.test(req.headers['accept-encoding'] || '')) {
      body = zlib.gzipSync(body); headers['Content-Encoding'] = 'gzip'; headers.Vary = 'Accept-Encoding';
    }
    headers['Content-Length'] = body.length;
    res.writeHead(status, headers).end(req.method === 'HEAD' ? undefined : body);
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

async function newContext(browser, options = {}) {
  const ctx = await browser.newContext(options);
  ctx.gaBlocked = 0;
  if (!opt.allowGa) await ctx.route(GA_HOSTS, (route) => { ctx.gaBlocked++; return route.abort('blockedbyclient'); });
  return ctx;
}

async function open(ctx, url, timeout = 60000) {
  const page = await ctx.newPage();
  page.consoleErrors = [];
  page.on('console', (m) => { if (m.type() === 'error') page.consoleErrors.push(m.text().slice(0, 200)); });
  page.on('pageerror', (e) => page.consoleErrors.push(String(e.message || e).slice(0, 200)));
  const resp = await page.goto(url, { waitUntil: 'load', timeout });
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  await page.evaluate(() => document.fonts && document.fonts.ready).catch(() => {});
  await page.waitForTimeout(600);
  page.status = resp ? resp.status() : null;
  return page;
}

// Scroll with instant jumps (the site sets scroll-behavior: smooth), optionally back to the top.
function scroll(page, { step = 0.5, delay = 120, back = true } = {}) {
  return page.evaluate(async ({ step, delay, back }) => {
    const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
    const go = (y) => window.scrollTo({ top: y, behavior: 'instant' });
    const max = () => document.documentElement.scrollHeight - innerHeight;
    for (let y = 0; y < max(); y += innerHeight * step) { go(y); await sleep(delay); }
    go(max()); await sleep(Math.max(delay, 1500));
    if (back) { for (let y = scrollY; y > 0; y -= innerHeight * 1.5) { go(y); await sleep(60); } go(0); await sleep(300); }
  }, { step, delay, back });
}

// ---------- screenshots ----------

async function shots(browser, url, slug) {
  fs.mkdirSync(opt.out, { recursive: true });
  const files = [];
  for (const [width, height] of SHOT_SIZES) {
    const ctx = await newContext(browser, { viewport: { width, height }, isMobile: width < 1024, hasTouch: width < 1024 });
    const page = await open(ctx, url);
    const fold = path.join(opt.out, `${slug}-${width}-fold.png`);
    await page.screenshot({ path: fold });
    await scroll(page); // loads lazy images before the full-page shot
    const full = path.join(opt.out, `${slug}-${width}-full.png`);
    await page.screenshot({ path: full, fullPage: true });
    files.push({ width, height, fold, full });
    await ctx.close();
  }
  return files;
}

// ---------- axe ----------

async function axe(browser, url, source) {
  const out = {};
  for (const [width, height] of [[390, 844], [1440, 900]]) {
    const ctx = await newContext(browser, { viewport: { width, height }, isMobile: width < 1024, hasTouch: width < 1024, bypassCSP: true });
    const page = await open(ctx, url);
    await page.addScriptTag({ content: source });
    const r = await page.evaluate(async () => {
      const res = await window.axe.run(document, { resultTypes: ['violations', 'incomplete'] });
      const brief = (v) => ({ id: v.id, impact: v.impact, help: v.help, nodes: v.nodes.length,
        targets: v.nodes.slice(0, 6).map((n) => n.target.join(' ')) });
      return { version: window.axe.version, violations: res.violations.map(brief), incomplete: res.incomplete.map(brief) };
    });
    const by_impact = { critical: [], serious: [], moderate: [], minor: [] };
    for (const v of r.violations) (by_impact[v.impact] || (by_impact[v.impact] = [])).push(v);
    out[width] = { axe_version: r.version, counts: Object.fromEntries(Object.entries(by_impact).map(([k, v]) => [k, v.length])),
      by_impact, incomplete: r.incomplete };
    await ctx.close();
  }
  return out;
}

// ---------- performance ----------

function perfInit() { // runs in the page before any of its scripts
  const m = (window.__measure = { lcp: [], shifts: [] });
  const desc = (el) => !el || !el.tagName ? { selector: '(node no longer in the DOM)', src: null, text: null } : {
    selector: el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + (el.classList.length ? '.' + [...el.classList].join('.') : ''),
    src: el.currentSrc || el.src || null,
    text: (el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 80) || null };
  try {
    new PerformanceObserver((l) => { for (const e of l.getEntries()) m.lcp.push({ ms: Math.round(e.startTime), size: e.size, url: e.url || null, element: desc(e.element) }); })
      .observe({ type: 'largest-contentful-paint', buffered: true });
    new PerformanceObserver((l) => { for (const e of l.getEntries()) m.shifts.push({ value: e.value, t: e.startTime, input: e.hadRecentInput,
      sources: (e.sources || []).slice(0, 3).map((s) => s.node && s.node.tagName ? desc(s.node).selector : s.node ? '#text' : null) }); })
      .observe({ type: 'layout-shift', buffered: true });
  } catch (e) { /* unsupported */ }
}

function perfRead() {
  const m = window.__measure || { lcp: [], shifts: [] };
  const nav = performance.getEntriesByType('navigation')[0] || {};
  const fcp = performance.getEntriesByName('first-contentful-paint')[0];
  let cls = 0, total = 0, win = 0, start = -1, prev = -1; // CLS = largest session window (1 s gap, 5 s cap)
  for (const s of m.shifts) {
    if (s.input) continue;
    total += s.value;
    if (start < 0 || s.t - prev > 1000 || s.t - start > 5000) { win = 0; start = s.t; }
    win += s.value; prev = s.t; cls = Math.max(cls, win);
  }
  const g = window;
  const scripts = [...document.scripts].map((s) => s.src).join(' ');
  const framework = g.__NEXT_DATA__ || document.getElementById('__next') ? 'next' : g.__NUXT__ ? 'nuxt'
    : g.React || document.querySelector('[data-reactroot]') ? 'react' : g.Vue || document.querySelector('[data-v-app]') ? 'vue'
    : g.angular || document.querySelector('[ng-version]') ? 'angular' : g.jQuery ? 'jquery'
    : (/(react|vue|angular|svelte|preact|jquery)[^/]*\.js/i.exec(scripts) || [])[1] || null;
  return { lcp: m.lcp[m.lcp.length - 1] || null, cls: +cls.toFixed(4), cls_total: +total.toFixed(4),
    shifts: m.shifts.filter((s) => !s.input && s.value > 0.001).slice(0, 8),
    fcp_ms: fcp ? Math.round(fcp.startTime) : null,
    dcl_ms: Math.round(nav.domContentLoadedEventEnd || 0), load_ms: Math.round(nav.loadEventEnd || 0), framework,
    render_blocking: performance.getEntriesByType('resource').filter((r) => r.renderBlockingStatus === 'blocking').map((r) => r.name) };
}

const CATEGORY = { Document: 'document', Script: 'script', Stylesheet: 'stylesheet', Font: 'font', Image: 'image' };
function tally(reqs) {
  const by_type = Object.fromEntries(['document', 'script', 'stylesheet', 'font', 'image', 'other'].map((k) => [k, { bytes: 0, count: 0 }]));
  let bytes = 0, count = 0, jsExGa = 0, failed = 0;
  const list = [];
  for (const r of reqs.values()) {
    if (r.url.startsWith('data:') || r.url.startsWith('blob:')) continue;
    if (r.failed) { failed++; continue; }
    if (r.bytes == null) continue;
    const cat = CATEGORY[r.type] || 'other';
    by_type[cat].bytes += r.bytes; by_type[cat].count++; bytes += r.bytes; count++;
    if (cat === 'script' && !GA_HOSTS.test(r.url)) jsExGa += r.bytes;
    list.push({ url: r.url, type: cat, bytes: r.bytes });
  }
  return { transfer_bytes: bytes, requests: count, failed_requests: failed, js_bytes_excl_ga: jsExGa, by_type,
    largest: list.sort((a, b) => b.bytes - a.bytes).slice(0, 8) };
}

async function perfOnce(browser, url, mobile) {
  const ctx = await newContext(browser, mobile
    ? { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, userAgent: MOBILE_UA }
    : { viewport: { width: 1440, height: 900 } });
  await ctx.addInitScript(perfInit);
  const page = await ctx.newPage();
  const cdp = await ctx.newCDPSession(page);
  const reqs = new Map();
  cdp.on('Network.requestWillBeSent', (e) => { const r = reqs.get(e.requestId); if (r) r.url = e.request.url; else reqs.set(e.requestId, { url: e.request.url, type: e.type }); });
  cdp.on('Network.responseReceived', (e) => { const r = reqs.get(e.requestId); if (r) { r.type = e.type; r.status = e.response.status; } });
  cdp.on('Network.loadingFinished', (e) => { const r = reqs.get(e.requestId); if (r) r.bytes = e.encodedDataLength; });
  cdp.on('Network.loadingFailed', (e) => { const r = reqs.get(e.requestId); if (r) r.failed = e.errorText; });
  await cdp.send('Network.enable');
  await cdp.send('Network.setCacheDisabled', { cacheDisabled: true });
  if (mobile) {
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 150,
      downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8 });
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  }
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e.message || e).slice(0, 200)));
  const resp = await page.goto(url, { waitUntil: 'load', timeout: mobile ? 120000 : 60000 });
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(1000);
  const atLoad = await page.evaluate(perfRead); // LCP is read before any scrolling
  const initial = tally(reqs);
  await scroll(page, { step: 0.5, delay: mobile ? 250 : 120 });
  await page.waitForTimeout(800);
  const afterScroll = await page.evaluate(perfRead);
  const total = tally(reqs);
  await ctx.close();
  return { profile: mobile ? 'mobile-throttled' : 'desktop', status: resp?.status() ?? null, ...total,
    initial_transfer_bytes: initial.transfer_bytes, initial_requests: initial.requests, ga_requests_blocked: ctx.gaBlocked,
    lcp: atLoad.lcp, cls: afterScroll.cls, cls_total: afterScroll.cls_total, shifts: afterScroll.shifts,
    fcp_ms: atLoad.fcp_ms, dcl_ms: atLoad.dcl_ms, load_ms: atLoad.load_ms,
    framework: atLoad.framework, render_blocking: atLoad.render_blocking, page_errors: errors };
}

async function perf(browser, url) {
  const out = {};
  for (const [key, mobile] of [['mobile', true], ['desktop', false]]) {
    const runs = [];
    for (let i = 0; i < opt.runs; i++) runs.push(await perfOnce(browser, url, mobile));
    const lcps = runs.map((r) => r.lcp?.ms ?? Infinity);
    out[key] = { ...runs[lcps.indexOf(median(lcps))], runs: runs.length, lcp_runs_ms: lcps };
  }
  return out;
}

// ---------- analytics ----------

function gaInit() { // mirror every dataLayer push with a timestamp; gtag's inline snippet keeps this array
  window.__gaLog = [];
  const dl = (window.dataLayer = window.dataLayer || []);
  dl.push = function (...items) {
    for (const a of items) window.__gaLog.push({ t: Math.round(performance.now()), args: JSON.parse(JSON.stringify(Array.from(a))) });
    return Array.prototype.push.apply(this, items);
  };
}

async function analytics(browser, url, pathName, dnt) {
  const ctx = await newContext(browser, { viewport: { width: 1440, height: 900 } });
  await ctx.addInitScript(gaInit);
  if (dnt) await ctx.addInitScript(() => Object.defineProperty(Navigator.prototype, 'doNotTrack', { get: () => '1', configurable: true }));
  const page = await open(ctx, url);
  // Reading pace: 40% of a screen every 1.2 s, so each section is half-visible for 1 s at some stop.
  await scroll(page, { step: 0.4, delay: 1200, back: false });
  const targets = await page.evaluate((resumeSrc) => {
    const resume = new RegExp(resumeSrc);
    const contactLike = (a) => /^mailto:/.test(a.href) || /linkedin\.com\/in\//.test(a.href) || /^https:\/\/github\.com\/[^/?#]+\/?$/.test(a.href);
    const els = new Set([...document.querySelectorAll('[data-project] a, a[data-contact], a[href]')]);
    const out = [];
    let i = 0;
    for (const a of els) {
      const project = a.closest('[data-project]')?.getAttribute('data-project') || null;
      const contact = a.getAttribute('data-contact');
      const isResume = resume.test(a.getAttribute('href') || '');
      const missingContact = !contact && !project && contactLike(a);
      if (!project && !contact && !isResume && !missingContact) continue;
      a.setAttribute('data-measure-click', String(i));
      const expect = [project && 'project_click', isResume && 'resume_download', (contact || missingContact) && 'contact_click'].filter(Boolean);
      out.push({ i: i++, text: (a.textContent || a.getAttribute('aria-label') || '').trim().replace(/\s+/g, ' ').slice(0, 50) || '(no text)',
        href: a.getAttribute('href'), where: ((w) => w ? w.tagName.toLowerCase() + (w.id ? '#' + w.id : w.classList[0] ? '.' + w.classList[0] : '') : null)(
          a.closest('header, footer, article, section')),
        project, contact, missing_data_contact: missingContact, expect });
    }
    // Prevent navigation after the page's own document-level listener has run (window is last).
    window.addEventListener('click', (e) => e.preventDefault());
    return out;
  }, RESUME_RE.source);
  const log = () => page.evaluate(() => window.__gaLog.length);
  for (const t of targets) {
    const before = await log();
    const sel = `[data-measure-click="${t.i}"]`;
    try { await page.locator(sel).click({ timeout: 2500 }); }
    catch { t.synthetic_click = true; await page.evaluate((s) => document.querySelector(s).dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window })), sel); }
    await page.waitForTimeout(120);
    t.fired = (await page.evaluate((n) => window.__gaLog.slice(n), before)).filter((e) => e.args[0] === 'event').map((e) => e.args[1]);
    t.ok = t.expect.every((x) => t.fired.includes(x));
  }
  await page.evaluate(() => window.dispatchEvent(new Event('pagehide'))); // flushes section_dwell
  await page.waitForTimeout(300);
  const raw = await page.evaluate(() => ({ log: window.__gaLog, gtag: typeof window.gtag === 'function', dom: {
    tracked_sections: [...document.querySelectorAll('section[id]')].map((s) => s.id),
    untracked_sections: [...document.querySelectorAll('section:not([id])')].map((s) => s.className || '(no class)'),
    ids_not_on_section: ['hero', 'education'].map((id) => ({ id, tag: document.getElementById(id)?.tagName.toLowerCase() || null })) } }));
  await ctx.close();
  const events = raw.log.filter((e) => e.args[0] === 'event').map((e) => ({ t: e.t, name: e.args[1], params: e.args[2] || {} }));
  const ids = (name) => [...new Set(events.filter((e) => e.name === name).map((e) => e.params.section_id))];
  const counts = {};
  for (const e of events) counts[e.name] = (counts[e.name] || 0) + 1;
  const measurement_ids = raw.log.filter((e) => e.args[0] === 'config').map((e) => e.args[1]);
  if (dnt) return { gtag_present: raw.gtag, custom_events: events.length, events, measurement_ids, pass: events.length === 0 };
  const expectedEvents = raw.gtag ? ['scroll_depth'] : []; // a page without gtag can't send anything
  if (raw.gtag && raw.dom.tracked_sections.length) expectedEvents.push('section_view', 'section_dwell');
  if (raw.gtag) for (const t of targets) for (const x of t.expect) if (!expectedEvents.includes(x) && !t.missing_data_contact) expectedEvents.push(x);
  const expectedSections = raw.gtag ? opt.expectSections || DEFAULT_SECTIONS[pathName] || [] : [];
  const missing_events = expectedEvents.filter((x) => !counts[x]);
  const missing_sections = expectedSections.filter((x) => !ids('section_view').includes(x));
  const failed_clicks = targets.filter((t) => !t.ok);
  return { gtag_present: raw.gtag, measurement_ids, ga_requests_blocked: ctx.gaBlocked, counts, events, section_view_ids: ids('section_view'),
    section_dwell_ids: ids('section_dwell'), scroll_depth: events.filter((e) => e.name === 'scroll_depth').map((e) => e.params.percent),
    dom: raw.dom, expected_events: expectedEvents, missing_events, expected_sections: expectedSections, missing_sections,
    clicks: targets, failed_clicks: failed_clicks.map((t) => `${t.missing_data_contact ? 'missing data-contact' : 'no event'}: ${t.text} ` +
      `(${t.href}, in ${t.where}) expected ${t.expect.join('+')}, fired ${t.fired.join('+') || 'nothing'}`),
    pass: !raw.gtag || (!missing_events.length && !missing_sections.length && !failed_clicks.some((t) => !t.missing_data_contact)) };
}

// ---------- budget ----------

function checkBudget(pages, budget) {
  const failures = [];
  const metric = { total_kb: (m) => m.transfer_bytes / KB, js_kb: (m) => m.js_bytes_excl_ga / KB, css_kb: (m) => m.by_type.stylesheet.bytes / KB,
    font_kb: (m) => m.by_type.font.bytes / KB, image_kb: (m) => m.by_type.image.bytes / KB, requests: (m) => m.requests,
    lcp_ms: (m) => m.lcp?.ms ?? Infinity, cls: (m) => m.cls };
  for (const p of pages) {
    const rules = budget[p.path] || budget['*'];
    if (!rules) continue;
    const m = p.perf?.mobile;
    if (!m) { failures.push(`${p.path}: no performance result`); continue; }
    p.budget = [];
    for (const [k, limit] of Object.entries(rules)) {
      const actual = k === 'no_framework' ? m.framework : metric[k] ? +metric[k](m).toFixed(3) : undefined;
      if (actual === undefined) { failures.push(`${p.path}: unknown budget key ${k}`); continue; }
      const pass = k === 'no_framework' ? !limit || !actual : actual < limit;
      p.budget.push({ metric: k, limit, actual, pass });
      if (!pass) failures.push(`${p.path}: ${k} ${actual} (limit ${limit})`);
    }
  }
  return { pass: !failures.length, failures };
}

// ---------- main ----------

const report = { tool: 'handoff/tools/measure.mjs', generated_utc: new Date().toISOString(), target: opt.url || null, site_dir: opt.site || null,
  modes: ['shots', 'axe', 'perf', 'budget', 'analytics', 'dnt'].filter((m) => opt[m]), ga_blocked: !opt.allowGa,
  throttling: { mobile: '390x844 DPR2, latency 150 ms, 1.6 Mbps down, 750 kbps up, CPU 4x', desktop: '1440x900, unthrottled' }, pages: [] };
let server = null, browser = null, exitCode = 0;
try {
  if (opt.site) { server = await serve(opt.site); }
  const base = server ? `http://127.0.0.1:${server.address().port}/` : opt.url;
  const paths = opt.paths.length ? opt.paths : [new URL(base).pathname];
  const proxy = process.env.HTTPS_PROXY || process.env.https_proxy;
  // Playwright appends '<-loopback>' (proxy loopback too) unless the list already has it; listing it
  // first and the loopback hosts after it keeps the local --site server off the proxy.
  browser = await chromium.launch({ proxy: proxy ? { server: proxy, bypass: '<-loopback>,127.0.0.1,localhost' } : undefined });
  report.base_url = base; report.chromium = browser.version();
  const axeSource = opt.axe ? await loadAxe(opt.axeFile) : null;
  for (const p of paths) {
    const url = new URL(p, base).href, slug = slugFor(p);
    const entry = { path: p, slug, url };
    report.pages.push(entry);
    const probeCtx = await newContext(browser, { viewport: { width: 1440, height: 900 } });
    try {
      const page = await open(probeCtx, url);
      Object.assign(entry, { status: page.status, final_url: page.url(), title: await page.title(),
        description: await page.$eval('meta[name="description"]', (m) => m.content).catch(() => null), console_errors: page.consoleErrors });
    } catch (e) { entry.error = String(e.message || e).slice(0, 300); }
    await probeCtx.close();
    const step = async (name, fn) => { try { entry[name] = await fn(); } catch (e) { entry[name] = { error: String(e.message || e).slice(0, 300) }; exitCode = 2; } };
    if (opt.shots) await step('shots', () => shots(browser, url, slug));
    if (opt.axe) await step('axe', () => axe(browser, url, axeSource));
    if (opt.perf) await step('perf', () => perf(browser, url));
    if (opt.analytics) await step('analytics', () => analytics(browser, url, p, false));
    if (opt.dnt) await step('dnt', () => analytics(browser, url, p, true));
  }
  if (opt.budget) {
    const budget = opt.budgetFile ? JSON.parse(fs.readFileSync(opt.budgetFile, 'utf8')) : DEFAULT_BUDGET;
    report.budget = { source: opt.budgetFile || 'built-in default', rules: budget, ...checkBudget(report.pages, budget) };
  }
} catch (e) {
  console.error(`measure.mjs: ${e.stack || e}`);
  exitCode = 2;
} finally {
  await browser?.close();
  server?.close();
}

// ---------- summary ----------

const lines = [`measure.mjs  ${report.generated_utc}  ${report.site_dir ? `site ${report.site_dir}` : report.target}${opt.allowGa ? '' : '  (GA requests blocked)'}`];
for (const p of report.pages) {
  lines.push('', `${p.path}  ${p.status ?? p.error ?? ''}  ${p.title || ''}`);
  for (const k of ['mobile', 'desktop']) {
    const m = p.perf?.[k];
    if (!m) continue;
    if (m.error) { lines.push(`  perf: ${m.error}`); break; }
    lines.push(`  ${k.padEnd(7)} ${kb(m.transfer_bytes)} total, JS ${kb(m.js_bytes_excl_ga)} excl. GA, CSS ${kb(m.by_type.stylesheet.bytes)}, fonts ${kb(m.by_type.font.bytes)}, ` +
      `img ${kb(m.by_type.image.bytes)}, ${m.requests} req | LCP ${m.lcp?.ms ?? '–'} ms (${m.lcp?.element?.selector || m.lcp?.url || '–'}) | CLS ${m.cls}` +
      `${m.framework ? ` | framework ${m.framework}` : ''}`);
  }
  for (const w of ['390', '1440']) {
    const a = p.axe?.[w];
    if (!a) continue;
    if (a.error || p.axe.error) { lines.push(`  axe: ${a.error || p.axe.error}`); break; }
    const top = [...a.by_impact.critical, ...a.by_impact.serious, ...a.by_impact.moderate, ...a.by_impact.minor].map((v) => `${v.id}×${v.nodes}`).join(', ');
    lines.push(`  axe ${w.padEnd(4)} critical ${a.counts.critical}, serious ${a.counts.serious}, moderate ${a.counts.moderate}, minor ${a.counts.minor}${top ? `  [${top}]` : ''}`);
  }
  const g = p.analytics;
  if (g?.error) lines.push(`  analytics: ${g.error}`);
  else if (g) {
    lines.push(`  analytics ${g.pass ? 'PASS' : 'FAIL'}: ${g.gtag_present ? '' : 'no gtag on this page; '}${Object.entries(g.counts).map(([k, v]) => `${k}×${v}`).join(', ') || 'no events'}`);
    lines.push(`    section_view: ${g.section_view_ids.join(', ') || '–'} | dwell: ${g.section_dwell_ids.join(', ') || '–'} | scroll_depth: ${g.scroll_depth.join(', ') || '–'}`);
    if (g.missing_events.length || g.missing_sections.length) lines.push(`    missing: ${[...g.missing_events, ...g.missing_sections.map((s) => `section_id ${s}`)].join(', ')}`);
    for (const f of g.failed_clicks) lines.push(`    ${f}`);
    if (!g.pass) exitCode = Math.max(exitCode, 1);
  }
  if (p.dnt?.error) lines.push(`  dnt: ${p.dnt.error}`);
  else if (p.dnt) { lines.push(`  dnt ${p.dnt.pass ? 'PASS' : 'FAIL'}: ${p.dnt.custom_events} custom events with doNotTrack = '1'`); if (!p.dnt.pass) exitCode = Math.max(exitCode, 1); }
  if (Array.isArray(p.shots)) lines.push(`  shots: ${p.shots.length * 2} PNGs in ${opt.out}`);
  else if (p.shots?.error) lines.push(`  shots: ${p.shots.error}`);
}
if (report.budget) {
  lines.push('', `budget (${report.budget.source}): ${report.budget.pass ? 'PASS' : 'FAIL'}`, ...report.budget.failures.map((f) => `  over: ${f}`));
  if (!report.budget.pass) exitCode = Math.max(exitCode, 1);
}
report.exit_code = exitCode;
if (opt.json) { fs.mkdirSync(path.dirname(path.resolve(opt.json)), { recursive: true }); fs.writeFileSync(opt.json, JSON.stringify(report, null, 2)); lines.push('', `JSON report: ${opt.json}`); }
console.log(lines.join('\n'));
process.exit(exitCode);
