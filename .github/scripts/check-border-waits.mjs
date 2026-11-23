#!/usr/bin/env node
// Guards the /border-waits/ budget measurement: measure.mjs only checks upper limits, so a 404 page,
// an empty shell, or the demo sitting in its saved-snapshot state would all pass it. This loads the
// built page the same way and fails unless it is the real demo showing data from the data route.
//
//   node .github/scripts/check-border-waits.mjs [_site]
//
// The site has no pipeline of its own, so the data route (/border-waits-pipeline/data/v1/, another
// repository's Pages) must first be given the demo's saved export, which is a real recorded one:
//   mkdir -p _site/border-waits-pipeline/data/v1 && cp -r _site/border-waits/fallback/. _site/border-waits-pipeline/data/v1/
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';

const require = createRequire(import.meta.url);
let playwright;
try { playwright = require('playwright'); }
catch { playwright = require(path.join(execSync('npm root -g').toString().trim(), 'playwright')); }

const root = path.resolve(process.argv[2] || 'dist');
const TYPES = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.json': 'application/json', '.woff2': 'font/woff2', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
  let file = path.join(root, decodeURIComponent(new URL(req.url, 'http://x').pathname));
  if (file.startsWith(root) && fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
  if (!file.startsWith(root) || !fs.existsSync(file)) return res.writeHead(404).end('Not found');
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' }).end(fs.readFileSync(file));
});
await new Promise((ok) => server.listen(0, '127.0.0.1', ok));

const browser = await playwright.chromium.launch();
const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
const seen = [];
page.on('response', (r) => seen.push([new URL(r.url()).pathname, r.status()]));
const response = await page.goto(`http://127.0.0.1:${server.address().port}/border-waits/`, { waitUntil: 'networkidle' });
const got = await page.evaluate(() => ({
  badge: document.getElementById('live-badge')?.textContent ?? null,
  note: document.getElementById('data-note')?.textContent ?? null,
  readings: document.querySelectorAll('#now-table tbody tr th[scope="row"]').length,
  chart: !!document.querySelector('#chart svg'),
  squares: document.querySelectorAll('#heat .cells i').length,
}));
await browser.close();
server.close();

const data = (name) => seen.some(([p, s]) => p === `/border-waits-pipeline/data/v1/${name}` && s === 200);
const checks = {
  'the page answers 200': response.status() === 200,
  'no uncaught error': errors.length === 0,
  'manifest.json came from the data route': data('manifest.json'),
  'summary.json came from the data route': data('summary.json'),
  'the page is showing that data, not its saved snapshot': /^(live|delayed) /.test(got.badge || '') && got.note === '',
  'six direction rows, a chart and the 12 x 24 grid are drawn': got.readings === 6 && got.chart && got.squares === 288,
};
let failed = false;
for (const [name, ok] of Object.entries(checks)) { console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`); failed ||= !ok; }
if (failed) { console.log(JSON.stringify({ got, errors, data: seen.filter(([p]) => p.includes('/data/v1/')) })); process.exit(1); }
