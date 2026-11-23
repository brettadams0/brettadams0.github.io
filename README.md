# brettadams.ca

[![Site](https://github.com/brettadams0/brettadams0.github.io/actions/workflows/site.yml/badge.svg)](https://github.com/brettadams0/brettadams0.github.io/actions/workflows/site.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Source for my personal site, **[brettadams.ca](https://brettadams.ca/)**. (brettadams0.github.io redirects there.)

An [Astro](https://astro.build) static site, built, tested and deployed to GitHub Pages by
[`site.yml`](.github/workflows/site.yml). The homepage ships no framework JavaScript: one small inline
script plays the project card clips. The live demos are prebuilt static apps published alongside it.

[Reproduce a Signal Lab revenue finding](https://brettadams.ca/projects/signal-lab/investigate/?alert=c646f48c31360b392784.weekday-1.median.net.2011-09-20) · [Try the MCP checker](https://brettadams.ca/mcp-surface/) · [Read the MCP case study](https://brettadams.ca/mcp/)

## Layout

| Path | |
|---|---|
| `src/pages/` | The homepage, the `/mcp/` and Signal Lab case studies, the 404 page and `sitemap.xml` |
| `src/data/site.ts` | Projects, experience and skills as data (edit content here) |
| `src/styles/global.css` | Design tokens (OKLCH colours, type scale, radii, motion) and base styles |
| `src/layouts/` | Page shell (SEO head, header, footer) and the case-study layout |
| `public/signal-lab/` | Published Signal Lab application and public snapshots |
| `public/neuronet-arena/` | Live demo, synced from the neuronet-arena repo (see below) |
| `public/border-waits/` | Live demo of the border-waits pipeline, synced from its repo's `web/` (see below) |
| `public/mcp-surface/` | Live playground for the mcp-surface checks (see below) |
| `public/mazefinder/` | Live demo of MazeFinder's genetic algorithm (see below) |
| `public/assets/` | Fonts (IBM Plex, SIL OFL 1.1), images, card clips, résumé PDF, demo shell CSS |
| `resume/resume.tex` | LaTeX source for the résumé (not published) |
| `tests/site.spec.ts` | Playwright: every page loads clean, metadata, nav, keyboard, 404, links, reduced motion |
| `scripts/render-brand.mjs` | Renders the social card and favicons from HTML |

## Running locally

Needs Node 22.23.4 or newer.

```sh
npm ci
npm run dev       # http://localhost:4321
npm run build && npm run preview
npm test          # Playwright against the local build
BASE_URL=https://brettadams.ca npx playwright test   # the same suite against the live site
```

## Résumé

`resume/resume.tex` compiles with XeLaTeX. Copy the output over `public/assets/resume/Resume_BrettAdams.pdf`.

## Analytics

Google Analytics 4 (measurement ID in `src/layouts/Base.astro`) loads only on brettadams.ca, after the
page is idle, and not at all for visitors who send Do Not Track.
[`public/assets/js/analytics-events.js`](public/assets/js/analytics-events.js) adds `section_view`,
`section_dwell`, `project_click`, `resume_download`, `contact_click`, `scroll_depth`, `nav_click` and
`job_more_open`. Their parameters only appear in GA reports after they're registered under
Admin → Custom definitions.

## Other directories

`public/neuronet-arena/` is the live demo of [neuronet-arena](https://github.com/brettadams0/neuronet-arena),
served at `/neuronet-arena/`. It is a verbatim copy of the runtime files in that repository's `web/`
directory — plain ES modules with no build step, so they are published as static files. Edit it
there, where the JavaScript engine is tested bit-for-bit against the Python one, and re-sync from a
sibling checkout:

```sh
rsync -a --delete --exclude node_modules --exclude test \
  --exclude package.json --exclude package-lock.json --exclude tsconfig.json \
  ../neuronet-arena/web/ public/neuronet-arena/
```

There are no local differences: the copy is exactly upstream's `web/` minus the excluded files.

`public/border-waits/` is the page for [border-waits-pipeline](https://github.com/brettadams0/border-waits-pipeline),
a pipeline over the CBSA and U.S. CBP border wait estimates. It is a verbatim copy of that
repository's `web/`; edit it there. Merge the pipeline change first and let one run publish its export,
refresh the `fallback/` snapshot there from the recorded data (that repository's README has the
commands), then re-sync from a sibling checkout. Look at the dry run first: `--delete` removes anything
here that upstream doesn't have.

```sh
rsync -an --delete -i --exclude node_modules --exclude test --exclude package.json --exclude package-lock.json --exclude tsconfig.json --exclude Makefile ../border-waits-pipeline/web/ public/border-waits/   # dry run
rsync -a --delete --exclude node_modules --exclude test --exclude package.json --exclude package-lock.json --exclude tsconfig.json --exclude Makefile ../border-waits-pipeline/web/ public/border-waits/
find public/border-waits \( -name 'package*.json' -o -name node_modules -o -name tsconfig.json -o -name Makefile -o -name test \) -print   # must print nothing
.github/scripts/check-border-waits-sync.sh ../border-waits-pipeline   # the copy matches, with the same exclusions
```

CI runs that last check against the pipeline repository's `main` whenever a change touches `public/border-waits/`.
To go back, revert the sync commit: the copy has no state of its own.

The live data comes from the project's own Pages at `/border-waits-pipeline/data/v1/`. `fallback/` is a
saved copy of one real export, limited to the three featured crossings (about 130 KB of Parquet and
JSON): the page shows it, labelled as a snapshot, if the live files can't be loaded, and its SQL
console runs on it. Nothing on the homepage fetches any of this: the card is a still screenshot,
`public/assets/img/border-waits-800.webp` and `-1600.webp` (the top 1600×1000 of the page; retake them and the
pipeline repository's `web/og.png` when the page's first screen changes).

CI copies `fallback/` to `/border-waits-pipeline/data/v1/` in a copy of the build first, and
`check-border-waits.mjs` fails unless the page then shows that data rather than its snapshot state.

`public/mcp-surface/` is a playground for [mcp-surface](https://github.com/brettadams0/mcp-surface): edit a
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

`public/mazefinder/` is the live demo of
[MazeFinder-GeneticAlgorithm](https://github.com/brettadams0/MazeFinder-GeneticAlgorithm): its C++
core compiled to WebAssembly. `index.html`, `styles.css`, `boot.js`, `app.js`, `engine-worker.js`, `evolution-history.js` and `og.png` are this repo's own
files. `engine-worker.js` embeds the fixed `open.txt` and `obstacles.txt` mazes and runs the real
WASM engine off the main thread. `app.js` renders transferred snapshots; it does not reimplement
the genetic algorithm. Only two files are copied from the MazeFinder repo's
`web/` build output, each by name, after `make wasm` and `make determinism` pass there:

```sh
cp ../MazeFinder-GeneticAlgorithm/web/mazefinder.js public/mazefinder/wasm/mazefinder.js
cp ../MazeFinder-GeneticAlgorithm/web/mazefinder.wasm public/mazefinder/wasm/mazefinder.wasm
```

Edit the algorithm there, not here. The MazeFinder browser regression check runs against the built site in CI:

```sh
node .github/scripts/check-mazefinder.mjs dist
# Or serve this checkout directly for demo-only checks:
node .github/scripts/check-mazefinder.mjs public
```

It uses the Playwright installed by `npm ci`. Set `CHROMIUM_PATH` to use a local
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
