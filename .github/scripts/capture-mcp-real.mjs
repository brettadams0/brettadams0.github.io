#!/usr/bin/env node
/**
 * Screenshot evidence captured from the ACTUAL mcp-surface browser application,
 * compiled from the fixed repository source. No mock terminals, generated UI,
 * or AI imagery. The script also tests the real AJV 2020-12 browser bundle.
 */
import assert from 'node:assert/strict';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createRequire } from 'node:module';
import http from 'node:http';
import path from 'node:path';
import { runChecks } from './mcp-bundle/node_modules/mcp-surface/dist/checks/index.js';
const require = createRequire(import.meta.url);
let chromium;
try { ({ chromium } = require('playwright')); }
catch { ({ chromium } = require(path.join(require('node:child_process').execSync('npm root -g').toString().trim(), 'playwright'))); }

const root = path.resolve(process.argv[2] || '.');
const mime = { '.html':'text/html', '.css':'text/css', '.js':'text/javascript', '.woff2':'font/woff2', '.svg':'image/svg+xml', '.webp':'image/webp', '.png':'image/png' };
const server = http.createServer((req,res) => {
  const uri=new URL(req.url, 'http://localhost');
  let file=path.resolve(root, '.' + decodeURIComponent(uri.pathname));
  if (!file.startsWith(root+path.sep) && file!==root) {res.writeHead(403).end(); return;}
  if (!existsSync(file)) {res.writeHead(404).end();return;}
  if (statSync(file).isDirectory()) file=path.join(file,'index.html');
  if (!existsSync(file)) {res.writeHead(404).end();return;}
  res.setHeader('content-type',mime[path.extname(file)]||'application/octet-stream');
  createReadStream(file).pipe(res);
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
try {
  const page=await browser.newPage({viewport:{width:1600,height:1000},deviceScaleFactor:1, reducedMotion:'reduce'});
  const errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('requestfailed',r=>errors.push(r.url()+': '+r.failure()?.errorText));
  const base='http://127.0.0.1:'+server.address().port+'/mcp-surface/';
  await page.goto(base,{waitUntil:'networkidle'});

  // Compare every displayed finding with the pinned release running on Node.
  const source = page.locator('#source');
  const threshold = page.locator('#fail-on');
  const verdict = page.locator('#verdict');
  async function compareWithNode(expectedTools) {
    const tools = expectedTools ?? JSON.parse(await source.inputValue()).tools;
    const findings = runChecks({
      server: { name: 'fixture', version: '1.0.0' }, tools, capabilities: { tools: {} },
      resources: [], prompts: [], listErrors: [],
    });
    const levels = ['info', 'warn', 'error'];
    const ordered = findings.toSorted((a, b) => levels.indexOf(b.level) - levels.indexOf(a.level));
    const actual = await page.locator('#report .finding').evaluateAll(rows => rows.map(row => ({
      level: row.querySelector('.lvl').textContent,
      subject: row.querySelector('.subject')?.textContent,
      message: row.querySelector('.msg').textContent,
      rule: row.querySelector('.rule-tag').textContent.slice(1, -1),
    })));
    assert.deepEqual(actual, ordered.map(({ level, subject, message, rule }) => ({ level, subject, message, rule })));
    assert(!findings.some(f => f.rule === 'check-crashed'));
    for (const failOn of ['warn', 'error']) {
      await threshold.selectOption(failOn);
      const fails = findings.some(f => levels.indexOf(f.level) >= levels.indexOf(failOn));
      assert.equal(await verdict.textContent(), fails ? 'exit 1CI fails' : 'exit 0CI passes');
    }
    return findings;
  }

  for (const example of ['healthy', 'modern', 'broken']) {
    await page.locator(`[data-example="${example}"]`).click();
    const findings = await compareWithNode();
    if (example === 'broken') assert(findings.some(f => f.rule === 'schema-valid'));
    else assert.deepEqual(findings, []);
  }

  // Fewer than 16K characters, more than 16 KiB in UTF-8.
  const large = { tools: [{ name: 'status', description: '界'.repeat(6000), inputSchema: { type: 'object' } }] };
  await source.fill(JSON.stringify(large));
  await page.locator('#report .rule-tag').filter({ hasText: 'surface-token-cost' }).waitFor();
  assert((await compareWithNode()).some(f => f.rule === 'surface-token-cost'));

  for (const [invalid, recovery] of [['{', large.tools], ['{"wrong":[]}', { result: large }]]) {
    await page.locator('[data-example="healthy"]').click();
    await source.fill(invalid);
    await page.waitForSelector('#source[aria-invalid="true"]');
    assert.match(await verdict.textContent(), /paused/i);
    assert.equal(await page.locator('#report').textContent(), '');
    assert.equal(await page.locator('#annotations').textContent(), '');
    assert.equal(await page.locator('#break-summary').textContent(), '');
    assert.equal(await page.locator('.rule.hit, [data-break]:enabled').count(), 0);
    await source.fill(JSON.stringify(recovery));
    await page.waitForSelector('#report .rule-tag');
    assert.equal(await source.getAttribute('aria-invalid'), null);
    await compareWithNode(large.tools);
  }

  // Deliberately broken schemas must still fail. This is the interactive
  // behavior shown in the cover, from the shipped application itself.
  await page.locator('[data-example="healthy"]').click();
  await page.locator('[data-break="required"]').click();
  await page.waitForFunction(()=>document.querySelector('#verdict')?.textContent?.includes('CI fails'));
  const result=await page.locator('#report').innerText();
  assert(result.includes('schema-required-props'), 'closed required-field defect was not detected');
  assert(result.includes('1 error'), 'broken contract did not produce an error');
  assert.deepEqual(errors,[], 'browser console or network errors during real interaction');

  await page.evaluate(async ()=>{
    await document.fonts.ready;
    window.scrollTo({top:110,behavior:'instant'});
  });
  await page.waitForTimeout(180);
  await page.screenshot({path:'/tmp/mcp-surface-real-audit.png',type:'png',animations:'disabled',caret:'hide'});

  // Also test the small viewport for accessibility and layout.
  await page.setViewportSize({width:390,height:844});
  await page.evaluate(()=>window.scrollTo(0,0));
  assert.equal(await page.locator('#source').count(),1);
  assert.equal(await page.locator('#report').count(),1);
  const body=await page.evaluate(()=>({scrollWidth:document.body.scrollWidth, viewport:window.innerWidth}));
  assert(body.scrollWidth <= body.viewport+2, 'mobile horizontal overflow: '+JSON.stringify(body));
  console.log('PASS Node/browser finding and threshold parity, UTF-8 size warning, invalid input and recovery, closed-schema regression, desktop screenshot, mobile layout');
} finally {
  await browser.close();
  await new Promise(resolve=>server.close(resolve));
}
