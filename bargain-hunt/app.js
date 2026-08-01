/* The Bargain Hunt — UI.
 *
 * All shared state and API work lives in core.js so the service worker can
 * run the same hunt in the background. This file hydrates that state into
 * memory once at boot, then every render is synchronous.
 */
(() => {
  "use strict";

  const BH = self.BH;

  /* ----------------------------------------------------------- app state */

  const state = {
    screen: "brief",
    settings: Object.assign({}, BH.DEFAULTS),
    cached: null, // { brief, generatedAtEpochMs, seen }
    watchMeta: {},
    hasKey: false,
    busy: false,
    step: 0,
    error: null,
    detail: null, // a single-ticker result being shown
    ready: false,
  };

  const STEPS = [
    "Scanning decliners",
    "Filtering for size and quality",
    "Pulling valuation numbers",
    "Grading candidates",
  ];

  /* ------------------------------------------------------------ helpers */

  const el = (tag, attrs, ...kids) => {
    const n = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v === false || v == null) continue;
      if (k === "class") n.className = v;
      else if (k === "text") n.textContent = v;
      else if (k === "style") n.setAttribute("style", v);
      else if (k.startsWith("on")) n.addEventListener(k.slice(2), v);
      else n.setAttribute(k, v === true ? "" : String(v));
    }
    for (const kid of kids.flat()) {
      if (kid == null || kid === false) continue;
      n.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    }
    return n;
  };

  const isNum = (v) => typeof v === "number" && Number.isFinite(v);
  const money = (v) => (isNum(v) ? `$${v.toFixed(2)}` : "—");

  const VERDICT_CLASS = {
    "real bargain": "bargain",
    "wait and see": "wait",
    "value trap": "trap",
  };

  function fmtWhen(ms) {
    const d = new Date(ms);
    const day = d.toLocaleDateString(undefined, {
      weekday: "long",
      day: "numeric",
      month: "long",
    });
    const time = d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
    return `${day} · ${time}`;
  }

  function timeLabel(s) {
    const d = new Date();
    d.setHours(s.refreshHour, s.refreshMinute, 0, 0);
    return d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
  }

  function announce(msg) {
    const live = document.getElementById("live");
    if (live) live.textContent = msg;
  }

  let snackTimer = null;
  function snackbar(message, actionLabel, onAction) {
    const host = document.getElementById("snackbar-host");
    host.textContent = "";
    clearTimeout(snackTimer);
    host.append(
      el(
        "div",
        { class: "snackbar" },
        el("span", { text: message }),
        actionLabel
          ? el("button", {
              type: "button",
              text: actionLabel,
              onclick: () => {
                host.textContent = "";
                onAction();
              },
            })
          : null
      )
    );
    snackTimer = setTimeout(() => (host.textContent = ""), 7000);
  }

  /* ----------------------------------------- the range bar (spec §11) --- */

  function rangeCaption(pos) {
    if (pos <= 0.12) return "Trading at the bottom of its 52-week range.";
    if (pos <= 0.33) return "In the lower third of its 52-week range.";
    if (pos <= 0.66) return "Around mid-range for the past year.";
    return "Still in the upper third of its 52-week range.";
  }

  function rangeBar(c) {
    const { price, prevClose, low52, high52 } = c;

    // Never draw a bar from invented numbers (spec §11 degradation).
    if (!isNum(price) || !isNum(low52) || !isNum(high52) || high52 <= low52) {
      return el("p", { class: "range-caption", text: "52-week range not confirmed." });
    }

    const span = high52 - low52;
    const at = (v) => Math.min(1, Math.max(0, (v - low52) / span));
    const now = at(price);
    const prev = isNum(prevClose) ? at(prevClose) : null;
    const pct = (n) => `${(n * 100).toFixed(2)}%`;

    // Track, with the fall segment laid over it. Percentage widths mean the
    // bar reflows with the container and with font scaling.
    const track = el("div", {
      style:
        "position:relative;height:8px;border-radius:4px;background:var(--line);overflow:hidden",
    });

    if (prev !== null && prev > now) {
      // The size of the drop shown in the context of the year — a 15% fall
      // from a high looks very different to a 15% fall to a new low.
      track.append(
        el("div", {
          style:
            `position:absolute;top:0;bottom:0;left:${pct(now)};` +
            `width:${pct(prev - now)};background:var(--trap);opacity:.65`,
        })
      );
    }

    const bar = el(
      "div",
      { style: "position:relative;padding:9px 0" },
      track,
      el("div", {
        style:
          `position:absolute;top:4px;left:${pct(now)};width:3px;height:18px;` +
          `background:var(--ink);border-radius:2px;transform:translateX(-50%)`,
      })
    );
    bar.setAttribute("role", "img");
    bar.setAttribute(
      "aria-label",
      `52-week range ${money(low52)} to ${money(high52)}; trading at ${money(price)}.`
    );

    return el(
      "div",
      { class: "range" },
      bar,
      el(
        "div",
        { class: "range-labels" },
        el("span", { text: `${money(low52)} low` }),
        el("span", { text: `${money(high52)} high` })
      ),
      el("p", { class: "range-caption", text: rangeCaption(now) })
    );
  }

  /* ------------------------------------------------- tearsheet (spec §10) */

  function tearsheet(c) {
    const verdict = String(c.verdict || "").toLowerCase();
    const causeType = String(c.causeType || "").toLowerCase();
    const causeClass =
      { "one-off": "oneoff", fixable: "fixable", permanent: "permanent" }[causeType] || "neutral";
    const metrics = Array.isArray(c.metrics) ? c.metrics.slice(0, 8) : [];

    return el(
      "article",
      { class: "card" },
      el(
        "div",
        { class: "card-head" },
        el("span", { class: "ticker", text: c.ticker || "—" }),
        el("span", {
          class: `pill ${VERDICT_CLASS[verdict] || "neutral"}`,
          text: c.verdict || "unrated",
        }),
        c.name ? el("span", { class: "name", text: c.name }) : null
      ),

      el(
        "div",
        { class: "prices" },
        el("span", { class: "now", text: money(c.price) }),
        isNum(c.prevClose) ? el("span", { class: "prev", text: money(c.prevClose) }) : null,
        el("span", {
          class: "drop",
          text: isNum(c.dropPct) ? `${c.dropPct.toFixed(1)}%` : "—",
        })
      ),
      c.dropWindow ? el("div", { class: "window", text: `over ${c.dropWindow}` }) : null,

      rangeBar(c),

      c.business
        ? el(
            "div",
            { class: "section" },
            el("div", { class: "label" }, "The business"),
            el("p", { text: c.business })
          )
        : null,

      c.cause
        ? el(
            "div",
            { class: "section" },
            el(
              "div",
              { class: "label" },
              "Why it dropped",
              causeType ? el("span", { class: `cause-type ${causeClass}`, text: causeType }) : null
            ),
            el("p", { text: c.cause })
          )
        : null,

      metrics.length
        ? el(
            "div",
            { class: "metrics" },
            metrics.map((m) =>
              el(
                "div",
                { class: "metric" },
                el("div", { class: "m-label", text: m.label || "" }),
                // The model is told to send "n/a" for anything it cannot
                // confirm; render that as a dash, never as a number.
                el("div", {
                  class: "m-value",
                  text: !m.value || /^n\s*\/?\s*a$/i.test(String(m.value)) ? "—" : m.value,
                })
              )
            )
          )
        : null,

      c.right
        ? el(
            "div",
            { class: "section" },
            el("div", { class: "label" }, "Must go right"),
            el("p", { text: c.right })
          )
        : null,

      c.wrong
        ? el(
            "div",
            { class: "section" },
            el("div", { class: "label" }, "Could go wrong"),
            el("p", { text: c.wrong })
          )
        : null
    );
  }

  /* --------------------------------------------------------- running work */

  let stepTimer = null;
  function startSteps() {
    state.step = 0;
    clearInterval(stepTimer);
    stepTimer = setInterval(() => {
      if (state.step < STEPS.length - 1) {
        state.step += 1;
        render();
      }
    }, 12000);
  }
  function stopSteps() {
    clearInterval(stepTimer);
    stepTimer = null;
  }

  function needKey() {
    state.screen = "settings";
    state.error = "Add your Anthropic API key first.";
    render();
  }

  async function runHunt() {
    if (state.busy) return;
    if (!state.hasKey) return needKey();

    state.busy = true;
    state.error = null;
    state.detail = null;
    startSteps();
    render();
    announce("Running the hunt.");

    try {
      const apiKey = await BH.getApiKey();
      const brief = await BH.runPrompt(BH.buildHuntPrompt(state.settings), apiKey);
      await BH.putBrief(brief, true); // he is looking at it right now
      state.cached = await BH.getBrief();
      state.error = null;
      announce(`${brief.candidates.length} candidates found.`);
      // Ask for notifications only after the value is obvious (spec §14).
      maybeAskForNotifications();
    } catch (err) {
      // Never clear a good cached brief because a refresh failed.
      state.error = (err && err.message) || "Something went wrong.";
      announce("The run failed.");
    } finally {
      state.busy = false;
      stopSteps();
      render();
    }
  }

  async function checkTicker(ticker) {
    if (state.busy) return;
    if (!state.hasKey) return needKey();

    state.busy = true;
    state.error = null;
    state.detail = { ticker, candidate: null };
    startSteps();
    render();

    try {
      const apiKey = await BH.getApiKey();
      const res = await BH.runPrompt(BH.buildCheckPrompt(ticker), apiKey);
      const cand = res.candidates && res.candidates[0];
      if (!cand) throw new Error(`Nothing came back for ${ticker}.`);
      state.detail = { ticker, candidate: cand };

      state.watchMeta[ticker] = { name: cand.name || "", verdict: cand.verdict || "" };
      await BH.putWatchMeta(state.watchMeta);
    } catch (err) {
      state.error = (err && err.message) || "Something went wrong.";
      state.detail = null;
    } finally {
      state.busy = false;
      stopSteps();
      render();
    }
  }

  /* --------------------------------------------------------- notifications */

  async function maybeAskForNotifications() {
    if (!("Notification" in window)) return;
    if (Notification.permission !== "default") return;
    if (!state.settings.notifyOnNewBrief && !state.settings.notifyOnWatchlistDrop) return;
    try {
      await Notification.requestPermission();
    } catch {}
  }

  /* --------------------------------------------------------- brief screen */

  function progressView() {
    const pct = ((state.step + 1) / STEPS.length) * 100;
    return el(
      "div",
      { class: "progress" },
      el("div", { class: "track" }, el("div", { class: "fill", style: `width:${pct}%` })),
      el("div", { class: "step", text: `${STEPS[state.step]}…` }),
      el("p", {
        class: "range-caption",
        text: "Searching live sources. This usually takes under a minute.",
      })
    );
  }

  function errorBanner() {
    if (!state.error) return null;
    return el("div", { class: "banner error", role: "alert" }, el("span", { text: state.error }));
  }

  function disclaimerBlock() {
    return el(
      "details",
      { class: "disclaimer" },
      el("summary", { text: "Important" }),
      el("p", { text: BH.DISCLAIMER })
    );
  }

  function briefScreen() {
    const nodes = [];
    if (state.busy) nodes.push(progressView());
    if (state.error) nodes.push(errorBanner());

    if (!state.cached) {
      if (!state.busy) {
        nodes.push(
          el(
            "div",
            { class: "state" },
            el("h2", { text: "No brief yet" }),
            el("p", {
              text: `The first one arrives tomorrow at ${timeLabel(state.settings)}. Or run it now.`,
            }),
            el("button", { class: "btn", type: "button", text: "Run the hunt", onclick: runHunt })
          )
        );
      }
      nodes.push(disclaimerBlock());
      return nodes;
    }

    const { brief, generatedAtEpochMs } = state.cached;

    // Do not hide stale data — he can still read it (spec §10.1).
    if (Date.now() - generatedAtEpochMs > BH.STALE_AFTER_MS) {
      const day = new Date(generatedAtEpochMs).toLocaleDateString(undefined, { weekday: "long" });
      nodes.push(
        el("div", { class: "banner" }, `This brief is from ${day}. Pull down to refresh.`)
      );
    }

    const cands = brief.candidates;
    const counts = {};
    for (const c of cands) {
      const v = String(c.verdict || "").toLowerCase();
      counts[v] = (counts[v] || 0) + 1;
    }
    const parts = [];
    if (counts["real bargain"]) parts.push(`${counts["real bargain"]} real bargain`);
    if (counts["wait and see"]) parts.push(`${counts["wait and see"]} to watch`);
    if (counts["value trap"]) parts.push(`${counts["value trap"]} value trap`);

    nodes.push(
      el(
        "section",
        { class: "summary" },
        el("div", {
          class: "count",
          text: `${cands.length} candidate${cands.length === 1 ? "" : "s"}`,
        }),
        parts.length ? el("div", { class: "breakdown", text: parts.join(" · ") }) : null,
        brief.note ? el("p", { class: "note", text: brief.note }) : null
      )
    );

    nodes.push(...cands.map(tearsheet));
    nodes.push(disclaimerBlock());
    return nodes;
  }

  /* ----------------------------------------------------------- watchlist */

  async function removeTicker(t) {
    const idx = state.settings.watchlist.indexOf(t);
    if (idx === -1) return;
    state.settings.watchlist = state.settings.watchlist.filter((x) => x !== t);
    await BH.putSettings(state.settings);
    render();
    snackbar(`Removed ${t}.`, "Undo", async () => {
      const next = state.settings.watchlist.slice();
      next.splice(Math.min(idx, next.length), 0, t);
      state.settings.watchlist = next;
      await BH.putSettings(state.settings);
      render();
    });
  }

  function watchScreen() {
    const nodes = [];
    if (state.busy) nodes.push(progressView());
    if (state.error) nodes.push(errorBanner());

    if (state.detail && state.detail.candidate) {
      nodes.push(
        el("button", {
          class: "btn ghost",
          type: "button",
          text: "← Back to watchlist",
          onclick: () => {
            state.detail = null;
            render();
          },
        })
      );
      nodes.push(tearsheet(state.detail.candidate));
      nodes.push(disclaimerBlock());
      return nodes;
    }

    const list = state.settings.watchlist;

    const input = el("input", {
      type: "text",
      class: "ticker-input",
      maxlength: "6",
      placeholder: "Ticker",
      "aria-label": "Ticker to add",
      autocapitalize: "characters",
      autocomplete: "off",
      spellcheck: "false",
    });
    input.addEventListener("input", () => {
      input.value = input.value.toUpperCase().replace(/[^A-Z.\-]/g, "").slice(0, 6);
    });

    const addTicker = async () => {
      const t = input.value.trim();
      if (!t) return;
      if (state.settings.watchlist.includes(t)) {
        input.value = "";
        snackbar(`${t} is already on the list.`);
        return;
      }
      state.settings.watchlist = state.settings.watchlist.concat(t);
      await BH.putSettings(state.settings);
      input.value = "";
      render();
    };
    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") addTicker();
    });

    nodes.push(
      el(
        "div",
        { class: "group" },
        el("h2", { text: "Watchlist" }),
        el(
          "div",
          { class: "add-row" },
          input,
          el("button", { class: "btn", type: "button", text: "Add", onclick: addTicker })
        ),
        list.map((t) => {
          const m = state.watchMeta[t] || {};
          const vclass = VERDICT_CLASS[String(m.verdict || "").toLowerCase()];
          return el(
            "div",
            { class: "watch-row" },
            el(
              "button",
              {
                class: "w-main",
                type: "button",
                onclick: () => checkTicker(t),
                "aria-label": `Check ${t} now`,
              },
              el("span", { class: "w-ticker", text: t }),
              m.name ? el("span", { class: "w-name", text: m.name }) : null
            ),
            m.verdict ? el("span", { class: `pill ${vclass || "neutral"}`, text: m.verdict }) : null,
            el("button", {
              class: "remove",
              type: "button",
              text: "✕",
              "aria-label": `Remove ${t}`,
              onclick: () => removeTicker(t),
            })
          );
        })
      )
    );

    if (!list.length) {
      nodes.push(
        el(
          "div",
          { class: "state" },
          el("h2", { text: "Nothing on the watchlist" }),
          el("p", { text: "Add a ticker to check it any time." })
        )
      );
    }

    return nodes;
  }

  /* ------------------------------------------------------------- settings */

  function settingsScreen() {
    const s = state.settings;
    const nodes = [];
    if (state.error) nodes.push(errorBanner());

    const update = async (patch) => {
      Object.assign(state.settings, patch);
      await BH.putSettings(state.settings);
      render();
    };

    const selectRow = (label, hint, value, options, onchange) =>
      el(
        "div",
        { class: "row" },
        el(
          "div",
          { class: "row-label" },
          el("span", { text: label }),
          hint ? el("span", { class: "hint", text: hint }) : null
        ),
        el(
          "select",
          { "aria-label": label, onchange: (e) => onchange(e.target.value) },
          options.map((o) =>
            el("option", { value: o.value, selected: String(o.value) === String(value) }, o.label)
          )
        )
      );

    const toggleRow = (label, hint, checked, onchange) => {
      const id = `t-${label.replace(/\W+/g, "-")}`;
      return el(
        "div",
        { class: "row" },
        el(
          "label",
          { class: "row-label", for: id },
          el("span", { text: label }),
          hint ? el("span", { class: "hint", text: hint }) : null
        ),
        el("input", {
          type: "checkbox",
          id,
          checked: checked || false,
          onchange: (e) => onchange(e.target.checked),
        })
      );
    };

    nodes.push(
      el(
        "div",
        { class: "group" },
        el("h2", { text: "The screen" }),
        selectRow(
          "Drop threshold",
          "How far it must have fallen",
          s.dropThreshold,
          [10, 15, 20].map((v) => ({ value: v, label: `${v}% or more` })),
          (v) => update({ dropThreshold: Number(v) })
        ),
        selectRow(
          "Minimum market cap",
          null,
          s.minMarketCap,
          Object.entries(BH.CAPS).map(([k, v]) => ({ value: k, label: v.label })),
          (v) => update({ minMarketCap: v })
        ),
        selectRow(
          "Max forward P/E",
          null,
          s.maxPe === null ? "none" : s.maxPe,
          [
            { value: "none", label: "No cap" },
            ...[10, 12, 15, 20].map((v) => ({ value: v, label: `Under ${v}` })),
          ],
          (v) => update({ maxPe: v === "none" ? null : Number(v) })
        ),
        toggleRow("Dividend payers only", null, s.dividendPayersOnly, (v) =>
          update({ dividendPayersOnly: v })
        ),
        selectRow(
          "How many candidates",
          null,
          s.candidateCount,
          [3, 5].map((v) => ({ value: v, label: String(v) })),
          (v) => update({ candidateCount: Number(v) })
        ),
        el("div", { class: "row" }, el("div", { class: "row-label", text: "Excluded sectors" })),
        el(
          "div",
          { class: "checks" },
          BH.SECTORS.map((sec) =>
            el("button", {
              class: "chip",
              type: "button",
              "aria-pressed": s.excludedSectors.includes(sec) ? "true" : "false",
              text: sec,
              onclick: () =>
                update({
                  excludedSectors: s.excludedSectors.includes(sec)
                    ? s.excludedSectors.filter((x) => x !== sec)
                    : s.excludedSectors.concat(sec),
                }),
            })
          )
        ),
        // He should never have to guess what the toggles combine to.
        el("div", { class: "preview" }, el("strong", { text: "Looking for: " }), BH.previewLine(s))
      )
    );

    const hh = String(s.refreshHour).padStart(2, "0");
    const mm = String(s.refreshMinute).padStart(2, "0");
    nodes.push(
      el(
        "div",
        { class: "group" },
        el("h2", { text: "Daily brief" }),
        el(
          "div",
          { class: "row" },
          el(
            "div",
            { class: "row-label" },
            el("span", { text: "Refresh time" }),
            el("span", {
              class: "hint",
              text: "A target, not a guarantee — the browser sets the real cadence.",
            })
          ),
          el("input", {
            type: "time",
            value: `${hh}:${mm}`,
            "aria-label": "Refresh time",
            onchange: (e) => {
              const [h, m] = String(e.target.value).split(":").map(Number);
              if (Number.isFinite(h) && Number.isFinite(m))
                update({ refreshHour: h, refreshMinute: m });
            },
          })
        ),
        toggleRow("Notify me about a new brief", null, s.notifyOnNewBrief, async (v) => {
          await update({ notifyOnNewBrief: v });
          if (v) maybeAskForNotifications();
        }),
        toggleRow(
          "Notify me when a watchlist name drops",
          "The highest-signal event the app can produce.",
          s.notifyOnWatchlistDrop,
          async (v) => {
            await update({ notifyOnWatchlistDrop: v });
            if (v) maybeAskForNotifications();
          }
        )
      )
    );

    const keyInput = el("input", {
      type: "password",
      placeholder: "sk-ant-…",
      "aria-label": "Anthropic API key",
      autocomplete: "off",
      spellcheck: "false",
    });

    nodes.push(
      el(
        "div",
        { class: "group" },
        el("h2", { text: "API key" }),
        el(
          "div",
          { class: "row" },
          el(
            "div",
            { class: "row-label" },
            el("span", { text: state.hasKey ? "Key is set" : "No key yet" }),
            el("span", {
              class: `hint key-state ${state.hasKey ? "ok" : "bad"}`,
              text: state.hasKey
                ? "✓ stored on this device"
                : "the app can't run without one",
            })
          )
        ),
        el(
          "div",
          { class: "add-row" },
          keyInput,
          el("button", {
            class: "btn",
            type: "button",
            text: state.hasKey ? "Change" : "Save",
            onclick: async () => {
              const v = keyInput.value.trim();
              if (!v) return;
              await BH.putApiKey(v);
              state.hasKey = true;
              keyInput.value = "";
              state.error = null;
              // Show him the app working within a minute of setup, rather
              // than waiting until tomorrow (spec §13).
              if (!state.cached) {
                state.screen = "brief";
                render();
                runHunt();
              } else {
                render();
                snackbar("Key saved.");
              }
            },
          })
        ),
        state.hasKey
          ? el(
              "div",
              { class: "row" },
              el("button", {
                class: "btn ghost",
                type: "button",
                text: "Forget key",
                onclick: async () => {
                  await BH.putApiKey("");
                  state.hasKey = false;
                  render();
                  snackbar("Key removed from this device.");
                },
              })
            )
          : null,
        el(
          "div",
          { class: "preview" },
          "Set a spend limit in the Anthropic console (Settings → Limits) so a bug can't run away. ",
          el(
            "a",
            {
              href: "https://console.anthropic.com/settings/limits",
              target: "_blank",
              rel: "noopener noreferrer",
            },
            "Open limits"
          ),
          "."
        )
      )
    );

    nodes.push(
      el(
        "div",
        { class: "group" },
        el("h2", { text: "About" }),
        el(
          "div",
          { class: "row" },
          el(
            "div",
            { class: "row-label" },
            el("span", { text: "The Bargain Hunt" }),
            el("span", { class: "hint", text: `Web app · model ${BH.MODEL}` })
          )
        ),
        el("div", { class: "preview", text: BH.DISCLAIMER })
      )
    );

    return nodes;
  }

  /* ---------------------------------------------------------------- render */

  function render() {
    const view = document.getElementById("view");
    const title = document.getElementById("screen-title");
    const sub = document.getElementById("screen-sub");
    const refreshBtn = document.getElementById("refresh-btn");

    view.textContent = "";

    let nodes = [];
    if (state.screen === "brief") {
      title.textContent = "The Bargain Hunt";
      sub.textContent = state.busy
        ? "Running…"
        : state.cached
        ? fmtWhen(state.cached.generatedAtEpochMs)
        : state.ready
        ? "No brief yet"
        : "Loading…";
      nodes = briefScreen();
      refreshBtn.classList.remove("hidden");
    } else if (state.screen === "watch") {
      title.textContent = "Watchlist";
      sub.textContent = state.detail ? state.detail.ticker : "Your own tickers";
      nodes = watchScreen();
      refreshBtn.classList.add("hidden");
    } else {
      title.textContent = "Settings";
      sub.textContent = `${state.settings.dropThreshold}%+ · above ${
        BH.CAPS[state.settings.minMarketCap].short
      }`;
      nodes = settingsScreen();
      refreshBtn.classList.add("hidden");
    }

    refreshBtn.disabled = state.busy;
    for (const n of nodes) if (n) view.append(n);

    document.querySelectorAll(".nav button").forEach((b) => {
      if (b.dataset.screen === state.screen) b.setAttribute("aria-current", "page");
      else b.removeAttribute("aria-current");
    });

    // Mark seen once the brief is actually on screen.
    if (state.screen === "brief" && !state.busy && state.cached && !state.cached.seen) {
      state.cached.seen = true;
      BH.idbSet(BH.K_BRIEF, state.cached);
    }
  }

  /* ------------------------------------------------------------- wiring */

  document.querySelectorAll(".nav button").forEach((b) => {
    b.addEventListener("click", () => {
      state.screen = b.dataset.screen;
      state.error = null;
      state.detail = null;
      render();
      document.getElementById("view").focus({ preventScroll: true });
      window.scrollTo({ top: 0 });
    });
  });

  document.getElementById("refresh-btn").addEventListener("click", runHunt);

  /* Pull-to-refresh, only from the top of the brief screen. */
  (() => {
    const hint = document.getElementById("pull-hint");
    let startY = null;
    let dist = 0;
    const THRESHOLD = 70;

    addEventListener(
      "touchstart",
      (e) => {
        if (scrollY > 0 || state.busy || state.screen !== "brief") return;
        startY = e.touches[0].clientY;
      },
      { passive: true }
    );

    addEventListener(
      "touchmove",
      (e) => {
        if (startY === null) return;
        dist = Math.max(0, e.touches[0].clientY - startY);
        hint.style.height = `${Math.min(56, dist * 0.5)}px`;
        hint.textContent = dist > THRESHOLD ? "Release to run" : "Pull to refresh";
      },
      { passive: true }
    );

    addEventListener("touchend", () => {
      if (startY === null) return;
      const go = dist > THRESHOLD;
      startY = null;
      dist = 0;
      hint.style.height = "0px";
      hint.textContent = "";
      if (go) runHunt();
    });
  })();

  /* Service worker + best-effort periodic background refresh (spec §21). */
  if ("serviceWorker" in navigator) {
    addEventListener("load", async () => {
      try {
        const reg = await navigator.serviceWorker.register("sw.js");
        if ("periodicSync" in reg) {
          const status = await navigator.permissions
            .query({ name: "periodic-background-sync" })
            .catch(() => ({ state: "denied" }));
          if (status.state === "granted") {
            // Chrome decides the real cadence no matter what we ask for.
            await reg.periodicSync
              .register("daily-hunt", { minInterval: 12 * 60 * 60 * 1000 })
              .catch(() => {});
          }
        }
      } catch {
        /* offline reads just won't be available */
      }
    });

    navigator.serviceWorker.addEventListener("message", async (e) => {
      if (e.data && e.data.type === "brief-updated") {
        state.cached = await BH.getBrief();
        state.screen = "brief";
        render();
      }
    });
  }

  // Re-render on resume so "generated at" and the stale banner stay honest.
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) render();
  });

  /* ---------------------------------------------------------------- boot */

  (async () => {
    render(); // paint the shell immediately
    const [settings, cached, watchMeta, apiKey] = await Promise.all([
      BH.getSettings(),
      BH.getBrief(),
      BH.getWatchMeta(),
      BH.getApiKey(),
    ]);
    state.settings = settings;
    state.cached = cached;
    state.watchMeta = watchMeta || {};
    state.hasKey = !!apiKey;
    state.ready = true;

    // A brand-new install with no key should land on Settings, not on an
    // empty brief with a button that bounces him there.
    if (!state.hasKey && !state.cached) state.screen = "settings";

    render();
  })();
})();
