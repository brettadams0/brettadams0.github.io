# Baseline audit: brettadams0.github.io

Audit of the site as it was on 2026-10-21, before any redesign work. Every number here is
**measured** unless it says "estimated". Later PRs re-run the same commands and compare.

## Method

- **When:** 2026-10-21. The main runs were 04:00–04:07 UTC. Extra checks ran 04:00–04:01 UTC and
  were spot-checked again at 13:35–16:25 UTC.
- **Tool:** `handoff/tools/measure.mjs` (Playwright Chromium 141.0.7390.37, axe-core 4.10.2 pinned by
  SHA-256). Command:
  `node handoff/tools/measure.mjs https://brettadams0.github.io/ --path / --path /mcp/ --path /neuronet-arena/ --path /mcp-surface/ --path /404.html --shots --axe --perf --runs 3 --budget --analytics --dnt`.
  The same command was run with `--site _site` against a local `jekyll build` (Jekyll 4.4.1).
- **Profiles:**
  - Throttled mobile: 390×844, DPR 2, `isMobile`, CDP latency 150 ms, 1.6 Mbps down, 750 kbps up,
    CPU 4×.
  - Unthrottled desktop: 1440×900.
  - Three runs per profile. The run with the median LCP is reported, and all three LCPs are in the
    JSON.
  - CLS is the largest session window, measured after a slow scroll to the bottom and back.
- **Transfer sizes:** CDP `encodedDataLength`, so headers are included. KB = 1024 bytes. "Images"
  counts everything loaded during a full scroll. The homepage's own images are lazy.
- **Google Analytics:** requests to googletagmanager.com, google-analytics.com and
  analytics.google.com were aborted in every measured run, so GA bytes are **not** in the tables.
  gtag.js was measured separately with one `curl` download (a script fetch, not a hit): **180,868
  B**.
  - Process note: one development test at 03:57 UTC ran with `--allow-ga` against a local build. It
    let gtag.js load from `http://127.0.0.1`, so the property probably received **one page_view**
    with a `127.0.0.1` page location. Every run after that blocked GA.
- **Caveats:**
  - The live site was loaded through the session's HTTPS proxy, so live timings include proxy
    latency. Local-build timings are in a separate table.
  - Google Fonts serves different font files to a mobile user agent. That is why mobile and desktop
    font bytes differ.
- **Extra checks:** scratch scripts (kept out of git) recorded headings, meta tags, tap targets, the
  Tab order, element positions and accessible names. Contrast ratios were computed from the
  `site.css` tokens with the WCAG 2.x formula.

Files in this folder:

- Screenshots: `<page>-<1440|390>-<fold|full>.webp`.
  - Folds are full resolution.
  - Full-page shots are downscaled to stay under 80 KB. Text is soft but layout and headings read.
- Reports: `measure-<page>.json`, trimmed per page. Each holds `perf`, `perf_local_build`, `axe`,
  `analytics`, `dnt`, `budget`, `page` (meta, headings, images) and `viewport.<390|1440>` (header
  links, key-info positions, tap targets, Tab order).
- Citations use the form `[brettadams0:<file>.<json.path>]`.

## 1. Measured baseline

### Throttled mobile (390×844) on the live site

| Page | Total | JS excl. GA | CSS | Fonts | Images | Requests | LCP (element) | CLS |
|---|---|---|---|---|---|---|---|---|
| `/` | 389.6 KB | 2.5 KB | 6.1 KB | 71.8 KB | 304.0 KB | 13 | 744 ms (`p.lede`) | 0.0011 |
| `/mcp/` | 86.0 KB | 2.5 KB | 6.1 KB | 71.8 KB | 0 | 8 | 844 ms (first body `p`) | 0.0234 |
| `/neuronet-arena/` | 119.8 KB | 28.1 KB | 5.5 KB | 61.2 KB | 0 | 19 | 692 ms (`p.lede`) | **0.1643** |
| `/mcp-surface/` | 125.7 KB | 46.9 KB | 4.7 KB | 71.9 KB | 0 | 10 | 1752 ms (report text, since re-rendered) | 0.0021 |
| `/404.html` | 72.1 KB | 2.5 KB | 6.1 KB | 61.3 KB | 0 | 7 | 592 ms (`h1`) | 0.0001 |

### Unthrottled desktop (1440×900) on the live site

| Page | Total | JS excl. GA | CSS | Fonts | Images | Requests | LCP (element) | CLS |
|---|---|---|---|---|---|---|---|---|
| `/` | 409.6 KB | 2.5 KB | 6.1 KB | 91.8 KB | 304.0 KB | 13 | 408 ms (first Geotab bullet `li`) | 0.0040 |
| `/mcp/` | 106.0 KB | 2.5 KB | 6.1 KB | 91.8 KB | 0 | 8 | 480 ms (`h1`) | 0.0431 |
| `/neuronet-arena/` | 134.3 KB | 28.1 KB | 5.4 KB | 75.8 KB | 0 | 19 | 440 ms (`p.lede`) | 0.0152 |
| `/mcp-surface/` | 145.9 KB | 46.9 KB | 4.9 KB | 91.8 KB | 0 | 10 | 636 ms (report text) | 0.0010 |
| `/404.html` | 87.5 KB | 2.5 KB | 6.1 KB | 76.7 KB | 0 | 7 | 328 ms (`h1`) | 0.0001 |

### Local `jekyll build`, served gzipped by measure.mjs

| Page | Mobile total | Mobile LCP | Mobile CLS | Desktop LCP | Desktop CLS |
|---|---|---|---|---|---|
| `/` | 385.2 KB | 604 ms | 0.0011 | 192 ms | 0.0034 |
| `/mcp/` | 84.4 KB | 596 ms | 0.0205 | 268 ms | 0.0423 |
| `/neuronet-arena/` | 104.2 KB | 508 ms | 0.1643 | 104 ms | 0.0198 |
| `/mcp-surface/` | 122.8 KB | 1156 ms | 0.0012 | 272 ms | 0.0010 |
| `/404.html` | 70.5 KB | 504 ms | 0.0001 | 172 ms | 0.0001 |

### Notes on the numbers

- **Budget:** the built-in default (homepage JS < 15 KB excl. GA, LCP < 2000 ms throttled,
  CLS < 0.05, no framework) **passes** on live and local
  `[brettadams0:measure-home.json.budget]`. No page uses a framework.
- **Homepage at load vs after scrolling:** before any scroll, the throttled mobile homepage
  transfers 85.5 KB in 8 requests `[brettadams0:measure-home.json.perf.mobile.initial_transfer_bytes]`.
  The five lazy project screenshots (304 KB) arrive while scrolling, so they make up 78% of a
  full-scroll visit.
- **GA cost:** real visitors also download gtag.js, 180,868 B (176.6 KB), measured with curl. That is
  about 72 times the site's own JS. It loads `async`, so it doesn't block rendering.
- **neuronet-arena:** 12 ES module requests plus `data/trained.json` (21.0 KB, counted as "other")
  `[brettadams0:measure-neuronet-arena.json.perf.mobile.by_type]`.
- **mcp-surface:** `checks.js` is 41.4 KB of the page's 46.9 KB of JS. LCP is the report text that `app.js`
  renders after the checks load, and that node is later replaced.

### axe-core 4.10.2 violations (critical / serious / moderate / minor)

| Page | 390×844 | 1440×900 | Rule ids (nodes) |
|---|---|---|---|
| `/` | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 | none. 32 `color-contrast` "incomplete" at 390 (axe can't resolve the hero gradient); manual ratios below |
| `/mcp/` | 0 / **1** / 0 / 0 | 0 / 0 / 0 / 0 | `scrollable-region-focusable` (1: the `<pre>`) at 390 |
| `/neuronet-arena/` | 0 / 0 / 0 / 1 | 0 / 0 / 0 / 1 | `empty-table-header` (1) |
| `/mcp-surface/` | 0 / **2** / 0 / 0 | 0 / **2** / 0 / 0 | `color-contrast` (17: `#gutter`, `.rule-tag`), `scrollable-region-focusable` (1: `.term`) |
| `/404.html` | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 | none |

The spec's pass bar is zero serious or critical violations. `/mcp/` and `/mcp-surface/` fail it today.

### WCAG contrast of the token pairs in use (computed from `assets/css/site.css:4-24`)

| Foreground | `--bg` #0b0f14 | `--bg-2` #0e141b | `--surface` #131a22 | `--surface-2` #18212b |
|---|---|---|---|---|
| `--text` #e7ecf2 | 16.18 | 15.58 | 14.75 | 13.69 |
| `--soft` #c4ccd6 | 11.86 | 11.42 | 10.81 | 10.03 |
| `--muted` #8695a7 | 6.29 | 6.06 | 5.73 | 5.32 |
| `--faint` #5a6878 | **3.37** | **3.25** | **3.08** | **2.85** |
| `--amber` #ffb454 | 10.90 | 10.50 | 9.94 | 9.22 |
| `--amber-hi` #ffc97f | 12.73 | 12.26 | 11.61 | 10.77 |
| `--teal` #4fd6c4 | 10.75 | 10.35 | 9.80 | 9.10 |
| #9fe3b7 (status, badge) | 12.95 | 12.47 | 11.80 | 10.95 |

Other pairs:

- #9fe3b7 on the composited status pill (#0e1c1a): 11.79. On the composited live badge (#162727): 10.45.
- #1a1206 on `--amber` (primary button): 10.51. On `--amber-hi` (hover): 12.28.
- Amber focus ring against `--bg`: 10.90.
- Non-text: `--border` on `--bg` is 1.36, and `--border-strong` is 1.69 on `--bg` and 1.54 on
  `--surface`.
- Every text token passes AA (4.5:1) except `--faint`, which reaches only 3:1 (large text and UI).
  `--faint` fails outright on `--surface-2`.

## 2. Rubric scores for the current site (1–5, PROMPT.md §4.3)

| # | Criterion | Score | Evidence |
|---|---|---|---|
| 1 | First-screen clarity | **4** | Name, "Data Analyst Intern at Geotab", "Computer Science at the University of Windsor" and "Graduating 2027 · Open to full-time roles" are all in the fold at both sizes, with Résumé and contact buttons `[brettadams0:home-390-fold.webp]` `[brettadams0:home-1440-fold.webp]` (`index.html:7-16`). Missing: which role family (the kicker only says "data & software engineering", `index.html:8`), the graduation month, and any sign of the projects. At 1440 the right ~55% of the hero is empty (estimated from the screenshot) `[brettadams0:home-1440-fold.webp]`. |
| 2 | Proof of skill | **4** | Two live demos built from the projects' code, a case study with real CI output (`mcp/index.html:44-58`) and source links on every card (`index.html:85-150`). It is slow to reach: the first "Live demo" badge is at y=1771 px at 1440 and y=2761 px at 390 `[brettadams0:measure-home.json.viewport.390.key_info.shipped_work]`. |
| 3 | Project presentation | **4** | Consistent cards: 16:9 screenshot, one line, chips, links. The featured neuronet-arena card spans the full row `[brettadams0:home-1440-full.webp]` (`index.html:85-96`, `site.css:486-492`). Weak spots: the chip "13.4 food vs 1.0 for the control" means nothing out of context (`index.html:91`). The MCP card's screenshot links off-site to GitHub, not to `/mcp/` (`index.html:99`). |
| 4 | Experience presentation | **3** | The timeline is clear: title, employer, dates, place, 2–4 bullets and chips per role `[brettadams0:home-1440-fold.webp]` (`index.html:24-77`). Impact is thin. Of 13 bullets, only "hours to minutes" (`:46`) and "over 20 employees" (`:61`) carry a measure, and the Geotab bullets describe duties (`:31-34`). The section is about 2.3 screens long at 390 (estimated from the measured positions, y ≈ 560 to 2496) `[brettadams0:measure-home.json.viewport.390.key_info.projects_heading]`. |
| 5 | Visual craft | **4** | Coherent dark token system, IBM Plex Sans/Mono, terminal touches (`> `, `## `, `// `, `@ `) and a restrained amber accent `[brettadams0:home-1440-fold.webp]`. It looks polished but sits in the familiar dark developer-portfolio idiom. The empty half of the desktop hero and the faint grid (`site.css:231-243`) add little. |
| 6 | Interaction and motion | **4** | Only hover transitions of 0.15–0.2 s (`site.css:172`, `:436`) and smooth anchor scrolling, which is turned off under reduced motion (`site.css:38-42`). The neuronet-arena demo starts paused under reduced motion (`neuronet-arena/ui/app.js:60`). Nothing gets in the way. There is also no purposeful motion that helps, such as feedback on the current section. |
| 7 | Navigation and IA | **3** | One page with four anchors plus Résumé on desktop. Under 860 px the section links are `display: none` and there is no menu (`site.css:859-862`), leaving "brett adams" and "Résumé" `[brettadams0:measure-home.json.viewport.390.header_links]`. The demo pages have a different header whose back link reads "brettadams0.github.io", with no résumé or contact link (`neuronet-arena/index.html:26-33`, `mcp-surface/index.html:24-32`). |
| 8 | Mobile quality | **3** | A real single-column layout with no horizontal scroll, and the demos work at 390 `[brettadams0:neuronet-arena-390-fold.webp]`. Against that: no section nav, projects 3 screens down, footer links 18 px tall and `.also` links 19 px `[brettadams0:measure-home.json.viewport.390.small_targets_24]`, and CLS 0.1643 on `/neuronet-arena/` `[brettadams0:measure-neuronet-arena.json.perf.mobile.cls]`. |
| 9 | Performance | **4** | Homepage: 2.5 KB of JS, LCP 744 ms throttled, CLS 0.0011 `[brettadams0:measure-home.json.perf.mobile]`. Minus points: render-blocking Google Fonts CSS on every page (FCP = LCP = 744 ms), 304 KB of oversized screenshots without `srcset`, 180,868 B of gtag.js, and CLS 0.1643 on the favourite demo on mobile. |
| 10 | Accessibility | **4** | The homepage and 404 have 0 axe violations at both sizes. There is a skip link (`_layouts/default.html:38`), landmarks, a 2 px amber focus ring on every Tab stop `[brettadams0:measure-home.json.viewport.1440.focus_walk]`, and all body text ≥ 5.32:1. Minus points: serious violations on `/mcp/` (1) and `/mcp-surface/` (2). Screen readers also hear CSS prefixes: headings are exposed as "## Experience" and "Data Analyst Intern @ Geotab" (measured with `ariaSnapshot`). |
| 11 | Copy | **4** | Plain and specific, with no hype words: "Checkout is one transaction, so the last unit can't sell twice" (`index.html:145`). Bullets name the systems ("Revenue Operations tables, including the device status, database dimension and accounts master datasets", `:32`). Weak spots: a generic kicker (`:8`), duty-style Geotab bullets and cryptic result chips. |
| 12a | Persona: recruiter | **4** | A 6-second phone scan gets name, current role and employer, school, graduation year, "Open to full-time roles" and a Résumé button `[brettadams0:home-390-fold.webp]`. A 30-second skim can't find the graduation month or term, location or work authorization, or the target roles. On a phone there is no nav to anything past the hero. |
| 12b | Persona: engineer colleague | **4** | Real demos, source links and the `/mcp/` case study with concrete security fixes (`mcp/index.html:92-150`). The site itself is lean (no framework, 2.5 KB JS) `[brettadams0:measure-home.json.perf.mobile.framework]`. Depth starts 2–3 screens down, and the homepage doesn't surface the technical decisions (bit-for-bit parity, snapshot-in-CI). |
| 12c | Persona: business professional | **3** | Language is mostly plain, but few outcomes are stated: MTO "hours to minutes" and dashboards that "replaced manual reporting" (`index.html:45-46`), CIMCO training "over 20 employees" (`:61`). Project lines are technical (MCP, OAuth, schemas, `index.html:103-104`). There is no problem-to-outcome framing. |
| 13 | Memorability | **3** | The thing you'd remember is the neuronet-arena demo: forty agents evolving in the browser `[brettadams0:neuronet-arena-390-fold.webp]`. It serves the content, since it is proof of skill, but it sits 3 screens down on mobile and behind a click. The homepage alone is a clean but familiar dark one-pager. |

## 3. Time to key info (from landing, measured from element positions)

"Scrolls" means full-viewport scrolls needed to bring the element's bottom edge into view:
`ceil((bottom − viewport height) / viewport height)`, where 0 means it is in the first screen. Data is
in `[brettadams0:measure-home.json.viewport.<390|1440>.key_info]`.

| Item | Element used | 390×844 | 1440×900 |
|---|---|---|---|
| (a) Current role | Hero lede "Data Analyst Intern at Geotab…" | 0 scrolls (y 249–328) | 0 scrolls (y 302–361) |
| (b) Résumé | Header "Résumé" button (also in the hero) | 0 (y 11–47) | 0 (y 11–47) |
| (c) Contact | Hero GitHub, LinkedIn and Email buttons | 0 (y 360–454; Email wraps to a second row) | 0 (y 393–435) |
| (d) Best project | neuronet-arena card (`[data-project="neuronet-arena"]`) | **3 scrolls** (y 2564–2744). No nav shortcut: section links are hidden | 2 scrolls (y 1771–2137), or **1 click** on "Projects" in the nav |
| (e) Evidence of shipped work | First "Live demo" badge or link | **3 scrolls** (y 2761) | 1 scroll (y 1771–1793), or 1 click on "Projects" |

The homepage is 6,346 px tall at 390 (7.5 screens) and 4,445 px at 1440 (4.9 screens)
`[brettadams0:measure-home.json.viewport.390.page_height]`. The Geotab bullets, which claim
production work but don't link to it, start just inside the fold at both sizes.

## 4. Analytics check

Run with `--analytics --dnt` on the live site and the local build, with identical results. The
browser was 1440×900 with GA requests blocked. The scroll stopped every 40% of a screen for 1.2 s.
Every link in `[data-project]`, every `[data-contact]` link, every résumé link and every contact-like
link without `data-contact` was clicked, with navigation prevented. `section_dwell` was flushed by
dispatching `pagehide`.

### Homepage `/`

Data is in `[brettadams0:measure-home.json.analytics]`.

- **Measurement ID:** `gtag('config', 'G-Z3KW627PQQ')` only. The old `G-EBYB5MK388` is absent.
- **`section_view` ×4:** `experience`, `projects`, `skills`, `contact`, at 3.9 s, 7.5 s, 12.3 s and
  14.7 s.
- **`section_dwell` ×4:** experience 4 s, projects 9 s, skills 5 s, contact 3 s. These durations come
  from the scripted scroll pace, not from real reading.
- **Not fired:**
  - `hero`: the hero is a `<section>` with no `id` (`index.html:4`).
  - `education`: it is a `div#education` inside `#skills` (`index.html:172`). The script only
    observes `section[id]` (`assets/js/analytics-events.js:68`), although its comment mentions
    `#education` (`:6`).
  - Both behave as the brief describes.
- **`scroll_depth` ×4:** 25, 50, 75 and 100.
- **`project_click` ×18:** every link in the five cards, including the `aria-hidden` screenshot links,
  plus MazeFinder. Each sends `project_name` and `link_url`, for example
  `{"project_name":"neuronet-arena","link_url":"https://brettadams0.github.io/neuronet-arena/"}`.
- **`resume_download` ×3:** header, hero and contact section.
- **`contact_click` ×6:** hero github, linkedin and email, then contact section email, linkedin and
  github.
- **Fired nothing:**
  - The footer GitHub, LinkedIn and Email links (`_layouts/default.html:61-63`), which have no
    `data-contact`.
  - "More on GitHub →" (`index.html:156`, no `data-project`). This is from reading the source; the
    click set didn't include it.
  - `data-project="nhl-arena"` (`index.html:154`) has no link inside it, so `project_click` can never
    fire for it.

### `/mcp/` and `/404.html`

- gtag is present.
- No `section[id]`, so there are no `section_view` or `section_dwell` events.
- `scroll_depth` fires 25 to 100, and the nav Résumé link fires `resume_download`.
- The footer is the only contact path on these pages, and its links have no `data-contact`, so
  `contact_click` can't fire there `[brettadams0:measure-mcp.json.analytics.failed_clicks]`.

### `/neuronet-arena/` and `/mcp-surface/`

- **No gtag at all.** These are static HTML files with no front matter, so `analytics.html` is never
  included `[brettadams0:measure-neuronet-arena.json.analytics.gtag_present]`.
- A demo visit records nothing: no page_view and no engagement. The only trace is the homepage
  `project_click` that led there.

### Do Not Track

- With `navigator.doNotTrack = '1'` set by an init script, every page queued **0 custom events**
  `[brettadams0:measure-home.json.dnt]`.
- `gtag('config', 'G-Z3KW627PQQ')` is still queued. The DNT guard (`analytics-events.js:12`) only
  covers the custom events, so gtag.js would still send its own page_view. That is the current
  behaviour, which the brief says to keep.

## 5. Issues

Severity: **critical** blocks a core task, **high** hurts a persona's main path or fails the
accessibility pass bar, **medium** is a visible quality or measurement gap, **low** is polish.
"Upstream" means the fix belongs in the neuronet-arena repo, not here.

1. **High: no section navigation on phones.** `assets/css/site.css:859-862` hides
   `.nav-section` under 860 px, and `_layouts/default.html:43-49` has no menu or toggle.
   - Evidence: at 390 the header shows only "brett adams" and "Résumé"
     `[brettadams0:measure-home.json.viewport.390.header_links]`. Projects start at y=2496 px, 3
     screens down `[brettadams0:measure-home.json.viewport.390.key_info.projects_heading]`.
   - Hurts: recruiter (30-second skim), engineer (wants proof).
2. **High: proof of work is below the fold at every size.** Experience (`index.html:21-79`, 13
   bullets) comes before Projects (`:81`).
   - Evidence: the first "Live demo" is at y=1771 (1440) and y=2761 (390), and nothing in either
     fold points to a project `[brettadams0:home-1440-fold.webp]` `[brettadams0:home-390-fold.webp]`.
   - Hurts: engineer, recruiter, and memorability.
3. **High: `/mcp-surface/` fails axe with 2 serious rules.**
   - `color-contrast` on 17 nodes: `#gutter` (`mcp-surface/styles.css:98`) and `.rule-tag`
     (`:161`) use `--faint` #5a6878 on `--term` #0e141b, which is 3.25:1.
   - `scrollable-region-focusable` on `.term` (`mcp-surface/index.html:69`, `styles.css:134-137`).
   - Evidence: `[brettadams0:measure-mcp-surface.json.axe.1440.by_impact.serious]`.
   - Hurts: keyboard and low-vision users, and an engineer judging craft.
4. **Medium: `/mcp/` fails axe at 390 with 1 serious rule.** The `<pre>` code block
   (`mcp/index.html:45`, `site.css:707-716`, `overflow-x: auto`) is a scroll region with no
   `tabindex`, so in browsers that don't auto-focus scrollers, keyboard users can't scroll it
   sideways at phone width.
   - Evidence: `[brettadams0:measure-mcp.json.axe.390.by_impact.serious]`.
   - Hurts: keyboard users, engineer.
5. **Medium (upstream): layout shift on the favourite demo on mobile, CLS 0.1643.** The font
   stylesheet loads non-blocking with the `media="print" onload` swap
   (`neuronet-arena/index.html:18-20`), so IBM Plex arrives after first paint.
   - Measured: when Plex replaces the fallback, `.lede` goes from 209 px to 186 px tall, and the
     arena canvas and controls move up 23 px.
   - The shift reproduces on the local build. Desktop CLS is 0.0152.
   - Evidence: `[brettadams0:measure-neuronet-arena.json.perf.mobile.shifts]`.
   - Hurts: mobile visitors, engineer.
6. **Medium: screen readers hear the terminal prefixes.**
   - CSS `content` on `.section-title::before` (`site.css:323-326`) and `.job-org::before`
     (`:394-397`) ends up in accessible names. Measured: headings and regions are named
     "## Experience", "## Projects" and "## Contact", and job headings "Data Analyst Intern @ Geotab".
   - `.site-name::before` (`:121-124`), `.kicker::before` (`:278-281`) and `.post-body h2::before`
     (`:683-688`) use the same technique. Those were not measured individually.
   - Hurts: screen-reader users, plus a recruiter using a screen reader.
7. **Medium: render-blocking third-party font CSS on every default-layout page.**
   `_layouts/default.html:31-33` loads Google Fonts CSS with 7 faces requested (Mono 400/500/600,
   Sans 400/500/600/700). The page downloads 4 files.
   - Evidence: on throttled mobile, FCP = LCP = 744 ms on `/` and 844 ms on `/mcp/`. Fonts are 71.8
     of 86.0 KB (83%) of `/mcp/` `[brettadams0:measure-mcp.json.perf.mobile.render_blocking]`.
   - Desktop `/mcp/` CLS is 0.0431, close to the 0.05 budget. Its source is `nav.site-nav` at 643 ms,
     most likely the font swap (inferred, not isolated).
   - Hurts: mobile visitors, engineer.
8. **Medium: oversized project screenshots with no `srcset`.** The images at `index.html:87, 100,
   114, 129, 142` have no `srcset` or `sizes`.
   - `mcp-servers.webp` and `mcp-surface.webp` are 2200×1376 but render at 524×294 at 1440, which is
     4.2× the needed width `[brettadams0:measure-home.json.page.images]`.
   - Images are 304 KB of a 389.6 KB scroll-through on mobile.
   - Hurts: mobile recruiter on data.
9. **Medium: the desktop first screen is half empty.** The hero is one column (`site.css:245-247`),
   with the lede capped at 34em (`:292`). At 1440×900 the right ~55% of the hero (estimated from the
   screenshot) is blank apart from the faint grid, and the fold ends at the first Geotab bullets `[brettadams0:home-1440-fold.webp]`.
   - Hurts: engineer and business persona (first impression, memorability).
10. **Medium: the status line leaves out what a recruiter checks.** `index.html:7` says "Graduating
    2027 · Open to full-time roles" with no month or term, no role family, and no location or
    work authorization. The kicker "data & software engineering" (`:8`) is generic.
    - These are facts only Brett has: fact sheet D4 and D5, `handoff/research/fact-sheet.md:442-443`.
    - Hurts: recruiter.
11. **Medium: demo visits are unmeasured.** `/neuronet-arena/` and `/mcp-surface/` load no gtag
    (`neuronet-arena/index.html:1-23` and `mcp-surface/index.html:1-21` have no front matter or
    include) `[brettadams0:measure-mcp-surface.json.analytics.gtag_present]`.
    - Brett's favourite page has no page_view or dwell data. Adding GA to `neuronet-arena/` is an
      upstream or sync decision.
    - Hurts: Brett (no evidence of what visitors use).
12. **Medium: footer contact links aren't tracked.** `_layouts/default.html:61-63` has no
    `data-contact`, so clicking them sends nothing. On `/mcp/` and `/404.html` they are the only
    contact links `[brettadams0:measure-home.json.analytics.failed_clicks]`.
    - Hurts: Brett (contact_click undercounts).
13. **Medium: the demo pages don't share the site shell.**
    - The back link text is the domain "brettadams0.github.io". `neuronet-arena/index.html:28` links
      to the absolute `https://brettadams0.github.io/`, and `mcp-surface/index.html:26` links to
      `/`. `/mcp/` instead uses "← Brett Adams" pointing at `/#projects` (`mcp/index.html:10`).
    - No résumé or contact link on the demo pages, and no skip link.
    - Favicons differ: neuronet-arena has an inline data-URI SVG (`neuronet-arena/index.html:15`),
      the rest use `/assets/img/favicon.svg`.
    - Font sets differ: neuronet-arena loads Mono 400/500 non-blocking; mcp-surface loads Mono
      400/500/600 blocking (`mcp-surface/index.html:18`); the site loads Mono 400–600 and Sans
      400–700 blocking.
    - Container is 1320 px on the demos vs 1200 px on the site. Radius is 10 vs 12 px.
    - On neuronet-arena, links fall back to the browser's default focus ring, because
      `neuronet-arena/styles.css:158` styles only buttons and inputs
      `[brettadams0:measure-neuronet-arena.json.viewport.1440.focus_walk]`.
    - The neuronet-arena items are upstream.
    - Hurts: a recruiter who lands on a shared demo link and wants the résumé.
14. **Medium: Experience bullets read as duties, not outcomes.** `index.html:31-34` (Geotab) and the
    remaining bullets have numbers only in "hours to minutes" (`:46`) and "over 20 employees"
    (`:61`).
    - Real numbers need Brett: `[BRETT: …]` placeholders.
    - Hurts: business persona, recruiter.
15. **Low: small tap targets at 390.**
    - Footer links are 18 px tall (`site.css:832-840`) and `.also` links 19 px (`:546-549`).
    - Project links are exactly 24 px (`:513-521`). mcp-surface's segmented buttons are 27 px
      (`mcp-surface/styles.css:71-79`).
    - All pass WCAG 2.2 SC 2.5.8, by size or by the spacing exception, but all are well under 44 px
      `[brettadams0:measure-home.json.viewport.390.small_targets_24]`.
    - Hurts: mobile visitors.
16. **Low: `--faint` text is under 4.5:1.** It measures 3.37:1 on `--bg` and 2.85:1 on
    `--surface-2`. On the site it is used only for decorative glyphs and list markers
    (`site.css:280`, `:417`, `:672`). On the demos it is used for real text:
    `neuronet-arena/styles.css:261` `td.pending` (upstream), and mcp-surface (issue 3).
17. **Low: UI borders are below 3:1.** `--border` is 1.36:1 and `--border-strong` 1.69:1 against
    `--bg`. The `.btn` outline (`site.css:166`) and the card edges rely on them.
    - Buttons are identified by their text, so this is not an SC 1.4.11 failure. Cards are hard to
      separate on some displays.
18. **Low: GA dominates the JS weight.** gtag.js is 180,868 B compared with 2.5 KB of site JS. It
    loads async, so render isn't blocked, and the spec budget excludes it by definition. It is
    still the largest single download a mobile visitor makes on `/mcp/`.
19. **Low: heavy social cards.** `assets/img/social-card.png` is 211,878 B (1200×630 PNG) and is the
    default image for every page (`_config.yml:25`). The demos' `og.png` files are 144,842 B and
    109,961 B.
    - Estimated: a tuned PNG or JPEG would be well under 100 KB.
20. **Low: demo titles don't name Brett.** "neuronet-arena · live demo" and "mcp-surface ·
    playground" (`neuronet-arena/index.html:6`, `mcp-surface/index.html:6`). Neither page has a
    canonical link. Browser tabs, shares and search results don't say whose work it is.
21. **Low: the 404 page is a dead end with generic meta.** A missing URL correctly returns 404 with
    the custom page (measured on `/this-page-does-not-exist/`).
    - It offers only "Back to the homepage" (`404.html:11`), with no links to projects, demos or the
      résumé.
    - Its meta description is the site default, and jekyll-seo-tag gives it
      `canonical: /404.html` `[brettadams0:measure-404.json.page.canonical]`.
22. **Low: JSON-LD is thin.**
    - The homepage has two blocks: jekyll-seo-tag `WebSite`, plus the custom `ProfilePage` with
      `Person` (`_layouts/default.html:11-30`).
    - `Person` has no `image`, `knowsAbout` or `email`, and uses `alumniOf` for a current student
      (fact sheet D19).
    - `/mcp/` has only `WebPage`; the demos have none.
23. **Low: the live badge sits inside the heading.** `index.html:89` and `:116` produce the
    accessible names "neuronet-arena Live demo" and "mcp-surface Live demo".
24. **Low: off-site first click on the MCP card.** The screenshot links to the google-workspace-mcp
    repo (`index.html:99`), not to the `/mcp/` case study that the card's first text link points to
    (`:106`).
25. **Low: hero buttons wrap on phones.** At 390 "Email" drops to a second row on its own
    (`index.html:11-16`) `[brettadams0:home-390-fold.webp]`.
26. **Low: cryptic result chip.** "13.4 food vs 1.0 for the control" (`index.html:91`) needs the
    demo page to make sense.
27. **Low (upstream): empty table header.** `neuronet-arena/index.html:118` has an empty first `<th>`
    in the parity table (axe minor `empty-table-header`).
28. **Low: mcp-surface has the site's slowest LCP.** 1752 ms throttled
    `[brettadams0:measure-mcp-surface.json.perf.mobile.lcp_runs_ms]`, because the report text paints
    only after `checks.js` loads (41.4 KB). It is under 2 s.

### Checked and fine (no issue)

- **Lazy featured image and LCP:** the neuronet-arena screenshot (`index.html:87`, `loading="lazy"`)
  starts at y=1771 (1440) and y=2564 (390), well below the fold. The LCP element is text on every
  page, so `lazy` is correct and costs nothing `[brettadams0:measure-home.json.perf.mobile.lcp]`.
- **Heading outline:** h1, then h2, then h3 in order on every page, one h1 per page. "Education" is
  an h2 inside the `#skills` region, which is valid.
- **Focus visibility on site pages:** every Tab stop on `/` and `/mcp/` showed a solid 2 px amber
  outline (10.90:1 against `--bg`). The skip link is the first stop.
- **Reduced motion:** smooth scrolling is disabled (`site.css:38-42`), and neuronet-arena starts
  paused (`neuronet-arena/ui/app.js:60`).
- **Titles and descriptions on default-layout pages:** distinct and accurate for `/` and `/mcp/`.
  The sitemap lists `/`, `/mcp/`, both demos and the résumé PDF. `robots.txt` allows everything and
  points to the sitemap.

## 6. What works today (keep)

- **Lean, framework-free pages.** The homepage ships 2.5 KB of its own JS. LCP is 744 ms under
  throttled mobile and CLS 0.0011, and the default budget passes
  `[brettadams0:measure-home.json.perf.mobile]` `[brettadams0:measure-home.json.budget]`.
- **The first screen states the facts a recruiter scans for.** Name, current role and employer,
  school, graduation year and availability are all there, with Résumé and contact buttons in the
  fold at 390 and 1440 `[brettadams0:home-390-fold.webp]` (`index.html:7-16`).
- **Real live demos, reached from their cards.** "Live demo" badges and links sit on the neuronet-arena
  and mcp-surface cards (`index.html:85-125`). neuronet-arena works on a phone with the arena in the
  first screen `[brettadams0:neuronet-arena-390-fold.webp]` and respects reduced motion
  (`neuronet-arena/ui/app.js:60`).
- **The analytics events work as documented.** All six events fire with the four expected
  `section_id` values, and DNT suppresses every custom event
  `[brettadams0:measure-home.json.analytics.counts]` `[brettadams0:measure-home.json.dnt.pass]`.
- **Accessibility basics on the homepage.** Zero axe violations at both sizes, a skip link,
  landmarks, a consistent focus ring, and every body-text token at 5.32:1 or better
  `[brettadams0:measure-home.json.axe]`.
- **One token system across site and demos.** `--bg`, `--surface`, `--border`, `--amber`, `--teal`,
  `--red` and `--good` have the same values in `assets/css/site.css:4-24`,
  `neuronet-arena/styles.css:5-27` and `mcp-surface/styles.css:4-22`. IBM Plex is used everywhere.
- **Specific, plain copy.** One line per project with facts as chips (`index.html:90`, `:117`,
  `:145`). `/mcp/` shows real CI output (`mcp/index.html:44-58`) and names each fix
  (`:92-150`).
- **Correct error handling and indexing.** Missing URLs return a real 404 with the custom page, and
  the sitemap and robots.txt are in place.
