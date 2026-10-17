// The arena, mirroring src/neuronet/world.py: food that respawns when eaten,
// hazards that stay put for the generation.

/** @typedef {import("./rng.js").Rng} Rng */
/** @typedef {import("./config.js").Config} Config */

const PLACEMENT_TRIES = 32;

export class World {
  /**
   * @param {Readonly<Config>} cfg
   * @param {Rng} rng
   */
  constructor(cfg, rng) {
    this.cfg = cfg;
    /** @type {number[]} */ this.hazardX = [];
    /** @type {number[]} */ this.hazardY = [];
    /** @type {number[]} */ this.foodX = [];
    /** @type {number[]} */ this.foodY = [];

    const margin = 2 * cfg.hazard_radius;
    for (let i = 0; i < cfg.hazard_count; i++) {
      this.hazardX.push(rng.uniform(margin, cfg.width - margin));
      this.hazardY.push(rng.uniform(margin, cfg.height - margin));
    }
    for (let i = 0; i < cfg.food_count; i++) {
      const [x, y] = this.freePoint(rng, cfg.food_radius);
      this.foodX.push(x);
      this.foodY.push(y);
    }
  }

  /**
   * A uniformly random point whose radius-disc clears every hazard by an
   * agent's diameter.
   * @param {Rng} rng
   * @param {number} radius
   * @returns {[number, number]}
   */
  freePoint(rng, radius) {
    const cfg = this.cfg;
    const margin = 2 * radius;
    const clear = cfg.hazard_radius + radius + 2 * cfg.agent_radius;
    const clear2 = clear * clear;
    let x = 0;
    let y = 0;
    tries: for (let t = 0; t < PLACEMENT_TRIES; t++) {
      x = rng.uniform(margin, cfg.width - margin);
      y = rng.uniform(margin, cfg.height - margin);
      for (let h = 0; h < this.hazardX.length; h++) {
        const dx = this.hazardX[h] - x;
        const dy = this.hazardY[h] - y;
        if (dx * dx + dy * dy < clear2) continue tries;
      }
      return [x, y];
    }
    return [x, y];
  }

  /**
   * @param {number} index
   * @param {Rng} rng
   */
  respawnFood(index, rng) {
    const [x, y] = this.freePoint(rng, this.cfg.food_radius);
    this.foodX[index] = x;
    this.foodY[index] = y;
  }
}
