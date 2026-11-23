#!/usr/bin/env node
// Real-browser regression coverage for worker startup, playback, replay, sharing and layout.
// Usage: node .github/scripts/check-mazefinder.mjs [site directory, default _site]
// Uses the same global Playwright installation as measure.mjs.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import { readFileSync, statSync } from 'node:fs';
import http from 'node:http';
import path from 'node:path';
const require = createRequire(import.meta.url);
let chromium;
try { ({ chromium } = require('playwright')); }
catch { ({ chromium } = require(path.join(execSync('npm root -g').toString().trim(), 'playwright'))); }
const root = path.resolve(process.argv[2] || 'dist');
// Resource Timing on the page does not include worker-initiated downloads. Budget those
// independently, using raw bytes (a conservative ceiling even when hosting compresses them).
const engineBytes = ['engine-worker.js', 'wasm/mazefinder.js', 'wasm/mazefinder.wasm']
  .reduce((total, file) => total + statSync(path.join(root, 'mazefinder', file)).size, 0);
assert(engineBytes <= 190 * 1024, `Engine payload exceeded 190 KiB: ${engineBytes} bytes`);
console.log(`MazeFinder worker + WASM payload: ${(engineBytes / 1024).toFixed(1)} KiB uncompressed`);
const { EvolutionHistory, changedEdges } = await import('data:text/javascript;base64,' + readFileSync(path.join(root, 'mazefinder/evolution-history.js')).toString('base64'));
const journal = new EvolutionHistory();
for (let generation = 0; generation < 600; generation++) journal.append([{
  generation, previousFitness: 1000, fitness: generation === 20 ? 0 : 999,
  firstSolution: generation === 10, optimal: generation === 599,
}]);
assert.equal(journal.events.length, 256);
for (const generation of [0, 10, 20, 599]) assert(journal.get(generation), `Lost protected checkpoint ${generation}`);
assert.deepEqual(changedEdges([0, 0, 1, 0, 1, 0], [1, 0, 0, 0, 0, 1]), { added: [[0, 0, 0, 1]], removed: [] });
const types = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm', '.css': 'text/css', '.woff2': 'font/woff2', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
  let file = path.resolve(root, '.' + new URL(req.url, 'http://localhost').pathname);
  if (!file.startsWith(root + path.sep) && file !== root) { res.writeHead(403).end(); return; }
  try {
    if (statSync(file).isDirectory()) file = path.join(file, 'index.html');
    res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream');
    res.end(readFileSync(file));
  } catch { res.writeHead(404).end(); }
});
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const origin = `http://127.0.0.1:${server.address().port}`;
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
const ready = (page) => page.waitForFunction(() => document.querySelector('#lab').getAttribute('aria-busy') === 'false' && !document.querySelector('#reset').disabled);
const gen = async (page) => Number((await page.locator('#s-gen').textContent()).replaceAll(',', ''));
try {
  const context = await browser.newContext({ reducedMotion: 'reduce' });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto(`${origin}/mazefinder/`); await ready(page);
  assert.equal(await gen(page), 0);
  assert.equal(await page.locator('#run-state').textContent(), 'Paused');
  const initialFitness = await page.locator('#s-fit').textContent();
  await page.locator('#step').click(); await page.waitForFunction(() => document.querySelector('#s-gen').textContent === '1');
  await page.locator('#reset').click(); await ready(page);
  assert.equal(await gen(page), 0); assert.equal(await page.locator('#s-fit').textContent(), initialFitness);
  assert.equal(await page.locator('#run-state').textContent(), 'Paused');
  // Rapid toggles used to leave several requestAnimationFrame loops alive.
  await page.evaluate(() => { const run = document.querySelector('#run'); for (let i = 0; i < 9; i++) run.click(); });
  await page.waitForTimeout(650); await page.locator('#run').click(); await page.waitForTimeout(150);
  const advanced = await gen(page);
  assert(advanced >= 1 && advanced <= 12, `Playback rate multiplied after rapid clicks: ${advanced}`);
  await page.waitForTimeout(200); assert.equal(await gen(page), advanced, 'Paused simulation advanced');
  await page.locator('#reset').click(); await ready(page);
  await page.locator('#step').click(); await page.waitForFunction(() => document.querySelector('#s-gen').textContent === '1');
  await page.locator('#show-optimal').check();
  assert.equal(await gen(page), 1, 'Layer toggle changed simulation');
  for (const width of [320, 390, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 }); await page.evaluate(() => scrollTo(0, 0));
    const layout = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth, run: document.querySelector('#run').getBoundingClientRect().top }));
    assert(layout.scroll <= width, `Overflow at ${width}`);
    assert(layout.run < 600, `Controls buried at ${width}`);
  }
  await page.goto(`${origin}/mazefinder/?size=40&kind=maze&seed=-1`); await ready(page);
  assert.equal(await page.locator('#kind-pick').inputValue(), 'loops');
  assert.equal(await page.locator('#s-seed').textContent(), '1');
  await page.locator('[data-preset="open"]').click(); await ready(page);
  assert(await page.locator('#size-pick').isDisabled());
  assert.equal(await page.locator('#new-maze').textContent(), 'New run');
  await page.locator('#speed').fill('20'); await page.locator('#run').click();
  await page.waitForFunction(() => document.querySelector('#run-state').textContent === 'Optimal', { timeout: 15000 });
  assert(await page.locator('#run').isDisabled()); assert(await page.locator('#step').isDisabled());
  assert.equal(await page.locator('#s-distance').textContent(), await page.locator('#s-short').textContent());
  // Seed 18 plateaued at 94 moves vs optimal 90 even after 10,000 generations.
  await page.goto(`${origin}/mazefinder/?size=40&kind=loops&seed=18`); await ready(page);
  await page.locator('#speed').fill('20'); await page.locator('#run').click();
  await page.waitForFunction(() => document.querySelector('#run-state').textContent === 'Optimal', null, { timeout: 20000 });
  assert.equal(await page.locator('#s-distance').textContent(), '90');
  assert(await page.locator('#continue').isHidden());
  const replayLink = await page.locator('#share').getAttribute('href');
  await page.goto(origin + replayLink); await ready(page); assert.equal(await gen(page), 0);
  // Compare batched event capture with every individual generation of the real engine.
  const capture = await page.evaluate(async () => {
    async function run(count) {
      const worker = new Worker('/mazefinder/engine-worker.js?v=4', { type: 'module' });
      let id = 0;
      const request = (type, extra = {}) => new Promise((resolve, reject) => {
        worker.onmessage = ({ data }) => data.type === 'error' ? reject(new Error(data.message)) : resolve(data);
        worker.onerror = reject; worker.postMessage({ type, id: ++id, runId: 1, ...extra });
      });
      let state = await request('create', { state: { size: 20, kind: 'maze', seed: 1 } });
      const events = [...state.events], records = [];
      while (state.fitness !== state.shortest && state.generation < 1500) {
        const before = { generation: state.generation, path: [...state.paths.slice(0, state.lengths[0] * 2)] };
        state = await request('step', { count });
        events.push(...state.events);
        if (count === 1 && state.events.length) records.push({ before, after: [...state.paths.slice(0, state.lengths[0] * 2)], success: Array.from(state.lengths).filter((length, i) => {
          const offset = (i * state.stride + length - 1) * 2;
          return state.paths[offset] === 19 && state.paths[offset + 1] === 19;
        }).length });
      }
      worker.terminate();
      return { events: events.map(e => ({ ...e, path: [...e.path], previousPath: [...e.previousPath] })), records };
    }
    return { batched: await run(20), single: await run(1) };
  });
  assert.deepEqual(capture.batched.events, capture.single.events, 'Batches lost or changed recorded events');
  capture.single.events.slice(1).forEach((event, index) => {
    const record = capture.single.records[index];
    assert.deepEqual(event.previousPath, record.before.path);
    assert.deepEqual(event.path, record.after);
    assert.equal(event.previousGeneration, record.before.generation);
    assert.equal(event.successfulRoutes, record.success);
  });
  assert.equal(capture.batched.events.find(e => e.firstSolution).generation, 78);
  assert.equal(capture.batched.events.at(-1).generation, 262);
  await page.goto(`${origin}/mazefinder/?size=20&kind=maze&seed=1`); await ready(page);
  await page.locator('#speed').fill('20'); await page.locator('#run').click();
  await page.waitForFunction(() => document.querySelector('#run-state').textContent === 'Optimal');
  const checkpoints = await page.locator('#event-pick option').count();
  await page.locator('#event-pick').selectOption('78');
  assert.equal(await gen(page), 78);
  assert.match(await page.locator('#s-success').textContent(), /1 \/ 100/);
  assert(await page.locator('#play-route').isDisabled(), 'Reduced motion should show static comparison');
  await page.waitForTimeout(150); assert.equal(await gen(page), 78);
  await page.locator('#return-live').click();
  assert.equal(await gen(page), 262);
  assert.equal(await page.locator('#run-state').textContent(), 'Optimal');
  await page.locator('#event-pick').focus(); await page.keyboard.press('Home'); await page.keyboard.press('ArrowDown');
  assert.equal(await gen(page), 0, 'Keyboard picker did not select starting population');
  await page.locator('#return-live').click();
  assert.equal(await page.locator('#event-pick option').count(), checkpoints);
  await page.locator('#event-pick').selectOption('78');
  await page.locator('#reset').click(); await ready(page);
  assert.equal(await page.locator('#lab').getAttribute('data-view'), 'live');
  assert.equal(await gen(page), 0);
  assert.equal(await page.locator('#event-pick option').count(), 2);
  // Clipboard denial must produce useful fallback feedback, not an unhandled rejection.
  await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', { value: { writeText: () => Promise.reject(new Error('denied')) }, configurable: true }));
  await page.locator('#copy').click();
  await page.waitForFunction(() => document.querySelector('#copy-status').textContent.includes('Copy unavailable'));
  assert.deepEqual(errors, []);
  await context.close();

  // Deliver a pending step after historical inspection begins. It must update only live cache.
  const delayed = await browser.newContext({ reducedMotion: 'reduce' });
  await delayed.route('**/engine-worker.js*', async route => {
    const response = await route.fetch();
    await route.fulfill({ response, body: (await response.text()).replace('postMessage(result, transfers);', 'setTimeout(() => postMessage(result, transfers), includeMaze ? 0 : 300);') });
  });
  await delayed.addInitScript(() => {
    const Original = window.Worker;
    window.stepRequests = 0; window.stepResponses = 0;
    window.Worker = class extends Original {
      constructor(...args) { super(...args); this.addEventListener('message', ({ data }) => { if (data.type === 'snapshot' && !data.cells) window.stepResponses++; }); }
      postMessage(data, ...args) { if (data.type === 'step') window.stepRequests++; return super.postMessage(data, ...args); }
    };
  });
  const late = await delayed.newPage();
  await late.goto(`${origin}/mazefinder/`); await ready(late);
  await late.locator('#speed').fill('20'); await late.locator('#run').click();
  await late.waitForFunction(() => window.stepRequests > window.stepResponses && window.stepResponses > 0 && !document.querySelector('#event-pick').disabled);
  await late.locator('#event-pick').selectOption('0');
  await late.waitForTimeout(500);
  assert.equal(await gen(late), 0, 'Late worker response replaced historical metrics');
  assert.equal(await late.locator('#lab').getAttribute('data-view'), 'replay');
  await late.locator('#return-live').click();
  assert(await gen(late) > 0, 'Live cache lost pending progress');
  assert.equal(await late.locator('#run-state').textContent(), 'Evolving');
  await delayed.close();

  // Exercise budget exhaustion with the real engine and a deliberately small test
  // budget; do not depend on a particular seed remaining unsolved after improvements.
  const capped = await browser.newContext({ reducedMotion: 'reduce' });
  await capped.route('**/engine-worker.js*', async (route) => {
    const response = await route.fetch();
    await route.fulfill({ response, body: (await response.text()).replace('const INITIAL_BUDGET = 1500;', 'const INITIAL_BUDGET = 5;') });
  });
  const limited = await capped.newPage();
  await limited.goto(`${origin}/mazefinder/?size=40&kind=loops&seed=18`); await ready(limited);
  await limited.locator('#speed').fill('20'); await limited.locator('#run').click();
  await limited.waitForFunction(() => document.querySelector('#run-state').textContent === 'Budget reached');
  assert.equal(await gen(limited), 5);
  assert(await limited.locator('#run').isDisabled());
  assert.match(await limited.locator('#goal').textContent(), /5 generation budget reached/);
  const seedBefore = await limited.locator('#s-seed').textContent();
  await limited.locator('#continue').click();
  await limited.waitForFunction(() => document.querySelector('#s-gen').textContent === '10');
  assert.equal(await limited.locator('#s-seed').textContent(), seedBefore);
  assert.equal(await limited.locator('#s-limit').textContent(), 'of 10');
  await limited.locator('#reset').click(); await ready(limited);
  assert.equal(await gen(limited), 0);
  assert.equal(await limited.locator('#s-limit').textContent(), 'of 5');
  await capped.close();

  const failed = await browser.newContext({ reducedMotion: 'reduce' });
  await failed.route('**/*.wasm*', (route) => route.abort('timedout'));
  const broken = await failed.newPage();
  await broken.goto(`${origin}/mazefinder/`);
  await broken.locator('#retry').waitFor({ state: 'visible' });
  assert(await broken.locator('#run').isDisabled());
  await failed.unroute('**/*.wasm*');
  await broken.locator('#retry').click(); await ready(broken);
  assert.equal(await gen(broken), 0);
  await failed.close();

  const stalled = await browser.newContext({ reducedMotion: 'reduce' });
  await stalled.route('**/engine-worker.js*', (route) => route.fulfill({ contentType: 'text/javascript', body: 'self.onmessage = () => {};' }));
  const hung = await stalled.newPage();
  await hung.clock.install();
  await hung.goto(`${origin}/mazefinder/`);
  await hung.waitForFunction(() => document.querySelector('#share').getAttribute('href').includes('seed='));
  await hung.clock.fastForward(16000);
  await hung.locator('#retry').waitFor({ state: 'visible' });
  assert.match(await hung.locator('#loading-detail').textContent(), /too long/);
  assert(await hung.locator('#run').isDisabled());
  await stalled.close();

  const moduleFailure = await browser.newContext();
  await moduleFailure.route('**/app.js*', (route) => route.abort());
  const unloaded = await moduleFailure.newPage(); await unloaded.goto(`${origin}/mazefinder/`);
  await unloaded.locator('#retry').waitFor({ state: 'visible' });
  assert.equal(await unloaded.locator('#loading-title').textContent(), 'The demo could not load');
  await moduleFailure.close();

  const active = await browser.newPage();
  await active.goto(`${origin}/mazefinder/`); await ready(active);
  await active.waitForFunction(() => document.querySelector('#run-state').textContent === 'Evolving');
  await active.locator('#run').click(); await active.locator('#reset').click(); await ready(active);
  assert.equal(await active.locator('#run-state').textContent(), 'Paused', 'Replay restarted a paused run');
  await active.locator('#speed').fill('20'); await active.locator('#run').click();
  await active.waitForFunction(() => document.querySelector('#event-pick option[value="78"]'));
  await active.locator('#event-pick').selectOption('78');
  await active.locator('#play-route').click();
  await active.waitForTimeout(3200);
  assert.equal(await gen(active), 78, 'Recorded playback advanced the simulation display');
  assert.match(await active.locator('#play-route').textContent(), /Play/);
  await active.locator('#return-live').click();
  assert.equal(await active.locator('#run-state').textContent(), 'Evolving');
  await active.locator('#run').click();
  await active.locator('#event-pick').selectOption('78');
  await active.locator('#return-live').click();
  assert.equal(await active.locator('#run-state').textContent(), 'Paused');
  console.log('MazeFinder PASS: startup/retry, single animation loop, step/replay, presets, completion, URL normalization, clipboard fallback, reduced motion and responsive controls.');
} finally { await browser.close(); await new Promise((resolve) => server.close(resolve)); }
