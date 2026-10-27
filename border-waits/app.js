// border-waits page: the static view. Everything shown comes from manifest.json,
// status.json or the summary file the manifest names. DuckDB loads only on click.
const DATA = '/border-waits-pipeline/data/v1/';
const FALLBACK = new URL('fallback/', import.meta.url).href;
const STALE_HOURS = 3; // estimated threshold
const LOCAL_TZ = 'America/Toronto';
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

async function json(url) {
  const r = await fetch(url, { cache: 'no-cache' });
  if (!r.ok) throw new Error(`${url}: ${r.status}`);
  return r.json();
}

async function load() {
  try {
    const [manifest, status] = await Promise.all([json(DATA + 'manifest.json'), json(DATA + 'status.json')]);
    if (manifest.schema_version !== 1) throw new Error(`schema_version ${manifest.schema_version}`);
    const summary = await json(DATA + manifest.files.summary.url);
    return { manifest, status, summary, live: true };
  } catch {
    const [manifest, summary] = await Promise.all([json(FALLBACK + 'manifest.json'), json(FALLBACK + 'summary.json')]);
    return { manifest, status: null, summary, live: false };
  }
}

// DuckDB writes '2026-10-27 17:00:00+00'; Date wants '2026-10-27T17:00:00+00:00'.
const toDate = (s) => new Date(String(s).replace(' ', 'T').replace(/([+-]\d\d)$/, '$1:00'));
const localTime = (iso, opts = {}) => iso ? new Intl.DateTimeFormat('en-CA', {
  timeZone: LOCAL_TZ, dateStyle: 'medium', timeStyle: 'short', ...opts }).format(toDate(iso)) : '—';

function cell(row) {
  if (!row) return '<td class="none">—</td>';
  if (row.is_open === false) return '<td class="closed">closed</td>';
  if (row.delay_min === null || row.delay_min === undefined) return '<td class="none">—</td>';
  return `<td class="num">${row.delay_min}</td>`;
}

function renderNow({ summary }) {
  const key = (r) => `${r.crossing_id}|${r.direction}|${r.lane}`;
  const latest = new Map(summary.latest.map((r) => [key(r), r]));
  $('now-table').tBodies[0].innerHTML = summary.crossings.map((c) => {
    const rows = ['to_canada|travellers', 'to_canada|commercial', 'to_us|travellers', 'to_us|commercial']
      .map((dl) => latest.get(`${c.crossing_id}|${dl}`));
    const updated = rows.filter(Boolean).map((r) => r.source_updated_at).sort().at(-1);
    return `<tr><th scope="row">${esc(c.display_name)}</th>${rows.map(cell).join('')}<td>${esc(localTime(updated))}</td></tr>`;
  }).join('');
}

function renderChart({ summary }) {
  const sel = { crossing: $('pick-crossing').value, direction: $('pick-direction').value, lane: $('pick-lane').value };
  const rows = summary.by_hour.filter((r) => r.crossing_id === sel.crossing && r.direction === sel.direction && r.lane === sel.lane);
  const byHour = new Map(rows.map((r) => [r.local_hour, r]));
  const W = 720, H = 240, L = 36, R = 8, T = 10, B = 26;
  const max = Math.max(10, ...rows.filter((r) => r.n >= 7).map((r) => (r.n >= 20 ? r.p90_delay_min : r.median_delay_min)));
  const x = (h) => L + (h / 23) * (W - L - R), y = (v) => H - B - (v / max) * (H - T - B);
  const med = [], band = [];
  for (let h = 0; h < 24; h++) {
    const r = byHour.get(h);
    if (r && r.n >= 7) med.push(`${x(h)},${y(r.median_delay_min)}`);
    if (r && r.n >= 20) band.push([h, r]);
  }
  const bandPath = band.length > 1 ? 'M' + band.map(([h, r]) => `${x(h)},${y(r.p90_delay_min)}`).join('L') +
    'L' + band.slice().reverse().map(([h, r]) => `${x(h)},${y(r.median_delay_min)}`).join('L') + 'Z' : '';
  const ticks = [0, 6, 12, 18, 23].map((h) => `<text x="${x(h)}" y="${H - 8}" text-anchor="middle">${h}:00</text>`).join('');
  const yt = [0, max / 2, max].map((v) => `<text x="${L - 6}" y="${y(v) + 4}" text-anchor="end">${Math.round(v)}</text>`).join('');
  const enough = med.length > 0;
  $('chart').innerHTML = `<svg class="chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="Median wait by local hour${enough ? '' : ': not enough readings yet'}">
    <line class="axis" x1="${L}" y1="${H - B}" x2="${W - R}" y2="${H - B}"/>${ticks}${yt}
    ${bandPath ? `<path class="band" d="${bandPath}"/>` : ''}
    ${med.length > 1 ? `<polyline class="median" points="${med.join(' ')}"/>` : ''}
    ${enough ? '' : `<text x="${W / 2}" y="${H / 2}" text-anchor="middle">not enough readings yet</text>`}</svg>`;
  const days = Math.max(0, ...rows.map((r) => r.days));
  $('chart-days').textContent = `Based on ${days} day${days === 1 ? '' : 's'} of readings. The median shows from 7 readings in an hour, the 90th-percentile band from 20.`;
  $('hour-table').tBodies[0].innerHTML = Array.from({ length: 24 }, (_, h) => {
    const r = byHour.get(h);
    if (!r || r.n < 7) return `<tr><th scope="row">${h}:00</th><td colspan="2" class="none">not enough readings yet</td><td class="num">${r ? r.n : 0}</td><td class="num">${r ? r.days : 0}</td></tr>`;
    return `<tr><th scope="row">${h}:00</th><td class="num">${r.median_delay_min}</td><td class="num">${r.n >= 20 ? r.p90_delay_min : '—'}</td><td class="num">${r.n}</td><td class="num">${r.days}</td></tr>`;
  }).join('');
}

function badge(state) { return state ? `<span class="badge ${esc(state)}">${esc(state)}</span>` : ''; }

function renderPipeline({ manifest, status, live }) {
  const m = manifest, t = m.tests || {};
  const ageH = (Date.now() - toDate(m.generated_at)) / 36e5;
  const link = (url, text) => url ? `<a href="${esc(url)}">${esc(text)}</a>` : esc(text);
  const attempt = status ? (status.result === 'success' && status.run_id === m.run_id
    ? 'the same run'
    : `${link(status.run_url, localTime(status.time))}: ${esc(status.result)}${status.failed_step ? `, failed at <code>${esc(status.failed_step)}</code>` : ''}`)
    : 'not available in the saved snapshot';
  const src = ['cbsa', 'cbp'].map((k) => {
    const s = m.sources[k];
    return `<li>${esc(k === 'cbsa' ? 'CBSA (to Canada)' : 'U.S. CBP (to the US)')}: newest update ${esc(localTime(s.newest_source_updated_at))}, ` +
      `${s.lag_minutes ?? '—'} min behind the fetch ${badge(s.freshness)}</li>`;
  }).join('');
  const hist = m.sources.cbsa_history?.metadata_modified;
  $('pipeline').innerHTML = `
    <p>Last successful run: ${link(m.run_url, localTime(m.generated_at))} ${ageH > STALE_HOURS ? badge('stale') : ''}<br>
    Last attempt: ${attempt}</p>
    <p>Tests in the last run: ${t.pass ?? 0} pass, ${t.warn ?? 0} warn, ${t.error ?? 0} error.</p>
    ${t.warnings?.length ? `<ul>${t.warnings.map((w) => `<li><code>${esc(w)}</code></li>`).join('')}</ul>` : ''}
    <ul>${src}${hist ? `<li>CBSA monthly service-standard history: last updated by CBSA ${esc(hist.slice(0, 10))}</li>` : ''}</ul>`;
}

function renderHow({ manifest: m }) {
  const f = m.files, c = m.cost || {};
  const kb = (b) => (b / 1024).toFixed(1);
  const blocks = [
    ['Fetch', `Every hour a scheduled GitHub Actions job downloads both agencies' feeds and checks them before anything is stored. The latest export holds ${f.fct_wait_readings.rows} readings.`],
    ['Model', `dbt models on DuckDB match ${f.dim_crossing.rows} crossings across the two feeds and resolve each local time to its zone. <a href="https://brettadams0.github.io/border-waits-pipeline/docs/">Lineage and column docs</a>`],
    ['Test', `${(m.tests?.pass ?? 0) + (m.tests?.warn ?? 0) + (m.tests?.error ?? 0)} tests ran on the last build. An error stops the deploy; warnings are listed above.`],
    ['Publish', c.run_seconds != null && c.snapshot_bytes_added != null
      ? `Each run takes ${Math.round(c.run_seconds)} s on a free runner and adds ${kb(c.snapshot_bytes_added)} KB.`
      : 'The run publishes content-hashed Parquet files and a manifest to GitHub Pages.'],
  ];
  $('how').innerHTML = blocks.map(([h, p]) => `<div><h3>${h}</h3><p>${p}</p></div>`).join('');
}

// "Collecting data since" and the live badge come from the export's manifest and
// status.json (the earliest stored reading, and the last run), never typed in.
function renderCollecting({ manifest: m, status, live }) {
  if (m.collecting_since) {
    const since = localTime(m.collecting_since); // en-CA times end in "p.m."; don't add a second period
    $('collecting').textContent = `Collecting data since ${since}${since.endsWith('.') ? '' : '.'} The by-hour charts fill in as readings accumulate.`;
  }
  if (!live) return;
  const last = toDate(status?.time || m.generated_at);
  const min = Math.max(0, Math.round((Date.now() - last) / 6e4));
  const ago = min < 60 ? `${min} min ago` : `${Math.round(min / 60)} h ago`;
  $('live-badge').innerHTML = `<span class="badge ${status?.result === 'failure' ? 'error' : 'pass'}">live</span> last run ${esc(ago)} · ${m.files.fct_wait_readings.rows.toLocaleString('en-CA')} readings`;
}

function setupPickers(data) {
  $('pick-crossing').innerHTML = data.summary.crossings.map((c) => `<option value="${esc(c.crossing_id)}">${esc(c.display_name)}</option>`).join('');
  for (const id of ['pick-crossing', 'pick-direction', 'pick-lane']) $(id).addEventListener('change', () => renderChart(data));
}

const data = await load();
if (!data.live) {
  $('data-note').textContent = `Showing a saved snapshot from ${localTime(data.manifest.generated_at, { timeStyle: undefined })}.`;
  $('sql-load').disabled = true;
  $('sql-status').textContent = 'SQL needs the live data files';
}
setupPickers(data);
renderNow(data);
renderChart(data);
renderPipeline(data);
renderCollecting(data);
renderHow(data);
$('sql-load').addEventListener('click', async () => {
  $('sql-load').disabled = true;
  try {
    const { start } = await import('./sql-console.js');
    await start(data.manifest, new URL(DATA, location.href).href);
  } catch (e) {
    $('sql-status').textContent = `Couldn't load DuckDB: ${e.message}`;
    $('sql-load').disabled = false;
  }
});
