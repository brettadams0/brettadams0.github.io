// Selection on vs off, side by side: the controlled experiment from the README,
// live. Two Simulations from the same seed and the same world; the right one
// has `selection: "random"` (parents drawn uniformly, no elites), so fitness is
// computed but never used. Both engines agree on that run bit for bit
// (fixtures/golden.json "run_random"). Starts on a click, pauses off screen.

import { makeConfig } from "../src/config.js";
import { rank } from "../src/evolution.js";
import { Simulation } from "../src/simulation.js";
import { ArenaView } from "./arena-view.js";

const FRAME_BUDGET_MS = 10;

/**
 * @param {string} id
 * @returns {HTMLElement}
 */
function $(id) {
  const node = document.getElementById(id);
  if (!node) throw new Error(`#${id} missing`);
  return node;
}

const run = /** @type {HTMLButtonElement} */ ($("cmp-run"));
const status = $("cmp-status");
const chart = $("cmp-chart");
const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;

/** @typedef {{ sim: Simulation, view: ArenaView, means: number[], stats: HTMLElement }} Side */

/** @type {Side[]} */
let sides = [];
let playing = false, visible = false, started = false;

/**
 * @param {string} id
 * @param {"tournament" | "random"} selection
 * @returns {Side}
 */
function makeSide(id, selection) {
  const sim = new Simulation(makeConfig({ selection }), 1);
  sim.beginGeneration();
  const canvas = /** @type {HTMLCanvasElement} */ ($(id));
  return { sim, view: new ArenaView(canvas, sim.cfg.width, sim.cfg.height), means: [], stats: $(`${id}-stats`) };
}

/**
 * Advance one step; true when a generation ended.
 * @param {Side} side
 * @returns {boolean}
 */
function advance(side) {
  const sim = side.sim;
  if (sim.done) {
    const s = sim.endGeneration();
    side.means.push(s.mean);
    sim.beginGeneration();
    return true;
  }
  sim.step();
  return false;
}

/** @param {Side} side */
function draw(side) {
  const agents = side.sim.agents;
  const leader = agents.length ? rank(agents.map((a) => [a.foodEaten, a.age]))[0] : -1;
  side.view.draw(side.sim, leader, leader);
  const last = side.means.at(-1);
  side.stats.textContent = `generation ${side.sim.generation}${last === undefined ? "" : ` · ${last.toFixed(1)} food per agent`}`;
}

function drawChart() {
  const n = Math.max(...sides.map((s) => s.means.length));
  if (n < 2) { chart.innerHTML = ""; return; }
  const max = Math.max(1, ...sides.flatMap((s) => s.means));
  /** @param {number[]} m */
  const line = (m) => m.map((v, i) => `${(i / (n - 1)) * 290 + 5},${95 - (v / max) * 85}`).join(" ");
  chart.innerHTML = `<polyline class="on" points="${line(sides[0].means)}"/><polyline class="off" points="${line(sides[1].means)}"/>`
    + `<text x="5" y="10">${max.toFixed(0)}</text><text x="295" y="10" text-anchor="end">generation ${n}</text>`;
  const [on, off] = sides.map((s) => s.means.at(-1) ?? 0);
  status.textContent = `After ${n} generations: ${on.toFixed(1)} food per agent with selection, ${off.toFixed(1)} without.`;
}

function frame() {
  if (!playing || !visible || document.hidden) return;
  const t0 = performance.now();
  let rolled = false;
  // Reduced motion: compute whole generations, redraw once per generation.
  do {
    for (const side of sides) rolled = advance(side) || rolled;
  } while (performance.now() - t0 < FRAME_BUDGET_MS && (!reduce || !rolled));
  if (!reduce || rolled) { for (const side of sides) draw(side); }
  if (rolled) drawChart();
  requestAnimationFrame(frame);
}

/** @param {boolean} on */
function setPlaying(on) {
  playing = on;
  run.textContent = on ? "Pause" : started ? "Resume" : "Run side by side";
  if (on) requestAnimationFrame(frame);
}

run.addEventListener("click", () => {
  if (!started) { sides = [makeSide("cmp-on", "tournament"), makeSide("cmp-off", "random")]; started = true; for (const s of sides) draw(s); }
  setPlaying(!playing);
});
document.addEventListener("visibilitychange", () => { if (!document.hidden && playing) requestAnimationFrame(frame); });
new IntersectionObserver((entries) => {
  visible = entries[0].isIntersecting;
  if (visible && playing) requestAnimationFrame(frame);
}, { threshold: 0.2 }).observe($("compare"));
