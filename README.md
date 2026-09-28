# brettadams0.github.io

[![CI](https://github.com/brettadams0/brettadams0.github.io/actions/workflows/ci.yml/badge.svg)](https://github.com/brettadams0/brettadams0.github.io/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

My portfolio, blog and résumé. Live at **[brettadams0.github.io](https://brettadams0.github.io/)**.

A single hand-written landing page plus a Jekyll blog, published by GitHub Pages. There is no
bundler and no framework — the stylesheet is compiled from `scss/` into `dist/css/styles.css`, and
every icon is baked into one SVG sprite (`_includes/icon-sprite.html`) instead of loading a webfont
and a client-side icon script.

## Layout

| Path | |
|---|---|
| `index.html` | The landing page — experience, projects, skills, education, contact |
| `_posts/` | Blog posts (Markdown, served at `/blog/:year/:month/:day/:title/`) |
| `blog/index.html` | Blog index |
| `_layouts/`, `_includes/` | Jekyll layouts and the icon sprite |
| `scss/` | Sass sources; compiled output lives in `dist/css/` |
| `assets/resume/` | Résumé PDF linked from the hero |
| `.github/scripts/check-links.mjs` | Build-time internal link/asset checker |

## Running it locally

The page is Jekyll-templated, so opening `index.html` in a browser directly renders raw Liquid.
Build it first. There is deliberately no `Gemfile` — adding one changes how the live Pages build
resolves dependencies — so install the plugins `_config.yml` declares and run Jekyll directly:

```sh
gem install jekyll jekyll-seo-tag jekyll-sitemap
jekyll serve   # http://localhost:4000
```

The stylesheet is not built by Jekyll. `dist/styles.scss` is the entry point and pulls its partials
from `scss/`, so rebuild it by hand after touching anything in there:

```sh
sass --load-path=scss dist/styles.scss dist/css/styles.css
```

## CI

[`ci.yml`](.github/workflows/ci.yml) builds the site with the same `jekyll-build-pages` action
GitHub Pages itself uses, then runs `check-links.mjs` over `_site`. Pages reports Jekyll build
failures but will happily publish a page pointing at a renamed image or a deleted post — that is the
failure mode the link checker exists to catch. It reads only the built output: no network, no
dependencies.

## Analytics

Google Analytics 4 is loaded from [`_includes/analytics.html`](_includes/analytics.html), which both
`index.html` and `_layouts/default.html` pull into `<head>`. The measurement ID lives in
`_config.yml` as `google_analytics`, and the include emits nothing whatsoever when that key is
absent — so a fork, or a local build with the key commented out, ships no beacon. `bargain-hunt/` is
deliberately left out of all of this: it is `noindex, nofollow`, a private tool rather than part of
the public site.

One historical note, because it is the sort of thing that gets restored by accident: the ID this site
carried from August 2025 until [#35](https://github.com/brettadams0/brettadams0.github.io/pull/35),
`G-EBYB5MK388`, belongs to a property under no Google account of ours — almost certainly the author
of an uploaded template, who was receiving every visitor's behaviour in the meantime. It is not a
fallback. The current ID is a property created for this site.

Page-level reporting is the wrong granularity for this site. The landing page is a single URL
holding six sections, so GA can report that `/` was viewed without saying whether anyone scrolled as
far as Education, or ever opened a project card.
[`assets/js/analytics-events.js`](assets/js/analytics-events.js) closes that gap with six custom
events. `section_view` fires once per section per page load, after that section has spent a
continuous second at least half in view, which is what separates reading from scrolling past.
`section_dwell` accumulates that visible time and flushes it in whole seconds when the page is
hidden or unloaded, dropping anything under two seconds as noise. `project_click` reports which card
a reader cared enough to open, by name and destination; `resume_download` records the résumé,
duplicating enhanced measurement's `file_download` under a name that cannot be renamed out from
under us. `contact_submit` is the one unambiguous win on the site, and is sent **with no parameters
at all** — the form carries a name, an email address and a message body, and none of that may reach
GA. `scroll_depth` marks the quarter points, because GA's own scroll event only fires at 90%, too
coarse to tell a skim from a read.

The script is inert wherever it has nothing to do: it returns immediately if the visitor sends Do
Not Track, guards every send so a blocked `gtag` throws nothing, and simply never arms the section
events on blog pages, which have no `section[id]`. Note that the event parameters — `section_id`,
`project_name`, `percent`, `seconds` — are collected from the moment the script ships, but stay
invisible in GA's reports until they are registered under Admin → Custom definitions, and that
registration is not retroactive.

## Contact

- GitHub: [brettadams0](https://github.com/brettadams0)
- LinkedIn: [Brett Adams](https://www.linkedin.com/in/bretta/)
- Email: [adamsbrett00@gmail.com](mailto:adamsbrett00@gmail.com)
