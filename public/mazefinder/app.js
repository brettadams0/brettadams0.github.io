import { EvolutionHistory, changedEdges } from './evolution-history.js?v=4';

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
let journal = new EvolutionHistory(), view = 'live', selectedEvent = null;
let replayPlaying = false, replayElapsed = 3000, resumeAfterReplay = false, resumeReplayOnVisible = false;
let liveComparison = null, replayComparison = null, timelineDirty = true;
const optionNodes = new Map();

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
  view = 'live'; selectedEvent = null; replayPlaying = false;
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
  resetJournal();
  $('loading').hidden = false; $('retry').hidden = true;
  $('loading-title').textContent = 'Waking up the population…';
  $('loading-detail').textContent = 'Loading the C++ simulation engine.';
  $('lab').setAttribute('aria-busy', 'true');
  syncControls();
  try {
    worker = new Worker(new URL('./engine-worker.js?v=4', import.meta.url), { type: 'module' });
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
      journal.append(data.events);
      if (data.events.length) {
        timelineDirty = true;
        const event = data.events.at(-1);
        if (event.generation > 0) liveComparison = { event, until: performance.now() + 1400 };
        if (view === 'live') showBreakthrough(data.events);
      }
      if (liveComparison) liveComparison.edges = changedEdges(liveComparison.event.previousPath, data.paths.subarray(0, data.lengths[0] * 2));
      updateHeat();
      phase = 'ready'; $('loading').hidden = true; $('lab').setAttribute('aria-busy', 'false');
      if (view === 'live') {
        if (terminal()) setRunning(false);
        else if (first || data.extended) setRunning(desiredRunning && !document.hidden);
        updateStats();
      }
      syncControls(); fitCanvas();
      if (view === 'live') draw(visualTime);
      if (first || !running) { renderChart(); renderTimeline(); }
    };
    runId++;
    request('create', { state });
  } catch { fail('The simulation worker could not start. Please retry in a current browser.'); }
}

function restart(next, autoplay = view === 'replay' ? resumeAfterReplay : running) {
  desiredRunning = autoplay;
  setRunning(false);
  resetJournal();
  state = normalize(next); runId++; phase = 'loading';
  $('lab').setAttribute('aria-busy', 'true');
  // The worker processes messages in order; runId discards responses from the previous run.
  syncControls(); request('create', { state });
}

function syncControls() {
  const ready = phase === 'ready', reviewing = view === 'replay';
  $('lab').dataset.view = view;
  $('view-label').textContent = reviewing ? 'RECORDED BREAKTHROUGH' : 'LIVE EVOLUTION';
  $('population-key').hidden = reviewing;
  $('replay-controls').hidden = !reviewing;
  $('replay-key').hidden = !reviewing && !liveComparison;
  $('event-pick').disabled = !ready || journal.events.length < 2;
  $('play-route').disabled = !ready || motion.matches;
  if (reviewing) $('replay-label').textContent = `Recorded generation ${selectedEvent.generation}${motion.matches ? ' · static comparison' : ''}`;
  $('play-route').textContent = replayPlaying ? 'Pause route' : 'Play route';
  $('return-live').disabled = !ready;
  $('speed').title = reviewing ? 'Return to live to change evolution speed' : '';
  for (const id of ['show-heat', 'show-population']) $(id).closest('label').hidden = reviewing;
  for (const control of document.querySelectorAll('[data-engine]')) control.disabled = !ready;
  $('run').disabled = !ready || reviewing || terminal();
  $('speed').disabled = !ready || reviewing;
  $('continue').hidden = reviewing || !ready || !snapshot || snapshot.fitness === snapshot.shortest || snapshot.generation < snapshot.limit || snapshot.limit >= snapshot.maxBudget;
  $('continue').disabled = !ready || pending;
  $('step').disabled = !ready || reviewing || pending || terminal();
  $('run').innerHTML = running ? '<span class="pause-icon" aria-hidden="true"></span> Pause' : '<span class="play-icon" aria-hidden="true"></span> Run';
  $('run').setAttribute('aria-label', running ? 'Pause evolution' : 'Run evolution');
  $('size-pick').value = String(state.size); $('kind-pick').value = state.kind;
  $('size-pick').disabled = !ready || ['open', 'obstacles'].includes(state.kind);
  $('kind-pick').querySelector('[value="maze"]').disabled = state.size === 40;
  $('new-maze').innerHTML = ['open', 'obstacles'].includes(state.kind) ? 'New run' : 'New maze';
  $('board-title').textContent = TITLES[state.kind];
  $('map-size').textContent = `${state.size} × ${state.size}`;
  $('run-state').textContent = phase === 'error' ? 'Offline' : !ready ? 'Loading' : reviewing ? `Replay · gen ${selectedEvent.generation}` : terminal() ? (snapshot.fitness === snapshot.shortest ? 'Optimal' : 'Budget reached') : running ? 'Evolving' : 'Paused';
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

function scheduleAnimation() {
  cancelAnimationFrame(raf); raf = 0; epoch++; lastFrame = 0;
  if (!running && !replayPlaying) return;
  const token = epoch;
  const frame = (now) => {
    if (token !== epoch) return;
    const elapsed = lastFrame ? Math.min(100, now - lastFrame) : 0;
    lastFrame = now;
    if (view === 'replay') {
      replayElapsed = Math.min(3000, replayElapsed + elapsed);
      draw();
      if (replayElapsed === 3000) { replayPlaying = false; syncControls(); }
    } else if (running) {
      if (!motion.matches) visualTime += elapsed;
      credit = Math.min(20, credit + elapsed / 1000 * 12 * Number($('speed').value));
      if (!pending && credit >= 1) {
        const count = Math.floor(credit); credit -= count;
        request('step', { count });
      }
      if (!motion.matches) draw(visualTime);
      if ((chartDirty || timelineDirty) && now - lastChart > 100) { renderChart(); renderTimeline(); lastChart = now; }
    }
    if ((running || replayPlaying) && token === epoch) raf = requestAnimationFrame(frame);
  };
  raf = requestAnimationFrame(frame);
}

function setRunning(on) {
  running = !!on && view === 'live' && phase === 'ready' && !terminal();
  credit = 0;
  scheduleAnimation();
  if (phase === 'ready') { syncControls(); updateStatus(); }
}

function resetJournal() {
  view = 'live'; selectedEvent = null; replayPlaying = false; resumeAfterReplay = false;
  resumeOnVisible = false; resumeReplayOnVisible = false;
  liveComparison = replayComparison = null;
  journal = new EvolutionHistory(); optionNodes.clear(); timelineDirty = true;
  $('event-pick').replaceChildren(new Option('Improvements will appear here', ''));
  $('milestones').replaceChildren(); $('chart-content').replaceChildren();
  $('breakthrough').textContent = 'Watch the best route change as evolution explores.';
  scheduleAnimation();
}

function eventLabel(event) {
  if (event.generation === 0) return 'Starting population';
  if (event.optimal) return 'Optimal route';
  if (event.firstSolution) return 'First exit';
  if (event.reached) return `${event.previousMoves - event.moves} steps removed`;
  return 'Closer to the exit';
}

function showBreakthrough(events) {
  const first = events.find((event) => event.firstSolution), last = events.at(-1);
  if (!last || last.generation === 0) return;
  const text = `${first ? `Exit found at generation ${first.generation}. ` : ''}${last.optimal ? `Optimal: ${last.moves} steps.` : `${eventLabel(last)} · generation ${last.generation}.`}`;
  $('breakthrough').textContent = text;
}

function inspectEvent(generation) {
  const event = journal.get(generation);
  if (!event || phase !== 'ready') return;
  if (view === 'live') resumeAfterReplay = running;
  running = false; resumeOnVisible = false;
  view = 'replay'; selectedEvent = event; replayPlaying = false; replayElapsed = 3000;
  replayComparison = changedEdges(event.previousPath, event.path);
  $('replay-label').textContent = `Recorded generation ${event.generation}`;
  $('event-pick').value = String(event.generation);
  $('breakthrough').textContent = event.generation === 0 ? 'The starting population, before any evolution.'
    : `Previous: generation ${event.previousGeneration} · recorded result: generation ${event.generation}. Thick segments are new; dashed segments were abandoned.`;
  chartDirty = true; timelineDirty = true;
  scheduleAnimation(); syncControls(); updateStats(); renderChart(); renderTimeline(); draw();
}

function returnToLive() {
  if (view !== 'replay') return;
  const resume = resumeAfterReplay;
  view = 'live'; selectedEvent = null; replayPlaying = false;
  $('event-pick').value = '';
  showBreakthrough([journal.events.at(-1)]);
  chartDirty = true; timelineDirty = true;
  resumeOnVisible = resume && document.hidden;
  setRunning(resume && !document.hidden);
  updateStats(); renderChart(); renderTimeline(); draw(visualTime);
  ($('run').disabled ? $('event-pick') : $('run')).focus({ preventScroll: true });
}

function renderTimeline() {
  if (!timelineDirty) return;
  timelineDirty = false;
  const retained = selectedEvent && !journal.get(selectedEvent.generation) ? [...journal.events, selectedEvent].sort((a, b) => a.generation - b.generation) : journal.events;
  const ids = new Set(retained.map((event) => event.generation));
  for (const [generation, node] of optionNodes) {
    if (!ids.has(generation)) { node.remove(); optionNodes.delete(generation); }
  }
  $('event-pick').options[0].textContent = journal.events.length > 1 ? 'Choose a breakthrough…' : 'Improvements will appear here';
  for (const event of retained) {
    if (!optionNodes.has(event.generation)) {
      const route = event.reached ? `${event.moves} steps` : `fitness ${event.fitness}`;
      const node = new Option(`Gen ${event.generation} · ${eventLabel(event)} · ${route}`, String(event.generation));
      optionNodes.set(event.generation, node); $('event-pick').append(node);
    }
  }
  const important = journal.events.filter((event) => event.firstSolution || event.optimal);
  const latest = journal.events.at(-1);
  if (latest?.generation && !important.includes(latest)) important.push(latest);
  if (selectedEvent && !important.includes(selectedEvent)) important.push(selectedEvent);
  const existing = new Map([...$('milestones').children].map((node) => [Number(node.dataset.generation), node]));
  for (const [generation, node] of existing) if (!important.some((event) => event.generation === generation)) node.remove();
  for (const event of important) {
    let button = existing.get(event.generation);
    if (!button) {
      button = document.createElement('button'); button.type = 'button'; button.dataset.generation = event.generation;
      button.addEventListener('click', () => inspectEvent(event.generation)); $('milestones').append(button);
    }
    button.textContent = `${event.firstSolution ? 'First exit' : event.optimal ? 'Optimal' : event === latest ? 'Latest improvement' : 'Selected checkpoint'} · ${event.generation}`;
    button.setAttribute('aria-pressed', String(selectedEvent?.generation === event.generation));
  }
  $('timeline-note').textContent = `${journal.events.length} recorded checkpoints${journal.events.length === 256 ? ' (older minor improvements condensed)' : ''}. Select a chart point or choose a generation.`;
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
  const reviewing = view === 'replay';
  const comparison = reviewing ? { event: selectedEvent, edges: replayComparison }
    : liveComparison && (motion.matches || !running || performance.now() < liveComparison.until) ? liveComparison : null;
  const bestPath = reviewing ? selectedEvent.path : paths.subarray(0, lengths[0] * 2);
  const visiblePoints = reviewing && replayElapsed < 3000 ? Math.max(1, Math.ceil(bestPath.length / 2 * replayElapsed / 3000)) : bestPath.length / 2;
  const cell = canvas.width / width;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = '#0b0f14'; ctx.fillRect(0, 0, canvas.width, canvas.height);
  const showHeat = !reviewing && $('show-heat').checked;
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
  if (!reviewing && $('show-population').checked) {
    ctx.strokeStyle = 'rgba(146,224,219,.12)'; ctx.lineWidth = Math.max(1, cell / 12);
    for (let i = population - 1; i > 0; i--) trace(paths, i * stride * 2, lengths[i]);
  }
  if ($('show-optimal').checked) {
    ctx.strokeStyle = '#eef7ff'; ctx.lineWidth = Math.max(1.5, cell / 8);
    ctx.setLineDash([cell * .25, cell * .35]); trace(optimal, 0, optimal.length / 2); ctx.setLineDash([]);
  }
  if (comparison?.event.previousPath.length) {
    ctx.strokeStyle = 'rgba(196,204,214,.5)'; ctx.lineWidth = Math.max(1.5, cell / 8);
    ctx.setLineDash([cell * .2, cell * .3]);
    trace(comparison.event.previousPath, 0, comparison.event.previousPath.length / 2); ctx.setLineDash([]);
  }
  ctx.strokeStyle = '#ffb454'; ctx.lineWidth = Math.max(2, cell / 5);
  ctx.shadowColor = 'rgba(255,180,84,.6)'; ctx.shadowBlur = cell / 2;
  trace(bestPath, 0, visiblePoints); ctx.shadowBlur = 0;
  if (comparison && (!reviewing || replayElapsed === 3000)) {
    const segments = (edges) => {
      ctx.beginPath();
      for (const [x1, y1, x2, y2] of edges) {
        ctx.moveTo((x1 + .5) * cell, (y1 + .5) * cell); ctx.lineTo((x2 + .5) * cell, (y2 + .5) * cell);
      }
      ctx.stroke();
    };
    ctx.strokeStyle = '#c4ccd6'; ctx.lineWidth = Math.max(1.5, cell / 9);
    ctx.setLineDash([cell * .12, cell * .22]); segments(comparison.edges.removed); ctx.setLineDash([]);
    ctx.strokeStyle = '#ffe0ad'; ctx.lineWidth = Math.max(3, cell / 3); segments(comparison.edges.added);
  }
  // These dots replay real paths in this snapshot, not additional simulated agents.
  if (!reviewing && $('show-population').checked && !motion.matches && running) {
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
  const end = (visiblePoints - 1) * 2;
  ctx.fillStyle = '#fff3d8'; ctx.beginPath(); ctx.arc((bestPath[end] + .5) * cell, (bestPath[end + 1] + .5) * cell, cell * .2, 0, Math.PI * 2); ctx.fill();
  const marker = (x, y, label, color) => {
    ctx.fillStyle = color; ctx.fillRect(x * cell + cell * .08, y * cell + cell * .08, cell * .84, cell * .84);
    ctx.fillStyle = '#0b0f14'; ctx.font = `bold ${Math.max(7, cell * .65)}px sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillText(label, (x + .5) * cell, (y + .54) * cell);
  };
  marker(0, 0, 'S', '#4fd6c4'); marker(width - 1, height - 1, 'G', '#ffb454');
}

function updateStatus() {
  if (!snapshot) return;
  let title, message;
  if (view === 'replay') {
    title = `${eventLabel(selectedEvent)} · gen ${selectedEvent.generation}`;
    message = `Recorded route. ${selectedEvent.reached ? `${selectedEvent.moves} steps versus optimal ${snapshot.shortest}.` : 'This route has not reached the exit.'} Return to live to continue the experiment.`;
  } else if (snapshot.fitness === snapshot.shortest) {
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
  const reviewing = view === 'replay';
  const s = reviewing ? { ...snapshot, ...selectedEvent } : snapshot;
  const moves = reviewing ? selectedEvent.moves : s.lengths[0] - 1;
  $('s-success').textContent = `${s.successfulRoutes} / ${s.population}`;
  $('success-meter').value = s.successfulRoutes; $('success-meter').max = s.population;
  $('population-note').textContent = reviewing ? `Population at recorded generation ${s.generation}; live heat and routes are hidden.` : 'The whole population, not just the leader.';
  const remaining = Math.max(0, Math.round((s.fitness - moves) / s.stride));
  $('s-limit').textContent = reviewing ? 'recorded generation' : `of ${s.limit.toLocaleString('en-US')}`;
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
  $('chart-note').textContent = reviewing ? `Inspecting generation ${s.generation}. The chart retains the live run’s full history.` : s.reached ? `First solution: generation ${s.reachedAt} · ${moves - s.shortest} excess steps now` : `${Math.max(0, initialDistance - remaining)} steps closer to the exit than the start`;
  canvas.setAttribute('aria-label', `${reviewing ? 'Recorded view. ' : ''}${s.width} by ${s.height} maze. Generation ${s.generation}. ${s.reached ? `Best route ${moves} steps; optimal ${s.shortest}.` : `${remaining} steps from the exit.`}`);
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
  const markers = journal.events.map((event) => `<circle class="event-marker${event.firstSolution || event.optimal ? ' milestone-marker' : ''}${selectedEvent?.generation === event.generation ? ' selected-marker' : ''}" data-generation="${event.generation}" cx="${x(event.generation)}" cy="${y(event.fitness)}" r="${event.firstSolution || event.optimal ? 4 : 2}"><title>Generation ${event.generation}: ${eventLabel(event)}</title></circle>`).join('');
  $('chart-content').insertAdjacentHTML('beforeend', markers);
  $('chart-description').textContent = `Best fitness decreased from ${history[0]} to ${history[last]} over ${last} generations. Lower is better.${snapshot.reachedAt !== null ? ` First solution at generation ${snapshot.reachedAt}.` : ''}`;
}

$('run').addEventListener('click', () => { setRunning(!running); desiredRunning = running; });
$('step').addEventListener('click', () => {
  setRunning(false); desiredRunning = false;
  if (view === 'live' && !pending && phase === 'ready' && !terminal()) { request('step', { count: 1 }); syncControls(); }
});
$('continue').addEventListener('click', () => {
  if (view !== 'live' || phase !== 'ready' || pending || !snapshot || snapshot.generation < snapshot.limit || snapshot.limit >= snapshot.maxBudget) return;
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
$('event-pick').addEventListener('change', (event) => { if (event.target.value !== '') inspectEvent(Number(event.target.value)); });
$('history').addEventListener('click', (event) => {
  if (!snapshot || !journal.events.length) return;
  const point = $('history').createSVGPoint(); point.x = event.clientX; point.y = event.clientY;
  const local = point.matrixTransform($('history').getScreenCTM().inverse());
  const maximum = Math.max(1, history[0]), last = Math.max(1, history.length - 1);
  let nearest = null, distance = 18;
  for (const candidate of journal.events) {
    const d = Math.hypot(local.x - (40 + candidate.generation / last * 306), local.y - (108 - candidate.fitness / maximum * 92));
    if (d < distance) { nearest = candidate; distance = d; }
  }
  if (nearest) { inspectEvent(nearest.generation); $('event-pick').focus({ preventScroll: true }); }
});
$('return-live').addEventListener('click', returnToLive);
$('play-route').addEventListener('click', () => {
  if (view !== 'replay' || motion.matches || phase !== 'ready') return;
  if (replayElapsed >= 3000) replayElapsed = 0;
  replayPlaying = !replayPlaying; scheduleAnimation(); syncControls();
});
$('retry').addEventListener('click', boot);
$('copy').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText($('share').href); $('copy-status').textContent = 'Copied. The link replays this maze and seed from the start.'; }
  catch { $('copy-status').textContent = 'Copy unavailable. Use the Open replay link to copy the address.'; $('share').focus(); }
});
document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    resumeOnVisible = running; resumeReplayOnVisible = replayPlaying;
    replayPlaying = false; setRunning(false);
  } else if (view === 'replay' && resumeReplayOnVisible && !motion.matches) {
    resumeReplayOnVisible = false; replayPlaying = true; scheduleAnimation(); syncControls();
  } else if (view === 'live' && resumeOnVisible) { resumeOnVisible = false; setRunning(true); }
});
motion.addEventListener('change', () => {
  if (motion.matches) {
    desiredRunning = false; resumeAfterReplay = false; resumeOnVisible = false; resumeReplayOnVisible = false;
    replayPlaying = false; replayElapsed = 3000; setRunning(false); draw(visualTime);
  } else syncControls();
});
new ResizeObserver(fitCanvas).observe(canvas);
boot();
