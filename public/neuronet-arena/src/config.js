// Every tunable number, with the same names and defaults as
// src/neuronet/config.py, so a snapshot's config loads unchanged in either
// engine. The Python dataclass documents each field.

/**
 * @typedef {object} Config
 * @property {number} width
 * @property {number} height
 * @property {number} population
 * @property {number} food_count
 * @property {number} hazard_count
 * @property {number} steps
 * @property {number} agent_radius
 * @property {number} food_radius
 * @property {number} hazard_radius
 * @property {number} vision
 * @property {number} max_speed
 * @property {number} turn_rate
 * @property {number} start_energy
 * @property {number} max_energy
 * @property {number} food_energy
 * @property {number} idle_cost
 * @property {number} move_cost
 * @property {number} hazard_damage
 * @property {number} hidden
 * @property {"tournament" | "random"} selection
 * @property {number} elite
 * @property {number} tournament
 * @property {number} crossover
 * @property {number} mutation_rate
 * @property {number} mutation_strength
 */

/** @type {Readonly<Config>} */
export const DEFAULTS = Object.freeze({
  width: 960,
  height: 600,
  population: 40,
  food_count: 60,
  hazard_count: 12,
  steps: 600,
  agent_radius: 6,
  food_radius: 4,
  hazard_radius: 16,
  vision: 140,
  max_speed: 3,
  turn_rate: 0.1,
  start_energy: 60,
  max_energy: 100,
  food_energy: 25,
  idle_cost: 0.08,
  move_cost: 0.02,
  hazard_damage: 4,
  hidden: 10,
  selection: "tournament",
  elite: 2,
  tournament: 3,
  crossover: 0.7,
  mutation_rate: 0.08,
  mutation_strength: 0.3,
});

/**
 * Defaults overlaid with `overrides`, rejecting unknown keys and values the
 * Python side would also reject.
 * @param {Partial<Config>} [overrides]
 * @returns {Readonly<Config>}
 */
export function makeConfig(overrides = {}) {
  const unknown = Object.keys(overrides).filter((k) => !(k in DEFAULTS));
  if (unknown.length) throw new Error(`unknown config keys: ${unknown.sort().join(", ")}`);
  const cfg = { ...DEFAULTS, ...overrides };
  const positive = /** @type {const} */ ([
    "width", "height", "population", "steps", "agent_radius", "food_radius", "hazard_radius",
    "vision", "max_speed", "max_energy", "start_energy", "hidden", "tournament",
  ]);
  for (const k of positive) if (!(cfg[k] > 0)) throw new Error(`${k} must be positive`);
  for (const k of /** @type {const} */ (["crossover", "mutation_rate"])) {
    if (!(cfg[k] >= 0 && cfg[k] <= 1)) throw new Error(`${k} must be in [0, 1]`);
  }
  if (cfg.selection !== "tournament" && cfg.selection !== "random") {
    throw new Error("selection must be 'tournament' or 'random'");
  }
  if (cfg.elite > cfg.population) throw new Error("elite cannot exceed population");
  return Object.freeze(cfg);
}
