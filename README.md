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

## Contact

- GitHub: [brettadams0](https://github.com/brettadams0)
- LinkedIn: [Brett Adams](https://www.linkedin.com/in/bretta/)
- Email: [adamsbrett00@gmail.com](mailto:adamsbrett00@gmail.com)
