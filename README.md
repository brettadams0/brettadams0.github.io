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
| `handoff/` | Briefs for work another session will pick up (not published) |

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
long), `project_click`, `resume_download`, `contact_click` (records only which link: email, LinkedIn
or GitHub), `scroll_depth`, `nav_click` (an in-page link in the header, the phone section row or the
hero's jump link) and `job_more_open` (an Experience "more" disclosure opened). The script does
nothing for visitors who send Do Not Track. The event parameters (`section_id`, `project_name`,
`method`, `percent`, `seconds`, `target_section`, `nav`, `item`) only appear in GA reports after
they're registered under Admin → Custom definitions.

## Other directories

`sift/` (an Android app, excluded from the build) is a separate project that lives in this
repository for now.

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
an hourly pipeline over the CBSA and U.S. CBP border wait estimates. It is a verbatim copy of that
repository's `web/`; edit it there. Refresh its `fallback/` snapshot there from the live export, then
re-sync from a sibling checkout:

```sh
rsync -a --delete --exclude node_modules --exclude test --exclude package.json --exclude package-lock.json --exclude tsconfig.json --exclude Makefile ../border-waits-pipeline/web/ border-waits/
find border-waits \( -name 'package*.json' -o -name node_modules -o -name tsconfig.json -o -name Makefile -o -name test \) -print   # must print nothing
```

The live data comes from the project's own Pages at `/border-waits-pipeline/data/v1/`; `fallback/`
is the saved snapshot the page shows if that fetch fails. Nothing on the homepage fetches it: the card
is a still screenshot.

`mcp-surface/` is a playground for [mcp-surface](https://github.com/brettadams0/mcp-surface): edit a
`tools/list` response and the published checks run on it in the browser. `checks.js` is generated
from the npm package with esbuild; `Buffer.byteLength` (the one Node API the checks call) is shimmed
with `TextEncoder`. To rebuild after a new release, in an empty directory:

```sh
npm pack mcp-surface@latest && tar xzf mcp-surface-*.tgz
npm install ajv@8 esbuild
printf 'const e = new TextEncoder();\nexport const Buffer = { byteLength: (s) => e.encode(String(s)).length };\n' > shim.js
printf "export { runChecks, allChecks } from './package/dist/checks/index.js';\n" > entry.js
npx esbuild entry.js --bundle --format=esm --minify --target=es2020 --inject:shim.js --outfile=checks.js
```

Then copy `checks.js` over `mcp-surface/checks.js`, keeping its header comment, update the rule
table in `mcp-surface/app.js` if the package README's table changed, and the version in `index.html`'s How
it works item and chip. `styles.css` starts with a copy of the token block at the top of
`assets/css/site.css`; change both together.

`mazefinder/` is the live demo of
[MazeFinder-GeneticAlgorithm](https://github.com/brettadams0/MazeFinder-GeneticAlgorithm): its C++
core compiled to WebAssembly. `index.html`, `styles.css`, `app.js` and `og.png` are this repo's own
files. `app.js` embeds that repo's `mazes/*.txt`. Only two files are copied from the MazeFinder repo's
`web/` build output, each by name, after `make wasm` and `make determinism` pass there:

```sh
cp ../MazeFinder-GeneticAlgorithm/web/mazefinder.js mazefinder/wasm/mazefinder.js
cp ../MazeFinder-GeneticAlgorithm/web/mazefinder.wasm mazefinder/wasm/mazefinder.wasm
```

Edit the algorithm there, not here. `styles.css` starts with a copy of the token block at the top of
`assets/css/site.css`; change both together.
