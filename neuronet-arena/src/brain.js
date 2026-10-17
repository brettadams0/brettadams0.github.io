// The network, mirroring src/neuronet/brain.py. Genome layout per layer: the
// weight matrix row-major (one row of nIn weights per output neuron), then the
// nOut biases.
//
// Softsign rather than tanh: Math.tanh is not required to be correctly
// rounded, and V8 and CPython disagree on it in the last bit. Softsign is an
// add, an abs and a divide, which IEEE 754 defines exactly.

/** @typedef {import("./rng.js").Rng} Rng */

export const INPUT_LABELS = Object.freeze([
  "food: near",
  "food: ahead",
  "food: right",
  "hazard: near",
  "hazard: ahead",
  "hazard: right",
  "wall ahead",
  "energy",
]);
export const OUTPUT_LABELS = Object.freeze(["steer", "throttle"]);

export const INPUTS = INPUT_LABELS.length;
export const OUTPUTS = OUTPUT_LABELS.length;

/** @param {number} hidden */
export const layerSizes = (hidden) => [INPUTS, hidden, OUTPUTS];

/** @param {readonly number[]} layers */
export function genomeSize(layers) {
  let n = 0;
  for (let l = 0; l + 1 < layers.length; l++) n += layers[l + 1] * layers[l] + layers[l + 1];
  return n;
}

export class Brain {
  /**
   * @param {readonly number[]} layers
   * @param {readonly number[]} genome
   */
  constructor(layers, genome) {
    const expected = genomeSize(layers);
    if (genome.length !== expected) {
      throw new Error(`genome has ${genome.length} values, layers [${layers}] need ${expected}`);
    }
    this.layers = layers.slice();
    this.genome = genome.slice();
  }

  /**
   * Glorot-uniform weights, zero biases.
   * @param {readonly number[]} layers
   * @param {Rng} rng
   */
  static random(layers, rng) {
    /** @type {number[]} */
    const genome = [];
    for (let l = 0; l + 1 < layers.length; l++) {
      const nIn = layers[l];
      const nOut = layers[l + 1];
      const limit = Math.sqrt(6 / (nIn + nOut));
      for (let k = 0; k < nOut * nIn; k++) genome.push(rng.uniform(-limit, limit));
      for (let k = 0; k < nOut; k++) genome.push(0);
    }
    return new Brain(layers, genome);
  }

  /** @param {readonly number[]} inputs */
  forward(inputs) {
    const all = this.activations(inputs);
    return all[all.length - 1];
  }

  /**
   * Every layer's values, input first.
   * @param {readonly number[]} inputs
   * @returns {number[][]}
   */
  activations(inputs) {
    const g = this.genome;
    const layers = this.layers;
    let x = inputs.slice();
    const all = [x];
    let off = 0;
    for (let l = 0; l + 1 < layers.length; l++) {
      const nIn = layers[l];
      const nOut = layers[l + 1];
      const biases = off + nOut * nIn;
      /** @type {number[]} */
      const out = new Array(nOut);
      for (let j = 0; j < nOut; j++) {
        let acc = g[biases + j];
        const row = off + j * nIn;
        for (let i = 0; i < nIn; i++) acc += g[row + i] * x[i];
        out[j] = acc / (1 + Math.abs(acc));
      }
      off = biases + nOut;
      all.push(out);
      x = out;
    }
    return all;
  }
}
