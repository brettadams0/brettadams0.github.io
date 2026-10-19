// Draws one agent's network with its live activations. Nodes are filled on a
// diverging scale (blue negative, grey zero, amber positive); each edge is
// drawn by the signal it is carrying right now — weight × source activation —
// so the paths that matter light up as the agent reacts.

import { INPUT_LABELS, OUTPUT_LABELS } from "../src/brain.js";

/** @typedef {import("../src/brain.js").Brain} Brain */

const POS = [255, 180, 84];
const NEG = [99, 169, 240];
const MID = [47, 59, 74];

/**
 * @param {number} v value in [-1, 1]
 * @param {number} [alpha]
 */
function diverging(v, alpha = 1) {
  const t = Math.max(-1, Math.min(1, v));
  const end = t >= 0 ? POS : NEG;
  const k = Math.abs(t);
  const c = MID.map((m, i) => Math.round(m + (end[i] - m) * k));
  return `rgba(${c[0]}, ${c[1]}, ${c[2]}, ${alpha})`;
}

export class BrainView {
  /** @param {HTMLCanvasElement} canvas */
  constructor(canvas) {
    this.canvas = canvas;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("2D canvas unavailable");
    this.ctx = ctx;
    this.w = 360;
    this.h = 260;
    this.scale = 1;
    this.resize();
    new ResizeObserver(() => this.resize()).observe(canvas);
  }

  resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const cssW = this.canvas.clientWidth || this.w;
    this.canvas.width = Math.round(cssW * dpr);
    this.canvas.height = Math.round(cssW * (this.h / this.w) * dpr);
    this.scale = this.canvas.width / this.w;
  }

  /**
   * @param {Brain | null} brain
   * @param {readonly number[]} inputs
   */
  draw(brain, inputs) {
    const { ctx, w, h } = this;
    ctx.setTransform(this.scale, 0, 0, this.scale, 0, 0);
    ctx.clearRect(0, 0, w, h);
    if (!brain) return;

    const acts = brain.activations(inputs);
    const layers = brain.layers;
    const xs = [104, 214, 296];
    const top = 14;
    const bottom = h - 14;
    /** @type {[number, number][][]} */
    const pos = layers.map((n, l) =>
      Array.from({ length: n }, (_, i) => /** @type {[number, number]} */ ([
        xs[l],
        n === 1 ? h / 2 : top + ((bottom - top) * i) / (n - 1),
      ])),
    );
    // Outputs sit nearer the middle rather than at the extremes.
    pos[2] = pos[2].map((p, i) => [p[0], h / 2 + (i - 0.5) * 70]);

    // Edges, weakest first so strong signals draw on top.
    /** @type {[number, number, number, number, number][]} */
    const edges = [];
    let off = 0;
    for (let l = 0; l + 1 < layers.length; l++) {
      const nIn = layers[l];
      const nOut = layers[l + 1];
      for (let j = 0; j < nOut; j++) {
        for (let i = 0; i < nIn; i++) {
          const signal = brain.genome[off + j * nIn + i] * acts[l][i];
          edges.push([l, i, j, signal, Math.abs(signal)]);
        }
      }
      off += nOut * nIn + nOut;
    }
    edges.sort((a, b) => a[4] - b[4]);
    for (const [l, i, j, signal, mag] of edges) {
      // Most signals sit well under 1; squaring the scale keeps the handful
      // of connections doing real work visible above the rest.
      const s = Math.min(1, mag / 1.5) ** 2;
      const [x1, y1] = pos[l][i];
      const [x2, y2] = pos[l + 1][j];
      ctx.strokeStyle = diverging(signal >= 0 ? 1 : -1, 0.05 + 0.85 * s);
      ctx.lineWidth = 0.5 + 2.5 * s;
      ctx.beginPath();
      ctx.moveTo(x1, y1);
      ctx.bezierCurveTo((x1 + x2) / 2, y1, (x1 + x2) / 2, y2, x2, y2);
      ctx.stroke();
    }

    // Nodes.
    for (let l = 0; l < layers.length; l++) {
      const r = l === 1 ? 6 : 7;
      for (let i = 0; i < layers[l]; i++) {
        const [x, y] = pos[l][i];
        ctx.fillStyle = diverging(acts[l][i]);
        ctx.strokeStyle = "#131a22";
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(x, y, r, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
      }
    }

    // Labels: text colours only, never the data colours.
    ctx.font = "10.5px 'IBM Plex Mono', ui-monospace, monospace";
    ctx.textBaseline = "middle";
    ctx.textAlign = "right";
    ctx.fillStyle = "#8695a7";
    INPUT_LABELS.forEach((label, i) => ctx.fillText(label, xs[0] - 12, pos[0][i][1]));
    ctx.textAlign = "left";
    OUTPUT_LABELS.forEach((label, i) => {
      ctx.fillStyle = "#e7ecf2";
      ctx.fillText(label, xs[2] + 12, pos[2][i][1] - 6);
      ctx.fillStyle = "#8695a7";
      const v = acts[2][i];
      ctx.fillText((v >= 0 ? "+" : "") + v.toFixed(2), xs[2] + 12, pos[2][i][1] + 8);
    });
  }
}
