#!/usr/bin/env node
// Verifies that authentic project cover images load when visible without adding
// below-the-fold screenshot bytes to the homepage's initial request budget.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import { createReadStream, existsSync, statSync } from 'node:fs';
import http from 'node:http';
import path from 'node:path';

const require = createRequire(import.meta.url);
let chromium;
try { ({ chromium } = require('playwright')); }
catch { ({ chromium } = require(path.join(execSync('npm root -g').toString().trim(), 'playwright'))); }

const root = path.resolve(process.argv[2] || '_site');
const types = {
  '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css',
  '.svg': 'image/svg+xml', '.webp': 'image/webp', '.woff2': 'font/woff2',
};
const server = http.createServer((req, res) => {
  const uri = new URL(req.url, 'http://localhost');
  let file = path.resolve(root, '.' + decodeURIComponent(uri.pathname));
  if (!file.startsWith(root + path.sep) && file !== root) { res.writeHead(403).end(); return; }
  if (!existsSync(file)) { res.writeHead(404).end(); return; }
  if (statSync(file).isDirectory()) file = path.join(file, 'index.html');
  if (!existsSync(file)) { res.writeHead(404).end(); return; }
  res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream');
  createReadStream(file).pipe(res);
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  const base = 'http://127.0.0.1:' + server.address().port;
  page.on('pageerror', error => console.error('BROWSER ERROR:', error.message));
  page.on('requestfailed', request => {
    if (request.url().includes('/assets/img/')) console.error('FAILED IMAGE:', request.url(), request.failure());
  });
  const signalRequests = [];
  page.on('request', r => {
    if (r.url().includes('signal-lab-800.webp') || r.url().includes('signal-lab-overview.webp'))
      signalRequests.push(r.url());
  });
  await page.goto(base + '/', { waitUntil: 'networkidle' });
  const signal = page.locator('[data-project="signal-lab"] img[data-real-cover]');
  assert.equal(await signal.count(), 1, 'Missing real screenshot card');
  assert.equal(await signal.getAttribute('src'), null, 'Lower-page screenshot was eagerly fetched');
  assert.equal(signalRequests.length, 0, 'Screenshot requested before its card entered view');

  await page.locator('[data-project="signal-lab"] .project-shot').scrollIntoViewIfNeeded();
  await page.waitForFunction(() => {
    const image = document.querySelector('img[data-real-cover]');
    return !!image && image.complete && image.naturalWidth >= 800;
  }, undefined, { timeout: 12000 });
  assert(signalRequests.length >= 1, 'Real Signal Lab screenshot was not requested after scroll');

  const sift = page.locator('[data-project="sift"] img');
  await sift.scrollIntoViewIfNeeded();
  await page.waitForFunction(() => {
    const image = document.querySelector('[data-project="sift"] img');
    return !!image && image.complete && image.naturalWidth >= 800 &&
      image.currentSrc.includes('sift-grade-');
  }, undefined, { timeout: 12000 });
  console.log('PASS authentic Signal Lab screenshot loaded on scroll; genuine Sift A/B cover decoded');
} finally {
  await browser.close();
  await new Promise(resolve => server.close(resolve));
}
