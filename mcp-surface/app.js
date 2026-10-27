// The playground page. Everything that decides a finding is mcp-surface's own
// code (checks.js, bundled from the npm package); this file only parses the
// editor, builds the Surface object the CLI would build, and draws the result.

import { runChecks } from "./checks.js";
import { EXAMPLES } from "./examples.js";

// The rule table from the package README, in the same order.
const RULES = [
  ["empty-surface", "error", "Connected, but advertises nothing"],
  ["list-failed", "error", "A declared capability's list call threw"],
  ["schema-valid", "error", "A schema isn't valid JSON Schema"],
  ["schema-missing", "error", "A tool has no inputSchema"],
  ["schema-root-type", "error", "Schema root type isn't \"object\""],
  ["schema-required-props", "error", "required names a property that doesn't exist"],
  ["tool-duplicate-name", "error", "Two tools share a name"],
  ["tool-naming", "warn", "Name outside [A-Za-z0-9_-]"],
  ["tool-name-case-collision", "warn", "Names differ only by case"],
  ["tool-description-missing", "warn", "No description"],
  ["tool-annotations", "warn", "Both readOnlyHint and destructiveHint set"],
  ["mutation-annotations", "warn", "Changes state but declares no annotations"],
  ["tool-call-error", "error", "--call: a tool returned isError", true],
  ["tool-call-threw", "error", "--call: the call failed", true],
  ["tool-call-none-eligible", "info", "--call: nothing was safe to call", true],
  ["surface-tool-count", "warn", "More than 40 tools"],
  ["surface-token-cost", "warn", "Definitions over 16 KB"],
  ["tool-description-long", "info", "Description over 1024 characters"],
  ["schema-property-descriptions", "info", "Input properties with no description"],
];

const RANK = { info: 0, warn: 1, error: 2 };

const $ = (id) => {
  const el = document.getElementById(id);
  if (!el) throw new Error(`#${id} missing`);
  return el;
};

const ui = {
  source: /** @type {HTMLTextAreaElement} */ ($("source")),
  gutter: $("gutter"),
  parse: $("parse"),
  report: $("report"),
  verdict: $("verdict"),
  cmd: $("cmd"),
  failOn: /** @type {HTMLSelectElement} */ ($("fail-on")),
  rules: $("rules"),
  annotations: $("annotations"),
  breakSummary: $("break-summary"),
  diff: $("diff"),
  diffTitle: $("diff-title"),
  diffBody: $("diff-body"),
  undo: /** @type {HTMLButtonElement} */ ($("undo")),
};

const state = {
  server: EXAMPLES.broken.server,
  /** @type {Set<string>} */
  skip: new Set(),
  /** Editor texts before each "Break it" change, newest last. */
  undo: [],
};

// "Break it": each is one real change to the editor's JSON. Nothing here decides a
// finding; the report below comes from runChecks on the changed JSON, as always.
const BREAKS = {
  required: {
    what: "required now names \"city\", which the first tool's schema doesn't define",
    min: 1,
    apply: (t) => { const s = t[0].inputSchema || (t[0].inputSchema = { type: "object", properties: {} }); s.required = [...(s.required || []), "city"]; },
  },
  duplicate: {
    what: "the second tool now has the first tool's name",
    min: 2,
    apply: (t) => { t[1].name = t[0].name; },
  },
  root: {
    what: "the first tool's inputSchema type is now \"array\"",
    min: 1,
    apply: (t) => { (t[0].inputSchema || (t[0].inputSchema = {})).type = "array"; },
  },
  description: {
    what: "the first tool's description is gone",
    min: 1,
    apply: (t) => { delete t[0].description; },
  },
};

function el(tag, cls, text) {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text !== undefined) node.textContent = text;
  return node;
}

function plural(n, one, many = one + "s") {
  return `${n} ${n === 1 ? one : many}`;
}

/** Line and column for a JSON.parse error, from whichever form the engine reports. */
function locate(err, text) {
  const msg = String(err && err.message);
  const lc = msg.match(/line (\d+) column (\d+)/);
  if (lc) return `line ${lc[1]}, column ${lc[2]}`;
  const pos = msg.match(/position (\d+)/);
  if (!pos) return "";
  const before = text.slice(0, Number(pos[1])).split("\n");
  return `line ${before.length}, column ${before[before.length - 1].length + 1}`;
}

/** Accepts a bare tools array, a tools/list result, or the whole JSON-RPC response. */
function toolsFrom(data) {
  if (Array.isArray(data)) return data;
  if (data && Array.isArray(data.tools)) return data.tools;
  if (data && data.result && Array.isArray(data.result.tools)) return data.result.tools;
  throw new Error('expected { "tools": [...] }, a JSON-RPC response, or an array of tools');
}

function surfaceFor(tools) {
  return {
    server: { name: state.server, version: "1.0.0" },
    capabilities: { tools: {} },
    tools,
    resources: [],
    prompts: [],
    listErrors: [],
  };
}

/** 1-based editor lines for each finding: the line(s) naming its subject tool. */
function linesFor(findings, text) {
  const lines = text.split("\n");
  const at = new Map();
  for (const f of findings) {
    if (!f.subject) continue;
    const needle = `"name": ${JSON.stringify(f.subject)}`;
    lines.forEach((l, i) => { if (l.includes(needle)) at.set(i + 1, [...(at.get(i + 1) || []), f]); });
  }
  return at;
}

function renderGutter(marks = new Map()) {
  const lines = ui.source.value.split("\n").length;
  ui.gutter.replaceChildren();
  for (let i = 1; i <= lines; i++) {
    const fs = marks.get(i);
    if (!fs) { ui.gutter.append(i + "\n"); continue; }
    const worst = fs.reduce((a, f) => (RANK[f.level] > RANK[a] ? f.level : a), "info");
    const mark = el("span", `mark-${worst}`, `● ${i}\n`);
    mark.title = fs.map((f) => `${f.rule}: ${f.message}`).join("\n");
    ui.gutter.append(mark);
  }
  ui.gutter.scrollTop = ui.source.scrollTop;
}

function renderAnnotations(findings, marks) {
  ui.annotations.replaceChildren();
  const pinned = new Set();
  for (const [line, fs] of [...marks].sort((a, b) => a[0] - b[0])) {
    for (const f of fs) {
      pinned.add(f);
      ui.annotations.append(el("li", `lvl-${f.level}`, `line ${line} · ${f.rule} · ${f.message}`));
    }
  }
  for (const f of findings) if (!pinned.has(f)) ui.annotations.append(el("li", `lvl-${f.level}`, `${f.rule} · ${f.message}`));
}

/** Line diff (LCS) between two texts, as hunks with one line of context. */
function lineDiff(a, b) {
  const x = a.split("\n"), y = b.split("\n");
  const n = x.length, m = y.length;
  const dp = Array.from({ length: n + 1 }, () => new Uint16Array(m + 1));
  for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) dp[i][j] = x[i] === y[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
  const ops = [];
  let i = 0, j = 0;
  while (i < n || j < m) {
    if (i < n && j < m && x[i] === y[j]) { ops.push([" ", x[i]]); i++; j++; }
    else if (j < m && (i === n || dp[i][j + 1] >= dp[i + 1][j])) { ops.push(["+", y[j]]); j++; }
    else { ops.push(["-", x[i]]); i++; }
  }
  return ops.filter((op, k) => op[0] !== " " || [k - 1, k + 1].some((q) => ops[q] && ops[q][0] !== " "));
}

function showDiff(before, after, what) {
  ui.diffTitle.textContent = `Changed: ${what}`;
  ui.diffBody.replaceChildren(...lineDiff(before, after).map(([op, line]) =>
    el("span", op === "+" ? "add" : op === "-" ? "del" : "ctx", `${op} ${line}`)));
  ui.diff.hidden = false;
}

function renderCommand() {
  let cmd = "npx mcp-surface node ./server.js";
  if (ui.failOn.value !== "error") cmd += ` --fail-on ${ui.failOn.value}`;
  for (const rule of state.skip) cmd += ` --skip ${rule}`;
  ui.cmd.textContent = cmd;
}

function renderRules(findings) {
  const hits = new Map();
  for (const f of findings) hits.set(f.rule, (hits.get(f.rule) || 0) + 1);
  const focused = document.activeElement?.dataset.rule;   // the rebuild below replaces the pressed button
  ui.rules.replaceChildren();
  for (const [id, level, what, callOnly] of RULES) {
    const li = el("li");
    const btn = el("button", `rule lvl-${level}`);
    btn.type = "button";
    btn.dataset.rule = id;
    btn.setAttribute("aria-pressed", String(state.skip.has(id)));
    if (callOnly) btn.classList.add("call-only");
    if (hits.has(id)) btn.classList.add("hit");
    btn.title = state.skip.has(id) ? `Skipped. Click to run ${id} again.` : `Click to skip ${id}.`;
    // State in words, not opacity: "skipped" in the level column, "--call only:" in the description.
    const shown = callOnly ? what.replace(/^--call:/, "--call only:") : what;
    btn.append(el("span", "rule-level", state.skip.has(id) ? "skipped" : level), el("code", "rule-id", id), el("span", "rule-what", shown));
    if (hits.has(id)) btn.append(el("span", "rule-hits", String(hits.get(id))));
    btn.addEventListener("click", () => {
      if (state.skip.has(id)) state.skip.delete(id);
      else state.skip.add(id);
      update();
    });
    li.append(btn);
    ui.rules.append(li);
  }
  if (focused) ui.rules.querySelector(`[data-rule="${focused}"]`)?.focus();
}

function renderReport(tools, findings) {
  const out = ui.report;
  out.replaceChildren();

  const head = el("p", "line");
  head.append(el("b", null, state.server), el("span", "dim", " 1.0.0"));
  out.append(head);
  out.append(el("p", "line dim", `${plural(tools.length, "tool")} · 0 resources · 0 prompts · capabilities: tools`));

  if (tools.length) {
    const list = el("div", "tools");
    for (const t of tools) {
      const row = el("p", "line");
      const name = typeof t?.name === "string" && t.name ? t.name : "<unnamed>";
      row.append(el("span", "tool-name", name));
      const desc = typeof t?.description === "string" ? t.description : "";
      if (desc) row.append(el("span", "dim", ` — ${desc.length > 64 ? desc.slice(0, 63) + "…" : desc}`));
      list.append(row);
    }
    out.append(list);
  }

  if (!findings.length) {
    out.append(el("p", "line ok", "No findings."));
  } else {
    const list = el("div", "findings");
    const sorted = [...findings].sort((a, b) => RANK[b.level] - RANK[a.level]);
    for (const f of sorted) {
      const row = el("p", `finding lvl-${f.level}`);
      row.append(el("span", "lvl", f.level));
      if (f.subject) row.append(el("span", "subject", f.subject));
      row.append(el("span", "msg", f.message), el("span", "rule-tag", `[${f.rule}]`));
      list.append(row);
    }
    out.append(list);
  }

  const count = { error: 0, warn: 0, info: 0 };
  for (const f of findings) count[f.level]++;
  out.append(
    el("p", "line summary", `${plural(count.error, "error")}, ${plural(count.warn, "warning")}, ${count.info} info`),
  );

  const threshold = RANK[ui.failOn.value];
  const fails = findings.some((f) => RANK[f.level] >= threshold);
  state.fails = fails;
  ui.verdict.className = `verdict ${fails ? "fail" : "pass"}`;
  ui.verdict.replaceChildren(
    el("b", null, fails ? "exit 1" : "exit 0"),
    el("span", null, fails ? "CI fails" : "CI passes"),
  );
}

function update() {
  renderGutter();
  renderCommand();
  const text = ui.source.value;
  let tools;
  try {
    tools = toolsFrom(JSON.parse(text));
  } catch (err) {
    const where = err instanceof SyntaxError ? locate(err, text) : "";
    ui.parse.textContent = err instanceof SyntaxError
      ? `Not valid JSON${where ? ` at ${where}` : ""}.`
      : `Can't read tools: ${err.message}.`;
    ui.parse.classList.add("bad");
    ui.source.setAttribute("aria-invalid", "true");
    return;
  }
  ui.parse.textContent = `${plural(tools.length, "tool")} parsed.`;
  ui.parse.classList.remove("bad");
  ui.source.removeAttribute("aria-invalid");
  const findings = runChecks(surfaceFor(tools), { skip: [...state.skip] });
  const marks = linesFor(findings, text);
  renderGutter(marks);
  renderAnnotations(findings, marks);
  renderReport(tools, findings);
  renderRules(findings);
  state.tools = tools;
  state.findings = findings;
  for (const b of document.querySelectorAll("[data-break]")) {
    b.disabled = tools.length < BREAKS[b.getAttribute("data-break")].min;
  }
}

function summarize(before) {
  const now = state.fails ? "CI fails" : "CI passes";
  const was = before ? "CI failed" : "CI passed";
  const rules = [...new Set(state.findings.map((f) => `${f.level} ${f.rule}`))].join(", ") || "no findings";
  ui.breakSummary.replaceChildren(el("span", state.fails ? "fail" : "pass", `${was} → ${now}`), document.createTextNode(` (fail on ${ui.failOn.value}: ${rules})`));
}

function breakIt(key) {
  const before = ui.source.value;
  let data;
  try { data = JSON.parse(before); toolsFrom(data); } catch { return; }
  const wasFailing = state.fails;
  const tools = toolsFrom(data);
  BREAKS[key].apply(tools);
  state.undo.push(before);
  ui.source.value = JSON.stringify(data, null, 2);
  ui.undo.disabled = false;
  update();
  showDiff(before, ui.source.value, BREAKS[key].what);
  summarize(wasFailing);
}

function undo() {
  if (!state.undo.length) return;
  const wasFailing = state.fails;
  const before = ui.source.value;
  ui.source.value = state.undo.pop();
  ui.undo.disabled = !state.undo.length;
  update();
  showDiff(before, ui.source.value, "undone");
  summarize(wasFailing);
}

function load(key) {
  const ex = EXAMPLES[key];
  state.server = ex.server;
  ui.source.value = JSON.stringify(ex.tools, null, 2);
  ui.source.scrollTop = 0;
  state.undo = [];
  ui.undo.disabled = true;
  ui.diff.hidden = true;
  ui.breakSummary.textContent = "";
  for (const b of document.querySelectorAll("[data-example]")) {
    b.setAttribute("aria-pressed", String(b.getAttribute("data-example") === key));
  }
  update();
}

let timer = 0;
ui.source.addEventListener("input", () => {
  renderGutter();
  clearTimeout(timer);
  timer = setTimeout(update, 120);
});
ui.source.addEventListener("scroll", () => {
  ui.gutter.scrollTop = ui.source.scrollTop;
});
ui.failOn.addEventListener("change", update);
for (const b of document.querySelectorAll("[data-example]")) {
  b.addEventListener("click", () => load(b.getAttribute("data-example")));
}
for (const b of document.querySelectorAll("[data-break]")) {
  b.addEventListener("click", () => breakIt(b.getAttribute("data-break")));
}
ui.undo.addEventListener("click", undo);

load("healthy"); // start from a passing server, so the first change shows CI flip
