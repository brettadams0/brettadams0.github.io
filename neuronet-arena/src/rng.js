// Seeded random numbers, identical bit-for-bit to src/neuronet/rng.py.
//
// xoshiro128** seeded through splitmix32: 32-bit integer arithmetic only,
// which JavaScript does exactly with Math.imul and `>>> 0`. Floats are built
// from integers by exact operations, and the normal sample is an Irwin-Hall
// sum because Box-Muller needs log and cos, which no standard requires to
// round the same way in V8 as in CPython's libm.

const TWO_26 = 67108864;
const TWO_53 = 9007199254740992;
const SQRT3 = Math.sqrt(3);

/**
 * @param {number} x
 * @param {number} k
 */
const rotl = (x, k) => ((x << k) | (x >>> (32 - k))) >>> 0;

export class Rng {
  /** @param {number} seed an integer in [0, 2^32) */
  constructor(seed) {
    let state = seed >>> 0;
    /** @type {number[]} */
    const words = [];
    for (let i = 0; i < 4; i++) {
      state = (state + 0x9e3779b9) >>> 0;
      let z = state;
      z = Math.imul(z ^ (z >>> 16), 0x85ebca6b) >>> 0;
      z = Math.imul(z ^ (z >>> 13), 0xc2b2ae35) >>> 0;
      words.push((z ^ (z >>> 16)) >>> 0);
    }
    if (!words.some((w) => w !== 0)) words[0] = 1; // all-zero is a fixed point
    /** @type {number[]} */
    this.s = words;
  }

  /** @param {number[]} state */
  static fromState(state) {
    const valid =
      state.length === 4 &&
      state.every((w) => Number.isInteger(w) && w >= 0 && w <= 0xffffffff) &&
      state.some((w) => w !== 0);
    if (!valid) throw new Error(`invalid xoshiro128 state: ${JSON.stringify(state)}`);
    const rng = new Rng(0);
    rng.s = state.slice();
    return rng;
  }

  /** @returns {number[]} */
  state() {
    return this.s.slice();
  }

  nextU32() {
    const s = this.s;
    let s0 = s[0];
    let s1 = s[1];
    let s2 = s[2];
    let s3 = s[3];
    const result = Math.imul(rotl(Math.imul(s1, 5) >>> 0, 7), 9) >>> 0;
    const t = (s1 << 9) >>> 0;
    s2 = (s2 ^ s0) >>> 0;
    s3 = (s3 ^ s1) >>> 0;
    s1 = (s1 ^ s2) >>> 0;
    s0 = (s0 ^ s3) >>> 0;
    s2 = (s2 ^ t) >>> 0;
    s3 = rotl(s3, 11);
    s[0] = s0;
    s[1] = s1;
    s[2] = s2;
    s[3] = s3;
    return result;
  }

  /** A float in [0, 1) with 53 random bits. */
  random() {
    const a = this.nextU32() >>> 5;
    const b = this.nextU32() >>> 6;
    return (a * TWO_26 + b) / TWO_53;
  }

  /**
   * @param {number} lo
   * @param {number} hi
   */
  uniform(lo, hi) {
    return lo + (hi - lo) * this.random();
  }

  /**
   * An integer in [0, n).
   * @param {number} n
   */
  below(n) {
    return Math.min(Math.floor(this.random() * n), n - 1);
  }

  /** Approximately standard normal: Irwin-Hall with four terms. */
  normal() {
    let u = this.random();
    u += this.random();
    u += this.random();
    u += this.random();
    return (u - 2) * SQRT3;
  }
}
