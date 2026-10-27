// MazeFinder live demo: drives MazeFinder's C++ core, compiled to WebAssembly (wasm/, copied from
// the MazeFinder repo's web/ build at fb10368). Mazes are generated in that core from a size and a
// seed; the two fixed 20x20 ones are its mazes/obstacles.txt and mazes/open.txt, verbatim.
import createModule from './wasm/mazefinder.js';

const FIXED = {
 "obstacles": "...........#........\n...........#........\n...#.......#........\n...#.......#........\n...#.......#........\n...#.......#...#....\n...#...#...#...#....\n...#...#...#...#....\n...#...#...#...#....\n...#...#...########.\n...#...#...#...#....\n...#...#...#...#....\n...#...#...#...#....\n.......#...#...#....\n.......#.......#....\n.......#.......#....\n#####..#.......#....\n.......#.......#....\n.......#............\n.......#............\n",
 "open": "....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n"
};
const SIZES = [20, 30, 40];
const KINDS = ['maze', 'loops', 'obstacles', 'open'];
const CAP = 1500;
const $ = (id) => document.getElementById(id);
const COLORS = Object.fromEntries(['bg', 'border', 'teal', 'amber', 'good', 'soft'].map((k) =>
  [k, getComputedStyle(document.documentElement).getPropertyValue(`--${k}`).trim()]));

const mf = await createModule();
const create = mf.cwrap('mf_create', 'number', ['string', 'number']);
const generate = mf.cwrap('mf_generate', 'string', ['number', 'number', 'number']);
const canvas = $('maze'), ctx = canvas.getContext('2d');
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
let running = false, history = [], reachedAt = null, state = null;

// ?size=&kind=&seed=, normalized so every link reaches the goal: 40x40 only with loops
// (one-route mazes that size solved 6 of 10 seeds within the cap), fixed mazes at 20.
function normalize(q) {
  let size = Number(q.size), kind = q.kind, seed = Number(q.seed);
  if (!KINDS.includes(kind)) kind = 'maze';
  if (!SIZES.includes(size)) size = 20;
  if (kind === 'obstacles' || kind === 'open') size = 20;
  if (size === 40 && kind === 'maze') kind = 'loops';
  if (!Number.isInteger(seed) || seed < 1 || seed > 2147483647) seed = 1;
  return { size, kind, seed };
}

function syncControls() {
  $('size-pick').value = String(state.size);
  $('kind-pick').value = state.kind;
  $('size-pick').disabled = state.kind === 'obstacles' || state.kind === 'open';
  $('kind-pick').querySelector('option[value="maze"]').disabled = state.size === 40;
  const q = new URLSearchParams({ size: state.size, kind: state.kind, seed: state.seed });
  history_replace(`?${q}`);
  $('share').href = `?${q}`;
  $('share').textContent = `${location.origin}${location.pathname}?${q}`;
}
function history_replace(search) { try { window.history.replaceState(null, '', search); } catch {} }

function newRun(next) {
  state = normalize(next);
  const text = FIXED[state.kind] ?? generate(state.size, state.seed, state.kind === 'loops' ? 1 : 0);
  if (create(text, state.seed) !== 0) throw new Error(mf.UTF8ToString(mf._mf_error()));
  history = [mf._mf_best_fitness()];
  reachedAt = mf._mf_reached_goal() ? 0 : null;
  syncControls();
  draw();
}

function fitCanvas() { // a bitmap as sharp as the screen: CSS width x devicePixelRatio
  const px = Math.round(canvas.getBoundingClientRect().width * (window.devicePixelRatio || 1));
  if (px > 0 && canvas.width !== px) { canvas.width = canvas.height = px; if (state) draw(); }
}

function draw() {
  const w = mf._mf_width(), h = mf._mf_height(), cell = canvas.width / w;
  const cells = new Uint8Array(mf.HEAPU8.buffer, mf._mf_cells(), w * h);
  ctx.fillStyle = COLORS.bg; ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = COLORS.border;
  for (let i = 0; i < w * h; i++) if (cells[i]) ctx.fillRect((i % w) * cell, Math.floor(i / w) * cell, Math.ceil(cell), Math.ceil(cell));
  const pop = mf._mf_population_size(), stride = mf._mf_genome_length() + 1;
  const paths = new Int16Array(mf.HEAP16.buffer, mf._mf_paths(), pop * stride * 2);
  const lens = new Int32Array(mf.HEAP32.buffer, mf._mf_path_lengths(), pop);
  const trace = (xy, offset, n) => {
    ctx.beginPath();
    for (let k = 0; k < n; k++) {
      const x = (xy[offset + k * 2] + 0.5) * cell, y = (xy[offset + k * 2 + 1] + 0.5) * cell;
      k ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
    }
    ctx.stroke();
  };
  ctx.lineJoin = 'round'; ctx.lineCap = 'round';
  ctx.lineWidth = Math.max(1, cell / 10); ctx.strokeStyle = COLORS.soft; ctx.globalAlpha = 0.07;
  for (let i = pop - 1; i > 0; i--) trace(paths, i * stride * 2, lens[i]);
  ctx.globalAlpha = 1;
  if ($('show-optimal').checked) {
    const n = mf._mf_optimal_length();
    ctx.lineWidth = Math.max(2, cell / 2.2); ctx.strokeStyle = COLORS.teal; ctx.globalAlpha = 0.35;
    trace(new Int16Array(mf.HEAP16.buffer, mf._mf_optimal_path(), n * 2), 0, n);
    ctx.globalAlpha = 1;
  }
  ctx.lineWidth = Math.max(2, cell / 4); ctx.strokeStyle = COLORS.amber;
  trace(paths, 0, lens[0]); // fittest first
  ctx.fillStyle = COLORS.teal; ctx.fillRect(cell * 0.2, cell * 0.2, cell * 0.6, cell * 0.6);
  ctx.fillStyle = COLORS.amber; ctx.fillRect((w - 0.8) * cell, (h - 0.8) * cell, cell * 0.6, cell * 0.6);
  stats(lens[0] - 1);
}

function stats(bestMoves) {
  const reached = mf._mf_reached_goal(), optimal = mf._mf_shortest_path();
  $('s-gen').textContent = mf._mf_generation();
  $('s-fit').textContent = mf._mf_best_fitness();
  $('s-short').textContent = `${optimal} steps`;
  $('s-best').textContent = reached ? `${bestMoves} steps vs optimal ${optimal}` : 'not at the goal yet';
  $('s-seed').textContent = state.seed;
  $('goal').textContent = reached ? `Reached the goal at generation ${reachedAt}.`
    : mf._mf_generation() >= CAP ? `Not solved in ${CAP} generations. Try a new maze.` : '';
  const max = Math.max(...history), min = Math.min(...history), n = history.length;
  const pts = history.map((f, i) => `${(i / Math.max(1, n - 1)) * 290 + 5},${90 - ((f - min) / Math.max(1, max - min)) * 80}`).join(' ');
  $('history').innerHTML = `<polyline points="${pts}"/><text x="5" y="98">gen 0</text><text x="295" y="98" text-anchor="end">gen ${n - 1}</text>`;
}

function step(n = 1) {
  for (let i = 0; i < n && mf._mf_generation() < CAP; i++) { // one generation at a time: exact history and goal generation
    mf._mf_step(1);
    history.push(mf._mf_best_fitness());
    if (reachedAt === null && mf._mf_reached_goal()) reachedAt = mf._mf_generation();
  }
  draw();
}

function frame() {
  if (!running) return;
  step(Number($('speed').value));
  if (mf._mf_generation() >= CAP) setRunning(false);
  else requestAnimationFrame(frame);
}

function setRunning(on) {
  running = on && mf._mf_generation() < CAP;
  $('run').textContent = running ? 'Pause' : 'Run';
  if (running) requestAnimationFrame(frame);
}

const restart = (next) => { const was = running; setRunning(false); newRun(next); if (was || !reduced) setRunning(true); };
$('run').addEventListener('click', () => setRunning(!running));
$('step').addEventListener('click', () => { setRunning(false); step(1); });
$('reset').addEventListener('click', () => restart(state));
$('new-maze').addEventListener('click', () => restart({ ...state, seed: 1 + Math.floor(Math.random() * 2147483646) }));
$('size-pick').addEventListener('change', (e) => restart({ ...state, size: e.target.value }));
$('kind-pick').addEventListener('change', (e) => restart({ ...state, kind: e.target.value }));
$('show-optimal').addEventListener('change', draw);
let resume = false;
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { resume = running; setRunning(false); } else if (resume) setRunning(true);
});
new ResizeObserver(fitCanvas).observe(canvas);

newRun(Object.fromEntries(new URLSearchParams(location.search)));
fitCanvas();
if (!reduced) setRunning(true); // under reduced motion it starts paused
