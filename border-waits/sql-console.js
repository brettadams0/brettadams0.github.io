// The SQL console. Loaded with import() only after the button is clicked.
// DuckDB-Wasm is pinned to 1.32.0, whose engine is v1.4.3, the same DuckDB the pipeline runs in CI.
const DUCKDB = 'https://cdn.jsdelivr.net/npm/@duckdb/duckdb-wasm@1.32.0/+esm';
const TOTAL_MB = 7.7;   // measured transfer on click
const MAX_ROWS = 200;   // rows fetched and shown; the rest of a longer result is never pulled from DuckDB
const STALL = 45000;    // ms without download progress before the load is given up
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

// One per <option> of #sql-preset in index.html, in the same order. g: the chart's gates from the summary.
const PRESETS = [
  (g) => `-- How long are the waits at the Windsor–Detroit crossings, by hour and direction?
-- Hours with an estimate on at least ${g.min_days_solid} of the last ${g.window_days} days: the chart's solid points.
select c.display_name, h.direction, h.lane, h.local_hour, h.median_delay_min, h.p90_delay_min, h.n, h.valid_days
from agg_wait_by_hour h join dim_crossing c using (crossing_id)
where c.featured and h.valid_days >= ${g.min_days_solid}
order by c.display_name, h.direction, h.lane, h.local_hour`,
  () => `-- What did each lane say at the last check? One whole row per lane from the newest poll:
-- a closed or pending lane shows as that, with no number carried over from an earlier reading.
select c.display_name, l.direction, l.lane, l.status_raw, l.delay_min, l.is_open, l.source_updated_at, l.observed_at
from latest_readings l join dim_crossing c using (crossing_id)
where c.featured
order by c.display_name, l.direction, l.lane`,
  () => `-- Average Canada-bound traveller wait by hour at the Ambassador Bridge, over everything stored
select hour(timezone('America/Toronto', reading_at)) as local_hour,
       round(avg(delay_min), 1) as avg_delay_min, count(delay_min) as estimates
from fct_wait_readings
where crossing_id = 'ambassador_bridge' and direction = 'to_canada' and lane = 'travellers'
group by all order by local_hour`,
  () => `-- Crossings with the most closed-lane readings in the newest 7 days of data
select c.display_name, count(*) filter (where not is_open) as closed_readings, count(*) as readings
from fct_wait_readings r join dim_crossing c using (crossing_id)
where reading_at >= (select max(reading_at) from fct_wait_readings) - interval 7 day
group by all order by closed_readings desc limit 20`,
  () => `-- CBSA's monthly service standard at the Windsor crossings since 2019.
-- pct_met is the percentage CBSA publishes for that port and month.
select port_of_entry, month_start, pct_met
from fct_service_standard_monthly
where crossing_id in ('ambassador_bridge', 'detroit_windsor_tunnel')
order by port_of_entry, month_start`,
  () => `-- How long after an agency stamps an update is it first fetched?
-- Negative minutes: the stamp is later than the fetch (CBP stamps the coming hour).
select source, count(*) as updates,
       round(avg(date_diff('minute', reading_at, fetched_at)), 1) as avg_minutes_to_first_fetch,
       count(*) filter (where reading_at > fetched_at) as stamped_ahead
from fct_wait_readings where time_basis = 'source' group by all`,
];
const GATES = { window_days: 14, min_days_point: 3, min_days_solid: 7, min_days_band: 14 };

let lib, db, worker, conn, version;
let open = null;      // the data the views point at: { manifest, base, live, gates }
let session = 0;      // bumped whenever the worker is replaced, so a finished query from an old one is ignored
let busy = false, stopping = false, wired = false;
let lastSet = '', stash = null, modelCall = null;

const say = (text, err) => { const s = $('sql-status'); s.textContent = text; s.classList.toggle('err', !!err); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const where = () => open.live ? '' : ' This is the saved snapshot (three crossings), not live data.';

function setBusy(on) {
  busy = on;
  $('sql-run').disabled = $('sql-check').disabled = $('sql-reset').disabled = on;
  $('sql-stop').disabled = !on;
  $('sql-result').setAttribute('aria-busy', on);
}

function shutdown() {
  session++;
  try { worker && worker.terminate(); } catch { /* already gone */ }
  worker = db = conn = null;
}

// Loads the module and starts one worker. Any failure terminates that worker, so a retry never leaks one.
async function boot(progress) {
  // a failed import() can be remembered by the browser for the page's lifetime: retry under a new URL
  lib ||= await import(DUCKDB + (boot.tries++ ? `?retry=${boot.tries}` : ''));
  const bundle = await lib.selectBundle(lib.getJsDelivrBundles());
  const url = URL.createObjectURL(new Blob([`importScripts("${bundle.mainWorker}");`], { type: 'text/javascript' }));
  worker = new Worker(url);
  db = new lib.AsyncDuckDB(new lib.VoidLogger(), worker);
  let last = Date.now(), watch;
  try {
    await new Promise((ok, no) => {
      worker.addEventListener('error', () => no(Error('the DuckDB worker could not be loaded from cdn.jsdelivr.net')), { once: true });
      watch = setInterval(() => Date.now() - last > STALL && no(Error(`the download stalled for ${STALL / 1000} s`)), 1000);
      db.instantiate(bundle.mainModule, bundle.pthreadWorker, (p) => {
        last = Date.now();
        // bytesLoaded counts the uncompressed module; show it as a share of the compressed transfer
        if (p.bytesTotal > 0 && progress) progress(`${(TOTAL_MB * Math.min(1, p.bytesLoaded / p.bytesTotal)).toFixed(1)} of ${TOTAL_MB} MB`);
      }).then(ok, no);
    });
    conn = await db.connect();
  } catch (e) {
    shutdown();
    throw e;
  } finally {
    clearInterval(watch);
    URL.revokeObjectURL(url);
  }
}
boot.tries = 0;

// The compiled model SQL names its inputs "bwt"."main"."<table>", as dbt built them. The views live in that
// same catalog and it is made the default, so a model's SQL and the short names in the questions both
// resolve, whether or not the rebuild check has run. Times print in UTC (cell()); the models and the
// questions name their zones, so only a visitor's own zone-less hour() or ::date follows the browser's zone.
async function openData(d) {
  const { manifest, base } = d;
  await conn.query(`attach if not exists ':memory:' as bwt`);
  for (const [name, f] of Object.entries(manifest.files)) {
    if (f.url.endsWith('.parquet')) await conn.query(`create or replace view bwt.main.${name} as select * from read_parquet('${new URL(f.url, base).href}')`);
  }
  await conn.query('use bwt');
  version = (await conn.query('select version() as v, (select count(*) from dim_crossing) as n')).toArray()[0].v;
  open = { manifest, base, live: d.live, gates: { ...GATES, ...d.summary.chart } };
}

function cell(v, type) {
  if (v === null || v === undefined) return '<td class="none">null</td>';
  if (/^Timestamp/.test(type)) v = new Date(Number(v)).toISOString().replace('T', ' ').replace('.000Z', 'Z');
  else if (/^Date/.test(type)) v = new Date(Number(v)).toISOString().slice(0, 10);
  // a double prints without its binary noise (8.000000000000007 is 8); a bigint prints in full, never through Number
  const s = String(typeof v === 'number' && !Number.isInteger(v) ? parseFloat(v.toPrecision(12)) : v);
  return `<td${typeof v === 'number' || typeof v === 'bigint' ? ' class="num"' : ''}>${esc(s.length > 300 ? `${s.slice(0, 300)}…` : s)}</td>`;
}

function show(fields, rows, more, sql) {
  const first = sql.split('\n').find((l) => l.trim() && !l.trim().startsWith('--')) || '';
  $('sql-result').innerHTML = `<div class="scroll" tabindex="0" role="region" aria-label="Query results"><table>
    <caption>${more ? `The first ${rows.length} rows; the query returned more, and the rest were not fetched` : `${rows.length} row${rows.length === 1 ? '' : 's'}`}
      · ran ${new Date().toLocaleTimeString()} · <code>${esc(first.trim().slice(0, 90))}${first.trim().length > 90 ? '…' : ''}</code></caption>
    <thead><tr>${fields.map((f) => `<th scope="col">${esc(f.name)}</th>`).join('')}</tr></thead>
    <tbody>${rows.map((r) => `<tr>${r.map((v, i) => cell(v, String(fields[i].type))).join('')}</tr>`).join('')}</tbody>
  </table></div>`;
}

// One query at a time. The result is streamed in batches and reading stops at MAX_ROWS.
async function run() {
  const sql = $('sql-editor').value;
  if (busy) return;
  if (!sql.trim()) return say('Nothing to run: the editor is empty.');
  const mine = session;
  stopping = false;
  setBusy(true);
  say('Running…');
  try {
    const reader = await conn.send(sql);
    const rows = [];
    let fields = null, more = false;
    for await (const batch of reader) {
      fields ||= batch.schema.fields;
      for (const row of batch) {
        if (rows.length === MAX_ROWS) { more = true; break; }
        rows.push(row.toArray());
      }
      if (more) break;
    }
    if (more) await conn.cancelSent();
    if (mine !== session) return;
    show(fields || reader.schema?.fields || [], rows, more, sql);
    say(`DuckDB ${version} in your browser, the same DuckDB version the pipeline runs in CI.${where()}`);
  } catch (e) {
    if (mine !== session) return;
    $('sql-result').innerHTML = '';  // an error is never shown above an earlier query's rows
    say(stopping ? 'Stopped. The earlier result was cleared.' : `Error: ${e.message}`, !stopping);
  } finally {
    if (mine === session) setBusy(false);
  }
}

// Stop asks DuckDB to cancel. If the worker is too deep in one operator to answer, it is replaced.
async function stop() {
  if (!busy) return;
  stopping = true;
  say('Stopping…');
  const mine = session;
  conn.cancelSent().catch(() => {});
  for (let i = 0; i < 20 && busy && mine === session; i++) await sleep(100);
  if (busy && mine === session) await reset('The query would not stop, so the session was restarted. Tables are restored.');
}

async function reset(note) {
  const d = open;
  shutdown();
  $('sql-result').innerHTML = '';
  $('sql-check-result').textContent = '';
  say('Restarting DuckDB…');
  for (const id of ['sql-run', 'sql-check', 'sql-reset', 'sql-stop']) $(id).disabled = true;
  try {
    await boot();
    await openData({ ...d, summary: { chart: d.gates } });
    setBusy(false);
    setEditor(PRESETS[$('sql-preset').value](open.gates), true);
    say(note || 'Session reset: the tables are restored and the editor is back on the selected question.');
  } catch (e) {
    $('sql-console').hidden = true;
    $('sql-load').hidden = false;
    $('sql-load').disabled = false;
    $('sql-load').textContent = 'Try loading DuckDB again';
    say(`Couldn't restart DuckDB: ${e.message}`, true);
  }
}

// Replacing the editor's text keeps what the visitor typed: one click brings it back.
function setEditor(text, quiet) {
  const ed = $('sql-editor');
  if (!quiet && ed.value.trim() && ed.value !== lastSet) { stash = ed.value; $('sql-undo').hidden = false; }
  ed.value = lastSet = text;
}

async function showModel(sel) {
  const key = sel.value;
  if (modelCall) modelCall.abort();
  if (!key) return;
  const call = modelCall = new AbortController();
  try {
    const r = await fetch(new URL(open.manifest.files[key].url, open.base), { signal: call.signal });
    if (!r.ok) throw Error(`HTTP ${r.status}`);
    const text = await r.text();
    if (/^\s*<(!doctype|html)/i.test(text)) throw Error('the server answered with a web page, not SQL');
    if (call !== modelCall) return;  // a later choice was made while this one was on its way
    setEditor(text);
    say(`Showing ${key.slice(4)} as dbt compiled it. Run executes it over the published files.`);
  } catch (e) {
    if (call !== modelCall || e.name === 'AbortError') return;
    sel.value = '';
    say(`Couldn't load that model's SQL (${e.message}). The editor was left as it was.`, true);
  }
}

// Rebuild check: run agg_wait_by_hour's compiled SQL over the published recent file, as the export did.
async function check() {
  const out = $('sql-check-result'), rc = open.manifest.rebuild_check;
  if (busy) return;
  setBusy(true);
  $('sql-stop').disabled = true;
  out.textContent = 'Rebuilding…';
  try {
    const r = await fetch(new URL(rc.sql, open.base));
    if (!r.ok) throw Error(`HTTP ${r.status} for the model's SQL`);
    const sql = await r.text();
    // The view is put back first, and the check runs in UTC as the export's did, so it depends neither on
    // what was run before it nor on the visitor's zone. The session's zone is then put back: DuckDB-Wasm
    // starts in the browser's zone, and a query must mean the same before and after this button.
    // (Setting the zone needs DuckDB's 2 MB ICU extension, which the model's own timezone() calls load
    // here anyway; setting it at start-up would add that to every first load.)
    await conn.query(`create or replace view bwt.main.fct_wait_readings_recent as select * from read_parquet('${new URL(rc.input, open.base).href}')`);
    await conn.query(`set TimeZone = 'UTC'`);
    const got = (await conn.query(rc.check_sql.replace('{sql}', () => sql))).toArray()[0], exp = rc.expected;
    const same = Number(got.n) === exp.n && Number(got.sum) === exp.sum && got.md5 === exp.md5;
    out.textContent = same
      ? `Matches the pipeline's build: ${exp.n} rows, md5 ${exp.md5}.`
      : `Differs from the pipeline's build: got n ${got.n}, sum ${got.sum}, md5 ${got.md5}; expected n ${exp.n}, sum ${exp.sum}, md5 ${exp.md5}.`;
  } catch (e) {
    out.textContent = `Rebuild check failed: ${e.message}`;
  } finally {
    await conn.query('reset TimeZone').catch(() => {});
    setBusy(false);
  }
}

function wire() {
  wired = true;
  $('sql-run').addEventListener('click', run);
  $('sql-stop').addEventListener('click', stop);
  $('sql-check').addEventListener('click', check);
  $('sql-reset').addEventListener('click', () => reset());
  $('sql-editor').addEventListener('keydown', (e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); run(); } });
  $('sql-preset').addEventListener('change', (e) => {
    if (!conn) return;  // before DuckDB is loaded the choice just waits for the button
    $('sql-model').value = '';
    setEditor(PRESETS[e.target.value](open.gates));
    run();
  });
  $('sql-model').addEventListener('change', (e) => showModel(e.target));
  $('sql-undo').addEventListener('click', () => {
    const ed = $('sql-editor'), mine = stash;
    stash = ed.value;
    ed.value = lastSet = mine;
    $('sql-model').value = '';
    ed.focus();
  });
}

// d: what the page is showing ({ manifest, summary, live, base }). Called on the first click, and again
// whenever the page's data changes (a retry that reached live data), so the console never queries
// files other than the ones on screen.
export async function start(d, progress) {
  const base = new URL(d.base, location.href).href;
  if (open && open.base === base && open.manifest.run_id === d.manifest.run_id) return;
  const first = !conn;
  if (first) await boot(progress);
  try {
    await openData({ ...d, base });
  } catch (e) {
    open = null;
    shutdown();  // a half-opened session is not kept: the next click starts clean
    throw Error(/extension/i.test(e.message)
      ? `DuckDB couldn't download its Parquet extension from extensions.duckdb.org (blocked?): ${e.message}`
      : `the data files couldn't be opened: ${e.message}`);
  }
  if (!wired) wire();
  $('sql-model').innerHTML = '<option value="">Choose a model</option>' + Object.keys(d.manifest.files).filter((k) => k.startsWith('sql/'))
    .map((k) => `<option value="${esc(k)}">${esc(k.slice(4))}</option>`).join('');
  $('sql-load').hidden = true;
  $('sql-console').hidden = false;
  setBusy(false);
  setEditor(PRESETS[$('sql-preset').value](open.gates), true);
  say(`DuckDB ${version} loaded.${where()}`);
  await run();
}
