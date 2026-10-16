# brettadams0.github.io

[![CI](https://github.com/brettadams0/brettadams0.github.io/actions/workflows/ci.yml/badge.svg)](https://github.com/brettadams0/brettadams0.github.io/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Source for my personal site, **[brettadams0.github.io](https://brettadams0.github.io/)**.

A Jekyll site published by GitHub Pages: one page, one stylesheet, and no JavaScript apart from
analytics.

## Layout

| Path | |
|---|---|
| `index.html` | The homepage |
| `_layouts/default.html` | Page shell: head, header and footer |
| `_layouts/post.html` | Post template (no posts yet) |
| `assets/css/site.css` | All styles. Light and dark themes follow the visitor's system setting |
| `assets/resume/Resume_BrettAdams.pdf` | Résumé, built from `resume/resume.tex` |
| `resume/resume.tex` | LaTeX source for the résumé (not published to the site) |
| `.github/scripts/check-links.mjs` | CI check that every internal link and asset resolves |

## Running locally

```sh
gem install jekyll jekyll-seo-tag jekyll-sitemap
jekyll serve   # http://localhost:4000
```

There is no `Gemfile` on purpose: adding one changes how GitHub Pages resolves dependencies.

## Résumé

`resume/resume.tex` compiles with XeLaTeX. On Overleaf, set Menu → Settings → Compiler to XeLaTeX.
Copy the output over `assets/resume/Resume_BrettAdams.pdf`.

## Writing a post

Add a file named `_posts/YYYY-MM-DD-some-title.md`:

```markdown
---
layout: post
title: "Some title"
---

Text goes here.
```

Once a post exists, a Writing section and nav link appear on the homepage. Posts are served at
`/writing/some-title/`.

## CI

[`ci.yml`](.github/workflows/ci.yml) builds the site with the same action GitHub Pages uses, then runs
`check-links.mjs` over `_site`.

## Analytics

Google Analytics 4 is loaded from [`_includes/analytics.html`](_includes/analytics.html), with the
measurement ID in `_config.yml`. Nothing is emitted when that key is unset.

[`assets/js/analytics-events.js`](assets/js/analytics-events.js) adds events that page views can't
show on a one-page site: `section_view` and `section_dwell` (which sections were read, and for how
long), `project_click`, `resume_download`, `contact_click` (records only which link: email, LinkedIn
or GitHub) and `scroll_depth`. The script does nothing for visitors who send Do Not Track. The event
parameters (`section_id`, `project_name`, `method`, `percent`, `seconds`) only appear in GA reports
after they're registered under Admin → Custom definitions.

## Other directories

`bargain-hunt/` (a private PWA served at `/bargain-hunt/`, marked `noindex`) and `sift/` (an Android
app, excluded from the build) are separate projects that live in this repository for now.
