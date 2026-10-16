// Draws the arena onto a canvas. Read-only: it never touches the simulation's
// state, so watching a run cannot change how it turns out.

import { nearestIndex } from "../src/agent.js";

/** @typedef {import("../src/simulation.js").Simulation} Simulation */
/** @typedef {import("../src/agent.js").Agent} Agent */

const COLORS = {
  bg: "#0b0f14",
  grid: "#121922",
  food: "#4fd6c4",
  hazard: "#ef6a5f",
  agent: "#c3ccd8",
  leader: "#ffb454",
  dead: "#2f3b4a",
  vision: "rgba(255, 180, 84, 0.22)",
  trail: "255, 180, 84",
};

const TRAIL = 90;

export class ArenaView {
  /**
   * @param {HTMLCanvasElement} canvas
   * @param {number} width  world width
   * @param {number} height world height
   */
  constructor(canvas, width, height) {
    this.canvas = canvas;
    this.worldW = width;
    this.worldH = height;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("2D canvas unavailable");
    this.ctx = ctx;
    /** @type {[number, number][]} */
    this.trail = [];
    this.trailOwner = -1;
    this.trailGeneration = -1;
    this.scale = 1;
    this.resize();
    new ResizeObserver(() => this.resize()).observe(canvas);
  }

  resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const cssW = this.canvas.clientWidth || this.worldW;
    const w = Math.round(cssW * dpr);
    const h = Math.round((cssW * this.worldH / this.worldW) * dpr);
    if (this.canvas.width !== w || this.canvas.height !== h) {
      this.canvas.width = w;
      this.canvas.height = h;
    }
    this.scale = w / this.worldW;
  }

  /**
   * World coordinates of a pointer event.
   * @param {MouseEvent} event
   * @returns {[number, number]}
   */
  toWorld(event) {
    const rect = this.canvas.getBoundingClientRect();
    return [
      ((event.clientX - rect.left) / rect.width) * this.worldW,
      ((event.clientY - rect.top) / rect.height) * this.worldH,
    ];
  }

  /**
   * @param {Simulation} sim
   * @param {number} leader   index of the current leader
   * @param {number} focus    index of the agent being inspected
   */
  draw(sim, leader, focus) {
    const { ctx, scale } = this;
    const cfg = sim.cfg;
    ctx.setTransform(scale, 0, 0, scale, 0, 0);
    ctx.fillStyle = COLORS.bg;
    ctx.fillRect(0, 0, this.worldW, this.worldH);

    ctx.strokeStyle = COLORS.grid;
    ctx.lineWidth = 1 / scale;
    ctx.beginPath();
    for (let x = 60; x < this.worldW; x += 60) {
      ctx.moveTo(x, 0);
      ctx.lineTo(x, this.worldH);
    }
    for (let y = 60; y < this.worldH; y += 60) {
      ctx.moveTo(0, y);
      ctx.lineTo(this.worldW, y);
    }
    ctx.stroke();

    const world = sim.world;
    if (!world) return;

    // Hazards: a soft halo marking the danger zone, then the core.
    for (let i = 0; i < world.hazardX.length; i++) {
      const x = world.hazardX[i];
      const y = world.hazardY[i];
      const halo = ctx.createRadialGradient(x, y, cfg.hazard_radius * 0.6, x, y, cfg.hazard_radius * 2.2);
      halo.addColorStop(0, "rgba(239, 106, 95, 0.28)");
      halo.addColorStop(1, "rgba(239, 106, 95, 0)");
      ctx.fillStyle = halo;
      ctx.beginPath();
      ctx.arc(x, y, cfg.hazard_radius * 2.2, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = COLORS.hazard;
      ctx.beginPath();
      ctx.arc(x, y, cfg.hazard_radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = "rgba(11, 15, 20, 0.35)";
      ctx.beginPath();
      ctx.arc(x, y, cfg.hazard_radius * 0.45, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.fillStyle = COLORS.food;
    ctx.shadowColor = "rgba(79, 214, 196, 0.6)";
    ctx.shadowBlur = 6 * scale;
    ctx.beginPath();
    for (let i = 0; i < world.foodX.length; i++) {
      ctx.moveTo(world.foodX[i] + cfg.food_radius, world.foodY[i]);
      ctx.arc(world.foodX[i], world.foodY[i], cfg.food_radius, 0, Math.PI * 2);
    }
    ctx.fill();
    ctx.shadowBlur = 0;

    const focused = sim.agents[focus];
    if (focused) this.drawFocus(sim, focused, focus);

    for (let i = 0; i < sim.agents.length; i++) {
      const a = sim.agents[i];
      if (!a.alive) {
        ctx.fillStyle = COLORS.dead;
        ctx.beginPath();
        ctx.arc(a.x, a.y, 2.5, 0, Math.PI * 2);
        ctx.fill();
        continue;
      }
      if (i === leader || i === focus) continue;
      this.drawAgent(a, COLORS.agent, 0.35 + 0.65 * (a.energy / cfg.max_energy), cfg.agent_radius);
    }
    // Leader and focus last, so they are never hidden under the crowd.
    if (focused && focused.alive && focus !== leader) {
      this.drawAgent(focused, COLORS.agent, 1, cfg.agent_radius * 1.15);
    }
    const lead = sim.agents[leader];
    if (lead && lead.alive) this.drawAgent(lead, COLORS.leader, 1, cfg.agent_radius * 1.2);
  }

  /**
   * @param {Agent} a
   * @param {string} color
   * @param {number} alpha
   * @param {number} r
   */
  drawAgent(a, color, alpha, r) {
    const { ctx } = this;
    const { x, y, hx, hy } = a;
    ctx.globalAlpha = alpha;
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.moveTo(x + hx * r * 1.7, y + hy * r * 1.7);
    ctx.lineTo(x - hx * r + hy * r * 0.9, y - hy * r - hx * r * 0.9);
    ctx.lineTo(x - hx * r * 0.45, y - hy * r * 0.45);
    ctx.lineTo(x - hx * r - hy * r * 0.9, y - hy * r + hx * r * 0.9);
    ctx.closePath();
    ctx.fill();
    ctx.globalAlpha = 1;
  }

  /**
   * Vision radius, sight lines to what the agent is reacting to, and a trail.
   * @param {Simulation} sim
   * @param {Agent} a
   * @param {number} index
   */
  drawFocus(sim, a, index) {
    const { ctx, scale } = this;
    const world = /** @type {NonNullable<Simulation["world"]>} */ (sim.world);
    const cfg = sim.cfg;

    if (this.trailOwner !== index || this.trailGeneration !== sim.generation) {
      this.trail = [];
      this.trailOwner = index;
      this.trailGeneration = sim.generation;
    }
    const last = this.trail[this.trail.length - 1];
    if (!last || last[0] !== a.x || last[1] !== a.y) {
      this.trail.push([a.x, a.y]);
      if (this.trail.length > TRAIL) this.trail.shift();
    }
    ctx.lineWidth = 2 / scale;
    for (let i = 1; i < this.trail.length; i++) {
      ctx.strokeStyle = `rgba(${COLORS.trail}, ${(i / this.trail.length) * 0.35})`;
      ctx.beginPath();
      ctx.moveTo(this.trail[i - 1][0], this.trail[i - 1][1]);
      ctx.lineTo(this.trail[i][0], this.trail[i][1]);
      ctx.stroke();
    }
    if (!a.alive) return;

    ctx.strokeStyle = COLORS.vision;
    ctx.lineWidth = 1 / scale;
    ctx.beginPath();
    ctx.arc(a.x, a.y, cfg.vision, 0, Math.PI * 2);
    ctx.stroke();

    const f = nearestIndex(world.foodX, world.foodY, a.x, a.y, cfg.vision);
    if (f >= 0) this.sightLine(a, world.foodX[f], world.foodY[f], "rgba(79, 214, 196, 0.7)");
    const h = nearestIndex(world.hazardX, world.hazardY, a.x, a.y, cfg.vision);
    if (h >= 0) this.sightLine(a, world.hazardX[h], world.hazardY[h], "rgba(239, 106, 95, 0.7)");
  }

  /**
   * @param {Agent} a
   * @param {number} x
   * @param {number} y
   * @param {string} color
   */
  sightLine(a, x, y, color) {
    const { ctx, scale } = this;
    ctx.strokeStyle = color;
    ctx.lineWidth = 1.25 / scale;
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(x, y);
    ctx.stroke();
  }
}
