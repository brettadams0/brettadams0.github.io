// The demo page: owns one Simulation and drives it from requestAnimationFrame.
// The rendering modules only read from it, and nothing on the page feeds
// back into it, so what you watch is exactly what the engine computes.

import { makeConfig } from "../src/config.js";
import { rank } from "../src/evolution.js";
import { Simulation } from "../src/simulation.js";
import { fromSnapshot } from "../src/snapshot.js";
import { ArenaView } from "./arena-view.js";
import { BrainView } from "./brain-view.js";
import { Chart } from "./chart.js";

/** @typedef {import("../src/simulation.js").GenerationStats} GenerationStats */
/** @typedef {import("../src/snapshot.js").Snapshot} Snapshot */

/**
 * @template {Element} T
 * @param {string} id
 * @returns {T}
 */
function $(id) {
  const node = document.getElementById(id);
  if (!node) throw new Error(`#${id} missing`);
  return /** @type {T} */ (/** @type {unknown} */ (node));
}

const FRAME_BUDGET_MS = 12;
const PARITY_FIELDS = /** @type {const} */ (["best", "mean", "survivors", "hazard_steps", "champion"]);

const ui = {
  arena: /** @type {HTMLCanvasElement} */ ($("arena")),
  brain: /** @type {HTMLCanvasElement} */ ($("brain")),
  play: /** @type {HTMLButtonElement} */ ($("play")),
  skip: /** @type {HTMLButtonElement} */ ($("skip")),
  restart: /** @type {HTMLButtonElement} */ ($("restart")),
  seed: /** @type {HTMLInputElement} */ ($("seed")),
  hudGen: $("hud-gen"),
  hudStep: $("hud-step"),
  hudAlive: $("hud-alive"),
  tileGens: $("tile-gens"),
  tileMean: $("tile-mean"),
  tileBest: $("tile-best"),
  table: /** @type {HTMLTableSectionElement} */ ($("history-table").querySelector("tbody")),
  inspectTitle: $("inspect-title"),
  inspectMeta: $("inspect-meta"),
  energyBar: /** @type {HTMLElement} */ ($("energy-bar")),
  energyVal: $("energy-val"),
  readout: $("readout"),
  parity: /** @type {HTMLElement} */ ($("parity")),
  parityBadge: $("parity-badge"),
  parityText: $("parity-text"),
  parityRows: $("parity-rows"),
};

const state = {
  /** @type {Simulation} */
  sim: new Simulation(makeConfig(), 1),
  /** @type {number} steps per frame; Infinity means "as many as fit in the frame budget" */
  speed: 1,
  paused: matchMedia("(prefers-reduced-motion: reduce)").matches,
  /** @type {"trained" | "scratch"} */
  source: "trained",
  /** @type {number | null} agent index under inspection, or null to follow the leader */
  focus: null,
  /** @type {Snapshot | null} */
  trained: null,
  /** @type {{ generation: number, expect: GenerationStats, got: GenerationStats | null } | null} */
  parity: null,
};

const arenaView = new ArenaView(ui.arena, state.sim.cfg.width, state.sim.cfg.height);
const brainView = new BrainView(ui.brain);
const chart = new Chart(/** @type {HTMLElement} */ ($("chart")));

// -- running ------------------------------------------------------------------

function start() {
  const sim = state.sim;
  sim.beginGeneration();
  state.focus = null;
  chart.reset(sim.history, state.parity ? state.parity.generation : null);
  ui.table.replaceChildren();
  for (const s of sim.history) addTableRow(s);
  updateTiles();
  renderParity();
}

function load() {
  if (state.source === "trained" && state.trained) {
    state.sim = fromSnapshot(state.trained);
    state.parity = state.trained.expect
      ? { generation: state.trained.generation, expect: state.trained.expect, got: null }
      : null;
  } else {
    const seed = Math.max(0, Math.min(0xffffffff, Math.floor(Number(ui.seed.value) || 0)));
    ui.seed.value = String(seed);
    state.sim = new Simulation(makeConfig(), seed);
    state.parity = null;
  }
  start();
}

/** Advance one step, rolling over to the next generation when this one ends. */
function advance() {
  const sim = state.sim;
  if (sim.done) {
    const stats = sim.endGeneration();
    onGeneration(stats);
    sim.beginGeneration();
    state.focus = null;
    return;
  }
  sim.step();
}

/** @param {GenerationStats} stats */
function onGeneration(stats) {
  chart.push(stats);
  addTableRow(stats);
  updateTiles();
  if (state.parity && stats.generation === state.parity.generation) {
    state.parity.got = stats;
    renderParity();
  }
}

function finishGeneration() {
  const sim = state.sim;
  while (!sim.done) sim.step();
  advance();
}

let ready = false;

/** @param {number} now */
function frame(now) {
  if (!ready) {
    requestAnimationFrame(frame);
    return;
  }
  if (!state.paused) {
    if (Number.isFinite(state.speed)) {
      for (let i = 0; i < state.speed; i++) advance();
    } else {
      const until = now + FRAME_BUDGET_MS;
      do advance();
      while (performance.now() < until);
    }
  }
  draw();
  requestAnimationFrame(frame);
}

// -- drawing ------------------------------------------------------------------

function draw() {
  const sim = state.sim;
  const agents = sim.agents;
  const leader = agents.length ? rank(agents.map((a) => [a.foodEaten, a.age]))[0] : -1;
  const focus = state.focus ?? leader;
  arenaView.draw(sim, leader, focus);

  ui.hudGen.textContent = `Generation ${sim.generation}`;
  ui.hudStep.textContent = `step ${sim.stepIndex} / ${sim.cfg.steps}`;
  ui.hudAlive.textContent = `${agents.filter((a) => a.alive).length} alive`;

  const a = agents[focus];
  if (!a) return;
  brainView.draw(a.brain, a.inputs);
  ui.inspectTitle.textContent = state.focus === null || focus === leader ? "Leader" : `Agent ${focus}`;
  ui.inspectMeta.textContent = `${a.foodEaten} food · ${a.alive ? `age ${a.age}` : "out of energy"}`;
  const e = a.energy / sim.cfg.max_energy;
  ui.energyBar.style.width = `${(e * 100).toFixed(1)}%`;
  ui.energyVal.textContent = a.energy.toFixed(0);
  const turn = a.steer > 0.05 ? "right" : a.steer < -0.05 ? "left" : "straight";
  ui.readout.innerHTML = "";
  const parts = [
    ["steering ", `${turn} ${Math.abs(a.steer).toFixed(2)}`],
    [" · speed ", `${a.speed.toFixed(2)} px/step`],
    [" · touched hazards for ", `${a.hazardSteps} steps`],
  ];
  for (const [label, value] of parts) {
    ui.readout.append(label);
    const b = document.createElement("b");
    b.textContent = value;
    ui.readout.append(b);
  }
}

function updateTiles() {
  const h = state.sim.history;
  const last = h[h.length - 1];
  ui.tileGens.textContent = String(h.length);
  ui.tileMean.textContent = last ? last.mean.toFixed(1) : "–";
  ui.tileBest.textContent = last ? String(last.best) : "–";
}

/** @param {GenerationStats} s */
function addTableRow(s) {
  const tr = document.createElement("tr");
  for (const v of [s.generation, s.mean.toFixed(2), s.best, s.survivors]) {
    const td = document.createElement("td");
    td.textContent = String(v);
    tr.append(td);
  }
  ui.table.prepend(tr);
}

function renderParity() {
  const p = state.parity;
  ui.parity.hidden = false;
  ui.parityRows.replaceChildren();
  if (!p) {
    ui.parityBadge.className = "badge";
    ui.parityBadge.textContent = "idle";
    ui.parityText.textContent =
      "Switch to the population trained in Python to watch this browser reproduce a generation the Python engine already scored.";
    return;
  }
  ui.parityText.textContent =
    `This population was trained for ${p.generation} generations by the Python engine, which also ` +
    `recorded what generation ${p.generation} scores. Your browser is replaying that generation now.`;
  let allMatch = true;
  for (const field of PARITY_FIELDS) {
    const tr = document.createElement("tr");
    const name = document.createElement("td");
    name.textContent = field.replace("_", " ");
    const py = document.createElement("td");
    py.textContent = String(p.expect[field]);
    const js = document.createElement("td");
    if (p.got) {
      const match = p.got[field] === p.expect[field];
      allMatch &&= match;
      js.textContent = String(p.got[field]);
      js.className = match ? "match" : "miss";
    } else {
      js.textContent = "…";
      js.className = "pending";
    }
    tr.append(name, py, js);
    ui.parityRows.append(tr);
  }
  if (!p.got) {
    ui.parityBadge.className = "badge";
    ui.parityBadge.textContent = "running";
  } else if (allMatch) {
    ui.parityBadge.className = "badge ok";
    ui.parityBadge.textContent = "identical";
    ui.parityText.textContent =
      `Generation ${p.generation}, replayed here from the Python engine's snapshot, scored exactly what ` +
      "Python recorded — every agent, every step, the same floating-point bits.";
  } else {
    ui.parityBadge.className = "badge bad";
    ui.parityBadge.textContent = "mismatch";
  }
}

// -- controls -----------------------------------------------------------------

/** @param {boolean} paused */
function setPaused(paused) {
  state.paused = paused;
  ui.play.textContent = paused ? "Play" : "Pause";
  ui.play.setAttribute("aria-pressed", String(paused));
}

/**
 * @param {string} groupSelector
 * @param {HTMLElement} chosen
 */
function check(groupSelector, chosen) {
  for (const b of document.querySelectorAll(`${groupSelector} button`)) {
    b.setAttribute("aria-checked", String(b === chosen));
  }
}

ui.play.addEventListener("click", () => setPaused(!state.paused));
ui.skip.addEventListener("click", finishGeneration);
ui.restart.addEventListener("click", load);
ui.seed.addEventListener("change", () => {
  const scratch = /** @type {HTMLElement} */ (document.querySelector('[data-source="scratch"]'));
  check('[aria-label="Starting population"]', scratch);
  state.source = "scratch";
  load();
});

for (const b of document.querySelectorAll("[data-speed]")) {
  b.addEventListener("click", () => {
    const v = /** @type {HTMLElement} */ (b).dataset.speed;
    state.speed = v === "max" ? Infinity : Number(v);
    check('[aria-label="Speed"]', /** @type {HTMLElement} */ (b));
    if (state.paused) setPaused(false);
  });
}

for (const b of document.querySelectorAll("[data-source]")) {
  b.addEventListener("click", () => {
    const source = /** @type {HTMLElement} */ (b).dataset.source === "scratch" ? "scratch" : "trained";
    check('[aria-label="Starting population"]', /** @type {HTMLElement} */ (b));
    state.source = source;
    load();
  });
}

ui.arena.addEventListener("click", (event) => {
  const [x, y] = arenaView.toWorld(event);
  let best = 24 * 24;
  /** @type {number | null} */
  let pick = null;
  state.sim.agents.forEach((a, i) => {
    const d2 = (a.x - x) ** 2 + (a.y - y) ** 2;
    if (d2 < best) {
      best = d2;
      pick = i;
    }
  });
  state.focus = pick;
});

document.addEventListener("keydown", (event) => {
  const target = /** @type {HTMLElement} */ (event.target);
  if (event.ctrlKey || event.metaKey || event.altKey || target.closest("input, textarea")) return;
  if (event.key === " " && !target.closest("button, a, [tabindex]")) {
    event.preventDefault();
    setPaused(!state.paused);
  } else if (event.key === "n" || event.key === "N") {
    finishGeneration();
  }
});

// -- boot ---------------------------------------------------------------------

setPaused(state.paused);
requestAnimationFrame(frame);

fetch("data/trained.json")
  .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
  .then((/** @type {Snapshot} */ data) => {
    state.trained = data;
    load();
    ready = true;
  })
  .catch((err) => {
    console.warn("trained population unavailable; starting from scratch", err);
    state.source = "scratch";
    const scratch = /** @type {HTMLElement} */ (document.querySelector('[data-source="scratch"]'));
    check('[aria-label="Starting population"]', scratch);
    const trained = /** @type {HTMLButtonElement} */ (document.querySelector('[data-source="trained"]'));
    trained.disabled = true;
    load();
    ready = true;
  });
