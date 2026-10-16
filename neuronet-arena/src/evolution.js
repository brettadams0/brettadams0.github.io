// Selection and variation, mirroring src/neuronet/evolution.py.

/** @typedef {import("./rng.js").Rng} Rng */
/** @typedef {import("./config.js").Config} Config */
/** @typedef {[food: number, age: number]} Score */

/**
 * More food wins; on equal food, surviving longer wins.
 * @param {Score} a
 * @param {Score} b
 */
export const better = (a, b) => a[0] > b[0] || (a[0] === b[0] && a[1] > b[1]);

/**
 * Indices from best to worst; exact ties keep their original order.
 * @param {readonly Score[]} scores
 */
export function rank(scores) {
  return scores
    .map((_, i) => i)
    .sort((i, j) => scores[j][0] - scores[i][0] || scores[j][1] - scores[i][1] || i - j);
}

/**
 * @param {readonly Score[]} scores
 * @param {Readonly<Config>} cfg
 * @param {Rng} rng
 */
export function select(scores, cfg, rng) {
  const n = scores.length;
  if (cfg.selection === "random") return rng.below(n);
  let winner = rng.below(n);
  for (let k = 0; k < cfg.tournament - 1; k++) {
    const challenger = rng.below(n);
    if (better(scores[challenger], scores[winner])) winner = challenger;
  }
  return winner;
}

/**
 * @param {readonly number[]} a
 * @param {readonly number[]} b
 * @param {Rng} rng
 */
export function crossover(a, b, rng) {
  /** @type {number[]} */
  const child = [];
  for (let i = 0; i < a.length; i++) child.push(rng.random() < 0.5 ? a[i] : b[i]);
  return child;
}

/**
 * @param {number[]} genome
 * @param {Readonly<Config>} cfg
 * @param {Rng} rng
 */
export function mutate(genome, cfg, rng) {
  for (let i = 0; i < genome.length; i++) {
    if (rng.random() < cfg.mutation_rate) genome[i] += cfg.mutation_strength * rng.normal();
  }
}

/**
 * @param {readonly (readonly number[])[]} genomes
 * @param {readonly Score[]} scores
 * @param {Readonly<Config>} cfg
 * @param {Rng} rng
 * @returns {number[][]}
 */
export function nextGeneration(genomes, scores, cfg, rng) {
  if (genomes.length !== scores.length) throw new Error("one score per genome");
  /** @type {number[][]} */
  let offspring = [];
  if (cfg.selection === "tournament") {
    offspring = rank(scores)
      .slice(0, cfg.elite)
      .map((i) => genomes[i].slice());
  }
  while (offspring.length < genomes.length) {
    const a = select(scores, cfg, rng);
    let child;
    if (rng.random() < cfg.crossover) {
      const b = select(scores, cfg, rng);
      child = crossover(genomes[a], genomes[b], rng);
    } else {
      child = genomes[a].slice();
    }
    mutate(child, cfg, rng);
    offspring.push(child);
  }
  return offspring;
}
