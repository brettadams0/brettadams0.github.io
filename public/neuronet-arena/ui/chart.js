// Food eaten per agent by generation: population mean and best agent, as an
// SVG line chart with a crosshair tooltip. When the run was resumed from the
// Python-trained snapshot, a hairline marks where the browser took over.

/** @typedef {import("../src/simulation.js").GenerationStats} GenerationStats */

const NS = "http://www.w3.org/2000/svg";
const M = { left: 30, right: 10, top: 10, bottom: 22 };

/**
 * @param {string} tag
 * @param {Record<string, string | number>} [attrs]
 */
function el(tag, attrs = {}) {
  const node = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, String(v));
  return node;
}

/** @param {number} max */
function niceStep(max) {
  const raw = max / 4;
  const mag = 10 ** Math.floor(Math.log10(raw));
  for (const m of [1, 2, 2.5, 5, 10]) if (m * mag >= raw) return m * mag;
  return 10 * mag;
}

export class Chart {
  /** @param {HTMLElement} container */
  constructor(container) {
    this.container = container;
    /** @type {GenerationStats[]} */
    this.history = [];
    /** @type {number | null} */
    this.handoff = null;
    /** @type {number | null} */
    this.hover = null;

    this.svg = el("svg", { role: "img", tabindex: 0 });
    this.svg.setAttribute("aria-label", "Food eaten per agent by generation. Use the arrow keys to read values.");
    container.append(this.svg);
    this.tip = document.createElement("div");
    this.tip.className = "tooltip";
    this.tip.hidden = true;
    container.append(this.tip);

    this.svg.addEventListener("pointermove", (e) => {
      const rect = this.svg.getBoundingClientRect();
      this.hover = this.indexAt(e.clientX - rect.left);
      this.render();
    });
    this.svg.addEventListener("pointerleave", () => {
      this.hover = null;
      this.render();
    });
    this.svg.addEventListener("keydown", (e) => {
      if (!this.history.length) return;
      const last = this.history.length - 1;
      if (e.key === "ArrowLeft") this.hover = Math.max(0, (this.hover ?? last + 1) - 1);
      else if (e.key === "ArrowRight") this.hover = Math.min(last, (this.hover ?? -1) + 1);
      else if (e.key === "Escape") this.hover = null;
      else return;
      e.preventDefault();
      this.render();
    });
    this.svg.addEventListener("blur", () => {
      this.hover = null;
      this.render();
    });
    new ResizeObserver(() => this.render()).observe(container);
  }

  /**
   * @param {GenerationStats[]} history
   * @param {number | null} handoff generation at which the browser took over
   */
  reset(history, handoff) {
    this.history = history.slice();
    this.handoff = handoff;
    this.hover = null;
    this.render();
  }

  /** @param {GenerationStats} stats */
  push(stats) {
    this.history.push(stats);
    this.render();
  }

  get width() {
    return this.container.clientWidth || 340;
  }

  get height() {
    return this.container.clientHeight || 170;
  }

  /** @param {number} px */
  indexAt(px) {
    const n = this.history.length;
    if (n === 0) return null;
    const t = (px - M.left) / (this.width - M.left - M.right);
    return Math.max(0, Math.min(n - 1, Math.round(t * (n - 1))));
  }

  render() {
    const { svg, width: w, height: h } = this;
    svg.setAttribute("viewBox", `0 0 ${w} ${h}`);
    svg.replaceChildren();
    const data = this.history;
    const pw = w - M.left - M.right;
    const ph = h - M.top - M.bottom;

    if (data.length < 2) {
      const t = el("text", { x: w / 2, y: h / 2, "text-anchor": "middle", class: "empty" });
      t.textContent = "The curve starts once a generation finishes.";
      svg.append(t);
      this.tip.hidden = true;
      return;
    }

    const peak = Math.max(5, ...data.map((d) => d.best));
    const step = niceStep(peak);
    const yMax = Math.ceil(peak / step) * step;
    const n = data.length;
    /** @param {number} i */
    const sx = (i) => M.left + (pw * i) / (n - 1);
    /** @param {number} v */
    const sy = (v) => M.top + ph * (1 - v / yMax);

    for (let v = 0; v <= yMax + 1e-9; v += step) {
      svg.append(el("line", { x1: M.left, x2: M.left + pw, y1: sy(v), y2: sy(v), class: "grid" }));
      const label = el("text", { x: M.left - 6, y: sy(v) + 3.5, "text-anchor": "end", class: "axis-label" });
      label.textContent = String(v);
      svg.append(label);
    }
    const first = el("text", { x: M.left, y: h - 6, class: "axis-label" });
    first.textContent = `gen ${data[0].generation}`;
    const last = el("text", { x: M.left + pw, y: h - 6, "text-anchor": "end", class: "axis-label" });
    last.textContent = String(data[n - 1].generation);
    svg.append(first, last);

    if (this.handoff !== null) {
      const i = data.findIndex((d) => d.generation === this.handoff);
      if (i > 0) {
        const x = sx(i - 0.5);
        svg.append(el("line", { x1: x, x2: x, y1: M.top, y2: M.top + ph, class: "handoff" }));
        const tag = el("text", { x: x - 4, y: M.top + 9, "text-anchor": "end", class: "handoff-label" });
        tag.textContent = "Python | browser";
        svg.append(tag);
      }
    }

    /** @type {[keyof GenerationStats, string][]} */
    const series = [["best", "s2"], ["mean", "s1"]];
    for (const [field, cls] of series) {
      const pts = data.map((d, i) => `${sx(i).toFixed(1)},${sy(d[field]).toFixed(1)}`).join(" ");
      svg.append(el("polyline", { points: pts, class: cls }));
    }

    if (this.hover === null) {
      this.tip.hidden = true;
      return;
    }
    const d = data[this.hover];
    const x = sx(this.hover);
    svg.append(el("line", { x1: x, x2: x, y1: M.top, y2: M.top + ph, class: "crosshair" }));
    for (const [field, cls] of series) {
      svg.append(el("circle", { cx: x, cy: sy(d[field]), r: 4, class: `dot ${cls}`, fill: `var(--series-${cls === "s1" ? 1 : 2})` }));
    }

    const tip = this.tip;
    tip.replaceChildren();
    const gen = document.createElement("div");
    gen.className = "t-gen";
    gen.textContent = `generation ${d.generation}`;
    tip.append(gen);
    for (const [label, value, color] of /** @type {const} */ ([
      ["mean", d.mean.toFixed(2), "var(--series-1)"],
      ["best", String(d.best), "var(--series-2)"],
    ])) {
      const row = document.createElement("div");
      row.className = "t-row";
      const key = document.createElement("i");
      key.style.background = color;
      const b = document.createElement("b");
      b.textContent = value;
      const name = document.createElement("span");
      name.textContent = label;
      row.append(key, b, name);
      tip.append(row);
    }
    tip.hidden = false;
    tip.style.left = `${Math.max(50, Math.min(w - 50, x))}px`;
    tip.style.top = `${Math.max(46, sy(d.best) - 8)}px`;
  }
}
