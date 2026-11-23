// border-waits page: the static view. Everything shown comes from manifest.json, status.json and the
// summary file the manifest names, or from fallback/, a saved copy of one real export.
// DuckDB loads only on click.
const LIVE = '/border-waits-pipeline/data/v1/';
const SAVED = new URL('fallback/', import.meta.url).href;
const DEADLINE = 8000;  // ms a request may take before the page stops waiting for it
const STALE = 180;      // minutes after which a reading or a run is marked stale (estimated)
const TZ = 'America/Toronto';
const DIRS = [['to_canada', 'To Canada', 'CBSA'], ['to_us', 'To the US', 'U.S. CBP']];
const LANES = ['travellers', 'commercial'];
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
let D = null;           // what is on screen: { manifest, summary, status, live, base, why }
let sql = null;         // the SQL console module, once it has been asked for

// DuckDB writes '2026-10-27 17:00:00+00'; Date wants '2026-10-27T17:00:00+00:00'.
const toDate = (s) => new Date(String(s).replace(' ', 'T').replace(/([+-]\d\d)$/, '$1:00'));
const clock = (d, o = { dateStyle: 'medium', timeStyle: 'short' }) => new Intl.DateTimeFormat('en-CA', { timeZone: TZ, ...o }).format(d);
const span = (min) => { const m = Math.round(min); return m < 1 ? 'under a minute' : m < 60 ? `${m} min` : m < 2880 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${Math.round(m / 1440)} days`; };
const age = (t) => (Date.now() - toDate(t)) / 6e4;
const badge = (k) => k ? `<span class="badge ${esc(k)}">${esc(k)}</span>` : '';

async function get(url, cache) {
  const c = new AbortController(), t = setTimeout(() => c.abort(), DEADLINE);
  try {
    const r = await fetch(url, { cache, signal: c.signal });
    if (!r.ok) throw Error(`HTTP ${r.status}`);
    return await r.json();
  } catch (e) {
    throw Error(e.name === 'AbortError' ? `no answer in ${DEADLINE / 1000} s` : e.name === 'SyntaxError' ? 'not JSON' : e.message);
  } finally { clearTimeout(t); }
}

// The contract (export/contract.py applies the same rules before publishing). Checked once, before
// anything is drawn: a payload that fails is not half-rendered, the saved snapshot is shown instead.
const num = (v) => typeof v === 'number' && v >= 0;
const opt = (v, f) => v == null || f(v);
const time = (v) => !isNaN(toDate(v));
const series = (r) => r && typeof r.crossing_id === 'string' && DIRS.some((d) => d[0] === r.direction) && LANES.includes(r.lane);
function check(ok, what) { if (!ok) throw Error(`unexpected ${what}`); }
function checkManifest(m) {
  check(m && m.schema_version === 1, `schema_version ${m && m.schema_version}`);
  const f = m.files;
  check(f && m.sources && time(m.generated_at) && ['summary', 'dim_crossing', 'fct_wait_readings', 'fct_wait_readings_recent'].every((k) => f[k]), 'manifest');
  // file names become table names and paths in the SQL console: relative paths and plain identifiers only
  for (const k in f) check(typeof f[k].url === 'string' && /^\w[\w.-]*(\/\w[\w.-]*)*$/.test(f[k].url) && (!f[k].url.endsWith('.parquet') || /^[a-z_][a-z0-9_]*$/.test(k)), `file ${k}`);
}
function checkSummary(s) {
  check(s && [s.crossings, s.latest, s.by_hour].every(Array.isArray), 'summary');
  check(s.crossings.every((c) => c && typeof c.crossing_id === 'string' && typeof c.display_name === 'string'), 'crossings');
  check(s.latest.every((r) => series(r) && opt(r.delay_min, num) && opt(r.is_open, (v) => typeof v === 'boolean')
    && opt(r.source_updated_at, time) && opt(r.observed_at, time)), 'latest readings');
  check(s.by_hour.every((r) => series(r) && Number.isInteger(r.local_hour) && r.local_hour >= 0 && r.local_hour < 24 && num(r.n) && num(r.days)
    && opt(r.valid_days, num) && opt(r.median_delay_min, num) && opt(r.p90_delay_min, num)), 'hourly rows');
}

async function load(base, live) {
  // status.json is health metadata: without it the readings still show
  const status = live ? get(base + 'status.json', 'no-cache').catch(() => null) : null;
  const manifest = await get(base + 'manifest.json', 'no-cache');
  checkManifest(manifest);
  const summary = await get(base + manifest.files.summary.url);  // named per run: the HTTP cache may keep it
  checkSummary(summary);
  return { manifest, summary, status: await status, live, base };
}

function reading(r) {
  if (!r) return '<td class="none"><b>No data</b><i class="meter"></i><small>not in the feed</small></td>';
  const at = r.source_updated_at && toDate(r.source_updated_at), seen = toDate(r.observed_at || D.manifest.generated_at);
  const t = at && at < seen ? at : seen;  // a stamp ahead of its fetch (CBP stamps the coming hour) counts from the fetch
  const old = D.live && (Date.now() - t) / 6e4 > STALE;
  const [cls, v] = r.is_open === false ? ['closed', 'Closed'] : r.delay_min == null
    ? ['none', /pending/i.test(r.status_raw) ? 'Update pending' : /n\/a|not app/i.test(r.status_raw) ? 'Not applicable' : 'No estimate']
    : ['num', r.delay_min ? `${r.delay_min} min` : 'No delay'];
  // a meter per reading on one shared scale (0 to 60 min or more), so lanes compare at a glance
  return `<td class="${cls}${old ? ' old' : ''}"><b>${v}</b><i class="meter"${r.delay_min ? ` style="--w:${Math.min(100, r.delay_min / 0.6).toFixed(0)}%"` : ''}></i><small>${at ? 'updated' : 'checked'} <time datetime="${t.toISOString()}" title="${esc(clock(t))}">` +
    `${D.live ? `${span((Date.now() - t) / 6e4)} ago` : clock(t)}</time>${old ? ' · stale' : ''}</small></td>`;
}

// the newest poll among the readings on screen: what "as of" means, live or saved
const seenAt = () => D.summary.latest.map((r) => r.observed_at).filter(Boolean).sort().at(-1) || D.manifest.generated_at;

function renderNow() {
  const { summary: s, live } = D, seen = seenAt();
  const latest = new Map(s.latest.map((r) => [`${r.crossing_id}|${r.direction}|${r.lane}`, r]));
  $('now-note').textContent = !s.latest.length ? 'No readings have been published yet.'
    : live ? `As of the last check at ${clock(toDate(seen))} (${span(age(seen))} ago). Each estimate says how long ago its agency updated it.`
    : `A saved snapshot: what the agencies were publishing at ${clock(toDate(seen)).replace(/\.$/, '')}. Not current.`;  // en-CA times end in "p.m."
  renderMap(latest);
  const t = $('now-table');
  [...t.tBodies].forEach((b) => b.remove());
  t.insertAdjacentHTML('beforeend', s.crossings.map((c) => `<tbody><tr><th colspan="3" scope="rowgroup">${esc(c.display_name)}</th></tr>` +
    DIRS.map(([d, label, who]) => `<tr><th scope="row">${label} <small>${who}</small></th>${LANES.map((l) => reading(latest.get(`${c.crossing_id}|${d}|${l}`))).join('')}</tr>`).join('') +
    '</tbody>').join(''));
}

// The three crossings as they sit on the Detroit River, west to east; Detroit is the north bank.
const RIVER = ['gordie_howe_bridge', 'ambassador_bridge', 'detroit_windsor_tunnel'];
function renderMap(latest) {
  const word = (r) => !r ? 'no data' : r.is_open === false ? 'closed' : r.delay_min == null ? 'no estimate' : r.delay_min ? `${r.delay_min} min` : 'no delay';
  const way = (c, d, label) => `<div class="way"><small>${label}</small>${LANES.map((l) => {
    const r = latest.get(`${c.crossing_id}|${d}|${l}`);
    return `<span><b${r && r.delay_min == null ? ' class="off"' : ''}>${word(r)}</b> ${l}</span>`;
  }).join('')}</div>`;
  const pos = (c) => (RIVER.indexOf(c.crossing_id) + 1) || 9;
  $('map').innerHTML = '<p>Detroit, Michigan</p><div class="river">' + [...D.summary.crossings].sort((a, b) => pos(a) - pos(b)).map((c) =>
    `<div class="x">${way(c, 'to_us', '↑ to the US')}<i></i>${way(c, 'to_canada', '↓ to Canada')}<strong>${esc(c.display_name)}</strong></div>`).join('') + '</div><p>Windsor, Ontario</p>';
}

const gates = () => ({ window_days: 14, min_days_point: 3, min_days_solid: 7, min_days_band: 14, ...D.summary.chart });
const daysOf = (r) => r ? r.valid_days ?? r.days : 0;

// Every series at once: a row of 24 squares per lane, brighter for a longer median. A square is left
// empty where the hour has too few days. Choosing a row charts that series below.
function renderHeat() {
  const s = D.summary, g = gates(), by = new Map(s.by_hour.map((r) => [`${r.crossing_id}|${r.direction}|${r.lane}|${r.local_hour}`, r]));
  const ok = (r) => r && r.median_delay_min != null && daysOf(r) >= g.min_days_point;
  const max = Math.max(10, ...s.by_hour.filter(ok).map((r) => r.median_delay_min));
  const cur = ['crossing', 'direction', 'lane'].map((k) => $('pick-' + k).value).join('|');
  $('heat').innerHTML = s.crossings.map((c) => `<h3>${esc(c.display_name)}</h3>` + DIRS.map(([d, label]) => LANES.map((l) => {
    const k = `${c.crossing_id}|${d}|${l}`;
    return `<button type="button" data-k="${esc(k)}" aria-pressed="${k === cur}" aria-label="Chart ${esc(c.display_name)}, ${label}, ${l}"><span>${label} · ${l}</span><span class="cells">` +
      Array.from({ length: 24 }, (_, h) => {
        const r = by.get(`${k}|${h}`);
        return ok(r) ? `<i style="--p:${(22 + 78 * r.median_delay_min / max).toFixed(0)}%" title="${h}:00: median ${r.median_delay_min} min over ${daysOf(r)} days"></i>`
          : `<i class="no" title="${h}:00: too few days yet"></i>`;
      }).join('') + '</span></button>';
  }).join('')).join('')).join('') +
    `<p class="scale"><span>0:00</span><span>6:00</span><span>12:00</span><span>18:00</span><span>23:00</span></p>
    <p class="note"><i style="--p:22%"></i> no delay <i style="--p:61%"></i> ${Math.round(max / 2)} min <i style="--p:100%"></i> ${Math.round(max)} min <i class="no"></i> under ${g.min_days_point} days of estimates</p>`;
}

function renderChart() {
  const s = D.summary, g = gates();
  const [cx, dir, lane] = ['crossing', 'direction', 'lane'].map((k) => $('pick-' + k).value);
  const by = new Map(s.by_hour.filter((r) => r.crossing_id === cx && r.direction === dir && r.lane === lane && r.median_delay_min != null).map((r) => [r.local_hour, r]));
  const days = (h) => daysOf(by.get(h));
  const hours = [...Array(24).keys()], at = (min) => hours.filter((h) => days(h) >= min);
  // drawn at the box's real width, so the labels keep their size on a phone
  const W = Math.max(220, $('chart').clientWidth), H = 240, L = 30, R = 12, T = 12, B = 26;
  const max = Math.max(10, ...at(g.min_days_point).map((h) => by.get(h)[days(h) >= g.min_days_band ? 'p90_delay_min' : 'median_delay_min']));
  const x = (h) => +(L + h / 23 * (W - L - R)).toFixed(1), y = (v) => +(H - B - v / max * (H - T - B)).toFixed(1);
  const pt = (h, k) => `${x(h)},${y(by.get(h)[k])}`;
  // Only neighbouring hours are joined: a line or a band never crosses an hour that has too little data.
  const runs = (hs) => hs.reduce((a, h) => (a.length && a.at(-1).at(-1) === h - 1 ? a.at(-1).push(h) : a.push([h]), a), []);
  const solid = at(g.min_days_solid), few = at(g.min_days_point).filter((h) => !solid.includes(h)), none = hours.filter((h) => days(h) < g.min_days_point);
  const band = runs(at(g.min_days_band)).map((r) => r.length > 1
    ? `<path class="band" d="M${r.map((h) => pt(h, 'p90_delay_min')).join('L')}L${r.reverse().map((h) => pt(h, 'median_delay_min')).join('L')}Z"/>`
    : `<path class="band one" d="M${pt(r[0], 'p90_delay_min')}L${pt(r[0], 'median_delay_min')}"/>`).join('');
  const line = runs(solid).filter((r) => r.length > 1).map((r) => `<polyline class="median" points="${r.map((h) => pt(h, 'median_delay_min')).join(' ')}"/>`).join('');
  const dot = (cls) => (h) => `<circle class="${cls}" cx="${x(h)}" cy="${y(by.get(h).median_delay_min)}" r="4"><title>${h}:00: median ${by.get(h).median_delay_min} min over ${days(h)} days</title></circle>`;
  const ticks = [0, 6, 12, 18, 23].map((h) => `<text x="${x(h)}" y="${H - 8}" text-anchor="${h === 23 ? 'end' : 'middle'}">${h}:00</text>`).join('') +
    [0, max / 2, max].map((v) => `<text x="${L - 6}" y="${y(v) + 4}" text-anchor="end">${Math.round(v)}</text>`).join('');
  const any = solid.length + few.length;
  $('chart').innerHTML = `<svg class="chart" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" role="img" aria-label="Median wait in minutes by local hour over the last ${g.window_days} days: ` +
    `${solid.length} hours with ${g.min_days_solid} or more days of estimates, ${few.length} with ${g.min_days_point} to ${g.min_days_solid - 1}, ${none.length} with too few to show. The data table below has every hour.">
    <line class="axis" x1="${L}" y1="${H - B}" x2="${W - R}" y2="${H - B}"/>${ticks}${band}${line}
    ${none.map((h) => `<path class="gap" d="M${x(h) - 3},${H - B - 5}h6"/>`).join('')}${few.map(dot('dot few')).join('')}${solid.map(dot('dot')).join('')}
    ${any ? '' : `<text x="${W / 2}" y="${H / 2}" text-anchor="middle">not enough days of estimates yet</text>`}</svg>`;
  $('chart-key').textContent = `● ${g.min_days_solid} or more days with an estimate in that hour (${solid.length} of 24 hours) · ○ ${g.min_days_point} to ${g.min_days_solid - 1} days (${few.length}) · – fewer, not drawn (${none.length})`;
  $('chart-days').textContent = `The median of the estimates published in each local hour over the last ${g.window_days} days` +
    `${g.days_with_readings != null ? `, ${g.days_with_readings} of which have readings` : ''}. The shaded band reaches the 90th percentile where an hour has ${g.min_days_band} days. ` +
    'These are display rules for how much history a point rests on, not confidence levels.';
  $('hour-table').caption.textContent = `Median and 90th percentile wait by local hour over the last ${g.window_days} days.`;
  $('hour-table').tBodies[0].innerHTML = hours.map((h) => {
    const r = by.get(h), d = days(h);
    return `<tr><th scope="row">${h}:00</th>${d < g.min_days_point ? '<td colspan="2" class="none">too few days yet</td>'
      : `<td class="num">${r.median_delay_min}</td><td class="num">${d >= g.min_days_band ? Math.round(r.p90_delay_min) : '—'}</td>`}<td class="num">${r ? r.n : 0}</td><td class="num">${d}</td></tr>`;
  }).join('');
  for (const b of $('heat').querySelectorAll('button')) b.setAttribute('aria-pressed', b.dataset.k === `${cx}|${dir}|${lane}`);
}

function renderHead() {
  // "live" needs both a recent export and a recent poll behind it: the older of the two is the one shown
  const { manifest: m, status: st, live } = D, last = Math.max(age(m.generated_at), age(seenAt())), ran = `${span(last)} ago`;
  const [k, text] = !live ? ['snapshot', `readings of ${clock(toDate(seenAt()), { dateStyle: 'medium' })}, not current`]
    : st?.result === 'failure' ? ['failing', `the last run failed ${span(age(st.time))} ago; data from ${ran}`]
    : last > STALE ? ['delayed', `last run ${ran}`] : ['live', `last run ${ran}`];
  $('live-badge').innerHTML = `${badge(k)} ${esc(text)} · ${(+m.files.fct_wait_readings.rows || 0).toLocaleString('en-CA')} readings`;
}

// The last 7 days of runs as a strip: one tick per run that stored readings. The scheduler asks for two
// an hour; the empty stretches are the runs GitHub dropped, and the longest one is marked.
function runs(c) {
  const at = (c?.recent_runs || []).map(toDate).filter((d) => !isNaN(d));
  if (at.length < 2) return '';
  const end = D.live ? Date.now() : +at.at(-1), start = end - 6048e5, x = (t) => ((t - start) / 6048e3).toFixed(2);
  const seen = at.filter((t) => t >= start), edges = [...seen, end];
  let gap = 0, from = 0;
  edges.forEach((t, i) => { if (i && t - edges[i - 1] > gap) { gap = t - edges[i - 1]; from = +edges[i - 1]; } });
  let days = '';
  for (let t = end; t > start; t -= 864e5) days += `<span style="left:${x(t - 432e5)}%">${clock(t - 432e5, { weekday: 'short' })}</span>`;
  return `<div class="runs" role="img" aria-label="${seen.length} runs in the last 7 days; the longest gap was ${span(gap / 6e4)}">` +
    `<b style="left:${x(from)}%;width:${(gap / 6048e3).toFixed(2)}%"></b>${seen.map((t) => `<i style="left:${x(t)}%"></i>`).join('')}${days}</div>` +
    `<p class="note">Runs that stored readings over the last 7 days (${seen.length}). The schedule asks for two an hour; GitHub runs it best-effort. Shaded: the longest gap, ${span(gap / 6e4)}.</p>`;
}

function renderPipeline(tick) {
  const { manifest: m, status: st, live } = D, t = m.tests || {}, c = m.cadence, q = m.quality;
  const link = (url, text) => url ? `<a href="${esc(url)}">${esc(text)}</a>` : esc(text);
  $('pipeline').innerHTML = (!live ? `<p>A saved snapshot: readings up to ${esc(clock(toDate(seenAt())))}, exported ${esc(clock(toDate(m.generated_at), { dateStyle: 'medium' }))}` +
      `${m.data_sha ? ` from the data branch at <code>${esc(String(m.data_sha).slice(0, 7))}</code>` : ''}.<br>What follows describes that export, not the pipeline now.`
    : `<p>Last successful run: ${link(m.run_url, clock(toDate(m.generated_at)))} (${span(age(m.generated_at))} ago) ${age(m.generated_at) > STALE ? badge('delayed') : ''}<br>` +
    (!st ? 'Last attempt: status not available.'
      : st.result !== 'failure' ? `Last attempt: ${st.run_id === m.run_id ? 'the same run' : esc(clock(toDate(st.time)))}, succeeded.`
      : `Last attempt: ${link(st.run_url, clock(toDate(st.time)))} failed${st.failed_step ? ` at <code>${esc(st.failed_step)}</code>` : ''} ${badge('failing')}` +
        `${st.collected ? '<br>Its readings were stored and will be in the next successful run.' : ''}`)) +
    `</p><p>Tests in that run: ${t.pass ?? 0} passed, ${t.warn ?? 0} warned, ${t.error ?? 0} failed.</p>${runs(c)}`;
  if (tick) return;  // only the ages above change with time; the details keep their place (and any focus in them)
  const src = ['cbsa', 'cbp'].map((k) => {
    const s = m.sources[k] || {}, r = s.last_run;
    return `<li>${k === 'cbsa' ? 'CBSA (to Canada)' : 'U.S. CBP (to the US)'}: ` + (r
      ? `${r.crossings} crossings at the last check; updates a median of ${span(r.median_age_minutes ?? 0)} old, the oldest ${span(r.oldest_age_minutes ?? 0)}, ${r.older_than_2h} over 2 h` +
        `${r.stamped_ahead ? `; ${r.stamped_ahead} stamped later than the fetch` : ''}${r.rows - r.timed_rows ? `; ${r.rows - r.timed_rows} lanes with no update time (closed or pending)` : ''}`
      : `newest update ${s.newest_source_updated_at ? esc(clock(toDate(s.newest_source_updated_at))) : 'unknown'}`) + ` ${badge(s.freshness)}</li>`;
  }).join('');
  const hist = m.sources.cbsa_history?.metadata_modified;
  $('pipeline-more').innerHTML = `<ul>${src}${hist ? `<li>CBSA monthly service-standard history: last updated by CBSA ${esc(hist.slice(0, 10))}</li>` : ''}` +
    `${c?.runs_7d ? `<li>Schedule: twice an hour when GitHub runs it. Last 7 days: ${c.runs_7d} runs, typically ${span(c.median_gap_minutes_7d)} apart, at most ${span(c.max_gap_minutes_7d)}.</li>` : ''}` +
    `${q ? `<li>${q.excluded.reduce((a, e) => a + e.rows, 0)} stored rows left out of the tables for quality${q.excluded.map((e) => ` (${esc(e.source)} ${esc(e.reason)}: ${e.rows})`).join('')}; ` +
      `${q.fetch_timed_readings} readings timed by their fetch; ${q.revised_readings} revised by the agency.</li>` : ''}</ul>` +
    ((t.details || []).length ? `<p>Warnings in that run:</p><ul>${t.details.map((w) => `<li>${w.rows} × ${esc(w.text || w.name)} <code>${esc(w.name)}</code></li>`).join('')}</ul>`
      : (t.warnings || []).length ? `<ul>${t.warnings.map((w) => `<li><code>${esc(w)}</code></li>`).join('')}</ul>` : '');
}

// The pipeline as four steps, each with a number from the last run.
function renderHow() {
  const { files: f, cost: c = {}, tests: t = {}, cadence: k, sources: src } = D.manifest, n = (v) => (+v || 0).toLocaleString('en-CA');
  const total = (t.pass ?? 0) + (t.warn ?? 0) + (t.error ?? 0), parquet = Object.values(f).filter((v) => v.url.endsWith('.parquet')).length;
  $('how').innerHTML = [
    ['Fetch', `${n(f.dim_crossing.rows)} crossings`, `A scheduled GitHub Actions job downloads both agencies' feeds and checks them before anything is stored${k?.median_gap_minutes_7d ? `. GitHub runs it best-effort: lately about every ${span(k.median_gap_minutes_7d)}` : ''}.`],
    ['Model', `${n(f.fct_wait_readings.rows)} readings`, 'dbt models on DuckDB match the crossings across the two feeds and resolve each local time to its zone. <a href="https://brettadams0.github.io/border-waits-pipeline/docs/">Lineage and column docs</a>'],
    ['Test', `${n(t.pass)} of ${n(total)} passed`, `${n(t.warn)} warned and are listed under Pipeline health. A failed test stops the deploy.`],
    ['Publish', `${parquet} Parquet files`, c.run_seconds != null && c.snapshot_bytes_added != null
      ? `Content-hashed, with a manifest, on GitHub Pages. A run takes ${Math.round(c.run_seconds)} s on a free runner and adds ${(c.snapshot_bytes_added / 1024).toFixed(1)} KB.`
      : 'Content-hashed, with a manifest, on GitHub Pages.'],
  ].map(([h, v, p]) => `<div><h3>${h}</h3><b>${v}</b><p>${p}</p></div>`).join('');
}

function pickers() {
  const s = D.summary, g = s.chart || {}, keep = $('pick-crossing').value;
  $('pick-crossing').innerHTML = s.crossings.map((c) => `<option value="${esc(c.crossing_id)}">${esc(c.display_name)}</option>`).join('');
  if (keep) return void ($('pick-crossing').value = keep);
  // Open on the series with the most well-supported hours, so the first chart shows what data there is.
  const best = {};
  for (const r of s.by_hour) if ((r.valid_days ?? r.days) >= (g.min_days_solid ?? 7)) { const k = `${r.crossing_id}|${r.direction}|${r.lane}`; best[k] = (best[k] || 0) + 1; }
  const top = Object.keys(best).sort((a, b) => best[b] - best[a])[0];
  if (top) [$('pick-crossing').value, $('pick-direction').value, $('pick-lane').value] = top.split('|');
}

function state(note, retry, saved) {
  $('data-note').textContent = note;
  $('retry').hidden = !retry;
  $('use-saved').hidden = !saved;
  for (const id of ['now', 'hours']) $(id).setAttribute('aria-busy', 'false');
}

async function start(want) {
  for (const id of ['now', 'hours']) $(id).setAttribute('aria-busy', 'true');
  $('retry').hidden = $('use-saved').hidden = true;
  if (D) $('data-note').textContent = 'Loading…';
  let next = null, why = '';
  if (want !== 'saved') try { next = await load(LIVE, true); } catch (e) { why = e.message; }
  if (!next) try { next = { ...await load(SAVED, false), why }; } catch (e) {
    if (!D) { $('live-badge').textContent = 'No data loaded.'; $('now-note').textContent = 'The readings could not be loaded.'; }
    return state(`Couldn't load the readings (live data: ${why || 'not tried'}; saved snapshot: ${e.message}).${D ? ' Still showing what was loaded before.' : ''}`, true);
  }
  const before = D;
  D = next;
  const { summary: s, manifest: m, live } = D;
  try {
    pickers(); renderHead(); renderNow(); renderHeat(); renderChart(); renderPipeline(); renderHow();
    $('collecting').textContent = live && s.latest.length && time(m.collecting_since) ? `Collecting since ${clock(toDate(m.collecting_since), { dateStyle: 'medium' })}.` : '';
  } catch (e) {  // the contract check should make this unreachable; if it isn't, say so rather than leave half a page
    D = before;
    return state(`Couldn't show the ${live ? 'live data' : 'saved snapshot'} (${e.message}).`, true, live);
  }
  $('sql-load').disabled = false;
  if (sql) sql.start(D).catch(() => {});  // the console follows the data that is on screen
  // kept to a line: the badge above already says which snapshot is showing, and a tall notice would push the page down
  state(live ? (s.latest.length ? '' : 'The pipeline has published no readings yet.')
    : want === 'saved' ? 'Showing the saved snapshot.' : `Live data couldn't be loaded (${why}).`,
  !live, live && !s.latest.length);
}

$('retry').addEventListener('click', () => start());
$('use-saved').addEventListener('click', () => start('saved'));
for (const k of ['crossing', 'direction', 'lane']) $('pick-' + k).addEventListener('change', () => D && renderChart());
$('heat').addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  [$('pick-crossing').value, $('pick-direction').value, $('pick-lane').value] = b.dataset.k.split('|');
  renderChart();
});
new ResizeObserver(() => D && renderChart()).observe($('chart'));
// Ages are recomputed from what is already loaded; nothing is downloaded again.
setInterval(() => { if (D) { renderHead(); renderNow(); renderPipeline(true); } }, 30000);
$('sql-load').addEventListener('click', async () => {
  const b = $('sql-load');
  b.disabled = true;
  $('sql-status').textContent = 'Loading DuckDB…';  // also clears the message of an earlier failed try
  try {
    sql = await import('./sql-console.js');
    await sql.start(D, (text) => { b.textContent = text; });
  } catch (e) {
    $('sql-status').textContent = `Couldn't start the SQL console: ${e.message}`;
    b.textContent = 'Try loading DuckDB again';
    b.disabled = false;
  }
});
start();
