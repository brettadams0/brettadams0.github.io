// MazeFinder live demo: drives MazeFinder's C++ core, compiled to WebAssembly (wasm/, copied from
// the MazeFinder repo's web/ build at 47f92eb). The mazes are that repo's mazes/*.txt, verbatim.
import createModule from './wasm/mazefinder.js';

const MAZES = {
 "maze": ".....#.........#...#\n####.###.#####.#.#.#\n...#...#.....#...#.#\n##.###.#.###.#.#####\n...#...#...#.#.#...#\n.#.#.#####.#.###.#.#\n.#.#.#...#.#.....#.#\n.###.#.#.#.#######.#\n.....#.#.#...#...#.#\n.#####.#.###.#.###.#\n.#.....#.#.....#...#\n.###.#####.#####.###\n...#...#...#...#...#\n##.#.#.#.###.#.###.#\n...#.#.....#.#...#.#\n.###.#######.###.#.#\n.#...#.......#.#.#.#\n.#####.#######.#.#.#\n.......#...........#\n##################..\n",
 "loops": "...........#.......#\n##.#.#.#.#.#.###.#.#\n...#.#...#...#.#.#.#\n.#.#.#######.#.#.#.#\n.#.#.............#.#\n.#.#.###.#.#.#.###.#\n.#...#...#.#.#.#...#\n.#.#.#.###.#.#.#.#.#\n...#...#...#...#.#.#\n##.#.#####.#######.#\n...#.#.............#\n.#.#.#.###.#####.#.#\n.#.#...#.......#...#\n.#########.#######.#\n...........#.....#.#\n.#.###.#.###.#.###.#\n.#.#...#.....#.#...#\n.#.#.#.###.###.#.###\n.#...#.............#\n##################..\n",
 "obstacles": "...........#........\n...........#........\n...#.......#........\n...#.......#........\n...#.......#........\n...#.......#...#....\n...#...#...#...#....\n...#...#...#...#....\n...#...#...#...#....\n...#...#...########.\n...#...#...#...#....\n...#...#...#...#....\n...#...#...#...#....\n.......#...#...#....\n.......#.......#....\n.......#.......#....\n#####..#.......#....\n.......#.......#....\n.......#............\n.......#............\n",
 "open": "....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n"
};
const $ = (id) => document.getElementById(id);
const COLORS = Object.fromEntries(['bg', 'border', 'teal', 'amber', 'good', 'soft'].map((k) =>
  [k, getComputedStyle(document.documentElement).getPropertyValue(`--${k}`).trim()]));

const mf = await createModule();
const create = mf.cwrap('mf_create', 'number', ['string', 'number']);
const ctx = $('maze').getContext('2d');
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
let running = false, seed = 1, history = [], reachedAt = null;

function newRun(newSeed) {
  seed = newSeed;
  if (create(MAZES[$('maze-pick').value], seed) !== 0) throw new Error(mf.UTF8ToString(mf._mf_error()));
  history = [mf._mf_best_fitness()];
  reachedAt = mf._mf_reached_goal() ? 0 : null;
  draw();
}

function draw() {
  const w = mf._mf_width(), h = mf._mf_height(), cell = $('maze').width / w;
  const cells = new Uint8Array(mf.HEAPU8.buffer, mf._mf_cells(), w * h);
  ctx.fillStyle = COLORS.bg; ctx.fillRect(0, 0, w * cell, h * cell);
  ctx.fillStyle = COLORS.border;
  for (let i = 0; i < w * h; i++) if (cells[i]) ctx.fillRect((i % w) * cell, Math.floor(i / w) * cell, cell, cell);
  const pop = mf._mf_population_size(), stride = mf._mf_genome_length() + 1;
  const paths = new Int16Array(mf.HEAP16.buffer, mf._mf_paths(), pop * stride * 2);
  const lens = new Int32Array(mf.HEAP32.buffer, mf._mf_path_lengths(), pop);
  const line = (i) => {
    ctx.beginPath();
    for (let k = 0; k < lens[i]; k++) {
      const x = (paths[(i * stride + k) * 2] + 0.5) * cell, y = (paths[(i * stride + k) * 2 + 1] + 0.5) * cell;
      k ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
    }
    ctx.stroke();
  };
  ctx.lineWidth = 1; ctx.strokeStyle = COLORS.soft; ctx.globalAlpha = 0.07;
  for (let i = pop - 1; i > 0; i--) line(i);
  ctx.globalAlpha = 1; ctx.lineWidth = Math.max(2, cell / 4); ctx.strokeStyle = COLORS.amber;
  line(0); // fittest first
  ctx.fillStyle = COLORS.teal; ctx.fillRect(cell * 0.2, cell * 0.2, cell * 0.6, cell * 0.6);
  ctx.fillStyle = COLORS.amber; ctx.fillRect((w - 0.8) * cell, (h - 0.8) * cell, cell * 0.6, cell * 0.6);
  stats(lens[0] - 1);
}

function stats(bestMoves) {
  const reached = mf._mf_reached_goal();
  $('s-gen').textContent = mf._mf_generation();
  $('s-fit').textContent = mf._mf_best_fitness();
  $('s-short').textContent = `${mf._mf_shortest_path()} moves`;
  $('s-best').textContent = reached ? `${bestMoves} moves, reaches the goal` : 'not at the goal yet';
  $('s-seed').textContent = seed;
  $('goal').textContent = reached ? `Reached the goal at generation ${reachedAt}.` : '';
  const max = Math.max(...history), min = Math.min(...history), n = history.length;
  const pts = history.map((f, i) => `${(i / Math.max(1, n - 1)) * 290 + 5},${90 - ((f - min) / Math.max(1, max - min)) * 80}`).join(' ');
  $('history').innerHTML = `<polyline points="${pts}"/><text x="5" y="98">gen 0</text><text x="295" y="98" text-anchor="end">gen ${n - 1}</text>`;
}

function step(n = 1) {
  for (let i = 0; i < n; i++) { // one generation at a time, so the history and the goal generation are exact
    mf._mf_step(1);
    history.push(mf._mf_best_fitness());
    if (reachedAt === null && mf._mf_reached_goal()) reachedAt = mf._mf_generation();
  }
  draw();
}

function frame() {
  if (!running) return;
  step(Number($('speed').value));
  if (mf._mf_generation() >= 1000) setRunning(false);
  else requestAnimationFrame(frame);
}

function setRunning(on) {
  running = on;
  $('run').textContent = on ? 'Pause' : 'Run';
  if (on) requestAnimationFrame(frame);
}

$('run').addEventListener('click', () => setRunning(!running));
$('step').addEventListener('click', () => { setRunning(false); step(1); });
$('reset').addEventListener('click', () => newRun((seed % 1000000) + 1 + Math.floor(Math.random() * 1000)));
$('maze-pick').addEventListener('change', () => newRun(seed));
let resume = false;
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { resume = running; setRunning(false); } else if (resume) setRunning(true);
});

newRun(1);
if (!reduced) setRunning(true); // under reduced motion it starts paused
