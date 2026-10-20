// The homepage hero: the neuronet-arena engine and renderer from /neuronet-arena/,
// running the population trained in Python. It only reads from the simulation,
// exactly as the full demo does.

import { makeConfig } from "/neuronet-arena/src/config.js";
import { rank } from "/neuronet-arena/src/evolution.js";
import { Simulation } from "/neuronet-arena/src/simulation.js";
import { fromSnapshot } from "/neuronet-arena/src/snapshot.js";
import { ArenaView } from "/neuronet-arena/ui/arena-view.js";

const canvas = /** @type {HTMLCanvasElement | null} */ (document.getElementById("hero-arena"));
const hudGen = document.getElementById("hero-gen");
const hudStep = document.getElementById("hero-step");
const hudBest = document.getElementById("hero-best");
const toggle = /** @type {HTMLButtonElement | null} */ (document.getElementById("hero-toggle"));

if (canvas && hudGen && hudStep && hudBest && toggle) start();

async function start() {
  const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
  let sim = new Simulation(makeConfig(), 1);
  try {
    const res = await fetch("/neuronet-arena/data/trained.json");
    if (res.ok) sim = fromSnapshot(await res.json());
  } catch {
    // Falls back to a population starting from scratch.
  }
  sim.beginGeneration();

  const view = new ArenaView(canvas, sim.cfg.width, sim.cfg.height);
  let paused = reduced;
  let visible = true;

  const setPaused = (value) => {
    paused = value;
    toggle.textContent = paused ? "Play" : "Pause";
    toggle.setAttribute("aria-pressed", String(paused));
  };
  setPaused(paused);
  toggle.hidden = false;
  toggle.addEventListener("click", () => setPaused(!paused));

  new IntersectionObserver((entries) => {
    visible = entries[0].isIntersecting;
  }).observe(canvas);

  const draw = () => {
    const agents = sim.agents;
    const leader = agents.length ? rank(agents.map((a) => [a.foodEaten, a.age]))[0] : -1;
    view.draw(sim, leader, leader);
    hudGen.textContent = `Generation ${sim.generation}`;
    hudStep.textContent = `step ${sim.stepIndex} / ${sim.cfg.steps}`;
    hudBest.textContent = leader >= 0 ? `leader ${agents[leader].foodEaten} food` : "";
  };

  const frame = () => {
    if (!paused && visible && !document.hidden) {
      if (sim.done) {
        sim.endGeneration();
        sim.beginGeneration();
      } else {
        sim.step();
      }
      draw();
    }
    requestAnimationFrame(frame);
  };

  draw();
  canvas.closest(".hero-arena")?.classList.add("is-live");
  requestAnimationFrame(frame);
}
