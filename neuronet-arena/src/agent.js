// An agent, mirroring src/neuronet/agent.py: egocentric senses, a softsign
// network, and turns made by Cayley rotation so that no cos/sin is needed.

import { INPUTS } from "./brain.js";

/** @typedef {import("./brain.js").Brain} Brain */
/** @typedef {import("./rng.js").Rng} Rng */
/** @typedef {import("./world.js").World} World */

export class Agent {
  /**
   * @param {Brain} brain
   * @param {number} x
   * @param {number} y
   * @param {number} hx
   * @param {number} hy
   * @param {number} energy
   */
  constructor(brain, x, y, hx, hy, energy) {
    this.brain = brain;
    this.x = x;
    this.y = y;
    this.hx = hx;
    this.hy = hy;
    this.energy = energy;
    this.alive = true;
    this.age = 0;
    this.foodEaten = 0;
    this.hazardSteps = 0;
    /** @type {number[]} */
    this.inputs = new Array(INPUTS).fill(0);
    this.steer = 0;
    this.throttle = 0;
    this.speed = 0;
  }

  /**
   * @param {Brain} brain
   * @param {World} world
   * @param {Rng} rng
   */
  static spawn(brain, world, rng) {
    const cfg = world.cfg;
    const [x, y] = world.freePoint(rng, cfg.agent_radius);
    let vx;
    let vy;
    let d2;
    for (;;) {
      vx = rng.uniform(-1, 1);
      vy = rng.uniform(-1, 1);
      d2 = vx * vx + vy * vy;
      if (d2 > 1e-9 && d2 <= 1) break;
    }
    const d = Math.sqrt(d2);
    return new Agent(brain, x, y, vx / d, vy / d, cfg.start_energy);
  }

  /** @param {World} world */
  sense(world) {
    const cfg = world.cfg;
    const { x, y, hx, hy } = this;
    const vision = cfg.vision;

    const food = nearest(world.foodX, world.foodY, x, y, hx, hy, vision);
    const hazard = nearest(world.hazardX, world.hazardY, x, y, hx, hy, vision);

    let ahead = vision;
    if (hx > 0) {
      const t = (cfg.width - x) / hx;
      if (t < ahead) ahead = t;
    } else if (hx < 0) {
      const t = x / -hx;
      if (t < ahead) ahead = t;
    }
    if (hy > 0) {
      const t = (cfg.height - y) / hy;
      if (t < ahead) ahead = t;
    } else if (hy < 0) {
      const t = y / -hy;
      if (t < ahead) ahead = t;
    }

    this.inputs = [
      food[0],
      food[1],
      food[2],
      hazard[0],
      hazard[1],
      hazard[2],
      1 - ahead / vision,
      this.energy / cfg.max_energy,
    ];
    return this.inputs;
  }

  think() {
    const out = this.brain.forward(this.inputs);
    this.steer = out[0];
    this.throttle = (out[1] + 1) * 0.5;
  }

  /**
   * @param {World} world
   * @param {Rng} rng
   */
  act(world, rng) {
    const cfg = world.cfg;

    const t = this.steer * cfg.turn_rate;
    const t2 = t * t;
    const c = (1 - t2) / (1 + t2);
    const s = (2 * t) / (1 + t2);
    const hx = this.hx * c - this.hy * s;
    const hy = this.hx * s + this.hy * c;
    const n = Math.sqrt(hx * hx + hy * hy);
    this.hx = hx / n;
    this.hy = hy / n;

    const speed = this.throttle * cfg.max_speed;
    this.speed = speed;
    const r = cfg.agent_radius;
    let x = this.x + this.hx * speed;
    let y = this.y + this.hy * speed;
    if (x < r) x = r;
    else if (x > cfg.width - r) x = cfg.width - r;
    if (y < r) y = r;
    else if (y > cfg.height - r) y = cfg.height - r;
    this.x = x;
    this.y = y;

    this.energy -= cfg.idle_cost + cfg.move_cost * speed * speed;

    const reach = r + cfg.food_radius;
    const reach2 = reach * reach;
    const { foodX, foodY } = world;
    for (let i = 0; i < foodX.length; i++) {
      const dx = foodX[i] - x;
      const dy = foodY[i] - y;
      if (dx * dx + dy * dy < reach2) {
        this.foodEaten += 1;
        this.energy = Math.min(cfg.max_energy, this.energy + cfg.food_energy);
        world.respawnFood(i, rng);
        break;
      }
    }

    const touch = r + cfg.hazard_radius;
    const touch2 = touch * touch;
    for (let h = 0; h < world.hazardX.length; h++) {
      const dx = world.hazardX[h] - x;
      const dy = world.hazardY[h] - y;
      if (dx * dx + dy * dy < touch2) {
        this.energy -= cfg.hazard_damage;
        this.hazardSteps += 1;
        break;
      }
    }

    this.age += 1;
    if (this.energy <= 0) {
      this.energy = 0;
      this.alive = false;
    }
  }

  /**
   * @param {World} world
   * @param {Rng} rng
   */
  update(world, rng) {
    this.sense(world);
    this.think();
    this.act(world, rng);
  }
}

/**
 * [nearness, ahead, right] of the closest item within vision, or zeros.
 * @param {number[]} xs
 * @param {number[]} ys
 * @param {number} x
 * @param {number} y
 * @param {number} hx
 * @param {number} hy
 * @param {number} vision
 * @returns {[number, number, number]}
 */
function nearest(xs, ys, x, y, hx, hy, vision) {
  let best = vision * vision;
  let bx = 0;
  let by = 0;
  let found = false;
  for (let i = 0; i < xs.length; i++) {
    const dx = xs[i] - x;
    const dy = ys[i] - y;
    const d2 = dx * dx + dy * dy;
    if (d2 < best) {
      best = d2;
      bx = dx;
      by = dy;
      found = true;
    }
  }
  if (!found) return [0, 0, 0];
  const d = Math.sqrt(best);
  if (d === 0) return [1, 0, 0];
  return [1 - d / vision, (bx * hx + by * hy) / d, (by * hx - bx * hy) / d];
}

/**
 * The nearest item's index, for drawing what an agent is looking at.
 * @param {number[]} xs
 * @param {number[]} ys
 * @param {number} x
 * @param {number} y
 * @param {number} vision
 */
export function nearestIndex(xs, ys, x, y, vision) {
  let best = vision * vision;
  let index = -1;
  for (let i = 0; i < xs.length; i++) {
    const dx = xs[i] - x;
    const dy = ys[i] - y;
    const d2 = dx * dx + dy * dy;
    if (d2 < best) {
      best = d2;
      index = i;
    }
  }
  return index;
}
