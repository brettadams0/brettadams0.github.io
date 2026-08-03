/* The Bargain Hunt — UI.
 *
 * A reader over the brief the morning run publishes. No API key, no network
 * cost. All shared state lives in core.js so the service worker can sync the
 * same brief in the background.
 */
(() => {
  "use strict";

  const BH = self.BH;

  const state = {
    screen: "brief",
    settings: Object.assign({}, BH.DEFAULTS),
    cached: null, // { brief, generatedAtEpochMs, seen }
    busy: false,
    error: null,
    detail: null, // a watchlist entry being shown
    ready: false,
  };

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
      return el("div", { class: "range-unknown" }, "52-week range not confirmed");
    }

    const span = high52 - low52;
    const at = (v) => Math.min(1, Math.max(0, (v - low52) / span));
    const now = at(price);
    const prev = isNum(prevClose) ? at(prevClose) : null;
    const pct = (n) => `${(n * 100).toFixed(2)}%`;

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
      { class: `card v-${VERDICT_CLASS[verdict] || "neutral"}` },
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
            { class: "section pair right" },
            el("div", { class: "label" }, "Must go right"),
            el("p", { text: c.right })
          )
        : null,

      c.wrong
        ? el(
            "div",
            { class: "section pair wrong" },
            el("div", { class: "label" }, "Could go wrong"),
            el("p", { text: c.wrong })
          )
        : null
    );
  }

  /* ------------------------------------------------------------- syncing */

  async function syncNow(quiet) {
    if (state.busy) return;
    state.busy = true;
    if (!quiet) {
      state.error = null;
      render();
      announce("Checking for today's brief.");
    }

    try {
      const result = await BH.syncPublished();
      state.cached = result.cached;
      state.error = null;
      if (!quiet) {
        announce(result.changed ? "Brief updated." : "Already up to date.");
        if (!result.changed) snackbar("Already up to date.");
      }
    } catch (err) {
      // A failed sync must never clear a good cached brief.
      if (!quiet || !state.cached) state.error = (err && err.message) || "Something went wrong.";
    } finally {
      state.busy = false;
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

  function loadingView() {
    return el(
      "div",
      { class: "progress" },
      el("div", { class: "track" }, el("div", { class: "fill", style: "width:60%" })),
      el("div", { class: "step", text: "Checking for today's brief…" })
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
      el("summary", { text: "Not investment advice — tap to read" }),
      el("p", { text: BH.DISCLAIMER })
    );
  }

  function footnote() {
    return el("p", { class: "footnote", text: "A new brief arrives each morning" });
  }

  function briefScreen() {
    const nodes = [];
    if (state.busy) nodes.push(loadingView());
    if (state.error) nodes.push(errorBanner());

    if (!state.cached) {
      if (!state.busy) {
        nodes.push(
          el(
            "div",
            { class: "state" },
            el("h2", { text: "No brief yet" }),
            el("p", {
              text: "The first one arrives after tomorrow morning's run. Or check now.",
            }),
            el("button", { class: "btn", type: "button", text: "Check now", onclick: () => syncNow() })
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

    const all = brief.candidates;
    const shown = BH.filterCandidates(all, state.settings);

    if (!shown.length) {
      nodes.push(
        el(
          "div",
          { class: "state" },
          el("h2", { text: "Nothing matches your settings" }),
          el("p", {
            text:
              `The morning run found ${all.length} candidate${all.length === 1 ? "" : "s"}, ` +
              "but none get through your filters. Loosen them in Settings.",
          }),
          el("button", {
            class: "btn ghost",
            type: "button",
            text: "Open Settings",
            onclick: () => go("settings"),
          })
        )
      );
      nodes.push(disclaimerBlock());
      return nodes;
    }

    const counts = {};
    for (const c of shown) {
      const v = String(c.verdict || "").toLowerCase();
      counts[v] = (counts[v] || 0) + 1;
    }
    const tallies = [
      ["real bargain", "bargain", "real bargain"],
      ["wait and see", "wait", "to watch"],
      ["value trap", "trap", "value trap"],
    ]
      .filter(([key]) => counts[key])
      .map(([key, cls, label]) =>
        el("span", { class: `tally ${cls}`, text: `${counts[key]} ${label}` })
      );

    nodes.push(
      el(
        "section",
        { class: "summary" },
        el("div", {
          class: "count",
          text: `${shown.length} candidate${shown.length === 1 ? "" : "s"}`,
        }),
        tallies.length ? el("div", { class: "breakdown" }, tallies) : null,
        brief.note ? el("p", { class: "note", text: brief.note }) : null,
        // Be explicit that a wider screen ran behind this, so a thin list
        // reads as "my filters are tight", not "nothing happened today".
        all.length > shown.length
          ? el("p", {
              class: "screened",
              text: `Screened ${all.length} · showing the ${shown.length} that match your settings`,
            })
          : null
      )
    );

    nodes.push(...shown.map(tearsheet));
    nodes.push(disclaimerBlock());
    nodes.push(footnote());
    return nodes;
  }

  /* ----------------------------------------------------------- watchlist */

  function watchScreen() {
    const nodes = [];
    if (state.error) nodes.push(errorBanner());

    const watch =
      state.cached && Array.isArray(state.cached.brief.watch) ? state.cached.brief.watch : [];

    if (state.detail) {
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
      nodes.push(tearsheet(state.detail));
      nodes.push(disclaimerBlock());
      return nodes;
    }

    nodes.push(
      el(
        "div",
        { class: "group" },
        el("h2", { text: "Watchlist" }),
        watch.length
          ? watch.map((c) => {
              const vclass = VERDICT_CLASS[String(c.verdict || "").toLowerCase()];
              return el(
                "div",
                { class: "watch-row" },
                el(
                  "button",
                  {
                    class: "w-main",
                    type: "button",
                    onclick: () => {
                      state.detail = c;
                      render();
                      window.scrollTo({ top: 0 });
                    },
                    "aria-label": `Open ${c.ticker}`,
                  },
                  el("span", { class: "w-ticker", text: c.ticker || "—" }),
                  c.name ? el("span", { class: "w-name", text: c.name }) : null
                ),
                c.verdict
                  ? el("span", { class: `pill ${vclass || "neutral"}`, text: c.verdict })
                  : null
              );
            })
          : null,
        el(
          "div",
          { class: "preview" },
          "These are checked every morning alongside the screen. ",
          el(
            "a",
            { href: BH.WATCHLIST_EDIT_URL, target: "_blank", rel: "noopener noreferrer" },
            "Edit the list"
          ),
          " — it opens the file on GitHub, which works fine on a phone."
        )
      )
    );

    if (!watch.length) {
      nodes.push(
        el(
          "div",
          { class: "state" },
          el("h2", { text: "Nothing on the watchlist" }),
          el("p", { text: "Add a ticker to have it checked every morning." })
        )
      );
    }

    return nodes;
  }

  /* ------------------------------------------------------------- settings */

  // Shows what these settings do to the brief he actually has, and says so
  // when a limit cannot bite because the figure is missing. A setting that
  // changes nothing should never be mistakable for a setting that is broken.
  function liveEffect(s) {
    if (!state.cached) {
      return el("div", {
        class: "preview effect",
        text: "No brief yet, so there is nothing to filter.",
      });
    }

    const stats = BH.filterStats(state.cached.brief.candidates, s);
    const gaps = BH.blindSpots(stats, s);

    return el(
      "div",
      { class: "preview effect" },
      el(
        "div",
        { class: "effect-count" },
        el("strong", { text: `${stats.shown} of ${stats.total}` }),
        ` in today's brief match`
      ),
      gaps.length
        ? el("div", {
            class: "effect-gap",
            text: `${gaps.join(", ")} — those can't be ruled out, so they stay.`,
          })
        : null
    );
  }

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
        el("div", { class: "preview" }, el("strong", { text: "Looking for: " }), BH.previewLine(s)),
        liveEffect(s)
      )
    );

    nodes.push(
      el(
        "div",
        { class: "group" },
        el("h2", { text: "Notifications" }),
        toggleRow("Tell me about a new brief", null, s.notifyOnNewBrief, async (v) => {
          await update({ notifyOnNewBrief: v });
          if (v) maybeAskForNotifications();
        }),
        toggleRow(
          "Tell me when a watchlist name drops",
          "The highest-signal event the app can produce.",
          s.notifyOnWatchlistDrop,
          async (v) => {
            await update({ notifyOnWatchlistDrop: v });
            if (v) maybeAskForNotifications();
          }
        )
      )
    );

    nodes.push(
      el(
        "div",
        { class: "group" },
        el("h2", { text: "How it updates" }),
        el("div", {
          class: "preview",
          text:
            "A scheduled job runs the hunt every morning and publishes the " +
            "brief. This app just reads it — there is no API key on your " +
            "phone and it costs nothing to run.",
        }),
        el(
          "div",
          { class: "row" },
          el("button", {
            class: "btn ghost",
            type: "button",
            text: "Check for a new brief",
            onclick: () => {
              go("brief");
              syncNow();
            },
          })
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
            el("span", {
              class: "hint",
              text: state.cached
                ? `Brief from ${fmtWhen(state.cached.generatedAtEpochMs)}`
                : "No brief yet",
            })
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
        ? "Checking…"
        : state.cached
        ? fmtWhen(state.cached.generatedAtEpochMs)
        : state.ready
        ? "No brief yet"
        : "Loading…";
      nodes = briefScreen();
      refreshBtn.classList.remove("hidden");
    } else if (state.screen === "watch") {
      title.textContent = "Watchlist";
      sub.textContent = state.detail ? state.detail.ticker : "Checked every morning";
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

  function go(screen) {
    state.screen = screen;
    state.error = null;
    state.detail = null;
    render();
    document.getElementById("view").focus({ preventScroll: true });
    window.scrollTo({ top: 0 });
  }

  /* ------------------------------------------------------------- wiring */

  document.querySelectorAll(".nav button").forEach((b) => {
    b.addEventListener("click", () => go(b.dataset.screen));
  });

  document.getElementById("refresh-btn").addEventListener("click", () => syncNow());

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
        hint.textContent = dist > THRESHOLD ? "Release to check" : "Pull to refresh";
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
      if (go) syncNow();
    });
  })();

  /* Service worker + best-effort periodic background sync. */
  if ("serviceWorker" in navigator) {
    addEventListener("load", async () => {
      try {
        const reg = await navigator.serviceWorker.register("sw.js");
        if ("periodicSync" in reg) {
          const status = await navigator.permissions
            .query({ name: "periodic-background-sync" })
            .catch(() => ({ state: "denied" }));
          if (status.state === "granted") {
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
        render();
      }
    });
  }

  // Re-render on resume, and quietly pick up a brief published since.
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) return;
    render();
    if (state.ready) syncNow(true);
  });

  /* ---------------------------------------------------------------- boot */

  (async () => {
    render(); // paint the shell immediately
    const [settings, cached] = await Promise.all([BH.getSettings(), BH.getBrief()]);
    state.settings = settings;
    state.cached = cached;
    state.ready = true;
    render(); // cache-first: on screen before any network call

    // Then quietly pick up anything newer. A failure here is silent when we
    // already have a brief to show.
    syncNow(!!cached);
  })();
})();
