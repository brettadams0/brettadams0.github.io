// The real C++ engine runs off the main thread. Copied WASM build: f1b80d59 (adaptive BFS-guided segment mutation).
// A snapshot owns its buffers; no views into WASM memory cross this boundary.
const FIXED = {
 "obstacles": "...........#........\n...........#........\n...#.......#........\n...#.......#........\n...#.......#........\n...#.......#...#....\n...#...#...#...#....\n...#...#...#...#....\n...#...#...#...#....\n...#...#...########.\n...#...#...#...#....\n...#...#...#...#....\n...#...#...#...#....\n.......#...#...#....\n.......#.......#....\n.......#.......#....\n#####..#.......#....\n.......#.......#....\n.......#............\n.......#............\n",
 "open": "....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n....................\n"
};

let mf, create, generate;
let reachedAt = null;
let limit = 1500;
let ready;
const INITIAL_BUDGET = 1500;
const MAX_BUDGET = 15000;

async function initialize() {
  const { default: createModule } = await import('./wasm/mazefinder.js?v=3');
  mf = await createModule({ locateFile: (file) => new URL(`./wasm/${file}?v=3`, import.meta.url).href });
  create = mf.cwrap('mf_create', 'number', ['string', 'number']);
  generate = mf.cwrap('mf_generate', 'string', ['number', 'number', 'number']);
}

function snapshot(id, runId, samples, includeMaze, extended = false) {
  const width = mf._mf_width(), height = mf._mf_height();
  const population = mf._mf_population_size(), stride = mf._mf_genome_length() + 1;
  const paths = new Int16Array(mf.HEAP16.buffer, mf._mf_paths(), population * stride * 2).slice();
  const lengths = new Int32Array(mf.HEAP32.buffer, mf._mf_path_lengths(), population).slice();
  const result = { type: 'snapshot', id, runId, limit, maxBudget: MAX_BUDGET, extended, width, height, population, stride, paths, lengths,
    generation: mf._mf_generation(), fitness: mf._mf_best_fitness(),
    reached: !!mf._mf_reached_goal(), shortest: mf._mf_shortest_path(), reachedAt, samples };
  const transfers = [paths.buffer, lengths.buffer];
  if (includeMaze) {
    result.cells = new Uint8Array(mf.HEAPU8.buffer, mf._mf_cells(), width * height).slice();
    result.optimal = new Int16Array(mf.HEAP16.buffer, mf._mf_optimal_path(), mf._mf_optimal_length() * 2).slice();
    transfers.push(result.cells.buffer, result.optimal.buffer);
  }
  postMessage(result, transfers);
}

self.onmessage = async ({ data }) => {
  try {
    if (!ready) ready = initialize();
    await ready;
    if (data.type === 'create') {
      const { size, kind, seed } = data.state;
      if (![20, 30, 40].includes(size) || !['maze', 'loops', 'open', 'obstacles'].includes(kind)
          || !Number.isInteger(seed) || seed < 1 || seed > 2147483647) throw new Error('Invalid run configuration');
      const text = FIXED[kind] ?? generate(size, seed, kind === 'loops' ? 1 : 0);
      if (create(text, seed)) throw new Error(mf.UTF8ToString(mf._mf_error()));
      reachedAt = mf._mf_reached_goal() ? 0 : null;
      limit = INITIAL_BUDGET;
      snapshot(data.id, data.runId, [mf._mf_best_fitness()], true);
    } else if (data.type === 'extend') {
      limit = Math.min(MAX_BUDGET, limit + INITIAL_BUDGET);
      snapshot(data.id, data.runId, [], false, true);
    } else if (data.type === 'step') {
      const samples = [];
      const count = Math.max(1, Math.min(20, Math.floor(data.count) || 1));
      for (let i = 0; i < count && mf._mf_generation() < limit; i++) {
        mf._mf_step(1);
        samples.push(mf._mf_best_fitness());
        if (reachedAt === null && mf._mf_reached_goal()) reachedAt = mf._mf_generation();
        if (mf._mf_best_fitness() === mf._mf_shortest_path()) break;
      }
      snapshot(data.id, data.runId, samples, false);
    }
  } catch (error) {
    postMessage({ type: 'error', id: data.id, runId: data.runId, message: String(error.message || error) });
  }
};
