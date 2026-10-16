// Reading and writing the snapshot format of src/neuronet/snapshot.py.

import { makeConfig } from "./config.js";
import { Rng } from "./rng.js";
import { Simulation } from "./simulation.js";

/** @typedef {import("./simulation.js").GenerationStats} GenerationStats */
/** @typedef {import("./config.js").Config} Config */

/**
 * @typedef {object} Snapshot
 * @property {"neuronet-arena/snapshot"} format
 * @property {1} version
 * @property {number} seed
 * @property {number} generation
 * @property {number[]} rng
 * @property {Config} config
 * @property {number[][]} genomes
 * @property {GenerationStats[]} history
 * @property {GenerationStats} [expect] what the writing engine scored on the next generation
 */

export const FORMAT = "neuronet-arena/snapshot";
export const VERSION = 1;

/**
 * @param {Snapshot} data
 * @returns {Simulation}
 */
export function fromSnapshot(data) {
  if (data.format !== FORMAT) throw new Error("not a neuronet-arena snapshot");
  if (data.version !== VERSION) throw new Error(`unsupported snapshot version ${data.version}`);
  const sim = new Simulation(makeConfig(data.config), data.seed, {
    genomes: data.genomes,
    generation: data.generation,
    rng: Rng.fromState(data.rng),
  });
  sim.history = (data.history ?? []).map((s) => ({ ...s }));
  return sim;
}

/**
 * @param {Simulation} sim
 * @returns {Snapshot}
 */
export function toSnapshot(sim) {
  if (sim.inGeneration) throw new Error("snapshots are taken between generations");
  return {
    format: FORMAT,
    version: VERSION,
    seed: sim.seed,
    generation: sim.generation,
    rng: sim.rng.state(),
    config: { ...sim.cfg },
    genomes: sim.genomes.map((g) => g.slice()),
    history: sim.history.map((s) => ({ ...s })),
  };
}
