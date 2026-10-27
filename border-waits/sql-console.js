// The SQL console. Loaded with import() only after the button is clicked.
// DuckDB-Wasm is pinned to 1.32.0, whose engine is v1.4.3, the same DuckDB the pipeline runs in CI.
const DUCKDB = 'https://cdn.jsdelivr.net/npm/@duckdb/duckdb-wasm@1.32.0/+esm';
const TOTAL_MB = 7.7; // measured transfer on click
const MAX_ROWS = 200;
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

const PRESETS = [
  ['How long are the waits at the Windsor–Detroit crossings, by hour and direction?',
   `select c.display_name, h.direction, h.lane, h.local_hour, h.median_delay_min, h.p90_delay_min, h.n, h.days
from agg_wait_by_hour h join dim_crossing c using (crossing_id)
where c.featured and h.n >= 7
order by c.display_name, h.direction, h.lane, h.local_hour`],
  ['Average Canada-bound traveller wait by hour at the Ambassador Bridge',
   `select hour(timezone('America/Toronto', source_updated_at)) as local_hour,
       round(avg(delay_min), 1) as avg_delay_min, count(delay_min) as readings
from fct_wait_readings
where crossing_id = 'ambassador_bridge' and direction = 'to_canada' and lane = 'travellers'
group by all order by local_hour`],
  ['Crossings with the most closed-lane readings this week',
   `select c.display_name, count(*) filter (where not is_open) as closed_readings, count(*) as readings
from fct_wait_readings r join dim_crossing c using (crossing_id)
where source_updated_at >= now() - interval 7 day
group by all order by closed_readings desc limit 20`],
  ['Monthly service standard at the Windsor crossings since 2019',
   `select port_of_entry, month_start, pct_met
from fct_service_standard_monthly
where crossing_id in ('ambassador_bridge', 'detroit_windsor_tunnel')
order by port_of_entry, month_start`],
  ['Which source updates late most often?',
   `select source, count(*) as readings,
       round(avg(date_diff('minute', source_updated_at, fetched_at)), 1) as avg_lag_min
from fct_wait_readings group by all`],
];

function table(cols, rows) {
  return `<div class="scroll" tabindex="0" role="region" aria-label="Query results"><table>
    <caption>${rows.length} row${rows.length === 1 ? '' : 's'}${rows.length === MAX_ROWS ? ` (first ${MAX_ROWS})` : ''}</caption>
    <thead><tr>${cols.map((c) => `<th scope="col">${esc(c)}</th>`).join('')}</tr></thead>
    <tbody>${rows.map((r) => `<tr>${cols.map((c) => `<td>${esc(r[c] === null ? 'null' : r[c])}</td>`).join('')}</tr>`).join('')}</tbody>
  </table></div>`;
}

function plain(v) { return typeof v === 'bigint' ? Number(v) : v; }

export async function start(manifest, base) {
  const status = $('sql-status'), button = $('sql-load');
  const duckdb = await import(DUCKDB);
  const bundle = await duckdb.selectBundle(duckdb.getJsDelivrBundles());
  const workerUrl = URL.createObjectURL(new Blob([`importScripts("${bundle.mainWorker}");`], { type: 'text/javascript' }));
  const db = new duckdb.AsyncDuckDB(new duckdb.VoidLogger(), new Worker(workerUrl));
  await db.instantiate(bundle.mainModule, bundle.pthreadWorker, (p) => {
    // bytesLoaded counts the uncompressed module; show it as a share of the compressed transfer
    if (p.bytesTotal > 0) button.textContent = `${(TOTAL_MB * Math.min(1, p.bytesLoaded / p.bytesTotal)).toFixed(1)} of ${TOTAL_MB} MB`;
  });
  URL.revokeObjectURL(workerUrl);
  const conn = await db.connect();
  const query = async (sql) => {
    const res = await conn.query(sql);
    const cols = res.schema.fields.map((f) => f.name);
    const rows = res.toArray().slice(0, MAX_ROWS).map((r) => Object.fromEntries(cols.map((c) => [c, plain(r[c])])));
    return { cols, rows };
  };

  const url = (name) => new URL(manifest.files[name].url, base).href;
  try {
    for (const name of Object.keys(manifest.files)) {
      if (!manifest.files[name].url.endsWith('.parquet')) continue;
      await conn.query(`create view ${name} as select * from read_parquet('${url(name)}')`);
    }
    await query(`select count(*) from dim_crossing`);
  } catch (e) {
    status.textContent = /extension/i.test(e.message)
      ? `DuckDB couldn't download its Parquet extension from extensions.duckdb.org (blocked?): ${e.message}`
      : `Couldn't open the data files: ${e.message}`;
    return;
  }
  const version = (await query('select version() as v')).rows[0].v;
  button.hidden = true;

  const sqlFiles = Object.keys(manifest.files).filter((k) => k.startsWith('sql/'));
  const panel = $('sql-console');
  panel.innerHTML = `
    <div class="console-row">
      <label>Question <select id="sql-preset">${PRESETS.map(([q], i) => `<option value="${i}">${esc(q)}</option>`).join('')}</select></label>
      <label>Show model SQL <select id="sql-model"><option value="">Choose a model</option>${sqlFiles.map((k) => `<option value="${esc(k)}">${esc(k.slice(4))}</option>`).join('')}</select></label>
    </div>
    <label for="sql-editor">SQL</label>
    <textarea id="sql-editor" spellcheck="false"></textarea>
    <div class="console-row"><button type="button" id="sql-run">Run</button><button type="button" id="sql-check">Rebuild check</button></div>
    <p id="sql-check-result" role="status"></p>
    <div id="sql-result"></div>`;
  panel.hidden = false;
  const editor = $('sql-editor');
  editor.value = PRESETS[0][1];
  $('sql-preset').addEventListener('change', (e) => { editor.value = PRESETS[e.target.value][1]; });
  $('sql-model').addEventListener('change', async (e) => {
    if (!e.target.value) return;
    editor.value = await (await fetch(url(e.target.value))).text();
  });
  const run = async () => {
    status.textContent = 'Running…';
    try {
      const { cols, rows } = await query(editor.value);
      $('sql-result').innerHTML = table(cols, rows);
      status.textContent = `DuckDB ${version} in your browser, the same DuckDB version the pipeline runs in CI.`;
    } catch (e) {
      status.textContent = `Error: ${e.message}`;
    }
  };
  $('sql-run').addEventListener('click', run);

  // Rebuild check: run agg_wait_by_hour's compiled SQL over the published recent file, as the export did.
  $('sql-check').addEventListener('click', async () => {
    const out = $('sql-check-result');
    const rc = manifest.rebuild_check;
    try {
      const sql = await (await fetch(new URL(rc.sql, base).href)).text();
      await conn.query(`attach if not exists ':memory:' as bwt`);
      await conn.query(`create or replace view bwt.main.fct_wait_readings_recent as select * from read_parquet('${new URL(rc.input, base).href}')`);
      const got = (await query(rc.check_sql.replace('{sql}', () => sql))).rows[0];
      const exp = rc.expected;
      const same = Number(got.n) === exp.n && Number(got.sum) === exp.sum && got.md5 === exp.md5;
      out.textContent = same
        ? `Matches the pipeline's build: ${exp.n} rows, md5 ${exp.md5}.`
        : `Differs from the pipeline's build: got n ${got.n}, sum ${got.sum}, md5 ${got.md5}; expected n ${exp.n}, sum ${exp.sum}, md5 ${exp.md5}.`;
    } catch (e) {
      out.textContent = `Rebuild check failed: ${e.message}`;
    }
  });
  status.textContent = `DuckDB ${version} loaded.`;
  await run();
}
