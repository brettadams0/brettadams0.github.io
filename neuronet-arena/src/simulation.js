// The generation loop, mirroring src/neuronet/simulation.py. One Rng drives
// world layout, spawns, food respawns, selection and mutation, in a fixed
// order — which is what lets a Python snapshot resume here exactly.

import { Agent } from "./agent.js";
import { Brain, layerSizes } from "./brain.js";
import { nextGeneration, rank } from "./evolution.js";
import { Rng } from "./rng.js";
import { World } from "./world.js";

/** @typedef {import("./config.js").Config} Config */
/** @typedef {import("./evolution.js").Score} Score */

/**
 * @typedef {object} GenerationStats
 * @property {number} generation
 * @property {number} best         most food eaten by any one agent
 * @property {number} mean         food eaten per agent
 * @property {number} survivors    agents alive at the final step
 * @property {number} hazard_steps agent-steps spent touching a hazard
 * @property {number} champion     index of the best agent
 */

export class Simulation {
  /**
   * @param {Readonly<Config>} cfg
   * @param {number} [seed]
   * @param {{ genomes?: number[][], generation?: number, rng?: Rng }} [resume]
   */
  constructor(cfg, seed = 0, resume = {}) {
    this.cfg = cfg;
    this.seed = seed;
    this.rng = resume.rng ?? new Rng(seed);
    this.layers = layerSizes(cfg.hidden);
    this.generation = resume.generation ?? 0;
    if (resume.genomes) {
      if (resume.genomes.length !== cfg.population) {
        throw new Error(`expected ${cfg.population} genomes, got ${resume.genomes.length}`);
      }
      this.genomes = resume.genomes.map((g) => g.slice());
    } else {
      this.genomes = Array.from(
        { length: cfg.population },
        () => Brain.random(this.layers, this.rng).genome,
      );
    }
    /** @type {GenerationStats[]} */
    this.history = [];
    /** @type {World | null} */
    this.world = null;
    /** @type {Agent[]} */
    this.agents = [];
    this.stepIndex = 0;
  }

  beginGeneration() {
    const world = new World(this.cfg, this.rng);
    this.world = world;
    this.agents = this.genomes.map((g) => Agent.spawn(new Brain(this.layers, g), world, this.rng));
    this.stepIndex = 0;
  }

  get inGeneration() {
    return this.world !== null;
  }

  /** The generation has used its steps, or nobody is left alive. */
  get done() {
    return this.stepIndex >= this.cfg.steps || !this.agents.some((a) => a.alive);
  }

  step() {
    const world = this.world;
    if (!world) throw new Error("call beginGeneration() first");
    for (const agent of this.agents) if (agent.alive) agent.update(world, this.rng);
    this.stepIndex += 1;
  }

  /** @returns {GenerationStats} */
  endGeneration() {
    if (!this.world) throw new Error("no generation in progress");
    /** @type {Score[]} */
    const scores = this.agents.map((a) => [a.foodEaten, a.age]);
    let total = 0;
    let best = 0;
    let survivors = 0;
    let hazardSteps = 0;
    for (const a of this.agents) {
      total += a.foodEaten;
      if (a.foodEaten > best) best = a.foodEaten;
      if (a.alive) survivors += 1;
      hazardSteps += a.hazardSteps;
    }
    const stats = {
      generation: this.generation,
      best,
      mean: total / this.agents.length,
      survivors,
      hazard_steps: hazardSteps,
      champion: rank(scores)[0],
    };
    this.genomes = nextGeneration(this.genomes, scores, this.cfg, this.rng);
    this.history.push(stats);
    this.generation += 1;
    this.world = null;
    this.agents = [];
    return stats;
  }

  runGeneration() {
    this.beginGeneration();
    while (!this.done) this.step();
    return this.endGeneration();
  }
}
