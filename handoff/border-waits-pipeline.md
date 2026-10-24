# Handoff: border-waits, an hourly border wait-time pipeline you can query in the browser

**Status: queued.** This comes out of the redesign research (`handoff/site-redesign.md` §14). Build
it only after Brett says go (Decision 1 below). It gives the site a live demo of the kind of work in
Brett's internships: scheduled pipelines, SQL and CI/CD (Airflow DAGs on Cloud Composer, BigQuery SQL
and GitLab CI/CD at Geotab, `resume/resume.tex:106-108`). Today's demos are ML (neuronet-arena) and
developer tooling (mcp-surface). Delete this file in the PR that adds the page.

Everything below marked **measured** was observed on 2026-10-21 (UTC). Evidence is in the session
scratchpad: `research/new-project/` (`samples/`, `engines/`, `proto/`). It is not committed.

## Ground rules (from Brett, and in `CLAUDE.md`)

- Build the demo from the project's real code and real output, not a mock-up. Every number on the page
  is something the pipeline wrote.
- **Nothing live on the homepage, and each project appears once.** The project is reached from its card
  in `#projects`: a still screenshot, a "Live demo" badge and a "Live demo" link. Nothing on the
  homepage fetches data, loads the engine or changes between visits. The page may fetch data when it
  loads. The SQL engine loads only after a click.
- Copy stays short and factual: one line per project, facts as chips. No blog-style prose.
- Visual style: dark only, IBM Plex Sans/Mono, the shared tokens (`--bg #0b0f14`, `--surface #131a22`,
  `--border #222c38`, `--amber #ffb454`, `--teal #4fd6c4`, `--red #ef6a5f`, `--good #3ccf6e`). Use the
  redesign spec's tokens (§7) and its shared demo shell (§8, `.demo-shell`) if they have landed by the
  time you build.
- Site stack: Jekyll on Pages, no Gemfile, no build step, plain HTML/CSS/ES modules. **No workflow, bot
  commit or build step in the site repo.** The site repo gets a static folder and a card, nothing else.
- Don't touch `neuronet-arena/` (synced) or `mcp-surface/checks.js` (generated).
- Honest framing:
  - The scheduler is GitHub Actions cron, not Airflow. Brett's Airflow work is at Geotab
    (`index.html:31`). Don't imply this project uses Airflow.
  - The values are wait **estimates published by CBSA and CBP**, not measured crossing times.
  - No source says where Brett lives (fact sheet §2). Don't write "my commute" or similar.
  - Don't put this project on the résumé or claim it anywhere until it ships (Decision 9).

## Repos and access

| Repo | What changes |
|---|---|
| `brettadams0/[BRETT: repo name, suggested border-waits-pipeline]` (new, public; licence `[BRETT: licence, suggested MIT]`) | Ingest, storage, dbt models and tests, export, workflows, Pages for data, the page UI in `web/` |
| `brettadams0/brettadams0.github.io` | The `/border-waits/` folder (synced from `web/`), homepage card, screenshot, README |

- **The repo name and the site folder must differ.** The project's Pages lives at
  `brettadams0.github.io/<repo>/`. GitHub's docs don't say what happens when that path matches a folder in
  the user site, so never let them collide. With the suggested names, data is served at
  `/border-waits-pipeline/data/v1/` and the page at `/border-waits/`. Also keep clear of the existing
  `/google-workspace-mcp-policy/` project site (fact sheet §12).
- Brett has to create the repo. In Settings → Pages, Brett sets the source to GitHub Actions. A session
  may not have admin rights for either step.
- The session needs push access to both repos. Open one draft PR in each. Do the project repo first:
  the site copies its `web/` output.

## What exists today

### Data sources (fetched and parsed today)

| Source | URL | Format (**measured**) | Licence |
|---|---|---|---|
| CBSA current waits, Canada-bound | `https://www.cbsa-asfc.gc.ca/bwt-taf/bwt-eng.csv` (also `bwt-fra.csv`) | 3,274 B. UTF-8 with BOM. `;;` delimiter plus a trailing `;;`. Columns: `Customs Office`, `Location`, `Last updated` (`2026-10-20 23:15 EDT`), `Commercial Flow - Canada bound`, `Commercial Flow - U.S. bound` (always `--`), `Travellers Flow - Canada bound`, `Travellers Flow - U.S. bound` (always `--`). 29 rows. Values: `No Delay`, `N minutes`, `Closed`, `Not Applicable`. `Last-Modified` was 4 minutes before the fetch. | Open Government Licence – Canada (open.canada.ca dataset "Current Border Wait Times", `d4a716f5-a2fc-4c3c-88ed-451fe05900e4`) |
| CBSA history, monthly | open.canada.ca dataset `000fe5aa-1d77-42d1-bfe7-458c51dacfef`, resource "Historical Border Wait Times - 2019-04-01 to 2025-10-31". The download 302s to a signed Azure blob; find it each time through `https://open.canada.ca/data/api/action/package_show?id=000fe5aa-1d77-42d1-bfe7-458c51dacfef` | 80,693 B CSV. Columns: `Port of Entry`, `Month`, `Fiscal Year` (`2019-20`), `Percentage Met Service Standard` (`99.58%`). 2,054 rows, 26 ports × 79 months, no gaps or duplicates. Dataset metadata last modified 2025-12-18. | OGL – Canada |
| CBP current waits, US-bound | `https://bwt.cbp.gov/xml/bwt.xml` (the XML feed the CBP help PDF documents). `https://bwt.cbp.gov/api/bwtnew` is the JSON the CBP app uses; it is undocumented. | XML 181,301 B; JSON 134,154 B. 85 ports: 30 `Canadian Border`, 55 Mexican. Per port: `port_number`, `port_name`, `crossing_name`, `date` (`10/20/2026`), `port_status`. Per lane group: `standard_lanes` with `operational_status`, `update_time` (`At 11:00 pm EDT`), `delay_minutes` (a string, empty when closed) and `lanes_open`. Also FAST, NEXUS/SENTRI and ready lanes. `cache-control: no-store`. | Public domain, CBP asks to be cited (cbp.gov Copyright Notice) |
| CBSA JSON (don't use) | `https://www.cbsa-asfc.gc.ca/bwt-taf/bwt-eng.json` | **Not valid JSON**: it ends with `//mdlmn bwt-taf 2026-10-20 23:50:02 -04:00`. `poe-updated` has no date. 10 crossing names differ from the CSV. | not listed on open.canada.ca |

Licence terms the page must meet (quoted from the pages loaded):
- **OGL – Canada:** you must "Acknowledge the source of the Information by including any attribution
  statement specified by the Information Provider(s) and, where possible, provide a link to this
  licence." The default statement is "Contains information licensed under the Open Government Licence –
  Canada." It does not grant "the names, crests, logos, or other official symbols", and you may not
  suggest "official status or that the Information Provider endorses you".
- **CBP:** "information on the U.S. Customs and Border Protection website is in the public domain ... We
  request only that the CBP be cited as the source". No logos on the page.

The Windsor–Detroit crossings across the feeds (**measured**):

| crossing_id | CBSA CSV `Customs Office` | CBSA history `Port of Entry` | CBP `port_number` / `crossing_name` |
|---|---|---|---|
| `ambassador_bridge` | `Ambassador Bridge` | `Ambassador Bridge` | `380001` / `Ambassador Bridge` |
| `detroit_windsor_tunnel` | `Windsor and Detroit Tunnel` | `Windsor and Detroit Tunnel` | `380002` / `Windsor Tunnel` |
| `gordie_howe_bridge` | `Gordie Howe International Bridge` | not present | `380102` / `Gordie Howe International Bridge ` (trailing space) |

Name drift for the other crossings, all in today's samples. Build the crosswalk from them; don't guess:
- 8 live CBSA names have no exact match in the history, and 5 history names have none in the live
  file. Examples: `Edmundston` vs `Edmunston`, `Woodstock Road` vs `Woodstock`,
  `Cornwall Traffic Office` vs `Cornwall`, `Fort Frances Bridge` vs `Fort Frances`, `Douglas` vs
  `Douglas (Peace Arch)`.
- CBSA uses `Blue Water Bridge`; CBP uses `380201` / `Bluewater Bridge` (port `Port Huron`).

Other quirks seen today (**measured**). Each one should become a test:
- DuckDB's CSV sniffer rejects the `;;` delimiter, so parse the CBSA CSV in Python.
- CBSA time-zone suffixes: EDT, PDT, ADT, CDT, CST, MDT.
- CBP reported `At 11:00 pm EST` for Port Huron while Detroit said `EDT` for the same hour. Abbreviations
  can be wrong, so trust a per-crossing IANA zone and use the abbreviation only as a check.
- CBP had `no delay` with `delay_minutes` 5, and `Lanes Closed` with empty fields.
- The CBSA page says "30 busiest land crossings", but the CSV has 29 rows.

### Prototype (this session; reuse the approach, not the files)

- dbt-core 1.12.6 with dbt-duckdb 1.11.0 and Python `duckdb==1.4.3`. They install together (**measured**,
  Python 3.13).
  - A staging model over the CBSA history CSV, a crosswalk seed, a mart, 2 singular and 5 generic tests:
    `dbt build` gave `PASS=10 WARN=0 ERROR=0`.
  - The `relationships` test worked written as
    `relationships: {arguments: {to: ref('port_crosswalk'), field: crossing_id}}`.
  - `dbt source freshness` with `loaded_at_field` and `warn_after`/`error_after` flagged a 12-hour-old
    sample `ERROR STALE`.
- DuckDB-Wasm `@duckdb/duckdb-wasm@1.32.0` in Chromium.
  - It reports engine version **v1.4.3**, which is why the Python side pins `duckdb==1.4.3`.
  - On click it transferred **7,682,098 B** in 7 requests. Before the click it made 0 CDN requests.
  - One of the 7 is the Parquet extension, auto-loaded from
    `https://extensions.duckdb.org/v1.4.3/wasm_eh/parquet.duckdb_extension.wasm` (667,153 B).
  - dbt's compiled SQL (`target/compiled/.../windsor_monthly.sql`, which references
    `"bwt"."main"."stg_cbsa_land_monthly"` and `"bwt"."main"."port_crosswalk"`) ran unchanged after:
    ```sql
    attach ':memory:' as bwt;
    create view bwt.main.stg_cbsa_land_monthly as select * from read_parquet('<url>');
    ```
  - The check gave the same `count(*)`, `sum(pct_met)` and
    `md5(string_agg(... order by ...))` as the Python build: 237 rows,
    `45cbd393329f382a01bc53fbff69e297`.
- npm's `latest` tag for duckdb-wasm points at a dev build (`1.33.1-dev57.0`). **Pin exactly
  `1.32.0`**, the last non-dev release (2025-12-16). Upgrade both pins together, never one alone.

### Hosting facts (measured or quoted from GitHub's docs)

- **GitHub Pages:**
  - Pages on this domain answers byte ranges (`206`) and sends `access-control-allow-origin: *` and
    `cache-control: max-age=600`.
  - Docs: published sites "no larger than 1 GB", "soft bandwidth limit of 100 GB per month",
    deployments "timeout if they take longer than 10 minutes". The 10-builds-per-hour limit "does not
    apply if you build and publish your site with a custom GitHub Actions workflow."
- **Scheduled workflows** (docs):
  - Shortest interval 5 minutes.
  - Delays near the start of the hour; "some queued jobs may be dropped".
  - "In a public repository, scheduled workflows are automatically disabled when no repository activity
    has occurred in 60 days."
- **Cost:** Actions is free for public repos on standard runners (docs).
- **Release assets:** they don't send CORS headers (**measured**), so the browser can't fetch them.
- **jsDelivr:** its `/gh/` mirror caches for up to 12 h at the CDN (**measured** `s-maxage=43200`).
  Use it only for the pinned engine.

### Site patterns to copy

- Demo page head and shell: the redesign spec's shared demo shell (§8, `.demo-shell`): back link
  "← Brett Adams" to `/#projects`, the project name, Résumé, Source. If that PR hasn't landed yet,
  start from `mcp-surface/index.html:1-32`, `:86-88` (title, description, og tags, favicon, Plex
  fonts, `header.top`, `footer.foot`) and switch to the shell when it lands.
- Synced folder rule: `README.md:63-76` (neuronet-arena `rsync`, "edit it there").
- Homepage card: the featured project card in the redesign spec (§8), placed as the second full-row
  card directly after neuronet-arena (§5.5). Before the redesign lands, the neuronet-arena card at
  `index.html:85-96` is the model (`data-project`, screenshot link with `tabindex="-1"
  aria-hidden="true"`, `badge-live`, links).
- Link checker: root-relative `href`/`src` must exist in `_site`, and anything with a scheme is skipped
  (`.github/scripts/check-links.mjs:29-30`). So link to the project's Pages with a full `https://` URL
  in HTML. Fetch it with a root-relative path in JS, which is not checked.

## Plan

### 1. Project repo

- **Layout:**
  - `ingest/`: Python package, stdlib plus `duckdb`.
    - `fetch.py` downloads each source and saves the body and response headers.
    - `cbsa.py` and `cbp.py` parse into the normalized schema below.
    - `write.py` writes one Parquet file per source per run.
  - `fixtures/`: real responses. Start with today's: `cbsa-bwt-eng.csv`, `cbp-bwt.xml`, `cbp-bwtnew.json`,
    the history CSV and the CKAN `package_show` response. Add one each time a quirk turns up. Never
    hand-edit fixtures.
  - `tests/`: pytest for the parsers and contracts.
  - `dbt/`: dbt project with `profiles.yml` pointing at `target/bwt.duckdb`. The database name must stay
    `bwt`, because the browser's rebuild check attaches that name.
  - `export/`: writes the published files.
  - `web/`: the page (see section 2). `web/test/` holds its tests.
  - `.github/workflows/ci.yml` and `pipeline.yml`.
  - `requirements.txt`, pinned: `duckdb==1.4.3`, `dbt-core==1.12.6`, `dbt-duckdb==1.11.0`, `pytest`.
- **Normalized reading** (one row per crossing × direction × lane per run):

  | column | type | notes |
  |---|---|---|
  | `fetched_at` | TIMESTAMPTZ | run time, UTC |
  | `source` | VARCHAR | `cbsa` or `cbp` |
  | `source_crossing_name` | VARCHAR | trimmed, as published |
  | `source_location` | VARCHAR | CBSA `Location`, CBP `port_name` |
  | `cbp_port_number` | VARCHAR | null for CBSA |
  | `direction` | VARCHAR | `to_canada` (CBSA) or `to_us` (CBP) |
  | `lane` | VARCHAR | `travellers` or `commercial`. CBP standard lanes only in v1; NEXUS/FAST/ready lanes later |
  | `status_raw` | VARCHAR | exactly as published |
  | `delay_min` | INTEGER | `No Delay` → 0; `N minutes` → N; closed or not applicable → null |
  | `is_open` | BOOLEAN | false for `Closed` / `Lanes Closed` |
  | `lanes_open` | INTEGER | CBP only |
  | `source_updated_raw` | VARCHAR | CBSA `Last updated`; CBP `date` + `update_time` |
  | `source_tz_abbrev` | VARCHAR | `EDT`, `CST`, ... |

  - Timestamp parsing happens in dbt, not in the parser. That keeps raw values intact and the logic
    testable.
- **CBSA parser:**
  - Decode with `utf-8-sig`, split on `;;`, strip cells, drop the empty last cell.
  - Assert the header row matches the 7 names above exactly. A mismatch is a contract failure.
  - Ignore the always-`--` U.S.-bound columns.
- **CBP parser:**
  - Parse the XML. Keep `border == "Canadian Border"`. Strip names.
  - Take `commercial_vehicle_lanes/standard_lanes` and `passenger_vehicle_lanes/standard_lanes`.
  - Contract-test the root fields `last_updated_date`, `last_updated_time` and `number_of_ports`.
  - If the XML ever fails, the JSON parser is the fallback, behind the same contract tests.
- **Storage:**
  - Each run writes `snapshots/source=<cbsa|cbp>/date=YYYY-MM-DD/<HHMMSS>.parquet` (zstd) to an orphan
    `data` branch in the project repo. One commit per run; bot commits happen here only.
  - Size: today's 118 rows (58 CBSA + 60 CBP) made a 4,039 B file (**measured**), so expect roughly
    35-70 MB a year (**estimated**).
  - Check the branch out with `fetch-depth: 1`.
  - Upload the raw responses and headers as a workflow artifact with `retention-days: 14`.
  - The CBSA history is fetched monthly: once a day, compare CKAN `metadata_modified` with the last
    stored value and only download when it changes. Store it at `history/cbsa_land/<metadata_modified>.csv`.
- **dbt models:**
  - `sources`:
    - `raw.readings` is `read_parquet('<data>/snapshots/**/*.parquet', hive_partitioning = true)`, with
      freshness on `fetched_at`: warn after 2 hours, error after 6 (**estimated** thresholds).
    - `raw.cbsa_land_history` is the latest history CSV.
  - `seeds/dim_crossing.csv` has one row per crossing: `crossing_id`, `display_name`, `province`,
    `us_state`, `iana_zone` (e.g. `America/Toronto`, `America/Detroit`), `cbsa_csv_name`,
    `cbsa_history_name`, `cbp_port_number`, `cbp_crossing_name`, `featured` (true for the three
    Windsor–Detroit rows). Fill it from the fixtures.
  - `stg_readings` casts the raw rows and computes `source_updated_at`:
    - Use the crossing's `iana_zone`.
    - CBP gives only a time with the port `date`. If the result is more than 15 minutes after
      `fetched_at`, subtract one day (midnight rollover).
    - Keep `tz_abbrev_matches_zone` as a flag.
  - `int_readings_matched` joins to `dim_crossing`. Rows with no match go to `unmatched_crossings`, and
    the pipeline keeps running.
  - `fct_wait_readings` dedupes on `(crossing_id, direction, lane, source_updated_at)` and keeps the
    earliest `fetched_at`. The feed is polled hourly but each crossing updates on its own clock.
  - `agg_wait_by_hour`: crossing × direction × lane × local weekday × local hour, with `n`,
    `median(delay_min)` and `quantile_cont(delay_min, 0.9)`. Publish `n`. The page hides cells with
    `n < 4` (**estimated** threshold).
  - `fct_service_standard_monthly`: CBSA history with `month_start` derived from the fiscal year.
    January to March belong to the second year of `2019-20`; this is proven in the prototype.
- **Tests** (generic and singular):
  - `not_null` and `accepted_values` on `source`, `direction`, `lane` and the known `status_raw` values.
    An unknown status is a **warn**, not an error.
  - Delay between 0 and 720. Warn above 240.
  - `relationships` from matched rows to `dim_crossing`.
  - `unmatched_crossings` is empty (**warn**). It lists the name that needs a crosswalk row.
  - Uniqueness of the dedupe key in `fct_wait_readings`.
  - Each run has at least 25 CBSA crossings and 25 CBP Canadian-border ports (29 and 30 today).
  - `source_updated_at` is not in the future relative to `fetched_at` after rollover handling.
  - `tz_abbrev_matches_zone` is true (**warn**; today's Port Huron `EST` would trip it).
  - One row per port and month in the monthly history, and `pct_met` between 0 and 100.
  - History freshness: warn when CKAN `metadata_modified` is more than 120 days old. It is 307 days old
    today, so expect a standing warning. That is honest: CBSA hasn't updated the file.
- **Export:** writes `public/data/v1/`:
  - `manifest.json` holds:
    - `schema_version: 1`, `generated_at`, run id and URL, git SHA, `data` branch SHA, `duckdb_version`,
      `dbt_version`.
    - Per source: URL, licence, `last_fetched_at`, newest `source_updated_at`, freshness status.
    - Test counts: pass, warn, error and skip, plus the warning messages.
    - Per table: file, rows, bytes.
    - The rebuild check: model name, its input files and the expected `{n, sum, md5}`.
  - `summary.json` (target ≤ 30 KB) holds:
    - The latest reading per featured crossing × direction × lane.
    - The last 48 hours of readings for those crossings.
    - `agg_wait_by_hour` for those crossings.
  - Parquet files: `dim_crossing`, `fct_wait_readings` (sorted by `crossing_id`, `source_updated_at`),
    `agg_wait_by_hour`, `fct_service_standard_monthly`, and `stg_cbsa_land_monthly` (the rebuild-check
    input).
  - `sql/<model>.sql`: compiled SQL from `dbt/target/compiled/` for the models the page shows.
  - `index.html`: one plain line saying this is the data for the border-waits page, with a link to
    `https://brettadams0.github.io/border-waits/`.
- **Workflows:**
  - `ci.yml` runs on push and PR:
    - pytest.
    - `dbt build` against fixture-built snapshots, with no network.
    - The export.
    - The `web/` tests.
  - `pipeline.yml` runs on `schedule: - cron: "17 * * * *"` and `workflow_dispatch`, with
    `concurrency: pipeline` and permissions `contents: write`, `pages: write`, `id-token: write`:
    1. Check out `main`, and the `data` branch into `datastore/`.
    2. `python -m ingest` writes the run files and the raw artifact.
    3. Commit and push to `data`.
    4. Run `dbt build`, then `dbt source freshness` (the legacy command, still supported; the dbt docs
       now describe `dbt freshness` for v2).
    5. Export.
    6. `actions/upload-pages-artifact` from `public/`, then `actions/deploy-pages`.
  - Deploy only when `dbt build` has no errors. Warnings deploy and show on the page. A failed run
    leaves the last good data up.
  - Use the current major version of each action; check each action's README.
- **README:**
  - What it does, the sources and their licences (with the attribution line), and the schema.
  - How to run it locally: `pip install -r requirements.txt && python -m ingest --fixtures && cd dbt &&
    dbt build`.
  - How to re-enable the schedule after the 60-day auto-disable.
  - How the site syncs `web/`.
  - Keep it short.

### 2. Site repo: the `/border-waits/` page

- **Files:** `border-waits/` in the site repo, a verbatim `rsync` of the project's `web/` (excluding
  `test/`), like neuronet-arena:
  - `index.html`, `styles.css`, `app.js`, `sql-console.js`.
  - `fallback/summary.json` and `fallback/manifest.json`: a snapshot exported by hand just before each
    sync (`python -m export --snapshot web/fallback`).
  - `og.png`.
- **Head and shell:** the redesign spec's `.demo-shell` (§8), or `mcp-surface/index.html:1-32` until
  that lands.
  - Title: follow the demo-page title rule in the spec (§12), which names Brett; for example
    `border-waits · live demo | Brett Adams`. Today's demo titles don't name Brett (audit issue 20),
    so don't copy them.
  - Description, for example: "Hourly CBSA and U.S. CBP border wait times, modelled and tested with dbt,
    queryable with SQL in your browser."
  - og tags with `og:url` `https://brettadams0.github.io/border-waits/`, favicon
    `/assets/img/favicon.svg`, Plex fonts loaded the way spec §7 says (self-hosted if that Decision
    landed).
  - Header (`.demo-shell`): back link "← Brett Adams" to `/#projects`, the project name, "Résumé"
    (`/assets/resume/Resume_BrettAdams.pdf`, no query string or fragment), "Source", plus "Data" (full
    URL `https://brettadams0.github.io/border-waits-pipeline/data/v1/` with the suggested repo name).
  - Footer: `Built by Brett Adams · Data: CBSA and U.S. CBP · Contains information licensed under the
    Open Government Licence – Canada.` The licence phrase links to
    https://open.canada.ca/en/open-government-licence-canada. Add "MIT-licensed source" only once the
    repo's licence is settled.
- **Data loading:**
  - `const DATA = '/border-waits-pipeline/data/v1/'` (same origin, root-relative in JS only; use
    `/<repo>/data/v1/` if Brett picks another repo name).
  - On load, fetch `manifest.json` and `summary.json`. Nothing else loads until a click.
  - On fetch failure, or if `schema_version` isn't 1, use `fallback/` and say so in one line:
    "Showing a saved snapshot from <date>." That line also covers `jekyll serve`, where the data path 404s.
- **Static view, top to bottom:**
  - Intro: `h1` "border-waits" and one line: "Border wait times published by CBSA and U.S. CBP, fetched
    every hour, modelled and tested with dbt. Query them with SQL below."
  - **Windsor–Detroit now:** a table with rows for the three crossings and columns for to Canada
    (travellers, commercial) and to the US (travellers, commercial), plus "source updated" in local
    time. A closed lane reads "closed"; null is never shown as 0.
  - **Typical wait by hour:**
    - A small SVG drawn with plain JS: median line and p90 band by local hour, with a crossing and
      direction picker.
    - Hidden cells show "not enough readings yet".
    - A `<details>` "Data table" with the same numbers, like neuronet-arena's
      (`neuronet-arena/index.html` "Data table").
  - **Pipeline:**
    - Last run time, with a link to the Actions run.
    - Tests: pass, warn and error counts, listing the warning messages.
    - Each source with freshness. A `stale` badge appears when `generated_at` is more than 3 hours old.
    - The history source with "last updated by CBSA <date>".
  - **How it works:** four short blocks (Fetch, Model, Test, Publish), each 1-2 plain sentences with
    real numbers from the manifest.
- **Query with SQL** (`sql-console.js`, loaded with `import()` only on click):
  - Button text "Load DuckDB to run SQL (about 7.7 MB)". The number is the measured transfer; update it
    if the pin changes.
  - Load with `import('https://cdn.jsdelivr.net/npm/@duckdb/duckdb-wasm@1.32.0/+esm')`, then
    `selectBundle(getJsDelivrBundles())` and a Blob-URL worker (`importScripts(bundle.mainWorker)`),
    then `instantiate`. This is the working prototype code path.
  - Create views named like the marts over `read_parquet('<DATA>file.parquet')`.
  - A textarea editor with 4-6 preset queries, each a plain question. Examples: "Average Canada-bound
    traveller wait by hour at the Ambassador Bridge"; "Crossings with the most closed-lane readings this
    week"; "Monthly service standard at the Windsor crossings since 2019".
  - A "Show model SQL" picker that loads `sql/<model>.sql` into the editor.
  - Results table: the first 200 rows, inside a horizontally scrolling container. The page itself never
    scrolls sideways.
  - **Rebuild check:**
    - `attach ':memory:' as bwt`, create `bwt.main.<input>` views over the input Parquet, run the
      compiled model SQL into a temp table.
    - Compute `{n, sum, md5}` the same way the export did and compare with `manifest.rebuild_check`.
    - Show "Matches the pipeline's build" or the difference.
  - Errors appear in a status line; the static view stays.
  - The Parquet extension downloads from `extensions.duckdb.org` on first use (667 KB, measured). If it
    is blocked, say so plainly.
- **Mobile, accessibility, motion:**
  - Works at 390 px with no horizontal page scroll.
  - Tables use `<caption>` and `<th scope>`. Status lines are `role="status"`. The editor has a
    `<label>`. All controls are reachable by keyboard.
  - No animation is needed. If any is added, respect `prefers-reduced-motion`.
  - axe: zero serious or critical violations at 390 and 1440 (the spec's pass bar, §10). Code and
    result containers that scroll sideways get `tabindex="0"` and a label.
  - Analytics: follow the spec's Decision on GA for demo pages (§13, §18). Unless Brett has approved
    GA there, add none, matching the other demos today (fact sheet §11).
- **Homepage card:**
  - Add an `article.project.project-featured` with `data-project="border-waits"`, using the featured
    card markup in the redesign spec (§8).
  - Placement: the second full-row featured card, directly after neuronet-arena (spec §5.5). The
    half-width cards stay an even four, so no card moves to `ul.also`. Brett can still choose another
    layout (Decision 6).
  - Screenshot `assets/img/border-waits.webp` at 1600×1000 plus the 800 px variant, with the `srcset`,
    `sizes` and `aspect-ratio` from spec §7, `loading="lazy"`, `width`/`height` set. Refresh it by hand
    when the page changes; nothing generates it.
  - Kind line (`.project-kind`): "Live demo · runs in your browser". The `badge-live` sits outside the
    `h3` (spec §8), so the heading's accessible name is just "border-waits".
  - `h3` "border-waits".
  - One line: "An hourly pipeline over CBSA and U.S. CBP border wait times, tested with dbt. Query it
    with SQL in your browser."
  - Chips: `dbt + DuckDB`, `Hourly on GitHub Actions`, `SQL in the browser`.
  - Links "Live demo" (`/border-waits/`) then "Source". "Live demo" only ever opens the running page.
  - Ship the card in the same PR as the page, never before. Check at 1440 and 390 px.
- **README:**
  - Add `border-waits/` to the layout table.
  - Add a section like neuronet-arena's: "synced from the project's `web/`, edit it there", the `rsync`
    line, and a note that live data comes from the project's Pages at `/border-waits-pipeline/data/v1/`
    with `fallback/` as the saved snapshot.
- **Résumé (optional):** only if Brett asks. Run any wording past Brett first. Recompile with XeLaTeX,
  check it is still one page, and copy it over `assets/resume/Resume_BrettAdams.pdf`.

## Verify before pushing

- **Project repo:**
  - CI green: pytest, `dbt build` on fixtures with 0 errors, the `web/` tests.
  - One `workflow_dispatch` run of `pipeline.yml` succeeds, commits to `data`, and deploys.
  - `curl -sI -r 0-99 https://brettadams0.github.io/<repo>/data/v1/fct_wait_readings.parquet` gives
    `206` with a `content-range`.
  - `manifest.json` loads and shows test counts and freshness.
- **Parity:** the rebuild check matches in Chromium, and the in-browser `select version()` equals the
  pipeline's pinned DuckDB (v1.4.3 for wasm 1.32.0).
- **Site build:**
  `gem install jekyll jekyll-seo-tag jekyll-sitemap && jekyll build && node .github/scripts/check-links.mjs`.
  It must pass.
- **Measure:** `node handoff/tools/measure.mjs --site _site --path / --path /border-waits/ --shots --axe --perf --budget`.
  The homepage still meets its budget, and `/border-waits/` has zero serious or critical axe
  violations. The data path 404s under the local server, so this also exercises the fallback.
- **Browser:** Playwright (Chromium is preinstalled; don't run `playwright install`) at 1440 and 390 px.
  Check:
  - No console errors and no horizontal overflow.
  - **Zero** requests to jsDelivr or `extensions.duckdb.org` before the click.
  - After the click, the presets run and the rebuild check matches.
  - With the data path blocked (route it to 404), the page shows the fallback snapshot line and still
    renders.
  - Nothing border-waits-related loads or runs on the homepage.
- **Analytics:** clicking the homepage card still sends `project_click` (via `data-project`).
- **Copy:** every number on the page comes from `manifest.json` or `summary.json`. The attribution line
  is present.
- **Screenshots:** send them to Brett with the PR links.

## Decisions and placeholders for Brett

1. Build it? Default: no build until Brett says go.
2. `[BRETT: repo name]` (suggested `border-waits-pipeline`; it must differ from the site folder
   `border-waits`). Brett creates the repo and enables Pages from GitHub Actions.
3. May the site page fetch live data from the project's Pages at runtime? Default: yes, with the synced
   fallback snapshot. Nothing in the site repo's build or deploy changes. Alternative: snapshot only,
   re-synced by hand.
4. Page in the site repo (default) or wholly on the project's Pages?
5. Pinned third-party hosts on click (jsDelivr, `extensions.duckdb.org`). Default: accept.
6. Homepage layout. Default: border-waits is the second full-row featured card after neuronet-arena
   (spec §5.5), and no card is demoted. Alternative: a half-width card, with the spec's
   `.project-wide` rule covering an odd count.
7. Scope. Default: all Canada–US crossings in both feeds, with the three Windsor–Detroit crossings
   featured.
8. Optional join to Bank of Canada Valet FX (`FXUSDCAD`). Its terms require attribution and respecting
   request limits. Default: no.
9. A résumé line once it ships. Default: none.
10. `[BRETT: any personal line about why these crossings, if true]`. Default: none.
11. `[BRETT: licence for the new repo, suggested MIT]`. Default: no licence line on the page until it
    is set.
12. GA on the demo page: follows the spec's demo-page Decision. Unless Brett approved GA on demo
    pages, none, like the other demos today.
