# brettadams0.github.io

[![CI](https://github.com/brettadams0/brettadams0.github.io/actions/workflows/ci.yml/badge.svg)](https://github.com/brettadams0/brettadams0.github.io/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Source for my personal site, **[brettadams0.github.io](https://brettadams0.github.io/)**.

A Jekyll site published by GitHub Pages: a one-page portfolio with live project demos, plus a case
study of the MCP work at `/mcp/`. The demos are plain ES modules with no build step. The homepage
only links to them: nothing runs until a visitor opens one.

## Layout

| Path | |
|---|---|
| `index.html` | The homepage |
| `mcp/index.html` | Case study of the MCP servers and `mcp-surface`, at `/mcp/` |
| `_layouts/default.html` | Page shell: head, header and footer |
| `assets/css/site.css` | All homepage styles (dark only, same tokens as the demos) |
| `assets/fonts/` | IBM Plex, self-hosted latin files (SIL OFL 1.1, see OFL.txt) |
| `assets/css/demo-shell.css` | Shell for demo pages: back link, résumé, how it works |
| `neuronet-arena/` | Live demo, synced from the neuronet-arena repo (see below) |
| `border-waits/` | Live demo of the border-waits pipeline, synced from its repo's `web/` (see below) |
| `mcp-surface/` | Live playground for the mcp-surface checks (see below) |
| `mazefinder/` | Live demo of MazeFinder's genetic algorithm, built from the MazeFinder repo's `web/` (see below) |
| `assets/resume/Resume_BrettAdams.pdf` | Résumé, built from `resume/resume.tex` |
| `resume/resume.tex` | LaTeX source for the résumé (not published to the site) |
| `.github/scripts/check-links.mjs` | CI check that every internal link and asset resolves |
| `.github/scripts/measure.mjs` | CI page-weight budget check (`budget-ci.json`), measured in a real browser |
| `.github/scripts/check-border-waits.mjs` | CI guard: the border-waits page that is measured is the real one, loaded with data |
| `.github/scripts/check-border-waits-sync.sh` | `border-waits/` matches the pipeline repository's `web/` |

## Running locally

```sh
gem install jekyll jekyll-seo-tag jekyll-sitemap
jekyll serve   # http://localhost:4000
```

There is no `Gemfile` on purpose: adding one changes how GitHub Pages resolves dependencies.

## Résumé

`resume/resume.tex` compiles with XeLaTeX. On Overleaf, set Menu → Settings → Compiler to XeLaTeX.
Copy the output over `assets/resume/Resume_BrettAdams.pdf`.

## CI

[`ci.yml`](.github/workflows/ci.yml) builds the site with the same action GitHub Pages uses, then runs
`check-links.mjs` over `_site`.

## Analytics

Google Analytics 4 is loaded from [`_includes/analytics.html`](_includes/analytics.html), with the
measurement ID in `_config.yml`. Nothing is emitted when that key is unset.

[`assets/js/analytics-events.js`](assets/js/analytics-events.js) adds events that page views can't
show on a one-page site: `section_view` and `section_dwell` (which sections were read, and for how
long), `project_click`, `resume_download`, `contact_click` (records only which link: LinkedIn
or GitHub), `scroll_depth`, `nav_click` (an in-page link in the header, the phone section row or the
hero's jump link) and `job_more_open` (an Experience "more" disclosure opened). The script does
nothing for visitors who send Do Not Track. The event parameters (`section_id`, `project_name`,
`method`, `percent`, `seconds`, `target_section`, `nav`, `item`) only appear in GA reports after
they're registered under Admin → Custom definitions.

## Other directories

`neuronet-arena/` is the live demo of [neuronet-arena](https://github.com/brettadams0/neuronet-arena),
served at `/neuronet-arena/`. It is a verbatim copy of the runtime files in that repository's `web/`
directory — plain ES modules with no build step, so Jekyll publishes them as static files. Edit it
there, where the JavaScript engine is tested bit-for-bit against the Python one, and re-sync from a
sibling checkout:

```sh
rsync -a --delete --exclude node_modules --exclude test \
  --exclude package.json --exclude package-lock.json --exclude tsconfig.json \
  ../neuronet-arena/web/ neuronet-arena/
```

There are no local differences: the copy is exactly upstream's `web/` minus the excluded files.

`border-waits/` is the page for [border-waits-pipeline](https://github.com/brettadams0/border-waits-pipeline),
a pipeline over the CBSA and U.S. CBP border wait estimates. It is a verbatim copy of that
repository's `web/`; edit it there. Merge the pipeline change first and let one run publish its export,
refresh the `fallback/` snapshot there from the recorded data (that repository's README has the
commands), then re-sync from a sibling checkout. Look at the dry run first: `--delete` removes anything
here that upstream doesn't have.

```sh
rsync -an --delete -i --exclude node_modules --exclude test --exclude package.json --exclude package-lock.json --exclude tsconfig.json --exclude Makefile ../border-waits-pipeline/web/ border-waits/   # dry run
rsync -a --delete --exclude node_modules --exclude test --exclude package.json --exclude package-lock.json --exclude tsconfig.json --exclude Makefile ../border-waits-pipeline/web/ border-waits/
find border-waits \( -name 'package*.json' -o -name node_modules -o -name tsconfig.json -o -name Makefile -o -name test \) -print   # must print nothing
.github/scripts/check-border-waits-sync.sh ../border-waits-pipeline   # the copy matches, with the same exclusions
```

CI runs that last check against the pipeline repository's `main` whenever a change touches `border-waits/`.
To go back, revert the sync commit: the copy has no state of its own.

The live data comes from the project's own Pages at `/border-waits-pipeline/data/v1/`. `fallback/` is a
saved copy of one real export, limited to the three featured crossings (about 130 KB of Parquet and
JSON): the page shows it, labelled as a snapshot, if the live files can't be loaded, and its SQL
console runs on it. Nothing on the homepage fetches any of this: the card is a still screenshot,
`assets/img/border-waits-800.webp` and `-1600.webp` (the top 1600×1000 of the page; retake them and the
pipeline repository's `web/og.png` when the page's first screen changes).

The budget job measures `/border-waits/` too (`budget-ci.json`). Because this build has no data route,
CI copies `fallback/` to `/border-waits-pipeline/data/v1/` inside `_site` first, and
`check-border-waits.mjs` fails unless the page then shows that data rather than its snapshot state.

`mcp-surface/` is a playground for [mcp-surface](https://github.com/brettadams0/mcp-surface): edit a
`tools/list` response and the published checks run on it in the browser. `checks.js` is generated
from the npm release pinned in `.github/scripts/mcp-bundle/package-lock.json`. The build injects
`TextEncoder` for the checker's UTF-8 byte count in a browser. With Node 22.12 or newer, regenerate
it from the repo root:

```sh
npm ci --prefix .github/scripts/mcp-bundle
npm run build --prefix .github/scripts/mcp-bundle
```

CI rebuilds the bundle, checks the committed file for drift and compares the browser findings
with the pinned Node package. For a release upgrade, update the dependency and lockfile together,
then review the version labels, third-party notices and the playground's rule list.

`styles.css` starts with a copy of the token block at the top of `assets/css/site.css`; change
both together.

`mazefinder/` is the live demo of
[MazeFinder-GeneticAlgorithm](https://github.com/brettadams0/MazeFinder-GeneticAlgorithm): its C++
core compiled to WebAssembly. `index.html`, `styles.css`, `boot.js`, `app.js`, `engine-worker.js`, `evolution-history.js` and `og.png` are this repo's own
files. `engine-worker.js` embeds the fixed `open.txt` and `obstacles.txt` mazes and runs the real
WASM engine off the main thread. `app.js` renders transferred snapshots; it does not reimplement
the genetic algorithm. Only two files are copied from the MazeFinder repo's
`web/` build output, each by name, after `make wasm` and `make determinism` pass there:

```sh
cp ../MazeFinder-GeneticAlgorithm/web/mazefinder.js mazefinder/wasm/mazefinder.js
cp ../MazeFinder-GeneticAlgorithm/web/mazefinder.wasm mazefinder/wasm/mazefinder.wasm
```

Edit the algorithm there, not here. `styles.css` starts with a copy of the token block at the top of
`assets/css/site.css`; change both together.

The MazeFinder browser regression check runs against the built site in CI:

```sh
node .github/scripts/check-mazefinder.mjs _site
# Or serve this checkout directly for demo-only checks:
node .github/scripts/check-mazefinder.mjs .
```

It uses the globally installed Playwright from the budget job. Set `CHROMIUM_PATH` to use a local
Chromium executable. Coverage includes loading failure/retry, rapid playback toggles, deterministic
replay, reduced motion, completion, presets, clipboard fallback and mobile overflow. It also caps
worker/engine files at 190 KiB uncompressed, because page Resource Timing excludes worker downloads.

MazeFinder engine build `f1b80d59` adds adaptive BFS-guided segment mutation to escape route
plateaus. Frontend requests use cache version 4; unchanged WASM requests retain version 3. Increment the
engine version when replacing the binary so returning visitors do not keep a stale build. The page labels a budget stop
separately from an optimal route, and can continue the same population up to 15,000 generations.

The worker captures every strict fitness improvement inside a batch, with owned before/after route
copies and the count of routes reaching the exit. The UI retains at most 256 checkpoints, preserving
the starting population, first exit, largest fitness drop and newest result. Numeric fitness history
is retained for the full generation budget. Selecting a checkpoint pauses evolution; recorded
playback only draws that route and Return to live restores the previous playback preference. A
late worker response updates cached live state without replacing the selected historical route.
Reduced motion uses static comparisons. Dashed/bright segments compare route geometry, not
parent-child mutation lineage. CI compares batch events against single-generation engine results
and checks retention, keyboard selection, replay state, population counts and terminal behavior.
