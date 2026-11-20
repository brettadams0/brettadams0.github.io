#!/usr/bin/env node
// Validate the actual captured Signal Lab dashboard and production Sift-graded
// A/B image load in a real browser. No synthetic/generative screenshots.
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
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css',
  '.webp': 'image/webp', '.woff2': 'font/woff2', '.svg': 'image/svg+xml' };
const server = http.createServer((req,res) => {
  const uri = new URL(req.url, 'http://localhost');
  let file = path.resolve(root, '.' + decodeURIComponent(uri.pathname));
  if (!file.startsWith(root + path.sep) && file !== root) {res.writeHead(403).end();return;}
  if (!existsSync(file)) {res.writeHead(404).end();return;}
  if (statSync(file).isDirectory()) file = path.join(file, 'index.html');
  if (!existsSync(file)) {res.writeHead(404).end();return;}
  res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream');
  createReadStream(file).pipe(res);
});
await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport:{ width:390,height:844 } });
  await page.goto('http://127.0.0.1:'+server.address().port+'/',{waitUntil:'domcontentloaded'});
  for (const [project, expected] of [['signal-lab','signal-lab-800.webp'],['sift','sift-grade-800.webp']]) {
    const image = page.locator('[data-project="'+project+'"] .project-shot img');
    assert.equal(await image.count(),1,project+' cover is missing');
    assert((await image.getAttribute('src')).includes(expected),project+' source is wrong');
    await image.scrollIntoViewIfNeeded();
    await page.waitForFunction(id => {
      const img=document.querySelector('[data-project="'+id+'"] .project-shot img');
      return Boolean(img && img.complete && img.naturalWidth > 0 && img.naturalHeight > 0);
    },project,{timeout:12000});
  }
  console.log('PASS real Signal Lab screenshot and authentic Sift before/after decoded in Chromium');
} finally {
  await browser.close();
  await new Promise(resolve => server.close(resolve));
}
