// UI and visualization only. engine-worker.js runs the project's actual C++/WASM engine.
const $ = (id) => document.getElementById(id);
const PRESETS = {
  open: { size: 20, kind: 'open', seed: 1 },
  maze: { size: 20, kind: 'maze', seed: 1 },
  loops: { size: 40, kind: 'loops', seed: 7 },
};
const TITLES = { open: 'Quick escape', maze: 'Find the way', loops: 'Take the long way', obstacles: 'Around the obstacles' };
const motion = matchMedia('(prefers-reduced-motion: reduce)');
const canvas = $('maze'), ctx = canvas.getContext('2d');
let worker, timeout, runId = 0, requestId = 0, pending = false;
let state = normalize(Object.fromEntries(new URLSearchParams(location.search)));
let snapshot = null, history = [], heat, seen;
let running = false, desiredRunning = !motion.matches, raf = 0, epoch = 0, lastFrame = 0, credit = 0;
let phase = 'loading', resumeOnVisible = false, chartDirty = true, lastChart = 0;
let visualTime = 0, initialDistance = 0;

function normalize(q) {
  let size = Number(q.size), kind = q.kind, seed = Number(q.seed);
  if (!['maze', 'loops', 'obstacles', 'open'].includes(kind)) kind = 'maze';
  if (![20, 30, 40].includes(size)) size = 20;
  if (kind === 'obstacles' || kind === 'open') size = 20;
  if (size === 40 && kind === 'maze') kind = 'loops';
  if (!Number.isInteger(seed) || seed < 1 || seed > 2147483647) seed = 1;
  return { size, kind, seed };
}
const terminal = () => snapshot && (snapshot.fitness === snapshot.shortest || snapshot.generation >= snapshot.limit);

function request(type, extra = {}) {
  pending = true;
  const id = ++requestId;
  clearTimeout(timeout);
  timeout = setTimeout(() => fail('The engine took too long to respond. Check your connection and retry.'), 15000);
  worker.postMessage({ type, id, runId, ...extra });
}

function fail(message) {
  setRunning(false);
  phase = 'error'; pending = false;
  clearTimeout(timeout);
  worker?.terminate();
  $('loading').hidden = false;
  $('loading-title').textContent = 'Let’s try that again';
  $('loading-detail').textContent = message;
  $('retry').hidden = false;
  $('status-title').textContent = 'Engine unavailable';
  $('goal').textContent = 'Your settings are saved. Retry to start this maze again.';
  $('lab').setAttribute('aria-busy', 'false');
  syncControls();
}

function boot() {
  if (!ctx || typeof Worker === 'undefined') {
    fail('This browser does not support the canvas or worker features this demo needs. Try a current browser.');
    return;
  }
  worker?.terminate();
  setRunning(false);
  phase = 'loading'; snapshot = null; history = []; pending = false;
  $('loading').hidden = false; $('retry').hidden = true;
  $('loading-title').textContent = 'Waking up the population…';
  $('loading-detail').textContent = 'Loading the C++ simulation engine.';
  $('lab').setAttribute('aria-busy', 'true');
  syncControls();
  try {
    worker = new Worker(new URL('./engine-worker.js?v=3', import.meta.url), { type: 'module' });
    worker.onerror = (event) => { event.preventDefault(); fail('The simulation could not load. Check your connection and retry.'); };
    worker.onmessageerror = () => fail('The engine returned an unreadable response. Please retry.');
    worker.onmessage = ({ data }) => {
      if (data.runId !== runId || data.id !== requestId || phase === 'error') return;
      clearTimeout(timeout); pending = false;
      if (data.type === 'error') { fail('The simulation could not start. Check your connection and retry.'); return; }
      const first = !!data.cells;
      snapshot = { ...snapshot, ...data };
      if (first) {
        history = []; heat = new Uint16Array(data.width * data.height);
        seen = new Int16Array(heat.length); initialDistance = data.shortest;
      }
      history.push(...data.samples); chartDirty = true;
      updateHeat();
      phase = 'ready'; $('loading').hidden = true; $('lab').setAttribute('aria-busy', 'false');
      if (terminal()) setRunning(false);
      else if (first || data.extended) setRunning(desiredRunning && !document.hidden);
      updateStats(); syncControls(); fitCanvas(); draw(visualTime); if (first || !running) renderChart();
    };
    runId++;
    request('create', { state });
  } catch { fail('The simulation worker could not start. Please retry in a current browser.'); }
}

function restart(next, autoplay = running) {
  desiredRunning = autoplay;
  setRunning(false);
  state = normalize(next); runId++; phase = 'loading';
  $('lab').setAttribute('aria-busy', 'true');
  // The worker processes messages in order; runId discards responses from the previous run.
  syncControls(); request('create', { state });
}

function syncControls() {
  const ready = phase === 'ready';
  for (const control of document.querySelectorAll('[data-engine]')) control.disabled = !ready;
  $('run').disabled = !ready || terminal();
  $('continue').hidden = !ready || !snapshot || snapshot.fitness === snapshot.shortest || snapshot.generation < snapshot.limit || snapshot.limit >= snapshot.maxBudget;
  $('continue').disabled = !ready || pending;
  $('step').disabled = !ready || pending || terminal();
  $('run').innerHTML = running ? '<span class="pause-icon" aria-hidden="true"></span> Pause' : '<span class="play-icon" aria-hidden="true"></span> Run';
  $('run').setAttribute('aria-label', running ? 'Pause evolution' : 'Run evolution');
  $('size-pick').value = String(state.size); $('kind-pick').value = state.kind;
  $('size-pick').disabled = !ready || ['open', 'obstacles'].includes(state.kind);
  $('kind-pick').querySelector('[value="maze"]').disabled = state.size === 40;
  $('new-maze').innerHTML = ['open', 'obstacles'].includes(state.kind) ? 'New run' : 'New maze';
  $('board-title').textContent = TITLES[state.kind];
  $('map-size').textContent = `${state.size} × ${state.size}`;
  $('run-state').textContent = phase === 'error' ? 'Offline' : !ready ? 'Loading' : terminal() ? (snapshot.fitness === snapshot.shortest ? 'Optimal' : 'Budget reached') : running ? 'Evolving' : 'Paused';
  $('run-state').dataset.running = String(running);
  $('setting-note').textContent = ['open', 'obstacles'].includes(state.kind) ? 'Fixed 20 × 20 layout. New run changes the population seed.' : state.size === 40 ? '40 × 40 uses loops to keep the search practical.' : 'Same seed, same maze, same evolution.';
  for (const button of document.querySelectorAll('[data-preset]')) {
    const preset = PRESETS[button.dataset.preset];
    button.setAttribute('aria-pressed', String(Object.keys(preset).every((k) => preset[k] === state[k])));
  }
  const q = new URLSearchParams(state);
  const link = `${location.pathname}?${q}`;
  $('share').href = link;
  if (`${location.pathname}${location.search}` !== link) {
    try { window.history.replaceState(null, '', link); } catch { /* Sharing still works in sandboxed previews. */ }
  }
}

function setRunning(on) {
  const next = !!on && phase === 'ready' && !terminal();
  // Cancel and invalidate every older callback before scheduling exactly one loop.
  cancelAnimationFrame(raf); raf = 0; epoch++; running = next; lastFrame = 0; credit = 0;
  if (running) {
    const token = epoch;
    const frame = (now) => {
      if (!running || token !== epoch) return;
      const elapsed = lastFrame ? Math.min(100, now - lastFrame) : 0;
      lastFrame = now;
      if (!motion.matches) visualTime += elapsed;
      credit = Math.min(20, credit + elapsed / 1000 * 12 * Number($('speed').value));
      if (!pending && credit >= 1) {
        const count = Math.floor(credit); credit -= count;
        request('step', { count });
      }
      if (!motion.matches) draw(visualTime);
      if (chartDirty && now - lastChart > 100) { renderChart(); lastChart = now; }
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
  }
  if (phase === 'ready') { syncControls(); updateStatus(); }
}

function updateHeat() {
  heat.fill(0); seen.fill(-1);
  const { paths, lengths, population, stride, width } = snapshot;
  for (let i = 0; i < population; i++) {
    const offset = i * stride * 2;
    for (let j = 0; j < lengths[i]; j++) {
      const index = paths[offset + j * 2 + 1] * width + paths[offset + j * 2];
      if (seen[index] !== i) { seen[index] = i; heat[index]++; }
    }
  }
}

function fitCanvas() {
  const px = Math.round(canvas.getBoundingClientRect().width * Math.min(devicePixelRatio || 1, 2));
  if (px > 0 && canvas.width !== px) { canvas.width = canvas.height = px; draw(visualTime); }
}

function draw(time = 0) {
  if (!snapshot || !ctx) return;
  const { width, height, cells, population, stride, paths, lengths, optimal } = snapshot;
  const cell = canvas.width / width;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = '#0b0f14'; ctx.fillRect(0, 0, canvas.width, canvas.height);
  const showHeat = $('show-heat').checked;
  for (let i = 0; i < cells.length; i++) {
    const x = i % width * cell, y = Math.floor(i / width) * cell;
    if (cells[i]) {
      ctx.fillStyle = '#536476';
      ctx.fillRect(x + .5, y + .5, cell - 1, cell - 1);
    } else {
      ctx.fillStyle = '#111b24'; ctx.fillRect(x + .5, y + .5, cell - 1, cell - 1);
      if (showHeat && heat[i]) {
        ctx.fillStyle = `rgba(79,214,196,${0.08 + .38 * Math.sqrt(heat[i] / population)})`;
        ctx.fillRect(x + .5, y + .5, cell - 1, cell - 1);
      }
    }
  }
  const trace = (xy, offset, length) => {
    ctx.beginPath();
    for (let j = 0; j < length; j++) {
      const x = (xy[offset + j * 2] + .5) * cell, y = (xy[offset + j * 2 + 1] + .5) * cell;
      if (j) ctx.lineTo(x, y); else ctx.moveTo(x, y);
    }
    ctx.stroke();
  };
  ctx.lineJoin = 'round'; ctx.lineCap = 'round';
  if ($('show-population').checked) {
    ctx.strokeStyle = 'rgba(146,224,219,.12)'; ctx.lineWidth = Math.max(1, cell / 12);
    for (let i = population - 1; i > 0; i--) trace(paths, i * stride * 2, lengths[i]);
  }
  if ($('show-optimal').checked) {
    ctx.strokeStyle = '#eef7ff'; ctx.lineWidth = Math.max(1.5, cell / 8);
    ctx.setLineDash([cell * .25, cell * .35]); trace(optimal, 0, optimal.length / 2); ctx.setLineDash([]);
  }
  ctx.strokeStyle = '#ffb454'; ctx.lineWidth = Math.max(2, cell / 5);
  ctx.shadowColor = 'rgba(255,180,84,.6)'; ctx.shadowBlur = cell / 2;
  trace(paths, 0, lengths[0]); ctx.shadowBlur = 0;
  // These dots replay real paths in this snapshot, not additional simulated agents.
  if ($('show-population').checked && !motion.matches && running) {
    ctx.fillStyle = '#9cf3e3';
    for (let i = 1; i < Math.min(population, 25); i++) {
      const progress = ((time / 4500 + i / 24) % 1) * (lengths[i] - 1);
      const j = Math.floor(progress), f = progress - j, offset = i * stride * 2 + j * 2;
      const next = Math.min(j + 1, lengths[i] - 1) * 2 + i * stride * 2;
      const x = (paths[offset] * (1 - f) + paths[next] * f + .5) * cell;
      const y = (paths[offset + 1] * (1 - f) + paths[next + 1] * f + .5) * cell;
      ctx.beginPath(); ctx.arc(x, y, Math.max(1, cell / 12), 0, Math.PI * 2); ctx.fill();
    }
  }
  const end = (lengths[0] - 1) * 2;
  ctx.fillStyle = '#fff3d8'; ctx.beginPath(); ctx.arc((paths[end] + .5) * cell, (paths[end + 1] + .5) * cell, cell * .2, 0, Math.PI * 2); ctx.fill();
  const marker = (x, y, label, color) => {
    ctx.fillStyle = color; ctx.fillRect(x * cell + cell * .08, y * cell + cell * .08, cell * .84, cell * .84);
    ctx.fillStyle = '#0b0f14'; ctx.font = `bold ${Math.max(7, cell * .65)}px sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillText(label, (x + .5) * cell, (y + .54) * cell);
  };
  marker(0, 0, 'S', '#4fd6c4'); marker(width - 1, height - 1, 'G', '#ffb454');
}

function updateStatus() {
  if (!snapshot) return;
  let title, message;
  if (snapshot.fitness === snapshot.shortest) {
    title = 'The shortest way through.'; message = `Optimal route found in ${snapshot.generation} generations. Replay it or explore a new maze.`;
  } else if (snapshot.generation >= snapshot.limit) {
    title = snapshot.reached ? 'A route worth keeping.' : 'A tougher maze.';
    message = `${snapshot.limit.toLocaleString('en-US')} generation budget reached. ${snapshot.reached ? 'A valid route was found, but it is not optimal.' : 'The population has not reached the exit yet.'} ${snapshot.limit < snapshot.maxBudget ? 'Keep evolving to continue this population.' : 'Try a new maze or replay another seed.'}`;
  } else if (snapshot.reached) {
    title = 'Found it. Now, make it shorter.'; message = `First exit at generation ${snapshot.reachedAt}. ${running ? 'Now evolving' : 'Run to evolve'} a more efficient route.`;
  } else {
    title = running ? 'Searching for a way through.' : 'A hundred routes. Your move.';
    message = running ? 'The brightest corridors are where the population is searching.' : 'Run to watch evolution, or Step through one generation at a time.';
  }
  if ($('status-title').textContent !== title) $('status-title').textContent = title;
  if ($('goal').textContent !== message) $('goal').textContent = message;
}

function updateStats() {
  const s = snapshot, moves = s.lengths[0] - 1;
  const remaining = Math.max(0, Math.round((s.fitness - moves) / s.stride));
  $('s-limit').textContent = `of ${s.limit.toLocaleString('en-US')}`;
  $('s-gen').textContent = s.generation.toLocaleString('en-US');
  $('s-fit').textContent = s.fitness.toLocaleString('en-US');
  $('s-short').textContent = s.shortest;
  $('s-seed').textContent = state.seed;
  $('s-genome').textContent = `${s.stride - 1} moves`;
  $('s-population').textContent = `${s.population} routes`;
  $('distance-label').textContent = s.reached ? 'Best route' : 'Distance to exit';
  $('s-distance').textContent = s.reached ? moves : remaining;
  $('distance-unit').textContent = s.reached ? `${moves - s.shortest} steps above optimal` : 'steps remaining';
  $('s-distance').dataset.reached = String(s.reached);
  $('chart-note').textContent = s.reached ? `First solution: generation ${s.reachedAt} · ${moves - s.shortest} excess steps now` : `${Math.max(0, initialDistance - remaining)} steps closer to the exit than the start`;
  canvas.setAttribute('aria-label', `${s.width} by ${s.height} maze. Generation ${s.generation}. ${s.reached ? `Best route ${moves} steps; optimal ${s.shortest}.` : `${remaining} steps from the exit.`}`);
  updateStatus();
}

function renderChart() {
  if (!snapshot || !chartDirty) return;
  chartDirty = false;
  const max = Math.max(1, history[0]), last = history.length - 1;
  const x = (i) => 40 + i / Math.max(1, last) * 306;
  const y = (f) => 108 - Math.min(1, f / max) * 92;
  // One sample per horizontal pixel is enough; keep the final sample and full data in memory.
  const stride = Math.max(1, Math.ceil(history.length / 306));
  const points = history.flatMap((f, i) => i % stride === 0 || i === last ? [`${x(i).toFixed(1)},${y(f).toFixed(1)}`] : []).join(' ');
  $('chart-content').innerHTML = `<line class="chart-grid" x1="40" y1="16" x2="346" y2="16"/><line class="chart-grid" x1="40" y1="108" x2="346" y2="108"/><text x="34" y="20" text-anchor="end">${max}</text><text x="34" y="111" text-anchor="end">0</text><polygon class="chart-area" points="40,108 ${points} ${x(last)},108"/><polyline points="${points}"/><circle cx="${x(last)}" cy="${y(history[last])}" r="3"/><text x="40" y="128">gen 0</text><text x="346" y="128" text-anchor="end">${last ? `gen ${last}` : 'Ready to evolve'}</text>`;
  $('chart-description').textContent = `Best fitness decreased from ${history[0]} to ${history[last]} over ${last} generations. Lower is better.${snapshot.reachedAt !== null ? ` First solution at generation ${snapshot.reachedAt}.` : ''}`;
}

$('run').addEventListener('click', () => { setRunning(!running); desiredRunning = running; });
$('step').addEventListener('click', () => {
  setRunning(false); desiredRunning = false;
  if (!pending && phase === 'ready' && !terminal()) { request('step', { count: 1 }); syncControls(); }
});
$('continue').addEventListener('click', () => {
  if (phase !== 'ready' || pending || !snapshot || snapshot.generation < snapshot.limit || snapshot.limit >= snapshot.maxBudget) return;
  desiredRunning = true; request('extend'); syncControls();
});
$('reset').addEventListener('click', () => restart(state));
$('new-maze').addEventListener('click', () => restart({ ...state, seed: 1 + Math.floor(Math.random() * 2147483646) }));
$('size-pick').addEventListener('change', (e) => restart({ ...state, size: e.target.value }));
$('kind-pick').addEventListener('change', (e) => restart({ ...state, kind: e.target.value }));
for (const button of document.querySelectorAll('[data-preset]')) button.addEventListener('click', () => restart(PRESETS[button.dataset.preset]));
for (const id of ['show-heat', 'show-population', 'show-optimal']) $(id).addEventListener('change', () => draw(visualTime));
$('speed').addEventListener('input', () => {
  const speed = $('speed').value;
  $('speed-value').textContent = `${speed}×`;
  $('speed').setAttribute('aria-valuetext', `${speed} times speed, up to ${Number(speed) * 12} generations per second`);
});
$('retry').addEventListener('click', boot);
$('copy').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText($('share').href); $('copy-status').textContent = 'Copied. The link replays this maze and seed from the start.'; }
  catch { $('copy-status').textContent = 'Copy unavailable. Use the Open replay link to copy the address.'; $('share').focus(); }
});
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { resumeOnVisible = running; setRunning(false); }
  else if (resumeOnVisible) { resumeOnVisible = false; setRunning(true); }
});
motion.addEventListener('change', () => { if (motion.matches) { desiredRunning = false; setRunning(false); draw(visualTime); } });
new ResizeObserver(fitCanvas).observe(canvas);
boot();
