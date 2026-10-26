# Site redesign spec

The redesign of brettadams0.github.io, written to be built PR by PR (§16) by someone who wasn't in
the research. It changes how the site looks, reads and works for three audiences: a recruiter
scanning on a phone, an engineer checking depth, and a business reader looking for outcomes.

Nothing here has been built. This file, the research it rests on and the first-screen mock live
under `handoff/`, which `_config.yml` keeps out of the published site.

| | |
|---|---|
| Research | 29 personal sites captured and scored on 2026-10-21, every cited claim re-checked by a second agent. Method and candidate pool: [`research/README.md`](research/README.md). Per-site evidence: [`research/appendix.md`](research/appendix.md). Scores: [`research/scores.csv`](research/scores.csv). Verification: [`research/verification-log.md`](research/verification-log.md) |
| Facts about Brett | [`research/fact-sheet.md`](research/fact-sheet.md): every role, date, tool and number, with its source line. No other source is used for facts about Brett |
| Current site | [`research/baseline/audit.md`](research/baseline/audit.md): measured baseline, scores and the issue list |
| First-screen mock | [`mocks/first-screen.html`](mocks/first-screen.html) and its screenshots (§6) |
| Measuring tool | [`tools/measure.mjs`](tools/measure.mjs): screenshots, axe, performance budget and analytics check for any URL or a local build |
| New project brief | [`border-waits-pipeline.md`](border-waits-pipeline.md) (§14) |
| Decisions for Brett | §18. Each entry has a default the builder uses if Brett doesn't answer |

Citation formats used throughout:
- `[src: index.html:31]` is a line in this repository. Every fact about Brett carries one, taken
  from the fact sheet.
- `[eugeneyan:desktop-fold.webp]` and `[eugeneyan:record.performance.desktop.lcp_ms]` point at a
  research capture or record field for another site, committed under `research/sites/<slug>/`.
  `.png` names point at full-page or inner-page captures, which were kept out of git (see the
  research README).
- `[brettadams0:measure-home.json.perf.mobile.lcp]` points at a baseline measurement of the current
  site in `research/baseline/`.
- `P1`–`P20`, `A1`–`A23` and `S01`–`S18` are the patterns, anti-patterns and hiring-side sources in
  §3 and the appendix.
- **measured** means measured in this research, with method and date. **estimated** means a
  judgement. `[BRETT: …]` marks a fact only Brett can supply. Each one is listed in §18 with the
  wording to ship until Brett answers.
- Working files from the research (the synthesis notes, the new-project evaluation, the capture
  scripts) stayed in the session scratchpad. The spec never cites them: each fact it needs is in
  §3, §14, the brief or a file under `research/`.

## Contents

0. [Ground rules](#0-ground-rules)
1. [Summary](#1-summary)
2. [Current-site audit](#2-current-site-audit)
3. [Research findings](#3-research-findings)
4. [Goals and personas](#4-goals-and-personas)
5. [Information architecture](#5-information-architecture)
6. [First screen](#6-first-screen)
7. [Visual system](#7-visual-system)
8. [Component specs](#8-component-specs)
9. [Motion](#9-motion)
10. [Accessibility](#10-accessibility)
11. [Performance budget](#11-performance-budget)
12. [SEO and sharing](#12-seo-and-sharing)
13. [Analytics](#13-analytics)
14. [New project proposal](#14-new-project-proposal)
15. [File-by-file change list](#15-file-by-file-change-list)
16. [Implementation plan](#16-implementation-plan)
17. [Risks and open questions](#17-risks-and-open-questions)
18. [Decisions for Brett](#18-decisions-for-brett)

## 0. Ground rules

Everything in this spec sits inside these rules. They restate `CLAUDE.md`, the README and Brett's brief
so the builder has them in one place. A PR that breaks one of them is wrong even if CI is green.

### Stack and CI

- Jekyll on GitHub Pages with exactly two plugins: `jekyll-seo-tag` and `jekyll-sitemap`
  (`_config.yml:13-15`). **Never add a `Gemfile`.** It changes how Pages resolves dependencies
  (`README.md:34`).
- No build step that Pages won't run. Site pages and demos are plain HTML, CSS and ES modules, with no
  bundler and no framework. Any extra plugin, bundler, framework or custom deploy workflow is a
  decision for Brett (§18), never a default.
- CI (`.github/workflows/ci.yml`) builds with `actions/jekyll-build-pages@v1`, then runs
  `node .github/scripts/check-links.mjs` over `_site`. Every PR keeps it green. Local check:
  `gem install jekyll jekyll-seo-tag jekyll-sitemap && jekyll build && node .github/scripts/check-links.mjs`.
  Local `gem install jekyll` gives Jekyll 4.x while Pages builds with 3.x. Use no Liquid or config
  feature that 3.x lacks, and treat CI as the final word.
- Playwright and Chromium are preinstalled. **Don't run `playwright install` in this environment.** The
  only exception is PR 18's GitHub-hosted runner, pinned to the same Playwright version (§16 PR 18). The Node `playwright`
  module resolves globally; `handoff/tools/measure.mjs` shows how. axe-core isn't installed:
  `measure.mjs` fetches a pinned `axe.min.js` at runtime. **Never commit `node_modules`. Never add a
  `package.json` outside `handoff/`** (Jekyll would publish a root one). If a manifest is unavoidable,
  it goes in `handoff/tools/`.

### Folders not to reshape

- `neuronet-arena/` is a verbatim copy of the neuronet-arena repo's `web/` directory, with one local
  difference (the "How it was built" link is removed; `README.md:75-76`). Don't edit its files here.
  Any change to it is written as an upstream change in that repo (§18).
- `mcp-surface/checks.js` is generated from the npm package with esbuild (`README.md:78-92`). Don't
  hand-edit it. `mcp-surface/index.html`, `styles.css`, `app.js` and `examples.js` are this repo's own
  files and may change.

### Analytics (GA4 `G-Z3KW627PQQ`, `_config.yml:11`)

- Every event in `assets/js/analytics-events.js` keeps working: `section_view`, `section_dwell`,
  `project_click` (any link inside an element with `[data-project]`), `resume_download`,
  `contact_click` (`[data-contact]` on the link) and `scroll_depth`. §13 maps each one to its new
  element.
- `section_view` and `section_dwell` observe only `section[id]` (`analytics-events.js:68`). Today they
  fire for `experience`, `projects`, `skills` and `contact`. The hero has no id, and `#education` is a
  `div` inside `#skills` (`index.html:172`), so neither fires, although the script's comment mentions
  `#education` (`analytics-events.js:6`). Those four `section_id` values stay, or each is mapped
  explicitly. Renaming or merging them breaks GA report continuity. Any new value (such as `hero` or
  `education`) is a decision (§18).
- `resume_download` matches the href against `/Resume_BrettAdams\.pdf$/` (`analytics-events.js:108`).
  The file stays at `assets/resume/Resume_BrettAdams.pdf`. No query string or fragment goes on the
  link, or the event stops firing without any error.
- Do Not Track behaviour stays as it is (`analytics-events.js:12`). **Never restore `G-EBYB5MK388`**
  (`_config.yml:9-10`). It belongs to someone else's property.
- When a PR runs `measure.mjs` against the live site, GA requests are blocked by default so tests
  don't send hits to Brett's property.

### Résumé

- The PDF is built from `resume/resume.tex` with XeLaTeX on Overleaf, and its format stays exactly as
  it is. The site links to it. This spec doesn't redesign it. Wording points that disagree with the
  site or the code are listed as decisions (§18).

### Brett's standing rules (a change needs Brett's sign-off; every default below keeps them)

- neuronet-arena's live demo is Brett's favourite thing on the site. More live in-browser showcases of
  real projects are welcome, built from the project's actual code and never mocked up.
- **Nothing live runs on the homepage, and each project appears there once.** A demo is reached from
  its project card (screenshot, "Live demo" badge, link) and starts only on its own page. The hero demo
  preview and the separate demos section were removed as duplicates. Neither comes back.
- **No blog posts.** Copy stays short and factual: one line per project, facts as chips. `/mcp/` is
  the only long-form page.
- Dark only. The tokens (`--bg #0b0f14`, `--surface #131a22`, `--border #222c38`, `--amber #ffb454`,
  `--teal #4fd6c4`, `--red #ef6a5f`, `--good #3ccf6e`) are shared with the demos. IBM Plex Sans and
  IBM Plex Mono, with terminal touches: the `> brett adams` brand, `## ` section titles, `// `
  kickers. §7 evolves these and says how each change reaches the demo pages.
- The tokens live in `assets/css/site.css` (`CLAUDE.md:12`), and by default they stay there. Demo
  pages carry a copy of the same values in their own `:root`, as neuronet-arena does today. A
  separate `assets/css/tokens.css` is created only if Brett answers §18 #49 with (a), and Brett
  updates `CLAUDE.md:12` in that same change. Builders never edit `CLAUDE.md`.

### Honesty and copy

- **Never invent metrics, employers, titles, dates, results, testimonials, user counts or quotes.**
  Every factual line of copy in this spec carries its source (`[src: resume/resume.tex:106]`). A line
  that needs a fact only Brett has uses a visible placeholder such as `[BRETT: rows/day migrated?]`.
  Each placeholder is also listed in §18. The builder ships no placeholder. Until Brett answers, the
  line ships in its fallback form (given next to each placeholder) or is left out.
- Where sources disagree (`index.html`, `resume.tex`, `/mcp/`), copy uses the wording named in that
  §18 entry's default. A claim contradicted by code, a README or a handoff file is never repeated as
  fact.
- Plain and specific. No hype words ("passionate", "cutting-edge", "leveraging", "seamless"), no
  buzzword walls, nothing that reads as AI-written.
- Every number in this spec is labelled **measured** (with method and date) or **estimated**.

### Process for every PR in §16

- Change only the files that PR lists. Run `jekyll build`, `check-links.mjs`, and
  `node handoff/tools/measure.mjs` (screenshots at 390, 768 and 1440 compared with the before state,
  axe, the budget, and the analytics check) before pushing.
- CI runs on pull requests to `main` and on pushes to `main` (`.github/workflows/ci.yml:3-7`). Open
  every PR against `main`, so CI runs before merge.

## 1. Summary

The current site is already lean, accessible and honest. It scores a mean of 3.67 on the rubric,
joint 10th of the 30 sites scored, with 0 axe violations on the homepage (measured, §2). It loses
most to the set on three criteria. Navigation scores 3, and 20 of the 29 sites score higher. Mobile
scores 3 (18 higher), and memorability scores 3 (20 higher) (measured, §3.3). The redesign keeps
what works and fixes what costs Brett with each audience: a recruiter on a phone, an engineer or
hiring manager looking for depth, and a business reader looking for outcomes.

### The changes that matter most

| # | Change | Who it's for | Evidence | Where |
|---|---|---|---|---|
| 1 | **The first screen reads like a résumé header.** The sourced hero copy stays, including "Graduating 2027 · Open to full-time roles". A new "At a glance" card lists now, before, studying, results and tools. Its Results row gives three sourced outcomes: Power BI dashboards that replaced manual reporting, processing time cut from hours to minutes, and Python training for over 20 employees. "Looking for" and "Based in" rows are added only once Brett answers (§18 #31–32). Résumé is the primary button, with Email · LinkedIn · GitHub beside it. The card fills the empty right half of the 1440 fold and sits under the buttons on a phone. | Recruiter (6-second scan, then the 30-second skim); business reader (the Results row) | P1, P3 (§3.4); audit issue 9 for the right-hand column (P19 is an idea: two independent sites). The first pass reads title, employer, dates and school in that order (H2: S01, S02). Audit issues 9, 10, 25, and 14 for the outcomes | §6, §8.2–8.3 |
| 2 | **Phones get the section links back.** Below 860 px, Experience · Projects · Skills · Contact stay visible as a second row of 44 px text links, with no menu and no JS. Today, phones show only the brand and Résumé. | Everyone on a phone | A3: 4 sites plus Brett's have no phone nav. P4. Audit issue 1 (high) | §5.3, §8.1 |
| 3 | **Proof of work is one tap away, and Experience stays first.** On a phone, the nav row's Projects link reaches the featured demo card in 1 tap. Today that takes 3 scrolls at 390. Above 860 px the hero also has a text link, "Projects and live demos ↓". It is hidden at ≤ 860 px, where the nav row already has Projects. In Experience, each role keeps its first bullet. Other bullets move one at a time into a native disclosure, outcome bullets last, and only as far as Projects' height limit needs. | Engineer, recruiter | P5, P6, P7. The internship is the strongest new-grad signal (H4: S04), so the order stays. Audit issue 2. Targets in §4.3 | §4.3–4.5, §5.2–5.3, §8.4 |
| 4 | **Every project card says what each link opens, in plain words.** Each card gets a kind line ("Live demo · runs in your browser", "Live demo · SQL in your browser", "Case study"). Links are named by destination. "Live demo" is used only for pages that run the project's code. Lines and chips say what the work does, for example "Tools checked on every change" for the MCP servers. Screenshots get `srcset` and 800 px variants (105,646 B against 307,644 B today, measured). | Engineer, business reader | P12, P13; A12, A21 (§3.6). Audit issues 8, 23, 24 and 26 | §7.9, §8.5–8.10 |
| 5 | **Demo pages lead back to the person.** `/mcp-surface/` and future demo pages share one header: "← Brett Adams", project name, Source, Résumé, a short "How it works" block and contact in the footer. neuronet-arena gets the same through an upstream PR in its own repo. | Recruiter arriving from a shared demo link, engineer | P8, P9, P10. Audit issue 13 | §8.20, §16 |
| 6 | **The visual system is written down and the remaining accessibility gaps close.** One `:root` block at the top of `assets/css/site.css`, where `CLAUDE.md:12` says the tokens live, holds the existing palette plus a text-safe `--dim` and type, space and motion tokens. Demo pages carry a copy of the same values, as neuronet-arena does. A separate `tokens.css` comes only if Brett chooses it (§18 #49). Plex is self-hosted with metric-matched fallbacks (70,472 B, measured; §18 #3), and the 6 failing contrast pairs are fixed (§7.3). Terminal prefixes stay on screen but leave accessible names, and code scrollers become keyboard-reachable. | Engineer judging craft, screen-reader and keyboard users | P16, P20; A14: 20 of 29 sites fail contrast. Audit issues 3, 4, 6, 7 | §7, §10 |
| 7 | **`/mcp/` follows one case-study outline.** It runs For, problem, a diagram on the first screen, decisions, tests, result and limits. The For line names the reader and the problem in plain words: "For anyone who lets an AI assistant send email, edit a calendar or post on their behalf, and needs to know when what it can do changes." It is still the only long-form page. | Engineer, business reader | P11 (§3.4). Hiring managers ask how and why, not just what (H5: S06, S07) | §8.18 |
| 8 | **One new project closes the data-engineering gap.** border-waits is an hourly pipeline over the wait estimates the Canadian and U.S. border agencies publish. It is modelled with dbt on DuckDB, tests gate every deploy, and a visitor can query it with SQL in the browser on click. It gets one card and its own page, and nothing live on the homepage. It is queued as a brief, not built. Its site PR waits for at least 21 days of collected data. | Data-engineering hiring manager, recruiter | No verified site in the set shows a scheduled pipeline with visible freshness or test status (§3.9; appendix, "Set-level checks"). Prototype parity measured (§14) | §14, `handoff/border-waits-pipeline.md` |

Copy stays sourced throughout. Every fact on the page traces, through the fact sheet, to
`index.html`, `resume/resume.tex` or the cited project sources: each project's README or code, and
`mcp/index.html` for the MCP work. Facts only Brett has, such as impact figures, target roles and
location, are `[BRETT: …]` placeholders. Each one has a sourced line that ships until Brett answers,
or its row is left out (§18).

### What changes, what stays

**Changes**
- The hero gains the At-a-glance card with its Results row (§6, §8.3). Above 860 px it also gains a
  jump link to Projects (§8.2).
- A second nav row appears on phones (§8.1).
- Experience keeps every role's first bullet in view. Other bullets move into a disclosure one at a
  time, outcome bullets last, until Projects sit no lower than today (§4.5, §8.4).
- Project cards get kind lines, named links, plainer lines and chips, and responsive screenshots
  (§8.5–8.10).
- `/mcp/` is reordered onto the case-study outline, with a plain For line (§8.18).
- `/mcp-surface/` gets the shared demo shell plus contrast and keyboard fixes (§8.20).
- The tokens are written as one `:root` block at the top of `assets/css/site.css`, and the demo
  pages copy it. `assets/css/tokens.css` is created only if Brett answers §18 #49 with (a). Fonts are
  self-hosted (§7, §18 #3).
- Footer contact links get `data-contact` (Decision, default yes, §13, §18 #12).
- `measure.mjs` gains an `--a11y` mode, and `handoff/tools/contrast.py` is added. Both ship in PR 0
  (§10.15, §7.3).

**Stays**
- The section order (Experience, Projects, Skills with Education, Contact) and every `section[id]`
  value GA reports on: `experience`, `projects`, `skills` and `contact` (§13).
- The résumé at `assets/resume/Resume_BrettAdams.pdf`, linked with no query string, and the résumé
  itself.
- GA4 `G-Z3KW627PQQ`, every existing event and the Do Not Track behaviour (§13).
- Nothing live on the homepage, each project once, one line per project with facts as chips, and no
  blog (§0).
- Dark only, the palette values, IBM Plex, the terminal touches as visual details, and the tokens in
  `assets/css/site.css` (§7, `CLAUDE.md:12`).
- Jekyll on Pages with the same two plugins, no Gemfile, no build step, and CI as it is (§0).
- `neuronet-arena/` untouched in this repo; changes to it go upstream (§0, §8.20).

## 2. Current-site audit

This is the site as it stood on 2026-10-21, before any redesign work. The full audit is
[`research/baseline/audit.md`](research/baseline/audit.md), and the per-page reports are
`research/baseline/measure-<page>.json`. Every number in this section is **measured** unless it says
**estimated**. Later PRs re-run the same command and compare against these tables.

### 2.1 How it was measured

- **When:** 2026-10-21, main runs 04:00–04:07 UTC, spot checks 13:35–16:25 UTC.
- **Tool:** `handoff/tools/measure.mjs` with Playwright Chromium 141.0.7390.37 and axe-core 4.10.2,
  pinned by SHA-256. Command:
  `node handoff/tools/measure.mjs https://brettadams0.github.io/ --path / --path /mcp/ --path /neuronet-arena/ --path /mcp-surface/ --path /404.html --shots --axe --perf --runs 3 --budget --analytics --dnt`.
  The same run against a local `jekyll build` (Jekyll 4.4.1) used `--site _site`.
- **Profiles:**
  - Throttled mobile: 390×844, DPR 2, `isMobile`, 150 ms latency, 1.6 Mbps down, 750 kbps up,
    CPU 4×.
  - Unthrottled desktop: 1440×900.
  - Three runs each. The run with the median LCP is reported.
  - CLS is the largest session window after a slow scroll to the bottom and back.
- **Sizes:** CDP `encodedDataLength`, so headers are included. 1 KB = 1,024 B. "Images" counts
  everything loaded during a full scroll.
- **Google Analytics was blocked** in every measured run, so GA bytes are not in the tables. gtag.js
  was fetched once with `curl` and is 180,868 B.
- **One GA test hit.** A development run of `measure.mjs` at 03:57 UTC used `--allow-ga` against
  the local build. It probably sent one `page_view` with a `127.0.0.1` page location to
  `G-Z3KW627PQQ`. Every later run blocked GA.
- **Caveat:** the live site was loaded through the session's HTTPS proxy, so live timings include
  proxy latency. The local-build table has no proxy in the path.

### 2.2 Rubric scores (1–5)

Scores use the rubric in [`research/README.md`](research/README.md#rubric) and come from audit §2.
The mean is **3.67** (measured: the arithmetic mean of the 15 integer scores). That puts the current
site joint 10th of the 30 scored sites (29 others plus this one). Nine sites score higher.

| # | Criterion | Score | Why |
|---|---|---|---|
| 1 | First-screen clarity | **4** | Name, "Data Analyst Intern at Geotab", school and "Graduating 2027 · Open to full-time roles" are in both folds, with Résumé and contact buttons `[brettadams0:home-390-fold.webp]` (`index.html:7-16`). Missing: role family (the kicker only says "data & software engineering", `index.html:8`), graduation month, any sign of the projects. The right ~55% of the 1440 hero is empty (estimated from the screenshot). |
| 2 | Proof of skill | **4** | Two live demos built from the projects' own code, a case study with real CI output (`mcp/index.html:44-58`), source links on every card. Slow to reach: the first "Live demo" badge is at y=1771 at 1440 and y=2761 at 390 `[brettadams0:measure-home.json.viewport.390.key_info.shipped_work]`. |
| 3 | Project presentation | **4** | Consistent cards: screenshot, one line, chips, links; neuronet-arena spans the full row (`index.html:85-96`). The chip "13.4 food vs 1.0 for the control" needs context (`index.html:91`). The MCP card's screenshot links to GitHub, not `/mcp/` (`index.html:99`). |
| 4 | Experience presentation | **3** | Clear timeline: title, employer, dates, place, 2–4 bullets and chips (`index.html:24-77`). Of 13 bullets only "hours to minutes" (`:46`) and "over 20 employees" (`:61`) carry a measure. The section is about 2.3 screens long at 390 (estimated from measured positions, y ≈ 560 to 2496). |
| 5 | Visual craft | **4** | Coherent dark tokens, IBM Plex Sans and Mono, terminal touches (`> `, `## `, `// `, `@ `), restrained amber `[brettadams0:home-1440-fold.webp]`. It sits in the familiar dark developer-portfolio idiom. The empty half of the desktop hero and the faint grid (`site.css:231-243`) add little. |
| 6 | Interaction and motion | **4** | Only hover transitions of 0.15–0.2 s (`site.css:172`, `:436`) and smooth anchor scrolling, turned off under reduced motion (`site.css:38-42`). neuronet-arena starts paused under reduced motion (`neuronet-arena/ui/app.js:60`). |
| 7 | Navigation and IA | **3** | Four anchors plus Résumé on desktop. Under 860 px the section links are `display: none` with no menu (`site.css:859-862`) `[brettadams0:measure-home.json.viewport.390.header_links]`. Demo pages use a different header whose back link reads "brettadams0.github.io", with no résumé or contact link. |
| 8 | Mobile quality | **3** | A real one-column layout with no horizontal scroll. But: no section nav, projects 3 screens down, footer links 18 px and `.also` links 19 px tall `[brettadams0:measure-home.json.viewport.390.small_targets_24]`, CLS 0.1643 on `/neuronet-arena/`. |
| 9 | Performance | **4** | Homepage 2.5 KB of JS, LCP 744 ms throttled, CLS 0.0011 `[brettadams0:measure-home.json.perf.mobile]`. Minus: render-blocking Google Fonts CSS, 304 KB of screenshots with no `srcset`, CLS 0.1643 on the favourite demo. |
| 10 | Accessibility | **4** | 0 axe violations on `/` and `/404.html` at both sizes, a skip link (`_layouts/default.html:38`), landmarks, a 2 px amber focus ring on every Tab stop. Minus: serious violations on `/mcp/` (1) and `/mcp-surface/` (2); screen readers hear "## Experience" and "Data Analyst Intern @ Geotab". |
| 11 | Copy | **4** | Plain and specific: "Checkout is one transaction, so the last unit can't sell twice" (`index.html:145`). Weak spots: a generic kicker, duty-style Geotab bullets, cryptic result chips. |
| 12a | Persona: recruiter | **4** | A 6-second phone scan gets name, role, employer, school, graduation year, "Open to full-time roles" and a Résumé button. A 30-second skim can't find graduation month, location, work authorization or target roles, and a phone has no nav past the hero. |
| 12b | Persona: engineer | **4** | Real demos, source links, `/mcp/` with concrete security fixes (`mcp/index.html:92-150`), a lean site. Depth starts 2–3 screens down, and the homepage doesn't surface decisions such as bit-for-bit parity or snapshot checks in CI. |
| 12c | Persona: business | **3** | Plain language but few outcomes: "replaced manual reporting", "hours to minutes" (`index.html:45-46`), "over 20 employees" (`:61`). Project lines are technical. No problem-to-outcome framing. |
| 13 | Memorability | **3** | The neuronet-arena demo: forty agents evolving in the browser `[brettadams0:neuronet-arena-390-fold.webp]`. It serves the content but sits 3 screens down on a phone and behind a click. |

Against the 29 other sites ([§3.3](#33-comparison-table)), measured by comparing columns:

- **Already at the top of the set:** interaction and motion (4; no site scores higher),
  accessibility (4; no site scores higher), recruiter persona (4; only damian scores 5).
- **Furthest behind:** navigation and IA (20 of 29 score higher), memorability (20), mobile (18),
  experience (13), business persona (9).

### 2.3 Measured baseline

**Throttled mobile, 390×844, live site**

| Page | Total | JS excl. GA | CSS | Fonts | Images | Requests | LCP (element) | CLS |
|---|---|---|---|---|---|---|---|---|
| `/` | 389.6 KB | 2.5 KB | 6.1 KB | 71.8 KB | 304.0 KB | 13 | 744 ms (`p.lede`) | 0.0011 |
| `/mcp/` | 86.0 KB | 2.5 KB | 6.1 KB | 71.8 KB | 0 | 8 | 844 ms (first body `p`) | 0.0234 |
| `/neuronet-arena/` | 119.8 KB | 28.1 KB | 5.5 KB | 61.2 KB | 0 | 19 | 692 ms (`p.lede`) | **0.1643** |
| `/mcp-surface/` | 125.7 KB | 46.9 KB | 4.7 KB | 71.9 KB | 0 | 10 | 1752 ms (report text) | 0.0021 |
| `/404.html` | 72.1 KB | 2.5 KB | 6.1 KB | 61.3 KB | 0 | 7 | 592 ms (`h1`) | 0.0001 |

Source: `[brettadams0:measure-<page>.json.perf.mobile]`.

**Unthrottled desktop, 1440×900, live site**

| Page | Total | JS excl. GA | CSS | Fonts | Images | Requests | LCP (element) | CLS |
|---|---|---|---|---|---|---|---|---|
| `/` | 409.6 KB | 2.5 KB | 6.1 KB | 91.8 KB | 304.0 KB | 13 | 408 ms (first Geotab bullet) | 0.0040 |
| `/mcp/` | 106.0 KB | 2.5 KB | 6.1 KB | 91.8 KB | 0 | 8 | 480 ms (`h1`) | 0.0431 |
| `/neuronet-arena/` | 134.3 KB | 28.1 KB | 5.4 KB | 75.8 KB | 0 | 19 | 440 ms (`p.lede`) | 0.0152 |
| `/mcp-surface/` | 145.9 KB | 46.9 KB | 4.9 KB | 91.8 KB | 0 | 10 | 636 ms (report text) | 0.0010 |
| `/404.html` | 87.5 KB | 2.5 KB | 6.1 KB | 76.7 KB | 0 | 7 | 328 ms (`h1`) | 0.0001 |

**Local `jekyll build`, served gzipped by `measure.mjs`**

| Page | Mobile total | Mobile LCP | Mobile CLS | Desktop LCP | Desktop CLS |
|---|---|---|---|---|---|
| `/` | 385.2 KB | 604 ms | 0.0011 | 192 ms | 0.0034 |
| `/mcp/` | 84.4 KB | 596 ms | 0.0205 | 268 ms | 0.0423 |
| `/neuronet-arena/` | 104.2 KB | 508 ms | 0.1643 | 104 ms | 0.0198 |
| `/mcp-surface/` | 122.8 KB | 1156 ms | 0.0012 | 272 ms | 0.0010 |
| `/404.html` | 70.5 KB | 504 ms | 0.0001 | 172 ms | 0.0001 |

**axe-core 4.10.2 violations (critical / serious / moderate / minor)**

| Page | 390×844 | 1440×900 | Rules (nodes) |
|---|---|---|---|
| `/` | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 | none; 32 `color-contrast` "incomplete" at 390 (axe can't resolve the hero gradient) |
| `/mcp/` | 0 / **1** / 0 / 0 | 0 / 0 / 0 / 0 | `scrollable-region-focusable` (the `<pre>`) at 390 |
| `/neuronet-arena/` | 0 / 0 / 0 / 1 | 0 / 0 / 0 / 1 | `empty-table-header` (1) |
| `/mcp-surface/` | 0 / **2** / 0 / 0 | 0 / **2** / 0 / 0 | `color-contrast` (17: `#gutter`, `.rule-tag`), `scrollable-region-focusable` (`.term`) |
| `/404.html` | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 | none |

The pass bar for the redesign is zero serious or critical violations (§10). `/mcp/` and
`/mcp-surface/` fail it today.

**Contrast.** Every text token passes 4.5:1 on every background except `--faint` #5a6878: 3.37:1
on `--bg`, 3.25:1 on `--bg-2`, 3.08:1 on `--surface`, 2.85:1 on `--surface-2` (computed from
`assets/css/site.css:4-24` with the WCAG 2.x formula). The lowest body-text pair is `--muted` on
`--surface-2` at 5.32:1. §7 has the full table for the new tokens.

**Notes on the numbers**

- The default budget (homepage JS < 15 KB excluding GA, LCP < 2000 ms throttled, CLS < 0.05, no
  framework) **passes** on live and local `[brettadams0:measure-home.json.budget]`.
- Before any scroll, the throttled homepage transfers 85.5 KB in 8 requests
  `[brettadams0:measure-home.json.perf.mobile.initial_transfer_bytes]`. The five lazy screenshots
  (304 KB) arrive while scrolling: 78% of a full-scroll visit.
- Real visitors also download gtag.js (180,868 B), about 72 times the site's own JS. It loads
  `async`.
- `checks.js` is 41.4 KB of `/mcp-surface/`'s 46.9 KB of JS. Its LCP is report text that `app.js`
  paints after the checks load.
- The homepage is 6,346 px tall at 390 (7.5 screens) and 4,445 px at 1440 (4.9 screens)
  `[brettadams0:measure-home.json.viewport.390.page_height]`. Time to key info for today's site is
  in §4.3.

**Analytics today** (audit §4, run with `--analytics --dnt`, GA blocked):

- `gtag('config', 'G-Z3KW627PQQ')` only. `G-EBYB5MK388` is absent.
- `section_view` and `section_dwell` fire for `experience`, `projects`, `skills`, `contact`. The hero
  (no id, `index.html:4`) and `div#education` (`index.html:172`) never fire.
- `scroll_depth` 25/50/75/100, `project_click` ×18, `resume_download` ×3 (header, hero, contact),
  `contact_click` ×6 (hero and contact section).
- Nothing fires for the footer links (`_layouts/default.html:61-63`, no `data-contact`), "More on
  GitHub →" (`index.html:156`, no `data-project`) or `data-project="nhl-arena"` (no link inside).
- `/neuronet-arena/` and `/mcp-surface/` load no gtag at all.
- With Do Not Track set, 0 custom events fire. gtag's own page view still runs. That is today's
  behaviour, and it stays.

### 2.4 Screenshots

Fold shots at full resolution. Full-page shots are linked (downscaled to stay under 80 KB, so text
is soft).

**Homepage `/`.** At 1440 the hero is one column and the right side is blank apart from the faint
grid. The fold ends on the Geotab bullets and chips. At 390 the header holds only the brand and
Résumé, and "Email" wraps onto its own row.

![Homepage at 1440×900, first screen](research/baseline/home-1440-fold.webp)
![Homepage at 390×844, first screen](research/baseline/home-390-fold.webp)

Full page: [1440](research/baseline/home-1440-full.webp) · [390](research/baseline/home-390-full.webp)

**`/mcp/`.** Back link "← Brett Adams", eyebrow, a three-line `h1` at 1440 (four at 390), the fact
line, then prose. The first screen is text only; the first figure (CI output,
`mcp/index.html:44-58`) is below it.

![/mcp/ at 1440×900, first screen](research/baseline/mcp-1440-fold.webp)
![/mcp/ at 390×844, first screen](research/baseline/mcp-390-fold.webp)

Full page: [1440](research/baseline/mcp-1440-full.webp) · [390](research/baseline/mcp-390-full.webp)

**`/neuronet-arena/`.** A different header ("← brettadams0.github.io", "Source on GitHub"). The
arena runs in the first screen at both sizes, with the chart and the leader's network beside it at
1440.

![/neuronet-arena/ at 1440×900, first screen](research/baseline/neuronet-arena-1440-fold.webp)
![/neuronet-arena/ at 390×844, first screen](research/baseline/neuronet-arena-390-fold.webp)

Full page: [1440](research/baseline/neuronet-arena-1440-full.webp) · [390](research/baseline/neuronet-arena-390-full.webp)

**`/mcp-surface/`.** Editor and report side by side at 1440. At 390 the header wraps onto two rows
(back link, then "npm" and "Source on GitHub"), and the editor fills the fold.

![/mcp-surface/ at 1440×900, first screen](research/baseline/mcp-surface-1440-fold.webp)
![/mcp-surface/ at 390×844, first screen](research/baseline/mcp-surface-390-fold.webp)

Full page: [1440](research/baseline/mcp-surface-1440-full.webp) · [390](research/baseline/mcp-surface-390-full.webp)

### 2.5 Issues

Severity: **critical** blocks a core task; **high** hurts a persona's main path or fails the
accessibility pass bar; **medium** is a visible quality or measurement gap; **low** is polish.
"Upstream" means the fix belongs in the neuronet-arena repo. Numbers 1–28 match audit §5; 29 is new
in this spec, from the baseline screenshot. "Fixed in" names the spec section that specifies the fix;
§16 assigns each one to a PR.

| # | Sev. | Issue | Where | Evidence (measured) | Fixed in |
|---|---|---|---|---|---|
| 1 | High | No section navigation on phones | `assets/css/site.css:859-862` hides `.nav-section` under 860 px; `_layouts/default.html:43-49` has no menu | At 390 the header shows only "brett adams" and "Résumé"; Projects start at y=2496, 3 screens down `[brettadams0:measure-home.json.viewport.390.key_info.projects_heading]` | §5.3, §8 header/nav (`.nav-sections`) |
| 2 | High | Proof of work is below the fold at every size | Experience (`index.html:21-79`, 13 bullets) comes before Projects (`:81`) | First "Live demo" at y=1771 (1440) and y=2761 (390); nothing in either fold points to a project | §4.3 targets; §5.3 phone nav row ("Projects") and, above 860 px, §5.2's hero link to `#projects`; §4.5 and §8.4 tightened timeline |
| 3 | High | `/mcp-surface/` fails axe, 2 serious rules | `#gutter` (`mcp-surface/styles.css:98`) and `.rule-tag` (`:161`) use `--faint` on `--term` (3.25:1); `.term` scroll region not focusable (`mcp-surface/index.html:69`, `styles.css:134-137`) | 17 contrast nodes `[brettadams0:measure-mcp-surface.json.axe.1440.by_impact.serious]` | §7 `--dim`, §10, §8 demo shell |
| 4 | Medium | `/mcp/` fails axe at 390, 1 serious rule | `<pre>` (`mcp/index.html:45`, `site.css:707-716`, `overflow-x: auto`) has no `tabindex` | `[brettadams0:measure-mcp.json.axe.390.by_impact.serious]` | §10, §8 `/mcp/` template |
| 5 | Medium (upstream) | Layout shift on the favourite demo, CLS 0.1643 on mobile | Font CSS loads with `media="print" onload` (`neuronet-arena/index.html:18-20`) | When Plex replaces the fallback, `.lede` shrinks 209 → 186 px and the arena moves up 23 px; reproduces locally `[brettadams0:measure-neuronet-arena.json.perf.mobile.shifts]` | §7 metric-matched fallback; upstream PR (§16) |
| 6 | Medium | Screen readers hear the terminal prefixes | CSS `content` on `.section-title::before` (`site.css:323-326`) and `.job-org::before` (`:394-397`); same technique at `:121-124`, `:278-281`, `:683-688` | Accessible names "## Experience", "Data Analyst Intern @ Geotab" (`ariaSnapshot`) | §7 terminal touches, §10 |
| 7 | Medium | Render-blocking third-party font CSS on every default-layout page | `_layouts/default.html:31-33`, 7 faces requested, 4 files downloaded | FCP = LCP = 744 ms on `/` and 844 ms on `/mcp/`; fonts are 71.8 of 86.0 KB of `/mcp/`; desktop `/mcp/` CLS 0.0431, most likely the font swap (inferred) | §7 font loading (self-host is a Decision) |
| 8 | Medium | Oversized screenshots, no `srcset` | `index.html:87, 100, 114, 129, 142` | `mcp-servers.webp` and `mcp-surface.webp` are 2200×1376 and render at 524×294 at 1440, 4.2× the width needed; images are 304 of 389.6 KB on mobile | §7 screenshot treatment, §8 project cards |
| 9 | Medium | The desktop first screen is half empty | One-column hero (`site.css:245-247`), lede capped at 34em (`:292`) | Right ~55% of the 1440 hero blank (estimated from the screenshot) | §6 At-a-glance card, §8 hero |
| 10 | Medium | The status line leaves out what a recruiter checks | `index.html:7`, kicker `:8` | No month or term, role family, location or work authorization (fact sheet D4, D5) | §6 copy with `[BRETT: …]` placeholders, §18 |
| 11 | Medium | Demo visits are unmeasured | No gtag in `neuronet-arena/index.html:1-23` or `mcp-surface/index.html:1-21` | `[brettadams0:measure-mcp-surface.json.analytics.gtag_present]` | §13 (Decision; neuronet-arena is upstream) |
| 12 | Medium | Footer contact links aren't tracked | `_layouts/default.html:61-63`, no `data-contact` | They are the only contact links on `/mcp/` and `/404.html` `[brettadams0:measure-mcp.json.analytics.failed_clicks]` | §8 footer, §13 (Decision) |
| 13 | Medium | Demo pages don't share the site shell | Back link "brettadams0.github.io" (`neuronet-arena/index.html:28`, absolute; `mcp-surface/index.html:26`, `/`) vs "← Brett Adams" on `/mcp/` (`mcp/index.html:10`); no résumé, contact or skip link; different favicons (`neuronet-arena/index.html:15`), font sets (`mcp-surface/index.html:18`), container (1320 vs 1200 px), radius (10 vs 12 px); neuronet-arena links use the browser focus ring (`neuronet-arena/styles.css:158`) | `[brettadams0:measure-neuronet-arena.json.viewport.1440.focus_walk]` | §8 shared demo shell; neuronet-arena parts upstream |
| 14 | Medium | Experience bullets read as duties | `index.html:31-34`; only `:46` and `:61` carry a measure | — | §4.5 internship story, §8.4 timeline entry, the glance card's Results row (§8.3), `[BRETT: …]` numbers |
| 15 | Low | Small tap targets at 390 | Footer links 18 px (`site.css:832-840`), `.also` 19 px (`:546-549`), project links 24 px (`:513-521`), mcp-surface segmented buttons 27 px (`mcp-surface/styles.css:71-79`) | All pass WCAG 2.2 SC 2.5.8 by size or spacing, all well under 44 px `[brettadams0:measure-home.json.viewport.390.small_targets_24]` | §10 target sizes, §8 footer and "also" list |
| 16 | Low | `--faint` text under 4.5:1 | Site uses it only for decorative glyphs (`site.css:280`, `:417`, `:672`); demos use it for text (`neuronet-arena/styles.css:261`, upstream; mcp-surface, issue 3) | 3.37:1 on `--bg`, 2.85:1 on `--surface-2` | §7 `--dim` |
| 17 | Low | UI borders below 3:1 | `.btn` outline (`site.css:166`), card edges | `--border` 1.36:1 and `--border-strong` 1.69:1 on `--bg`. Not an SC 1.4.11 failure, since buttons are identified by text | §7 borders |
| 18 | Low | GA dominates the JS weight | `_includes/analytics.html` | gtag.js 180,868 B vs 2.5 KB of site JS; async, and excluded from the budget by definition | §11 (noted, not changed) |
| 19 | Low | Heavy social cards | `_config.yml:25` | `social-card.png` 211,878 B; demo `og.png` 144,842 B and 109,961 B; a tuned image would be well under 100 KB (estimated) | §12 |
| 20 | Low | Demo titles don't name Brett | `neuronet-arena/index.html:6`, `mcp-surface/index.html:6`; no canonical link | — | §12, §8 demo shell |
| 21 | Low | The 404 page is a dead end with generic meta | `404.html:11` offers only "Back to the homepage" | Site-default description; `canonical: /404.html` `[brettadams0:measure-404.json.page.canonical]` | §8 404 page, §12 |
| 22 | Low | JSON-LD is thin | `_layouts/default.html:11-30` | `Person` has no `image`, `knowsAbout` or `email`; `alumniOf` for a current student (fact sheet D19); `/mcp/` has only `WebPage` | §12 |
| 23 | Low | The live badge sits inside the heading | `index.html:89`, `:116` | Accessible names "neuronet-arena Live demo", "mcp-surface Live demo" | §8 Live demo badge |
| 24 | Low | Off-site first click on the MCP card | Screenshot links to the google-workspace-mcp repo (`index.html:99`), first text link to `/mcp/` (`:106`) | — | §8 standard project card |
| 25 | Low | Hero buttons wrap on phones | `index.html:11-16` | "Email" alone on a second row at 390 `[brettadams0:home-390-fold.webp]` | §6, §8 buttons |
| 26 | Low | Cryptic result chip | `index.html:91` | "13.4 food vs 1.0 for the control" needs the demo page to make sense | §8 featured project card |
| 27 | Low (upstream) | Empty table header | `neuronet-arena/index.html:118` | axe minor `empty-table-header` | Upstream PR (§16) |
| 28 | Low | mcp-surface has the site's slowest LCP | Report paints after `checks.js` (41.4 KB) loads | 1752 ms throttled `[brettadams0:measure-mcp-surface.json.perf.mobile.lcp_runs_ms]`; under 2 s | §11 (watch) |
| 29 | Low | mcp-surface header wraps onto two rows at 390 | `mcp-surface/index.html:24-32` | Back link on one row, "npm" and "Source on GitHub" on the next `[brettadams0:mcp-surface-390-fold.webp]` | §8 demo shell |

### 2.6 What works today (keep)

- **Lean, framework-free pages.** 2.5 KB of site JS on the homepage, LCP 744 ms throttled, CLS
  0.0011, default budget passes.
- **The first screen already states what a recruiter scans for:** name, current role and employer,
  school, graduation year and availability, with Résumé and contact buttons in both folds
  (`index.html:7-16`).
- **Real live demos reached from their cards,** never on the homepage (`index.html:85-125`).
  neuronet-arena works on a phone and starts paused under reduced motion.
- **Analytics work as documented:** all six events, the four `section_id` values, DNT suppresses
  every custom event `[brettadams0:measure-home.json.analytics.counts]`.
- **Accessibility basics on the homepage:** zero axe violations, skip link, landmarks, one focus ring,
  body text at 5.32:1 or better.
- **One token system across site and demos:** `--bg`, `--surface`, `--border`, `--amber`, `--teal`,
  `--red` and `--good` match in `assets/css/site.css:4-24`, `neuronet-arena/styles.css:5-27` and
  `mcp-surface/styles.css:4-22`.
- **Plain, specific copy:** one line per project, facts as chips; `/mcp/` shows real CI output and
  names each fix.
- **Correct 404 handling and indexing:** missing URLs return a real 404 with the custom page; the
  sitemap and `robots.txt` are in place.

## 3. Research findings

Twenty-nine personal sites were captured, scored on the rubric and checked by an independent
verifier. Every claim below about another site comes from a page loaded in this session and survived
verification. Each site's full entry, with fold screenshots, scores, what works and what doesn't, is
in the appendix: [`research/appendix.md`](research/appendix.md). Method, the full candidate pool and
the rubric are in [`research/README.md`](research/README.md). Verification outcomes are in
[`research/verification-log.md`](research/verification-log.md).

**How to read the citations.** `[slug:desktop-fold.webp]` is a committed fold shot in
`research/sites/<slug>/`. `[slug:record.<path>]` is a field in that site's capture record (the
committed `record.json` is a trimmed copy). `tiles/…`, `inner-desktop-fold.png` and `*-full.png` name
full-page and inner-page captures. They were kept out of git (research README, "What is not
committed"), and the verifier reloaded each cited page rather than trusting them. S-ids and X-ids are
hiring sources, listed in the [appendix](research/appendix.md#hiring-side-evidence).

### 3.1 What was done (measured counts)

- **Pool:** 79 entries from four discovery agents (77 distinct sites; henryheffernan and mitchivin
  were found twice). 29 were captured in full. The rest are listed with the reason in the README.
- **Capture:** each homepage at 1440×900 and 390×844 (fold and full page), one inner page, a
  `record.json` with performance, axe-core 4.10.2 and time to key info. Captured 2026-10-21,
  13:35–21:32 UTC.
- **Verification:** an agent that did not capture each site reloaded it and tried to disprove every
  claim. Result: 1,212 confirmed, 121 corrected, 1 removed, 0 unverifiable. Corrected claims carry
  the verifier's wording.
- **Blind re-score:** 8 of 29 sites (25%, rounded up, drawn with a fixed seed). Of 120 criterion
  pairs, none differed by 2 or more, so no tie-break ran.
- **Memory check:** for every seed site the capture agent wrote what it expected before looking, then
  compared it with the capture (`memory_check` in each score file).

### 3.2 Coverage against the brief's minimums

| Minimum (Brett's brief, §4.1) | Result | Sites |
|---|---|---|
| ≥ 8 data, analytics or ML sites | **10** | beingbrute, deshraj-jogiya, eugeneyan, mehdio, mujtabasaqib, pahasara, shaundataanalytics, ssp, wisl, plus robinlinacre (in the interactive group; their /about page says data engineer) |
| ≥ 4 DE/AE practitioners, not counting blog-first ML seeds | **9** | deshraj-jogiya, mehdio, mujtabasaqib, pahasara, shaundataanalytics, ssp, wisl, robinlinacre, beingbrute. Four are early-career by their own pages: beingbrute, deshraj-jogiya, mujtabasaqib, pahasara |
| ≥ 6 new-grad or intern portfolios that clearly worked | **7** | damian (Citadel full-time), florea (Datadog after an internship), shao (Tesla), kushalgoel (Citadel internship), richardshuai (Databricks S26, Google F26), darshshah (Wealthsimple intern; the weakest signal), henryheffernan (built for a job search, now at Vercel) |
| Software and design engineers, design-forward and minimal | met | design-forward: brittanychiang, emilkowal, rauno; minimal: antfu, shud; minimal counterexample: danluu; content-heavy: taniarascia |
| Interactive and live-demo sites | **5** | ciechanow, madebyevan, redblobgames, robinlinacre, samwho |
| Cautionary "wow" examples | met | bruno-simon, henryheffernan; also weighed: ciechanow (demo on the homepage), ssp (graph landing), mujtabasaqib (splash gate) |

### 3.3 Comparison table

Scores 1–5 per the [rubric](research/README.md#rubric). Means are **measured**: the arithmetic mean
of the integer scores. No cell is tie-broken. Source: [`research/scores.csv`](research/scores.csv),
generated from the verified score files. Brett's row is from §2.2.

| Site | Group | First screen | Proof | Projects | Experience | Craft | Motion | Nav/IA | Mobile | Perf | A11y | Copy | Recruiter | Engineer | Business | Memorable | Mean |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| [beingbrute](research/appendix.md#beingbrute) | data | 4 | 5 | 5 | 4 | 4 | 4 | 3 | 3 | 3 | 4 | 5 | 4 | 5 | 5 | 4 | 4.13 |
| [deshraj-jogiya](research/appendix.md#deshraj-jogiya) | data | 4 | 5 | 4 | 4 | 3 | 3 | 3 | 4 | 4 | 3 | 3 | 4 | 4 | 3 | 4 | 3.67 |
| [eugeneyan](research/appendix.md#eugeneyan) | data | 4 | 4 | 4 | 3 | 3 | 3 | 4 | 3 | 3 | 4 | 4 | 3 | 4 | 4 | 4 | 3.60 |
| [mehdio](research/appendix.md#mehdio) | data | 3 | 4 | 5 | 4 | 4 | 2 | 4 | 4 | 4 | 3 | 4 | 3 | 4 | 4 | 4 | 3.73 |
| [mujtabasaqib](research/appendix.md#mujtabasaqib) | data | 2 | 3 | 2 | 4 | 3 | 1 | 4 | 2 | 1 | 3 | 2 | 2 | 2 | 2 | 2 | 2.33 |
| [pahasara](research/appendix.md#pahasara) | data | 3 | 4 | 4 | 3 | 4 | 3 | 4 | 3 | 3 | 2 | 3 | 3 | 3 | 3 | 3 | 3.20 |
| [shaundataanalytics](research/appendix.md#shaundataanalytics) | data | 3 | 4 | 3 | 4 | 3 | 3 | 3 | 4 | 4 | 4 | 2 | 2 | 3 | 3 | 3 | 3.20 |
| [ssp](research/appendix.md#ssp) | data | 4 | 3 | 2 | 2 | 4 | 4 | 4 | 4 | 4 | 3 | 3 | 2 | 4 | 3 | 4 | 3.33 |
| [wisl](research/appendix.md#wisl) | data | 5 | 5 | 4 | 3 | 5 | 4 | 5 | 4 | 4 | 3 | 4 | 3 | 5 | 3 | 4 | 4.07 |
| **Mean, data (9)** | | 3.56 | 4.11 | 3.67 | 3.44 | 3.67 | 3.00 | 3.78 | 3.44 | 3.33 | 3.22 | 3.33 | 2.89 | 3.78 | 3.33 | 3.56 | **3.47** |
| [damian](research/appendix.md#damian) | new-grad | 5 | 3 | 1 | 4 | 3 | 3 | 4 | 3 | 4 | 2 | 4 | 5 | 2 | 4 | 4 | 3.40 |
| [darshshah](research/appendix.md#darshshah) | new-grad | 4 | 3 | 2 | 4 | 3 | 3 | 4 | 4 | 5 | 2 | 3 | 3 | 3 | 2 | 3 | 3.20 |
| [florea](research/appendix.md#florea) | new-grad | 3 | 3 | 3 | 4 | 2 | 3 | 4 | 3 | 3 | 2 | 4 | 4 | 3 | 4 | 3 | 3.20 |
| [kushalgoel](research/appendix.md#kushalgoel) | new-grad | 4 | 3 | 3 | 5 | 3 | 3 | 4 | 4 | 4 | 3 | 4 | 4 | 3 | 3 | 3 | 3.53 |
| [richardshuai](research/appendix.md#richardshuai) | new-grad | 4 | 3 | 4 | 4 | 4 | 2 | 4 | 4 | 3 | 4 | 3 | 4 | 3 | 4 | 5 | 3.67 |
| [shao](research/appendix.md#shao) | new-grad | 4 | 4 | 3 | 3 | 4 | 3 | 3 | 4 | 3 | 4 | 4 | 4 | 4 | 4 | 4 | 3.67 |
| **Mean, new-grad (6)** | | 4.00 | 3.17 | 2.67 | 4.00 | 3.17 | 2.83 | 3.83 | 3.67 | 3.67 | 2.83 | 3.67 | 4.00 | 3.00 | 3.50 | 3.67 | **3.44** |
| [antfu](research/appendix.md#antfu) | software/design | 4 | 5 | 4 | 2 | 4 | 3 | 3 | 4 | 4 | 3 | 4 | 3 | 5 | 3 | 4 | 3.67 |
| [brittanychiang](research/appendix.md#brittanychiang) | software/design | 4 | 4 | 4 | 4 | 4 | 4 | 4 | 4 | 3 | 4 | 4 | 4 | 4 | 4 | 4 | 3.93 |
| [danluu](research/appendix.md#danluu) | software/design | 1 | 3 | 1 | 1 | 1 | 3 | 2 | 2 | 5 | 3 | 5 | 1 | 4 | 2 | 3 | 2.47 |
| [emilkowal](research/appendix.md#emilkowal) | software/design | 4 | 5 | 3 | 2 | 4 | 4 | 4 | 5 | 3 | 4 | 5 | 2 | 4 | 3 | 4 | 3.73 |
| [rauno](research/appendix.md#rauno) | software/design | 3 | 3 | 3 | 2 | 5 | 3 | 2 | 2 | 4 | 2 | 3 | 2 | 3 | 2 | 4 | 2.87 |
| [shud](research/appendix.md#shud) | software/design | 4 | 4 | 3 | 4 | 4 | 3 | 4 | 4 | 4 | 2 | 4 | 3 | 4 | 3 | 4 | 3.60 |
| [taniarascia](research/appendix.md#taniarascia) | software/design | 4 | 4 | 4 | 4 | 4 | 4 | 4 | 4 | 4 | 3 | 5 | 4 | 5 | 3 | 5 | 4.07 |
| **Mean, software/design (7)** | | 3.43 | 4.00 | 3.14 | 2.71 | 3.71 | 3.43 | 3.29 | 3.57 | 3.86 | 3.00 | 4.29 | 2.71 | 4.14 | 2.86 | 4.00 | **3.48** |
| [ciechanow](research/appendix.md#ciechanow) | interactive | 2 | 5 | 2 | 1 | 5 | 4 | 4 | 4 | 3 | 3 | 5 | 1 | 4 | 2 | 5 | 3.33 |
| [madebyevan](research/appendix.md#madebyevan) | interactive | 4 | 5 | 4 | 3 | 3 | 4 | 4 | 4 | 3 | 4 | 5 | 2 | 5 | 4 | 5 | 3.93 |
| [redblobgames](research/appendix.md#redblobgames) | interactive | 4 | 5 | 4 | 2 | 4 | 4 | 4 | 4 | 4 | 3 | 5 | 2 | 5 | 3 | 5 | 3.87 |
| [robinlinacre](research/appendix.md#robinlinacre) | interactive | 2 | 4 | 2 | 2 | 3 | 4 | 4 | 3 | 4 | 3 | 5 | 2 | 5 | 3 | 4 | 3.33 |
| [samwho](research/appendix.md#samwho) | interactive | 3 | 5 | 4 | 2 | 4 | 4 | 4 | 4 | 5 | 3 | 5 | 2 | 5 | 3 | 5 | 3.87 |
| **Mean, interactive (5)** | | 3.00 | 4.80 | 3.20 | 2.00 | 3.80 | 4.00 | 4.00 | 3.80 | 3.80 | 3.20 | 5.00 | 1.80 | 4.80 | 3.00 | 4.80 | **3.67** |
| [bruno-simon](research/appendix.md#bruno-simon) | cautionary | 1 | 2 | 1 | 1 | 4 | 1 | 1 | 2 | 1 | 1 | 3 | 1 | 3 | 1 | 3 | 1.73 |
| [henryheffernan](research/appendix.md#henryheffernan) | cautionary | 2 | 3 | 2 | 2 | 5 | 1 | 2 | 1 | 2 | 2 | 3 | 1 | 3 | 1 | 3 | 2.20 |
| **Mean, cautionary (2)** | | 1.50 | 2.50 | 1.50 | 1.50 | 4.50 | 1.00 | 1.50 | 1.50 | 1.50 | 1.50 | 3.00 | 1.00 | 3.00 | 1.00 | 3.00 | **1.97** |
| **Mean, all 29** | | 3.38 | 3.90 | 3.10 | 3.00 | 3.66 | 3.10 | 3.55 | 3.45 | 3.48 | 2.97 | 3.90 | 2.76 | 3.83 | 3.03 | 3.86 | **3.40** |
| **[brettadams0 (current)](research/baseline/audit.md)** | self | 4 | 4 | 4 | 3 | 4 | 4 | 3 | 3 | 4 | 4 | 4 | 4 | 4 | 3 | 3 | **3.67** |

**What the table says** (measured by comparing columns):

- **Brett is joint 10th of 30.** Nine sites score higher: beingbrute, taniarascia, wisl,
  brittanychiang, madebyevan, redblobgames, samwho, emilkowal, mehdio.
- **The sites that impress engineers mostly fail the recruiter.** The interactive group has the
  highest engineer mean (4.80) and the lowest recruiter mean outside the cautionary pair (1.80).
  New-grad sites have the highest recruiter mean (4.00).
- **Only three sites score 4 or more for all three personas:** beingbrute (4, 5, 5), brittanychiang
  (4, 4, 4) and shao (4, 4, 4). Two of them are early-career. Brett is 4, 4, 3: the business persona
  is the gap.
- **Brett's weak columns** are navigation, mobile, memorability and experience (§2.2). Those match
  audit issues 1, 2 and 14.

### 3.4 Patterns to adopt

Each pattern is seen on 3 or more sites, with verified claims, and is checked against Brett's
position in the sources (graduating 2027 and "Open to full-time roles", `index.html:7`; kicker "data &
software engineering", `:8`) and the standing rules (nothing
live on the homepage, each project once, one line per project, facts as chips, dark only, Plex, no
blog). Site counts are **measured** from the verified files. "Adopt as" points to the spec section
that builds it. Patterns Brett already follows are marked **keep**.

#### For the recruiter's 6-second scan

**P1. The first screen works as a résumé header.** Current title, employer and level in one or two
plain sentences under the name, in HTML, at both sizes.
- Sites (9): [brittanychiang](research/appendix.md#brittanychiang), [damian](research/appendix.md#damian),
  [darshshah](research/appendix.md#darshshah), [deshraj-jogiya](research/appendix.md#deshraj-jogiya),
  [richardshuai](research/appendix.md#richardshuai), [taniarascia](research/appendix.md#taniarascia),
  [wisl](research/appendix.md#wisl), [shao](research/appendix.md#shao), [emilkowal](research/appendix.md#emilkowal).
  damian puts graduation class, title, employer, team and city in the first two sentences
  `[damian:desktop-fold.webp]`; shao gives graduation year and school in one sentence, with the
  employer on its own line `[shao:mobile-fold.webp]`. Current role is at 0 scrolls and 0 clicks on 17
  of the 26 sites that show it, at both sizes.
- Evidence: the first pass reads current title and employer, then the previous role, then dates (S02,
  S01; Tier 1). "A short, one phrase summary" helps (S14).
- Adopt as: hero status, `h1`, lede and the At-a-glance card (§6, §8 hero). Brett already has most
  of it (audit §2 #1); missing are the role family and the graduation term (`[BRETT: …]`).

**P2. Availability, location and target role sit beside the résumé button.**
- Sites (4): [beingbrute](research/appendix.md#beingbrute) (location, relocation, availability next
  to the résumé button `[beingbrute:desktop-fold.webp]`), [deshraj-jogiya](research/appendix.md#deshraj-jogiya)
  (title, city, relocation, roles wanted `[deshraj-jogiya:mobile-fold.webp]`),
  [richardshuai](research/appendix.md#richardshuai) ("Seeking 2027 New Grad Roles"
  `[richardshuai:mobile-fold.webp]`), [mujtabasaqib](research/appendix.md#mujtabasaqib) (status chips
  `[mujtabasaqib:mobile-fold.webp]`).
- Evidence: right to work and location are checked before skills (S11, S13; Tier 2, forum only).
- Adopt as: the status line, which already gives availability ("Graduating 2027 · Open to full-time
  roles", `index.html:7`), and the At-a-glance card's "Looking for" and "Based in" rows. Those two rows
  are added only once Brett answers (§18 #31–32). Each missing fact is a placeholder:
  `[BRETT: graduation month 2027]`, `[BRETT: target roles wording]`,
  `[BRETT: where based / relocation]`, `[BRETT: work authorization]`.

**P3. The résumé is one tap away on the phone's first screen.** — **keep**
- Sites (6 with `resume-in-hero`): [beingbrute](research/appendix.md#beingbrute), [damian](research/appendix.md#damian),
  [deshraj-jogiya](research/appendix.md#deshraj-jogiya), [florea](research/appendix.md#florea),
  [kushalgoel](research/appendix.md#kushalgoel), [pahasara](research/appendix.md#pahasara). damian
  uses a plain text "résumé" link in the top row `[damian:mobile-fold.webp]`.
- Evidence: the résumé is the gate; 81% of hiring managers screen on it first (S03, Tier 1).
- Adopt as: header Résumé button and hero primary button. The path stays
  `/assets/resume/Resume_BrettAdams.pdf` with no query or fragment (`resume_download`).

**P4. A short text nav that fits a 390 px phone, with no hamburger.**
- Sites (4): [wisl](research/appendix.md#wisl) (three items, identical on mobile
  `[wisl:mobile-fold.webp]`), [shud](research/appendix.md#shud) (four items `[shud:mobile-fold.webp]`),
  [eugeneyan](research/appendix.md#eugeneyan) (five labels, but the row is wider than the phone, so
  the page lays out at 453 px `[eugeneyan:record.home.mobile.dom.vw]`),
  [richardshuai](research/appendix.md#richardshuai) (four anchors, but 16 px tall on mobile
  `[richardshuai:record.home.mobile.dom.a11y_manual.small_target_examples]`).
- Evidence: none directly; mobile is an evidence gap, so this is a hedge.
- Adopt as: the `.nav-sections` row at ≤ 860 px (§5.3, §8 header/nav). Labels must fit without
  widening the layout, and each target is ≥ 44 px tall. Fixes audit issue 1.

**P7. Internships and co-ops are shown as real roles, with the term visible.**
- Sites (4): [brittanychiang](research/appendix.md#brittanychiang) (co-ops in the same timeline as
  senior roles `[brittanychiang:tiles/m-01.png]`), [florea](research/appendix.md#florea) (intern and
  full-time entries at one company listed together `[florea:tiles/d-exp.png]`),
  [richardshuai](research/appendix.md#richardshuai) (term codes `[richardshuai:mobile-fold.webp]`),
  [damian](research/appendix.md#damian) ("8 internships" `[damian:desktop-fold.webp]`).
- Evidence: an internship in the employer's industry is the most influential tie-breaker (S04,
  Tier 1).
- Adopt as: the timeline entry (§8), with "Co-op" as written in the sources. The 2023 CIMCO title is
  `[BRETT: …]` (fact sheet §10).

**P14. Contact is written out as text, not only icons.** — **keep**
- Sites (5): [florea](research/appendix.md#florea) (email, LinkedIn, GitHub as text by the intro
  `[florea:desktop-fold.webp]`), [antfu](research/appendix.md#antfu) (labels next to icons, email
  written out `[antfu:desktop-fold.webp]`), [shao](research/appendix.md#shao) (full mailto at 0
  scrolls on a phone `[shao:record.home.mobile.dom.features.contact_links]`),
  [redblobgames](research/appendix.md#redblobgames) (email in the nav
  `[redblobgames:record.home.desktop.dom.nav]`), [beingbrute](research/appendix.md#beingbrute)
  (`[beingbrute:tiles/d-06.png]`, though at the very bottom on mobile).
- Adopt as: hero buttons labelled Email, LinkedIn and GitHub in one row at 390 (fixes issue 25), the
  address as text in `#contact`, footer links with `data-contact` (a Decision; issue 12).

#### For the engineer and the hiring manager's second look

**P5. Proof of work is one step from the first screen.**
- Sites: the set's median to shipped work is 1 step (scrolls plus clicks) at both sizes; at 390, 12
  sites reach it at 0 scrolls and ≤ 1 click (§4.3). [beingbrute](research/appendix.md#beingbrute)
  links four headline results to their case studies `[beingbrute:probe-home-links.json]`;
  [antfu](research/appendix.md#antfu) shows "Creator of" and "Core team of" rows with project chips
  `[antfu:desktop-fold.webp]`; 4 sites carry `selected-work-first` ([emilkowal](research/appendix.md#emilkowal),
  [madebyevan](research/appendix.md#madebyevan), [pahasara](research/appendix.md#pahasara),
  [redblobgames](research/appendix.md#redblobgames)).
- Evidence: hiring managers weigh proven work above credentials (S03, Tier 1); for new grads the
  internship is the strongest tie-breaker (S04, Tier 1), which is why Experience stays first.
- Adopt as: the phone nav row's link to `#projects`, a plain text hero link "Projects and live
  demos ↓" above 860 px (hidden at ≤ 860 px, where the nav row has Projects), and a tighter Experience
  section (§4.3, §5.2–5.3). Not a hero demo or project image: the standing rules forbid it.

**P6. Experience as compact dated blocks with chips and one outcome line.**
- Sites (7): [brittanychiang](research/appendix.md#brittanychiang) (date range, "Role · Company",
  short summary, chips `[brittanychiang:tiles/m-01.png]`), [deshraj-jogiya](research/appendix.md#deshraj-jogiya)
  (engagement type in a pill `[deshraj-jogiya:tiles/d-05.png]`), [darshshah](research/appendix.md#darshshah)
  (full tool names a recruiter can match `[darshshah:desktop-fold.webp]`), [mujtabasaqib](research/appendix.md#mujtabasaqib),
  [shaundataanalytics](research/appendix.md#shaundataanalytics), [richardshuai](research/appendix.md#richardshuai),
  [kushalgoel](research/appendix.md#kushalgoel) (most bullets carry a number and a named tool
  `[kushalgoel:tiles/m-01.png]`).
- Evidence: recruiters check dates for progression (S02, Tier 1); "short, with numbers, and include
  the impact not the work" (S09, Tier 2).
- Adopt as: the timeline entry (§8), the internship story (§4.5) and the At-a-glance card's Results
  row (§8.3). Only "over 20 employees" is a number today (fact sheet §5.3); every other outcome number
  is `[BRETT: …]`.

**P8. Live demos run on their own page, reached from a static card or link.** — **keep** (standing rule)
- Sites (6): [redblobgames](research/appendix.md#redblobgames) (a homepage with no JS; each demo on
  its own page `[redblobgames:record.inner.desktop.network_initial]`), [madebyevan](research/appendix.md#madebyevan)
  `[madebyevan:tiles/d-00.png]`, [samwho](research/appendix.md#samwho)
  `[samwho:record.home.desktop.network_initial]`, [emilkowal](research/appendix.md#emilkowal),
  [robinlinacre](research/appendix.md#robinlinacre) `[robinlinacre:inner-desktop-fold.png]`,
  [deshraj-jogiya](research/appendix.md#deshraj-jogiya).
- Counter-evidence: ciechanow, bruno-simon and henryheffernan run live code on the landing page (A5).
- Adopt as: unchanged rule; every demo card in §8 links to a page that runs.

**P9. One instruction sentence sits directly above each demo control.**
- Sites (4): [ciechanow](research/appendix.md#ciechanow) `[ciechanow:desktop-fold.webp]`,
  [redblobgames](research/appendix.md#redblobgames) `[redblobgames:inner-desktop-fold.png]`,
  [samwho](research/appendix.md#samwho) `[samwho:tiles/id-02.png]`,
  [madebyevan](research/appendix.md#madebyevan) (controls as short bullets beside the canvas
  `[madebyevan:inner-desktop-fold.png]`).
- Adopt as: the "How it works" block of the shared demo shell (§8). Copy only. For neuronet-arena it
  is an upstream change.

**P10. The demo page says how it was built, uses real data, and links the source.**
- Sites (5): [robinlinacre](research/appendix.md#robinlinacre) (two sentences on the build, a source
  link, then the working tool `[robinlinacre:inner-desktop-fold.png]`), [redblobgames](research/appendix.md#redblobgames)
  (a "Meta" section with build notes and the licence `[redblobgames:tiles/d-02.png]`),
  [emilkowal](research/appendix.md#emilkowal) `[emilkowal:tiles/id-00.png]`,
  [wisl](research/appendix.md#wisl) (live CI, coverage and version badges `[wisl:inner-desktop-fold.png]`),
  [bruno-simon](research/appendix.md#bruno-simon) (a hidden panel linking the MIT source
  `[bruno-simon:index-html.txt]`).
- Evidence: the hiring manager checks GitHub "to verify your technical taste" (S06, named); "I am
  going to click in to start viewing code" (S09).
- Adopt as: the shared demo shell (§8) for `/mcp-surface/`, `/mazefinder/` and `/border-waits/`;
  an upstream PR for neuronet-arena. Fixes audit issue 13.

**P11. Case studies follow one outline from problem to result.** Who it is for and the question,
data, how it works with a diagram, problems and decisions, testing, results with numbers, limits,
stack.
- Sites (4): [deshraj-jogiya](research/appendix.md#deshraj-jogiya) (that outline, opening with a
  dated "For:" line `[deshraj-jogiya:record.inner.desktop.dom.headings]`),
  [shaundataanalytics](research/appendix.md#shaundataanalytics) (business problem before the
  architecture `[shaundataanalytics:record.inner.desktop.dom.headings]`),
  [beingbrute](research/appendix.md#beingbrute) (question, dataset, findings with caveats, a negative
  result reported `[beingbrute:tiles/d-02.png]`), [shao](research/appendix.md#shao) (an internship
  write-up with an Impact section `[shao:tiles/id-03.png]`).
- Evidence: Monzo's call asks for problem, impact, trade-offs, downstream use and your own part (S07,
  named); "No diagram means no project" (S06, named).
- Adopt as: the `/mcp/` template (§8), and a three-line version on demo pages. Not a reason to add
  posts: `/mcp/` stays the only long-form page.

**P12. Project cards: a still visual, one line, facts, and links named by what you get.**
- Sites: 7 carry `project-cards-with-visuals`, for example [mehdio](research/appendix.md#mehdio)
  `[mehdio:tiles/id-00.png]`, [richardshuai](research/appendix.md#richardshuai)
  `[richardshuai:tiles/m-07.png]`, [pahasara](research/appendix.md#pahasara)
  `[pahasara:desktop-fold.webp]`. Links named by destination on 5:
  [madebyevan](research/appendix.md#madebyevan) ("View demo", "Use app" `[madebyevan:tiles/d-00.png]`),
  [taniarascia](research/appendix.md#taniarascia) `[taniarascia:inner-desktop-fold.png]`,
  [eugeneyan](research/appendix.md#eugeneyan) `[eugeneyan:inner-desktop-fold.png]`,
  [rauno](research/appendix.md#rauno) `[rauno:inner-desktop-fold.png]`,
  [shaundataanalytics](research/appendix.md#shaundataanalytics) `[shaundataanalytics:inner-desktop-fold.png]`.
- Adopt as: featured, standard and wide cards with the `.project-kind` line (§8). Already mostly true
  (audit §2 #3); fixes issues 23, 24 and 26.

**P13. Numbers are verifiable: each links to, or sits beside, its source.**
- Sites (6): [beingbrute](research/appendix.md#beingbrute) `[beingbrute:probe-home-links.json]`,
  [eugeneyan](research/appendix.md#eugeneyan) `[eugeneyan:tiles/d-01.png]`,
  [taniarascia](research/appendix.md#taniarascia) `[taniarascia:record.summary.hero_text]`,
  [mehdio](research/appendix.md#mehdio) `[mehdio:desktop-fold.webp]`,
  [brittanychiang](research/appendix.md#brittanychiang) `[brittanychiang:tiles/d-01.png]`,
  [wisl](research/appendix.md#wisl) `[wisl:inner-desktop-fold.png]`.
- Evidence: "I strongly caution against inflating your work experience… they'll know" (S10);
  "Tell me which metric your work moves" (S06).
- Adopt as: every number chip in §8 cites a source file. Open disagreements stay decisions: 2
  featured servers and 35 tools vs 5 servers and 69 tools (fact sheet D1), "200+" vs "250+" tests
  (D14), "19 checks" (D17).

**P15. A static, near-zero-JS homepage; weight only where it is needed.** — **keep**
- Sites: 8 of 29 homepages ship under 100 KB of JS; 21 of 29 transfer under 1 MB.
  [redblobgames](research/appendix.md#redblobgames) (0 KB JS homepage, 73 KB demo page
  `[redblobgames:record.inner.desktop.network_initial]`), [madebyevan](research/appendix.md#madebyevan)
  `[madebyevan:record.summary.js_bytes_desktop]`, [samwho](research/appendix.md#samwho) (4.4 KB JS
  `[samwho:record.home.desktop.network_initial]`), [beingbrute](research/appendix.md#beingbrute),
  [danluu](research/appendix.md#danluu).
- Caution: light pages still had slow reloads; wisl's LCP was held back by a fade-in paragraph
  `[wisl:record.summary.lcp_ms_desktop]`. Never fade in the LCP element.
- Adopt as: §11 budget. Remaining work: render-blocking font CSS (issue 7), `srcset` (issue 8).

**P16. Accessibility basics: skip link, visible focus, named controls, alt text.** — **keep**
- Sites (6): [brittanychiang](research/appendix.md#brittanychiang)
  `[brittanychiang:record.home.mobile.dom.a11y_manual]`, [shao](research/appendix.md#shao),
  [florea](research/appendix.md#florea), [samwho](research/appendix.md#samwho) (essay pages only),
  [beingbrute](research/appendix.md#beingbrute) `[beingbrute:record.home.desktop.focus_check]`,
  [mujtabasaqib](research/appendix.md#mujtabasaqib).
- How rare a clean pass is: only 2 of 29 homepages had zero serious and critical axe violations at
  both sizes (emilkowal, and henryheffernan, whose landing has almost nothing on it). Brett's
  homepage is clean too.
- Adopt as: §10. Fix `/mcp/` and `/mcp-surface/` (issues 3, 4) and the spoken prefixes (issue 6).

**P17. Motion waits for the visitor.**
- Sites (3): [ciechanow](research/appendix.md#ciechanow) (simulations behind a play button
  `[ciechanow:tiles/d-03.png]`), [redblobgames](research/appendix.md#redblobgames) (Start button and
  scrubber `[redblobgames:tiles/id-01.png]`), [samwho](research/appendix.md#samwho) (reader-driven
  widgets `[samwho:tiles/id-02.png]`).
- Counter-evidence: madebyevan's demo animates on load with no reduced-motion handling
  `[madebyevan:record.inner.desktop.code_scan]`.
- Adopt as: §9. neuronet-arena already starts paused under reduced motion.

**P18. Dates show the page is current, and are kept true.**
- Sites (6): [redblobgames](research/appendix.md#redblobgames), [ciechanow](research/appendix.md#ciechanow),
  [samwho](research/appendix.md#samwho) (dates under titles), [darshshah](research/appendix.md#darshshah)
  ("Accurate as of" footer `[darshshah:tiles/d-00.png]`), [kushalgoel](research/appendix.md#kushalgoel)
  ("Last updated" `[kushalgoel:tiles/d-02.png]`), [eugeneyan](research/appendix.md#eugeneyan)
  (date column `[eugeneyan:desktop-fold.webp]`).
- Caution: darshshah shows a "Present" role with a frozen "3 mos" duration ten months after its
  footer date `[darshshah:mobile-fold.webp]`. A stale date is worse than none.
- Adopt as: an "Updated" chip on `/mcp/` and demo pages; a freshness line on `/border-waits/` (§14).
  "Present" on Geotab stays true only with `[BRETT: Geotab end date]`.

#### For the first impression at 1440 and memorability

**P19. A two-column desktop first screen.** *An idea, not a pattern.*
- Sites (2 independent, 3 tagged): [brittanychiang](research/appendix.md#brittanychiang) (sticky
  identity column beside a dated timeline `[brittanychiang:desktop-fold.webp]`),
  [ssp](research/appendix.md#ssp) `[ssp:desktop-fold.webp]`. The third tagged site,
  [kushalgoel](research/appendix.md#kushalgoel), is a credited near-copy of brittanychiang
  `[kushalgoel:tiles/d-02.png]` (A23), so it is not an independent example. That is below the 3-site
  rule, so P19 counts as an idea. It keeps its id here because §6 and §8.3 cite it.
- What the build rests on: audit issue 9. The right ~55% of the 1440 hero is empty today (estimated
  from the screenshot).
- Adopt as: the hero grid with the static At-a-glance card on the right (§6). Fixes issue 9. The right
  column holds no project image or demo still: that would bring back the removed hero preview.

**P20. A type-led identity: dark palette, one accent, Mono for metadata.** — **keep**
- Sites: 7 carry `monospace-accents`, 5 are dark-only, including beingbrute (mean 4.13) and
  brittanychiang (3.93). [ssp](research/appendix.md#ssp) `[ssp:tiles/d-00.png]`,
  [wisl](research/appendix.md#wisl) (tool names as code chips `[wisl:desktop-fold.webp]`),
  [beingbrute](research/appendix.md#beingbrute) (IBM Plex Mono for figures
  `[beingbrute:record.summary.fonts]`), [eugeneyan](research/appendix.md#eugeneyan).
- Counter-evidence: damian sets all body text in a monospace face, which stretches a short bio to
  nearly three phone screens `[damian:mobile-fold.webp]`.
- Adopt as: §7. Keep Plex Mono for kickers, dates and chips; body stays Plex Sans.

### 3.5 Ideas (seen on 1 or 2 sites)

Fewer than 3 sites show these, so they are **ideas, not patterns**. P19 (§3.4) is an idea too, with
two independent sites. It stays in §3.4 under its id because other sections cite it.

| # | Idea | Sites | Use it? |
|---|---|---|---|
| I1 | Skills list holds only what is demonstrated, plus a skills-by-project matrix | [beingbrute](research/appendix.md#beingbrute) `[beingbrute:tiles/d-05.png]` | **Lightly:** a skill chip may link to the role or project that shows it |
| I2 | Demo states what it needs before it loads, with a fallback | [madebyevan](research/appendix.md#madebyevan) `[madebyevan:inner-desktop-fold.png]`, [henryheffernan](research/appendix.md#henryheffernan) `[henryheffernan:mobile-fold.webp]` | **Yes**, for heavy engines on their own pages (MazeFinder Wasm, DuckDB-Wasm). No site states a download size |
| I3 | A dated "For:" line naming the user and the question | [deshraj-jogiya](research/appendix.md#deshraj-jogiya) `[deshraj-jogiya:inner-desktop-fold.png]` | **Yes**, on `/mcp/` and the border-waits page |
| I4 | A "previously" list, one phrase per employer, under the current role | [shao](research/appendix.md#shao) `[shao:desktop-fold.webp]`; [taniarascia](research/appendix.md#taniarascia) `[taniarascia:desktop-fold.webp]` | **Yes:** the "Before" row of the At-a-glance card |
| I5 | Season or term codes on roles (S26, F26) | [richardshuai](research/appendix.md#richardshuai) `[richardshuai:desktop-fold.webp]` | Maybe; Brett's terms read better as date ranges |
| I6 | Live CI, coverage and version badges on project pages | [wisl](research/appendix.md#wisl) `[wisl:inner-desktop-fold.png]` | **Yes**, on demo and project pages where the repo has CI |
| I7 | A first-person "my part" line on team projects | [shud](research/appendix.md#shud) `[shud:inner-desktop-fold.png]` | Only if a project was team work: `[BRETT: …]` |
| I8 | Link to an employer's blog post about the intern's work | [florea](research/appendix.md#florea) `[florea:tiles/d-exp.png]` | Only if one exists and may be linked: `[BRETT: …]` |
| I9 | Previous / next links at the foot of each project page | [pahasara](research/appendix.md#pahasara) `[pahasara:tiles/id-mid.png]` | **Yes**, in the shared demo shell |
| I10 | An archive table for the long tail of work | [brittanychiang](research/appendix.md#brittanychiang) `[brittanychiang:inner-desktop-fold.png]` | Not needed at 7 projects |
| I11 | Prose words coloured to match the widget objects | [ciechanow](research/appendix.md#ciechanow) `[ciechanow:tiles/d-03.png]` | Maybe on demo pages, never colour alone |
| I12 | A prerequisite call-out at the top of a demo | [samwho](research/appendix.md#samwho) `[samwho:inner-desktop-fold.png]` | Low; upstream for neuronet-arena |
| I13 | A "safe to click" live instance | [deshraj-jogiya](research/appendix.md#deshraj-jogiya) `[deshraj-jogiya:record.home.desktop.dom.features.demo_links]` | True of mcp-surface already; say it in one line |
| I14 | An output tally linking to proof | [eugeneyan](research/appendix.md#eugeneyan) `[eugeneyan:tiles/d-01.png]` | **No:** needs volume, risks reading as vanity |
| I15 | A recommendation call-out closing each case study | [beingbrute](research/appendix.md#beingbrute) `[beingbrute:tiles/d-02.png]` | Low |
| I16 | A command palette | [mehdio](research/appendix.md#mehdio) `[mehdio:desktop-fold.webp]` | **No:** JS weight for a five-page site |
| I17 | Topic filter chips over lists | [robinlinacre](research/appendix.md#robinlinacre), [shaundataanalytics](research/appendix.md#shaundataanalytics) | **No:** too few items |
| I18 | Older site versions kept as links | [rauno](research/appendix.md#rauno) `[rauno:tiles/m-overview.png]` | No |
| I19 | Webring footer | [damian](research/appendix.md#damian), [shao](research/appendix.md#shao) | No, unless Brett belongs to one |
| I20 | An honest "limits" note on each project | [deshraj-jogiya](research/appendix.md#deshraj-jogiya), [beingbrute](research/appendix.md#beingbrute) | **Yes:** matches MazeFinder's "Honest scope" and the contradicted-claims rule; folded into P11 |

### 3.6 Anti-patterns

The count is the number of sites with a verified claim or measured field showing it. "Brett today"
says whether the current site has it (from §2).

| # | Anti-pattern | Count and evidence | Who it hurts | Brett today → rule in this spec |
|---|---|---|---|---|
| A1 | No résumé on the site | **19 of 29**, including student sites darshshah `[darshshah:record.summary.has_resume_link]`, shao `[shao:record.summary.has_resume_link]`, mujtabasaqib, richardshuai | Recruiter (S03) | Has one → keep it in the header and hero |
| A2 | Résumé buried or mislabelled | **5:** brittanychiang (end of the experience list), taniarascia (in the menu on mobile), mehdio (two clicks), henryheffernan (4 clicks), florea (a link labelled "here" to Google Drive `[florea:desktop-fold.webp]`) | Recruiter (S01, S02) | No → label stays "Résumé" |
| A3 | No navigation on phones | **4** plus Brett: beingbrute `[beingbrute:record.home.mobile.dom.menu_button]`, brittanychiang, kushalgoel, shaundataanalytics | Recruiter and engineer on a phone | **Yes (issue 1)** → `.nav-sections` row |
| A4 | Splash intro or click gate | **3:** bruno-simon (no readable text at 3–35 s `[bruno-simon:fold-30s-probe.json]`), henryheffernan (two click gates, ~32 s to a role `[henryheffernan:click-through-probe.json]`), mujtabasaqib (LCP 8.7 s `[mujtabasaqib:record.home.desktop.splash_check]`) | Recruiter (S02) | No → none, ever |
| A5 | Live WebGL or a running demo on the landing page | **3:** ciechanow (120 canvases, 7.08 MB `[ciechanow:record.home.desktop.network_initial]`), bruno-simon (1.23 MB JS), henryheffernan (4.8 MB) | Recruiter on a phone; breaks the standing rule | No → nothing live on the homepage |
| A6 | Scroll-jacking or scroll-driven reveals | **2:** rauno (wheel moves a sideways strip `[rauno:tiles/d-overview.png]`), richardshuai (a 250dvh hero; projects and contact blank in the full shot `[richardshuai:tiles/d-06.png]`) | All | No → §9 forbids it |
| A7 | Autoplaying media | **2:** rauno (/craft autoplays 68 muted videos, 24–44 MB `[rauno:record.inner.desktop.network_initial.by_type.media]`), richardshuai `[richardshuai:record.home.desktop.dom.features.videos]` | Mobile data, reduced-motion users | No → none |
| A8 | Animated, rotating or typed role line | **3:** mehdio (typewriter `[mehdio:mobile-fold.webp]`), deshraj-jogiya (typing effect and widgets crowd the fold `[deshraj-jogiya:desktop-fold.webp]`), eugeneyan (rotating descriptor `[eugeneyan:record.summary.hero_text_mobile]`) | Recruiter | No → static text |
| A9 | Slogan or vague tagline instead of a role | **3:** ssp (title ~8 phone screens down `[ssp:tiles/m-04.png]`), shaundataanalytics `[shaundataanalytics:mobile-fold.webp]`, richardshuai `[richardshuai:tiles/d-01.png]` | Recruiter, business | Partly: the generic kicker (issue 10) → role line in plain words |
| A10 | AI-sounding or buzzword copy | **3:** shaundataanalytics `[shaundataanalytics:tiles/d-02.png]`, pahasara `[pahasara:record.inner.desktop.dom.hero_text]`, mujtabasaqib `[mujtabasaqib:tiles/m-01.png]` | Business, engineer | No → copy rules in §0 |
| A11 | Unsourced vanity stat tiles | **3:** deshraj-jogiya ("98%+ ETL Reliability %" `[deshraj-jogiya:desktop-fold.webp]`), shaundataanalytics (a tile that disagrees with its own demo `[shaundataanalytics:inner-desktop-fold.png]`), mujtabasaqib `[mujtabasaqib:tiles/id-01.png]` | Engineer, hiring manager (S10) | No → no aggregate stat tiles |
| A12 | "Live demo" labels that open a repository | **1:** shaundataanalytics, 3 of 7 `[shaundataanalytics:record.home.desktop.dom.features.demo_links]` | Everyone who clicks | No → "Live demo" only opens a running page |
| A13 | Icon-only or unnamed links and buttons | **11** tagged; **7** with axe `link-name` or `button-name` failures. antfu's mobile nav is six unlabelled icons `[antfu:mobile-fold.webp]`; wisl's card links are bare icons `[wisl:desktop-fold.webp]` | Recruiter, screen-reader users | No → text labels everywhere |
| A14 | Low-contrast secondary text | **20 of 29** fail axe `color-contrast` (antfu 30 places, deshraj-jogiya 50 nodes, eugeneyan 28) | Low-vision users, phones in daylight | Only on the demos (issues 3, 16) → `--dim` |
| A15 | Pinch-zoom disabled | **3** (axe `meta-viewport`, critical): pahasara, rauno, shud | Low-vision users | No → never |
| A16 | Tap targets under 24 px | **18 of 29** homepages; worst danluu (29) and redblobgames (21) `[danluu:record.home.mobile.dom.a11y_manual.small_targets_under_24px]` | Phone users | Footer 18 px, `.also` 19 px, passing by spacing (issue 15) → §10 sizes |
| A17 | Mobile layout wider than the phone | **5:** eugeneyan 453 px `[eugeneyan:record.home.mobile.dom.vw]`, mujtabasaqib 417 px, rauno 2,994 px, henryheffernan's OS page 873 px, shaundataanalytics' inner page 496 px | Phone users | No → the nav row must not widen the page |
| A18 | Roles without dates | **7:** antfu, beingbrute, damian, emilkowal, florea `[florea:tiles/d-exp.png]`, rauno, shao | Recruiter (S02) | No → every role dated |
| A19 | Stale "current" content | **4:** damian `[damian:tiles/m-01.png]`, darshshah, henryheffernan (meta still job-seeking `[henryheffernan:record.summary.meta_description]`), florea | Recruiter | Risk → keep "Present" true; "Updated" chips |
| A20 | Heavy JS or page weight for a page of text | **3** at ≥ 700 KB JS: bruno-simon, emilkowal (887 KB plus 1.09 MB of fonts), florea (2.1 MB for one page `[florea:record.summary.total_bytes_desktop]`); **8 of 29** homepages over 1 MB | Recruiter on a phone; tells an engineer the site isn't built with care | No → §11 budget |
| A21 | Projects as bare titles with no one-liner or link | **5:** mujtabasaqib, darshshah, robinlinacre `[robinlinacre:desktop-fold.webp]`, shud, samwho | Business, recruiter | No → one line and labelled links per card |
| A22 | No email; contact only through icons or the footer | **7:** brittanychiang, madebyevan, richardshuai, samwho, danluu, shud, taniarascia (its envelope icon is a newsletter signup `[taniarascia:record.home.desktop.dom.nav]`) | Recruiter, business | No → email in the hero and `#contact` |
| A23 | A credited copy of a well-known layout | **1:** kushalgoel `[kushalgoel:tiles/d-02.png]` | Engineer, memorability | No → take patterns, not identity |

**Listed in the brief but not found in the set:**
- **Skill-percentage bars: 0 of 29** (`features.progress_bars` is 0 on every homepage; the high
  `percent_labels` counts on beingbrute and deshraj-jogiya are result numbers, not skill bars).
- **Logo walls: no verified instance.** Up to 7 logo images on some homepages, but no verified claim
  calls any of them a wall. antfu's labelled "Creator of" rows are the positive version.
- **Buzzword clouds: none.** The one candidate (shaundataanalytics' "Tech Stack") was corrected by
  the verifier to a generic icon grid `[shaundataanalytics:tiles/d-00.png]`.

### 3.7 The cautionary "wow" verdict

The test for each site is what it does well against what it costs a recruiter: the 6-second phone
scan, then the 30-second skim (S01, S02). All figures are **measured** and verified.

| Site | What it does well | What it costs | Recruiter score |
|---|---|---|---|
| [bruno-simon](research/appendix.md#bruno-simon) (mean 1.73) | Designed loading state; controls for keyboard, mouse, touch and gamepad; MIT source credited `[bruno-simon:index-html.txt]`; craft 4 | Zero readable characters at 3–35 s in a software-GL harness `[bruno-simon:fold-30s-probe.json]`; 1.23 MB JS, 3.8–5.5 MB desktop; name, role and contact only in hidden panels; no résumé | 1 |
| [henryheffernan](research/appendix.md#henryheffernan) (2.20) | One idea through every layer; craft 5, the top in the set; a plain 2D version at its own URL; the owner landed a role | Two click gates and ~32 s to a readable role `[henryheffernan:click-through-probe.json]`; 4.8 MB initial; the OS page overflows a phone; the 2D version isn't linked from the landing | 1 |
| [ciechanow](research/appendix.md#ciechanow) (3.33) | The best "wow" in the set: server-rendered prose readable at 500 ms, an instruction above each widget, simulations behind a play button; memorability, craft and copy all 5 | Live WebGL on the homepage (120 canvases, 7.08 MB); never says what the author does; no résumé | 1 |
| [ssp](research/appendix.md#ssp) (3.33) | The graph is secondary to a complete text column, keyboard-reachable; 150 KB JS, LCP 0.84 s | On mobile the graph fills ~680 px before the first content card `[ssp:tiles/m-00.png]`; job title ~8 phone screens down | 2 |
| [mujtabasaqib](research/appendix.md#mujtabasaqib) (2.33) | Dated, typed experience cards | Splash gate, LCP 8.7 s; the current job ~5 phone screens down; 4.2 MB | 2 |

**Verdict.** The trade-off is lopsided. The two 3D landings score 1 for the recruiter and only 3 for
memorability, because what you remember is the effect, not the person's work. The "wow" that scores
well is different: ciechanow, redblobgames and samwho score 5 on memorability and 4–5 for the
engineer by putting text first and starting interaction only on request, and redblobgames and samwho
keep the heavy part on its own page. No hiring source supports a WebGL landing for data roles; the
only opinion available (S16, S18) says content matters an order of magnitude more.

So the standing rule is the evidence-backed choice. neuronet-arena stays the "wow", on its own page,
with a still screenshot on its card. The homepage gets memorability from a plain, specific identity
and that screenshot, not from motion.

### 3.8 Hiring-side evidence

Sources are graded in the appendix ([source table](research/appendix.md#hiring-side-evidence)):
**evidence** is a survey or study with a stated method, or a named person in a hiring role
(S01–S08); **evidence, pseudonymous** is a forum comment whose author says they hire (S09–S16),
which never carries a finding alone; **opinion** is advice with no method or hiring role (S17,
S18). X01–X13 are supplementary loads. Every number below was measured by the source, not by this
workflow.

- **Tier 1:** at least two surveys, studies or named hiring staff.
- **Tier 2:** one Tier-1 source plus self-identified hiring staff, or self-identified hiring staff in
  two or more separate threads.

**What the evidence supports**

| # | Finding | Tier | Sources | Used for |
|---|---|---|---|---|
| H1 | The first look is short and rules people out: a 7.4 s average first pass ([S02](research/appendix.md#s02)); a 31 s median per résumé in a study of 76 recruiters ([S01](research/appendix.md#s01)). The exact figure is disputed ([S08](research/appendix.md#s08)), but every source is under a minute | 1 | S01, S02, S08; also S09, S13, X04, X10 | P1–P3, A4, A8, A9; §4.3 targets |
| H2 | The first pass reads current title and employer, then the previous role, then dates, then education. Brand names weigh heavily | 1 | S02, S01; also S13, X10 | P1, P6, P7, A18; the At-a-glance order |
| H3 | The résumé is the gate; a site or GitHub is read later, by a hiring manager or engineer. 81% of hiring managers screen on the résumé first ([S03](research/appendix.md#s03)). 72% of campus recruiters *say* they would open a portfolio link at the top of a résumé ([S05](research/appendix.md#s05), stated intent) | 1 | S03, S06, S01; also S14, S15, X08; S05 as counterweight | P3, P8, P10, A1, A2 |
| H4 | Proof of work outweighs credentials. For new grads, an internship in the industry is the strongest tie-breaker ([S04](research/appendix.md#s04)) | 1 | S03, S04, S06, S07 | P5–P7, P12; Experience before Projects |
| H5 | Data hiring checks the reasoning: problem, trade-offs, why each tool, downstream use, which metric moved ([S06](research/appendix.md#s06), [S07](research/appendix.md#s07)) | 1 | S06, S07; also S10, S12, S09 | P11, P13, I3; the `/mcp/` outline |
| H6 | Tutorial or copied projects count for little | 2 | S06; also S12, X03, X07 | P11; the new project (§14) |
| H7 | Repo hygiene is checked when someone opens it: tests, data-quality controls, a README with a diagram, clean commits | 2 | S06; also S10, S09 | P10, I6 |
| H8 | Cost awareness is a plus | 2 | S10, S06 | P11 (results and limits); border-waits shows each run's time and storage (§14.9) |
| H9 | A weak or empty portfolio hurts more than none | 2 (forum only) | S09, S12, S15, X07 | A12, A21; curate hard |
| H10 | Right to work and location are checked before skills | 2 (forum only) | S11, S13; X13 is opinion | P2; the `[BRETT: …]` location and authorization rows |

**Opinion, and where sources disagree**

- **Does anyone open the site?** Split. S17 (a bootcamp, opinion) says GitHub is often read before
  the CV; S03 and the recruiter threads (S14, S15) contradict it. Hiring managers on forums: some
  always open the links and want live sites (S16), some only after a shortlist (S15), some never
  (X05). Either way the answer is the same: fast to scan (P1–P3) and deep where someone clicks
  (P8–P11).
- **Flashy or 3D sites.** No evidence-grade source. The only opinion (S16, S18) says content matters
  far more than design outside frontend and game roles, and calls bruno-simon slow on phones.
- **Mobile.** No source measures how often recruiters open candidate links on a phone. S05's 69% "on
  a tablet" is in-person stated intent. **Every mobile requirement in this spec is a hedge**, backed
  by the measured mobile failures in the set (A3, A16, A17), not by hiring data.

### 3.9 What this means for the redesign

- The first screen becomes a résumé header in HTML (P1, P2, P3, P14; H1, H2, H10), static and
  never animated (A4, A8).
- Phones get a short text section nav (P4; A3, A17). It fixes the highest-severity audit issue.
- Experience stays first, compact and dated, with internships labelled (P6, P7; H4), and the hero
  links straight to `#projects` (P5).
- Demos stay on their own pages behind static cards with links named by destination (P8, P12; A5,
  A12), and every demo page gets one shell with how-it-works, build notes and source (P9, P10).
- `/mcp/` follows the case-study outline, diagram first (P11; H5, S06).
- Every number is sourced or a placeholder (P13; A11, S10).
- The homepage stays static and light (P15; A20), and the accessibility lead holds (P16; A14–A16).
- The desktop hero's empty half gets static facts, not a project image (audit issue 9; ideas P19
  and I4).
- The new data project fills a gap no verified claim in the set covers: a scheduled pipeline whose
  freshness and test status a visitor can see (appendix, "Set-level checks"; §14).

## 4. Goals and personas

### 4.1 What the site is for

- **Brett's position, from the sources:** Data Analyst Intern at Geotab since May 2026
  [src: index.html:27-28], Honours Computer Science at the University of Windsor, 2022 – 2027
  [src: index.html:175-177], "Graduating 2027 · Open to full-time roles" [src: index.html:7].
- **What Brett wants next:** `[BRETT: target roles wording: data engineering, data analytics,
  software engineering?]`. No site or résumé line names target roles (fact sheet §4). Until Brett
  answers, the status line keeps "Open to full-time roles" [src: index.html:7], the kicker stays
  "data & software engineering" [src: index.html:8], and the At-a-glance card has no "Looking for"
  row (§6.3, §18 #31).
- **The job of the redesign,** per audience:
  - **Recruiter:** in a 6-second phone scan, see who Brett is, the current role, the level, what
    Brett wants, and where the résumé is. In a 30-second skim, confirm dates and earlier roles.
  - **Engineer colleague or hiring manager:** reach running code and its source in one or two taps,
    see technical decisions and tests, and see that the site itself is built with care.
  - **Business professional:** read, in plain words, what Brett did at each job and what changed
    because of it, and find one project framed as a problem and its outcome.
- **How success is checked:** the time-to-key-info targets in §4.3, measured on the built site with
  the method given there, and the persona checks in §4.4. The review panel (recruiter, senior data
  engineer, business manager) repeats them on the PR screenshots.

### 4.2 Personas

#### Recruiter

- **Who and how they arrive.** A technical or campus recruiter. They usually arrive from the site URL
  on the résumé [src: resume/resume.tex:82] or from LinkedIn [src: index.html:14]. Recruiters judge
  mostly from the résumé and forward links to hiring managers (S14, S15), so the site's job for them
  is to confirm, fast.
- **Time budget.** A first pass of seconds (7.4 s average, S02) and a 31 s median per résumé (S01).
  Plan for a 6-second scan and a 30-second skim (H1).
- **Device.** Unknown: no source measures it. Design for 390 px first as a hedge (§3.8).
- **Must find, in the order the first pass reads (H2):**
  1. Current title and employer: "Data Analyst Intern at Geotab" [src: index.html:10].
  2. Level and timing: student, "Graduating 2027" [src: index.html:7], with `[BRETT: graduation
     month 2027, earliest full-time start]`. Once Brett answers, the status line reads
     `Graduating [BRETT: month] 2027 · Open to full-time roles`. A start date that differs from
     graduation goes in the "Looking for" row, not the status line (§18 #30).
  3. What Brett wants: "Open to full-time roles" [src: index.html:7]. With a role answer, the line
     becomes `Graduating [BRETT: month] 2027 · Open to full-time [BRETT: role family] roles`, and the
     card gains its "Looking for" row (§18 #31). At ≤ 640 px the line may wrap to two lines.
  4. The résumé PDF, labelled as such.
  5. A contact path: email, LinkedIn, GitHub as text buttons.
  6. Earlier roles with dates: Ontario Ministry of Transportation (co-op, Jan – Aug 2025), CIMCO
     Refrigeration (Apr – Sep 2024 co-op; Apr – Sep 2023) [src: index.html:41-42, 54-55, 68-69].
  7. Location and work authorization: `[BRETT: where based, open to relocation or remote?]`,
     `[BRETT: work authorization]`. Fallback: the row is left out (fact sheet §2: no source gives a
     location).
- **Where it lives after the redesign:** items 1–5 in the hero (status, `h1`, lede, actions) and
  the header Résumé button. Item 6 is also in the At-a-glance card's "Before" row, and item 1 in its
  "Now" row above 640 px. Item 7 joins the card as "Based in" once Brett answers. Item 6 is in full
  in `#experience`.
- **Today:** items 1–5 are in both folds; 6 starts just inside the fold; 7 is missing; the phone has
  no nav past the hero (audit §2 #12a, issue 1). Recruiter score 4.
- **Research:** P1, P2, P3, P7, P14; A1, A2, A8, A9, A18; H1, H2, H3, H10.

#### Engineer colleague (including the hiring manager or tech lead)

- **Who and how they arrive.** An engineer, tech lead or data-engineering hiring manager, often
  forwarded by a recruiter (S14) or opening the link after a shortlist (S15). They check GitHub "to
  verify your technical taste" (S06) and "click in to start viewing code" (S09).
- **Time budget.** Longer and deliberate: one reads a CV for 30 s, then 1 minute, then opens code
  (S09). Estimated 1–3 minutes on the site.
- **Must find:**
  1. A running demo built from the real project: neuronet-arena (JavaScript port reproducing the
     Python engine bit for bit, confirmed for the JS half, fact sheet §7.1) and mcp-surface (the npm
     package's checks in the browser, `mcp-surface/app.js:1-5`).
  2. The source link for every project, labelled "Source".
  3. Technical decisions and tests: `/mcp/` (fixes, tests, CI output, `mcp/index.html:44-58`,
     `:92-150`).
  4. The system flow in seconds: "If I cannot see the system flow in five seconds… No diagram means
     no project" (S06). A diagram near the top of `/mcp/` and of the border-waits page.
  5. That the internship work is production work: Airflow DAGs, BigQuery SQL, GitLab CI/CD
     [src: index.html:31-34].
  6. That the site itself is built well: fast, light, accessible, no framework (§11, §10).
- **Where it lives:** `#projects` cards (kind label, labelled links), the demo pages and their shared
  shell (how it works, build notes, Source), `/mcp/`.
- **Today:** real demos, source links and `/mcp/` exist, but depth starts 2–3 screens down, the
  homepage doesn't surface decisions, and the demo pages have different shells with no build notes
  (audit §2 #12b, issue 13). Engineer score 4.
- **Research:** P5, P8, P9, P10, P11, P12, P13, P15, P16; H4, H5, H6, H7.

#### Business professional

- **Who.** A hiring manager outside engineering, an operations or product lead, or someone Brett
  might work for. They want problem, outcome, plain language and credibility (the business persona in
  the [rubric](research/README.md#rubric)). Employers look for "evidence of a student's ability to
  solve problems" (S04); a data hiring manager asks "which metric your work moves" (S06).
- **Time budget.** Estimated 30–60 seconds on the homepage, longer only if a project interests them.
- **Must find:**
  1. In each role, what changed because of the work, in plain words. The sourced outcomes are
     "replaced manual reporting across business units" and "cutting processing time from hours to
     minutes" (MTO) [src: index.html:45-46], "replacing manual data entry" and training "for over 20
     employees" (CIMCO 2024) [src: index.html:60-61], "cutting turnaround time between departments"
     (CIMCO 2023) [src: index.html:73]. Three of them are on the first screen in the At-a-glance
     card's Results row: "Power BI dashboards that replaced manual reporting (MTO) · Processing time
     cut from hours to minutes (MTO) · Python training for over 20 employees (CIMCO)"
     [src: index.html:45], [src: index.html:46], [src: index.html:61] (§6.3, §8.3). Geotab's bullets
     state no outcome in any source, so its outcome is a `[BRETT: …]` question (§4.5). Numbers beyond
     "over 20" are `[BRETT: …]` too.
  2. One project framed as problem → outcome: the `/mcp/` For line, "For anyone who lets an AI
     assistant send email, edit a calendar or post on their behalf, and needs to know when what it
     can do changes." [src: mcp/index.html:17-19, :40-42], and its first h2, which states the problem
     [src: mcp/index.html:29-32] (§8.18). Later, the border-waits question "How long are the waits at
     the Windsor–Detroit crossings, by hour and direction?" opens that page (§14.9).
  3. Credibility: real employers and dates, and numbers that trace to a source (no stat tiles, A11).
- **Today:** plain language but few outcomes, and none on the first screen; project lines are
  technical; no problem-to-outcome framing (audit §2 #12c). Business score 3, the weakest persona.
- **Research:** P6, P11, P13; I3, I20; A10, A11; H4, H5.

### 4.3 Time-to-key-info targets

**Method (same as audit §3).** Load the page at 390×844 or 1440×900 and take the element's bottom
edge in page coordinates at load. Scrolls = `max(0, ceil((bottom − viewportHeight) / viewportHeight))`,
so 0 means it is on the first screen. A tap or click is counted only when a link must be followed. In
a Playwright `page.evaluate`:

```js
const el = document.querySelector(SELECTOR);
const bottom = el.getBoundingClientRect().bottom + window.scrollY;
Math.max(0, Math.ceil((bottom - innerHeight) / innerHeight));
```

"Set median" and "set best" come from the 29 verified sites, with the verifier's corrections
applied. The full set tables are in the appendix, ["Set-level time to key
info"](research/appendix.md#set-level-time-to-key-info). "Steps" means scrolls plus clicks.
**Today** is measured (audit §3). **Targets are estimated** and apply to the redesigned site. Seconds
assume the §11 budget: first-screen text painted within 2.0 s on throttled mobile (today's LCP is
744 ms, and the LCP element is the lede).

**390 × 844**

| Item | Element checked | Today (measured) | Set median / best | Target (scrolls / taps) | Target (seconds, estimated) | How the design meets it |
|---|---|---|---|---|---|---|
| (a) Current role | `.hero .lede` | 0 / 0 (y 249–328) | 0 / 0; best 0 / 0 on 17 sites | **0 / 0** | Readable at first paint, ≤ 2.0 s | Lede unchanged [src: index.html:10]. The glance "Now" row is hidden at ≤ 640 px, since the lede says the same |
| (b) Résumé | header Résumé link | 0 scrolls, 1 tap (y 11–47) | 0 / 1; best 0 / 1 (5 sites) | **0 / 1** | Link visible at first paint; PDF (37,452 B) opens in 1 tap | Header row 1 (sticky) and the hero primary button, both "Résumé" |
| (c) Contact | first hero `[data-contact]` | 0 scrolls, 1 tap (y 360–402; "Email" wraps) | 0 / 1; best 0 / 0 (6 sites) | **0 / 1**; the address as text in 1 tap | Buttons at first paint | Email · LinkedIn · GitHub in one row; nav "Contact" reaches the written-out address |
| (d) Best project | `[data-project="neuronet-arena"] .project-shot` | **3 / 0** (y 2564–2744), no nav shortcut | 0.5 / 1; best 0 / 0 (ciechanow, by running it on the homepage, which the rules forbid) | **0 / 1** via the nav row's "Projects" link; by scrolling alone, `#projects-title` no lower than today (see below) | ≤ 5 s from landing to the card in view (read the first screen, one tap) | `.nav-sections` row (§5.3). The hero jump link is hidden at ≤ 860 px, because the row already has Projects |
| (e) Shipped work | first `.badge-live` / "Live demo" link | **3 / 0** (y 2761) | 1 / 1; best 0 / 0 (robinlinacre, shud, taniarascia show proof inline in a text list) | **0 / 1**, the same tap as (d) | Same as (d); the demo is one more tap, and `/neuronet-arena/` paints its lede at 692 ms today | The featured card's kind line "Live demo · runs in your browser" sits at the top of the card |
| Graduation and availability | `.hero .status` | 0 / 0 | — | **0 / 0** | First paint | Status line [src: index.html:7] |
| Earlier roles and dates | glance "Before" row | first Geotab bullets just inside the fold; earlier roles further down | — | **0 / 0** | First paint | At-a-glance card under the buttons |
| Outcomes (business) | glance "Results" row | not on the first screen; the first outcome bullet (`index.html:45`) is in the MTO entry, below the fold (audit §2 #12c) | — | **≤ 1 scroll / 0** | — | At ≤ 640 px the Results row takes the place of "Works with" (§8.3) |

**1440 × 900**

| Item | Element checked | Today (measured) | Set median / best | Target (scrolls / clicks) | Target (seconds, estimated) | How the design meets it |
|---|---|---|---|---|---|---|
| (a) Current role | `.hero .lede` | 0 / 0 (y 302–361) | 0 / 0; best 0 / 0 on 17 sites | **0 / 0** | First paint | Lede, glance "Now" |
| (b) Résumé | header Résumé link | 0 scrolls, 1 click (y 11–47) | 0 / 1 | **0 / 1** | First paint | Header button, hero primary button |
| (c) Contact | first hero `[data-contact]` | 0 scrolls, 1 click (y 393–435) | 0 / 1; best 0 / 0 (8 sites) | **0 / 1** | First paint | Hero buttons |
| (d) Best project | `[data-project="neuronet-arena"] .project-shot` | 2 / 0 (y 1771–2137), or 1 click on "Projects" | 0 / 1; best 0 / 0 (ciechanow, pahasara, wisl) | **0 / 1**; by scrolling alone ≤ 1 scroll for `#projects-title` | ≤ 3 s (one click) | Nav "Projects", hero jump link |
| (e) Shipped work | first `.badge-live` / "Live demo" link | 1 / 0 (y 1771), or 1 click | 0 / 1; best 0 / 0 (5 sites) | **0 / 1**, or ≤ 1 scroll | Same as (d) | Kind line at the top of the featured card |
| Graduation and availability | `.hero .status` | 0 / 0 | — | **0 / 0** | First paint | Status line |
| Earlier roles and dates | glance "Before" row | first role only | — | **0 / 0** | First paint | At-a-glance card in the right column |
| Outcomes (business) | glance "Results" row | not on the first screen (audit §2 #12c) | — | **0 / 0** | First paint | Results row in the right column. If the card's bottom passes the 900 px fold, its third item is dropped (§8.3) |

**The scroll-only check for Projects.** The section order stays hero → Experience → Projects (§5.2;
moving Projects up is a Decision whose default keeps today's order). The hero grows (the
At-a-glance card, and at 390 the section-link row). Experience must shrink by at least as much, so
Projects end up no lower than today:

- `#projects-title` bottom edge **≤ 2,532 px at 390** (2 scrolls; today 2,513 px, measured) and
  **≤ 1,800 px at 1440** (1 scroll; today 1,720 px, measured).
- If a build misses this, move Experience bullets into their role's disclosure one at a time, in
  the order given in §4.5. Never cut the hero facts or a role's first bullet.

**What "best" would cost.** Every 0 / 0 best case in the set breaks a rule or needs proof inline:
ciechanow runs its demo on the homepage, and robinlinacre, shud and taniarascia list proof in plain
text. The 0 / 1 targets above are the best the set shows that keep the standing rules (appendix,
"Set-level time to key info").

### 4.4 What each persona checks on the built site

These are the persona checks for the review panel and for each PR's screenshots.

| Persona | Check | Pass when |
|---|---|---|
| Recruiter | 6-second scan of the 390 first screen | Name, "Data Analyst Intern at Geotab", "Graduating 2027", "Open to full-time roles", Résumé and Email/LinkedIn/GitHub are all readable without scrolling |
| Recruiter | 30-second skim | Earlier employers with years (glance "Before"), and every role in `#experience` has dates, place and its type ("Co-op" where the source says so) |
| Recruiter | Résumé | One tap opens `/assets/resume/Resume_BrettAdams.pdf`; the link has no query string or fragment |
| Engineer | Reach running code | From landing, one tap on "Projects" then one on "Live demo" opens `/neuronet-arena/` or `/mcp-surface/` |
| Engineer | Source | Every project card has a "Source" link (or a named repo link) |
| Engineer | Depth | `/mcp/`'s flow figure (§8.18) is inside its first screen at 1440 with both of its rows, "At runtime" and "In CI", and decisions and tests follow. If Brett declines the two-row figure (§18 #14), this check reads "CI-flow figure" instead |
| Engineer | Site quality | §11 budget and §10 axe pass bar met |
| Business | Outcomes | The glance card's Results row shows its sourced outcomes on the first screen at 1440, and below the buttons at 390. Each role's shown bullets include its sourced outcome bullet wherever §4.3's height limit allows; outcome bullets are the last to move (§4.5). Where no source states an outcome (Geotab today), the lead bullet ships as sourced and §18 #33 holds the outcome question |
| Business | One problem → outcome story | `/mcp/` opens with a For line that names who it is for and the problem without MCP or CI shorthand, and its first h2 states the problem (`mcp/index.html:29-32`) |

### 4.5 Telling the internship story

The story is a progression, and every step is in the sources. It runs from automating manual office
work, to Python tools, pipelines and training, to BI reporting, to production data pipelines:

| Thread | Where | Sourced line | Number missing |
|---|---|---|---|
| Automation | CIMCO 2023 | "Built Power Platform tools adopted by several departments to automate repetitive manual workflows." [src: index.html:72] | `[BRETT: how many departments adopted the Power Platform tools?]` |
| Automation | CIMCO 2023 | "Automated SharePoint document routing and approvals, cutting turnaround time between departments." [src: index.html:73] | `[BRETT: turnaround before/after?]` |
| Web apps | CIMCO 2024 | "Built Flask web applications that deliver real-time data and alarm information to NHL arena operations teams, including a thermal imagery viewer used by arena technicians." [src: index.html:58] | `[BRETT: how many arenas/teams used the Flask apps?]` |
| Pipelines | CIMCO 2024 | "Wrote a Python pipeline that aggregates data from two APIs into structured JSON for the arena interface." [src: index.html:59] | — |
| Automation | CIMCO 2024 | "Automated controller data collection for CAG engineers with a Python tool, replacing manual data entry." [src: index.html:60] | `[BRETT: what does "CAG" stand for, and may it be expanded?]` |
| Training staff | CIMCO 2024 | "Designed and ran hands-on Python training sessions for over 20 employees." [src: index.html:61] | none: "over 20" is the only number in any bullet |
| BI | MTO 2025 | "Built Power BI dashboards on Azure Databricks and SQL that replaced manual reporting across business units." [src: index.html:45] | `[BRETT: dashboards built, business units?]` |
| Automation | MTO 2025 | "Automated legacy workflows with SQL queries and VBA scripts, cutting processing time from hours to minutes." [src: index.html:46] | `[BRETT: one hours-to-minutes example?]` |
| Training staff (rollout) | MTO 2025 | "Led the rollout of the internal Data Broker Tool, onboarding teams and standardizing the data vocabulary." [src: index.html:47] | `[BRETT: teams onboarded? OK to name the Data Broker Tool?]` |
| Pipelines | Geotab 2026 | "Maintain and debug production Apache Airflow DAGs on Cloud Composer that run the SalesOps data pipelines." [src: resume/resume.tex:106] (the site says "Google Cloud Composer", `index.html:31`; fact sheet D6) | `[BRETT: DAGs maintained, run frequency?]`; `[BRETT: one DAG failure you debugged: cause and fix?]`; `[BRETT: what do the SalesOps and Revenue Operations tables feed (which reports or teams rely on them), and what improved after your work?]` |
| Pipelines (SQL) | Geotab 2026 | "Write and deploy production BigQuery SQL for Revenue Operations tables, including the device status, database dimension and accounts master datasets." [src: index.html:32] | `[BRETT: tables or rows/day?]`; `[BRETT: did a SQL change cut runtime or bytes scanned, and by how much?]`; `[BRETT: OK to name SalesOps, Revenue Operations and the datasets?]` |
| Pipelines (CI/CD) | Geotab 2026 | "Ship changes through GitLab CI/CD, from branch development through staging validation to production." [src: index.html:33] | `[BRETT: what does "staging validation" check before production (row counts, schema diff, tests)?]` |
| Pipelines (migrations) | Geotab 2026 | "Carry out live table migrations against fixed infrastructure deadlines, coordinating with dataset owners." [src: index.html:34] | `[BRETT: tables/datasets migrated, deadlines met?]`; `[BRETT: how many tables migrated, and was there downtime?]` |

Every fallback is the sourced line as shown. Nothing in the last column ships until Brett answers,
and each question is also in §18 #33 (or #7 for names).

**How the site tells it**

1. **Order and dates do most of the work.** Reverse-chronological, every role dated, "Co-op" where
   the source says so (MTO [src: index.html:41], CIMCO 2024 [src: index.html:54]). The 2023 CIMCO
   title stays "Digital Solutions" as written [src: index.html:68] until
   `[BRETT: the 2023 CIMCO title: intern? co-op? summer student?]`. "Present" on Geotab stays true
   only while it is: `[BRETT: Geotab internship end date]` (A19).
2. **No summary sentence under `## Experience`.** The four dated entries already tell the
   progression. A counted, three-part summary ("Four roles since 2023: …") would only restate them,
   and the site's copy rule is short and factual (`CLAUDE.md:10-11`). The business reader gets the
   outcomes from the glance card's Results row instead (§8.3). A summary sentence is an option under
   §18 #6, not the default.
3. **A number from Brett goes into the bullet it measures,** with no new bullet and no tile. Until
   Brett supplies it, the bullet ships in its sourced wording. Never an invented figure (S10). Two
   target forms:
   - MTO: "Built `[BRETT: N]` Power BI dashboards on Azure Databricks and SQL that replaced manual
     reporting across `[BRETT: N]` business units."
   - Geotab: "Maintain and debug production Apache Airflow DAGs on Cloud Composer that run the
     SalesOps data pipelines; fixed `[BRETT: N]` failing runs, including `[BRETT: cause]`." An answer
     about who relies on the SalesOps and Revenue Operations tables goes into `:31` or `:34` in the
     same way.
4. **Homepage bullets per role** (the résumé PDF keeps every bullet; this is a site-only selection,
   not a wording change). Every role starts with all its bullets shown, in source order, and keeps
   its first bullet in view:

   | Role | Bullets, in source order | Never moved (lead) | Moves into `.job-more`, at this step of the cut order |
   |---|---|---|---|
   | Geotab | `:31` in the résumé's wording [src: resume/resume.tex:106]; `:32` trimmed to "Write and deploy production BigQuery SQL for Revenue Operations tables." [src: index.html:32, trimmed, §18 #7]; `:33`; `:34` | `:31` | `:33` (1st), `:34` (4th), `:32` (5th) |
   | MTO | `:45`; `:46`; `:47` | `:45` | `:47` (3rd), `:46` (9th) |
   | CIMCO 2024 | `:58` trimmed to "Built Flask web applications that deliver real-time data and alarm information to NHL arena operations teams." [src: index.html:58]; `:59`; `:60`; `:61` | `:58` | `:59` (2nd), `:61` (6th), `:60` (8th) |
   | CIMCO 2023 | `:72`; `:73` | `:72` | `:73` (7th) |

   If `#projects-title` sits lower than the §4.3 limit after the layout work in §8, move one bullet
   at a time into its role's `details.job-more`, and re-measure after each. The order is `:33`,
   `:59`, `:47`, `:34`, `:32`, `:61`, `:73`, `:60`, `:46`. Stop at the first state that meets the
   limit.
   - **Never moved:** `:31`, `:45`, `:58` and `:72`, each role's lead bullet. A role's dates and
     chips never move either.
   - **Why this order.** The outcome bullets (`:61`, `:73`, `:60`, `:46`) go last, so the business
     reader keeps them longest. The CI/CD bullet `:33` goes before the migrations bullet `:34`. Its
     tool stays visible as the `GitLab CI/CD` chip and in Skills, and `:34` is the stronger
     production signal for a data-engineering reader.
   - **Counting.** The site shows 13 bullets today. PR 5's acceptance counts the shown and moved
     bullets, and they must add up to 13.
   - **Worst case.** If every move is needed, the homepage shows the four lead bullets. The Results
     row still carries three sourced outcomes on the first screen.
   - **`:58` stays.** It is CIMCO 2024's lead bullet, trimmed of the thermal-viewer clause. The
     `ul.also` "NHL Arena Web Interface" row stays too [src: index.html:154] (fact sheet D18). The
     bullet says what Brett built in the role, and the row lists the project once.
   - **`:60`'s fallback.** If `[BRETT: what "CAG" stands for]` has no answer, `:60` ships as
     "Automated controller data collection with a Python tool, replacing manual data entry." (a
     trim of the sourced line, §18 #7).
   - **A Geotab reorder, as an option.** A site-only reorder of `:31` puts the business object first
     and keeps every sourced word: "Maintain and debug the SalesOps data pipelines in production, and
     the Apache Airflow DAGs on Cloud Composer that run them." [src: resume/resume.tex:106]. It is an
     option under §18 #6, not the default.
5. **Chips carry the tools with full names** a recruiter can match (P6): Geotab "BigQuery · Apache
   Airflow · Cloud Composer · SQL · GitLab CI/CD" (the site's chip says "Airflow", `index.html:36`;
   fact sheet D7); MTO "Power BI · Azure Databricks · SQL · VBA" [src: index.html:49]; CIMCO 2024
   "Python · Flask · JavaScript" [src: index.html:63]; CIMCO 2023 "Power Platform · SharePoint"
   [src: index.html:75].
6. **Nothing else claims impact.** No stat tiles, no totals, no adjectives standing in for numbers.
   The Results row is sourced text, not a tile. Every `[BRETT: …]` above is also listed in §18; the
   builder ships the fallback until Brett answers.

## 5. Information architecture

The site stays one homepage plus a few pages that each hold one thing. The homepage lists; the
project and demo pages show; `/mcp/` is the only long-form page. Nothing is added that needs a new
plugin, a build step or a new `section[id]` value without a Decision (§18).

### 5.1 Sitemap

| URL | Page | Source in this repo | Status | Shell | GA today | In `sitemap.xml` |
|---|---|---|---|---|---|---|
| `/` | Homepage | `index.html` | Current, redesigned | `_layouts/default.html` | Yes | Yes |
| `/#experience` · `/#projects` · `/#skills` · `/#education` · `/#contact` | Homepage anchors | `index.html:21`, `:81`, `:161`, `:172`, `:183` | Current; ids unchanged | — | `section_view` for the four `section` ids only | — |
| `/mcp/` | MCP case study, the only long-form page | `mcp/index.html` | Current, restructured on the case-study outline (§8) | Default layout | Yes | Yes |
| `/neuronet-arena/` | Live demo | `neuronet-arena/` (synced; never edited here) | Current; shell changes go upstream | Its own HTML | No (Decision) | Yes |
| `/mcp-surface/` | Live playground | `mcp-surface/` (`checks.js` is generated) | Current; gets the shared demo shell | `.demo-shell` (§8) | No (Decision) | Yes |
| `/404.html` | Not found | `404.html` | Current; gains links to the projects and the résumé (§8) | Default layout | Yes | No (`sitemap: false`, `404.html:5`) |
| `/assets/resume/Resume_BrettAdams.pdf` | Résumé PDF | built from `resume/resume.tex` | **Path unchanged** | — | `resume_download` on links to it | Yes |
| `/sitemap.xml` | Sitemap | generated by `jekyll-sitemap` | Unchanged | — | — | — |
| `/robots.txt` | Robots | `robots.txt` | Unchanged | — | — | — |
| `/mazefinder/` | MazeFinder live demo | future `mazefinder/`, per `handoff/mazefinder-live-demo.md` | Queued | `.demo-shell` | Per the demo-page Decision | Yes, once built |
| `/border-waits/` | border-waits live demo | future `border-waits/`, synced from the project repo's `web/` (§14) | Queued, gated on Brett's go-ahead | `.demo-shell` | Per the demo-page Decision | Yes, once built |
| `/<repo-name>/data/v1/` | border-waits data files | **not this repo:** the project repo's own Pages, `[BRETT: repo name, suggested border-waits-pipeline]` | Future | — | — | No (another repo's site) |
| `/google-workspace-mcp-policy/` | OAuth privacy policy | **not this repo:** the google-workspace-mcp project site | Exists; reserved | — | — | No |

Not pages, and they stay unpublished: `sift/` (excluded, `_config.yml:31-32`), `handoff/` and
`resume/` (excluded, `_config.yml:26-30`), `README.md`, `CLAUDE.md`.

Two path rules:
- **Never create a folder in this repo with the same name as one of Brett's project repos** that
  publishes Pages. GitHub's docs don't say which one wins (§14.4). That is why the
  border-waits data repo must not be called `border-waits`, and why `/google-workspace-mcp-policy/`
  is never created here.
- **Links from site pages to another repo's Pages use full `https://` URLs.** `check-links.mjs`
  resolves root-relative links inside `_site` and fails CI if they are missing
  (`.github/scripts/check-links.mjs:29-30`); URLs with a scheme are skipped. `fetch()` calls in JS
  are not checked, so the border-waits page may fetch its data root-relatively.

### 5.2 Homepage section order

Today's order is kept by default: the internship is the strongest new-grad signal (S04, H4), and the
same order keeps `scroll_depth` and dwell comparable with today's reports. Moving Projects above
Experience is a Decision (§18) whose default keeps this order.

| # | Element | `id` | `section_view` value | Contents | Change from today |
|---|---|---|---|---|---|
| 0 | `header.site-header` | — | — | Brand, section links, Résumé | Adds the `.nav-sections` row at ≤ 860 px (§5.3) |
| 1 | `section.hero` | none | none (a new `hero` value is a Decision) | Status, kicker, `h1`, lede, action buttons, the text link "Projects and live demos ↓" (above 860 px only), the At-a-glance card with its Results row | Two columns at 1440; the At-a-glance card and the jump link are new (§6, §8.2–8.3) |
| 2 | `section#experience` | `experience` | `experience` | The timeline: four roles | Tightened (§4.5) so Projects sit no lower than today |
| 3 | `section#projects` | `projects` | `projects` | Featured card(s), standard cards, `ul.also` | Cards gain the kind line, labelled links and `srcset` (§8) |
| 4 | `section#skills` | `skills` | `skills` | Skills list; Education stays a `div#education` inside it | No new `education` value unless Brett approves one |
| 5 | `section#contact` | `contact` | `contact` | Email written out, LinkedIn, GitHub, Résumé | Copy and layout only |
| 6 | `footer.site-footer` | — | — | © line; GitHub, LinkedIn, Email | Links gain `data-contact` (a Decision) |

Rules for the builder:
- **No other `section` element with an `id` on the homepage.** `analytics-events.js:68` observes
  every `section[id]`, so a new one silently creates a new `section_id` value. The At-a-glance card
  has no `id` (§8.3 gives its element); headings inside it may carry ids for `aria-labelledby`.
- **No project image, demo still or "selected work" strip anywhere in the hero or first screen.** It
  would bring back the removed hero preview. The hero link to `#projects` is plain text.
- The hero link is
  `<a class="hero-jump" href="#projects">Projects and live demos <span aria-hidden="true">↓</span></a>`
  (§8.2), so screen readers don't announce the arrow. `check-links.mjs` skips fragment-only links
  (`check-links.mjs:30`), and `#projects` exists (`index.html:81`).
- **The hero link is hidden at ≤ 860 px from PR 4,** with
  `@media (max-width: 860px) { .hero-jump { display: none; } }`. At those widths the nav's second row
  already has Projects, a tap away on the same screen (§5.3).

### 5.3 Navigation

**Desktop (> 860 px), one row, today's order:**

`> brett adams` · Experience · Projects · Skills · Contact · **Résumé**

| Item | `href` (Liquid, as today) | Notes |
|---|---|---|
| `> brett adams` | `{{ '/' \| relative_url }}` | `> ` is visual only, so the accessible name is "brett adams" (`_layouts/default.html:42`, §7) |
| Experience | `{{ '/#experience' \| relative_url }}` | `_layouts/default.html:44` |
| Projects | `{{ '/#projects' \| relative_url }}` | `:45` |
| Skills | `{{ '/#skills' \| relative_url }}` | `:46` |
| Contact | `{{ '/#contact' \| relative_url }}` | `:47` |
| Résumé | `{{ '/assets/resume/Resume_BrettAdams.pdf' \| relative_url }}` | `:48`; no query string or fragment, ever |

**Phones and tablets (≤ 860 px), two rows:**
- Row 1, sticky: brand and the Résumé button.
- Row 2, `.nav-sections`, not sticky (it scrolls away): Experience · Projects · Skills · Contact as
  plain text links, always visible, no JS, no toggle. Each target is at least 44 px tall.
- The row must fit 390 px without widening the page (eugeneyan's nav widened its layout to 453 px,
  A17). Acceptance: at 390, `document.documentElement.scrollWidth === 390`.

**Other entry points:**
- Hero, above 860 px only: "Projects and live demos ↓" → `#projects`.
- `/mcp/` and `/404.html` use the same header from `_layouts/default.html`; their section links
  already point at `/#…`.
- Demo pages use the `.demo-shell` header (§8): "← Brett Adams" → `/#projects`, the project name,
  Résumé → the PDF, Source → the repo. No section links. neuronet-arena gets this only as an
  upstream change.
- Footer: GitHub, LinkedIn, Email on every default-layout page.
- `/mcp/` is reached from the MCP servers and mcp-surface cards, not from the nav. Demos are reached
  from their cards. Education is reached through Skills.

### 5.4 What lives where

| Content | Homepage | Project and demo pages | `/mcp/` | Résumé PDF |
|---|---|---|---|---|
| Name, role, level, availability | Hero and At-a-glance card | Name in the shell's back link and title; Résumé link | Header and back link | Yes |
| Experience | `#experience`: dated roles, selected bullets, chips | — | — | Every bullet (format unchanged) |
| Projects | One card each: one line, fact chips, kind line, labelled links, one still screenshot | The running thing, a "How it works" block, 2–3 build-note sentences, Source, an "Updated" date | The MCP servers and mcp-surface story only | Its own project list (fact sheet D11) |
| Technical decisions and tests | Chips only | The "How it works" block | The case-study outline: For → problem → diagram → decisions → tests and CI output → result → limits → stack and source | — |
| Numbers | Sourced chips, bullets and the glance card's Results row (sourced text); no stat tiles | Values the project's own code or data wrote | The fact line, sourced (`mcp/index.html:13`) | — |
| Live code | **Never** | Only here, started on the page | No (static figures and CI output) | — |
| Dates | Role dates | "Updated" chip | "Updated" chip and the eyebrow date | Role dates |
| Contact | Hero buttons, `#contact` with the address written out, footer | Shell header Résumé link; back link home | Footer | Header line |
| Skills and education | `#skills` with `div#education` | — | — | Yes |
| Long-form writing | — | — | **Only here.** No blog, no posts | — |

### 5.5 Projects: order and placement

**Today** (`index.html:84-157`): neuronet-arena (featured, full row, `:85`) · MCP servers (`:98`) ·
mcp-surface (`:112`) · PromptPal (`:127`) · TechNest (`:140`); then `ul.also`: NHL Arena Web
Interface (`:154`), MazeFinder (`:155`), "More on GitHub →" (`:156`).

Writer's note: this section keeps the order in `index.html`, with MCP servers before mcp-surface. No
research finding favours either order, and swapping the two is a one-line change if Brett prefers
it.

**Layout rule.** neuronet-arena stays the featured card (Brett's favourite, `CLAUDE.md`). Other
cards are half width in two columns. When the number of half-width cards is odd, the last one gets
`.project-wide` and spans the row in the compact horizontal layout (§8). No card is demoted to
`ul.also` to fix a gap. Brett can still choose to demote one (Decision).

| State | Full-row cards | Half-width cards, in order | `ul.also` |
|---|---|---|---|
| After this redesign | neuronet-arena | MCP servers, mcp-surface, PromptPal, TechNest (4) | NHL Arena Web Interface, MazeFinder, More on GitHub |
| When `/border-waits/` ships | neuronet-arena, then border-waits | unchanged (4) | unchanged |
| When `/mazefinder/` ships | as above | MCP servers, mcp-surface, MazeFinder, PromptPal, TechNest `.project-wide` (5) | NHL Arena Web Interface, More on GitHub |

- **Each project appears once.** When MazeFinder becomes a card, its `ul.also` line is removed and
  the card keeps `data-project="mazefinder"` (`index.html:155`).
- **border-waits** gets `data-project="border-waits"`, a new `project_name` value. That needs no
  new GA custom definition: `project_name` is already a parameter.
- **`data-project` values for existing projects never change:** `neuronet-arena`, `mcp-servers`,
  `mcp-surface`, `promptpal`, `technest`, `nhl-arena`, `mazefinder`.

**`/mazefinder/`** follows `handoff/mazefinder-live-demo.md`: its own page with the WebAssembly
build, reached from its card, using the shared `.demo-shell` header and the "what loads" line for a
heavy engine (I2). Nothing about it runs on the homepage. The handoff's open question (which card
drops to `ul.also`) is answered by the `.project-wide` rule above.

**`/border-waits/`** is the new project page (§14). It is a second full-row featured card directly
after neuronet-arena, because it is the closest match to the Geotab work: pipelines, SQL and CI/CD
[src: index.html:31-34] (§14.3). It ships only when Brett says go; until then nothing on the site
mentions it.

**Sift** stays off the site by default (Decision; fact sheet D21). If Brett says yes, it gets one
`ul.also` line, `<li data-project="sift">`, linking to https://github.com/brettadams0/sift, using
only upstream facts from `sift@e37fa56` (for example, the app "ships with no `INTERNET` permission
at all", fact sheet §7.8). It has no in-browser demo, so it never takes a card. Before that line
ships, the stale `sift/` mirror (0.2.0 against upstream 0.2.8, fact sheet C5) is re-synced or
removed. The mirror stays excluded from the build either way.

### 5.6 What must not move

- `/assets/resume/Resume_BrettAdams.pdf`: `resume_download` matches `/Resume_BrettAdams\.pdf$/`
  (`assets/js/analytics-events.js:108`).
- The section ids `experience`, `projects`, `skills`, `contact` (GA continuity), plus `education`
  and `main` (anchors and the skip link).
- `/mcp/`, `/neuronet-arena/`, `/mcp-surface/`: shared links point at them, and the synced demo
  carries absolute links to this domain (`neuronet-arena/index.html:12-13`, `:28`, `:147`).
- Shared files that other pages load by path: `assets/css/demo-shell.css` and the
  `assets/fonts/*.woff2` files (and `assets/css/tokens.css`, if Brett answers §18 #49 with (a)). The
  demo pages load them, including the synced neuronet-arena after PR 11a and `/border-waits/`'s
  fonts. Renaming or removing one needs every page that loads it changed first, upstream for
  neuronet-arena.
- The domain: a custom domain is `[BRETT: custom domain?]` and would also move the OAuth policy URL
  on the Google consent screen (fact sheet §10). Default: no change.

## 6. First screen

The first screen is the header, the hero and the "At a glance" card. It is built as an HTML mock at
`handoff/mocks/first-screen.html`.
- **What it uses:** §7's tokens unchanged, §8's markup and class names (8.0–8.4), and only sourced
  copy. The mock shows the copy that ships by default (6.3), so a builder can copy it as it is.
- **What it sets:** the layout, spacing and copy of the first screen at 390 and 1440. §8 gives every
  state (hover, focus, active) and the widths in between.

Nothing on this screen is live, and no project appears on it: no screenshot, demo still or
"selected work" strip. Above 860 px the hero links to `#projects` in plain text. At 860 px and
below, that link is hidden and the nav's second row does the same job. The homepage keeps today's
order: hero, `#experience`, `#projects`, `#skills`, `#contact` (§5).

### 6.1 Screenshots

**How they were taken.** Measured 2026-10-26 with Playwright Chromium 141.0.7390.37, loading the
mock from disk.
- Fold only.
- Desktop: 1440×900 at DPR 1.
- Mobile: 390×844 at DPR 2, with `isMobile` and touch on.
- The PNGs were converted to WebP with PIL 12.3 (`method=6`), lowering the quality from 92 until the
  file was at most 85,000 B: 71,752 B at quality 92 (1440) and 84,378 B at quality 88 (390).
- Fonts come from Google Fonts in the mock only. The site self-hosts them (§7.5).

**Reading them.**
- The magenta circles are the numbered markers for 6.6. They are absolutely positioned and
  `aria-hidden`.
- The hollow "8" marks where Experience starts. At 390 the solid "8" sits on the nav row's
  "Projects", because the jump link is hidden there.
- No `[BRETT: …]` placeholder appears. The two card rows that wait on Brett ("Looking for", "Based
  in") are kept in the mock as an HTML comment (`first-screen.html:412-415`), and 6.2 gives where
  they land once answered.

![First screen at 1440×900: header with brand, four section links and a Résumé button; hero with status line, kicker, name, lede, four buttons and a "Projects and live demos" link on the left; the At a glance card with Now, Before, Studying, Results and Works with rows on the right; the Experience title and the Geotab role below the hero. Markers 1–8.](mocks/first-screen-1440.webp)

![First screen at 390×844: brand and Résumé button; a row of four section links; status line, kicker, name, lede; a full-width Résumé (PDF) button over an Email / LinkedIn / GitHub row; the At a glance card with its Before, Studying and Results rows. Markers 1–8.](mocks/first-screen-390.webp)

### 6.2 What the mock proves

Measured in the same run, from `getBoundingClientRect()` in CSS px from the top of the page, with
fonts loaded. Names come from Chrome's accessibility tree (CDP `Accessibility.getFullAXTree`). The
one-off check script is not committed. PR 4 (§16) repeats these checks on the real page with
`handoff/tools/measure.mjs`.

| Check | 1440×900 | 390×844 | Pass? |
|---|---|---|---|
| Horizontal overflow (`scrollWidth` vs viewport) | 1440 = 1440 | 390 = 390. Also 768 = 768 and 320 = 320. | yes |
| Overlap between status, kicker, h1, lede, buttons, jump link and card | none | none | yes |
| Section links visible without scrolling | in the header, y 0–57 | row under the header, y 57–101, four links 90 px wide each (72 px each at 320) | yes |
| Section link target size | 44 px tall (y 6–50) | 44 × 90 px | yes (≥ 24 px, SC 2.5.8) |
| Résumé reachable | header button y 10–46 (36 px tall), hero button y 378–422 | header button y 10–46, hero button y 378–422 (358 × 44, full width) | yes |
| Contact buttons on one row | y 378–422 | Email, LinkedIn and GitHub at y 430–474, 114 px wide each | yes. Today "Email" wraps onto its own row (audit issue 25). |
| Jump link | y 442–486 | hidden (`display: none` at ≤ 860 px). Row 2's "Projects" (y 57–101) does its job. | yes |
| At a glance card | y 121–559. Rows: Now 167–230, Before 230–293, Studying 293–356, Results 356–461, Works with 461–538 | y 498–851. Rows: Before 540–624, Studying 624–708, Results 708–834. Now and Works with are hidden. | yes: the 1440 card ends 341 px above the fold, so all three Results items stay |
| Experience starts above the fold | `## Experience` y 688–708; Geotab head y 740–764; first bullet y 776–802 | — (the hero ends at y 892) | yes, below 900 |
| No project image on the first screen | none | none | yes |
| Animations running after load (`document.getAnimations()`) | 0 | 0 | yes |
| Accessible names | link "brett adams"; heading "AT A GLANCE" and heading "EXPERIENCE" (Chrome applies the CSS uppercase); heading "Data Analyst Intern at Geotab"; link "Projects and live demos"; kicker text "data & software engineering" | the same, without the hidden jump link | yes, no prefixes. Today: "## Experience" and "Data Analyst Intern @ Geotab" (audit issue 6). |

Time to key info against today. Today's numbers are from audit §3.

| Item | Today 390 | Mock 390 | Today 1440 | Mock 1440 |
|---|---|---|---|---|
| Current role | lede y 249–328 | lede y 275–354 | lede y 302–361 | lede y 295–354; "Now" row y 167–230 |
| Graduation and school | status line and lede | status y 134–163, lede, "Studying" row y 624–708 | status line and lede | status y 121–153, lede, "Studying" row y 293–356 |
| Résumé | 0 scrolls, 1 tap | 0 scrolls, 1 tap (two buttons) | 0 scrolls, 1 click | 0 scrolls, 1 click |
| Contact | 0 scrolls; Email on a second row (y 412–454) | 0 scrolls, one row | 0 scrolls | 0 scrolls |
| What came of the work | in Experience only, below the first screen | "Results" row y 708–834 | in Experience only, below the first screen | "Results" row y 356–461 |
| Best project and shipped work | 3 scrolls, no shortcut | **1 tap** ("Projects" in the nav row) | 2 scrolls, or 1 click | 1 click (nav, or the jump link) |

These meet the targets in §4.3: 0 / 1 for everything a recruiter needs, and 0 / 1 to proof on a
phone through the nav.

**Once Brett answers.** Measured on the same mock, with the two commented rows put back:
- **With one-line test answers** ("Full-time data engineering roles" and "Test City, ON · open to
  relocation"; test text, not facts):
  - At 390, "Looking for" is at y 708–750 and "Based in" at 750–792, both inside 844. "Results"
    moves to y 792–918.
  - At 1440, the card ends at y 644. `## Experience` moves to y 773–792 and the Geotab head line to
    y 824–849, both still above 900. PR 15 re-measures this on the real page.
- **With the placeholder text** (the longest case): at 390 "Looking for" is at y 708–792 and "Based
  in" at 792–855; at 1440 the card ends at y 664.
- **Status line** (§18 #30, with "May" as test text): `Graduating May 2027 · Open to full-time
  roles` (45 characters) and the 62-character role-family form both stay on one line at 1440. At
  390 both wrap to two lines (46 px tall) and move the buttons down 17 px, with no overflow. That is
  allowed (6.5). At 320, today's 41-character line already wraps to 46 px.

### 6.3 Copy, exactly as it ships

Every factual line comes from `handoff/research/fact-sheet.md`. UI labels that state no fact are
marked "label". The mock uses exactly this copy.

| # | Element | Exact copy | Source | Notes |
|---|---|---|---|---|
| 7 | Brand | `brett adams`, after an `aria-hidden` `> ` prefix | `_layouts/default.html:42`; prefix today `assets/css/site.css:121-124` | Prefix markup: §7.10, §8.0 |
| 7 | Desktop nav | Experience · Projects · Skills · Contact · **Résumé** | `_layouts/default.html:44-48` | Today's order and labels. Résumé is the bordered button. |
| 7 | Phone section row (≤ 860 px) | Experience · Projects · Skills · Contact | same | Same four `/#…` anchors. Résumé stays in the sticky row above. |
| 1 | Status line | `Graduating 2027 · Open to full-time roles` | `index.html:7` | Ships as is. Once Brett gives a month: `Graduating [BRETT: month] 2027 · Open to full-time roles`. After a role answer: `Graduating [BRETT: month] 2027 · Open to full-time [BRETT: role family] roles` (§18 #30, #31). Fallback: the sourced line. A start date that differs from graduation goes in "Looking for", not here. |
| 2 | Kicker | `data & software engineering`, after an `aria-hidden` `// ` | `index.html:8`; prefix today `site.css:278-281` | |
| 2 | h1 | `Brett Adams` | `index.html:9` | |
| 3 | Lede | `Data Analyst Intern at Geotab, on production pipelines in BigQuery and Airflow. Computer Science at the University of Windsor.` | `index.html:10` | "Geotab" stays a link to `https://www.geotab.com` (`index.html:10`). The lede keeps "Airflow" (D7 option (a), §18 #5). |
| 4 | Primary button | `Résumé (PDF)` → `/assets/resume/Resume_BrettAdams.pdf` | link `index.html:12`; file `README.md:22` | "(PDF)" is a label. No query string, fragment or `download` attribute, or `resume_download` stops firing (`assets/js/analytics-events.js:107-108`). |
| 5 | Contact buttons | `Email` → `mailto:adamsbrett00@gmail.com` · `LinkedIn` → `https://www.linkedin.com/in/bretta/` · `GitHub` → `https://github.com/brettadams0` | `index.html:13-15` | `data-contact="email"`, `"linkedin"` and `"github"` stay. Email moves first. |
| 8 | Jump link (> 860 px only) | `Projects and live demos ↓` → `#projects` | label. "Live demos" is true for neuronet-arena and mcp-surface (`index.html:89`, `:116`). | The arrow is in an `aria-hidden` span, so the name is "Projects and live demos". It sits outside any `[data-project]`, so it sends no `project_click`. Hidden at ≤ 860 px, where row 2's "Projects" does its job. |
| 6 | Card heading | `At a glance` | label | |
| 6 | Now (hidden ≤ 640 px) | `Data Analyst Intern, Geotab · May 2026 – Present` | `index.html:27-28` | On phones the lede says the same two lines above (§8.3). |
| 6 | Before | `Ontario Ministry of Transportation (co-op, 2025) · CIMCO Refrigeration (2023; co-op, 2024)` | `index.html:41-42`, `:54-55`, `:68-69` | "Co-op" where the source says so: MTO (`:41`) and CIMCO 2024 (`:54`). The 2023 title is a `[BRETT]` item (fact sheet §10). |
| 6 | Studying | `Honours Computer Science, University of Windsor · 2022 – 2027` | `index.html:175-177` | Short form of the degree. The canonical wording is D3 (§18). |
| 6 | Looking for (**only once Brett answers**) | `[BRETT: target roles, e.g. data engineering, analytics or software roles]`; with a later start date: `Full-time [BRETT: role family] roles, from [BRETT: month] 2027` | — | **Ships without this row** until Brett answers (§18 #31). Before that it would only repeat the status line. |
| 6 | Based in (**only once Brett answers**) | `[BRETT: city / open to relocation?]` | — | **Ships without this row** until Brett answers (§18 #32). No source gives a personal location (fact sheet §2). |
| 6 | Results | `Power BI dashboards that replaced manual reporting (MTO) · Processing time cut from hours to minutes (MTO) · Python training for over 20 employees (CIMCO)` | `index.html:45`, `:46`, `:61` | Sourced text, not a stat tile (A11). Brett's numbers (§18 #33) go in here once given, in §4.5 item 3's form. A first-screen copy option under §18 #4, default ship. |
| 6 | Works with (chips; hidden ≤ 640 px) | `Apache Airflow` `BigQuery` `SQL` `Python` `Power BI` `GitLab CI/CD` | `index.html:167` (Apache Airflow), `:36` (BigQuery, SQL, GitLab CI/CD), `:49` (Power BI), `:63` (Python) | D7 default (§18 #5), the name Experience uses. On phones Results takes this row's place: the tools repeat in every job's chips and in Skills. |
| 8 | Below the hero | `Experience`; `Data Analyst Intern` + `Geotab`; `May 2026 – Present · Oakville, ON`; the first Geotab bullets as PR 5 ships them: `Maintain and debug production Apache Airflow DAGs on Cloud Composer that run the SalesOps data pipelines.` · `Write and deploy production BigQuery SQL for Revenue Operations tables.` | `index.html:23`, `:27-28`; bullets `resume/resume.tex:106` (D6 default) and `index.html:32`, trimmed (§18 #7) | The other Geotab bullets sit behind §8.4's "N more" disclosure, below the fold. PR 5's fit loop decides how many (§8.4); `:31` is never moved. |

Each date range and term in the card goes in `<span class="nowrap">` (`white-space: nowrap`), as the
mock does. Without it, at 1440 "May 2026 –" and "Present" broke across two lines, and at 390
"2022 –" and "2027" did. The copy is unchanged.

### 6.4 Structure

The mock uses §8's markup exactly: 8.1 (header and `.nav-sections`), 8.2 (`.hero`), 8.3 (`.glance`)
and 8.4 (the start of Experience). Those subsections are the source; the shape is repeated here so
this section can be read alone.

```html
<header class="site-header"> … brand · nav.site-nav (4 × .nav-section, .nav-resume) … </header>
<nav class="nav-sections" aria-label="Sections"><div class="wrap nav-sections-inner"> 4 links </div></nav>
<main id="main" tabindex="-1">
  <section class="hero" aria-labelledby="hero-name">          <!-- no id: the hero stays untracked (§13) -->
    <div class="wrap hero-grid">
      <div class="hero-text">
        p.status · p.kicker (span.prefix "// ") · h1#hero-name · p.lede · p.actions (4 × a.btn, data-contact) ·
        <p class="hero-jump"><a href="#projects">Projects and live demos<span class="nowrap" aria-hidden="true">&nbsp;↓</span></a></p>
      </div>
      <div class="glance">                                     <!-- a div: no landmark, no section_view -->
        <h2 class="glance-title" id="glance-title">At a glance</h2>
        <dl class="glance-list"> div.glance-now · Before · Studying · [Looking for · Based in, once answered] ·
          div.glance-results · div.glance-tools (Works with, ul.chips role=list) </dl>
      </div>
    </div>
  </section>
  <section id="experience" class="section" aria-labelledby="experience-title"> … </section>
```

- **Heading outline:** h1 "Brett Adams", h2 "At a glance", then h2 "Experience" and the rest as
  today. The card heading gives screen-reader users a jump target. It adds no `section_id`, because
  the observer watches only `section[id]` (`analytics-events.js:68`).
- **Two navs.** The phone row is a sibling of the sticky header, not a child, so only the brand and
  Résumé row sticks and the section row scrolls away, with no JS. Only one set of section links is
  ever displayed. The other is `display: none`, which also takes it out of the accessibility tree.
- **The status dot** is decorative (`aria-hidden`). It never pulses (§9).

### 6.5 Layout by breakpoint

The values are §7 tokens or §8 literals. The mock uses exactly these.

| Width | Header | Hero | At a glance |
|---|---|---|---|
| ≥ 981 px | One sticky row, `--header-h`: brand left; four links and the Résumé button right, `gap: var(--space-6)` (`--space-4` under 1000 px). The brand and the four links are 44 px tall; the Résumé button is 36 px. | `.hero-grid` is `minmax(0, 7fr) minmax(0, 5fr)`, `gap: var(--space-8)`. Padding `var(--space-9) 0`. Lede is `--fs-lg`, max 34em. Buttons in a row, gap 10 px. Jump link under the buttons. | Right column, top-aligned with the status line. Padding 20 px. Rows use a 96 px label column and a 12 px gap. Labels are Mono `--fs-xs` `--muted`; values `--fs-sm` `--soft`. |
| 861–980 px | Same | Same two columns. Padding 40 px top, 48 px bottom. | Right column |
| 641–860 px | Sticky row: brand and Résumé only. `.nav-sections` below: four links sharing the width, 44 px tall, `--fs-sm` 500, `--soft`. | One column, `gap: var(--space-6)`. The jump link is hidden (`.hero-jump { display: none; }`). | Full width under the buttons, `max-width: 560px` |
| ≤ 640 px | Gutter 16 px | Padding 32 px top, 40 px bottom. Status `--fs-xs`: today's 41-character line fits on one row at 390 (337 px, measured). A longer answered line may wrap to two lines (6.2). Lede `--fs-md`. `.actions` becomes a 3-column grid, `gap: var(--space-2)`: Résumé spans all three columns, then Email, LinkedIn and GitHub, all 44 px tall. | Padding 16 px. 88 px label column. The "Now" and "Works with" rows are hidden, so the card reads Before, Studying, Results. |

### 6.6 Why each element earns its place

**1. Status line.**
- **What it does:** it carries the level and availability before anything else is read.
- **Evidence:** availability beside the résumé button is P2. It is seen on beingbrute
  `[beingbrute:desktop-fold.webp]`, deshraj-jogiya `[deshraj-jogiya:mobile-fold.webp]`,
  richardshuai's "Seeking 2027 New Grad Roles" `[richardshuai:mobile-fold.webp]` and mujtabasaqib
  `[mujtabasaqib:mobile-fold.webp]`. Hiring staff on forums say location and right to work are
  checked before skills (H10: S11, S13).
- **What it avoids:** it is static text, unlike the typed or rotating role lines on mehdio
  `[mehdio:mobile-fold.webp]` and eugeneyan `[eugeneyan:record.summary.hero_text_mobile]` (A8).
- **Copy:** it ships in its sourced form until Brett gives a month (§18 #30). The answered form
  keeps "Graduating", the recruiter's level signal, which §4.4's recruiter check looks for.

**2. Name and kicker.**
- **What it does:** the name is the page's only h1. The kicker keeps the terminal look Brett chose,
  but screen readers no longer hear its `// ` (6.2).
- **Evidence:** a one-phrase role summary helps a recruiter scan (S14). The sources have only "data
  & software engineering" (`index.html:8`), so a sharper phrase waits on the target-roles answer
  (§18).
- **What it avoids:** a slogan in place of a role, as on ssp `[ssp:tiles/m-04.png]` and
  shaundataanalytics `[shaundataanalytics:mobile-fold.webp]` (A9).

**3. Lede.**
- **What it does:** current title, employer and school in two plain sentences. This is the
  résumé-header pattern (P1).
- **Evidence:**
  - damian `[damian:desktop-fold.webp]`, shao `[shao:mobile-fold.webp]`, richardshuai
    `[richardshuai:mobile-fold.webp]`, wisl `[wisl:desktop-fold.webp]` and emilkowal
    `[emilkowal:mobile-fold.webp]` all lead this way.
  - The first pass over a résumé reads current title and company, then the previous role, then
    dates (H2: S02).
  - In S01's study of 76 recruiters, experience at a top company was the most predictive signal for
    an interview.
- **Constraint:** the lede is the homepage's LCP element today (measured `p.lede`, 744 ms throttled,
  `[brettadams0:measure-home.json.perf.mobile.lcp]`), so it never fades in. A fade-in paragraph
  held wisl's LCP to 5.0 s `[wisl:record.summary.lcp_ms_desktop]` (§9).

**4. Résumé (PDF).**
- **What it does:** it is the one primary button, full width on a phone and 44 px tall at every
  width.
- **Evidence:**
  - The résumé is the gate: 81% of hiring managers screen on it first (H3: S03).
  - 19 of the 29 sites studied have no résumé at all (A1, measured).
  - Five reach it at 0 scrolls and 1 tap on a phone (P3): beingbrute, damian, deshraj-jogiya,
    kushalgoel and pahasara. See `[damian:mobile-fold.webp]`.
- **Label:** "(PDF)" says what the click opens, like the destination-named links on madebyevan
  `[madebyevan:tiles/d-00.png]` and taniarascia `[taniarascia:inner-desktop-fold.png]` (P12).
- **Analytics:** the `href` is unchanged, so `resume_download` keeps firing.

**5. Email, LinkedIn, GitHub.**
- **What they are:** text buttons, not icons.
- **Evidence:** seven sites fail axe for unnamed links or buttons (A13, measured), and antfu's phone
  nav is six unlabelled icons `[antfu:mobile-fold.webp]`. Contact written out is P14: florea
  `[florea:desktop-fold.webp]`, shao `[shao:record.home.mobile.dom.features.contact_links]` and
  antfu `[antfu:desktop-fold.webp]`.
- **Fix:** at 390 the three now sit on one row (measured y 430–474), which fixes audit issue 25.
- **Order:** Email comes first because it is the most direct route. The full address stays in
  `#contact`. `data-contact` is unchanged.

**6. At a glance.**
- **What it does:** it fills the empty right half of the desktop hero with facts, not a picture
  (audit issue 9: about 55% blank, estimated). Its "Results" row puts what came of the work on the
  first screen, which §4.1 asks for on behalf of the business reader.
- **Evidence:**
  - A two-column desktop first screen is P19 (an idea: brittanychiang
    `[brittanychiang:desktop-fold.webp]` and ssp `[ssp:desktop-fold.webp]`; kushalgoel is a
    credited copy, A23).
  - A short "previously" list under the current role is idea I4, from shao `[shao:desktop-fold.webp]`
    and taniarascia `[taniarascia:desktop-fold.webp]`.
  - Its first rows follow H2's reading order: now, before, studying.
  - It names both co-op terms, so internships read as real roles with years. That is P7:
    brittanychiang `[brittanychiang:tiles/m-01.png]`, florea `[florea:tiles/d-exp.png]`. For new
    grads an internship is the strongest tie-breaker (H4: S04).
  - The Results row quotes the bullets' own outcomes with their employer, so each one sits beside
    its source (P13). It is not a stat tile with an unsourced number (A11, deshraj-jogiya's "98%+
    ETL Reliability" `[deshraj-jogiya:desktop-fold.webp]`).
  - The chips use Mono for metadata, as in P20.
- **Constraint:** the card is static text only. A project screenshot here would bring back the hero
  preview Brett removed (audit issue 9; §3.4 P19).
- **On a phone:** it sits under the buttons, and the "Before", "Studying" and "Results" rows are
  inside the 844 px fold (measured y 540–834).

**7. Navigation.**
- **What it does:** on desktop it keeps today's links and order. On a phone, the four section links
  stay visible in a plain row, with no hamburger and no JS.
- **Why it matters:** it fixes the highest-severity audit issue (issue 1, High). Today the links are
  `display: none` under 860 px (`site.css:859-862`).
- **Evidence:** a short text nav that fits a phone is P4: wisl `[wisl:mobile-fold.webp]` and shud
  `[shud:mobile-fold.webp]`. Four sites hide the nav on phones with nothing in its place (A3), for
  example brittanychiang `[brittanychiang:record.home.mobile.dom.nav]`.
- **What it avoids:**
  - eugeneyan's row is wider than the phone, so its page lays out at 453 px
    `[eugeneyan:record.home.mobile.dom.vw]`. The mock measures 390 = 390, and 320 = 320.
  - richardshuai's links are 16 px tall
    `[richardshuai:record.home.mobile.dom.a11y_manual.small_target_examples]`. The mock's are
    44 px.

**8. "Projects and live demos ↓" (row 2's "Projects" on a phone), and Experience in the fold.**
- **What it does:** it puts proof of work one step from the first screen (P5). Across the set, the
  median to shipped work is 0 scrolls and 1 click at 1440 and 1 scroll and 1 click at 390, and
  Brett is at 3 scrolls on a phone today (appendix, "Set-level time to key info"; audit §3).
- **Constraint:** the link is plain text pointing at `#projects`. It is not a preview, so the
  standing rules hold.
- **Why it is hidden at ≤ 860 px:** row 2 of the nav already has "Projects", about 440 px higher on
  the same screen (measured: y 57–101, against the link's y 494–538 when it was shown). Hiding the
  repeat moves the card up 64 px at 390 (measured), so its Before, Studying and Results rows fit
  inside the phone fold.
- **Why Experience stays first:** an internship is the strongest new-grad signal (H4: S04; §3.4 P5).
  At 1440 the `## Experience` title, the Geotab head line and the first bullet are all above
  y = 900 (measured y 688–802). Today the fold ends at the first Geotab bullets, and nothing in
  either fold points to a project (audit issues 2 and 9).

### 6.7 Writer's notes

- **Writer's note (for §8, found by building the mock):** three markup details render wrongly unless
  written as the mock writes them.
  1. **Spaces vanish in inline-flex parents.** `.site-name` and `.hero-jump a` are `inline-flex`, so
     a space at the edge of a child span is dropped. The mock first rendered ">brett adams" and
     "demos↓". Fix: `.prefix { white-space: pre; }` and `&nbsp;↓` (§7.10).
  2. **Row hiding needs the child selector.** `.glance-now { display: none; }` loses to
     `.glance-list > div { display: grid; }` on specificity. Fix:
     `.glance-list > .glance-now, .glance-list > .glance-tools { display: none; }` at ≤ 640 px.
  3. **Dates break across lines.** Date ranges in the card break mid-range unless wrapped in
     `.nowrap` (6.3).
- **Writer's note:** P2 (§3.4) puts the target role and location next to the résumé button. This
  design puts them in the card, after "Studying", once Brett answers. With one-line answers both
  rows sit inside the 390 fold (6.2). The status line gains the role family at the same time
  (`Graduating [BRETT: month] 2027 · Open to full-time [BRETT: role family] roles`), so the role is
  also at 0 scrolls next to the résumé button. Until then the sourced line ships.

## 7. Visual system

**What stays:**
- dark only;
- every palette value;
- IBM Plex Sans for reading and IBM Plex Mono for metadata;
- the terminal touches;
- the amber accent.

**What changes:**
- one new text-safe grey (`--dim`);
- type, space, radius, layer and motion values become tokens;
- one radius for site and demos;
- the fonts are self-hosted, with metric-matched fallbacks;
- screenshots get `srcset` in boxes the shape of the images;
- the terminal prefixes become visual only.

The tokens stay in `assets/css/site.css`, at the top of the file, so `CLAUDE.md:12` stays true. Each
demo page carries a copy of the same `:root` block in its own stylesheet, as neuronet-arena already
does (7.11). Every value below is the one the mock uses (`handoff/mocks/first-screen.html`).

### 7.1 Tokens (ready to paste)

**Where it goes (default).** The top of `assets/css/site.css`, in place of today's `:root` block
(`site.css:4-24`):
- PR 1 adds the `:root` block, the `≤ 640 px` gutter rule, the reduced-motion durations and the
  three metric-matched `… Fallback` faces.
- PR 2 adds the four IBM Plex `@font-face` rules next to them, because the font files arrive in
  PR 2 (§16).
- `mcp-surface/styles.css` gets the same values as a copy in its own `:root` (PR 9, 7.11), and so
  does every later demo page.

**Option (a) of §18 #49.** If Brett answers #49 with (a), the same block moves to a new
`assets/css/tokens.css`, linked before `site.css` in `_layouts/default.html` and before `styles.css`
in `mcp-surface/index.html`, and `CLAUDE.md:12` is updated in the same change. The synced demos
(neuronet-arena, border-waits) keep their copies either way, because their own repos can't load this
site's files.

```css
/* Tokens for brettadams0.github.io. Dark only. Demo pages copy this :root block (spec 7.11). */
:root {
  color-scheme: dark;

  /* Colour: existing values, unchanged */
  --bg: #0b0f14;
  --bg-2: #0e141b;
  --surface: #131a22;
  --surface-2: #18212b;
  --border: #222c38;
  --border-strong: #2f3b4a;
  --text: #e7ecf2;
  --soft: #c4ccd6;
  --muted: #8695a7;
  --faint: #5a6878;        /* list markers and control edges only; never words (3.37:1 on --bg) */
  --amber: #ffb454;
  --amber-hi: #ffc97f;
  --teal: #4fd6c4;
  --red: #ef6a5f;
  --good: #3ccf6e;

  /* Colour: new */
  --dim: #7b8a9c;          /* words that used --faint; >= 4.61:1 on every background */
  --good-text: #9fe3b7;    /* text on green washes: status line, Live demo badge */
  --on-amber: #1a1206;     /* text on --amber / --amber-hi fills */
  --term: var(--bg-2);     /* terminal and code panels */
  --focus: var(--amber);
  --amber-line: rgba(255, 180, 84, 0.5);
  --amber-wash: rgba(255, 180, 84, 0.1);
  --good-line: rgba(60, 207, 110, 0.3);
  --good-wash: rgba(60, 207, 110, 0.07);

  /* Type */
  --sans: "IBM Plex Sans", "IBM Plex Sans Fallback", -apple-system, "Segoe UI", Helvetica, Arial, sans-serif;
  --mono: "IBM Plex Mono", "IBM Plex Mono Fallback", ui-monospace, "SF Mono", Menlo, Consolas, monospace;
  --fs-xs: 0.75rem;                            /* 12px */
  --fs-sm: 0.875rem;                           /* 14px */
  --fs-base: 1rem;                             /* 16px */
  --fs-md: 1.0625rem;                          /* 17px */
  --fs-lg: 1.1875rem;                          /* 19px */
  --fs-xl: 1.375rem;                           /* 22px */
  --fs-2xl: clamp(2rem, 5vw, 2.75rem);         /* 32–44px */
  --fs-3xl: clamp(1.625rem, 4.5vw, 3rem);      /* 26–48px */
  --fs-display: clamp(2.75rem, 6vw, 4.5rem);   /* 44–72px */
  --lh-tight: 1.1;
  --lh-snug: 1.3;
  --lh-base: 1.6;
  --lh-loose: 1.75;
  --tracking-tight: -0.03em;
  --tracking-caps: 0.08em;

  /* Space (4 px base) */
  --space-1: 0.25rem;   /* 4 */
  --space-2: 0.5rem;    /* 8 */
  --space-3: 0.75rem;   /* 12 */
  --space-4: 1rem;      /* 16 */
  --space-5: 1.25rem;   /* 20 */
  --space-6: 1.5rem;    /* 24 */
  --space-7: 2rem;      /* 32 */
  --space-8: 3rem;      /* 48 */
  --space-9: 4rem;      /* 64 */
  --space-10: 5.5rem;   /* 88 */

  /* Layout */
  --container: 1200px;
  --container-demo: 1320px;
  --container-post: 760px;
  --gutter: 24px;
  --header-h: 56px;

  /* Shape and layers */
  --radius-sm: 6px;
  --radius: 10px;
  --radius-pill: 999px;
  --shadow-focus: 0 0 0 3px var(--bg), 0 0 0 5px var(--focus);
  --z-header: 10;
  --z-skip: 20;

  /* Motion (section 9) */
  --dur-fast: 120ms;
  --dur: 180ms;
  --dur-slow: 240ms;
  --ease-out: cubic-bezier(0.2, 0, 0, 1);
  --ease-in-out: cubic-bezier(0.4, 0, 0.2, 1);
}

@media (max-width: 640px) {
  :root { --gutter: 16px; }
}

@media (prefers-reduced-motion: reduce) {
  :root { --dur-fast: 0ms; --dur: 0ms; --dur-slow: 0ms; }
}

/* IBM Plex, self-hosted (7.5). Files are Google Fonts' latin subsets, byte for byte. Licence: ../fonts/OFL.txt */
@font-face {
  font-family: "IBM Plex Sans";
  font-style: normal;
  font-weight: 400 700;
  font-display: swap;
  src: url("../fonts/ibm-plex-sans-latin-var.woff2") format("woff2");
  unicode-range: U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, U+0304, U+0308, U+0329, U+2000-206F, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215, U+FEFF, U+FFFD;
}
@font-face {
  font-family: "IBM Plex Mono";
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url("../fonts/ibm-plex-mono-latin-400.woff2") format("woff2");
  unicode-range: U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, U+0304, U+0308, U+0329, U+2000-206F, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215, U+FEFF, U+FFFD;
}
@font-face {
  font-family: "IBM Plex Mono";
  font-style: normal;
  font-weight: 500;
  font-display: swap;
  src: url("../fonts/ibm-plex-mono-latin-500.woff2") format("woff2");
  unicode-range: U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, U+0304, U+0308, U+0329, U+2000-206F, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215, U+FEFF, U+FFFD;
}
@font-face {
  font-family: "IBM Plex Mono";
  font-style: normal;
  font-weight: 600;
  font-display: swap;
  src: url("../fonts/ibm-plex-mono-latin-600.woff2") format("woff2");
  unicode-range: U+0000-00FF, U+0131, U+0152-0153, U+02BB-02BC, U+02C6, U+02DA, U+02DC, U+0304, U+0308, U+0329, U+2000-206F, U+20AC, U+2122, U+2191, U+2193, U+2212, U+2215, U+FEFF, U+FFFD;
}

/* Metric-matched fallbacks: shown until Plex arrives, sized so no line re-wraps on swap (7.5). */
@font-face {
  font-family: "IBM Plex Sans Fallback";
  src: local("Arial"), local("ArialMT"), local("Liberation Sans"), local("Helvetica");
  size-adjust: 100.3%;
  ascent-override: 102.2%;
  descent-override: 27.4%;
  line-gap-override: 0%;
}
@font-face {
  font-family: "IBM Plex Sans Fallback";
  font-weight: 600 700;
  src: local("Arial Bold"), local("Arial-BoldMT"), local("Liberation Sans Bold"), local("Helvetica Bold");
  size-adjust: 98%;
  ascent-override: 104.6%;
  descent-override: 28.1%;
  line-gap-override: 0%;
}
@font-face {
  font-family: "IBM Plex Mono Fallback";
  src: local("Courier New"), local("CourierNewPSMT"), local("Liberation Mono");
  size-adjust: 100%;
  ascent-override: 102.5%;
  descent-override: 27.5%;
  line-gap-override: 0%;
}
```

This block replaces `site.css:4-24`. The `.prefix`, `.nowrap` and `.sr-only` utilities in 7.10 go
in `site.css` too. The `:root` block and font faces add about 5.4 KB to `site.css` (measured as a
separate file: 5,415 B, 1,788 B gzipped, with Python `gzip` level 9; GitHub Pages' own compression
may differ slightly).

### 7.2 New tokens against today's `site.css`

| New token | Value | Today | Where today | Change |
|---|---|---|---|---|
| `--bg` … `--good` (15 colours) | unchanged | same names | `site.css:6-20` | none |
| `--faint` | #5a6878 | `--faint` | `site.css:15`; used `:280`, `:417`, `:672` | Value unchanged. Its role narrows: list markers and decoration only. `:280` moves to `--dim`. |
| `--dim` | #7b8a9c | `--faint` wherever it carries words | `site.css:280` (kicker `// `); `mcp-surface/styles.css:98` (gutter), `:161` (`.rule-tag`); upstream `neuronet-arena/styles.css:261` (`td.pending`) | New. It sits between `--faint` and `--muted`, at ≥ 4.61:1 everywhere (7.3). |
| `--good-text` | #9fe3b7 | literal | `site.css:258`, `:471` | Becomes a token |
| `--on-amber` | #1a1206 | literal | `site.css:184`, `:191` | Becomes a token |
| `--term` | `var(--bg-2)` = #0e141b | `--term` (mcp-surface only) | `mcp-surface/styles.css:9`; `--bg-2` `site.css:7` | Shared alias |
| `--focus` | `var(--amber)` | `outline: 2px solid var(--amber)` | `site.css:69`; `mcp-surface/styles.css:30` | Becomes a token |
| `--amber-line` | rgba(255,180,84,.5) | literal | `site.css:148` | Becomes a token |
| `--amber-wash` | rgba(255,180,84,.1) | literal | `site.css:155` | Becomes a token |
| `--good-line` | rgba(60,207,110,.3) | literals .3 and .4 | `site.css:255`, `:470` | The badge border goes from .4 to .3 |
| `--good-wash` | rgba(60,207,110,.07) | literal | `site.css:257`, `:469` | Becomes a token |
| `--sans`, `--mono` | + fallback face second | same | `site.css:22-23` | Fallback face added (7.5) |
| `--fs-*` | 7.4 | px and rem literals | 7.4 lists every line | Rounded to the scale |
| `--lh-tight` | 1.1 | `1`, `1.12` | `site.css:287`, `:641` | Hero h1 +0.1 |
| `--lh-snug` | 1.3 | `1.4`, `1.3` | `site.css:386`, `:680`, `:695` | |
| `--lh-base` | 1.6 | `1.6` | `site.css:48` | none |
| `--lh-loose` | 1.75 | `1.75` | `site.css:658` | none |
| `--tracking-tight` | -0.03em | -0.035, -0.025, -0.02em | `site.css:288`, `:642`, `:770`, `:798` | One value for display text |
| `--tracking-caps` | 0.08em | 0.1em, 0.08em | `site.css:318`, `:473` | Section titles from 0.1 to 0.08 |
| `--space-1` … `--space-10` | 7.6 | px literals | 7.6 lists them | Layout spacing only |
| `--container` | 1200px | literal | `site.css:75` | none |
| `--container-demo` | 1320px | literal | `mcp-surface/styles.css:33`; upstream neuronet-arena `.wrap` | none |
| `--container-post` | 760px | literal | `site.css:618` | none |
| `--gutter` | 24px; 16px ≤ 640 | literals | `site.css:77`, `:883`; `mcp-surface/styles.css:33` (20px) | The demo gutter goes from 20 to 24/16 |
| `--header-h` | 56px | 58px | `site.css:112`; `scroll-padding-top: 72px` `:35` | Header 2 px shorter. `scroll-padding-top: calc(var(--header-h) + var(--space-4))` keeps 72 px. |
| `--radius-sm` | 6px | 4, 6, 8px | `site.css:88`, `:449`, `:701` | One small radius. The focus outline's 4 px (`:71`) stays (§10.6). |
| `--radius` | 10px | 12px site, 9 and 8px buttons, 10px `pre` and demos | `site.css:21`, `:149`, `:167`, `:712`; `mcp-surface/styles.css:19` | **One radius, 10 px, the demos' value.** The site moves; the demos don't. |
| `--radius-pill` | 999px | literal | `site.css:213`, `:256`, `:468` | Becomes a token |
| `--shadow-focus` | 2-ring | none | — | New. Only where an outline would be clipped (7.7). |
| `--z-header`, `--z-skip` | 10, 20 | literals | `site.css:100`, `:84` | Becomes a token |
| `--dur-fast`, `--dur`, `--dur-slow`, `--ease-*` | §9 | `0.15s ease`, `0.2s ease` | `site.css:172`, `:436`; `mcp-surface/styles.css:206` | §9 |

The header backdrop stays a literal, but its alpha rises from 0.82 to 0.92 (`site.css:101`), as
§8.1 sets it. With `--text` scrolled under the header, the nav's `--muted` drops to 3.92:1 at 0.82
and holds 5.29:1 at 0.92 (7.3).

These literals stay literal, because each is single-use and decorative:
- the hero and contact washes (`:227-228`, `:763`);
- the status-dot glow (`:268`);
- the current-job glow (`:371`).

The hero's 60 px grid overlay (`site.css:231-243`) is removed. The audit found it adds little
(audit §2 #5). The washes stay, and 7.3 checks text over them.

### 7.3 Contrast, computed for every text/background pair in use

**Method.** The script below uses the WCAG 2.x relative-luminance formula on the hex values in 7.1.
- **Translucent fills** are flattened over what sits under them:
  - the header over `--text` scrolling beneath it, the worst case, with the blur ignored;
  - each wash at its strongest point;
  - opacity states, with both text and fill composited.
- **Bars:** body text needs 4.5:1. Large text (≥ 24 px, or ≥ 18.66 px bold) and UI need 3:1.
  Decorative marks are reported but have no bar.
- **Rows marked TODAY** are current values that fail. The **FIX** rows below them are the
  replacements.
- **Run:** measured 2026-10-22, `python3 -I contrast.py`, no dependencies. The script goes in
  `handoff/tools/contrast.py` in PR 0, so every later PR can re-run it.

<details><summary>contrast.py</summary>

```python
# WCAG 2.x contrast for every text/background pair the redesign uses.
# Run: python3 -I contrast.py   (no dependencies)
T = {  # proposed :root values (spec section 7)
 'bg':'#0b0f14','bg-2':'#0e141b','surface':'#131a22','surface-2':'#18212b',
 'border':'#222c38','border-strong':'#2f3b4a','text':'#e7ecf2','soft':'#c4ccd6',
 'muted':'#8695a7','dim':'#7b8a9c','faint':'#5a6878','amber':'#ffb454','amber-hi':'#ffc97f',
 'teal':'#4fd6c4','red':'#ef6a5f','good':'#3ccf6e','good-text':'#9fe3b7','on-amber':'#1a1206',
 'term':'#0e141b','explain':'#b9c2cd','amber-hover-nn':'#ffc370',
}
def rgb(h): h=h.lstrip('#'); return [int(h[i:i+2],16) for i in (0,2,4)]
def hexs(c): return '#%02x%02x%02x' % tuple(round(v) for v in c)
def mix(fg, bg, a):  # fg at alpha a over opaque bg
    f, b = rgb(fg if fg.startswith('#') else T[fg]), rgb(bg if bg.startswith('#') else T[bg])
    return hexs([a*x+(1-a)*y for x, y in zip(f, b)])
def lum(h):
    def ch(c): c/=255; return c/12.92 if c<=0.04045 else ((c+0.055)/1.055)**2.4
    r,g,b = rgb(h); return 0.2126*ch(r)+0.7152*ch(g)+0.0722*ch(b)
def ratio(a, b):
    la, lb = lum(a), lum(b); return (max(la,lb)+0.05)/(min(la,lb)+0.05)
C = {}  # composited backgrounds (alpha fills flattened over what sits under them)
C['header'] = mix('#0b0f14', 'text', 0.92)               # header rgba(11,15,20,.92) (section 8.1) over --text scrolling under it: worst case, blur ignored
C['header-0.82'] = mix('#0b0f14', 'text', 0.82)          # today's alpha (site.css:101), same worst case
C['hero-teal'] = mix('teal', 'bg', 0.07)                   # peak of the teal radial wash
C['hero-amber'] = mix('amber', 'bg', 0.06)                 # peak of the amber radial wash
C['status'] = mix('good', C['hero-amber'], 0.07)           # status pill fill over the worst hero point
C['badge'] = mix('good', 'surface', 0.07)                  # live badge fill on a card
C['resume-hover'] = mix('amber', C['header'], 0.10)        # nav Résumé hover fill
C['contact'] = mix('amber', 'bg', 0.06)                    # contact section wash peak
C['seg-on'] = mix('amber', 'surface', 0.12)                # mcp-surface selected example
C['rule-hit'] = mix('amber', 'surface', 0.06)              # mcp-surface rule with findings
C['verdict-pass'] = mix('good', 'surface', 0.07)
C['verdict-fail'] = mix('red', 'surface', 0.07)
C['rule-0.6'] = mix('surface-2', 'surface', 0.6)           # .rule.call-only {opacity:.6}: card fill
C['rule-0.45'] = mix('surface-2', 'surface', 0.45)         # .rule[aria-pressed=true] {opacity:.45}
C['hud'] = mix('#0b0f14', 'bg', 0.78)                      # neuronet .hud over the arena's --bg
def col(x): return C.get(x) or (T[x] if x in T else x)
def fgop(fg, bgname, a, under='surface'):  # text inside an element with opacity a
    return mix(col(fg), under, a)
# (where, fg, bg, kind) kind: text 4.5 | large 3.0 | ui 3.0 | deco (reported, no bar)
P = [
 # --- site (assets/css/site.css, _layouts/default.html, index.html, mock) ---
 ('body, h1, job and card titles', 'text','bg','text'), ('cards, buttons, glance card', 'text','surface','text'),
 ('button hover', 'text','surface-2','text'), ('brand under sticky header', 'text','header','text'),
 ('h1 on hero wash', 'text','hero-teal','large'), ('contact email on wash', 'text','contact','large'),
 ('lede, bullets', 'soft','bg','text'), ('lede on hero wash', 'soft','hero-teal','text'),
 ('card one-liners, edu', 'soft','surface','text'), ('chips', 'soft','bg-2','text'),
 ('section titles, dates, job-org, footer', 'muted','bg','text'), ('nav links in header', 'muted','header','text'),
 ('TODAY nav links, header at .82', 'muted','header-0.82','text'),
 ('glance labels (dt)', 'muted','surface','text'), ('glance values (dd)', 'soft','surface','text'),
 ('phone section row (8.1: --soft)', 'soft','bg','text'),
 ('kicker prefix "// " (span, 8.0)', 'dim','bg','text'), ('kicker text', 'teal','bg','text'),
 ('kicker on hero wash', 'teal','hero-teal','text'), ('case-facts', 'teal','bg','text'),
 ('links, "## " "> " "@ ", eyebrow', 'amber','bg','text'), ('card links', 'amber','surface','text'),
 ('"Projects and live demos" link', 'amber','hero-amber','text'), ('nav Résumé', 'amber','header','text'),
 ('nav Résumé hover', 'amber-hi','resume-hover','text'), ('link hover', 'amber-hi','bg','text'),
 ('card link hover', 'amber-hi','surface','text'),
 ('status pill', 'good-text','status','text'), ('live badge', 'good-text','badge','text'),
 ('primary button', 'on-amber','amber','text'), ('primary button hover', 'on-amber','amber-hi','text'),
 ('skip link', 'bg','amber','text'),
 ('focus ring vs page', 'amber','bg','ui'), ('focus ring vs card', 'amber','surface','ui'),
 ('focus ring vs button hover', 'amber','surface-2','ui'),
 ('list markers (::marker)', 'faint','bg','deco'), ('list markers in cards', 'faint','surface','deco'),
 # --- mcp-surface (this repo: mcp-surface/styles.css) ---
 ('back link, top links, foot', 'muted','bg','text'), ('.meta, card text', 'muted','surface','text'),
 ('editor text, .cmd', 'text','term','text'), ('.finding .msg', 'soft','term','text'),
 ('.dim .parse .summary', 'muted','term','text'), ('gutter (was --faint)', 'dim','term','text'),
 ('.rule-tag (was --faint)', 'dim','term','text'), ('gutter TODAY --faint', 'faint','term','text'),
 ('.tool-name', 'teal','term','text'), ('.prompt .ok', 'good','term','text'), ('.parse.bad, error', 'red','term','text'),
 ('warn level', 'amber','term','text'), ('verdict pass', 'good','verdict-pass','text'),
 ('verdict fail', 'red','verdict-fail','text'), ('selected example', 'amber','seg-on','text'),
 ('.rule-id, select', 'text','surface-2','text'), ('.rule-what, .rule-level', 'muted','surface-2','text'),
 ('.rule-level error', 'red','surface-2','text'), ('.rule-level warn', 'amber','surface-2','text'),
 ('.rule.hit text', 'text','rule-hit','text'), ('.rule.hit .rule-what', 'muted','rule-hit','text'),
 ('.rule-hits count', 'text','border-strong','text'),
 ('TODAY call-only .rule-id (opacity .6)', fgop('text','',0.6), 'rule-0.6', 'text'),
 ('TODAY call-only .rule-what (opacity .6)', fgop('muted','',0.6), 'rule-0.6', 'text'),
 ('TODAY skipped .rule-id (opacity .45)', fgop('text','',0.45), 'rule-0.45', 'text'),
 ('TODAY skipped .rule-what (opacity .45)', fgop('muted','',0.45), 'rule-0.45', 'text'),
 ('FIX call-only/skipped .rule-id', 'muted','surface-2','text'), ('FIX call-only/skipped .rule-what', 'dim','surface-2','text'),
 # --- neuronet-arena (upstream only: neuronet-arena/styles.css) ---
 ('.meta .legend .readout', 'muted','surface','text'), ('.tiles dt', 'muted','surface-2','text'),
 ('.hud text', 'muted','hud','text'), ('.parity p (#c4ccd6)', 'soft','surface','text'),
 ('.explain p (#b9c2cd)', 'explain','bg','text'), ('td.pending TODAY --faint', 'faint','surface','text'),
 ('td.pending FIX --dim', 'dim','surface','text'), ('btn-primary', 'bg','amber','text'),
 ('btn-primary hover (#ffc370)', 'bg','amber-hover-nn','text'),
 ('chart line --series-1 #c98500', '#c98500','surface','ui'), ('chart line --series-2 #22a392', '#22a392','surface','ui'),
 ('--blue #63a9f0 marks', '#63a9f0','surface','ui'),
 # --- non-text boundaries (SC 1.4.11 applies only where the boundary is the sole cue) ---
 ('--border vs page', 'border','bg','deco'), ('--border-strong vs page', 'border-strong','bg','deco'),
 ('--border-strong vs card', 'border-strong','surface','deco'), ('--faint as control edge vs card', 'faint','surface','ui'),
]
BAR = {'text':4.5,'large':3.0,'ui':3.0,'deco':None}
print('| # | Where | FG | BG | Ratio | Needs | Result |'); print('|---|---|---|---|---|---|---|')
fails = 0
for i, (w, fg, bg, k) in enumerate(P, 1):
    f, b = col(fg), col(bg); r = ratio(f, b); bar = BAR[k]
    res = 'n/a (decorative)' if bar is None else ('pass' if r >= bar else 'FAIL')
    fails += res == 'FAIL'
    fgl = f'`--{fg}` {f}' if fg in T else f'{f}'; bgl = f'`--{bg}` {b}' if bg in T else f'{bg} {b}'
    print(f'| {i} | {w} | {fgl} | {bgl} | {r:.2f} | {bar or "—"} | {res} |')
print(f'\n{fails} pairs below their bar. Composites: ' + ', '.join(f'{k} {v}' for k, v in C.items()))
```
</details>

Output:

| # | Where | FG | BG | Ratio | Needs | Result |
|---|---|---|---|---|---|---|
| 1 | body, h1, job and card titles | `--text` #e7ecf2 | `--bg` #0b0f14 | 16.18 | 4.5 | pass |
| 2 | cards, buttons, glance card | `--text` #e7ecf2 | `--surface` #131a22 | 14.75 | 4.5 | pass |
| 3 | button hover | `--text` #e7ecf2 | `--surface-2` #18212b | 13.69 | 4.5 | pass |
| 4 | brand under sticky header | `--text` #e7ecf2 | header #1d2126 | 13.62 | 4.5 | pass |
| 5 | h1 on hero wash | `--text` #e7ecf2 | hero-teal #101d20 | 14.51 | 3.0 | pass |
| 6 | contact email on wash | `--text` #e7ecf2 | contact #1a1918 | 14.78 | 3.0 | pass |
| 7 | lede, bullets | `--soft` #c4ccd6 | `--bg` #0b0f14 | 11.86 | 4.5 | pass |
| 8 | lede on hero wash | `--soft` #c4ccd6 | hero-teal #101d20 | 10.63 | 4.5 | pass |
| 9 | card one-liners, edu | `--soft` #c4ccd6 | `--surface` #131a22 | 10.81 | 4.5 | pass |
| 10 | chips | `--soft` #c4ccd6 | `--bg-2` #0e141b | 11.42 | 4.5 | pass |
| 11 | section titles, dates, job-org, footer | `--muted` #8695a7 | `--bg` #0b0f14 | 6.29 | 4.5 | pass |
| 12 | nav links in header | `--muted` #8695a7 | header #1d2126 | 5.29 | 4.5 | pass |
| 13 | TODAY nav links, header at .82 | `--muted` #8695a7 | header-0.82 #33373c | 3.92 | 4.5 | FAIL |
| 14 | glance labels (dt) | `--muted` #8695a7 | `--surface` #131a22 | 5.73 | 4.5 | pass |
| 15 | glance values (dd) | `--soft` #c4ccd6 | `--surface` #131a22 | 10.81 | 4.5 | pass |
| 16 | phone section row (8.1: --soft) | `--soft` #c4ccd6 | `--bg` #0b0f14 | 11.86 | 4.5 | pass |
| 17 | kicker prefix "// " (span, 8.0) | `--dim` #7b8a9c | `--bg` #0b0f14 | 5.45 | 4.5 | pass |
| 18 | kicker text | `--teal` #4fd6c4 | `--bg` #0b0f14 | 10.75 | 4.5 | pass |
| 19 | kicker on hero wash | `--teal` #4fd6c4 | hero-teal #101d20 | 9.64 | 4.5 | pass |
| 20 | case-facts | `--teal` #4fd6c4 | `--bg` #0b0f14 | 10.75 | 4.5 | pass |
| 21 | links, "## " "> " "@ ", eyebrow | `--amber` #ffb454 | `--bg` #0b0f14 | 10.90 | 4.5 | pass |
| 22 | card links | `--amber` #ffb454 | `--surface` #131a22 | 9.94 | 4.5 | pass |
| 23 | "Projects and live demos" link | `--amber` #ffb454 | hero-amber #1a1918 | 9.96 | 4.5 | pass |
| 24 | nav Résumé | `--amber` #ffb454 | header #1d2126 | 9.18 | 4.5 | pass |
| 25 | nav Résumé hover | `--amber-hi` #ffc97f | resume-hover #34302b | 8.68 | 4.5 | pass |
| 26 | link hover | `--amber-hi` #ffc97f | `--bg` #0b0f14 | 12.73 | 4.5 | pass |
| 27 | card link hover | `--amber-hi` #ffc97f | `--surface` #131a22 | 11.61 | 4.5 | pass |
| 28 | status pill | `--good-text` #9fe3b7 | status #1c261e | 10.51 | 4.5 | pass |
| 29 | live badge | `--good-text` #9fe3b7 | badge #162727 | 10.45 | 4.5 | pass |
| 30 | primary button | `--on-amber` #1a1206 | `--amber` #ffb454 | 10.51 | 4.5 | pass |
| 31 | primary button hover | `--on-amber` #1a1206 | `--amber-hi` #ffc97f | 12.28 | 4.5 | pass |
| 32 | skip link | `--bg` #0b0f14 | `--amber` #ffb454 | 10.90 | 4.5 | pass |
| 33 | focus ring vs page | `--amber` #ffb454 | `--bg` #0b0f14 | 10.90 | 3.0 | pass |
| 34 | focus ring vs card | `--amber` #ffb454 | `--surface` #131a22 | 9.94 | 3.0 | pass |
| 35 | focus ring vs button hover | `--amber` #ffb454 | `--surface-2` #18212b | 9.22 | 3.0 | pass |
| 36 | list markers (::marker) | `--faint` #5a6878 | `--bg` #0b0f14 | 3.37 | — | n/a (decorative) |
| 37 | list markers in cards | `--faint` #5a6878 | `--surface` #131a22 | 3.08 | — | n/a (decorative) |
| 38 | back link, top links, foot | `--muted` #8695a7 | `--bg` #0b0f14 | 6.29 | 4.5 | pass |
| 39 | .meta, card text | `--muted` #8695a7 | `--surface` #131a22 | 5.73 | 4.5 | pass |
| 40 | editor text, .cmd | `--text` #e7ecf2 | `--term` #0e141b | 15.58 | 4.5 | pass |
| 41 | .finding .msg | `--soft` #c4ccd6 | `--term` #0e141b | 11.42 | 4.5 | pass |
| 42 | .dim .parse .summary | `--muted` #8695a7 | `--term` #0e141b | 6.06 | 4.5 | pass |
| 43 | gutter (was --faint) | `--dim` #7b8a9c | `--term` #0e141b | 5.25 | 4.5 | pass |
| 44 | .rule-tag (was --faint) | `--dim` #7b8a9c | `--term` #0e141b | 5.25 | 4.5 | pass |
| 45 | gutter TODAY --faint | `--faint` #5a6878 | `--term` #0e141b | 3.25 | 4.5 | FAIL |
| 46 | .tool-name | `--teal` #4fd6c4 | `--term` #0e141b | 10.35 | 4.5 | pass |
| 47 | .prompt .ok | `--good` #3ccf6e | `--term` #0e141b | 9.12 | 4.5 | pass |
| 48 | .parse.bad, error | `--red` #ef6a5f | `--term` #0e141b | 6.08 | 4.5 | pass |
| 49 | warn level | `--amber` #ffb454 | `--term` #0e141b | 10.50 | 4.5 | pass |
| 50 | verdict pass | `--good` #3ccf6e | verdict-pass #162727 | 7.64 | 4.5 | pass |
| 51 | verdict fail | `--red` #ef6a5f | verdict-fail #222026 | 5.29 | 4.5 | pass |
| 52 | selected example | `--amber` #ffb454 | seg-on #2f2c28 | 7.88 | 4.5 | pass |
| 53 | .rule-id, select | `--text` #e7ecf2 | `--surface-2` #18212b | 13.69 | 4.5 | pass |
| 54 | .rule-what, .rule-level | `--muted` #8695a7 | `--surface-2` #18212b | 5.32 | 4.5 | pass |
| 55 | .rule-level error | `--red` #ef6a5f | `--surface-2` #18212b | 5.34 | 4.5 | pass |
| 56 | .rule-level warn | `--amber` #ffb454 | `--surface-2` #18212b | 9.22 | 4.5 | pass |
| 57 | .rule.hit text | `--text` #e7ecf2 | rule-hit #212325 | 13.27 | 4.5 | pass |
| 58 | .rule.hit .rule-what | `--muted` #8695a7 | rule-hit #212325 | 5.16 | 4.5 | pass |
| 59 | .rule-hits count | `--text` #e7ecf2 | `--border-strong` #2f3b4a | 9.58 | 4.5 | pass |
| 60 | TODAY call-only .rule-id (opacity .6) | #92989f | rule-0.6 #161e27 | 5.78 | 4.5 | pass |
| 61 | TODAY call-only .rule-what (opacity .6) | #586472 | rule-0.6 #161e27 | 2.79 | 4.5 | FAIL |
| 62 | TODAY skipped .rule-id (opacity .45) | #727880 | rule-0.45 #151d26 | 3.81 | 4.5 | FAIL |
| 63 | TODAY skipped .rule-what (opacity .45) | #47515e | rule-0.45 #151d26 | 2.11 | 4.5 | FAIL |
| 64 | FIX call-only/skipped .rule-id | `--muted` #8695a7 | `--surface-2` #18212b | 5.32 | 4.5 | pass |
| 65 | FIX call-only/skipped .rule-what | `--dim` #7b8a9c | `--surface-2` #18212b | 4.61 | 4.5 | pass |
| 66 | .meta .legend .readout | `--muted` #8695a7 | `--surface` #131a22 | 5.73 | 4.5 | pass |
| 67 | .tiles dt | `--muted` #8695a7 | `--surface-2` #18212b | 5.32 | 4.5 | pass |
| 68 | .hud text | `--muted` #8695a7 | hud #0b0f14 | 6.29 | 4.5 | pass |
| 69 | .parity p (#c4ccd6) | `--soft` #c4ccd6 | `--surface` #131a22 | 10.81 | 4.5 | pass |
| 70 | .explain p (#b9c2cd) | `--explain` #b9c2cd | `--bg` #0b0f14 | 10.67 | 4.5 | pass |
| 71 | td.pending TODAY --faint | `--faint` #5a6878 | `--surface` #131a22 | 3.08 | 4.5 | FAIL |
| 72 | td.pending FIX --dim | `--dim` #7b8a9c | `--surface` #131a22 | 4.97 | 4.5 | pass |
| 73 | btn-primary | `--bg` #0b0f14 | `--amber` #ffb454 | 10.90 | 4.5 | pass |
| 74 | btn-primary hover (#ffc370) | `--bg` #0b0f14 | `--amber-hover-nn` #ffc370 | 12.16 | 4.5 | pass |
| 75 | chart line --series-1 #c98500 | #c98500 | `--surface` #131a22 | 5.71 | 3.0 | pass |
| 76 | chart line --series-2 #22a392 | #22a392 | `--surface` #131a22 | 5.61 | 3.0 | pass |
| 77 | --blue #63a9f0 marks | #63a9f0 | `--surface` #131a22 | 7.06 | 3.0 | pass |
| 78 | --border vs page | `--border` #222c38 | `--bg` #0b0f14 | 1.36 | — | n/a (decorative) |
| 79 | --border-strong vs page | `--border-strong` #2f3b4a | `--bg` #0b0f14 | 1.69 | — | n/a (decorative) |
| 80 | --border-strong vs card | `--border-strong` #2f3b4a | `--surface` #131a22 | 1.54 | — | n/a (decorative) |
| 81 | --faint as control edge vs card | `--faint` #5a6878 | `--surface` #131a22 | 3.08 | 3.0 | pass |

6 pairs below their bar. Composites: header #1d2126, header-0.82 #33373c, hero-teal #101d20, hero-amber #1a1918, status #1c261e, badge #162727, resume-hover #34302b, contact #1a1918, seg-on #2f2c28, rule-hit #212325, verdict-pass #162727, verdict-fail #222026, rule-0.6 #161e27, rule-0.45 #151d26, hud #0b0f14

**Every pair the redesign uses passes.** The six failures are all TODAY rows, and each one is
fixed:

| Today | Ratio | Fix | New ratio | Who changes it |
|---|---|---|---|---|
| Header nav `--muted` over `--text` showing through the 0.82 backdrop (`site.css:101`) | 3.92 | backdrop alpha 0.92 (§8.1) | 5.29 | this repo, PR 3 |
| mcp-surface gutter and `.rule-tag` in `--faint` on `--term` (`mcp-surface/styles.css:98`, `:161`) | 3.25 | `color: var(--dim)` | 5.25 | this repo, PR 9. Clears axe `color-contrast` (audit issue 3). |
| `.rule.call-only { opacity: .6 }`, rule description (`mcp-surface/styles.css:214`) | 2.79 | drop the opacity; use the colours below | 4.61 | this repo, PR 9 |
| `.rule[aria-pressed="true"] { opacity: .45 }`, id and description (`:229`) | 3.81 / 2.11 | same | 5.32 / 4.61 | this repo, PR 9 |
| neuronet-arena `td.pending` in `--faint` on `--surface` (`neuronet-arena/styles.css:261`) | 3.08 | `color: var(--dim)` | 4.97 | upstream (§18) |

```css
/* mcp-surface/styles.css: replaces :214 and :229. State comes from colour and line style, never opacity. */
.rule.call-only { border-style: dashed; }
.rule.call-only .rule-id,
.rule[aria-pressed="true"] .rule-id { color: var(--muted); }
.rule.call-only .rule-what,
.rule[aria-pressed="true"] .rule-what { color: var(--dim); }
.rule[aria-pressed="true"] .rule-id { text-decoration: line-through; }   /* keeps today's cue (:230) */
```

Rules that follow from the table:
- `--faint` never colours words. The kicker's `// ` moves to `--dim` (5.45:1). It is now real text
  in a span (7.10), so axe checks it.
- `--border` (1.36:1) and `--border-strong` (1.54–1.69:1) only separate surfaces. Every control on
  the site is identified by its text, so SC 1.4.11 doesn't apply to them (audit issue 17). Where a
  boundary is ever the only cue, use `--faint` (3.08:1 on `--surface`).
- Every new text colour gets a row in `contrast.py` before it ships.
- axe can't read contrast over the hero's gradient washes. It reported 32 `color-contrast` nodes as
  "incomplete" at 390 (audit §1). The wash rows (h1, lede and kicker on the hero wash, the jump link,
  the status pill and the contact email) are the manual check for those, so an
  "incomplete" there is expected and not a failure.

### 7.4 Type scale

| Token | rem | px | Family and weight | Line height | Tracking | Used for | Replaces (`site.css` unless named) |
|---|---|---|---|---|---|---|---|
| `--fs-xs` | 0.75 | 12 | Mono 400–600 | 1.5–1.6 | `--tracking-caps` when uppercase | chips; Live demo badge; card and glance labels; status line ≤ 640 px | `:216` 12.5px, `:472` 11px, `:895` 11.5px |
| `--fs-sm` | 0.875 | 14 | Mono 400–600 for metadata; Sans 400–500 for nav and footer | 1.4–1.5 | caps on section titles | section titles; dates (`.job-when`); status line; kicker; jump link; eyebrow; skills labels; back links; case facts; code blocks; `.also` names; nav and the phone section row; glance values; footer; figcaptions | `:317`, `:402`, `:586`, `:625`, `:652`, `:715`, `:259` (13px); `:548` (15px); `:134`, `:274`, `:808`, `:816`, `:736` (14px) |
| `--fs-base` | 1 | 16 | Sans 400 body, 500 buttons and links; Mono 600 brand | `--lh-base` | 0 | body; buttons; card links; brand | `:48`; `:170`, `:519`, `:117`, `:750`, `:755` (15px) |
| `--fs-md` | 1.0625 | 17 | Sans 400; Mono 600 for project titles | 1.4 titles; `--lh-loose` long-form | 0 | project h3; education h3; `/mcp/` body and h3; lede ≤ 640 px | `:505`, `:602`, `:657`, `:693`, `:891` |
| `--fs-lg` | 1.1875 | 19 | Sans 400 lede, 600 job h3 | 1.55 lede; `--lh-snug` h3 | 0 | hero lede; job titles | `:295`; `:384` (18px) |
| `--fs-xl` | 1.375 | 22 | Sans 600 | `--lh-snug` | 0 | `/mcp/` h2 | `:678` |
| `--fs-2xl` | clamp(2, 5vw, 2.75) | 32–44 | Sans 700 | `--lh-tight` | `--tracking-tight` | `/mcp/` h1; 404 h1 | `:639`; `:796` (40px) |
| `--fs-3xl` | clamp(1.625, 4.5vw, 3) | 26–48 | Sans 600 | 1.2 | `--tracking-tight` | contact email | `:768` |
| `--fs-display` | clamp(2.75, 6vw, 4.5) | 44–72 | Sans 700 | `--lh-tight` | `--tracking-tight` | hero h1 | `:285` |

- **Sizes change by 1 px at most.** The one exception is the 404 h1, which goes from 40 to 44 px on
  desktop because it joins `/mcp/`'s h1 on `--fs-2xl`. That brings the 15 absolute sizes in use today
  (counted with `grep` in `site.css`) down to 9 tokens. The two `em` sizes inside code and `/mcp/`
  headings stay relative.
- `--fs-3xl` is smaller than `--fs-2xl` below 978 px. The contact email has to stay at 26 px on a
  phone to fit on one line. These are named roles, not a strict ladder.
- **Weights in use:** Sans 400, 500, 600 and 700 (one variable file); Mono 400, 500 and 600. No
  italics anywhere, and there are none today. Bold is never synthesised.
- **Mono is for metadata only:** brand, kicker, dates, chips, labels, section titles, project titles
  and code. Paragraphs stay in Sans. damian sets all body text in Space Mono, and a short bio
  stretches to nearly three phone screens `[damian:mobile-fold.webp]` (P20 counter-evidence).
- **Minimum text size is 12 px**, used only for Mono labels and chips. Running text (paragraphs and
  bullets) never goes below 16 px. 14 px is for metadata, nav and the glance card's values.

### 7.5 Font loading

**Recommendation: self-host.** This is a Decision for Brett (§18), and the default is self-host. It
adds no build step: the files are static. What changes is that visitors no longer contact Google to
get fonts.

Today, `_layouts/default.html:31-33` and `mcp-surface/index.html:16-18` load a render-blocking
stylesheet from `fonts.googleapis.com`, which then loads files from `fonts.gstatic.com`. On
throttled mobile, FCP = LCP = 744 ms on `/` and 844 ms on `/mcp/` (audit issue 7). neuronet-arena
loads the same CSS non-blocking with `media="print" onload` (`neuronet-arena/index.html:18-20`).
Plex then arrives after first paint, and the page shifts: CLS 0.1643 on mobile (audit issue 5).

Bytes measured 2026-10-22 16:31 UTC with `curl` through the session proxy. The CSS is today's
`css2` URL. The files are the `/* latin */` blocks Google returns to a Windows Chrome UA (desktop)
and an Android Chrome UA (mobile):

| File | Desktop UA | Mobile UA | Hinting tables (`cvt`, `fpgm`, `prep`), read from the WOFF2 table directory |
|---|---|---|---|
| IBM Plex Sans, variable (one file for 400–700) | 45,712 B | **40,240 B** | desktop yes; mobile none |
| IBM Plex Mono 400 | 14,708 B | **10,052 B** | desktop yes; mobile none |
| IBM Plex Mono 500 | 14,888 B | **10,060 B** | desktop yes; mobile none |
| IBM Plex Mono 600 | 15,620 B | **10,120 B** | desktop yes; mobile none |
| Total | 90,928 B | **70,472 B** | |
| Google CSS | 930 B gzip (15,785 B raw), `cache-control: private, max-age=86400` | | |

| | Google Fonts (today) | Self-hosted (proposed) |
|---|---|---|
| Requests | CSS from `fonts.googleapis.com`, plus 4 files from `fonts.gstatic.com` | 4 same-origin files. The `@font-face` rules ride in `site.css`, which the 7.1 block grows by 1,788 B gzipped (measured) |
| Origins on first visit | 3 (site, googleapis, gstatic) | 1 |
| Render-blocking | Google's CSS | only same-origin CSS |
| Font bytes | 70,472 B (mobile UA) or 90,928 B (desktop UA) | 70,472 B for every visitor |
| Swap layout shift | depends on each OS's fallback | metric-matched fallback: 0 re-wrapped paragraphs in the test below |
| Caching | files long-cached by Google; CSS one day | GitHub Pages `cache-control: max-age=600`, measured on `/assets/css/site.css`. Revalidated with an ETag after 10 minutes. |
| Third parties | Google gets a request on every page view | none for fonts (GA stays as it is) |

**Files.** Add them to `assets/fonts/`, byte for byte as Google serves them to the mobile UA:

| Published path | Bytes | Source URL (2026-10-22) |
|---|---|---|
| `assets/fonts/ibm-plex-sans-latin-var.woff2` | 40,240 | `https://fonts.gstatic.com/s/ibmplexsans/v23/zYXzKVElMYYaJe8bpLHnCwDKr932-G7dytD-Dmu1syxeKYbSB4Zh.woff2` |
| `assets/fonts/ibm-plex-mono-latin-400.woff2` | 10,052 | `https://fonts.gstatic.com/s/ibmplexmono/v20/-F63fjptAgt5VM-kVkqdyU8n1i8q131nj-o.woff2` |
| `assets/fonts/ibm-plex-mono-latin-500.woff2` | 10,060 | `https://fonts.gstatic.com/s/ibmplexmono/v20/-F6qfjptAgt5VM-kVkqdyU8n3twJwlBFgsAXHNk.woff2` |
| `assets/fonts/ibm-plex-mono-latin-600.woff2` | 10,120 | `https://fonts.gstatic.com/s/ibmplexmono/v20/-F6qfjptAgt5VM-kVkqdyU8n3vAOwlBFgsAXHNk.woff2` |
| `assets/fonts/OFL.txt` | 4,456 | `https://raw.githubusercontent.com/google/fonts/main/ofl/ibmplexsans/OFL.txt` (identical to the Mono one, `cmp`) |

- **Reproduce:** `curl -A '<Android Chrome UA>' 'https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=IBM+Plex+Sans:wght@400;500;600;700&display=swap'`,
  then take the four `/* latin */` URLs. If Google has moved to a new version, sizes may differ.
  Record the new ones in the PR.
- **Why the mobile files:** they are 20,456 B smaller, because they carry no TrueType hinting. The
  only possible cost is slightly softer rendering on older Windows at small sizes (**estimated**,
  not testable here). Check `/` on a Windows machine before merging. If it looks worse, swap in the
  desktop files under the same names, at 90,928 B.
- **Licence:** SIL OFL 1.1, "Copyright © 2017 IBM Corp. with Reserved Font Name "Plex"" (the first
  line of `OFL.txt`, read 2026-10-22). Ship `OFL.txt` next to the files. Never re-subset, convert
  or edit them: a modified font may not carry the reserved name. If Brett would rather take the
  files from IBM's own release, that is the alternative in §18. Its sizes are not measured here.
- **Subset:** latin only, the same `unicode-range` Google uses. Measured with Chromium's
  `CSS.getPlatformFontsForNode`: these files don't contain the arrows ← → ↑ ↓ (U+2190–2193). Those
  render from the next font in the stack. The site uses → in "More on GitHub →" (`index.html:156`),
  ← in "← Brett Adams" (`mcp/index.html:10`) and in the mcp-surface back link
  (`mcp-surface/styles.css:38`), and ↓ in the hero link. The ·, –, —, é, ×, ’ and “ glyphs are in
  the subset.
- **`font-display: swap`**, so text always shows at once. The fallback is metric-matched, so the
  swap moves no lines.
- **Preload Sans only**, first in `<head>` after the meta tags:
  `<link rel="preload" href="{{ '/assets/fonts/ibm-plex-sans-latin-var.woff2' | relative_url }}" as="font" type="font/woff2" crossorigin>`.
  In `mcp-surface/index.html`, use the literal `/assets/fonts/ibm-plex-sans-latin-var.woff2`, and
  write the `@font-face` `url()`s in its `styles.css` copy as `/assets/fonts/…` (7.11).
  `crossorigin` is required, or the preload is fetched twice. `check-links.mjs` checks this `href`,
  so a wrong path fails CI. Mono is not preloaded: its fallback has the same advance width (below),
  so its swap moves nothing, and a second preload would compete with the CSS on a slow link
  (**estimated**).
- **Remove:** the two `preconnect` tags and the Google stylesheet, from `_layouts/default.html:31-33`
  and `mcp-surface/index.html:16-18`.

**Metric-matched fallback: how the numbers were found.** Measured 2026-10-22 in Chromium
141.0.7390.37, with canvas `measureText` at 1000 px and at 100 px. The width sample was six
sentences of the site's own copy: the lede, two Geotab bullets, the neuronet-arena intro, and the
mcp-surface and TechNest lines. This machine has no Arial. Liberation Sans and Liberation Mono stand
in, because they are built with Arial's and Courier New's advance widths. Arial is on macOS, iOS
and Windows.

| Font | Ascent / descent (per 1000) | Sample width at 100 px | Ratio to Plex | Descriptor |
|---|---|---|---|---|
| IBM Plex Sans 400 (the file above) | 1025 / 275 | 33,151.1 | — | — |
| Liberation Sans 400 (Arial widths) | 905 / 212 | 33,044.3 | 0.9968 | `size-adjust: 100.3%`; ascent 102.5 / 1.003 = `102.2%`; descent 27.5 / 1.003 = `27.4%` |
| Plex Sans 600 / 700 vs Liberation Sans Bold (heading sample) | — | — | 0.973 / 0.988 | `size-adjust: 98%`; `104.6%` / `28.1%` |
| IBM Plex Mono 400 | 1025 / 275 | 44,040.0 | — | — |
| Liberation Mono (Courier New widths) | 833 / 300 | 44,047.1 | 1.0002 | `size-adjust: 100%`; `102.5%` / `27.5%` |

**Check.** The same copy was set at phone widths in Plex and in the fallback face: the
neuronet-arena intro at 350 px, the homepage lede at 358 px, and three Experience bullets at
330 px. All five paragraphs came out the same height (223.2, 79.0, 76.8, 102.4 and 102.4 px). The
h1 "Brett Adams" was 244 px wide in Plex and 240 px in the fallback, on one line. In the same
Chromium, today's stack re-wrapped one of the five paragraphs (102.4 → 128.0 px).

**Limits.** Android has no Arial, Helvetica or Liberation Sans, so there the fallback is the system
sans (Roboto), with no override. That case is not measured. The Sans file is preloaded, so the
fallback shows only briefly on a first visit.

### 7.6 Space, grid, breakpoints, containers

| Token | px | Use it for |
|---|---|---|
| `--space-1` | 4 | tight gaps inside a line (job-head row gap, link rows) |
| `--space-2` | 8 | icon–text gaps, button-grid gaps on phones, glance title margin |
| `--space-3` | 12 | card internals, glance row gap, skip-link padding |
| `--space-4` | 16 | header gap, list gaps, `scroll-padding` margin |
| `--space-5` | 20 | glance card padding, `.edu` padding |
| `--space-6` | 24 | nav gap, project grid gap, kicker top margin, lede bottom margin |
| `--space-7` | 32 | section-title bottom margin, job bottom padding, hero top padding ≤ 640 px |
| `--space-8` | 48 | hero column gap, hero bottom padding ≤ 980 px, section padding ≤ 640 px |
| `--space-9` | 64 | hero padding ≥ 981 px (PR 4), section padding (PR 5; 88 px until then), `/mcp/` top padding |
| `--space-10` | 88 | `/mcp/` bottom padding; today's section padding until PR 5 |

- **Spacing below 8 px** inside small components stays literal: chip padding `3px 9px`, the
  `padding-top: 3px` that aligns a label, the `5px` margins between list items. Tokens are for
  layout spacing.

**What PR 1 does with spacing: renames only.** PR 1 swaps a literal for a token only where the token
has the same pixel value, so PR 1 moves nothing on screen and PR 5's height limits don't depend on
it. On today's `site.css` that is:
- `:35` `scroll-padding-top: 72px` → `calc(var(--header-h) + var(--space-4))` (56 + 16);
- `:77`, `:883` gutters → `var(--gutter)`;
- every margin, padding, gap or skip-link offset of 4, 8, 12, 16, 20, 24, 32, 48, 64 or 88 px → its
  `--space-*` token (24 px is `--space-6`, never `--gutter`, which is 16 px at ≤ 640): `:82`, `:85`, `:92`, `:111`, `:133`, `:252`, `:293`, `:311` (88, `--space-10`), `:316`,
  `:425`, `:500`, `:532`, `:541`, `:594`, `:607`, `:620`, `:646`, `:710` (the 16 only), `:734`,
  `:795`, `:823`, `:855`.

Every other layout literal is **not touched in PR 1**. It keeps its value until the PR that rebuilds
its component, which sets §8's value, or it stays as it is:

| `site.css` today | Value | Final value | Set by |
|---|---|---|---|
| `:147` `.nav-resume` padding | 6px 14px | `min-height: 36px; padding: 0 14px` (§8.1) | PR 3 |
| `:165` `.btn` padding | 0 18px | kept | — |
| `:197` `.actions` gap | 10px | kept (§6.5, §8.11) | — |
| `:223` `.hero` padding | 72px 0 80px | `var(--space-9) 0` (§6.5) | PR 4 |
| `:272` `.kicker` margin | 28px 0 6px | `var(--space-6) 0 var(--space-1)` (mock) | PR 4 |
| `:284` `.hero h1` margin | 0 0 18px | `0 0 var(--space-4)` (mock) | PR 4 |
| `:850` `.hero` padding ≤ 980 | 48px 0 56px | `40px 0 var(--space-8)` (§6.5) | PR 4 |
| `:349` `.job` padding-bottom | 44px | `var(--space-7)` (§8.4) | PR 5 |
| `:407` job list margin | 12px 0 14px | `.job-points`: `var(--space-3) 0 var(--space-4)` (mock) | PR 5 |
| `:311` / `:887` `.section` padding | 88px / 60px at ≤ 640 | `var(--space-9)` / `var(--space-8)` (§8.4) | PR 5 |
| `:431`, `:432`, `:491` card gap, padding, featured gap | 10px, 18px, 10px 28px | kept (§8.5–8.7) | — |
| `:464-466` badge gap, margin, padding | 7px, 8px, 3px 9px | margin dropped, the rest kept (§8.12) | PR 6 |
| `:516` `.links` gap | 4px 18px | `4px 20px` (§8.6) | PR 6 |
| `:542` `.also li` padding | 14px 0 | `var(--space-4) 0` (§8.9) | PR 6 |
| `:833` footer link margin | 18px | removed; `.footer-links` flex gap `var(--space-6)` (§8.16) | PR 7 |
| `:564` `.two-col` gap | 56px | kept | — |
| `:624` `.post-back`, `:650` `.case-facts`, `:767` `.contact-email` margins | 36px, 16px 0 44px, 28px | kept | — |
| `:790-791` `.not-found` padding | 120px | kept; 64 px at ≤ 640 (§8.17) | PR 8 |
| `:824-825` footer padding | 28px, 40px | kept | — |
| timeline geometry `:333`, `:340-342`, `:359-360`, `:906`, `:910` | 32, 6, 8, 7, 26 px | kept: the dots and line move together | — |

**§8 decides** any component spacing it specifies. "Kept" literals stay px; a later PR may move
one onto the scale only if §8 says so.

**Grid.** §8 owns each grid. For reference, the ones 7.9's `sizes` depend on:
- Hero: `minmax(0, 7fr) minmax(0, 5fr)`, `gap: var(--space-8)`, one column at ≤ 860 px (§8.2).
- Projects: 2 columns with `gap: var(--space-6)` above 860 px.
- Featured card: `minmax(0, 3fr) minmax(0, 2fr)`, screenshot left (§8.5).
- `.project-wide`: `minmax(0, 2fr) minmax(0, 3fr)`, screenshot left (§8.7).
- Both full-row cards use `gap: 10px 28px`.
- Cards keep 18 px padding and a 1 px border.
- If §8 changes any of these, recompute `sizes` with the formulas in 7.9.

**Breakpoints.** CSS variables can't be used in media queries, so these are literal everywhere.

| Width | What changes |
|---|---|
| 390 | The phone the mock and every check target. It is not a media query. |
| ≤ 640 px | `--gutter: 16px`. Section padding `--space-8`. Hero buttons become a 3-column grid. The glance "Now" and "Works with" rows hide and the label column narrows to 88 px. `.also` and skills rows go to one column. Status line `--fs-xs`, lede `--fs-md`. |
| ≤ 860 px | Section links move to `.nav-sections` under the header. The hero goes to one column, with the card under the buttons, and the jump link hides. Project cards go to one column, featured and wide included. |
| ≤ 980 px | Hero padding drops to 40 / 48 px. Skills and education go to one column. |
| ≤ 1000 px | Header nav gap drops to `--space-4` so the row fits at 861 px (§8.1). This is the one breakpoint off the list. |
| ≥ 1200 px | `--container` reached. Margins grow, and nothing else changes. |

**Containers.**
- `--container` (1200) for the site;
- `--container-post` (760) for `/mcp/` and the 404;
- `--container-demo` (1320) for demo pages, whose tools need the width.

All three use `padding: 0 var(--gutter)`.

### 7.7 Radius, borders, shadows, layers

- **Radius:**
  - `--radius-sm` (6): screenshot frames, inline code, the skip link, small controls inside cards.
    The focus outline keeps its own 4 px radius (§10.6).
  - `--radius` (10): cards, buttons, the header Résumé button, the glance card, `pre`, demo panels.
  - `--radius-pill`: chips, the status line, the Live demo badge.
  - Nothing else.
- **Borders:** always 1 px.
  - `--border` separates surfaces: cards, sections, the header and row dividers.
  - `--border-strong` marks buttons, controls, timeline dots and hover.
  - `--amber-line` is for the Résumé button, and `--good-line` for green pills.
- **Shadows:** none on cards or buttons. The look stays flat.
  - The two existing glows (status dot, current job) stay as decoration.
  - `--shadow-focus` is only for a focusable element whose outline would be clipped by an
    `overflow: hidden` ancestor. mcp-surface's `<textarea>` sits in `.editor { overflow: hidden }`
    (`mcp-surface/styles.css:85-92`), so use `.editor:focus-within { box-shadow: var(--shadow-focus); }`.
    §10 lists the clipped outline as issue N6. PR 9 checks it in the focus walk.
- **Focus:** §10.6 owns the rule: `outline: 2px solid var(--focus); outline-offset: 3px` on every
  page, demos included, with a 2 px offset inside nav and link rows. That is 10.90:1 against
  `--bg` and 9.22:1 against `--surface-2` (7.3). It is never animated (§9).
- **Layers:** `--z-header` (10) for the sticky header and `--z-skip` (20) for the skip link. Nothing
  else on the site gets a `z-index`. A new layer is added to 7.1 first.

### 7.8 Surfaces

From the bottom up:
1. `--bg` is the page.
2. `--bg-2` / `--term` is for chips, code and terminals.
3. `--surface` is for cards, buttons and the glance card.
4. `--surface-2` is for hover and nested panels.

There are no images in backgrounds. The hero keeps its two radial washes (teal at 85% 20%, amber at
10% 90%). The contact section keeps its amber wash (`site.css:763`). The hero grid overlay is
removed (7.2). The washes are why 7.3 checks text over composited colours.

### 7.9 Screenshot treatment

- **Format and sizes.** WebP only, which is what the site uses today. Each card image ships at
  800 px and 1600 px wide, in a 16:10 box. Every source image is already 16:10: 1280×800,
  1600×1000, or 2200×1376, which is 16:10.007 (fact sheet §12). So the box no longer crops 10% of
  each image the way today's 16:9 box does (`site.css:445`).
- **Encode:** Lanczos resize to the target width, WebP quality 80, `method=6`. Measured with PIL
  12.3 from today's files:

| Image | Source | Source bytes | `-800.webp` | `-1600.webp` |
|---|---|---|---|---|
| neuronet-arena | 1280×800 | 30,948 | 13,038 (800×500) | none: keep `neuronet-arena.webp` as the 1280w candidate |
| mcp-servers | 2200×1376 | 47,458 | 13,566 | 34,808 (1600×1001) |
| mcp-surface | 2200×1376 | 85,016 | 26,058 | 63,704 (1600×1001) |
| promptpal | 1600×1000 | 75,196 | 26,674 | 65,714 |
| technest | 1600×1000 | 69,026 | 26,310 | 59,014 |
| **Total** | | **307,644** | **105,646** | 223,240, plus 30,948 for neuronet-arena |

- **Bytes.** A 390 px phone at DPR 2 needs at most 640 device px per image (below), so it gets the
  800 set: 105,646 B against today's 307,644 B (−66%, measured on the files). A 1440 desktop at
  DPR 1 also gets the 800 set. Only DPR 2 desktops fetch the 1600s.
- **File changes.** Delete `mcp-servers.webp`, `mcp-surface.webp`, `promptpal.webp` and
  `technest.webp` once nothing references them. Today only `index.html:87-142` does (measured with
  `grep`). The future `mazefinder.webp` from the MazeFinder handoff
  (`handoff/mazefinder-live-demo.md:144-152`) ships as `mazefinder-800.webp` and
  `mazefinder-1600.webp` too.

Markup. This is §8.5's, unchanged. The other cards change only the file names and `sizes`.

```html
<a class="project-shot" href="{{ '/neuronet-arena/' | relative_url }}" tabindex="-1" aria-hidden="true">
  <img src="{{ '/assets/img/neuronet-arena-800.webp' | relative_url }}"
       srcset="{{ '/assets/img/neuronet-arena-800.webp' | relative_url }} 800w, {{ '/assets/img/neuronet-arena.webp' | relative_url }} 1280w"
       sizes="(min-width: 1200px) 650px, (min-width: 861px) 56vw, calc(100vw - 70px)"
       alt="" width="1280" height="800" loading="lazy" decoding="async" />
</a>
```

| Card | `srcset` | `sizes` (as §8.5–8.7) | `width` × `height` |
|---|---|---|---|
| Featured (`.project-featured`) | `<name>-800.webp 800w, <name>-1600.webp 1600w` (neuronet-arena: `neuronet-arena.webp 1280w` as the large one) | `(min-width: 1200px) 650px, (min-width: 861px) 56vw, calc(100vw - 70px)` | the largest file: 1280 × 800 for neuronet-arena, 1600 × 1000 otherwise |
| Standard (half row) | same pattern | `(min-width: 1200px) 524px, (min-width: 861px) 45vw, calc(100vw - 70px)` | 1600 × 1000 |
| Wide (`.project-wide`) | same pattern | `(min-width: 1200px) 435px, (min-width: 861px) 37vw, calc(100vw - 70px)` | 1600 × 1000 |

**Checking the strings** against the grid in 7.6. A slot that is too large only costs bytes; one
that is too small blurs the image.
- **One column:** the card image is viewport − 2 × gutter − 38 px (18 px padding and a 1 px border,
  each side).
  - At ≤ 640 px that is exactly `100vw - 70px`: 320 px at 390.
  - From 641 to 860 px it is 16 px narrower than `sizes` says, so slightly large. That is fine.
- **Featured:** 0.6 × (min(viewport, 1200) − 48 − 38 − 28).
  - 448 px at 861, where `56vw` = 482.
  - 651 px at 1199, where `56vw` = 671.
  - 650 px at ≥ 1200, measured today (`[brettadams0:measure-home.json.page.images]`).
- **Standard:** (min(viewport, 1200) − 48 − 24) / 2 − 38.
  - 357 px at 861 (`45vw` = 387); 526 px at 1199 (`45vw` = 540); 524 px at 1440, measured.
- **Wide:** 0.4 × (min(viewport, 1200) − 48 − 38 − 28).
  - 299 px at 861 (`37vw` = 319); 434 px at 1199 (`37vw` = 444).
- **Result:** every string is at or just above the real slot.
  - At 390 and DPR 2, a card needs 640 device px, so it picks the 800 file.
  - At 1440 and DPR 1, every card picks the 800 file.
  - At 1440 and DPR 2, cards pick the 1600 file (1280 for neuronet-arena).

```css
.project-shot { display: block; aspect-ratio: 16 / 10; overflow: hidden; border: 1px solid var(--border); border-radius: var(--radius-sm); background: var(--bg); }
.project-shot img { display: block; width: 100%; height: 100%; object-fit: cover; object-position: left top; }
```

- **No layout shift:** the `aspect-ratio` box and the `width`/`height` attributes reserve the space
  before the image loads, and both are required. `width`/`height` give the browser the 16:10 ratio.
  The CSS box sets the rendered size.
- **Which image is eager: none.** Every card image is `loading="lazy" decoding="async"`, with no
  `fetchpriority`. With Experience first, the featured card starts well below the fold at both
  sizes (today it is at y 1771 at 1440 and y 2564 at 390, audit §3). The LCP element is text (the
  lede). The audit already found `lazy` correct here ("Checked and fine").
- **Alt text** stays `alt=""`, because the screenshot link is an `aria-hidden` duplicate of the
  card's title link (§8, §10).
- **CI gap:** `check-links.mjs` checks `src` and `href` but not `srcset`. PR 6 adds a check to its
  verification: every URL in every `srcset` returns 200 under `measure.mjs`, or is found with `ls`
  in `_site`.

### 7.10 Terminal touches: kept on screen, removed from the accessible name

Today the CSS prefixes end up in accessible names: "## Experience", "Data Analyst Intern @ Geotab"
(audit issue 6, measured with `ariaSnapshot`).

**The fix is markup (§8.0).** Each prefix becomes a real `<span class="prefix" aria-hidden="true">`,
and the `::before` rules that inject them are deleted. An `aria-hidden` span works in every browser.
And since the prefix is now real text, axe checks its contrast, which is why no prefix may use
`--faint`.

| Touch | Today | After (markup) | Colour | Accessible name after |
|---|---|---|---|---|
| `> ` brand | `site.css:121-124` | `<span class="prefix" aria-hidden="true">&gt; </span>brett adams` | `--amber` 10.90:1 | "brett adams" (measured in the mock) |
| `// ` kicker | `site.css:278-281` | `<span class="prefix" aria-hidden="true">// </span>data &amp; software engineering` | `--dim` 5.45:1 (was `--faint` 3.37) | "data & software engineering" (measured) |
| `## ` section titles | `site.css:323-326` | `<span class="prefix" aria-hidden="true">## </span>Experience` | `--amber` | "Experience". Chrome reports "EXPERIENCE" because of `text-transform` (measured). |
| `## ` `/mcp/` h2 | `site.css:683-688` | same span. Keep `font-family: var(--mono); font-size: 0.85em` on `.post-body h2 .prefix`. | `--amber` | the heading text |
| `@ ` employer | `site.css:394-397`; hidden ≤ 640 px at `:917-919` | `<span class="job-org"><span class="prefix" aria-hidden="true">@ </span><span class="sr-only">at </span>Geotab</span>`, in all four roles (`index.html:27`, `:41`, `:54`, `:68`) | `--amber` | "Data Analyst Intern at Geotab" (measured) |
| `← ` / `→ ` arrows | `mcp/index.html:10`; `index.html:156`; `mcp-surface/styles.css:38` | a plain `aria-hidden` span | the link's colour | the link text alone |
| `> ` mcp-surface h1 | `mcp-surface/styles.css:46` (deleted, §8.20) | `<span class="prefix" aria-hidden="true">&gt; </span>mcp-surface` | `--amber` | "mcp-surface" |
| `> ` / `← ` neuronet-arena | `neuronet-arena/styles.css:68`, `:58` | the same spans | same | upstream only (§18) |

```css
/* site.css. Demo pages get .prefix and .sr-only from demo-shell.css (section 8.20). */
.prefix { color: var(--amber); white-space: pre; }   /* pre keeps the trailing space inside inline-flex parents */
.kicker .prefix { color: var(--dim); }
.post-body h2 .prefix { font-family: var(--mono); font-size: 0.85em; }
@media (max-width: 640px) { .job-org .prefix { display: none; } }   /* replaces content: "" at :917-919 */
.nowrap { white-space: nowrap; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
           overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }
```

- **`white-space: pre` is required.** `.site-name` and `.hero-jump a` are `inline-flex` (§8.1,
  §8.2), and a flex container drops whitespace at the edge of a child. The first build of the mock
  showed ">brett adams". The arrow in the jump link uses `&nbsp;↓` for the same reason.
- **Measured** in the mock with Chrome 141's accessibility tree (CDP
  `Accessibility.getFullAXTree`): "brett adams", "Data Analyst Intern at Geotab", "Projects and live
  demos", and kicker text "data & software engineering".
- Playwright's own `ariaSnapshot` computes names its own way. Check names with the CDP tree or a
  real screen reader.
- **Tried and dropped:** the CSS alt-text form (`content: "## " / ""`) gave the same names in
  Chrome during the first mock iteration. §8 chose spans because they need no browser support
  check, and axe can see them.

### 7.11 How the demo pages stay consistent

**One `:root` block, copied, never forked.** §7.1's block is the source. Under the default it sits at
the top of `assets/css/site.css`. Each demo page keeps a copy of it in its own stylesheet, and each
copy is compared with `diff` against `site.css`'s block whenever the page changes or is re-synced.
- Shell layout comes from `assets/css/demo-shell.css` (§8.20). It holds no tokens and reads the
  page's own `:root`.
- Under §18 #49 (a), `/mcp-surface/` and `/mazefinder/` link `assets/css/tokens.css` instead of
  carrying a copy. The synced demos keep their copies.

**`/mcp-surface/` (this repo's own files; PR 9):**
- `index.html`:
  - Replace lines 16-18 (preconnects and Google CSS) with the Sans preload (7.5), if PR 2 has
    self-hosted the fonts. Under §18 #3 (d) the Google stylesheet stays. Link
    `/assets/css/demo-shell.css` after `styles.css` (line 19).
  - `checks.js` is untouched: it is generated (`README.md:78-92`).
- `styles.css`:
  - Replace the `:root` block (`:4-22`) with a copy of §7.1's `:root` block, the `≤ 640 px` gutter
    rule and the reduced-motion durations. Every name the old block defines is in it, `--term`
    included.
  - Add §7.1's `@font-face` rules (the four Plex faces and the three fallback faces), with every
    `url()` written as `/assets/fonts/…`.
  - Replace the literals:
    - `#ffc97f` (`:28`) → `var(--amber-hi)`;
    - `#c4ccd6` (`:47`, `:160`) → `var(--soft)`;
    - `var(--faint)` (`:98`, `:161`) → `var(--dim)`;
    - `.wrap` (`:33`) → `max-width: var(--container-demo); padding: 0 var(--gutter);`;
    - the opacity states (`:214`, `:229`) → the rules in 7.3;
    - `transition` (`:206`) → §9 tokens.
  - Delete the rules `demo-shell.css` now provides: `:focus-visible` (`:30`), `.sr-only` (`:31`), the
    old header (`.top`, `.back`, `.top-links`, `:35-42`) and footer (`.foot`, `:232-233`).
  - Add `.editor:focus-within` (7.7).
  - Delete the `h1::before` prefix (`:46`); the h1 gets the span (7.10, §8.20).
  - Radius stays 10 px, which is now `--radius`, so it looks the same.
  - Tool text inside the editor and terminal may keep its 12–13 px literals.
  - Page chrome uses the scale: header, intro, footer.

**`/mazefinder/` (this repo's own files, when PR 13 ships it):** the same as `/mcp-surface/`: a copy
of the `:root` block and the font faces in its `styles.css`, and a link to `demo-shell.css`
(`handoff/mazefinder-live-demo.md:125-126` models it on `mcp-surface/`).

**`/neuronet-arena/` (synced; every change is upstream, in the neuronet-arena repo's `web/`, §18):**
- **Tokens.** Copy the `:root` block into `web/styles.css` in place of `:5-27`.
  - Keep the demo-only tokens: `--blue`, `--series-1`, `--series-2` and `--bad`. Chart lines pass
    3:1 on `--surface` (the three chart rows in 7.3).
  - A copy, not a link to the site's CSS, keeps the folder working when the upstream repo is opened
    on its own.
  - Whoever syncs it compares the two `:root` blocks with `diff`.
- **Shell.** Copy the `demo-shell.css` rules into `web/styles.css` too (§8.20's upstream table).
- **Fonts.**
  - Replace `index.html:16-20` (the `media="print" onload` swap) with the Sans preload and the
    7.1 `@font-face` rules. Write their `url()`s as `/assets/fonts/…`: same origin on the live
    site, so they share the homepage's cache.
  - Keep the fallback faces, so the demo still lays out the same when run elsewhere.
  - Target: mobile CLS from 0.1643 to under 0.05 (budget, §11).
- **Contrast and focus.**
  - `td.pending` (`:261`) → `var(--dim)`.
  - `#ffc370` (`:157`) → `var(--amber-hi)`.
  - `#c4ccd6` (`:69`, `:243`) and `#b9c2cd` (`:273`) → `var(--soft)`. Both pass today (the
    `.parity p` and `.explain p` rows in 7.3). This only removes the literals.
  - Add a link focus ring: `:focus-visible` as in 7.7. Today only buttons and inputs get one
    (`:158`, audit issue 13).
  - Replace the `h1::before` (`:68`) and `.back::before` (`:58`) prefixes with `aria-hidden`
    spans (7.10).
- **Radius** is already 10 px, so nothing changes.

**`/border-waits/` (synced verbatim from its repo's `web/`, like neuronet-arena; PR 12):**
- **Tokens and shell.** `web/styles.css` carries a copy of §7.1's `:root` block and a copy of the
  `demo-shell.css` rules (§8.20). It links neither of the site's files, so `web/` tests and local
  runs stay styled in the project repo.
- **Checked at each sync.** Whoever re-syncs the folder compares both copies with `diff` against
  `site.css`'s `:root` block and `assets/css/demo-shell.css`, as for neuronet-arena.
- **Fonts.** The 7.1 `@font-face` rules, with `url()`s written as `/assets/fonts/…`, plus the
  fallback faces, so a local run without the site's font files still lays out the same.
- `handoff/border-waits-pipeline.md` ("Head and shell") says the same; the research group keeps
  that file.

**Favicon.** The site favicon uses `#15181c` and `#67d2e6` (`assets/img/favicon.svg:2-4`), neither
of which is a token. neuronet-arena's inline icon uses `--bg`, `--amber` and `--teal`
(`neuronet-arena/index.html:15`). The low-cost fix is one favicon in token colours used by every
page. §12 decides it.

**Dark only, everywhere.**
- `color-scheme: dark` is in the `:root` block, and so in every copy of it.
- `<meta name="color-scheme" content="dark">` stays (`_layouts/default.html:6`).
- There is no light theme and no `prefers-color-scheme` branch.

### 7.12 Writer's notes

- **Writer's note:** this section self-hosts the mobile-UA font files, not the desktop-UA ones
  (45,712 B for Sans and so on): measured 70,472 B against 90,928 B, with the same glyphs and no
  hinting. The Windows check in 7.5 is the guard.
- **Writer's note:** a featured image could load eagerly only if it were in the desktop fold. With
  Experience first it is not, so every card image stays lazy.
- **Writer's note:** these tokens name values that were single-purpose literals, and other sections
  may use them: `--amber-line`, `--amber-wash`, `--good-line`, `--good-wash` (today's rgba literals)
  and `--container-post` (760 px).
- **Writer's note:** the single radius is 10 px, the demos' value, not the site's 12 px. That way
  neither demo changes, and the neuronet-arena PR has one item fewer.
- **Writer's note:** 7.9 uses §8's `sizes` strings and `width`/`height` values unchanged. The
  formulas check each string against the grid, so there is one answer.
- **Writer's note:** the header backdrop alpha (0.92) is §8.1's number. 7.3 confirms it with the
  same worst case: `--text` under the header.

## 8. Component specs

This section covers every component the redesign builds or changes: first in page order, then on
the inner pages. Each subsection gives:
- purpose and copy;
- markup;
- states;
- breakpoints;
- motion and accessibility;
- the research behind it.

Tokens are named, not valued, because §7 owns every value. Where a size is given in px, it is the
target rendered size; the builder uses the matching `--space-*` or `--fs-*` token from §7. The
first screen (header, hero, At a glance) must match the measured mock in §6
(`handoff/mocks/first-screen.html`). Where §6.5 gives a layout value, it wins.

Writer's notes. These are places where a source disagrees, or where this section had to make a call:

- **Project order.** `index.html:98-125` has MCP servers before mcp-surface. This section keeps the
  `index.html` order, as §5.5 does.
- **Image loading.** With Experience kept above Projects, the featured image is in neither fold, so
  every project image stays `loading="lazy"` (§7.9).
- **Badge placement.**
  - The MazeFinder handoff (`handoff/mazefinder-live-demo.md:146-147`) puts the "Live demo" badge
    inside the `h3`. Here the badge moves into the kind label instead (8.8; audit issue 23).
  - border-waits is a second featured card (8.5), as `handoff/border-waits-pipeline.md` ("Homepage
    card") and §5.5 say, so the half-width cards stay an even four.
- **Experience bullets.** §4.5 sets which Experience bullets the homepage carries and the one order
  in which PR 5 moves them into each role's `.job-more` disclosure: `:33`, `:59`, `:47`, `:34`,
  `:32`, `:61`, `:73`, `:60`, `:46`. `:31`, `:45`, `:58` and `:72` lead their roles and never move.
  "Moved" never means deleted (8.4). 8.4 estimates where the loop stops.
- **Changes to `/mcp/`.** Laying it out on the case-study outline (§5.4) needs four copy changes on
  Brett's long-form page: a "For" line, a new "At runtime" row for the diagram, a two-line "Limits"
  section and a reworded sentence tail. 8.18 lists all four. They are sourced, but each is new
  wording on a page Brett merged, so §18 #14 lists them as one veto-able decision. Default: ship.
- **"Updated" date on demo pages.** §5.4 gives demo pages an "Updated" date. 8.20 uses a version
  chip ("Checks from mcp-surface 0.3.0") instead, because it changes exactly when the code does
  (A19). If a date is preferred, use "Updated <Month YYYY>", set in the PR that last changed the
  folder.

### 8.0 Conventions that apply to every component

**Breakpoints.** Every table below uses these five ranges. The CSS uses `max-width` queries at 640,
860 and 980, as `site.css:848-920` does today.

| Label | Range | Test width |
|---|---|---|
| Phone | ≤ 640 px | 390 (and 320 for reflow, §10.13) |
| Small tablet | 641–860 px | 768 |
| Tablet | 861–980 px | 900 |
| Laptop | 981–1199 px | 1024 |
| Desktop | ≥ 1200 px | 1440 (container `--container`, 1200 px) |

**Terminal prefixes stay on screen, out of accessible names.** The `> `, `## `, `// ` and `@ `
prefixes become real text in an `aria-hidden` span with `white-space: pre`, and the `::before` rules
that inject them today are deleted (§7.10). The mock writes them this way:
`first-screen.html:364` (brand), `:392` (kicker), `:426` and `:444` (section titles), `:430`
(employer). Today headings are read as "## Experience" and "Data Analyst Intern @ Geotab" (audit
issue 6, measured with `ariaSnapshot`).

```html
<a class="site-name" href="/"><span class="prefix" aria-hidden="true">&gt; </span>brett adams</a>
<p class="kicker"><span class="prefix" aria-hidden="true">// </span>data &amp; software engineering</p>
<h2 id="experience-title" class="section-title"><span class="prefix" aria-hidden="true">## </span>Experience</h2>
<span class="job-org"><span class="prefix" aria-hidden="true">@ </span><span class="sr-only">at </span>Geotab</span>
```

```css
.prefix { color: var(--amber); white-space: pre; }   /* pre keeps the trailing space inside inline-flex parents */
.kicker .prefix { color: var(--dim); }
.post-body h2 .prefix { font-family: var(--mono); font-size: 0.85em; }
@media (max-width: 640px) { .job-org .prefix { display: none; } }
```

- **Why spans.** An `aria-hidden` span works in every browser, and axe checks its contrast, because
  it is real text. Measured in the mock, Chromium's accessibility tree names the brand "brett adams"
  and the heading "Data Analyst Intern at Geotab" (§6.2).
- **Why `white-space: pre`.** `.site-name` and `.hero-jump a` are `inline-flex`, and a flex
  container drops a space at the edge of a child (§6.7).
- **"@" is read as "at".** The `@ ` prefix is read as the word "at" through a hidden
  `<span class="sr-only">at </span>` inside `.job-org` (8.4).
- **Arrows are not prefixes.** Arrows inside link text (← → ↓) are real characters in an
  `aria-hidden` span, after a `&nbsp;` so the space survives, as the mock's "↓" is
  (`first-screen.html:403`).
- **The same rule applies on demo pages.** `mcp-surface/styles.css:46` (`h1::before "> "`) is
  deleted, and the h1 gets the span (8.20). `.back::before "← "` (`:38`) is deleted, because the
  shell's back link carries its arrow in an `aria-hidden` span (8.20).
- **Prefix colour.** The `// ` prefix moves from `--faint` (3.37:1) to `--dim` (5.45:1, §7.3).
  - Sighted readers still read it.
  - It only passes axe today because axe doesn't test generated text: the homepage shows 0
    `color-contrast` violations with `.kicker::before` in `--faint` (audit §1, `site.css:280`).

**Visually hidden text.**
- `.sr-only` is new in `site.css`, identical to the mock's (`first-screen.html:166`) and
  `mcp-surface/styles.css:31`:

```css
.sr-only { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0;
           overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }
```

**Lists keep their semantics.** Every unstyled list (`ul.chips`, `ul.also`) gets `role="list"`.
Without it, Safari drops list semantics from `list-style: none` lists.

**Link labels.**
- The visible text says what the link opens: "Live demo", "Case study", "Source", "npm",
  "Live app", "Résumé (PDF)", "Email".
- When a label repeats on a page, a visually hidden suffix names the project:
  `Source<span class="sr-only">: TechNest</span>`.
  - This makes a screen reader's links list unambiguous.
  - The visible label still starts the accessible name (WCAG 2.5.3).
- No link opens a new tab.
- Full rules are in §10.9.

**"Live demo" only points at something that runs.**
- A link or badge reading "Live demo" points only at a page on this site that runs the project's own
  code.
  - Today: `/neuronet-arena/` and `/mcp-surface/`.
  - Later: `/mazefinder/` and `/border-waits/`, once they ship.
- It never points at a repository or a video. That is anti-pattern A12: 3 of 7 "Live Demo" buttons
  on shaundataanalytics open GitHub
  `[shaundataanalytics:record.home.desktop.dom.features.demo_links]`.

**Analytics carriers.** Nothing here adds an event or a `section_id`. Each component keeps the
carriers in this table:

| Carrier | Where it lives after the redesign | Rule |
|---|---|---|
| `section#experience`, `#projects`, `#skills`, `#contact` | 8.4, 8.5–8.9, 8.13, 8.15 | Ids, element type and `aria-labelledby` unchanged |
| hero | `section.hero` (8.2) | **No `id`.** Adding one would start a new `hero` value (Decision) |
| At a glance | `div.glance` (§6.4, 8.3) | A `div`, with no `id`: no landmark and no `section_view` |
| education | `div#education` inside `#skills` (8.14) | Stays a `div`. A `section` would start a new value (Decision) |
| `data-project` | each `article.project` and the `li` rows in `ul.also` | Values exactly `neuronet-arena`, `mcp-servers`, `mcp-surface`, `promptpal`, `technest`, `nhl-arena`, `mazefinder`; later `border-waits` |
| `data-contact` | hero (8.2), contact (8.15), footer (8.16, on every default-layout page including 404), demo footer (8.20) | Values `email`, `linkedin`, `github` |
| résumé link | header, hero, contact, 404, demo shell | `href` ends in `Resume_BrettAdams.pdf`: no query, no fragment, no `download` attribute |
| any other `section[id]` | none | No new `<section id>` on `/`, `/mcp/` or `/404.html` without a Decision |

### 8.1 Header and nav (with the ≤ 860 px section-link row)

**Purpose.**
- Shows the brand, the four homepage sections and the résumé on every default-layout page (`/`,
  `/mcp/`, `/404.html`).
- On phones, the four section links become a second text row that is always visible, so Projects is
  one tap from the first screen (audit issue 1, High).

**Copy.**
- Brand: "brett adams", with a visual `> ` prefix [src: _layouts/default.html:42].
- Links: "Experience", "Projects", "Skills", "Contact", and the button "Résumé" [src:
  _layouts/default.html:44-48].
- Desktop order is today's, which §5.3 keeps.

**Markup.** Replace `_layouts/default.html:38-53`. This is the mock's markup
(`first-screen.html:360-386`, without the `.mk` markers), with `relative_url` on every `href`:

```html
<a class="skip-link" href="#main">Skip to content</a>

<header class="site-header">
  <div class="wrap site-header-inner">
    <a class="site-name" href="{{ '/' | relative_url }}"><span class="prefix" aria-hidden="true">&gt; </span>brett adams</a>
    <nav class="site-nav" aria-label="Main">
      <a class="nav-section" href="{{ '/#experience' | relative_url }}">Experience</a>
      <a class="nav-section" href="{{ '/#projects' | relative_url }}">Projects</a>
      <a class="nav-section" href="{{ '/#skills' | relative_url }}">Skills</a>
      <a class="nav-section" href="{{ '/#contact' | relative_url }}">Contact</a>
      <a class="nav-resume" href="{{ '/assets/resume/Resume_BrettAdams.pdf' | relative_url }}">Résumé</a>
    </nav>
  </div>
</header>

<nav class="nav-sections" aria-label="Sections">
  <div class="wrap nav-sections-inner">
    <a href="{{ '/#experience' | relative_url }}">Experience</a>
    <a href="{{ '/#projects' | relative_url }}">Projects</a>
    <a href="{{ '/#skills' | relative_url }}">Skills</a>
    <a href="{{ '/#contact' | relative_url }}">Contact</a>
  </div>
</nav>

<main id="main" tabindex="-1">
```

- **Why row 2 sits outside the header.** `nav.nav-sections` is a sibling **after** the sticky
  header, not a child of it. A sticky element sticks only within its parent, so this is how row 1
  stays put and row 2 scrolls away, with no JavaScript (§6.4).
- **The four section links are in the DOM twice.** Only one set is ever displayed, so only one set
  is in the accessibility tree and the Tab order at any width. The two `nav` landmarks have
  different labels: "Main" and "Sections".
- **`tabindex="-1"` on `main`** lets the skip link move focus, not only the scroll position (§10.2).

**CSS.** These are the mock's rules, and they are authoritative: `first-screen.html:176-195` (header),
`:291` (the 1000 px gap) and `:293-301` (the header part of the ≤ 860 px block). Values come from
§7, and §6.5 gives them per width.

```css
html { scroll-padding-top: calc(var(--header-h) + var(--space-4)); }
main:focus { outline: none; }
.site-header {
  position: sticky; top: 0; z-index: var(--z-header);
  background: rgba(11, 15, 20, 0.92);   /* --bg at 92% */
  -webkit-backdrop-filter: saturate(1.4) blur(12px);
  backdrop-filter: saturate(1.4) blur(12px);
  border-bottom: 1px solid var(--border);
}
.site-header-inner { display: flex; align-items: center; justify-content: space-between; gap: var(--space-4); height: var(--header-h); }
.site-name, .site-nav a { display: inline-flex; align-items: center; min-height: 44px; }
.site-name { color: var(--text); font: 600 var(--fs-base) var(--mono); text-decoration: none; }
.site-name:hover { color: var(--text); }
.site-nav { display: flex; align-items: center; gap: var(--space-6); font-size: var(--fs-sm); }
.site-nav a { color: var(--muted); text-decoration: none; }
.site-nav a:hover { color: var(--text); }
.site-nav .nav-resume {
  min-height: 36px; padding: 0 14px; border: 1px solid var(--amber-line); border-radius: var(--radius);
  color: var(--amber); font-weight: 500;
}
.site-nav .nav-resume:hover { background: var(--amber-wash); color: var(--amber-hi); }
.nav-sections { display: none; border-bottom: 1px solid var(--border); background: var(--bg); }
@media (max-width: 1000px) { .site-nav { gap: var(--space-4); } }
@media (max-width: 860px) {
  .site-nav .nav-section { display: none; }
  .nav-sections { display: block; }
  .nav-sections-inner { display: flex; justify-content: space-between; }
  .nav-sections a {
    flex: 1 1 0; min-height: 44px; display: inline-flex; align-items: center; justify-content: center;
    color: var(--soft); text-decoration: none; font: 500 var(--fs-sm) var(--sans);
  }
  .nav-sections a:hover { color: var(--text); }
}
```

The transitions on these links come from §9.2 (rows 1–3), and PR 1 adds them site-wide.

- **Header background.** It goes from 0.82 alpha to 0.92 (`site.css:101`). This is the worst case,
  with `--text` scrolled under the header and ignoring the blur:
  - at 0.82, `--muted` nav text drops to 3.92:1;
  - at 0.92, it stays at 5.29:1.

  Both figures are **computed** (2026-10-22, WCAG 2.x formula on the blended colour). §7 may pick
  another value, as long as that worst case stays at 4.5:1 or more.
- **Row 2 widths** (**measured** in the mock, §6.2). At 390 each of the four links is 90 px wide
  (`flex: 1 1 0` over the 358 px content box) and 44 px tall. Their labels are 72, 52, 35 and 50 px
  wide. At 320 each link is 72 px wide, "Experience" just fits, and `scrollWidth` is 320.

**States.**

| Element | Default | Hover | `:focus-visible` | Active | Visited |
|---|---|---|---|---|---|
| `.site-name` | `--text`, prefix `--amber` | unchanged | focus ring (§10.6) | unchanged | not styled |
| `.nav-section` (desktop) | `--muted` | `--text` | focus ring, `outline-offset: 2px` | `--amber` | not styled |
| `.nav-sections a` (row 2) | `--soft` (11.86:1, §7.3 row 16) | `--text` | focus ring, `outline-offset: 2px` | `--amber` | not styled |
| `.nav-resume` | `--amber` text, 1 px amber border at 50% alpha | amber 10% background, `--amber-hi` text | focus ring | amber 16% background | not styled |

Visited links are not styled anywhere in the nav. These are fixed destinations, and a second colour
would add nothing.

**Breakpoints.**

| Width | Behaviour |
|---|---|
| ≥ 981 | One row, `--header-h` tall. Brand on the left. On the right: Experience · Projects · Skills · Contact · [Résumé], gap per §6.5. The brand and each section link are 44 px tall (**measured** y 6–50 in the mock, §6.2), against 22 px today (`[brettadams0:measure-home.json.viewport.1440.small_targets_24]`). The Résumé button is 36 px (y 10–46) |
| 861–980 | Same as desktop |
| 641–860 | Row 1 is sticky: brand and Résumé. Row 2 is not sticky: the four links spread with `space-between`, each 44 px tall |
| ≤ 640 (390) | As 641–860, with a 16 px gutter |

**Motion and accessibility.**
- **Motion.** Colour changes only: `transition: color var(--dur-fast) var(--ease-out)`. No sliding
  or collapsing menu, and no JavaScript. Smooth scrolling to anchors stays off under
  `prefers-reduced-motion` (`site.css:38-42`; §9).
- **Focus not obscured.** `scroll-padding-top` keeps a focused or targeted element out from under
  the sticky row (WCAG 2.2 SC 2.4.11, §10.6).
- **Tab order.** It matches the visual order at both sizes:
  - Desktop: skip link, brand, the four section links, Résumé.
  - ≤ 860: skip link, brand, Résumé, then the four row-2 links.
- **No `aria-current`.** On `/` the targets are in-page sections, and on `/mcp/` none of them is
  the current page.

**Research.**
- **P4: a short text nav that fits a phone.**
  - wisl keeps a three-item nav with no hamburger `[wisl:mobile-fold.webp]`.
  - shud's four-item nav is fully visible on a phone `[shud:mobile-fold.webp]`.
- **Two P4 cautions:**
  - eugeneyan's nav row is wider than the phone, so the page is laid out at 453 px
    `[eugeneyan:record.home.mobile.dom.vw]` (A17). Hence the 320 px check.
  - richardshuai's mobile links are 16 px tall
    `[richardshuai:record.home.mobile.dom.a11y_manual.small_target_examples]`. Hence 44 px.
- **A3: no navigation on phones.** Four sites drop it, and so does Brett's site today. Examples:
  brittanychiang `[brittanychiang:record.home.mobile.dom.nav]` and beingbrute
  `[beingbrute:record.home.mobile.dom.menu_button]`.
- **P3: a text résumé link at 0 scrolls on the phone**, as damian has `[damian:mobile-fold.webp]`.
- **Plain labels with visible focus on every nav link:** mujtabasaqib
  `[mujtabasaqib:record.home.desktop.focus_check]`.
- **A13: icon-only links.** antfu's mobile nav is six unlabelled icons `[antfu:mobile-fold.webp]`.
  Hence text only.

### 8.2 Hero (with the "Projects and live demos" jump link)

**Purpose.**
- The hero is a résumé header in HTML. Within 6 seconds on a phone, a visitor sees: who this is,
  the current role and employer, the school, the graduation year, availability, the résumé and
  contact (P1–P3; hiring evidence H1, H2).
- On desktop, a plain text link brings proof of work one click closer without showing any project.
  The standing rule allows nothing live and no project preview in the hero.

**Copy.** Every line is sourced. This is the same as §6.3.

| Element | Copy | Source |
|---|---|---|
| `.status` | Graduating 2027 · Open to full-time roles | [src: index.html:7] |
| `.status`, once Brett gives a month | Graduating [BRETT: month] 2027 · Open to full-time roles | fallback: the line above (§18 #30) |
| `.status`, once Brett also gives a role family | Graduating [BRETT: month] 2027 · Open to full-time [BRETT: role family] roles | fallback: the line above (§18 #31). A start date that differs from graduation goes in the card's "Looking for" row (8.3) |
| `.kicker` | data & software engineering (visual `// ` prefix) | [src: index.html:8] |
| `h1` | Brett Adams | [src: index.html:9] |
| `.lede` | Data Analyst Intern at Geotab, on production pipelines in BigQuery and Airflow. Computer Science at the University of Windsor. | [src: index.html:10] |
| primary button | Résumé (PDF) | [src: index.html:12] |
| buttons | Email · LinkedIn · GitHub | [src: index.html:13-15] |
| `.hero-jump` (> 860 px only) | Projects and live demos ↓ | label; links to `#projects` |
| At a glance | see 8.3 | |

**Markup.** This replaces `index.html:4-19` and is the mock's hero (`first-screen.html:387-422`,
without the `.mk` markers and `.mk-host` classes):

```html
<section class="hero" aria-labelledby="hero-name">
  <div class="wrap hero-grid">
    <div class="hero-text">
      <p class="status"><span class="status-dot" aria-hidden="true"></span>Graduating 2027 · Open to full-time roles</p>
      <p class="kicker"><span class="prefix" aria-hidden="true">// </span>data &amp; software engineering</p>
      <h1 id="hero-name">Brett Adams</h1>
      <p class="lede">Data Analyst Intern at <a href="https://www.geotab.com">Geotab</a>, on production pipelines in BigQuery and Airflow. Computer Science at the University of Windsor.</p>
      <p class="actions">
        <a class="btn btn-primary btn-resume" href="{{ '/assets/resume/Resume_BrettAdams.pdf' | relative_url }}">Résumé (PDF)</a>
        <a class="btn" href="mailto:adamsbrett00@gmail.com" data-contact="email">Email</a>
        <a class="btn" href="https://www.linkedin.com/in/bretta/" data-contact="linkedin">LinkedIn</a>
        <a class="btn" href="https://github.com/brettadams0" data-contact="github">GitHub</a>
      </p>
      <p class="hero-jump"><a href="#projects">Projects and live demos<span class="nowrap" aria-hidden="true">&nbsp;↓</span></a></p>
    </div>
    <!-- 8.3 div.glance goes here -->
  </div>
</section>
```

- **Button order.** It changes from Résumé · GitHub · LinkedIn · Email (`index.html:12-15`) to
  Résumé · Email · LinkedIn · GitHub, because Email is the contact a recruiter uses (§6.6 #5).
- **Phone layout.** On phones the three secondary buttons share one row. That fixes the lone
  wrapped "Email" (audit issue 25).
- **No `id` on `section.hero`** (8.0).
- **The jump link** sits outside every `[data-project]`, so it sends no `project_click` (§6.3).

**Layout.** These are the mock's rules: `first-screen.html:197-241` (hero), `:292` (≤ 980 padding)
and the hero lines of `:293-318` (≤ 860 and ≤ 640). The key lines, with the button grid scoped to
the hero:

```css
.hero-grid { display: grid; grid-template-columns: minmax(0, 7fr) minmax(0, 5fr); gap: var(--space-8); align-items: start; }
.hero-jump { margin: 20px 0 0; font: 500 var(--fs-sm) var(--mono); }
.hero-jump a { display: inline-flex; align-items: center; min-height: 44px; text-decoration: none; }
.hero-jump a:hover { text-decoration: underline; }
@media (max-width: 980px) { .hero { padding: 40px 0 var(--space-8); } }
@media (max-width: 860px) {
  .hero-grid { grid-template-columns: minmax(0, 1fr); gap: var(--space-6); }
  .hero-jump { display: none; }   /* row 2's "Projects" does the same job */
}
@media (max-width: 640px) {
  .hero { padding: var(--space-7) 0 40px; }
  .hero .actions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-2); }
  .hero .actions .btn { justify-content: center; padding: 0 var(--space-2); }
  .hero .actions .btn-resume { grid-column: 1 / -1; }
}
```

- **Gaps and padding.** Gaps, hero padding and font tokens per breakpoint are in §6.5.
- **Every button is 44 px tall at every width**, the primary included (`.btn { min-height: 44px }`,
  8.11).
- **The 3-column button grid is hero-only.** The contact section (8.15) keeps a wrapping row.
- **Background.** §7.2 removes the faint grid (`site.css:231-243`) and keeps the two washes.

**States.**

| Element | Default | Hover | `:focus-visible` | Active |
|---|---|---|---|---|
| `.lede a` | `--text`, `--border-strong` underline | underline turns `--amber` (`site.css:299-306`) | focus ring | unchanged |
| `.hero-jump` | `--amber`, no underline | underline, `--amber-hi` | focus ring | `--amber-hi` |
| Buttons | see 8.11 | | | |

None of them has a visited style.

**Breakpoints.** The mock is measured at 390 and 1440 (§6.2).

| Width | Behaviour |
|---|---|
| ≥ 981 | Two columns: 7/12 text with the jump link, 5/12 glance card, inside 1200 px. The `## Experience` title (y 688–708) and the Geotab head line (y 740–764) sit above y = 900 at 1440 (**measured** in the mock, §6.2) |
| 861–980 | The same two columns, with 40 / 48 px hero padding |
| 641–860 | One column: text, then the card. The jump link is hidden, and row 2 of the nav takes its place. Buttons sit in one row |
| ≤ 640 (390) | One column. "Résumé (PDF)" is full width and 44 px tall. Email · LinkedIn · GitHub share one 3-column row, 114 px wide each (**measured**, y 430–474). The status line, h1, lede, all four buttons and the card's first three rows are inside the 844 px first screen |

**Motion and accessibility.**
- **Nothing animates.** No typing, rotating or fading text (§6.2: 0 running animations).
- **The LCP element is never faded in.** That element is `p.lede` today
  (`[brettadams0:measure-home.json.perf.mobile.lcp]`). A fade-in is what held wisl's LCP at 5.0 s
  `[wisl:record.summary.lcp_ms_desktop]`.
- **The status dot is decorative** (`aria-hidden`). The text carries the meaning.
- **The jump link** scrolls smoothly unless reduced motion is set (§9). Focus then starts from
  `#projects`.
- **Heading order.** The h1 is here, then the card's h2 (8.3), then the section h2s.

**Research.**
- **P1: the first screen as a résumé header** (9 sites):
  - damian gives class year, title, employer and team in two sentences
    `[damian:desktop-fold.webp]`;
  - shao puts graduation year and school in one sentence `[shao:mobile-fold.webp]`.
- **P2: availability next to the résumé button.** beingbrute `[beingbrute:desktop-fold.webp]`, and
  richardshuai's "Seeking 2027 New Grad Roles" `[richardshuai:mobile-fold.webp]`.
- **P14: contact as text buttons.** florea `[florea:desktop-fold.webp]`.
- **Anti-patterns avoided:**
  - A8, typed or rotating role lines: mehdio `[mehdio:mobile-fold.webp]`, eugeneyan
    `[eugeneyan:record.summary.hero_text_mobile]`;
  - A9, slogans: ssp `[ssp:tiles/m-04.png]`;
  - A4, splash gates.
- **Hiring evidence:** S01 and S02 (H1, H2).
- **The jump link** is P5's allowed fix (§3.4 P5).

### 8.3 At a glance card (new, `div.glance > h2.glance-title + dl.glance-list`)

**Purpose.**
- Fills the empty right half of the desktop hero (audit issue 9) with static résumé facts.
- Gives phones a dated summary before the timeline.
- Puts what came of the work on the first screen (the "Results" row), for the business reader
  (§4.1).
- Text only: no project, image or demo. Any of those would be the removed hero preview (audit issue
  9; §3.4 P19).

**Copy.** Exactly as §6.3 ships it, row by row, in this order. Each value is one line of text.

| `dt` | `dd` | Source |
|---|---|---|
| Now (hidden ≤ 640) | Data Analyst Intern, Geotab · May 2026 – Present | [src: index.html:27-28] |
| Before | Ontario Ministry of Transportation (co-op, 2025) · CIMCO Refrigeration (2023; co-op, 2024) | [src: index.html:41-42, :54-55, :68-69]. "Co-op" where the source says so (`:41`, `:54`) |
| Studying | Honours Computer Science, University of Windsor · 2022 – 2027 | [src: index.html:175-177]; wording per fact sheet D3/D4 |
| Looking for (**only once Brett answers**) | [BRETT: target roles, e.g. data engineering, analytics or software roles] | §18 #31. Left out until then, because it would repeat the status line |
| Based in (**only once Brett answers**) | [BRETT: city / open to relocation?] | §18 #32. Left out until then |
| Results | Power BI dashboards that replaced manual reporting (MTO) · Processing time cut from hours to minutes (MTO) · Python training for over 20 employees (CIMCO) | [src: index.html:45], [src: index.html:46], [src: index.html:61]. A first-screen copy option under §18 #4, default ship |
| Works with (hidden ≤ 640) | chips: Apache Airflow · BigQuery · SQL · Python · Power BI · GitLab CI/CD | [src: index.html:167 (Apache Airflow), :36, :49, :63]; D7 default (§18 #5), the name Experience uses |

- **Results** quotes the outcome half of three sourced bullets, each with its employer. It is not a
  stat tile and carries no number the bullet doesn't (A11). When Brett gives a number (§18 #33), it
  goes into the matching item in §4.5 item 3's form. If a future 1440 card ever ends below y = 900,
  the third item is dropped first. Today it ends at y 559 (measured, §6.2).
- **At ≤ 640, "Works with" is hidden and "Results" takes its place.** The tools repeat in every
  job's chips and in Skills.

**Markup.** The mock's card (`first-screen.html:406-420`), without the `.mk` marker and the
`.mk-host` class:

```html
<div class="glance">
  <h2 class="glance-title" id="glance-title">At a glance</h2>
  <dl class="glance-list">
    <div class="glance-now"><dt>Now</dt><dd>Data Analyst Intern, Geotab · <span class="nowrap">May 2026 – Present</span></dd></div>
    <div><dt>Before</dt><dd>Ontario Ministry of Transportation <span class="nowrap">(co-op, 2025)</span> · CIMCO Refrigeration <span class="nowrap">(2023; co-op, 2024)</span></dd></div>
    <div><dt>Studying</dt><dd>Honours Computer Science, University of Windsor · <span class="nowrap">2022 – 2027</span></dd></div>
    <!-- "Looking for", then "Based in", go here once Brett answers (§6.3) -->
    <div class="glance-results"><dt>Results</dt><dd>Power BI dashboards that replaced manual reporting (MTO) · Processing time cut from hours to minutes (MTO) · Python training for over 20 employees (CIMCO)</dd></div>
    <div class="glance-tools"><dt>Works with</dt><dd><ul class="chips" role="list"><li>Apache Airflow</li><li>BigQuery</li><li>SQL</li><li>Python</li><li>Power BI</li><li>GitLab CI/CD</li></ul></dd></div>
  </dl>
</div>
```

- **A `div`: no landmark and no `section_view`.** A `section` with an accessible name would be a
  region, and a `section` is what the observer watches (§5.2, §6.4, §13.3).
- **`<div>` groups inside `<dl>`** are valid HTML.
- **The h2 "At a glance"** makes the card a heading-navigation stop.

**Style.** The mock's rules, `first-screen.html:243-257`, and the card lines of `:293-318`:
- the card is `--surface` with a 1 px `--border`, `--radius` and 20 px padding (16 px at ≤ 640);
- `.glance-title`: Mono 600, `--fs-xs`, `--muted`, uppercase with `--tracking-caps`;
- each row is a grid, `96px minmax(0, 1fr)` (88 px at ≤ 640) with a 12 px gap, 10 px padding and a
  `--border` line on top;
- `dt`: Mono `--fs-xs` `--muted`; `dd`: `--fs-sm` `--soft`, line height 1.5;
- the chips keep §8.10's rule. The mock's own `.chips` lines (`:258-262`) are not ported;
- at ≤ 640: `.glance-list > .glance-now, .glance-list > .glance-tools { display: none; }`. The
  child selector is needed to beat `.glance-list > div` (§6.7).

**States.** Static: no hover and nothing to focus.

**Breakpoints.**

| Width | Behaviour |
|---|---|
| ≥ 861 | Right hero column, top-aligned with the status line. 96 px label column. At 1440 the card is y 121–559 (**measured**, §6.2) |
| 641–860 | Full width under the buttons, `max-width: 560px` |
| ≤ 640 (390) | Full width, 88 px label column, labels beside values. The "Now" row is hidden (the lede says the same) and so is "Works with". The "Before", "Studying" and "Results" rows fall inside the 844 px first screen (**measured** in the mock, y 540–834, §6.6 #6) |

**Motion and accessibility.**
- **Motion.** Nothing animates.
- **Values must stay true.** "Present" changes when Brett gives the Geotab end date
  (`[BRETT: Geotab end date]`). A frozen "Present" is anti-pattern A19: darshshah's role still read
  "3 mos" ten months on `[darshshah:mobile-fold.webp]`.

**Research.**
- **P19: a two-column desktop first screen** (an idea: brittanychiang and ssp; kushalgoel is a
  credited copy, A23). For example `[brittanychiang:desktop-fold.webp]`.
- **I4: a "previously" list, one phrase per employer.** shao `[shao:desktop-fold.webp]`, and
  taniarascia's dated rows `[taniarascia:desktop-fold.webp]`.
- **P2: availability rows.**
- **P13 and A11: numbers beside their source, not unsourced stat tiles,** such as deshraj-jogiya's
  "98%+ ETL Reliability" `[deshraj-jogiya:desktop-fold.webp]`. The card holds dated facts and
  sourced outcomes only.

### 8.4 Timeline entry (`.job`, with the new `.job-more` disclosure)

**Purpose.**
- Each role is one compact, dated block: title, employer, dates, place, its lead bullets and its tool
  chips.
- The rest of the role's bullets are one click away in a native disclosure. They are not deleted.
- This lets Projects start no lower than today at 390 while Experience stays first. H4 says the
  internship is the strongest new-grad signal.
- Which bullets the homepage carries, and the order in which they move, are §4.5's call. This section
  turns them into markup.

**Copy.** Titles, dates and bullets are verbatim from the sources, with the three trims §4.5 and §18
#6–#7 allow. `index.html` matches `resume.tex` for 12 of 13 bullets (fact sheet §5). All 13 of
today's bullets stay on the page. The number after each bullet is its place in the move order.

| Role | Title / org / when | Bullets, in display order (move order) | Chips |
|---|---|---|---|
| Geotab (current, `.job-current`) | "Data Analyst Intern" / "Geotab" / "May 2026 – Present · Oakville, ON" [src: index.html:27-28] | "Maintain and debug production Apache Airflow DAGs on Cloud Composer that run the SalesOps data pipelines." [src: resume/resume.tex:106] (never moved; the site says "Google Cloud Composer", `index.html:31`; D6 default) · "Write and deploy production BigQuery SQL for Revenue Operations tables." [src: index.html:32, trimmed, §18 #7] (5) · "Ship changes through GitLab CI/CD, …" [src: index.html:33] (1) · "Carry out live table migrations …" [src: index.html:34] (4) | BigQuery · Apache Airflow · Cloud Composer · SQL · GitLab CI/CD [src: index.html:36; "Apache Airflow" :167; D7] |
| MTO | "Data Management Associate, Co-op" / "Ontario Ministry of Transportation" / "Jan – Aug 2025 · St. Catharines, ON" [src: index.html:41-42] | "Built Power BI dashboards on Azure Databricks and SQL that replaced manual reporting across business units." [src: index.html:45] (never moved) · [src: index.html:46] (9) · [src: index.html:47] (3) | Power BI · Azure Databricks · SQL · VBA [src: index.html:49] |
| CIMCO 2024 | "Digital Solutions, Co-op" / "CIMCO Refrigeration" / "Apr – Sep 2024 · Burlington, ON" [src: index.html:54-55] | "Built Flask web applications that deliver real-time data and alarm information to NHL arena operations teams." [src: index.html:58, trimmed] (never moved) · "Designed and ran hands-on Python training sessions for over 20 employees." [src: index.html:61] (6) · [src: index.html:59] (2) · [src: index.html:60], shipped as "Automated controller data collection with a Python tool, replacing manual data entry." until `[BRETT: what does "CAG" stand for?]` is answered (§4.5) (8) | Python · Flask · JavaScript [src: index.html:63] |
| CIMCO 2023 | "Digital Solutions" / "CIMCO Refrigeration" / "Apr – Sep 2023 · Burlington, ON" [src: index.html:68-69]; title is `[BRETT: the 2023 CIMCO title]` | "Built Power Platform tools adopted by several departments to automate repetitive manual workflows." [src: index.html:72] (never moved) · [src: index.html:73] (7) | Power Platform · SharePoint [src: index.html:75] |

- **The Flask bullet stays, trimmed.** `:58` is CIMCO 2024's first bullet, without its "including a
  thermal imagery viewer used by arena technicians" tail. It is the one line that says who used the
  software, and it is why the role's "Flask" chip is there. The "NHL Arena Web Interface" row in
  8.9 stays too, as today (`index.html:58`, `:154`).
- **The fit loop (PR 5).** Start with all 13 bullets shown. While `#projects-title` is over a §4.3
  limit, move the next bullet in the move order into its role's `details.job-more`, then
  re-measure: `:33`, `:59`, `:47`, `:34`, `:32`, `:61`, `:73`, `:60`, `:46`. Outcome bullets go last,
  and the CI/CD bullet goes before the migrations bullet. A role's first bullet, its dates and its
  chips never move.
- **Moving bullets back out.** If a later change frees height, move bullets back one at a time in
  the reverse order (`:46`, `:60`, `:73`, `:61`, `:32`, `:34`, `:47`, `:59`, `:33`) and re-measure
  after each. Never ship a state that breaks a limit.
- **Numbers from Brett.** When Brett supplies a number (§4.5's `[BRETT: …]` items), it goes into
  the bullet it measures (§4.5 item 3), with no new bullet. For example: "Built
  `[BRETT: N]` Power BI dashboards on Azure Databricks and SQL that replaced manual reporting across
  `[BRETT: N]` business units." The fallback is the sourced line as shown.
- **No summary line.** `## Experience` is followed directly by the timeline. §4.5 item 2's summary
  sentence is a non-default option under §18 #6; it would repeat the bullets and the glance card.

**Markup.** One entry, replacing `index.html:24-77`. It is shown in the estimated end state of the
fit loop (the height check below): two Geotab bullets shown and two in the disclosure.

```html
<li class="job job-current">
  <div class="job-head">
    <h3 class="job-title">Data Analyst Intern <span class="job-org"><span class="prefix" aria-hidden="true">@ </span><span class="sr-only">at </span>Geotab</span></h3>
    <p class="job-when">May 2026 – Present · Oakville, ON</p>
  </div>
  <ul class="job-points">
    <li>Maintain and debug production Apache Airflow DAGs on Cloud Composer that run the SalesOps data pipelines.</li>
    <li>Write and deploy production BigQuery SQL for Revenue Operations tables.</li>
  </ul>
  <details class="job-more">
    <summary>2 more<span class="sr-only"> from Geotab</span></summary>
    <ul class="job-points">
      <li>Ship changes through GitLab CI/CD, from branch development through staging validation to production.</li>
      <li>Carry out live table migrations against fixed infrastructure deadlines, coordinating with dataset owners.</li>
    </ul>
  </details>
  <ul class="chips" role="list"><li>BigQuery</li><li>Apache Airflow</li><li>Cloud Composer</li><li>SQL</li><li>GitLab CI/CD</li></ul>
</li>
```

- **Accessible name of the h3.** It becomes "Data Analyst Intern at Geotab": the `@ ` is an
  `aria-hidden` span (8.0) and the hidden "at " is spoken. Today the name is "Data Analyst Intern @
  Geotab" (audit issue 6). The mock has the same markup (`first-screen.html:430`).
- **Summary texts.** Only a role that has a `details.job-more` after the fit loop gets a summary. It
  reads "<N> more" with a hidden suffix naming the employer, so each accessible name is unique:
  "<N> more from Geotab", "<N> more from Ontario Ministry of Transportation", "<N> more from CIMCO
  Refrigeration, 2024", "<N> more from CIMCO Refrigeration, 2023". N is the number of bullets moved.
- **CSS class.** `.job > ul:not(.chips)` (`site.css:405-418`) becomes `.job-points`, as the mock's
  `first-screen.html:286-288`.
- **Spacing.**
  - `.job` padding-bottom goes from 44 px to 32 px (`var(--space-7)`).
  - `.section` padding is 64 px at desktop and 48 px at ≤ 640.

**States.**
- **`summary`.**
  - Default: Mono, `--fs-xs`, `--amber`, with the native disclosure marker coloured `--amber` and
    `cursor: pointer`.
  - Size: `min-height: 24px`; 32 px at ≤ 640, inside the 24 px minimum (§10.7). Keep
    `display: list-item`.
  - Hover: underline. Focus: the focus ring.
  - Open: the marker turns and the list shows. No height animation.
- **`.job-current::before`** (`site.css:368-372`) is a filled amber dot; past roles have hollow
  `--border-strong` dots. Shape and fill differ, not only colour, and the text "Present" carries the
  meaning (§10.10).

**Breakpoints.**

| Width | Behaviour |
|---|---|
| ≥ 861 | `.job-head` on one line: title and org on the left, `.job-when` on the right (`site.css:374-380`). Bullets `max-width: 54em` |
| 641–860 | Same, wrapping when needed |
| ≤ 640 (390) | Org on its own line (`.job-org { display: block }`), its `@ ` hidden (`.job-org .prefix { display: none; }`, which replaces `site.css:917-919`), timeline indent 26 px (`site.css:905-911`) |

**Height check.** The first screen is **measured** in the mock (§6.2). Experience is **estimated**:
the mock's first screen plus all four roles built with this section's markup and CSS in a scratch
copy (not committed), measured at each step of the fit loop. The real page will differ by a few
pixels per role, so PR 5 measures it.

| Item | Today (**measured**) | After | Limit (§4.3) |
|---|---|---|---|
| Hero bottom at 390 | ≈ 560 | 892 (**measured** in the mock: row 2 of the nav and the card; no jump link) | — |
| `#projects-title` bottom at 390, all 13 bullets shown | 2,513 | ≈ 2,704 (**estimated**) | ≤ 2,532 |
| at 390 after moving `:33`, `:59`, `:47` | — | ≈ 2,602 (**estimated**) | ≤ 2,532 |
| at 390 after moving `:33`, `:59`, `:47`, `:34` | — | ≈ 2,521 (**estimated**; 11 px margin) | ≤ 2,532 |
| at 390 after also moving `:32` | — | ≈ 2,464 (**estimated**; 68 px margin) | ≤ 2,532 |
| `#projects-title` bottom at 1440 | 1,720 | ≈ 1,677 with all 13 shown and ≈ 1,674 after four moves; at most ≈ 1,705 in any state of the loop (**estimated**) | ≤ 1,800 |
| `#projects` from the first screen at 390 | 3 scrolls, no shortcut | 0 scrolls + 1 tap (row 2 "Projects") | — |

- **Where the loop should stop:** after four or five moves (**estimated**). After four: 9 bullets
  shown, and three summaries, "2 more from Geotab", "1 more from Ontario Ministry of
  Transportation" and "1 more from CIMCO Refrigeration, 2024". CIMCO 2023 keeps both bullets. 1440
  needs no move on its own.
- **If the measurement still misses the limit** after all nine moves (≈ 2,236 at 390,
  **estimated**), the remaining lever is the phone card in §6.5. That is a §6 change, so the builder
  reports it to Brett rather than moving a role's first bullet.

**Motion and accessibility.**
- `<details>` needs no JavaScript, and it opens with Enter or Space.
- It opens without animation.
- The bullets in the disclosure are also in the résumé PDF, which keeps every bullet.

**Research.**
- **P6: compact dated blocks with chips** (7 sites):
  - brittanychiang puts co-op internships in the same dated timeline
    `[brittanychiang:tiles/m-01.png]`;
  - deshraj-jogiya puts the engagement type in a pill `[deshraj-jogiya:tiles/d-05.png]`;
  - darshshah uses full tool names `[darshshah:desktop-fold.webp]`.
- **P7: internships shown as real roles.** florea's intern-to-full-time pair
  `[florea:tiles/d-exp.png]`.
- **A18 and A19:** undated or stale roles.
- **Hiring evidence:**
  - S02: dates are checked;
  - S09: "short, with numbers";
  - S04: internships weigh most.
- **The disclosure itself is an idea, not a pattern.** No site in the set uses one.

### 8.5 Featured project card (`.project-featured`)

**Purpose.** The favourite demo gets the full row: a still screenshot, the name, a kind label with
the Live demo badge, one line, three chips, and links. The demo itself runs only on its own page
(standing rule; P8).

**Copy, neuronet-arena:**

| Element | Copy | Source |
|---|---|---|
| `h3` | neuronet-arena | [src: index.html:89] |
| kind (8.8) | badge "Live demo" + "runs in your browser" | [src: neuronet-arena@07bc91f:README.md:12-14]; [src: index.html:89] |
| line | Forty agents with their own neural networks evolve to forage. Watch any agent's network fire as it moves. | [src: index.html:90] |
| chip 1 | 13.7× the food with selection on | [src: neuronet-arena@07bc91f:README.md:46] "13.7× as much" (stated); from README.md:41's 13.39 vs 0.98, 13.39 / 0.98 = 13.66 (computed, fact sheet §7.1); mean of the last 10 of 150 generations over 8 seeds |
| chip 2 | JS matches Python bit for bit | [src: neuronet-arena/index.html:38]; [src: neuronet-arena@07bc91f:README.md:9-10] |
| chip 3 | No ML libraries | [src: index.html:91] |
| links | Live demo → `/neuronet-arena/` · Source → GitHub | [src: index.html:93-94] |

- **Flagged chip:** "13.4 food vs 1.0 for the control" (`index.html:91`) is cryptic without the demo
  page (audit issue 26). The replacement keeps the sourced numbers' meaning.
  - It uses the README's own "13.7×", stated at README.md:46 and computed from its table as
    13.39 / 0.98 = 13.66 (fact sheet §7.1).
  - "the food" keeps the README's "as much": "13.7× more" could be read as 14.7× as much.
  - It names what the control is: selection switched off (README "selection switched off").
  - It is 32 characters, the limit in 8.10 (counted with Python `len()`).
- Chip 2 replaces "Python + JavaScript" (`index.html:91`). It surfaces the technical decision the
  audit says the homepage hides (§2 #12b).

**Markup:**

```html
<article class="project project-featured" data-project="neuronet-arena">
  <a class="project-shot" href="{{ '/neuronet-arena/' | relative_url }}" tabindex="-1" aria-hidden="true">
    <img src="{{ '/assets/img/neuronet-arena-800.webp' | relative_url }}"
         srcset="{{ '/assets/img/neuronet-arena-800.webp' | relative_url }} 800w, {{ '/assets/img/neuronet-arena.webp' | relative_url }} 1280w"
         sizes="(min-width: 1200px) 650px, (min-width: 861px) 56vw, calc(100vw - 70px)"
         alt="" width="1280" height="800" loading="lazy" decoding="async" />
  </a>
  <h3 class="project-name">neuronet-arena</h3>
  <p class="project-kind"><span class="badge-live">Live demo</span> runs in your browser</p>
  <p class="project-line">Forty agents with their own neural networks evolve to forage. Watch any agent's network fire as it moves.</p>
  <ul class="chips" role="list"><li>13.7× the food with selection on</li><li>JS matches Python bit for bit</li><li>No ML libraries</li></ul>
  <p class="links">
    <a href="{{ '/neuronet-arena/' | relative_url }}">Live demo<span class="sr-only">: neuronet-arena</span></a>
    <a href="https://github.com/brettadams0/neuronet-arena">Source<span class="sr-only">: neuronet-arena</span></a>
  </p>
</article>
```

- **Screenshot rule:** the `.project-shot` `href` equals the first link in `.links`. The screenshot
  stays `aria-hidden="true" tabindex="-1"` with `alt=""` (decision and reasons in §10.8).
- **Image set:** 800 px and 1600 px WebP (§7). If the source is narrower than 1600, the widest
  candidate is the source itself: neuronet-arena is 1280×800 (fact sheet §7.1), so `1280w`.
- `sizes` comes from the measured rendered width, 650 px at 1440
  (`[brettadams0:measure-home.json.page.images]`).
- `width`/`height` are the largest file's pixels. The box is `aspect-ratio: 16 / 10`. All five
  screenshots are 16:10 (1280×800, 2200×1376, 1600×1000), so nothing is cropped. Today's 16 / 9 box
  (`site.css:445`) cuts their bottom edge.

**Grid** (`site.css:486-501`, adapted for the extra kind row):

```css
.project-featured { grid-column: 1 / -1; display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  grid-template-rows: repeat(4, auto) 1fr; gap: 10px 28px; }
.project-featured .project-shot { grid-row: 1 / -1; margin-bottom: 0; }
```

**Later, border-waits** (only after it ships; `handoff/border-waits-pipeline.md`, "Homepage card"):
- A second `article.project.project-featured` directly after neuronet-arena, with
  `data-project="border-waits"` (new value).
- `h3` "border-waits"; kind: `Live demo · SQL in your browser` (badge + "SQL in your browser", 8.8).
- Line: "An hourly pipeline over the wait estimates the Canadian and U.S. border agencies publish,
  tested with dbt. Query it with SQL in your browser." The homepage spells the agencies out; the
  page itself names CBSA and CBP. The values are estimates the agencies publish, not measured
  crossing times (`handoff/border-waits-pipeline.md:31`).
- Chips: "dbt + DuckDB" · "Tests gate every deploy" · "CBSA + U.S. CBP open data". The second
  restates the pipeline's rule that a run deploys only when `dbt build` has no errors (`:281`). The
  third names the sources and their terms (`:58-60`: Open Government Licence – Canada; CBP public
  domain). Neither repeats the kind line, as "SQL in the browser" did.
- Screenshots: `border-waits-800.webp` and `border-waits-1600.webp`, 16:10, with the featured card's
  `srcset` and `sizes` (§7.9).
- Links: Live demo → `/border-waits/` · Source.
- The half-width count is unchanged, so 8.7 does not trigger.

**States.**
- Card: `--border` by default. Hover and `:focus-within` → `--border-strong` (today hover only,
  `site.css:439-441`). `:focus-within` shows a keyboard user which card they are in.
- Links: see 8.6. The card itself is not a link.

**Breakpoints.**

| Width | Behaviour |
|---|---|
| ≥ 1200 | Full row: shot 3/5 (650 px), text 2/5 |
| 981–1199, 861–980 | Same split, scaling |
| ≤ 860 (768, 390) | Stacked like a standard card (`site.css:868-878`): shot, name, kind, line, chips, links. Shot 320 px wide at 390 (rendered 200 px tall, estimated) |

**Motion and accessibility.**
- No hover zoom or lift on the screenshot; only the border colour changes (`--dur-fast`).
- Nothing on the card loads demo code. `/neuronet-arena/` is fetched only when the link is followed.
- Heading text is exactly the project name: the badge is no longer inside the h3 (audit issue 23).

**Research.**
- P8, demos on their own page from a static card (6 sites): redblobgames, whose homepage has no JS
  `[redblobgames:record.inner.desktop.network_initial]`, and samwho's illustrated cards
  `[samwho:record.home.desktop.network_initial]`.
- P12, still visual + one line + facts + named links: mehdio `[mehdio:tiles/id-00.png]`.
- P13, numbers beside their source.
- A5: live WebGL on the landing page (ciechanow, bruno-simon) is what this avoids
  `[ciechanow:record.home.desktop.network_initial]`.

### 8.6 Standard project card (`.project`)

**Purpose.** One card per remaining project, each appearing once: screenshot, name, kind, one line,
up to three chips, and links that say what they open.

**Copy:**

| `data-project` | `h3` | Kind (8.8) | Line | Chips | Links (label → target) |
|---|---|---|---|---|---|
| `mcp-servers` | MCP servers for Google Workspace and Reddit [src: index.html:102] (fact sheet D1, D16) | Case study [src: index.html:106] | Two servers that let an AI assistant act on real accounts: 23 tools for Gmail, Calendar, Drive, Sheets and YouTube, 12 for Reddit. [src: index.html:103] | Node.js · OAuth 2.0 [src: index.html:104] · Tools checked on every change [src: mcp/index.html:40-42] | Case study → `/mcp/` · Google Workspace source → github.com/brettadams0/google-workspace-mcp · Reddit source → github.com/brettadams0/reddit-mcp [src: index.html:106-108] |
| `mcp-surface` | mcp-surface [src: index.html:116] | `Live demo · runs in your browser`: badge "Live demo" + "runs in your browser" [src: mcp-surface/index.html:7] | Checks an MCP server the way an AI assistant sees it, and fails the build when a tool changes, so every change gets a review. [src: index.html:117; mcp/index.html:20-21, :40-42] | TypeScript [src: index.html:118] · 19 checks [src: resume/resume.tex:145; mcp/index.html:80] · GitHub Action [src: index.html:118] | Live demo → `/mcp-surface/` · Case study → `/mcp/` · npm → npmjs.com/package/mcp-surface · Source → GitHub [src: index.html:120-123] |
| `promptpal` | PromptPal [src: index.html:131] | Web app on Vercel [src: promptpal@82ffb5d:README.md:124-125] | Write an AI prompt once as a template with fill-in fields. It flags mistakes that quietly ruin answers and exports a ready-to-run Claude API call. [src: promptpal@82ffb5d:README.md:6-8, cited in fact sheet §7.4] | TypeScript · Next.js · E2E + accessibility tests [src: index.html:133; promptpal@82ffb5d:prompt-builder/e2e/a11y.spec.ts] | Live app → prompt-pal-gules.vercel.app · Source → GitHub [src: index.html:135-136] |
| `technest` | TechNest [src: index.html:144] | Source code [src: index.html:148] | A PHP store with no framework. Checkout is one transaction, so the last unit can't sell twice. [src: index.html:145] | PHP · MySQL · 200+ tests [src: index.html:146] (fact sheet D14: README says 250+) | Source → github.com/brettadams0/TechNest [src: index.html:148] |

Chip review (every chip a reader might not parse is flagged, as audit issue 26 does for neuronet-arena's):

| Chip today | Verdict | Change |
|---|---|---|
| "npm" (mcp-surface) | Repeats the "npm" link | Replace with "19 checks". Both sources agree on 19 (fact sheet D17, 19 rules measured); "checks" vs "rules" follows D17's default |
| "E2E + a11y tests" (PromptPal) | "a11y" is a numeronym a business reader won't parse | "E2E + accessibility tests" (25 characters) |
| "Snapshot-checked in CI" (MCP servers) | Clear to an engineer, jargon to a business reader | "Tools checked on every change" (29 characters) [src: mcp/index.html:40-42: any change to a tool "fails the build until the snapshot is updated"]. Not "114 offline tests": that number includes mcp-surface's 54 tests (fact sheet §7.3), so it would overstate the two servers |
| "200+ tests" (TechNest) | Clear; understated against 262 counted | Fact sheet D14 decides 200+ vs 250+ |

**Markup** (mcp-surface shown; the others follow the same shape):

```html
<article class="project" data-project="mcp-surface">
  <a class="project-shot" href="{{ '/mcp-surface/' | relative_url }}" tabindex="-1" aria-hidden="true">
    <img src="{{ '/assets/img/mcp-surface-800.webp' | relative_url }}"
         srcset="{{ '/assets/img/mcp-surface-800.webp' | relative_url }} 800w, {{ '/assets/img/mcp-surface-1600.webp' | relative_url }} 1600w"
         sizes="(min-width: 1200px) 524px, (min-width: 861px) 45vw, calc(100vw - 70px)"
         alt="" width="1600" height="1000" loading="lazy" decoding="async" />
  </a>
  <h3 class="project-name">mcp-surface</h3>
  <p class="project-kind"><span class="badge-live">Live demo</span> runs in your browser</p>
  <p class="project-line">Checks an MCP server the way an AI assistant sees it, and fails the build when a tool changes, so every change gets a review.</p>
  <ul class="chips" role="list"><li>TypeScript</li><li>19 checks</li><li>GitHub Action</li></ul>
  <p class="links">
    <a href="{{ '/mcp-surface/' | relative_url }}">Live demo<span class="sr-only">: mcp-surface</span></a>
    <a href="{{ '/mcp/' | relative_url }}">Case study<span class="sr-only">: mcp-surface</span></a>
    <a href="https://www.npmjs.com/package/mcp-surface">npm<span class="sr-only">: mcp-surface</span></a>
    <a href="https://github.com/brettadams0/mcp-surface">Source<span class="sr-only">: mcp-surface</span></a>
  </p>
</article>
```

- **Plain words for the business reader.** The mcp-surface line, its kind label and the MCP servers'
  third chip drop "client", "schemas", "CI" and "npm package" for what the check does and why. The
  chips keep the engineering facts ("OAuth 2.0" is what recruiters match), and "npm" stays a named
  link. PromptPal's line now says what the app is for; its chips keep how it is built. §18 #51 lists
  these as one copy decision, default ship.
- **MCP servers' screenshot** now links to `/mcp/`, the card's first text link (audit issue 24:
  today it goes to GitHub, `index.html:99`). `project_click` still fires, with `link_url` `/mcp/`
  for screenshot clicks (§13).
- **PromptPal's links** are "Live app", not "Live demo": the app is hosted off-site (rule in 8.0).
  The fact sheet notes it wasn't loaded this session (§7.4). PR verification loads
  https://prompt-pal-gules.vercel.app once.
  - If it doesn't return 200 with the app, drop the "Live app" link.
  - Point the screenshot at Source and change the kind to "Source code".
  - Tell Brett.
- **`sizes`**: 524 px is the measured half-card width at 1440
  (`[brettadams0:measure-home.json.page.images]`); 320 px at 390 (358 px content − 36 px padding −
  2 px border).

**CSS for the links row** (replacing `site.css:513-529`):

```css
.project .links { display: flex; flex-wrap: wrap; gap: 4px 20px; margin-top: auto; padding-top: 4px; font-weight: 500; }
.project .links a { display: inline-flex; align-items: center; min-height: 24px; text-decoration: none; }
@media (max-width: 640px) { .project .links a { min-height: 44px; } }
```

**States.**

| Element | Default | Hover | `:focus-visible` | Active | Visited |
|---|---|---|---|---|---|
| `.project` | `--border` | `--border-strong` | — (`:focus-within` → `--border-strong`) | — | — |
| `.links a` | `--amber`, no underline | underline, `--amber-hi` | focus ring, `outline-offset: 2px` | `--amber-hi` | not styled: only colours may change under `:visited`, and the amber/amber-hi pair is too close to read as a state |
| `.project-shot` | — | the cursor is the only cue | never focused (`tabindex="-1"`) | — | — |

**Breakpoints.**

| Width | Behaviour |
|---|---|
| ≥ 861 | Two columns, gap 24 px (`site.css:422-426`) |
| ≤ 860 | One column. Links wrap; at ≤ 640 each link is 44 px tall, so mcp-surface's four links take two rows |

**Motion and accessibility.**
- Border colour transition only.
- Each card's heading is the project name. The kind label follows it, so heading navigation reads
  "mcp-surface" and then "Live demo, runs in your browser".
- External links (GitHub, npm, Vercel) open in the same tab.

**Research.**
- P12, links named by what you get: madebyevan "View demo", "Use app", "Visit website"
  `[madebyevan:tiles/d-00.png]`; taniarascia Article / Demo / Source `[taniarascia:inner-desktop-fold.png]`.
- A12, "Live demo" that opens a repository
  `[shaundataanalytics:record.home.desktop.dom.features.demo_links]`.
- A13, icon-only links, e.g. wisl's card icons `[wisl:desktop-fold.webp]`.
- A21, bare titles: darshshah `[darshshah:tiles/d-00.png]`.
- Hiring: S16 ("links to live sites"), X12.

### 8.7 Wide project card (new, `.project-wide`)

**Purpose.** It settles the MazeFinder handoff's open question (`handoff/mazefinder-live-demo.md:150-152`,
"a fifth half-width card leaves a gap") without demoting any project. When the number of half-width
cards is odd, the last one in source order spans the row in a compact horizontal layout. Today
there are four (MCP servers, mcp-surface, PromptPal, TechNest), so the class is **unused until a
fifth card ships**. Then, per §5.5, MazeFinder's card goes in after mcp-surface, and TechNest, now
the fifth and last, gets `.project-wide`. Brett can still choose to demote a card instead
(Decision).

**Copy.** The card's own copy is unchanged. When MazeFinder gets a card (only after `/mazefinder/`
ships and walls exist), it uses:
- `data-project="mazefinder"`;
- kind: badge + "C++ compiled to WebAssembly" [src: handoff/mazefinder-live-demo.md:100-106];
- line: [BRETT: one line once walls ship]. The fallback is today's "C++ genetic algorithm with
  fitness evaluation on a hand-rolled thread pool." [src: index.html:155]. It never says "solves
  mazes" before walls exist (fact sheet C2);
- chips: "20x20 walled mazes" (only once true), "C++ → WebAssembly", "Seeded, reproducible"
  [src: handoff/mazefinder-live-demo.md:148-149].

**Markup.** Same as 8.6, plus the class. The builder adds `project-wide` by hand to the last
half-width `article` when the count is odd, and removes it when the count becomes even. There is
no JavaScript and no `:nth-child(... of S)` trick.

```html
<article class="project project-wide" data-project="technest"> … same children as 8.6 … </article>
```

```css
.project-wide { grid-column: 1 / -1; display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
  grid-template-rows: repeat(4, auto) 1fr; gap: 10px 28px; }
.project-wide .project-shot { grid-row: 1 / -1; margin-bottom: 0; }
@media (max-width: 860px) { .project-wide { display: flex; } .project-wide .project-shot { margin-bottom: 6px; } }
```

- The shot is 2/5 of the row, smaller than the featured card's 3/5, so the wide card doesn't read
  as a second favourite.
- `sizes="(min-width: 1200px) 435px, (min-width: 861px) 37vw, calc(100vw - 70px)"` (435 = (1116 −
  28) × 0.4; estimated from the measured card widths).

**States, breakpoints, motion and accessibility.** As 8.6. At ≤ 860 it is indistinguishable from a
standard card. Acceptance: at 1440, the screenshot shows no empty grid cell in `.projects`.

**Research.** None: no site in the set has this problem. The rule comes from the handoff and the
standing rule "each project appears once".

### 8.8 Project kind label (new, `.project-kind`)

**Purpose.** One short line under each project name that says what the visitor can open first. It
also takes the "Live demo" badge out of the heading (audit issue 23: names read "neuronet-arena Live
demo") while keeping the badge on the card, as `CLAUDE.md` requires.

**Copy.**

| Project | Kind label | Read as |
|---|---|---|
| neuronet-arena | [Live demo] runs in your browser | `Live demo · runs in your browser` |
| MCP servers | Case study | |
| mcp-surface | [Live demo] runs in your browser | `Live demo · runs in your browser` |
| PromptPal | Web app on Vercel | |
| TechNest | Source code | |
| later: border-waits | [Live demo] SQL in your browser | `Live demo · SQL in your browser` |
| later: MazeFinder | [Live demo] C++ compiled to WebAssembly | |

Sources are in 8.5–8.7. "[Live demo]" is the badge (8.12); the "·" in the "Read as" column is the
badge's edge on screen, not a character in the markup.

**Rules.**
- One line, at most four words after the badge, and only from this table. A new kind is added here
  first. This keeps the label a fact, not a second project line (`CLAUDE.md:10`: one line per
  project).
- A kind label never makes a claim the card's links can't back.
- §18 #6 lists the kind labels as a site-copy item, default ship.

**Markup.**

```html
<p class="project-kind"><span class="badge-live">Live demo</span> runs in your browser</p>
<p class="project-kind">Case study</p>
```

It always sits directly after the `h3` in the DOM and on screen, so reading order and visual order
match. Style: `display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 0;` with mono
`--fs-xs` in `--muted` (6.29:1 on `--bg`, 5.73:1 on `--surface`, audit §1).

**States.** Static: no hover, no focus.

**Breakpoints.** Same at every width; it wraps under the badge at ≤ 640 if needed.

**Motion and accessibility.** No motion. It is a paragraph, so a screen reader reads it right after
the heading.

**Research.** P12 (links named by what you get): madebyevan `[madebyevan:tiles/d-00.png]`, rauno's
"View Production / View Prototype / Read Essay" `[rauno:inner-desktop-fold.png]`. I2: say what a
demo is before it loads, as madebyevan does `[madebyevan:inner-desktop-fold.png]`.

### 8.9 "Also" list (`ul.also`)

**Purpose.** The long tail: one line per project that has no card, each project once.

**Copy** (verbatim):

| `data-project` | Name | Line | Link |
|---|---|---|---|
| `nhl-arena` | NHL Arena Web Interface | Live refrigeration data, alarms and a thermal imagery viewer for NHL arena operations teams and technicians. Built at CIMCO; internal. [src: index.html:58, :154] (§18 #36, default ship; fact sheet D18; `resume/resume.tex:125` agrees) | none: internal, with no public URL, so `project_click` can never fire here (fact sheet §11) |
| `mazefinder` | MazeFinder | C++ genetic algorithm with fitness evaluation on a hand-rolled thread pool. [src: index.html:155] | Source → github.com/brettadams0/MazeFinder-GeneticAlgorithm [src: index.html:155] |
| (none) | — | — | More on GitHub → github.com/brettadams0?tab=repositories [src: index.html:156] |
| `sift`, only if Brett says yes (fact sheet D21) | Sift | Swipe-based triage for the Android camera roll, with grading on the device and no internet permission. [src: sift@e37fa56:README.md:1-8] | Source → github.com/brettadams0/sift |

- MazeFinder's name is no longer the link, as it is today (`index.html:155`). A link reading
  "MazeFinder" could be a demo; "Source" says what it opens. `project_click` still fires from the
  Source link, with the same `link_url`.
- The MazeFinder row leaves this list when its card ships (8.7).

**Markup:**

```html
<ul class="also" role="list">
  <li data-project="nhl-arena">
    <span class="also-name">NHL Arena Web Interface</span>
    <span class="also-desc">Live refrigeration data, alarms and a thermal imagery viewer for NHL arena operations teams and technicians. Built at CIMCO; internal.</span>
  </li>
  <li data-project="mazefinder">
    <span class="also-name">MazeFinder</span>
    <span class="also-desc">C++ genetic algorithm with fitness evaluation on a hand-rolled thread pool.</span>
    <span class="also-links"><a href="https://github.com/brettadams0/MazeFinder-GeneticAlgorithm">Source<span class="sr-only">: MazeFinder</span></a></span>
  </li>
  <li class="also-more"><a href="https://github.com/brettadams0?tab=repositories">More on GitHub<span aria-hidden="true">&nbsp;→</span></a></li>
</ul>
```

- `.also b` (`site.css:546-553`) becomes `.also-name`, the same class `/mcp/` already uses
  (`mcp/index.html:71`).
- **Who used it.** The row now names the users and the thermal viewer, which `:58`'s trimmed
  Experience bullet (8.4) leaves out. §18 #36 records the wording, default ship.
- Grid at ≥ 641: `grid-template-columns: 240px minmax(0, 1fr) auto`. `.also-more` spans all three
  columns. Rows are `padding: var(--space-4) 0` (today 14 px, `site.css:542`).
- Target size, for `.also-links a` and `.also-more a`:

```css
.also a { display: inline-flex; align-items: center; min-height: 24px; }
@media (max-width: 640px) { .also a { min-height: 44px; } }
```

- The arrow follows a `&nbsp;`, because a normal space at the edge of an `aria-hidden` span
  collapses inside `inline-flex` (§6.7).
- In `/mcp/`, `.also-name` stays an `<a>` (the repository name is the destination), and its
  `.also-links` cell is empty.

**States.** Links: as `.links a` in 8.6. Rows: no hover (not clickable as a whole).

**Breakpoints.**

| Width | Behaviour |
|---|---|
| ≥ 641 | Three columns: name (mono, `--text`), line (`--muted`), link right-aligned |
| ≤ 640 (390) | Stacked: name, line, link. Each link 44 px tall (today 19 px, `[brettadams0:measure-home.json.viewport.390.small_targets_24]`; audit issue 15) |

**Motion and accessibility.** None animated. The arrow is `aria-hidden`, so the name is "More on
GitHub", not "More on GitHub right arrow".

**Research.**
- emilkowal's homepage is lists of a name plus one plain line `[emilkowal:desktop-fold.webp]`.
- I10: brittanychiang's archive table `[brittanychiang:inner-desktop-fold.png]`. Not needed at
  seven projects.
- A21: bare titles. Every row here has a line.

### 8.10 Chip (`ul.chips > li`)

**Purpose.** Facts as chips (standing rule): tools, counts and proof points. Chips are not links and
not buttons.

**Rules for chip copy.**
- At most 3 chips per project card. Job and skill chips follow their sources (`index.html:36`, `:49`,
  `:63`, `:75`, `:166-169`).
- At most 32 characters. Full tool names a recruiter can match ("Azure Databricks", "GitLab CI/CD").
- Every chip with a number has a source line in this spec (P13). No chip carries an unsourced
  total.
- A chip must make sense without opening the project. This is the test that flags 8.5's "13.4 food
  vs 1.0 for the control".

**Markup.**

```html
<ul class="chips" role="list"><li>BigQuery</li><li>Apache Airflow</li></ul>
```

Style (from `site.css:201-217`):
- `li`: `padding: 3px 9px; border: 1px solid var(--border); border-radius: var(--radius-pill);
  background: var(--bg-2); color: var(--soft); font: var(--fs-xs)/1.5 var(--mono);`
- `--soft` on `--bg-2` is 11.42:1 (audit §1).
- `white-space: normal; overflow-wrap: anywhere;`, so a long chip wraps instead of widening a
  390 px layout.

**States.** Default only. No hover, cursor or focus: a hover effect would suggest a control.

**Breakpoints.** Same everywhere. The flex row wraps with gap 6 px.

**Motion and accessibility.** None animated. `role="list"` keeps "list, 3 items" in Safari.

**Research.**
- P20, Mono for metadata: wisl styles tool names as code chips `[wisl:desktop-fold.webp]`.
- P6: darshshah's full tool names `[darshshah:desktop-fold.webp]`.
- P13 and A11: numbers need a source. No site in the set uses skill-percentage bars (0 of 29,
  §3.6), and neither does this.

### 8.11 Buttons (`.btn`, `.btn-primary`)

**Purpose.** Primary actions as text buttons: Résumé (PDF) as the one primary, then Email, LinkedIn
and GitHub. They are links styled as buttons, because every one navigates.

**Copy.** "Résumé (PDF)", "Email", "LinkedIn", "GitHub" (hero and contact); "Back to the homepage",
"Projects" (404). Sources are in 8.2, 8.15 and 8.17.

**Markup.**

```html
<a class="btn btn-primary" href="{{ '/assets/resume/Resume_BrettAdams.pdf' | relative_url }}">Résumé (PDF)</a>
<a class="btn" href="mailto:adamsbrett00@gmail.com" data-contact="email">Email</a>
```

**CSS changes to `site.css:161-192`.**
- `height: 42px` → `min-height: 44px` (padding 0 18 px), so text-spacing overrides can't clip a label
  (WCAG 1.4.12).
- `#1a1206` → `var(--on-amber)`.

**States.**

| | Default | Hover | `:focus-visible` | Active |
|---|---|---|---|---|
| `.btn` | `--surface` background, 1 px `--border-strong`, `--text` | background `--surface-2`, border `--muted` | focus ring, offset 3 px | background `--bg-2` |
| `.btn-primary` | `--amber` background, `--on-amber` text (10.51:1, audit §1) | `--amber-hi` (12.28:1) | ring: 2 px `--focus` with a 3 px offset, so the `--bg` gap separates ring from fill | background `--amber` |

- No visited style: the résumé link must look the same after a first download.
- Under 3:1, `--border-strong` doesn't fail SC 1.4.11 here, because the button is identified by its
  text label (audit issue 17).

**Breakpoints.** ≥ 641: inline row, gap 10 px. ≤ 640: the hero grid in 8.2. The contact section
(8.15) stays a wrapping row with 44 px buttons.

**Motion and accessibility.** Background and border colour transitions at `--dur-fast`. No movement.
Every button is 44 px tall at every width, the hero's primary included (measured 358 × 44 at 390 in
the mock, §6.2). 44 px is the goal for primary controls on phones (§10.7).

**Research.**
- P3, a résumé button at 0 scrolls: kushalgoel `[kushalgoel:mobile-fold.webp]`.
- P14: antfu's text labels beside icons `[antfu:desktop-fold.webp]`.
- A13: unnamed icon buttons fail axe on 7 sites.
- A16: targets under 24 px.

### 8.12 Live demo badge (`.badge-live`)

**Purpose.** It marks the cards whose project runs on this site, as `CLAUDE.md` describes
("screenshot, 'Live demo' badge and link").

**Copy.** "Live demo" [src: index.html:89, :116]. Shown uppercase by CSS; the DOM text stays "Live
demo".

**Where it may appear.** Only inside `.project-kind` (8.8), and only on cards whose "Live demo" link
opens a page on this site running the project's own code. Today: neuronet-arena and mcp-surface.
Later: MazeFinder and border-waits, each in the PR that ships its page. Never on PromptPal (off-site
app), the MCP servers (case study) or TechNest.

**Markup.**

```html
<span class="badge-live">Live demo</span>
```

Style (`site.css:461-483` minus `margin-left` and `vertical-align`, which only served the heading
placement):
- `--good-text` on the composited badge background, 10.45:1 (audit §1).
- The dot is a `::before` with `content: ""`, so it adds no text.

**States.** Static.

**Breakpoints.** Same everywhere.

**Motion and accessibility.**
- The dot doesn't pulse. A pulsing "live" dot would be decorative motion on the homepage (§9).
- The badge's text, not its green, carries the meaning (§10.10).

**Research.** P8, with the A12 caution; richardshuai's card badge `[richardshuai:tiles/m-07.png]`.

### 8.13 Skills (`section#skills > dl.skills`)

**Purpose.** Tools grouped as facts. It is not a word cloud and not percentage bars.

**Copy.** Unchanged until Brett answers fact sheet D8/D9 (the lists and group names differ between
the site and the résumé):
- Languages [src: index.html:166];
- Data [src: index.html:167];
- Tools [src: index.html:168];
- Web [src: index.html:169].

**Markup** (`index.html:161-171`; the `## ` is an `aria-hidden` span, 8.0):

```html
<section id="skills" class="section" aria-labelledby="skills-title">
  <div class="wrap two-col">
    <div>
      <h2 id="skills-title" class="section-title"><span class="prefix" aria-hidden="true">## </span>Skills</h2>
      <dl class="skills">
        <div><dt>Languages</dt><dd><ul class="chips" role="list"><li>Python</li><li>SQL</li><li>TypeScript</li><li>JavaScript</li><li>PHP</li><li>Java</li><li>C++</li><li>C</li></ul></dd></div>
        <!-- Data, Tools, Web as index.html:167-169 -->
      </dl>
    </div>
    <div id="education"> … 8.14 … </div>
  </div>
</section>
```

**States.** Static.

**Breakpoints.**

| Width | Behaviour |
|---|---|
| ≥ 981 | `.two-col`: skills 2/3, education 1/3 (`site.css:561-565`) |
| ≤ 980 | One column: skills, then education (`site.css:853-856`) |
| ≤ 640 | `dt` above `dd` (`site.css:898-903`) |

**Motion and accessibility.** None. `id="skills"` and `aria-labelledby` unchanged.

**Research.**
- I1: beingbrute lists only demonstrated skills `[beingbrute:tiles/d-05.png]`. Linking each chip to
  its proof is held back: it would add about 24 tab stops and break the "chips are not controls"
  rule.
- No site in the set has skill bars or buzzword clouds (§3.6).

### 8.14 Education (`div#education > .edu`)

**Purpose.** School, degree and dates, read last by a recruiter (H2: title, employer, dates, then
education).

**Copy.**

| Line | Copy | Source |
|---|---|---|
| `h3` | University of Windsor | [src: index.html:175] |
| degree | Honours Computer Science, Software Engineering Specialization (Co-op) | [src: index.html:176] (D3: résumé wording differs) |
| when | 2022 – 2027 · Windsor, ON | [src: index.html:177; resume/resume.tex:89] |
| when, once Brett answers | 2022 – [BRETT: graduation month] 2027 · Windsor, ON | fallback: the line above |

**Markup.**

```html
<div id="education">
  <h2 id="education-title" class="section-title"><span class="prefix" aria-hidden="true">## </span>Education</h2>
  <div class="edu">
    <h3>University of Windsor</h3>
    <p>Honours Computer Science, Software Engineering Specialization (Co-op)</p>
    <p class="job-when">2022 – 2027 · Windsor, ON</p>
  </div>
</div>
```

It stays a `div` (8.0). A `section` would start a new `education` value, which is a Decision.

**States.** Static.

**Breakpoints.** As 8.13. The card is full width at ≤ 980.

**Motion and accessibility.** None. The heading outline stays h2 Education → h3 University of Windsor
(audit "checked and fine").

**Research.** P1: shao puts year and school in one plain sentence `[shao:mobile-fold.webp]`. Hiring
S02 (H2).

### 8.15 Contact (`section#contact`)

**Purpose.** The written-out email address, plus LinkedIn, GitHub and the résumé, at the end of the
page. The same contact paths are also in the hero and the footer.

**Copy.** "adamsbrett00@gmail.com" [src: index.html:186]; buttons LinkedIn, GitHub, Résumé (PDF) [src:
index.html:188-190]. Optional, once answered: "[BRETT: where are you based, and open to relocation or
remote?]". The fallback omits the line. The phone number stays off the site unless Brett says so
(fact sheet §10).

**Markup.**

```html
<section id="contact" class="section contact" aria-labelledby="contact-title">
  <div class="wrap">
    <h2 id="contact-title" class="section-title"><span class="prefix" aria-hidden="true">## </span>Contact</h2>
    <p class="contact-email"><a href="mailto:adamsbrett00@gmail.com" data-contact="email">adamsbrett00@gmail.com</a></p>
    <p class="actions">
      <a class="btn" href="https://www.linkedin.com/in/bretta/" data-contact="linkedin">LinkedIn</a>
      <a class="btn" href="https://github.com/brettadams0" data-contact="github">GitHub</a>
      <a class="btn" href="{{ '/assets/resume/Resume_BrettAdams.pdf' | relative_url }}">Résumé (PDF)</a>
    </p>
  </div>
</section>
```

**States.** `.contact-email a`: `--text` with a 2 px `--border-strong` underline. Hover → `--amber`
text and underline (`site.css:774-785`). Focus ring. Buttons as 8.11.

**Breakpoints.** The email scales with `clamp()` (`site.css:768`) and wraps anywhere at 390
(`overflow-wrap: anywhere`). Buttons stay a wrapping row at every width; the 3-column grid from 8.2
is hero-only (scope it as `.hero .actions`).

**Motion and accessibility.** Colour only. The email is real link text, not an icon.

**Research.**
- P14, contact written as text (5 sites): shao's visible mailto at 0 scrolls
  `[shao:record.home.mobile.dom.features.contact_links]`, redblobgames' email in the nav
  `[redblobgames:record.home.desktop.dom.nav]`.
- A22: no email on 7 sites.

### 8.16 Footer (`.site-footer`)

**Purpose.** Copyright and the same three contact links on every default-layout page. On `/mcp/` and
`/404.html` they are the only contact path (audit issue 12).

**Copy.** "© {{ site.time | date: "%Y" }} Brett Adams" [src: _layouts/default.html:59]. The year is
the build year, so it stays current with every deploy. Links GitHub, LinkedIn, Email [src:
_layouts/default.html:61-63], in that order on every page, demo footers included (WCAG 3.2.6, §10).

**Markup** (`_layouts/default.html:57-66`):

```html
<footer class="site-footer">
  <div class="wrap site-footer-inner">
    <p>&copy; {{ site.time | date: "%Y" }} Brett Adams</p>
    <p class="footer-links">
      <a href="https://github.com/brettadams0" data-contact="github">GitHub</a>
      <a href="https://www.linkedin.com/in/bretta/" data-contact="linkedin">LinkedIn</a>
      <a href="mailto:adamsbrett00@gmail.com" data-contact="email">Email</a>
    </p>
  </div>
</footer>
```

**Analytics.** Adding `data-contact` here is a Decision (§18 #12).
- Recommended default: add it, with the existing `method` values. It needs no new parameter or
  custom definition.
- Effect: `contact_click` starts counting footer clicks too, so totals rise from the day it ships.
  §13 notes this.
- If Brett says no, ship the markup without the three attributes.

**States.** `--muted`. Hover → `--text`. Focus ring. Not visited-styled.

**Breakpoints.** ≥ 641: one row, copyright left, links right, gap 24 px. ≤ 640: copyright, then the
links. Each link `display: inline-flex; min-height: 44px` (today 18 px tall,
`[brettadams0:measure-home.json.viewport.390.small_targets_24]`).

**Motion and accessibility.** Colour only. `footer` is the `contentinfo` landmark.

**Research.**
- P18 caution: a date must stay true. darshshah's "Accurate as of" line went stale
  `[darshshah:tiles/d-00.png]`. A build-year copyright is maintained automatically; a "last updated"
  line is not added.
- A16: small footer targets. I19 (webring footers): not used.

### 8.17 404 page (`.not-found`)

**Purpose.** A missing URL still leads somewhere useful: the homepage, the projects and the résumé
(audit issue 21: today only "Back to the homepage").

**Copy.**

| Element | Copy | Source |
|---|---|---|
| eyebrow | 404 | [src: 404.html:8] |
| `h1` | Page not found | [src: 404.html:9] |
| text | That page doesn't exist, or it has moved. | [src: 404.html:10] |
| buttons | Back to the homepage · Projects · Résumé (PDF) | [src: 404.html:11]; targets as 8.1 |

**Markup** (`404.html:7-12`; the front matter is unchanged, and §12 owns its description):

```html
<section class="not-found wrap" aria-labelledby="not-found-title">
  <p class="eyebrow">404</p>
  <h1 id="not-found-title">Page not found</h1>
  <p>That page doesn't exist, or it has moved.</p>
  <p class="actions">
    <a class="btn btn-primary" href="{{ '/' | relative_url }}">Back to the homepage</a>
    <a class="btn" href="{{ '/#projects' | relative_url }}">Projects</a>
    <a class="btn" href="{{ '/assets/resume/Resume_BrettAdams.pdf' | relative_url }}">Résumé (PDF)</a>
  </p>
</section>
```

- The `section` has **no `id`** (8.0), so `section_view` doesn't start firing on 404.
- Every URL goes through `relative_url`, because Pages serves this page at whatever missing path
  was requested. A relative `href` would break.

**States.** Buttons as 8.11.

**Breakpoints.** Padding 120 px top and bottom at ≥ 641 (`site.css:789-792`), 64 px at ≤ 640.
Buttons wrap; each 44 px tall.

**Motion and accessibility.** None. The only heading is the h1.

**Research.** No site in the set was captured on a 404. This follows audit issue 21.

### 8.18 `/mcp/` template (`.post.case-study`, with the new `.case-for`, `.flow` and `.post-next`)

**Purpose.**
- `/mcp/` is the one long-form page. It is laid out on the case-study outline from §5.4: For →
  problem → diagram → decisions → tests and CI output → result → limits → stack and source (P11).
- There are no new posts.
- The existing paragraphs stay as written and in today's order. The outline comes from moving the
  diagram up, plus four small copy changes, listed below for §18.

**Mapping the outline onto today's content** (`mcp/index.html`):

| Outline step | Content | Where it is today | Change |
|---|---|---|---|
| For | `.case-for` line (new copy) | — | **new**, under the facts |
| Diagram | `.flow-figure`: row 1 "At runtime" (new labels), row 2 "In CI" (the three CI steps) | the `ol` at `:35-39`, about y 870 at 1440 (estimated) | **moved** to directly under the For line, with a new first row. Estimated bottom ≈ 700 px at 1440 with both rows, inside the first screen (§4.4 engineer check) |
| Problem | intro (`:16-26`) and "One check at the end of CI" (`:28-34`) | same | one sentence tail reworded (below) |
| Stack and source | "The repositories" (`:68-82`) | second h2 | **none**: it stays second. It holds the page's one plain cost result: the Google server asks for no restricted scopes, which "keeps it out of the paid security assessment Google requires for restricted scopes" (`:72`) |
| Tests and CI output | the figure (`:44-59`) | same | `pre` made a keyboard region (8.19) |
| Decisions | the Zod 3 paragraph (`:60-66`); "What the review found" and its eight h3s (`:84-150`) | same | none. The h3s are also the results a business reader can follow, such as "Calendar invitations that were never sent" (`:124`) and "Reddit failures that looked like success" (`:115`), so they need no new copy |
| Result | "What both servers now have" (`:152-159`) | same | none |
| Limits | h2 "Limits" (new copy, two bullets) | — | **new**, after "What both servers now have" |

**Copy.**

| Element | Copy | Source |
|---|---|---|
| back link | ← Brett Adams → `/#projects` (arrow `aria-hidden`) | [src: mcp/index.html:10] |
| eyebrow | Case study · October 2026 | [src: mcp/index.html:11] (P18: a date on the page) |
| `h1` | Two MCP servers that act on real accounts, and the tool that checks them | [src: mcp/index.html:12] |
| `.case-facts`, now chips | 2 servers · 35 tools · 3 repositories · 114 offline tests | [src: mcp/index.html:13] (fact sheet D1 framing) |
| `.case-for` (**new**) | For anyone who lets an AI assistant send email, edit a calendar or post on their behalf, and needs to know when what it can do changes. | restates [src: mcp/index.html:17-19] (what the servers do) and [src: mcp/index.html:40-42] (a tool change "fails the build until the snapshot is updated") |
| `.flow` row 1 label (**new**) | At runtime | label |
| `.flow` row 1 steps (**new**) | An AI assistant · The MCP server · Google Workspace or Reddit, over OAuth | [src: mcp/index.html:17-19, :72, :76] |
| `.flow` row 2 label (**new**) | In CI | label; restates [src: mcp/index.html:33] |
| `.flow` row 2 steps | the three existing `li` texts, unchanged | [src: mcp/index.html:36-38] |
| `.flow` figcaption | How the servers run, and what checks them at the end of each server's CI | label; restates [src: mcp/index.html:24], [src: mcp/index.html:33] |
| body sentence (**reworded tail**) | "So the last step in each server's CI runs mcp-surface, in the "In CI" row at the top of this page." (today ends "runs mcp-surface:") | [src: mcp/index.html:33] |
| h2 "Limits" (**new**), bullet 1 | The test suites run without network access or credentials, so they never call the real Google or Reddit APIs. | [src: mcp/index.html:154; resume/resume.tex:155] (the offline half of the bullet is confirmed, fact sheet §7.3) |
| h2 "Limits" (**new**), bullet 2 | By default, mcp-surface checks the tool list a server shows, not what its tools do when called. Three of its 19 checks run only with `--call`. | [src: mcp/index.html:80; mcp-surface@ea93b87:README.md:157-175, :169-171] |
| h2s | unchanged; the `## ` is an `aria-hidden` span (8.0) | [src: mcp/index.html:28, :68, :84, :152] |
| `.post-next` link 1 | Try mcp-surface's checks in your browser → | [src: mcp-surface/index.html:7] |
| `.post-next` link 2 | ← All projects | to `/#projects` |

- **The For line** names who the page is for and the problem without MCP or CI shorthand. It answers
  "why would I care?" for the business reader, which §4.4's business check looks for.
- **The two-row diagram** shows the system the page covers ("how the three fit together",
  `mcp/index.html:24`), not only the CI check. Row 1 is what runs for the user; row 2 is what
  guards it.

The four items marked **new** or **reworded** are copy on Brett's merged page. §18 #14 lists them as
one decision, with default: ship. If Brett vetoes them:
- the CI steps stay where they are today, at `:35-39`, as a plain list, and there is no row 1;
- the sentence keeps its colon;
- "Limits" and the For line are left out.

Nothing else on the page moves.

**Markup** (key parts):

```html
<article class="post case-study">
  <p class="post-back"><a href="{{ '/#projects' | relative_url }}"><span aria-hidden="true">←&nbsp;</span>Brett Adams</a></p>
  <p class="eyebrow">Case study · October 2026</p>
  <h1>Two MCP servers that act on real accounts, and the tool that checks them</h1>
  <ul class="chips case-facts" role="list"><li>2 servers</li><li>35 tools</li><li>3 repositories</li><li>114 offline tests</li></ul>
  <p class="case-for">For anyone who lets an AI assistant send email, edit a calendar or post on their behalf, and needs to know when what it can do changes.</p>
  <figure class="flow-figure">
    <div class="flow-row">
      <p class="flow-label" id="flow-runtime">At runtime</p>
      <ol class="flow" aria-labelledby="flow-runtime">
        <li>An AI assistant</li>
        <li>The MCP server</li>
        <li>Google Workspace or Reddit, over OAuth</li>
      </ol>
    </div>
    <div class="flow-row">
      <p class="flow-label" id="flow-ci">In CI</p>
      <ol class="flow" aria-labelledby="flow-ci">
        <li>Start the server the way a client would. Listing tools needs no credentials.</li>
        <li>Read the tool list and check every schema, name and annotation.</li>
        <li>Compare the result with <code>mcp-surface.json</code>, a snapshot committed to the repository.</li>
      </ol>
    </div>
    <figcaption>How the servers run, and what checks them at the end of each server's CI</figcaption>
  </figure>
  <div class="post-body">
    <p>I've built two …</p>                                   <!-- :16-26 unchanged -->
    <h2><span class="prefix" aria-hidden="true">## </span>One check at the end of CI</h2>
    <p>A broken MCP server … So the last step in each server's CI runs mcp-surface, in the "In CI" row at the top of this page.</p>
    <p>Any change to a tool's name …</p>                      <!-- :40-43 -->
    <figure>
      <pre tabindex="0" role="region" aria-label="Terminal output of npm run surface"><code>$ npm run surface …</code></pre>
      <figcaption>Real output, shortened where marked: …</figcaption>
    </figure>
    <p>It caught something on the first day. …</p>             <!-- :60-66 -->
    <h2><span class="prefix" aria-hidden="true">## </span>The repositories</h2> <ul class="also" role="list"> … </ul>   <!-- :68-82, unchanged, still second -->
    <h2><span class="prefix" aria-hidden="true">## </span>What the review found</h2> …                          <!-- :84-150 -->
    <h2><span class="prefix" aria-hidden="true">## </span>What both servers now have</h2> …                     <!-- :152-159 -->
    <h2><span class="prefix" aria-hidden="true">## </span>Limits</h2>
    <ul>
      <li>The test suites run without network access or credentials, so they never call the real Google or Reddit APIs.</li>
      <li>By default, mcp-surface checks the tool list a server shows, not what its tools do when called. Three of its 19 checks run only with <code>--call</code>.</li>
    </ul>
  </div>
  <p class="post-next">
    <a href="{{ '/mcp-surface/' | relative_url }}">Try mcp-surface's checks in your browser<span aria-hidden="true">&nbsp;→</span></a>
    <a href="{{ '/#projects' | relative_url }}"><span aria-hidden="true">←&nbsp;</span>All projects</a>
  </p>
</article>
```

- **No `section[id]`.** If the builder wraps parts in `<section>`, they get no `id`. Otherwise
  `/mcp/` starts sending new `section_view` values; none fire there today (fact sheet §11).
- **`.case-for` style:** `--soft`, `--fs-md`, `max-width: 34em`, margin 16 px top.
- **`.flow` layout:**
  - Each `.flow-row` is a label and a row of boxes. `.flow-label` is Mono `--fs-xs` `--muted`,
    uppercase with `--tracking-caps`, `margin: var(--space-4) 0 var(--space-2)`.
  - At ≥ 641: three equal boxes in a row (`display: grid; grid-template-columns: repeat(3, 1fr); gap:
    28px`). Each box is `--surface` with a 1 px `--border` and `--radius-sm`.
  - Row 2's step number comes from `::marker`, Mono, `--amber`. Row 1 has no numbers
    (`list-style: none`): it is a chain, not steps.
  - Arrows between the boxes are `li + li::before` with `content: ""` and a CSS-border chevron.
    Empty content means nothing extra is read.
  - At ≤ 640: each row is a vertical stack with downward chevrons.
  - The figcaption sits under both rows in `--muted`, `--fs-sm`.
- **`.post-next`** is a `p`, not a `nav`, so the page doesn't gain a third navigation landmark. The
  demo link points at a page that runs (8.0).

**States.**
- `.post-body a`: underlined, `--amber`; hover `--amber-hi`. Links in running text need the
  underline (§10.10).
- `.post-back a` and `.post-next a`:
  - `--muted`; hover `--text`; focus ring;
  - `min-height: 24px`, rising to 44 px at ≤ 640. The back link is 17 px tall today
    (`[brettadams0:measure-mcp.json.viewport.390.small_targets_24]`).
- `pre`: see 8.19.

**Breakpoints.**

| Width | Behaviour |
|---|---|
| ≥ 861 | `.post` max-width 760 px, centred (`site.css:617-621`). `.flow` in a row |
| 641–860 | Same, with side padding `--gutter` |
| ≤ 640 (390) | Padding 16 px. `.flow` vertical. The code block scrolls sideways inside itself (8.19), and the page never does. `.also` rows stack |

**Motion and accessibility.**
- Nothing animates.
- Heading outline (§10.4): h1, then h2 ×5 (One check at the end of CI, The repositories, What the
  review found, What both servers now have, Limits). The eight h3s stay under "What the review
  found". Today's outline is `[brettadams0:measure-mcp.json.page.headings]`.
- Each `.flow` list is named by its row label (`aria-labelledby`), so a screen reader hears "At
  runtime, list, 3 items".

**Research.**
- **P11, case studies on one outline** (4 sites): deshraj-jogiya's headings, including limits and
  next steps `[deshraj-jogiya:record.inner.desktop.dom.headings]`; shao's internship write-up
  `[shao:tiles/id-03.png]`.
- **I3, a "For:" line:** deshraj-jogiya `[deshraj-jogiya:inner-desktop-fold.png]`.
- **I20, honest limits.**
- **P18, a date under the title:** redblobgames `[redblobgames:inner-desktop-fold.png]`.
- **I9, next links at the foot:** pahasara `[pahasara:tiles/id-mid.png]`.
- **Hiring:** S06 ("If I cannot see the system flow in five seconds… No diagram means no project")
  and S07 (problem, trade-offs). Row 1 is what makes the diagram a system flow, not only a CI
  checklist.

### 8.19 Scrollable code and output region (new pattern)

**Purpose.** Any box that scrolls on its own must be reachable and scrollable by keyboard. Today
`/mcp/`'s `<pre>` fails `scrollable-region-focusable` at 390 (audit issue 4), and mcp-surface's
`.term` fails it at both sizes (audit issue 3).

**Where.**
- `/mcp/`: the `pre` at `mcp/index.html:45`.
- `/mcp-surface/`: `.term` at `mcp-surface/index.html:69`.
- Future: a results table in a horizontal scroller (`handoff/border-waits-pipeline.md`: "inside a
  horizontally scrolling container").

**Markup.**

```html
<pre tabindex="0" role="region" aria-label="Terminal output of npm run surface"><code>…</code></pre>
<div class="term" id="report-region" tabindex="0" role="region" aria-label="Report output">…</div>
<div class="table-scroll" tabindex="0" role="region" aria-labelledby="results-caption"><table>…<caption id="results-caption">…</caption></table></div>
```

- `role="region"` is required with the name. A `pre` or `div` has no role, and naming a role-less
  element trips axe's `aria-prohibited-attr`.
- Names must not repeat another region's name on the page. On mcp-surface the card
  `section[aria-labelledby="report-title"]` is already the region "Report", so `.term` is "Report
  output" (a duplicate trips axe's `landmark-unique`).
- On mcp-surface, `aria-live="polite"` moves off `.term`. That region re-renders the whole report on
  every keystroke (`mcp-surface/app.js:136-186`), so a live region would re-read all of it. The
  short `#verdict` gets `role="status"` instead and announces "exit 1 CI fails" or "exit 0 CI
  passes" (`app.js:181-185`).

**States.** `:focus-visible` uses the focus ring with `outline-offset: 2px`. The box keeps its own
border; no hover.

**Breakpoints.**
- `/mcp/`: at ≥ 861 the code fits at 760 px and doesn't scroll.
- At ≤ 640 it scrolls sideways inside itself, with `overflow-x: auto` (`site.css:707-716`).

**Motion and accessibility.** Arrow keys scroll it once focused. It is one extra Tab stop per box.

**Research.** None needed: this follows axe's rule and audit issues 3–4.

### 8.20 Shared shell for project and demo pages (`.demo-shell`, `.demo-how`, `.demo-foot`)

**Purpose.** A visitor who lands on a shared demo link can see whose work it is and reach the
résumé, the source and the rest of the projects (audit issues 13, 20). The page also says how it was
built (P10). It applies in this repo to `/mcp-surface/` now and to `/mazefinder/` when it ships.
`/border-waits/` and `/neuronet-arena/` are synced from other repos, so they copy the same rules
(7.11 and the end of this section).

**Shell CSS location.** New file `assets/css/demo-shell.css`, linked by each of this repo's own demo
pages after its own stylesheet.
- **Shell only.** It holds the skip link, the shared utilities (`.sr-only`, `.prefix`, the focus
  ring), the header row, "How it works", the version chip and the footer. It defines no tokens: it
  reads the page's own `:root`, which is a copy of §7.1's block (7.11). Under the default it is not
  a token file, so `CLAUDE.md:12` stays true.
- **Size:** 4,640 B, 1,421 B gzipped (**measured**, Python `gzip` level 9, on the block below).
- **Checked:** pasted into a scratch copy of `/mcp-surface/` with this section's markup, it gives a
  53 px header row at 1440 with 24 px links and the 36 px Résumé button, and at 390 two header rows
  with every link 44 px tall, the version chip without a bullet, and `scrollWidth` 390 (**measured**
  2026-10-24, Chromium 141).

```css
/* assets/css/demo-shell.css: the shared shell for demo pages (spec section 8.20).
   Shell layout only. It defines no tokens: it reads the page's own :root (section 7.1, 7.11). */

/* Utilities the shell and the page share */
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
           overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }
.prefix { color: var(--amber); white-space: pre; }   /* section 7.10 */
:focus-visible { outline: 2px solid var(--focus); outline-offset: 3px; border-radius: 4px; }
.demo-links a:focus-visible, .demo-source a:focus-visible, .footer-links a:focus-visible { outline-offset: 2px; }
main:focus { outline: none; }

/* Skip link (as site.css, with tokens) */
.skip-link {
  position: absolute; left: var(--space-4); top: -48px; z-index: var(--z-skip);
  padding: var(--space-2) var(--space-3); border-radius: var(--radius-sm);
  background: var(--amber); color: var(--bg); text-decoration: none;
}
.skip-link:focus { top: var(--space-3); }

/* Header row: crumb left, links right. Not sticky. */
.demo-shell { border-bottom: 1px solid var(--border); }
.demo-shell-inner, .demo-foot-inner {
  max-width: var(--container-demo); margin: 0 auto; padding-left: var(--gutter); padding-right: var(--gutter);
}
.demo-shell-inner {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
  gap: var(--space-2) var(--space-4); min-height: 52px;
}
.demo-crumb { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); margin: 0; font: 500 var(--fs-sm) var(--mono); }
.demo-sep { color: var(--dim); }
.demo-name { color: var(--text); }
.demo-links { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-1) var(--space-5); font-size: var(--fs-sm); }
.demo-back, .demo-links a, .demo-source a, .footer-links a {
  display: inline-flex; align-items: center; min-height: 24px;
  color: var(--muted); text-decoration: none;
  transition: color var(--dur-fast) var(--ease-out);
}
.demo-back:hover, .demo-links a:hover, .demo-source a:hover, .footer-links a:hover { color: var(--text); }
.demo-back:active, .demo-links a:active, .demo-source a:active { color: var(--amber); }
.demo-links .demo-resume {   /* as .nav-resume, section 8.1 */
  min-height: 36px; padding: 0 14px; border: 1px solid var(--amber-line); border-radius: var(--radius);
  color: var(--amber); font-weight: 500;
  transition: background-color var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}
.demo-links .demo-resume:hover { background: var(--amber-wash); color: var(--amber-hi); }
.demo-links .demo-resume:active { background: rgba(255, 180, 84, 0.16); }

/* How it works */
.demo-how { max-width: var(--container-post); margin: var(--space-8) 0 0; padding-top: var(--space-6); border-top: 1px solid var(--border); }
.demo-how h2 { margin: 0 0 var(--space-3); font-size: var(--fs-sm); font-weight: 600; }
.demo-how ul:not(.chips) { margin: 0 0 var(--space-4); padding-left: 1.1em; color: var(--soft); }
.demo-how ul:not(.chips) li { margin: 5px 0; }
.demo-how ul:not(.chips) li::marker { color: var(--faint); }
.demo-how p a, .demo-how li a { color: var(--muted); }
.demo-source { display: flex; flex-wrap: wrap; gap: var(--space-1) var(--space-5); margin: var(--space-4) 0 0; font-weight: 500; }

/* Chips (as section 8.10): the version chip */
.chips { display: flex; flex-wrap: wrap; gap: 6px; margin: 0; padding: 0; list-style: none; }
.chips li {
  padding: 3px 9px; border: 1px solid var(--border); border-radius: var(--radius-pill);
  background: var(--bg-2); color: var(--soft); font: var(--fs-xs) / 1.5 var(--mono);
  white-space: normal; overflow-wrap: anywhere;
}

/* Footer */
.demo-foot { margin-top: var(--space-8); border-top: 1px solid var(--border); color: var(--muted); font-size: var(--fs-sm); }
.demo-foot-inner {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
  gap: var(--space-3) var(--space-6); padding-top: var(--space-6); padding-bottom: var(--space-7);
}
.demo-foot p { margin: 0; }
.demo-foot p > a { color: var(--muted); }   /* running text: keeps its underline */
.demo-foot p > a:hover { color: var(--text); }
.footer-links { display: flex; flex-wrap: wrap; gap: 0 var(--space-6); }

@media (max-width: 640px) {
  .demo-shell-inner { flex-direction: column; align-items: flex-start; gap: 0; padding-top: var(--space-1); padding-bottom: var(--space-1); }
  .demo-back, .demo-links a, .demo-source a, .footer-links a, .demo-links .demo-resume { min-height: 44px; }
  .demo-foot-inner { flex-direction: column; align-items: flex-start; }
}
```

- The shell's inner boxes set their own width (`--container-demo`, `--gutter`), so the shell looks
  the same whatever the page's `.wrap` is. The markup keeps the `wrap` class as well.
- Transitions use §9's tokens, so they are instant under reduced motion through the page's `:root`.
- With this file, `mcp-surface/styles.css` deletes its own `.sr-only` (`:31`), `:focus-visible`
  (`:30`), `.top*`, `.back*` (`:35-42`) and `.foot*` (`:232-233`) rules.

**Copy for `/mcp-surface/`.**

| Element | Copy | Source |
|---|---|---|
| back link | ← Brett Adams → `/#projects` | label; replaces "brettadams0.github.io" → `/` (`mcp-surface/index.html:26`) |
| project name | mcp-surface | [src: mcp-surface/index.html:36] |
| header links | Source → GitHub · npm → npmjs.com · Résumé → `/assets/resume/Resume_BrettAdams.pdf` | [src: mcp-surface/index.html:28-29]; the résumé link is new |
| h1 | mcp-surface. The `> ` is an `aria-hidden` span (§7.10); `styles.css:46`'s `h1::before` is deleted | [src: mcp-surface/index.html:36] |
| instruction (P9) | The checks from the npm package, running in your browser. Edit a server's `tools/list` response and the report updates as you type. | [src: mcp-surface/index.html:37], unchanged |
| rules card hint | click a rule to skip it | [src: mcp-surface/index.html:80], unchanged |
| How it works, item 1 (**new**, first) | A broken MCP server can look connected while the AI assistant gets a worse tool or none at all. These checks catch that. | [src: mcp/index.html:30-32] |
| How it works, item 2 | The checks are the npm package's own code, mcp-surface 0.3.0, bundled for the browser with esbuild. | [src: mcp-surface/checks.js:1-5; README.md:78-92] |
| How it works, item 3 | This page only reads what is in the editor, hands it to those checks the way the command-line tool does, and draws the result. | [src: mcp-surface/app.js:1-3] |
| How it works, item 4 | Nothing you type leaves this tab. | **measured** 2026-10-22: 0 matches for `fetch(`, `XMLHttpRequest`, `sendBeacon` or `WebSocket` in `mcp-surface/*.js` |
| version chip | Checks from mcp-surface 0.3.0 | [src: mcp-surface/checks.js:1]. Updated in the same step as a `checks.js` rebuild (README.md:91-92); a version can't go stale the way a date can (A19) |
| source links | Source · npm · Case study → `/mcp/` | [src: index.html:121-123] |
| footer | Built by Brett Adams · MIT-licensed source · Third-party licenses · GitHub · LinkedIn · Email | [src: mcp-surface/index.html:87]; contact links in the site footer's order |

- **Item 1** says in plain words why the checks exist, for a business reader who opens a shared demo
  link. **Item 3** replaces a code comment ("parses the editor, builds the same surface") with what
  the page does.

**Markup for `/mcp-surface/index.html`** (it has no front matter, so URLs are root-relative
literals, as `/assets/img/favicon.svg` is today at `:15`):

```html
<link rel="stylesheet" href="styles.css">
<link rel="stylesheet" href="/assets/css/demo-shell.css">
…
<body>
<a class="skip-link" href="#main">Skip to content</a>
<header class="demo-shell">
  <div class="wrap demo-shell-inner">
    <p class="demo-crumb">
      <a class="demo-back" href="/#projects"><span aria-hidden="true">←&nbsp;</span>Brett Adams</a>
      <span class="demo-sep" aria-hidden="true">/</span>
      <span class="demo-name">mcp-surface</span>
    </p>
    <nav class="demo-links" aria-label="Project">
      <a href="https://github.com/brettadams0/mcp-surface">Source</a>
      <a href="https://www.npmjs.com/package/mcp-surface">npm</a>
      <a class="demo-resume" href="/assets/resume/Resume_BrettAdams.pdf">Résumé</a>
    </nav>
  </div>
</header>

<main id="main" class="wrap" tabindex="-1">
  <section class="intro">
    <h1><span class="prefix" aria-hidden="true">&gt; </span>mcp-surface</h1>
    <p class="lede">…unchanged…</p>
  </section>
  <!-- existing editor, report and rules cards (index.html:40-83) -->
  <section class="demo-how" aria-labelledby="how-title">
    <h2 id="how-title">How it works</h2>
    <ul>
      <li>A broken MCP server can look connected while the AI assistant gets a worse tool or none at all. These checks catch that.</li>
      <li>The checks are the npm package's own code, mcp-surface 0.3.0, bundled for the browser with esbuild.</li>
      <li>This page only reads what is in the editor, hands it to those checks the way the command-line tool does, and draws the result.</li>
      <li>Nothing you type leaves this tab.</li>
    </ul>
    <ul class="chips" role="list"><li>Checks from mcp-surface 0.3.0</li></ul>
    <p class="demo-source">
      <a href="https://github.com/brettadams0/mcp-surface">Source</a>
      <a href="https://www.npmjs.com/package/mcp-surface">npm</a>
      <a href="/mcp/">Case study</a>
    </p>
  </section>
</main>

<footer class="demo-foot">
  <div class="wrap demo-foot-inner">
    <p>Built by <a href="/">Brett Adams</a> · <a href="https://github.com/brettadams0/mcp-surface">MIT-licensed source</a> · <a href="THIRD_PARTY_LICENSES.txt">Third-party licenses</a></p>
    <p class="footer-links">
      <a href="https://github.com/brettadams0" data-contact="github">GitHub</a>
      <a href="https://www.linkedin.com/in/bretta/" data-contact="linkedin">LinkedIn</a>
      <a href="mailto:adamsbrett00@gmail.com" data-contact="email">Email</a>
    </p>
  </div>
</footer>
```

**Other mcp-surface fixes in the same PR** (this repo's own files; `checks.js` is not touched):

1. Contrast: `#gutter` and `.finding .rule-tag` move from `--faint` to `--dim`
   (`mcp-surface/styles.css:98`, `:161`). That clears the 17 `color-contrast` nodes
   (`[brettadams0:measure-mcp-surface.json.axe.1440.by_impact.serious]`; 3.25:1 today on `--term`).
2. Scroll region: `.term` per 8.19 (`index.html:69`), and `role="status"` on `#verdict`
   (`index.html:73`).
3. Examples control: today it is `role="radiogroup"` with three `role="radio"` buttons
   (`index.html:44-48`). Each is its own Tab stop (`[brettadams0:measure-mcp-surface.json.viewport.1440.focus_walk]`),
   and nothing handles arrow keys (no `keydown` in `app.js`, measured by grep).
   - Change: plain buttons with `aria-pressed`. Remove `role="radiogroup"` and `role="radio"`; put
     `role="group" aria-label="Example"` on `.seg`.
   - `app.js:218` sets `aria-pressed` instead of `aria-checked`, and `styles.css:82` selects
     `[aria-pressed="true"]`.
4. Focus after a rule click: `renderRules` rebuilds every rule button on each update
   (`app.js:112-134`), so the button just pressed is replaced and focus drops to `body`.
   - Change: before `replaceChildren`, remember `document.activeElement?.dataset.rule`.
   - Give each button `data-rule="<id>"`, and after rebuilding, call `.focus()` on the button with
     that id.
5. State without opacity:
   - `.rule[aria-pressed="true"]` uses `opacity: 0.45` (`styles.css:229`), which drops `--muted`
     text to about 2.15:1 on `--surface-2`. `.rule.call-only` uses 0.6 (`:214`), about 2.81:1 (**computed**
     2026-10-22 by alpha blending).
   - Replace both with full-opacity text in `--muted`. Skipped rules keep the line-through and gain
     a visible "skipped" word in `.rule-level`. Call-only rules show "--call only" in `.rule-what`
     (the RULES text already starts with "--call:", `app.js:22-24`).
6. Form-control borders: the textarea box (`.editor`) and the `select` use `--border` or
   `--border-strong` (1.36–1.54:1). Give them `border-color: var(--muted)` (5.73:1 on `--surface`,
   audit §1) so the control's edge passes SC 1.4.11.
   - Add `.editor:focus-within { box-shadow: var(--shadow-focus); }`, because `.editor` has
     `overflow: hidden` and clips the textarea's own outline (`styles.css:85-92`, `textarea {
     outline: 0 }` at `:110`).
7. Targets at ≤ 640: `.seg button` and `select` get `min-height: 44px`. Today they are 27 px (audit
   issue 15).
8. Page title names Brett: §12 sets the exact string. It must contain "Brett Adams" (audit issue 20).
9. Move the `<noscript>` paragraph (`index.html:90`, after the footer today) inside `main`, so its
   text sits in a landmark when it shows.

**States** (shell):

| Element | Default | Hover | `:focus-visible` | Active |
|---|---|---|---|---|
| `.demo-back`, `.demo-links a` | `--muted` | `--text` | focus ring | `--amber` |
| `.demo-resume` | as `.nav-resume` (8.1) | as 8.1 | focus ring | as 8.1 |
| `.demo-how a`, `.demo-foot a` | `--muted`, underlined in running text | `--text` | focus ring | — |

**Breakpoints.**

| Width | Behaviour |
|---|---|
| ≥ 861 | One header row, `min-height: 52px` (53 px measured in the scratch check): crumb left, links right. Not sticky (as today), so the demo keeps the full height. Container `--container-demo` (1320 px) |
| 641–860 | Same; the crumb may wrap |
| ≤ 640 (390) | Row 1: crumb. Row 2: Source · npm · Résumé. Every link 44 px tall (today the back link is 17 px, `[brettadams0:measure-mcp-surface.json.viewport.390.small_targets_24]`). `.demo-how` full width under the rules card |

**Motion and accessibility.**
- The shell adds no motion. Each demo keeps its own reduced-motion behaviour (neuronet-arena starts
  paused, `neuronet-arena/ui/app.js:60`).
- Landmarks: `banner`, a `nav` "Project", `main`, `contentinfo`.
- Skip link first in the Tab order (today missing on both demos, audit issue 13).
- Analytics: demo pages load no GA today (fact sheet §11). Adding the include is a Decision (audit
  issue 11). The `data-contact` attributes are there so it works the day GA is added; they do
  nothing without it.

**Future demo pages** (`/mazefinder/`, `/border-waits/`): the same header, footer and `.demo-how`
block. `/mazefinder/` is this repo's own files (`handoff/mazefinder-live-demo.md:125-126`), so it
links `demo-shell.css`. `/border-waits/` is a verbatim sync of its repo's `web/`, so `web/styles.css`
carries a copy of the `demo-shell.css` rules (7.11). Each is filled from its handoff file:
- "Thread pool: native build only" (`handoff/mazefinder-live-demo.md:112-113`);
- border-waits' Fetch / Model / Test / Publish blocks and its load-size line;
- one instruction sentence directly above each control (P9);
- a "what loads" line before any heavy engine (I2): border-waits' "Load DuckDB to run SQL (about
  7.7 MB)" button.

**Upstream equivalent for neuronet-arena** (a PR in the neuronet-arena repo against `web/`, then a
re-sync here with the README's `rsync` line, re-applying the one local difference, `README.md:75-76`;
nothing is edited in this repo's `neuronet-arena/`):

| Upstream change (`web/…`) | Why | Today (this repo's synced copy) |
|---|---|---|
| Header to the shell markup, with absolute URLs because the upstream page also runs on its own: back link "← Brett Adams" → `https://brettadams0.github.io/#projects`, links Source and Résumé → `https://brettadams0.github.io/assets/resume/Resume_BrettAdams.pdf` | audit issue 13 | `neuronet-arena/index.html:26-33` |
| Skip link and `<main id="main" tabindex="-1">` | P16 | none |
| Copy the shell rules into `web/styles.css` (the upstream repo can't load `/assets/css/demo-shell.css` when run on its own) | — | — |
| `:focus-visible` on links, `summary` and the focusable canvas, not only `.btn, .seg button, input` | links fall back to the browser ring (`[brettadams0:measure-neuronet-arena.json.viewport.1440.focus_walk]`) | `neuronet-arena/styles.css:158` |
| `td.pending` from `--faint` to `--dim` | 3.37:1 or less | `neuronet-arena/styles.css:261` |
| Empty `<th></th>` → `<td></td>` | axe minor `empty-table-header` (audit issue 27) | `neuronet-arena/index.html:118` |
| Title naming Brett (exact string in §12) | audit issue 20 | `neuronet-arena/index.html:6` |
| Font loading / fallback metrics per §7 | CLS 0.1643 on mobile (audit issue 5) | `neuronet-arena/index.html:18-20` |
| Footer contact links in the site's order | WCAG 3.2.6 | `neuronet-arena/index.html:146-148` |
| Already done upstream, keep: instruction line above the controls (`index.html:78`, P9), the "How it works" blocks Sense / Think / Evolve / Same bits (`:124-141`, P10), the data-table alternative to the chart, and the paused start under reduced motion | — | — |

**Research.**
- P9, one instruction sentence above each control: ciechanow `[ciechanow:desktop-fold.webp]`,
  redblobgames `[redblobgames:inner-desktop-fold.png]`.
- P10, how it was built plus a source link:
  - robinlinacre's two build sentences and source link `[robinlinacre:inner-desktop-fold.png]`;
  - redblobgames' Meta section `[redblobgames:tiles/d-02.png]`;
  - wisl's version badges `[wisl:inner-desktop-fold.png]`, the model for the version chip.
- I2, what loads before it loads: madebyevan `[madebyevan:inner-desktop-fold.png]`.
- I13, safe to click: deshraj-jogiya `[deshraj-jogiya:record.home.desktop.dom.features.demo_links]`.
  Hence "Nothing you type leaves this tab".
- P16, skip link on inner pages: samwho `[samwho:record.inner.desktop.dom.a11y_manual.skip_link]`.
- Hiring: S06 (checks GitHub for taste), S09, S12 ("why you built it like you did").

### 8.21 Component index

| § | Component | Classes | New? | PR in §16 |
|---|---|---|---|---|
| 8.0 | Visual-only prefixes and hidden text | `span.prefix` (`aria-hidden`), `.sr-only` | changed / new | 1 Tokens and prefixes |
| 8.1 | Header and nav | `.site-header`, `.site-nav`, `.nav-sections` | row 2 new | 3 Header and mobile nav |
| 8.2 | Hero | `.hero`, `.hero-grid`, `.status`, `.kicker`, `.lede`, `.actions`, `.hero-jump` | jump link new | 4 Hero |
| 8.3 | At a glance | `.glance`, `.glance-title`, `.glance-list`, `.glance-now`, `.glance-results`, `.glance-tools` | new | 4 Hero |
| 8.4 | Timeline entry | `.timeline`, `.job`, `.job-head`, `.job-title`, `.job-org`, `.job-when`, `.job-points`, `.job-more` | disclosure new | 5 Experience |
| 8.5 | Featured project card | `.project-featured` | — | 6 Project cards |
| 8.6 | Standard project card | `.project`, `.project-shot`, `.project-name`, `.project-line`, `.links` | — | 6 |
| 8.7 | Wide project card | `.project-wide` | new | 6 (rule), used in 13 |
| 8.8 | Kind label | `.project-kind` | new | 6 |
| 8.9 | Also list | `.also`, `.also-name`, `.also-desc`, `.also-links`, `.also-more` | links cell new | 6 |
| 8.10 | Chip | `.chips` | — | 1 (style), 6 (copy) |
| 8.11 | Buttons | `.btn`, `.btn-primary` | — | 1 (tokens), 4 |
| 8.12 | Live demo badge | `.badge-live` | moved | 6 |
| 8.13 | Skills | `.skills`, `.two-col` | — | 7 |
| 8.14 | Education | `.edu` | — | 7 |
| 8.15 | Contact | `.contact`, `.contact-email` | — | 7 |
| 8.16 | Footer | `.site-footer`, `.footer-links` | — | 7 |
| 8.17 | 404 | `.not-found` | — | 8 |
| 8.18 | `/mcp/` template | `.post`, `.case-study`, `.case-facts`, `.case-for`, `.flow-figure`, `.flow-row`, `.flow-label`, `.flow`, `.post-body`, `.post-next` | For line, two-row flow, next new | 8 |
| 8.19 | Scroll region | `pre[tabindex]`, `.term`, `.table-scroll` | new pattern | 8, 9 |
| 8.20 | Demo shell | `assets/css/demo-shell.css`: `.skip-link`, `.sr-only`, `.prefix`, `.demo-shell`, `.demo-shell-inner`, `.demo-crumb`, `.demo-back`, `.demo-sep`, `.demo-name`, `.demo-links`, `.demo-resume`, `.demo-how`, `.demo-source`, `.chips`, `.demo-foot`, `.demo-foot-inner`, `.footer-links` | new | 9; neuronet-arena upstream in 11a; border-waits copy in 12 |

## 9. Motion

**The rule.**
- On site pages, things move only when the visitor does something, and what moves is colour.
- Demos move on their own pages, and the visitor can stop them.
- Nothing animates on load, nothing is tied to scrolling, and no animation changes layout.

**Today the site is already there.**
- The only motion is hover transitions of 0.15–0.2 s (`assets/css/site.css:172`, `:436`;
  `mcp-surface/styles.css:206`). There are no `@keyframes` in any stylesheet (measured: `grep`).
- Smooth anchor scrolling is turned off under reduced motion (`site.css:38-42`).
- neuronet-arena starts paused when reduced motion is set (`neuronet-arena/ui/app.js:60`). It has a
  Pause button and a Space shortcut (`neuronet-arena/index.html:60`, `:78`).
- Interaction and motion scores 4, and no site in the set scores higher (§3.3). This section
  keeps that and writes it down as rules.

**Evidence for the rule:**
- **Motion that waits for the visitor** is P17: ciechanow's simulations wait behind a play button
  `[ciechanow:tiles/d-03.png]`, redblobgames waits for Start `[redblobgames:tiles/id-01.png]`, and
  samwho's widgets are driven by the reader `[samwho:tiles/id-02.png]`.
- **What the rule rules out:**
  - **Splash gates (A4).** bruno-simon shows zero readable characters at 3, 6, 12, 20 and ~35 s
    `[bruno-simon:fold-30s-probe.json]`.
  - **Scroll-driven reveals (A6).** richardshuai's projects and contact are blank in the full-page
    shot `[richardshuai:tiles/d-06.png]`.
  - **Autoplay (A7).** rauno's `/craft` autoplays 68 videos
    `[rauno:record.inner.desktop.network_initial.by_type.media]`.
  - **Typed role lines (A8).** mehdio `[mehdio:mobile-fold.webp]`.
  - **Faded-in text.** A fade-in paragraph held wisl's LCP to 5.0 s
    `[wisl:record.summary.lcp_ms_desktop]` (P15's caution).

### 9.1 Tokens

These are defined in §7.1's `:root` block: at the top of `site.css`, and in each demo page's copy of
it (§7.11).

| Token | Value | Use | Reduced motion |
|---|---|---|---|
| `--dur-fast` | 120ms | Hover and press feedback: link and nav colour, button fill and border | 0ms |
| `--dur` | 180ms | Card border on hover and `:focus-within` | 0ms |
| `--dur-slow` | 240ms | Demo pages only: a panel changing state as a result updates (mcp-surface `.rule` turning "hit"). Never on site pages. | 0ms |
| `--ease-out` | `cubic-bezier(0.2, 0, 0, 1)` | Everything that answers the pointer: quick start, soft stop | n/a |
| `--ease-in-out` | `cubic-bezier(0.4, 0, 0.2, 1)` | Demo-page state changes that aren't direct pointer feedback | n/a |

- **Values.** Today's are 150 and 200 ms. The new ones are a little quicker, so a hover reads as an
  immediate response. That is a design call (**estimated**), not a measurement. Keep anything a
  visitor waits on under 250 ms.
- **Reduced motion.** The `:root` block sets all three durations to `0ms` under
  `@media (prefers-reduced-motion: reduce)` (§7.1). Every transition written with the tokens then
  becomes instant, with no per-component rule.
- **How to write a transition:**
  - Name each property, as in `transition: color var(--dur-fast) var(--ease-out)`. Never
    `transition: all`.
  - Never use a raw `ms` or `s` value.
  - Only these properties may transition: `color`, `background-color`, `border-color` and
    `text-decoration-color`.

### 9.2 What animates: the complete list

Anything not in this table does not animate. Adding a row is a spec change.

| # | Element | Trigger | Property | Duration, easing | Under `prefers-reduced-motion: reduce` | Today |
|---|---|---|---|---|---|---|
| 1 | Text links (`a`, `.lede a`, `.links a`, `.also a`, footer, back links) | hover | `color`, `text-decoration-color` | `--dur-fast`, `--ease-out` | instant | no transition (`site.css:58-66`, `:299-306`) |
| 2 | Nav links (`.site-nav a`, `.nav-sections a`) | hover | `color` | `--dur-fast`, `--ease-out` | instant | none (`site.css:137-144`) |
| 3 | Header Résumé (`.nav-resume`) | hover, active | `background-color`, `color` | `--dur-fast`, `--ease-out` | instant | none (`site.css:146-157`) |
| 4 | Buttons (`.btn`, `.btn-primary`) | hover, active | `background-color`, `border-color` | `--dur-fast`, `--ease-out` | instant | 0.15 s ease (`site.css:172`) |
| 5 | Project card (`.project`) | hover, `:focus-within` | `border-color` | `--dur`, `--ease-out` | instant | 0.2 s ease (`site.css:436`) |
| 6 | In-page jumps: nav, `.nav-sections`, the hero's "Projects and live demos" link, the skip link | click or Enter | native smooth scroll | the browser's | `scroll-behavior: auto`, an instant jump | smooth, off under reduce (`site.css:34`, `:38-42`) |
| 7 | mcp-surface `.rule` | hover | `border-color`, `background-color` | `--dur-fast`, `--ease-out` | instant | 0.15 s ease (`mcp-surface/styles.css:206`) |
| 8 | mcp-surface `.rule.hit` | the report finds a hit as you type | `border-color`, `background-color` | `--dur-slow`, `--ease-in-out` | instant | instant today; optional |
| 9 | neuronet-arena simulation (canvas) | starts on load, on its own page only | the simulation | its own frame loop | starts paused; the visitor presses Play (`ui/app.js:60`). Unchanged; upstream. | same |
| 10 | Future demos (`/mazefinder/`, `/border-waits/`) | a Run or Play control, or load, on their own page only | engine output | the demo's | starts paused, or shows a static first frame and waits for Run | not built |

**Smooth scrolling becomes opt-in.** Replace `site.css:34` and `:38-42` with:

```css
@media (prefers-reduced-motion: no-preference) {
  html { scroll-behavior: smooth; }
}
```

The result is the same as today. A browser that doesn't support the media query gets the instant
jump, which is the safe default. `scroll-padding-top` (§7.2) still keeps the target clear of the
sticky header.

**Demo pages.** Rows 9 and 10 are the only continuous motion anywhere, and they meet two WCAG
criteria:
- **SC 2.2.2 (Pause, Stop, Hide):** anything that moves for more than 5 s on its own has a visible
  Pause control and a keyboard path. neuronet-arena has both, the Pause button and Space
  (`neuronet-arena/index.html:60`, `:78`).
- **SC 2.3.1:** nothing flashes more than three times a second.

A demo page states how to start or stop the demo in its "How it works" line (§8, demo shell).

### 9.3 What never animates

Each of these is checked in 9.5.

- **Nothing on load.**
  - No entrance fades, slides, staggered reveals, counters or loaders.
  - The LCP element (`p.lede` today, `[brettadams0:measure-home.json.perf.mobile.lcp]`) and the h1
    are painted in their final state.
  - Measured in the mock: `document.getAnimations()` returns 0 after load at 390 and 1440 (§6.2).
- **No text changes by itself.** No typed, rotating or cycling role line (A8). The status line,
  kicker and lede are static text.
- **Nothing is tied to scrolling.**
  - No scroll-jacking, parallax, `animation-timeline: scroll()` or `view()`, scroll-snap on the page,
    or reveal-on-scroll (A6).
  - The sticky header never hides, shrinks or changes on scroll. The `.nav-sections` row simply
    scrolls away (§8.1).
  - Native scrolling only, with no smooth-scroll library.
- **Nothing that changes layout.**
  - No transition or animation on `width`, `height`, `margin`, `padding`, `top`/`left`/`inset`,
    `font-size`, grid tracks or `transform`.
  - `<details>` disclosures (§8.4 "2 more") open and close instantly.
  - Lazy images appear in their reserved 16:10 box with no fade (§7.9).
- **Focus and the skip link.** Both appear instantly (§10.6). A focus ring never fades or grows.
- **Decorative dots.** The status-line dot and the Live demo badge dot never pulse or blink (§8.12).
- **Hover.** Cards and buttons never lift, scale or tilt. A target never moves under the pointer.
- **Media and effects:**
  - no video, GIF, Lottie, canvas or WebGL on any site page (A5, A7, and the standing rule);
  - no custom cursor, cursor trail, animated favicon or title;
  - no View Transitions between pages.

### 9.4 No layout shift, nothing delayed

- **Only colour moves.** Every property in 9.2 is paint-only, so no animation can cause layout
  shift. CLS from motion is 0 by construction.
- **The other shift sources are handled elsewhere:**
  - font swap, by the metric-matched fallback (§7.5; measured: 0 of 5 test paragraphs re-wrap);
  - lazy images, by `aspect-ratio` plus `width`/`height` (§7.9).
- **No script runs motion.** The homepage's only script is `analytics-events.js`. Its
  `requestAnimationFrame` (`assets/js/analytics-events.js:134`) throttles scroll measurement and
  draws nothing.
- **Nothing waits on a timer or an animation before it shows.** All content is in the HTML at first
  paint.
- **Budget:** CLS under 0.05 at 390 and 1440 on every page (§11). For neuronet-arena that needs the
  upstream font fix (§7.11).

### 9.5 Checks for every PR

These go in each PR's verification (§16), using Playwright through `handoff/tools/measure.mjs` or a
one-off script.

1. **Nothing runs after load.** On `/`, `/mcp/` and `/404.html` at 390 and 1440, run
   `await page.evaluate(() => document.getAnimations().length)` after `networkidle` and
   `document.fonts.ready`. It must return `0`.
2. **Reduced motion makes everything instant.** It must return `[]` and `'auto'`.

   ```js
   await page.emulateMedia({ reducedMotion: 'reduce' });
   await page.goto(url, { waitUntil: 'networkidle' });
   const moving = await page.evaluate(() => [...document.querySelectorAll('*')]
     .filter(el => getComputedStyle(el).transitionDuration.split(',').some(d => parseFloat(d) > 0)
                || getComputedStyle(el).animationName !== 'none')
     .map(el => el.tagName.toLowerCase() + '.' + String(el.className).trim().replace(/\s+/g, '.')));
   const scroll = await page.evaluate(() => getComputedStyle(document.documentElement).scrollBehavior);
   // expect(moving).toEqual([]); expect(scroll).toBe('auto');
   ```

   Measured on the mock, 2026-10-26, at 390 and 1440:
   - `reduce`: 0 moving elements, `scrollBehavior` `auto`.
   - `no-preference`: transitions only on links and buttons: the skip link, brand, nav links,
     Résumé, the Geotab link, the four buttons and the jump link, with `scrollBehavior` `smooth`.
3. **Static grep**, which must print nothing:
   `grep -nE "@keyframes|animation:|transition:[^;]*(all|width|height|margin|padding|top|left|transform)|transition:[^;]*[0-9](ms|s)[ ,;]" assets/css/*.css mcp-surface/styles.css`.
   Today it prints the three raw-duration transitions listed in 9.2 (`site.css:172`, `:436`;
   `mcp-surface/styles.css:206`). PR 1 replaces the two in `site.css` with tokens, and PR 9 the one in
   `mcp-surface/styles.css`.
4. **CLS:** under 0.05 at both sizes on every page, from the `measure.mjs` budget run (§11).
5. **neuronet-arena (upstream PR only):** with `reducedMotion: 'reduce'`, the arena doesn't advance
   in 2 s. Compare two canvas `toDataURL()` snapshots taken 2 s apart. The Play control starts it.

### 9.6 Writer's notes

- **Writer's note:** none of this changes a standing rule. Brett's demos are allowed to start on
  their own page (`handoff/mazefinder-live-demo.md:12-13`), and 9.2 rows 9–10 keep that. The only
  added requirement is the reduced-motion default, which neuronet-arena already meets.

## 10. Accessibility

The target is **WCAG 2.2 Level AA** on every page this repo serves:
- the site pages: `/`, `/mcp/`, `/404.html`;
- the demos: `/mcp-surface/`, and later `/mazefinder/` and `/border-waits/`.

`/neuronet-arena/` is held to the same bar through the upstream PR (§8.20), because its files are
synced and never edited here.

Today the homepage is one of the cleaner sites in the set. It has 0 axe violations at both sizes, and
only 2 of the 29 researched homepages are clean of serious and critical violations (measured,
§3.4 P16). The redesign has to keep that and bring the inner pages up to it.

### 10.1 Pass bar

**The hard bar: zero serious and zero critical axe violations** on `/`, `/mcp/`, `/mcp-surface/` and
`/404.html`, at 390×844 and at 1440×900, on a local build:

```sh
jekyll build
node handoff/tools/measure.mjs --site _site --path / --path /mcp/ --path /mcp-surface/ --path /404.html --axe --json /tmp/axe.json
```

- `measure.mjs` injects axe-core 4.10.2 (SHA-256 pinned, `handoff/tools/measure.mjs:76-78`).
- It runs every rule axe enables by default (`axe.run(document, { resultTypes: ['violations',
  'incomplete'] })`, `:231`). That covers best-practice rules (`region`, `landmark-unique`,
  `heading-order`, `scrollable-region-focusable`) as well as WCAG A and AA, so §8's landmark and
  heading rules are tested, not just stated.

| Page | Baseline 390 (crit / ser / mod / min) | Baseline 1440 | Target, both sizes | Fixed by |
|---|---|---|---|---|
| `/` | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 | keep (every PR) |
| `/mcp/` | 0 / **1** / 0 / 0 (`scrollable-region-focusable`) | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 | §8.19, PR 8 |
| `/mcp-surface/` | 0 / **2** / 0 / 0 (`color-contrast` ×17, `scrollable-region-focusable`) | same | 0 / 0 / 0 / 0 | §8.19–8.20, PR 9 |
| `/404.html` | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 | keep |
| `/neuronet-arena/` (not gated here) | 0 / 0 / 0 / 1 (`empty-table-header`) | same | 0 / 0 / 0 / 0 after re-sync | upstream PR 11 |

Baseline: **measured** 2026-10-21 (audit §1, `[brettadams0:measure-<page>.json.axe]`).

- Serious and critical are the gate. On these four pages no PR may add a moderate or minor violation
  either, since the baseline there is zero.
- axe reports the homepage's 32 `color-contrast` items at 390 as "incomplete" because it can't
  resolve the hero gradient. §10.13 settles them by hand.
- PR numbers are §16's: 0 measurement tools · 1 tokens and prefixes · 3 header and mobile nav ·
  4 hero · 5 experience · 6 project cards · 7 skills, contact, footer · 8 `/mcp/` and 404 ·
  9 mcp-surface · 11a upstream neuronet-arena · 11b re-sync.

axe cannot check the rest of this section: focus order, focus visibility, target size beyond its own
rule, names polluted by visual prefixes, reflow, and whether alt text makes sense. §10.15 adds a
`--a11y` mode to `measure.mjs` that checks those. It ships in PR 0.

### 10.2 Skip link

- The first focusable element on every page, site and demos: `<a class="skip-link" href="#main">Skip
  to content</a>` (§8.1, §8.20). Demo pages have none today (audit issue 13).
- Target: `<main id="main" tabindex="-1">`. With `tabindex="-1"`, activating the link moves focus
  into `main`, so the next Tab continues from the content, and a screen reader announces where it
  landed. `main:focus { outline: none; }` is the one allowed outline removal: `main` is not a
  control, and a ring round the whole page would mislead.
- Hidden until focused, then shown at top left, above the sticky header (`z-index: var(--z-skip)`,
  higher than `--z-header`).
- Text `--bg` on `--amber` (10.90:1, §7.3 row 32), as today (`site.css:87`) and as in the mock
  (`first-screen.html:171`).
- Acceptance:
  - press Tab once and `document.activeElement` is the skip link, inside the viewport;
  - press Enter and `document.activeElement.id === 'main'`;
  - this holds on all four gated pages at both sizes.

### 10.3 Landmarks

Every visible piece of content sits inside a landmark (axe `region`), and no two landmarks of the
same role share a name (axe `landmark-unique`).

| Page | Landmarks after the redesign |
|---|---|
| `/` | `banner` (header.site-header) · `navigation` "Main" · `navigation` "Sections" (≤ 860 px only; `display: none` above) · `main` · regions "Brett Adams" (hero), "Experience", "Projects", "Skills", "Contact" (each `section[aria-labelledby]`) · `contentinfo` |
| `/mcp/` | `banner` · `navigation` "Main" · `navigation` "Sections" (≤ 860) · `main` › `article` · region "Terminal output of npm run surface" (the `pre`, §8.19) · `contentinfo` |
| `/404.html` | `banner` · `navigation` "Main" · `navigation` "Sections" (≤ 860) · `main` · region "Page not found" · `contentinfo` |
| `/mcp-surface/` | `banner` (header.demo-shell) · `navigation` "Project" · `main` · regions "tools/list response", "Report", "Report output" (`.term`), "Rules", "How it works" · `contentinfo` |
| `/neuronet-arena/` (upstream) | `banner` · `navigation` "Project" · `main` · `complementary` (aside.side, today) · `contentinfo` |

Rules:
- The At-a-glance card is a `div` (§6.4, §8.3): not a landmark, and never a `section_view` value. An
  `aside` inside the hero region would trip `landmark-complementary-is-top-level`. Its h2 "At a
  glance" is the screen-reader stop.
- The `/mcp/` closing links are a `p`, not a third `nav` (§8.18).
- mcp-surface's `<noscript>` moves inside `main` (§8.20 fix 9).

### 10.4 Heading outline, per page

One `h1` per page and no skipped levels. Headings hold only their words: no badge, prefix or link.
The names below are the **accessible names** a screen reader reads, and §10.15 checks them exactly.

`/`
- h1 Brett Adams
  - h2 At a glance
  - h2 Experience
    - h3 Data Analyst Intern at Geotab
    - h3 Data Management Associate, Co-op at Ontario Ministry of Transportation
    - h3 Digital Solutions, Co-op at CIMCO Refrigeration
    - h3 Digital Solutions at CIMCO Refrigeration
  - h2 Projects
    - h3 neuronet-arena
    - h3 MCP servers for Google Workspace and Reddit
    - h3 mcp-surface
    - h3 PromptPal
    - h3 TechNest
  - h2 Skills
  - h2 Education
    - h3 University of Windsor
  - h2 Contact

Today the names are "EXPERIENCE" with a "## " prefix, "Data Analyst Intern @ Geotab" and
"neuronet-arena Live demo" (audit issues 6 and 23, `[brettadams0:measure-home.json.page.headings]`).
Later cards add h3 "border-waits" after neuronet-arena and h3 "MazeFinder", in the PRs that ship
them.

`/mcp/` (after §8.18; today's outline is `[brettadams0:measure-mcp.json.page.headings]`)
- h1 Two MCP servers that act on real accounts, and the tool that checks them
  - h2 One check at the end of CI
  - h2 The repositories (second, as today)
  - h2 What the review found
    - h3 Header injection through an email subject
    - h3 Formula injection into a spreadsheet
    - h3 Requests that escaped their API path
    - h3 Reddit failures that looked like success
    - h3 Calendar invitations that were never sent
    - h3 A consent flow anyone on the network could answer
    - h3 282 MB of dependencies, cut to 45 MB
    - h3 The checker had a bug of its own
  - h2 What both servers now have
  - h2 Limits (new, §8.18; left out if Brett vetoes the copy change)

`/404.html`
- h1 Page not found

`/mcp-surface/` (today's three h2s plus the new block; `[brettadams0:measure-mcp-surface.json.page.headings]`)
- h1 mcp-surface (today the accessible name includes the CSS "> ")
  - h2 tools/list response
  - h2 Report
  - h2 Rules
  - h2 How it works

`/neuronet-arena/` (upstream, today's outline kept; `[brettadams0:measure-neuronet-arena.json.page.headings]`)
- h1 neuronet-arena (today the accessible name includes the CSS "> ", `neuronet-arena/styles.css:68`;
  the upstream PR fixes it)
  - h2 Food eaten per agent
  - h2 Leader
  - h2 Cross-engine check
  - h2 Sense
  - h2 Think
  - h2 Evolve
  - h2 Same bits

Future demo pages (`/mazefinder/`, `/border-waits/`): h1 is the project name, then one h2 per panel,
ending with h2 "How it works".

### 10.5 Keyboard order

The Tab order follows the visual order at every width. There is no positive `tabindex` and no focus
trap. Project screenshots never take focus (`tabindex="-1"`, §10.8).

**`/` at ≥ 861 px** (34 + S stops, where S is the number of "N more" summaries after PR 5's fit
loop; 38 with a summary in every role; today 33, measured
`[brettadams0:measure-home.json.viewport.1440.focus_walk]`). The jump link (stop 13) is shown only
above 860 px (§8.2):

1. Skip to content
2. brett adams
3. Experience
4. Projects
5. Skills
6. Contact
7. Résumé
8. Geotab (the link in the lede)
9. Résumé (PDF)
10. Email
11. LinkedIn
12. GitHub
13. Projects and live demos
14 … 13 + S. One summary per role that has a `details.job-more` after PR 5's fit loop, in role
    order, named "<N> more from <employer>[, year]" (§8.4), where N is the number of bullets moved:
    "<N> more from Geotab", "<N> more from Ontario Ministry of Transportation", "<N> more from CIMCO
    Refrigeration, 2024", "<N> more from CIMCO Refrigeration, 2023". §8.4's estimate is S = 3 (no
    CIMCO 2023 summary).
Then, numbered from 14 + S:
- 12 card links, card by card:
  - Live demo: neuronet-arena · Source: neuronet-arena;
  - Case study: MCP servers · Google Workspace source · Reddit source;
  - Live demo: mcp-surface · Case study: mcp-surface · npm: mcp-surface · Source: mcp-surface;
  - Live app: PromptPal · Source: PromptPal (one stop fewer if PromptPal's fallback drops "Live
    app", §8.6);
  - Source: TechNest.
- Source: MazeFinder
- More on GitHub
- adamsbrett00@gmail.com
- LinkedIn
- GitHub
- Résumé (PDF)
- GitHub (footer)
- LinkedIn (footer)
- Email (footer)

**`/` at ≤ 860 px** (33 + S stops: 37 with a summary in every role, 36 at §8.4's estimate):
- Stops 3–7 become Résumé, Experience, Projects, Skills, Contact (row 1, then row 2).
- The jump link is hidden (`display: none` at ≤ 860 px, §8.2), so it is not a stop; row 2's
  "Projects" does its job.
- The rest is unchanged, one number earlier.

An opened disclosure adds no stops, because it holds text only.

**`/mcp/`:**
1. Skip to content.
2. brett adams.
3. The header nav: four section links and Résumé, or at ≤ 860 Résumé and then the four.
4. ← Brett Adams.
5. Model Context Protocol, then mcp-surface (links in the intro). The For line and the two-row
   `.flow` diagram above the intro have no links.
6. The code region.
7. google-workspace-mcp, reddit-mcp, mcp-surface ("The repositories", the second section, as
   today).
8. Try mcp-surface's checks in your browser.
9. All projects.
10. The footer's three links.

At 390 today the `pre` is already a stop, because Chromium makes scrollable boxes focusable
(`[brettadams0:measure-mcp.json.viewport.390.focus_walk]`). `tabindex="0"` makes that true in every
browser and at every width.

**`/404.html`:**
1. Skip to content.
2. brett adams.
3. The header nav.
4. Back to the homepage, Projects, Résumé (PDF).
5. The footer's three links.

**`/mcp-surface/`:**
1. Skip to content.
2. Brett Adams, then Source, npm and Résumé (the shell).
3. npm package (the lede link).
4. Broken, Healthy, Empty.
5. The JSON editor (textarea).
6. fail on (select).
7. Report output (scroll region).
8. The 19 rule buttons.
9. How it works: Source, npm, Case study.
10. The footer: Brett Adams, MIT-licensed source, Third-party licenses, GitHub, LinkedIn, Email.

Tab leaves the textarea, which is the browser default; don't capture Tab for indentation.

Keyboard operation beyond Tab:
- `<details>` opens with Enter or Space.
- mcp-surface's example buttons and rule buttons toggle with Enter or Space, and focus stays on the
  pressed button after the report re-renders (§8.20 fixes 3–4).
- neuronet-arena keeps Space and N (`neuronet-arena/index.html:60`, `:67`).

### 10.6 Focus ring

```css
:focus-visible { outline: 2px solid var(--focus); outline-offset: 3px; border-radius: 4px; }
.site-nav a:focus-visible, .nav-sections a:focus-visible, .links a:focus-visible,
pre[tabindex]:focus-visible, .term:focus-visible, .table-scroll:focus-visible { outline-offset: 2px; }
main:focus { outline: none; }
.editor:focus-within { box-shadow: var(--shadow-focus); }   /* mcp-surface: .editor clips the textarea's outline */
```

- `--focus` is `var(--amber)`, today's ring (`site.css:68-72`). Against what sits next to it:
  - 10.90:1 on `--bg`, 9.94:1 on `--surface` and 9.22:1 on `--surface-2` (**computed** 2026-10-22,
    WCAG 2.x formula);
  - so the 3:1 non-text minimum (SC 1.4.11) holds everywhere.
  - On the amber primary button, the 3 px offset leaves a `--bg` gap between ring and fill.
- Every Tab stop shows this ring: measured today on `/` and `/mcp/` (audit §5, "checked and fine").
  neuronet-arena's links fall back to the browser ring (`[brettadams0:measure-neuronet-arena.json.viewport.1440.focus_walk]`);
  that is fixed upstream (§8.20).
- `outline: none` appears in exactly two places: `main:focus` above, and the mcp-surface textarea
  (`styles.css:110`), which gets the `.editor:focus-within` ring instead.
- **Focus not obscured (SC 2.4.11, new in 2.2).** The sticky header is the only sticky or fixed
  element on any page. `html { scroll-padding-top: calc(var(--header-h) + 16px); }` keeps every
  focused element and every anchor target clear of it (today `scroll-padding-top: 72px`,
  `site.css:35`). §10.15's `obscured` check tests every focus stop below the header (not the skip
  link, and not the header's own links) after the browser scrolls it into view.
- `:focus-within` on project cards (border to `--border-strong`, §8.5–8.6) is an extra cue, not a
  replacement for the ring.
- Motion: the ring appears instantly, with no transition (§9).

### 10.7 Target sizes

- **Minimum, every page:** every pointer target is at least 24 × 24 CSS px (SC 2.5.8). New components
  don't rely on the spacing exception.
- **Exempt:** links inside running text (the lede's "Geotab", `/mcp/` body links), under 2.5.8's
  inline exception.
- **Goal on phones (≤ 640 px):** primary controls and every link row at least 44 px tall. That is the
  target the research supports (§3.4 P4, estimated).
- **Header values come from the mock** (§8.1, `first-screen.html:176-195`, `:291`, `:293-301`), as
  measured in §6.2.

| Target | Today (measured, `[brettadams0:measure-<page>.json.viewport.<size>.small_targets_24]`) | After |
|---|---|---|
| Brand "brett adams" | 19 px tall | 44 px (`inline-flex`, `min-height: 44px`; **measured** y 6–50 in the mock) |
| Desktop nav links | 22 px | 44 px (**measured** in the mock, y 6–50, §6.2) |
| Mobile section links | none (hidden) | 44 × 90 px at 390, 44 × 72 px at 320 (**measured** in the mock) |
| Header Résumé | 36 px | 36 px at every width (**measured** in the mock, y 10–46). It passes 24 px; on phones the 44 px hero button opens the same PDF on the same screen |
| Hero "Résumé (PDF)" | 42 px | 44 px at every width (**measured** in the mock: 143 × 44 at 1440, 358 × 44 at 390) |
| Other hero buttons and contact buttons | 42 px | 44 px (`min-height`) |
| "Projects and live demos" (shown > 860 px only; hidden at ≤ 860) | new | 44 px |
| Job disclosure summary | new | 24 px; 32 px at ≤ 640 |
| Project card links | 24 px | 24 px; 44 px at ≤ 640 |
| `.also` links | 19 px | 24 px; 44 px at ≤ 640 |
| Footer links | 18 px | 24 px; 44 px at ≤ 640 |
| `/mcp/` "← Brett Adams" | 17 px | 24 px; 44 px at ≤ 640 |
| mcp-surface back link, header links | 17 px, 22 px | 24 px; 44 px at ≤ 640 (**measured** in a scratch copy with §8.20's `demo-shell.css`) |
| mcp-surface header Résumé (new) | — | 36 px; 44 px at ≤ 640 |
| mcp-surface example buttons, `select` | 27 px (audit issue 15) | 44 px at ≤ 640 |

### 10.8 Alt text

**Decision: project screenshots stay `alt=""` inside a link that is `aria-hidden="true"
tabindex="-1"`, as today (`index.html:86-87`).**

Why:
1. The screenshot link always goes where the card's first text link goes (the rule in §8.5). Making
   it reachable would put a second link to the same place in the Tab order and the links list of
   every card: 5 extra stops today, 7 later.
2. The text link is the better name: it says "Live demo" or "Case study". An image link would need
   alt text that repeats it.
3. Nothing a screen-reader user needs is only in the picture. The card's name, line and chips carry
   the facts, and the screenshot is a preview of the page the link opens.
4. Pointer users keep the large click target. The homepage's `project_click` already counts
   screenshot clicks: 18 events, including the `aria-hidden` screenshot links (audit §4).
5. It passes axe today. `aria-hidden-focus` is satisfied because the link has `tabindex="-1"`
   (0 violations, audit §1).

Conditions:
- The screenshot's `href` must equal the first text link's. That is the fix for the MCP card, whose
  screenshot points at GitHub while its first link is `/mcp/` (audit issue 24).
- If a card ever has no text link, its screenshot link becomes an ordinary link with alt text naming
  the destination.

Alternatives considered:
- No link on the image. Loses the click target and the event.
- A focusable image link with alt text. Duplicates a stop on every card.

Other rules:
- Decorative marks carry no text and need no alt: the status dot, the badge dot, the timeline dots,
  the `.flow` arrows and the hero background. They are CSS or empty `aria-hidden` spans.
- Inline SVG icons, if any are added: `aria-hidden="true" focusable="false"`, always next to visible
  text.
- A headshot, if Brett adds one (`[BRETT: headshot (yes/no; file)?]`): `alt="Brett Adams"`.
- Demo canvases need an `aria-label` that says what is drawn and how to use it, as neuronet-arena's
  does (`neuronet-arena/index.html:44`). Any chart also needs a data-table alternative, as
  neuronet-arena's "Data table" and border-waits' planned `<details>` have.
- Social images aren't rendered on the page. §12 covers their text.

### 10.9 Link text

1. Visible text names what the link opens: "Live demo", "Case study", "Source", "npm", "Live app",
   "Résumé (PDF)", "Email", "LinkedIn", "GitHub", "More on GitHub", "← Brett Adams".
   - Never "here", "read more", "click here", a bare URL or a bare icon.
   - The research has both failures: florea's résumé link reads "here" (`[florea:record.home.desktop.axe.violations]`),
     and 11 sites carry the `unlabeled-icons` tag (A13).
2. "Live demo" only for a page on this site that runs the project's own code (§8.0, A12).
3. The résumé label says it is a PDF wherever space allows ("Résumé (PDF)"). The header keeps
   "Résumé"; it is the same destination and shares the word (SC 3.2.4).
4. Repeated labels on one page get a visually hidden suffix that names the project:
   `Source<span class="sr-only">: TechNest</span>`.
   - The visible word comes first, so the accessible name contains the visible label (SC 2.5.3).
   - The preceding `h3` already satisfies SC 2.4.4. The suffix is for screen-reader links lists,
     which show links without their headings.
5. Arrows in links are `aria-hidden` spans after a `&nbsp;`, and the brand's `> ` is an
   `aria-hidden` span (§8.0). The names are "More on GitHub", "Brett Adams" and "brett adams", not "right arrow" or
   "left arrow".
6. Same tab, always. If a new-tab link is ever added, its visible text ends "(opens in a new tab)".
7. `mailto:` links show the address (contact section) or the word "Email".

### 10.10 Colour independence (SC 1.4.1)

| Indicator | Colour cue | Non-colour cue that carries the meaning |
|---|---|---|
| Live demo badge | green dot and green text | the words "Live demo" (§8.12) |
| Status pill | green dot | the text "Graduating 2027 · Open to full-time roles" (later "Graduating [BRETT: month] 2027 · …", §6.3) |
| Current job marker | filled amber dot vs hollow grey dots | "Present" in the date line; the role is listed first; filled vs hollow shape (§8.4) |
| Links in running text (lede, `/mcp/` body, contact email, demo "How it works") | amber | underline |
| Links in nav rows, link rows and buttons | amber or grey | position and shape: they sit in a row of links or inside a button outline |
| mcp-surface finding level | red / amber / grey | the words "error", "warn", "info" in `.lvl` (`mcp-surface/app.js:165`) |
| mcp-surface rule with hits | tinted border | the hit count in `.rule-hits` (`app.js:125`) |
| mcp-surface skipped rule | today opacity only, plus line-through | line-through plus the word "skipped" (§8.20 fix 5) |
| mcp-surface verdict | green or red box | "exit 0 CI passes" or "exit 1 CI fails" (`app.js:183-184`) |
| mcp-surface invalid JSON | red bar on the editor | `aria-invalid="true"` and the message "Not valid JSON at line …" (`app.js:196-201`) |
| neuronet-arena legend (upstream) | colours | text labels food, hazard, agent, leader (`neuronet-arena/index.html:50-55`) |

Idea I11 (prose coloured to match a demo's objects) is allowed only with one of these text cues.

### 10.11 Terminal prefixes stay out of accessible names

Each prefix becomes real text in a `<span class="prefix" aria-hidden="true">`, with
`white-space: pre` so its trailing space survives (`&nbsp;` before an arrow), and the `::before`
rules that inject it today are deleted (§7.10, §8.0). This is the mock's markup
(`handoff/mocks/first-screen.html:364`, `:392`, `:426`, `:430`, `:444`).
- `aria-hidden` works in every browser, so no support check is needed.
- Arrows inside link text become `aria-hidden` spans too.
- The `$` prompts inside terminal output and code blocks stay as text: they belong to the
  transcript (`mcp/index.html:45`, mcp-surface `.prompt`).

| Prefix | Today | Accessible name today | After |
|---|---|---|---|
| `> ` brand | `site.css:121-124` | includes "> " (same technique; not measured individually, audit issue 6) | "brett adams" (**measured** in the mock, §6.2) |
| `// ` kicker | `site.css:278-281` | includes "// " (not measured individually) | "data & software engineering" |
| `## ` section titles | `site.css:323-326` | "## Experience" (measured) | "Experience" |
| `@ ` employer | `site.css:394-397` | "Data Analyst Intern @ Geotab" (measured) | "Data Analyst Intern at Geotab" (a hidden "at "; **measured** in the mock, §6.2) |
| `## ` `/mcp/` h2s | `site.css:683-688` | includes "## " | "One check at the end of CI" |
| `> ` mcp-surface h1 | `mcp-surface/styles.css:46` | includes "> " | "mcp-surface" (the `::before` is deleted; the h1 gets the span) |
| `← ` back links | `mcp-surface/styles.css:38` (deleted; the shell's link carries an `aria-hidden` arrow); `&larr;` text at `mcp/index.html:10` (wrapped in an `aria-hidden` span) | "← brettadams0.github.io", "← Brett Adams" | "Brett Adams" |
| `→`, `↓` in link text | `index.html:156` (`&rarr;`) | "More on GitHub →" | "More on GitHub", "Projects and live demos", "Try mcp-surface's checks in your browser" |
| neuronet-arena equivalents | upstream `web/styles.css` | same technique, if present | fixed in the upstream PR |

axe does not test generated text. It passes the `// ` kicker in `--faint` (3.37:1) today: 0
violations on `/` with `site.css:280` in place. As spans, the prefixes are real text, so axe checks
them from PR 1 on. Their colours are `--amber` for `>`, `##` and `@`, and `--dim` for `//` (§8.0;
the mock's `.kicker .prefix`, `first-screen.html:165`). Never `--faint`.

**Check.** §10.15's heading-name test fails if a heading's accessible name starts with "##", ">"
or "//", or contains " @ ".

### 10.12 Scrollable regions

- Any element with `overflow: auto` or `overflow: scroll` that can actually scroll gets
  `tabindex="0"`, `role="region"` and a name unique on the page (§8.19). Today that means:
  - the `/mcp/` code block (`mcp/index.html:45`; audit issue 4);
  - mcp-surface's `.term` (`mcp-surface/index.html:69`; audit issue 3).
  - Later: any table scroller on a demo page.
- The mcp-surface textarea is natively focusable and needs nothing.
- The gutter (`#gutter`) is `aria-hidden`, so it needs nothing either.
- The page itself never scrolls sideways at any width from 320 to 1440 (§10.13).

### 10.13 Contrast, zoom, reflow, text spacing

**Text contrast.**
- At least 4.5:1 for body text and 3:1 for large text. §7 has the full pair table.
- `--faint` (3.37:1 on `--bg`, 2.85:1 on `--surface-2`, audit §1) is never used for words. `--dim`
  replaces it (audit issue 16; mcp-surface issue 3; neuronet-arena `td.pending`, upstream).
- Two cases axe can't see, settled here (**computed** 2026-10-22, WCAG 2.x formula on blended
  colours):
  - **Sticky header over scrolled content.**
    - At today's 0.82 alpha, with `--text` underneath, `--muted` nav text falls to 3.92:1 (4.41:1
      over `--amber`).
    - At 0.92 alpha it is 5.29:1 and 5.55:1.
    - §8.1 sets 0.92. The blur only lowers the contrast swing further, so these are worst cases.
  - **Hero gradients** (the 32 "incomplete" items at 390). The brightest point of the teal glow
    (7% over `--bg`, `site.css:227`) gives a background of rgb(16, 29, 32):
    - `--soft` is 10.63:1 on it and `--muted` 5.64:1, so both pass;
    - `--faint` would be 3.02:1, and is not used for text.
- Opacity is never used to show a state on text. mcp-surface's skipped (0.45) and call-only (0.6)
  rules fall to about 2.15:1 and 2.81:1 for `--muted` (computed). §8.20 fix 5 replaces them with
  full-opacity colour plus words.

**Non-text contrast (SC 1.4.11).**
- The focus ring is 9.22:1 or better (§10.6).
- Form-control edges on demo pages use `--muted` (5.32–6.06:1 on `--surface-2`, `--surface` and
  `--term`). Today they are 1.36–1.54:1 (§8.20 fix 6).
- Card and button borders under 3:1 are acceptable. Buttons are identified by their text and cards
  by their content (audit issue 17). §7 may still raise them.

**Zoom (SC 1.4.4).** The viewport meta stays `width=device-width, initial-scale=1`
(`_layouts/default.html:5`, `mcp-surface/index.html:5`), with no `maximum-scale` and no
`user-scalable=no`. Three sites in the set fail axe's critical `meta-viewport` (A15): pahasara,
rauno and shud `[pahasara:record.home.desktop.axe.violations]`.

**Reflow (SC 1.4.10).**
- No horizontal page scroll at 320 × 640 on any page.
- Code blocks and tables scroll inside themselves (§8.19).
- The ≤ 860 section-link row must fit at 320 without wrapping or overflowing (§8.1). eugeneyan's
  nav row is what laid that page out at 453 px `[eugeneyan:record.home.mobile.dom.vw]`.

**Text spacing (SC 1.4.12).**
- No fixed `height` on anything holding text that could wrap: `.btn` changes from `height: 42px`
  (`site.css:164`) to `min-height: 44px` (§8.11).
- The header row keeps a fixed `--header-h`, but it holds single-line items with 44 px boxes that
  stay inside it at line-height 1.5.
- mcp-surface's `.editor` and `.term` keep fixed heights; they scroll, so nothing is lost.

### 10.14 Dynamic content and live regions

- **Homepage:** none. The only script is analytics (`assets/js/analytics-events.js`), and
  `<details>` needs no script.
- **mcp-surface:**
  - `#parse` is already `role="status"` (`index.html:55`). `#verdict` gains `role="status"`.
  - `.term` loses `aria-live`, so a keystroke doesn't make a screen reader re-read the whole report
    (§8.19).
  - Toggle buttons use `aria-pressed`, and focus survives re-renders (§8.20 fixes 3–4).
  - The textarea keeps `aria-invalid` (`app.js:201`).
- **neuronet-arena** (upstream, unchanged):
  - the HUD is `aria-live="off"`, so it doesn't chatter (`index.html:45`);
  - the cross-engine check is `aria-live="polite"` (`index.html:114`);
  - it starts paused under reduced motion (`ui/app.js:60`).
- **Future demos:** status lines are `role="status"` (`handoff/border-waits-pipeline.md`). A heavy engine loads only
  on a click, and the button says what loads and roughly how much (I2): border-waits' "Load DuckDB
  to run SQL (about 7.7 MB)".
- Reduced motion for every component: §9.

### 10.15 How each PR checks this

1. `jekyll build && node .github/scripts/check-links.mjs`: CI green.
2. The axe pass bar in §10.1.
3. **New `--a11y` mode in `handoff/tools/measure.mjs`** (added in PR 0, so every later PR can
   compare). Today these fields came from one-off scripts (audit "Method: Extra checks"), not from
   `measure.mjs`.
   - For each path, at 390×844 and 1440×900, it writes the baseline JSON's field names in the
     baseline's formats, so later runs diff against `handoff/research/baseline/measure-*.json`:
     - `viewport.<size>.focus_walk`: presses Tab up to 120 times and records each stop as
       `"<name> [<outline-style> <outline-width> <outline-color>]"`, the baseline's format. The
       name comes from CDP `Accessibility.getFullAXTree` (the source of truth, as §7.10 and PR 4
       use), not from `ariaSnapshot()`.
     - `viewport.<size>.small_targets_24`: a list of `{el, text, w, h, inline, top, bottom,
       height}` for visible links, buttons, `summary`, `select`, `textarea` and `[tabindex="0"]`
       with `w < 24` or `h < 24`. `inline` is true for an `a` whose parent `p` or `li` holds other
       text; inline targets are listed but don't fail (SC 2.5.8's exception).
     - `viewport.<size>.targets_under_44` and `targets_total`: integers, as in the baseline.
     - `viewport.<size>.page_height` and `viewport.<size>.key_info.<item>` (PR 0, §16). The
       `resume` item uses `.site-header .nav-resume`. An item is `null` when its selector matches
       nothing, as on the demo pages.
     - `page.headings`: `"H<level> <name>"`, with the name from the CDP tree. The baseline's
       values are innerText (for example "H2 EXPERIENCE", "H3 Data Analyst Intern Geotab"), so
       they won't diff equal; compare them with §10.4 instead.
   - It also writes an `a11y` object:
     - `reflow_320`: at 320×640, `document.documentElement.scrollWidth <= innerWidth`;
     - `skip_link`: the §10.2 check;
     - `obscured`: only when `.site-header` computes `position: sticky` or `fixed`. For each focus
       stop that is not the skip link and not inside the sticky header, after the browser's own
       focus scroll, the stop is listed if `stop.getBoundingClientRect().top <
       header.getBoundingClientRect().bottom`. `.demo-shell` is not sticky (§8.20), so demo pages
       have no `obscured` entries.
   - Example output for `/` (values shortened; `"…"` marks cut lists):

     ```json
     {
       "path": "/",
       "page": {
         "headings": ["H1 Brett Adams", "H2 AT A GLANCE", "H2 EXPERIENCE", "H3 Data Analyst Intern at Geotab", "…"]
       },
       "viewport": {
         "390": {
           "page_height": 5210,
           "focus_walk": [
             "Skip to content [solid 2px rgb(255, 180, 84)]",
             "brett adams [solid 2px rgb(255, 180, 84)]",
             "Résumé [solid 2px rgb(255, 180, 84)]",
             "…"
           ],
           "small_targets_24": [],
           "targets_under_44": 3,
           "targets_total": 36,
           "key_info": {
             "projects_heading": { "el": "h2#projects-title.section-title", "text": "PROJECTS", "top": 2501, "bottom": 2521, "height": 20, "screens_to_reach": 2, "in_first_screen": false },
             "resume": { "el": "a.nav-resume", "text": "Résumé", "top": 10, "bottom": 46, "height": 36, "screens_to_reach": 0, "in_first_screen": true },
             "…": "…"
           }
         },
         "1440": { "…": "…" }
       },
       "a11y": {
         "reflow_320": { "scrollWidth": 320, "innerWidth": 320, "pass": true },
         "skip_link": { "first_tab": "Skip to content", "in_viewport": true, "after_enter": "main", "pass": true },
         "obscured": [],
         "failures": []
       }
     }
     ```

     The numbers are illustrative, not measured; the shape is the contract. Chrome reports the
     uppercased section titles ("EXPERIENCE") because of `text-transform`. That is CSS, not text.
   - It exits 1 when any of these hold:
     - a focus stop has no 2 px outline (allowed exceptions: `main`, and the mcp-surface
       textarea, whose `.editor` shows the box-shadow ring);
     - `small_targets_24` has a non-inline entry;
     - a heading name starts with "##", ">" or "//" or contains " @ ";
     - reflow fails;
     - the skip-link check fails;
     - `obscured` isn't empty.
   - It loads Playwright the same way, writes no `package.json` and installs nothing. §15 and §16
     must list it.
4. Compare `focus_walk` with §10.5 and `page.headings` with §10.4. The order and names must match,
   ignoring case (Chrome applies the section titles' `text-transform`).
   On `/`, the "N more" stops are whatever PR 5's fit loop left: one per role with a
   `details.job-more`, each named "<N> more from <employer>[, year]" and unique (§8.4).
5. Screen reader, manual, about 10 minutes (estimated), on a real device. It can't run in the
   container. With VoiceOver (iOS or macOS) or NVDA on `/`:
   - the headings list matches §10.4;
   - the links list shows no bare duplicate "Source" or "Live demo";
   - the skip link lands in `main`.
   Brett or the builder records the result in the PR description.

### 10.16 Every audit §5 issue, mapped to the component and PR that fixes it

"A11y" marks the issues that are accessibility problems, with the WCAG criterion. The other issues
are listed so nothing in audit §5 is left unassigned.

| # | Issue (audit §5) | A11y | Fixed in | PR |
|---|---|---|---|---|
| 1 | High: no section nav on phones (`site.css:859-862`) | Supports 2.4.1 / 2.4.5 (usability) | §8.1 `.nav-sections` | 3 Header and mobile nav |
| 2 | High: proof of work below the fold | No | §8.2 `.hero-jump` (> 860 px), §8.1 row 2 (≤ 860 px), §8.4 `.job-more` | 3, 4, 5 |
| 3 | High: `/mcp-surface/` 2 serious axe rules | **Yes**, 1.4.3, 2.1.1 | §8.20 fixes 1–2, §8.19 | 9 mcp-surface |
| 4 | Medium: `/mcp/` scrollable `pre` | **Yes**, 2.1.1 | §8.19, §8.18 | 8 `/mcp/` and 404 |
| 5 | Medium (upstream): CLS 0.1643 on neuronet-arena | No (performance) | §7 font fallback; §8.20 upstream table | 11 upstream |
| 6 | Medium: screen readers hear the terminal prefixes | **Yes**, 1.3.1, 2.4.6 | §8.0 (`aria-hidden` prefix spans, hidden "at "); §10.11 | 1 Tokens and prefixes (homepage, `/mcp/`); 9 (mcp-surface); 11 (upstream) |
| 7 | Medium: render-blocking font CSS | No | §7 (self-hosted fonts) | 2 Fonts |
| 8 | Medium: oversized screenshots, no `srcset` | No | §8.5–8.7 `srcset`/`sizes` | 6 Project cards |
| 9 | Medium: half-empty desktop first screen | No | §8.3 `div.glance` | 4 Hero |
| 10 | Medium: status line missing term, role family, location | No | §8.2–8.3 answered forms, §18 #30–#32 | 4 (sourced line); 15 (answers) |
| 11 | Medium: demo visits unmeasured | No | Decision (§18); §8.20 keeps `data-contact` ready | 9; 11 (upstream) |
| 12 | Medium: footer contact links untracked | No | §8.16 `data-contact` (Decision, default add) | 7 Skills, contact, footer |
| 13 | Medium: demo pages don't share the shell (no skip link, back link reads the domain, neuronet-arena focus ring) | **Yes**, 2.4.1, 2.4.7, 3.2.3, 3.2.6 | §8.20 shell; §10.2; §10.6 | 9; 11 (upstream) |
| 14 | Medium: Experience bullets read as duties | No | §8.4 `[BRETT]` outcome lines | 5 Experience |
| 15 | Low: small tap targets at 390 | **Yes**, 2.5.8 (passes today by spacing; 44 px goal) | §8.1, §8.6, §8.9, §8.16, §8.18, §8.20 fix 7; §10.7 | 3, 6, 7, 8, 9 |
| 16 | Low: `--faint` text under 4.5:1 | **Yes**, 1.4.3 | §7 `--dim`; §8.0; §8.20 fix 1; upstream `td.pending` | 1; 9; 11 |
| 17 | Low: UI borders under 3:1 | Partly, 1.4.11 (form controls on demos) | §8.20 fix 6; §7 for card and button borders | 9; 1 |
| 18 | Low: GA dominates JS weight | No | §11, §18 | — |
| 19 | Low: heavy social cards | No | §12 | 10 SEO and sharing |
| 20 | Low: demo titles don't name Brett | **Yes**, 2.4.2 (quality, not a failure) | §12 titles; §8.20 fix 8 | 9; 11 (upstream) |
| 21 | Low: 404 is a dead end | No | §8.17 | 8 |
| 22 | Low: thin JSON-LD | No | §12 | 10 |
| 23 | Low: live badge inside the heading | **Yes**, 2.4.6 (heading names) | §8.8 `.project-kind`, §8.12 | 6 |
| 24 | Low: MCP card's first click goes off-site | No (consistency) | §8.6 screenshot rule; §10.8 | 6 |
| 25 | Low: hero buttons wrap on phones | No | §8.2 button grid | 4 |
| 26 | Low: cryptic result chip | No (copy) | §8.5 chip "13.7× the food with selection on" | 6 |
| 27 | Low (upstream): empty table header | **Yes**, 1.3.1 (axe minor) | §8.20 upstream table | 11 upstream |
| 28 | Low: mcp-surface has the slowest LCP | No | §11 | 9 |

**Found while writing this spec, not in audit §5** (each with its evidence and fix):

| # | Issue | Evidence | A11y | Fixed in | PR |
|---|---|---|---|---|---|
| N1 | Examples control uses `role="radio"` without arrow-key handling; three Tab stops | `mcp-surface/index.html:44-48`; no `keydown` in `app.js` (grep); `[brettadams0:measure-mcp-surface.json.viewport.1440.focus_walk]` | **Yes**, 4.1.2, 2.1.1 | §8.20 fix 3 | 9 |
| N2 | Pressing a rule button drops focus to `body` (buttons rebuilt) | `mcp-surface/app.js:112-134` | **Yes**, 2.4.3 | §8.20 fix 4 | 9 |
| N3 | Skipped and call-only rules shown by opacity (about 2.15:1 and 2.81:1) | `mcp-surface/styles.css:214`, `:229`; computed | **Yes**, 1.4.3 | §8.20 fix 5 | 9 |
| N4 | `.term` is `aria-live` and re-renders the whole report on every keystroke | `mcp-surface/index.html:69`; `app.js:136-186` | **Yes** (usability for screen-reader users) | §8.19 | 9 |
| N5 | Header translucency lets `--muted` nav text fall to 3.92:1 over bright content | `site.css:101`; computed | **Yes**, 1.4.3 (worst case) | §8.1 | 1 or 3 |
| N6 | mcp-surface textarea focus outline clipped; form-control edges 1.36–1.54:1 | `mcp-surface/styles.css:85-92`, `:110`, `:127` | **Yes**, 2.4.7, 1.4.11 | §8.20 fix 6 | 9 |
| N7 | The `// ` kicker prefix is `--faint` (3.37:1). It passes axe only because axe skips generated text | `site.css:280`; audit §1 (0 violations on `/`) | **Yes**, 1.4.3 (read by sighted users) | §8.0 (`.kicker .prefix` uses `--dim`) | 1 |
| N8 | `.btn` uses a fixed `height`, which can clip under text-spacing overrides | `site.css:164` | **Yes**, 1.4.12 | §8.11 | 1 or 4 |
| N9 | mcp-surface `<noscript>` text sits outside every landmark | `mcp-surface/index.html:90` | Minor (axe `region` when shown) | §8.20 fix 9 | 9 |

## 11. Performance budget

The site is already light, and the budget keeps it that way. The homepage ships 2.5 KB of its own
JS, no framework and nothing live (§2.6). The redesign adds markup and CSS, and takes weight out
in two places:
- the screenshots get `srcset` (§7.9);
- the fonts move to this origin (§7.5; §18 #3, default self-host).

The budget is a committed file, `handoff/tools/budget.json`, that `handoff/tools/measure.mjs`
checks. Every PR in §16 runs it, and it exits non-zero when a page goes over.

### 11.1 How it is measured

Everything here uses the method in §2.1, so before and after are comparable.

- **Tool:** `node handoff/tools/measure.mjs --site _site … --budget handoff/tools/budget.json --runs 3`.
  It serves the local `jekyll build` gzipped on a localhost port, with no proxy in the path.
- **Profile the budget reads:** the throttled mobile run only.
  - 390×844, DPR 2, 150 ms latency, 1.6 Mbps down, 750 kbps up, CPU 4×.
  - The run with the median LCP of three is used (`handoff/tools/measure.mjs:342-350`).
  - Desktop CLS is read too, through the `desktop_cls` key that PR 0 adds (11.7).
- **Sizes:** CDP `encodedDataLength`, so headers are included. 1 KB = 1,024 B.
  - JS excludes Google's hosts (`GA_HOSTS`, `measure.mjs:79`).
  - "Images" and "total" are counted after a slow scroll to the bottom, so every lazy screenshot is
    in them.
- **Comparison:** each limit is a strict upper bound. A page passes a key only when the measured
  value is below the limit (`actual < limit`, `measure.mjs:448`).
- **Paths:** a rule applies only to a page passed with `--path`. The file has no `"*"` rule, so a new
  page gets no budget until its PR adds a line.
- **GA is blocked** in every run (`measure.mjs:20-23`). Real visitors also download gtag.js,
  180,868 B (**measured** with `curl`, 2026-10-21, §2.1). That is outside the budget by definition
  and is not changed (audit issue 18).
- **Live vs local:** live runs go through the session proxy and are slower. The budget is set for the
  local build. Live numbers are reported for information and never gate a PR.

### 11.2 Homepage `/`: baseline and target

Throttled mobile. Baselines are **measured** on 2026-10-21
(`[brettadams0:measure-home.json.perf.mobile]` and `.perf_local_build.mobile`). Targets are
**estimated** from the measured file sizes named in the last column.

| Metric | Live (measured) | Local (measured) | Target after PR 6 (estimated) | Budget limit | How the target is reached |
|---|---|---|---|---|---|
| Total transfer, full scroll | 389.6 KB (398,940 B) | 385.2 KB (394,464 B) | **about 190 KB** | < 240 KB | images −200 KB (below); the rest roughly flat |
| JS, excluding GA | 2.5 KB (2,590 B) | 2.0 KB (2,036 B) | 2.5 KB | < 15 KB | unchanged: `analytics-events.js` is the only script (4,420 B raw, 1,826 B gzip, measured with `gzip -9`) |
| CSS | 6.1 KB (6,247 B, 2 files) | — | about 7 KB, 1 file | < 12 KB | `site.css` (3,714 B gzip today, measured) plus the token block at its top, which adds about 5.4 KB raw (measured as a separate file: 5,415 B, 1,788 B gzipped, §7.1), plus the new components; Google's CSS (930 B gzip) goes. Under §18 #49 (a) the same bytes are split over 2 files |
| Fonts | 71.8 KB (73,571 B, 4 files) | — | about 72 KB, 4 files | < 80 KB | the same four latin files, 70,472 B (measured, §7.5), now same-origin |
| Images | 304.0 KB (311,341 B, 5 files) | — | **about 104 KB**, 5 files | < 130 KB | every card picks its `-800.webp` at 390 DPR 2 and 1440 DPR 1: 105,646 B for the five (measured on the encoded files, §7.9) |
| Requests | 13 | 13 | 12 | < 16 | document, `site.css`, 4 fonts, 1 script, 5 images (13 under §18 #49 (a), with `tokens.css`) |
| Before any scroll | 85.5 KB, 8 requests | — | about 88 KB, 7 requests | — (not a key) | images are lazy and below the fold at both sizes (§7.9) |
| LCP | 744 ms (`p.lede`; runs 696, 756, 744) | 604 ms (runs 644, 604, 588) | under 1,000 ms, a text element | < 1,500 ms | no third-party render-blocking CSS; Sans preloaded; the LCP element is never faded in (§9.3) |
| CLS, mobile | 0.0011 | 0.0011 | under 0.01 | < 0.05 | metric-matched fallback faces (§7.5); 16:10 boxes with `width`/`height` (§7.9) |
| CLS, desktop | 0.0040 | 0.0034 | under 0.01 | < 0.05 (`desktop_cls`) | same |
| Framework | none | none | none | `no_framework: true` | plain HTML, CSS and one deferred script |

- **Desktop fonts drop too.** Desktop visitors download the hinted files from Google today, 91.8 KB
  (94,039 B, measured). After PR 2 every visitor gets the same 70,472 B (measured, §7.5).
- **The page gets taller in one place and shorter in another.** The hero gains the At-a-glance card,
  and the 16:10 boxes are taller than today's 16:9 ones. Experience shrinks (§4.5, §8.4). Neither
  changes the byte budget. §4.3 owns the height limits.

### 11.3 `/mcp/`: baseline and target

Throttled mobile. Baselines are **measured** on 2026-10-21 (`[brettadams0:measure-mcp.json.perf.mobile]`,
`.perf_local_build.mobile`). Targets are **estimated**.

| Metric | Live (measured) | Local (measured) | Target (estimated) | Budget limit | How |
|---|---|---|---|---|---|
| Total transfer | 86.0 KB (88,070 B) | 84.4 KB (86,380 B) | about 88 KB | < 110 KB | the page has no images, so fonts dominate (72 of 88 KB) |
| JS, excluding GA | 2.5 KB | 2.0 KB | 2.5 KB | < 15 KB | unchanged |
| CSS | 6.1 KB (2 files) | — | about 7 KB (1 file) | < 12 KB | as `/` |
| Fonts | 71.8 KB (4 files) | — | about 72 KB (4 files) | < 80 KB | as `/` |
| Images | 0 | 0 | 0 | < 10 KB | the `.flow` diagram is HTML and CSS (§8.18), not an image |
| Requests | 8 | 8 | 7 | < 10 | document, `site.css`, 4 fonts, 1 script (8 under §18 #49 (a)) |
| LCP | 844 ms (first body `p`; runs 844, 780, 864) | 596 ms | under 800 ms, a text element | < 1,500 ms | FCP = LCP today, held back by Google's CSS (audit issue 7); that hop goes |
| CLS, mobile | 0.0234 | 0.0205 | under 0.01 | < 0.05 | metric-matched fallback |
| CLS, desktop | **0.0431** | 0.0423 | under 0.01 | < 0.05 (`desktop_cls`) | the largest shift is `nav.site-nav` at 643 ms (`[brettadams0:measure-mcp.json.perf.desktop.shifts]`), which fits a font swap in the header; the fallback faces remove it (inferred, re-measured in PR 2) |

### 11.4 The other pages

Throttled mobile, local build (**measured** 2026-10-21, §2.3). Limits are set from today's values
with headroom, and tightened where a PR fixes the cause.

| Page | Total / JS / requests today | LCP today | CLS today (mobile / desktop) | Limits in `budget.json` | Notes |
|---|---|---|---|---|---|
| `/404.html` | 70.5 KB / 2.0 KB / 7 | 504 ms | 0.0001 / 0.0001 | total < 100, JS < 15, requests < 10, LCP < 1,500, CLS < 0.05 | gains two buttons (§8.17); no weight change |
| `/mcp-surface/` | 122.8 KB / 45.0 KB / 10 | 1,156 ms (live 1,752 ms) | 0.0012 / 0.0010 | total < 150, **JS < 55**, requests < 14, **LCP < 2,000**, CLS < 0.05 | `checks.js` is 41.4 KB of the JS and is generated (§0); see 11.5 for the LCP |
| `/neuronet-arena/` | 104.2 KB / 21.6 KB / 19 | 508 ms (live 692 ms) | **0.1643** / 0.0198 | total < 140, JS < 35, requests < 22, LCP < 1,500, **CLS < 0.2 until PR 11b, then < 0.05** | synced; the CLS fix is upstream (audit issue 5, §7.11) |
| `/border-waits/` (PR 12) | — | — | — | start: total < 250, JS < 40, requests < 20, LCP < 2,000, CLS < 0.05 (**estimated**) | measured before any click, so the 7.7 MB DuckDB-Wasm load (§14.6) is never in the budget file. PR 12 lowers each limit to 1.25 × its first measured value where that is lower. The click path gets its own check in PR 12, outside `budget.json`: after one scripted click, under 8.5 MB more transfer, at most 8 more requests, and origins exactly the site, `cdn.jsdelivr.net` and `extensions.duckdb.org`. On the throttled profile (1.6 Mbps) the 7,682,098 B load takes about 38 s (**estimated**: 7,682,098 × 8 / 1.6 M), so the button shows its size and load progress (§14.6) |
| `/mazefinder/` (PR 13) | — | — | — | start: total < 450, JS < 120, requests < 25, LCP < 2,000, CLS < 0.05 (**estimated**) | a `.wasm` fetch counts as "other", not JS, in `measure.mjs`. Same 1.25 × rule |

### 11.5 Changes to the starting budget, and why

The brief's starting point was: homepage JS under 15 KB compressed excluding GA, LCP under 2.0 s,
CLS under 0.05, no framework, nothing live on the homepage.

| Starting rule | In this budget | Reason |
|---|---|---|
| JS < 15 KB on `/` | **kept**, and applied to `/mcp/` and `/404.html` | 2.5 KB today; the redesign adds no script |
| LCP < 2.0 s | **tightened to 1.5 s** on `/`, `/mcp/`, `/404.html` and `/neuronet-arena/`; **kept at 2.0 s** on `/mcp-surface/` and future demos | Today's local medians are 504–604 ms. A 2.0 s line would let LCP more than triple before failing. 1.5 s still leaves 2.5× headroom for run-to-run spread, which was up to 60% between runs (desktop `/`: 296–476 ms, measured). mcp-surface paints its report only after `checks.js` arrives (1,752 ms live, audit issue 28), so it keeps 2.0 s |
| CLS < 0.05 | **kept**, on every page, and **at both sizes** through `desktop_cls` | §9.4 requires both sizes. `/mcp/` desktop is 0.0431 today, the closest to the line |
| No framework | **kept** (`no_framework: true` on every page) | `measure.mjs` detects React, Vue, Angular, Next, Nuxt, jQuery and Svelte bundles (`:275-278`) |
| Nothing live on the homepage | **kept**, checked outside the budget file (11.6) | the budget keys can't express it |
| — | **added:** `total_kb`, `css_kb`, `font_kb`, `image_kb`, `requests` per page | the two weight fixes (fonts, images) need a guard so they can't regress. `font_kb < 80` also catches a font fetched twice, which happens when the preload's `crossorigin` is missing (§7.5): a fifth font request pushes the total to about 112 KB (estimated from the 40,240 B Sans file) |
| — | **ratchets:** `/` `total_kb` 420 → 240 and `image_kb` 330 → 130 in PR 6; `/neuronet-arena/` `cls` 0.2 → 0.05 in PR 11b | the guard can't be at the target before the PR that reaches it, or PRs 0–5 would fail |

**mcp-surface LCP.** The report waits for a two-step module chain: `app.js` imports `checks.js` and
`examples.js` (`mcp-surface/app.js:5-6`). PR 9 adds
`<link rel="modulepreload" href="checks.js">` and `<link rel="modulepreload" href="examples.js">`
so the browser fetches both with `app.js`.
- Expected: about one round trip earlier, 150–300 ms on the throttled profile (**estimated**).
- PR 9 keeps the two tags only if the three-run median LCP is no worse than without them.

### 11.6 Rules the budget file can't express

Each PR that touches the homepage checks these from the `--json` report or with one `page.evaluate`.

| Rule | Check | Pass |
|---|---|---|
| Nothing live on `/` | `perf.mobile.by_type.script.count` for `/` | `1` (only `analytics-events.js`; gtag.js is blocked, so it counts as failed, not as a script) |
| | `document.querySelectorAll('canvas, video, iframe, object, embed, script[type="module"]').length` on `/` | `0` |
| | `document.getAnimations().length` after load (§9.5) | `0` |
| Only this origin on `/` | `perf.mobile.origins` (new field, PR 0: the distinct origins of successful requests) | `["http://127.0.0.1:<port>"]` locally, `["https://brettadams0.github.io"]` live. GA is blocked, so its request fails and isn't listed; real visitors also reach Google's GA hosts |
| Only same-origin CSS blocks render | `perf.mobile.render_blocking` | `site.css` only, after PR 2 (`tokens.css` and `site.css` under §18 #49 (a)) |
| Nothing failed to load | `perf.mobile.failed_requests` | equal to `perf.mobile.ga_requests_blocked` (the blocked GA request is the only failure) |
| LCP is text | `perf.mobile.lcp.url` | `null`: never an image (the card images are lazy and below the fold) |
| `srcset` files exist | the one-liner in §16 PR 6 | every URL found in `_site` (`check-links.mjs` doesn't read `srcset`) |

### 11.7 `handoff/tools/budget.json`

**Final file**, as it stands after PR 11b. Commit it exactly like this, in the order shown. JSON has
no comments, so the reasons live in 11.5.

```json
{
  "/":                { "total_kb": 240, "js_kb": 15, "css_kb": 12, "font_kb": 80, "image_kb": 130, "requests": 16, "lcp_ms": 1500, "cls": 0.05, "desktop_cls": 0.05, "no_framework": true },
  "/mcp/":            { "total_kb": 110, "js_kb": 15, "css_kb": 12, "font_kb": 80, "image_kb": 10,  "requests": 10, "lcp_ms": 1500, "cls": 0.05, "desktop_cls": 0.05, "no_framework": true },
  "/404.html":        { "total_kb": 100, "js_kb": 15, "css_kb": 12, "font_kb": 80, "image_kb": 10,  "requests": 10, "lcp_ms": 1500, "cls": 0.05, "desktop_cls": 0.05, "no_framework": true },
  "/mcp-surface/":    { "total_kb": 150, "js_kb": 55, "css_kb": 12, "font_kb": 80, "image_kb": 10,  "requests": 14, "lcp_ms": 2000, "cls": 0.05, "desktop_cls": 0.05, "no_framework": true },
  "/neuronet-arena/": { "total_kb": 140, "js_kb": 35, "css_kb": 10, "font_kb": 80, "image_kb": 10,  "requests": 22, "lcp_ms": 1500, "cls": 0.05, "desktop_cls": 0.05, "no_framework": true }
}
```

**The version PR 0 commits** differs in three values only, so it passes on today's site:

| Path | Key | PR 0 value | Today, local (measured) | Tightened to | In |
|---|---|---|---|---|---|
| `/` | `total_kb` | 420 | 385.2 | 240 | PR 6 |
| `/` | `image_kb` | 330 | 304.0 (live; local images are not gzipped either) | 130 | PR 6 |
| `/neuronet-arena/` | `cls` | 0.2 | 0.1643 | 0.05 | PR 11b |

Later edits:
- PR 12 adds the `/border-waits/` line (11.4). It raises `/` `image_kb` and `total_kb` by the new
  card's measured `-800.webp` size, rounded up to the next 10 KB.
- PR 13 does the same for `/mazefinder/` and its card.
- If Brett keeps the hinted desktop font files after the Windows check (§7.5), the PR that swaps
  them raises `font_kb` to 100 on every line (90,928 B plus headers).

**`desktop_cls` needs PR 0.** Today `checkBudget` only reads the mobile run (`measure.mjs:434-454`),
and an unknown key fails the run (`:447`). PR 0 changes three lines:

```js
// measure.mjs, checkBudget(): metrics take the desktop run as a second argument
const metric = { total_kb: (m) => m.transfer_bytes / KB, js_kb: (m) => m.js_bytes_excl_ga / KB, css_kb: (m) => m.by_type.stylesheet.bytes / KB,
  font_kb: (m) => m.by_type.font.bytes / KB, image_kb: (m) => m.by_type.image.bytes / KB, requests: (m) => m.requests,
  lcp_ms: (m) => m.lcp?.ms ?? Infinity, cls: (m) => m.cls, desktop_cls: (m, d) => d?.cls ?? Infinity };
// …inside the loop over rules:
const d = p.perf?.desktop;
const actual = k === 'no_framework' ? m.framework : metric[k] ? +metric[k](m, d).toFixed(3) : undefined;
```

It also adds `"desktop_cls"` to the key list in the usage comment (`:45-46`).

### 11.8 How to run it

```sh
# from the repository root
jekyll build
node handoff/tools/measure.mjs --site _site \
  --path / --path /mcp/ --path /404.html --path /mcp-surface/ --path /neuronet-arena/ \
  --budget handoff/tools/budget.json --runs 3 --json /tmp/budget.json
```

- **Time:** about 5–8 minutes for five pages × two profiles × three runs (**estimated** from the
  baseline run, which also took screenshots and ran axe).
- **Output:** one line per profile per page, then `budget (handoff/tools/budget.json): PASS`, or
  `FAIL` followed by one `over: <path>: <key> <actual> (limit <limit>)` line per failure. The exit
  code is 1 on any failure.
- **On a failure:**
  - Re-run once with `--runs 5`. Timing keys (`lcp_ms`, `cls`) can move between machines.
  - A size key that fails twice is a real regression. Find the file in `perf.mobile.largest` in the
    JSON.
  - Never raise a limit in the same PR that breaks it unless the PR's goal is the extra weight (a new
    card image). Say so in the PR description.
- **Record in each PR:** the Chromium version (`chromium` in the JSON) and the five pages' mobile
  lines. A Chromium upgrade can move LCP, so a changed version is noted next to any timing change.
- **Live site, for information only:**
  `node handoff/tools/measure.mjs https://brettadams0.github.io/ --path / --path /mcp/ --budget handoff/tools/budget.json --runs 3`.
  GA stays blocked. Never add `--allow-ga` on the live site (§2.1's one test hit).
- **CI does not run this.** Running the size keys in CI is a Decision (§18 #28). The recommendation
  is yes; the default is no, because a CI change needs Brett's sign-off.

### 11.9 Writer's notes

- **Writer's note:** the brief's LCP line of 2.0 s is tightened to 1.5 s on four pages (11.5). The
  2.0 s line stays as the hard ceiling for mcp-surface and future demos.
- **Writer's note:** `desktop_cls` and the `origins` field are additions to `measure.mjs`. They go in
  PR 0 with §10.15's `--a11y` mode, before any site change, so that every later PR is measured the
  same way.
- **Writer's note:** the CSS and request counts assume §18 #49's default: the tokens stay at the top
  of `site.css`, so the redesign adds no CSS file to the site's pages. The limits hold either way.

## 12. SEO and sharing

Today's metadata is mostly right: distinct, accurate titles and descriptions on `/` and `/mcp/`, a
sitemap, a robots file and a social card (audit §5 "Checked and fine"). This section fixes the gaps
the audit found:
- the demo titles don't name Brett (issue 20);
- the 404 page reuses the site description (issue 21);
- the JSON-LD says `alumniOf` for a current student (issue 22, fact sheet D19);
- the social images are heavier than they need to be (issue 19).

It adds no plugin. `jekyll-seo-tag` keeps writing the title, description, canonical, Open Graph and
Twitter tags on default-layout pages (`_layouts/default.html:10`). The two demos write their own,
since they have no front matter.

### 12.1 Titles, descriptions and social images, per page

Today's values are **measured** from the local build of 2026-10-21 (`_site/*.html`) and the
baseline reports (`[brettadams0:measure-<page>.json.title]`, `.description`). Each "after" value
names its source.

| Page | Title today | Title after | Description today | Description after | Social image | Set in |
|---|---|---|---|---|---|---|
| `/` | `Brett Adams \| Data & Software Engineering` (seo-tag from `_config.yml:1-2`) | **unchanged** | "Data Analyst Intern at Geotab and Computer Science student at the University of Windsor. Experience, projects and résumé." (`_config.yml:3-5`) | "Data Analyst Intern at Geotab and Computer Science student at the University of Windsor, graduating 2027. Experience, live project demos and résumé." [src: _config.yml:3-5; index.html:7; index.html:89, :116] (148 characters) | `social-card.png`, re-encoded (12.2) | `_config.yml:3-5`, PR 10 |
| `/mcp/` | `MCP servers, and the tool that checks them \| Brett Adams` | **unchanged** | "Model Context Protocol servers for Google Workspace and Reddit, with 35 tools between them, and mcp-surface, the command-line tool both run in CI." (`mcp/index.html:3-5`) | **unchanged** (it already uses the D1 framing of the page itself) | `social-card.png` | — |
| `/404.html` | `Page not found \| Brett Adams` | **unchanged** | the site description (`_config.yml:3-5`) | "Page not found on brettadams0.github.io. The homepage, the projects and the résumé are linked from here." [src: 404.html:9; §8.17's three buttons] | `social-card.png` | `404.html` front matter, PR 8 |
| `/mcp-surface/` | `mcp-surface · playground` (`mcp-surface/index.html:6`) | `mcp-surface · playground \| Brett Adams` | "The checks from the mcp-surface npm package, running in your browser against a tools/list response you can edit." (`:7`) | **unchanged** | `mcp-surface/og.png`, re-encoded (12.2) | `mcp-surface/index.html:6`, `:8`, PR 9 |
| `/neuronet-arena/` | `neuronet-arena · live demo` (`neuronet-arena/index.html:6`) | `neuronet-arena · live demo \| Brett Adams` | the page's own (`:7`) | **unchanged** | `og.png`, re-encoded upstream | upstream `web/index.html`, PR 11a |
| `/mazefinder/` (PR 13) | — (the handoff proposes `MazeFinder · live demo`, `handoff/mazefinder-live-demo.md:127`) | `MazeFinder · live demo \| Brett Adams` | — | per the handoff, plain and factual; never "solves mazes" before walls exist (fact sheet C2) | its own `og.png` | PR 13 |
| `/border-waits/` (PR 12) | — | `border-waits · live demo \| Brett Adams` (the brief's own example, `handoff/border-waits-pipeline.md`, "Head and shell") | — | "Hourly border wait-time estimates from the Canadian and U.S. border agencies, collected and tested automatically, queryable with SQL in your browser." (the brief's example; the agencies are spelled out, since a share preview can't expand CBSA or CBP) | its own `og.png` | PR 12 |

**The demo title rule.** `<project> · <kind> | Brett Adams`. It keeps today's demo strings and adds
the suffix that jekyll-seo-tag already puts on `/mcp/` and `/404.html`. That way a tab, a share and
a search result all say whose work it is (audit issue 20).
- Each demo's `og:title` is the same string as its `<title>`.
- `og:url` stays absolute, as today (`mcp-surface/index.html:11`).
- Each demo gains `<link rel="canonical" href="https://brettadams0.github.io/<folder>/">`. Neither
  has one today (`[brettadams0:measure-mcp-surface.json.page.canonical]` is `null`).

**What stays as it is, and why:**
- **The homepage title.** It is sourced, it doesn't go stale, and it starts with the name a
  recruiter searches for. If Brett answers the target-roles question, `tagline` (`_config.yml:2`)
  can change in PR 15, and the title follows.
- **The `/mcp/` title and description.** They match the page and the D1 framing it already uses
  (2 servers, 35 tools).
- **`twitter:card` `summary_large_image`** on every page.
- **The 404 canonical.** jekyll-seo-tag writes `https://brettadams0.github.io/404.html`
  (`[brettadams0:measure-404.json.page.canonical]`). Pages serves that file with a real 404 status
  (measured on `/this-page-does-not-exist/`, audit issue 21), so search engines don't index it. No
  `noindex` tag is needed, and jekyll-seo-tag has no option to drop the canonical anyway.

**Copy rules for any future description.**
- At most 160 characters.
- Plain and factual, with no claim the page can't back.
- Nothing that goes stale silently. The homepage's "Data Analyst Intern at Geotab" is one such line:
  it must change when `[BRETT: Geotab internship end date]` passes (§18 #30).

### 12.2 Social card: re-encode now, regenerate only when its text stops being true

`assets/img/social-card.png` is 1200×630 and 211,878 B (**measured**). It is the default
`og:image` for every default-layout page (`_config.yml:20-25`).
- **Its text is all sourced and still true:** "// data & software engineering", "Brett Adams",
  "Data Analyst Intern at Geotab. Computer Science, University of Windsor.", "> brettadams0.github.io"
  [src: index.html:8-10; _config.yml:6] (fact sheet §2, viewed this session).
- **Its weight isn't on any page's critical path.** Only link-preview crawlers fetch it. So it is low
  priority (audit issue 19, Low).

**Decision in this spec: don't redesign it, re-encode it.** Measured 2026-10-22 with PIL 12.3.0 on
copies outside the repo:

| Encoding | Bytes | Mean / max per-channel difference from today's file | Use? |
|---|---|---|---|
| Today's PNG | 211,878 | — | — |
| PNG, lossless re-save (`optimize=True`) | 179,699 | 0 / 0 | fallback if the palette version shows banding |
| **PNG, 256-colour palette (median cut, no dither), `optimize=True`** | **84,198** | 0.13 / 49 | **yes**: no visible difference when viewed side by side |
| PNG, 128 colours | 72,489 | 0.42 / 91 | no: more change for 12 KB |
| JPEG q85 | 45,293 | 1.09 / 79 | no: crawlers handle PNG everywhere, and JPEG softens the small Mono text |

The same palette re-encode, also measured:
- `mcp-surface/og.png`: 109,961 → 39,783 B (mean difference 0.05). This is this repo's file, so it
  goes in PR 10.
- `neuronet-arena/og.png`: 144,842 → 78,033 B (mean 0.10). That file is synced, so the change goes
  in the upstream PR 11a.

```python
# PR 10. Run from the repo root with python3 -I. Needs Pillow; the measured run used PIL 12.3.0.
from PIL import Image
for path in ("assets/img/social-card.png", "mcp-surface/og.png"):
    im = Image.open(path).convert("RGB")
    im.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(path, "PNG", optimize=True)
```

**When to regenerate the card,** rather than re-encode it: only when a line on it stops being true.
That happens when the Geotab internship ends (`[BRETT: Geotab internship end date]`), or if Brett
changes the kicker or tagline after the target-roles answer. Then:
- rebuild it at 1200×630 from an HTML page that links `assets/css/site.css` (for its token block and
  font faces; `tokens.css` under §18 #49 (a)), with the same layout and copy taken from the
  then-current hero;
- screenshot it with Playwright and re-encode it as above;
- do this in the PR that changes the hero copy (PR 15).

The card's faint grid matches the hero overlay that §7.2 removes. That alone is no reason to rebuild
the card.

### 12.3 JSON-LD

**Keep the `ProfilePage` / `Person` block. Change three fields.** It is already there, costs 545 bytes
on the homepage only (measured in `_site/index.html`), and tells search engines which profiles are Brett's. The changes fix
one wrong field and add two facts the page already shows.

Today (`_layouts/default.html:11-30`, emitted only when `page.url == "/"`):
- `"@type": "ProfilePage"` with a `Person` as `mainEntity`;
- the Person has `name`, `jobTitle` "Data Analyst Intern", `worksFor` Geotab, `alumniOf` University
  of Windsor, `url`, and `sameAs` GitHub and LinkedIn.
- jekyll-seo-tag adds a separate `WebSite` block with `author` Brett Adams (measured in `_site/index.html`).

After (PR 10). This replaces lines 12-29:

```html
<script type="application/ld+json">
{
  "@context": "https://schema.org",
  "@type": "ProfilePage",
  "mainEntity": {
    "@type": "Person",
    "name": "Brett Adams",
    "jobTitle": "Data Analyst Intern",
    "worksFor": { "@type": "Organization", "name": "Geotab", "url": "https://www.geotab.com" },
    "affiliation": { "@type": "CollegeOrUniversity", "name": "University of Windsor" },
    "knowsAbout": ["Apache Airflow", "BigQuery", "SQL", "Python", "Power BI", "GitLab CI/CD"],
    "email": "mailto:adamsbrett00@gmail.com",
    "url": "{{ site.url }}/",
    "sameAs": [
      "https://github.com/brettadams0",
      "https://www.linkedin.com/in/bretta/"
    ]
  }
}
</script>
```

| Field | Change | Source | Why |
|---|---|---|---|
| `alumniOf` → `affiliation` | renamed | [src: index.html:175-177; index.html:7] | schema.org's `alumniOf` means a former member. Brett graduates in 2027 (fact sheet D19). `affiliation` covers a current student. **§18 #19**: default `affiliation`; switch back to `alumniOf` when Brett graduates |
| `worksFor.url` | added | [src: index.html:10] (the lede links Geotab to `https://www.geotab.com`) | names the employer without ambiguity |
| `knowsAbout` | added | the At-a-glance "Works with" chips [src: index.html:36, :49, :63, :166-168]; names per D7's default | the same six tools the first screen shows; no list that disagrees with the page |
| `email` | added | [src: index.html:186] | the address is already on the page as text |
| `image` | **not added** | — | needs `[BRETT: headshot (yes/no; file)?]`. The social card is not a picture of the person, so it isn't used here |

Rules:
- `jobTitle` and `worksFor` change in the PR that changes the hero once the Geotab internship ends.
  They are true only while "Present" is (A19).
- Every value is the same text the page shows. No fact appears here that isn't on the page.
- **Check:** in `_site/index.html`, `JSON.parse` succeeds on every `application/ld+json` block. Exactly
  one has `"@type": "ProfilePage"`, and no block contains `[BRETT`.
- Optional, by Brett after deploy: paste the live URL into the Schema Markup Validator
  (validator.schema.org). It isn't run from the build container (§0: no third-party submissions from
  tests).

**`/mcp/` and the demos.** `/mcp/` keeps jekyll-seo-tag's `WebPage` with `author` Brett Adams
(measured). An `Article` type would need a publication date, and the sources give only "October
2026" (`mcp/index.html:11`). The demos get no JSON-LD. Their title and canonical carry the name.

### 12.4 Sitemap and robots

- **No change to the plugin or its config.** `jekyll-sitemap` lists every page and every static
  `.html` and `.pdf` file. Today (measured, local build 2026-10-21) that is `/`, `/mcp/`,
  `/mcp-surface/`, `/neuronet-arena/` and `/assets/resume/Resume_BrettAdams.pdf`.
  - `/404.html` is left out by `sitemap: false` (`404.html:5`).
  - `og.png`, `THIRD_PARTY_LICENSES.txt` and the new fonts and CSS are not listed, which is correct.
- **After the redesign** the list is the same five URLs.
  - `/border-waits/` and `/mazefinder/` join it automatically in PRs 12 and 13.
  - The border-waits data on the project repo's Pages is another site, so it is never in this sitemap.
  - If the GA-on-demo-pages Decision adds front matter to `mcp-surface/index.html` (§13.7), that
    page is still listed, now as a page rather than a static file.
- **Check (PR 10, PR 12, PR 13):** list the `<loc>` values in `_site/sitemap.xml`. They are exactly
  the expected URLs, and none is under `/handoff/`, `/resume/` or `/sift/`.
- **`robots.txt`** stays as it is: allow all, plus the sitemap URL (`robots.txt:1-4`).
- **The résumé PDF stays in the sitemap.** It is the file a recruiter searching the name most wants,
  and its path never changes (§0).

### 12.5 Favicon

§7.11 leaves the favicon to this section. Today's `assets/img/favicon.svg` uses `#15181c`, `#ffffff`
and `#67d2e6`, none of which is a token (`assets/img/favicon.svg:2-4`).
- **PR 10 recolours it to tokens:**
  - `#15181c` → `#0b0f14` (`--bg`);
  - `#ffffff` → `#e7ecf2` (`--text`);
  - `#67d2e6` → `#4fd6c4` (`--teal`).
  The "BA" mark, its size and its position stay the same. This is part of the visual-system Decision
  (§18 #2), default ship.
- **`apple-touch-icon.png`** (180×180, 2,743 B, measured) is redrawn from the recoloured SVG in the
  same PR:
  - render the SVG inline in a 180×180 page that loads the self-hosted Plex Sans (an SVG loaded as an
    `<img>` can't use web fonts);
  - screenshot it with Playwright;
  - palette-encode it as in 12.2 (today's file re-encodes to 1,682 B, measured).
- **`/neuronet-arena/` keeps its own inline icon.** It is already drawn in `--bg`, `--amber` and
  `--teal` (`neuronet-arena/index.html:15`), and it is synced, so it changes only upstream.

### 12.6 Writer's notes

- **Writer's note:** the brief asks whether to regenerate `social-card.png`. The answer here is to
  re-encode it (−60%, measured) and leave its design alone until its text goes stale. Regenerating
  now would change nothing a reader sees.
- **Writer's note:** the 404 description is set in PR 8 with the rest of the 404 work. The demo
  titles are set in PR 9 (mcp-surface, §8.20 fix 8) and PR 11a (neuronet-arena). PR 10 holds the
  homepage description, the JSON-LD, the image re-encodes and the favicon.

## 13. Analytics

The redesign changes markup around every analytics carrier, but not the carriers themselves. GA4
stays on `G-Z3KW627PQQ` (`_config.yml:11`). `assets/js/analytics-events.js` keeps its six events and
its code; the only edit by default is a comment (13.7). Every PR proves this with
`measure.mjs --analytics --dnt` (13.10).

### 13.1 What stays exactly as it is

- **The loader:** `_includes/analytics.html:1-12`. gtag.js async plus `gtag('config', …)`, then
  `analytics-events.js` with `defer`. It runs on every default-layout page: `/`, `/mcp/` and
  `/404.html` (`_layouts/default.html:35`).
- **Do Not Track:** `analytics-events.js:12` returns before any listener is attached. gtag's own
  `config` page view still runs for DNT visitors, as today (fact sheet §11). Nothing in this spec
  touches either line.
- **The measurement ID:** `G-Z3KW627PQQ` only. **`G-EBYB5MK388` is never restored** (`_config.yml:9-10`).
  The comment that names it stays in `_config.yml`, which Jekyll doesn't publish.
- **What is observed:** `section[id]` only (`analytics-events.js:68`), the click listener
  (`:95-112`) and the scroll listener (`:131-137`).
- **The résumé file:** `assets/resume/Resume_BrettAdams.pdf`, matched by `/Resume_BrettAdams\.pdf$/`
  (`:108`).

### 13.2 Each event and the element that carries it after the redesign

| Event | Today: carrier (measured, audit §4) | After: carrier | PR | Count on `/` at 1440 (`measure.mjs --analytics`) |
|---|---|---|---|---|
| `section_view` | `section#experience`, `#projects`, `#skills`, `#contact` (`index.html:21`, `:81`, `:161`, `:183`) | the same four `section` elements, same ids, same `aria-labelledby` (§8.4–8.15; rows in 13.3) | 4–7 keep them | 4 values: experience, projects, skills, contact |
| `section_dwell` | the same four | the same four | — | up to 4 values (sections visible ≥ 2 s) |
| `project_click` | any `<a>` inside `[data-project]`: 5 `article.project` cards and 2 `li` in `ul.also` (`index.html:85`, `:98`, `:112`, `:127`, `:140`, `:154`, `:155`) | `article.project[data-project]` for the five cards (§8.5, §8.6) and `li[data-project]` in `ul.also` (§8.9). Every `<a>` inside counts: the `aria-hidden` `.project-shot` link, each `.links a` and `.also-links a`. The `sr-only` suffixes don't change `link.href` | 6 | **18**, as today: neuronet-arena 3, mcp-servers 4, mcp-surface 5, promptpal 3, technest 2, mazefinder 1. `nhl-arena` has no link and never fires (fact sheet §11) |
| `resume_download` | header `.nav-resume`, hero `.btn-primary`, contact `.btn` (`_layouts/default.html:48`, `index.html:12`, `:190`) | header `.nav-resume` (§8.1), hero `.btn-primary` "Résumé (PDF)" (§6.3), contact `.btn` (§8.15); also the 404 button (§8.17) and the demo shell's `.demo-resume` (§8.20) | 3, 4, 7, 8, 9 | **3** |
| `contact_click` | hero `github`, `linkedin`, `email`; contact `email`, `linkedin`, `github` (`index.html:13-15`, `:186`, `:188-189`) | hero `.btn[data-contact]` ×3, now in the order email, linkedin, github (§8.2); contact ×3 (§8.15); **footer ×3 if the footer Decision is yes** (§8.16, 13.7); demo footer ×3 (§8.20, sends only if GA is on that page) | 4, 7 | 6 until PR 7; **9** after PR 7 (default) |
| `scroll_depth` | the document: 25, 50, 75, 100 % of `scrollHeight` (`analytics-events.js:114-137`) | the same document | — | 4: 25, 50, 75, 100 |

**Values that change, and when:**
- **PR 6:** the MCP card's screenshot now goes to `/mcp/` (audit issue 24), so its `project_click`
  sends `link_url: https://brettadams0.github.io/mcp/` instead of the google-workspace-mcp repo.
  MazeFinder's click moves from the name to a "Source" link with the same URL (§8.9).
- **PR 7:** `contact_click` totals rise from the day the footer carries `data-contact`. They are
  extra clicks counted, not a change of meaning. `method` keeps the same three values.
- **PR 8:** `resume_download` gains the 404 page's button as a source.
- **PRs 12 and 13:** `project_name` gains `border-waits` (PR 12) and `mazefinder` moves from the
  list to a card (PR 13). `project_name` is already registered, so neither needs a new custom
  definition (§5.5).

Nothing the hero's jump link "Projects and live demos" does sends an event. It sits outside every
`[data-project]`, has no `data-contact`, and isn't the résumé (§6.3). It is shown above 860 px only
(§8.2); below that, the phone row's "Projects" link does the same job.

### 13.3 One row per current `section_id`

| `section_id` | Today | After the redesign | What changes inside it | Rule |
|---|---|---|---|---|
| `experience` | `<section id="experience" class="section" aria-labelledby="experience-title">` (`index.html:21`) | the same element (§8.4) | four `li.job`, shorter: bullets beyond the lead ones sit in `details.job-more` (§4.5, §8.4) | id, tag and `aria-labelledby` unchanged |
| `projects` | `<section id="projects" class="section" aria-labelledby="projects-title">` (`:81`) | the same (§8.5–8.9) | cards gain the kind line and `srcset`; `ul.also` gains a links cell | unchanged |
| `skills` | `<section id="skills" class="section" aria-labelledby="skills-title">` (`:161`), holding `div#education` (`:172`) | the same (§8.13); `div#education` stays a `div` (§8.14) | none | unchanged; `#education` gets no `section_view` unless 13.8's N2 is approved |
| `contact` | `<section id="contact" class="section contact" aria-labelledby="contact-title">` (`:183`) | the same (§8.15) | button order and copy only | unchanged |

**No new `section[id]` anywhere.**
- The hero stays `section.hero` with no id (§8.2).
- The At-a-glance card is a `div.glance` with no id (§6.4). §8.3's `section.glance-card` form also
  has no id; §6's `div` is what ships.
- `/mcp/` has no `section[id]` (§8.18), and the 404's `section.not-found` has none (§8.17).
- mcp-surface's new `.demo-how` section has no id (§8.20).

**Acceptance in every PR:** on `/`, `[...document.querySelectorAll('section[id]')].map(s => s.id)`
equals `["experience","projects","skills","contact"]`. On `/mcp/` and `/404.html` it is `[]`.
- **A tool enforces it from PR 0 on.** `measure.mjs --analytics` checks each of those three pages
  against `EXACT_SECTIONS` (§16 PR 0). A stray `section[id]`, or a `section_view` for an id outside
  the list, prints `unexpected section_id: <id>` and fails the run. Before PR 0, the tool only
  failed on a missing id (`measure.mjs:419-429`), so a new id passed silently.
- `--expect-sections` overrides the list for one run. PR 14 changes `EXACT_SECTIONS` itself for each
  approved new value.

### 13.4 The résumé link

| Where | `href` in the source | Published `href` | Fires `resume_download` |
|---|---|---|---|
| Header "Résumé" (`.nav-resume`, every default-layout page) | `{{ '/assets/resume/Resume_BrettAdams.pdf' \| relative_url }}` | `/assets/resume/Resume_BrettAdams.pdf` | yes |
| Hero "Résumé (PDF)" (`.btn-primary`) | same | same | yes |
| Contact "Résumé (PDF)" | same | same | yes |
| 404 "Résumé (PDF)" (PR 8) | same | same | yes |
| mcp-surface shell "Résumé" (PR 9) | literal `/assets/resume/Resume_BrettAdams.pdf` (the page has no Liquid) | same | only if GA is added there (13.7) |
| neuronet-arena shell (upstream, PR 11a) | `https://brettadams0.github.io/assets/resume/Resume_BrettAdams.pdf` (the upstream page also runs on its own) | same | the regex matches the end of the string, so yes, if GA is ever added there |

- **The path is unchanged:** `assets/resume/Resume_BrettAdams.pdf`, built from `resume/resume.tex`
  (`README.md:22`, `:36-39`).
- **No link carries a query string, a fragment or a `download` attribute.** Any of them stops the
  regex matching, or changes what the click does, with no error.
- **Check in every PR:** `grep -rnoE 'Resume_BrettAdams\.pdf[^"]+"' _site --include=*.html` prints
  nothing (no character between `.pdf` and the closing quote). `--analytics` shows
  `resume_download` ×3 on `/`.

### 13.5 Expected `--analytics` results, page by page

These are the numbers each PR's verification compares against. The tool clicks every carrier at
1440×900 with navigation prevented. It reads the `dataLayer` pushes, and GA requests are blocked
(`measure.mjs:364-430`).

| Page | Before PR 7 | After PR 7 and PR 8 (defaults) |
|---|---|---|
| `/` | `project_click` ×18, `resume_download` ×3, `contact_click` ×6, `section_view` 4 values, `scroll_depth` 25/50/75/100; `failed_clicks` lists the 3 footer links as "missing data-contact" | `project_click` ×18, `resume_download` ×3, `contact_click` ×9, same sections and marks; `failed_clicks` empty |
| `/mcp/` | `resume_download` ×1, `scroll_depth`; footer links "missing data-contact" | `resume_download` ×1, `contact_click` ×3, `scroll_depth`; no `section_view` |
| `/404.html` | `resume_download` ×1, `scroll_depth` | `resume_download` ×2, `contact_click` ×3, `scroll_depth` |
| `/mcp-surface/`, `/neuronet-arena/` | "no gtag on this page" | the same, unless 13.7's demo-page Decision adds GA |
| Every page with `--dnt` | 0 custom events | 0 custom events |
| `measurement_ids` | `["G-Z3KW627PQQ"]` | `["G-Z3KW627PQQ"]` |

### 13.6 How `scroll_depth` and dwell keep their meaning

**With the default order** (hero → Experience → Projects → Skills → Contact, §5.2), every
`section_id` still names the same content in the same place in the page. Two things move:
- the page's height;
- how long each section stays on screen.

**`scroll_depth`** is the share of the document's height that has been on screen:
`(pageYOffset + innerHeight) / scrollHeight` (`analytics-events.js:124`). The marks stay 25, 50, 75
and 100. What sits at each mark depends on the height.
- Today the page is 6,346 px tall at 390 and 4,445 px at 1440
  (`[brettadams0:measure-home.json.viewport.390.page_height]`).
- So the 25% mark is reached when the bottom of the screen passes y = 1,587 at 390 and y = 1,111 at
  1440 (computed). Both are inside Experience. At 390 it runs from y ≈ 560 to `#projects` at
  y = 2,496. At 1440 it starts inside the first screen and ends at `#projects`, y = 1,703 (measured,
  `[brettadams0:measure-home.json.viewport.1440.key_info.projects_heading]`).
- The 50% mark (y = 3,173 at 390, 2,223 at 1440, computed) is inside Projects at both sizes.

After the redesign:
- the hero grows (the glance card, and on phones the section-link row);
- Experience shrinks to meet §4.3's limit (`#projects-title` bottom ≤ 2,532 px at 390);
- the card images grow from 16:9 to 16:10 boxes (§7.9).

To keep the marks readable:
1. PRs 4, 5 and 6 record `viewport.390.page_height` and `viewport.1440.page_height` from
   `measure.mjs --a11y`. They also record which section holds y = 25%, 50% and 75% of that height,
   before and after, in a table in the PR description.
2. Brett keeps the merge dates of PRs 4–7 next to the GA property (the PR descriptions are enough).
   A step in `scroll_depth` on those dates is then a layout change, not a change in behaviour.
3. `percent` keeps its four values. No new mark is added.

**`section_dwell`** is the time a section is "at least half visible": half of it on screen, or, for a
section taller than the screen, half the screen filled by it (`analytics-events.js:31-38`). The
seconds still mean "time this section held the screen" for each section.
- Experience gets shorter (§8.4), so a visitor who reads it the same way spends fewer seconds in
  it.
- At 390 it stays taller than the 844 px screen (about 1,350 px, estimated from §8.4's height check),
  so the same "fills half the screen" rule applies before and after.
- Compare dwell before and after only per section, and expect Experience's to drop at PR 5 for
  layout reasons.
- `section_view` reach (sections viewed per page view) is the steadier series across the change,
  because "seen for one second" doesn't depend on length.

**If Brett moves Projects above Experience** (§18 #4; not the default):
- The `section_id` values stay the same, and nothing is renamed or merged.
- The order in which sections are reached changes, so:
  - `projects` reach rises and `experience` reach falls for layout reasons alone;
  - the 25% mark lands in Projects instead of Experience;
  - Experience dwell is not comparable across that date.
- What keeps the reports honest:
  - treat that PR's merge date as a break in the series;
  - compare reach per page view within each period, not across it;
  - leave the four `section_id` values exactly as they are, so a later reader can still join the
    periods.

### 13.7 The two analytics Decisions in this redesign

**Footer `data-contact`** (§8.16; §18 #12, **default: add**).
- **Change:** `data-contact="github"`, `"linkedin"` and `"email"` on the three footer links
  (`_layouts/default.html:61-63`), and the same on the demo shell's footer (§8.20).
- **Effect:**
  - `contact_click` counts footer clicks from the PR 7 deploy on;
  - on `/mcp/` and `/404.html`, the footer is the only contact path and is measured for the first
    time (audit issue 12).
- **Needs:** no new parameter and no new custom definition (`method` already exists).
- **If no:** PR 7 ships the footer markup without the three attributes. `measure.mjs` then keeps
  listing them under `failed_clicks` as "missing data-contact", which doesn't fail the run.

**GA on demo pages** (audit issue 11; §18 #17, **default: none**, as today).
- **Option (b), mcp-surface only.** Add an empty front matter block (`---` on line 1, `---` on line 2)
  to `mcp-surface/index.html`, and `{% include analytics.html %}` before `</head>`.
  - Jekyll then runs the file through Liquid. It contains no `{{` or `{%` today (measured with
    `grep`), and the sitemap keeps listing it (12.4).
  - The page then sends:
    - `page_view`;
    - `scroll_depth`;
    - `resume_download` from the shell's "Résumé";
    - `contact_click` from the demo footer.
  - It sends no `section_view`: mcp-surface has no `section[id]`. Its cards use `aria-labelledby`
    (`mcp-surface/index.html:41`, `:58`, `:77`), and `.demo-how` gets no id.
  - DNT behaves as on the site. "Nothing you type leaves this tab" (§8.20) stays true, because the
    editor's content is never sent.
- **Option (c), pages built in this repo.** The same for `/mazefinder/`, which is built in this repo
  (`handoff/mazefinder-live-demo.md:125-128`).
  - `/border-waits/` is synced from the project's `web/`, so there the include would be a local
    difference after every sync, like neuronet-arena's one line (`README.md:75-76`).
- **Option (d), neuronet-arena.** Needs an upstream snippet that runs only on this domain, or a second
  local difference after every sync.
  - Its `section#parity` (`neuronet-arena/index.html:114`) would start a new `section_id` value,
    `parity`. That id would have to go upstream first.
  - Not recommended.
- **Recommendation:** (b), plus (c) for `/mazefinder/`. Direct visits to a shared demo link are
  invisible today, and the homepage's `project_click` only counts visits that start on the homepage.

### 13.8 Proposed new values and events

Each item here is a Decision for Brett (§18 #24–27). **The default for every one is "not added".** If
approved, it ships in PR 14. None is needed for the redesign to work.

**N1. `section_id` `hero`.**
- **Markup:** `<section class="hero" id="hero" aria-labelledby="hero-name">` (`index.html:4`).
- **Fires:**
  - `section_view` on nearly every page view, after the first second, because the hero fills the
    first screen;
  - `section_dwell` with the time spent on the first screen.
- **Answers:** how long people stay on the first screen before scrolling or leaving.
- **Cost:** a new value in the `section_id` dimension. Every report that lists sections gains a row
  that is close to 100% of page views. `/#hero` becomes a valid anchor; nothing links to it.
- **Recommendation:** no. GA4's engagement time already covers most of it.

**N2. `section_id` `education`.**
- **Markup:** `div#education` (`index.html:172`) becomes
  `<section id="education" aria-labelledby="education-title">`, with the same children. It stays
  inside `section#skills > .two-col`, so the CSS (`#education`, `.edu`) is unchanged.
- **Fires:** `section_view` and `section_dwell` for the Education block.
  - Nested inside `#skills`, its time is also counted in `skills` dwell, as today.
  - At ≥ 981 px the block sits beside Skills (two columns, §8.13), so it fires whenever Skills does.
    The extra information comes from phones and tablets, where it is below Skills.
- **Side effects:**
  - a nested region landmark "Education" inside "Skills" (axe allows it; `landmark-unique` still
    passes, since the names differ);
  - the script's comment about `#education` (fact sheet C6) becomes true again.
- **Recommendation:** optional. It answers the question the script's comment asks, but mainly on
  phones.

**N3. Event `nav_click`.**
- **Fires:** on a click of an in-page link inside an element with `data-nav`.
- **Params:**
  - `target_section`: the target, `link.hash.slice(1)`, one of experience, projects, skills,
    contact. It is a new parameter, not `section_id`: `section_id` is the dimension the reach and
    dwell reports are built on, and click counts in it would mix into any Section breakdown that
    isn't filtered by event name;
  - `nav`: `header` (`nav.site-nav`), `sections` (the phone row `nav.nav-sections`) or `hero` (the
    jump link's `p.hero-jump`, shown above 860 px only).
- **Markup:** `data-nav="header"` on `nav.site-nav`, `data-nav="sections"` on `nav.nav-sections`,
  `data-nav="hero"` on `p.hero-jump`. The header's Résumé link has no hash, so it never sends
  `nav_click`.
- **Code,** added inside the existing click listener (`analytics-events.js:95-112`), after the
  `project` block:

  ```js
  var nav = closest(link, '[data-nav]');
  if (nav && link.hash) send('nav_click', { target_section: link.hash.slice(1), nav: nav.getAttribute('data-nav') });
  ```

- **Answers:** whether the new phone row (audit issue 1, High) and the jump link are used.
- **Recommendation:** yes, if Brett wants to judge the phone nav. It is the cheapest way to see
  whether issue 1's fix is used.

**N4. Event `job_more_open`.**
- **Fires:** once per disclosure per page view, when a `details.job-more` opens (§8.4). Not on close.
- **Params:** `item` from `data-item` on the `details`: one of `geotab`, `mto`, `cimco-2024`,
  `cimco-2023`. No `section_id`: `item` already names the role, and it keeps clicks out of the
  Section dimension (N3).
- **Code:** `toggle` doesn't bubble, so the listener captures it at the document:

  ```js
  document.addEventListener('toggle', function (event) {
    var d = event.target;
    if (!d.matches || !d.matches('details.job-more') || !d.open || d.getAttribute('data-sent')) return;
    d.setAttribute('data-sent', '1');
    send('job_more_open', { item: d.getAttribute('data-item') });
  }, true);
  ```

- **Answers:** whether hiding bullets behind "N more" hides something people want. That is the
  main cost of §4.5's trim.
- **Recommendation:** yes, for a few months after PR 5.

All four inherit the DNT return at `analytics-events.js:12`, because they use the same `send()`.
PR 14 also extends `measure.mjs` (§16 PR 14):
- `EXACT_SECTIONS['/']` (PR 0) gains each approved id;
- `--analytics` clicks every `[data-nav] a[href*="#"]` (N3) and each `details.job-more > summary`
  once (N4), with navigation prevented as today. Today it clicks only project, contact and résumé
  links (`measure.mjs:371-389`), so neither event could fire;
- it adds the approved events to its expected list.

### 13.9 GA4 custom definitions to register

In GA4, Admin → Custom definitions. Every one is event-scoped. Parameters only appear in reports
once registered (`README.md:55-56`).

| Display name (suggested) | Parameter | Type | Used by | Status |
|---|---|---|---|---|
| Section | `section_id` | Dimension | `section_view` and `section_dwell` only | listed in the README today |
| Project | `project_name` | Dimension | `project_click` | listed today |
| Contact method | `method` | Dimension | `contact_click` | listed today |
| Scroll percent | `percent` | Dimension | `scroll_depth` | listed today |
| Dwell seconds | `seconds` | Metric, unit seconds (recommended, so it can be averaged) | `section_dwell` | listed today; the README doesn't say metric or dimension |
| Link URL | `link_url` | Dimension | `project_click` | **sent today, not in the README list** (`analytics-events.js:103`, fact sheet §11). Optional; it tells screenshot clicks from "Source" clicks |
| Nav target | `target_section` | Dimension | `nav_click` | only if N3 is approved |
| Nav | `nav` | Dimension | `nav_click` | only if N3 is approved |
| Disclosure | `item` | Dimension | `job_more_open` | only if N4 is approved |

N1 and N2 need no new definition. They are new values of `section_id`.

### 13.10 How to verify

Every PR that touches a page runs:

```sh
jekyll build
node handoff/tools/measure.mjs --site _site --path / --path /mcp/ --path /404.html \
  --analytics --dnt --json /tmp/ga.json
grep -rn "G-EBYB5MK388" _site     # must print nothing
grep -rnoE 'Resume_BrettAdams\.pdf[^"]+"' _site --include=*.html     # must print nothing
```

- **Pass:**
  - every page prints `analytics PASS` and `dnt PASS: 0 custom events`;
  - the counts match 13.5;
  - `pages[].analytics.measurement_ids` is `["G-Z3KW627PQQ"]` on every page;
  - `section_view_ids` on `/` equals the four ids, and no page prints `unexpected section_id`
    (13.3);
  - `failed_clicks` holds only the "missing data-contact" footer lines before PR 7, and nothing
    after.
- **GA stays blocked.** The tool aborts every request to Google's GA hosts by default
  (`measure.mjs:20-23`). **Never use `--allow-ga`.** The baseline's one development run with it
  probably sent one `page_view` with a `127.0.0.1` page location to `G-Z3KW627PQQ` on 2026-10-21 at
  03:57 UTC (§2.1; §18 #50).
- **GA DebugView is for Brett only, after a deploy.** On Brett's own browser, with the Google Analytics
  Debugger extension or `debug_mode`, Brett opens `/`, clicks a card, the résumé and a contact link,
  and sees `project_click`, `resume_download` and `contact_click` with their parameters in Admin →
  DebugView. The builder never sends hits to the property from a test.
- **On the live site after a deploy** (optional, still blocked):
  `node handoff/tools/measure.mjs https://brettadams0.github.io/ --path / --analytics --dnt`.

### 13.11 Writer's notes

- **Writer's note:** the brief asks how `scroll_depth` and dwell keep their meaning "when Projects
  moves above Experience". §5.2 keeps today's order by default. So 13.6 covers the default first,
  and the move as the non-default answer to §18's section-order Decision (#4).
- **Writer's note:** 13.3 follows §6.4 (`div.glance`) rather than §8.3's `section.glance-card`, as
  the plan does for every first-screen element. Either form has no `id`, so analytics are the same.
- **Writer's note:** `link_url` is already sent but isn't in the README's list of definitions. It is
  listed in 13.9 as optional. No code changes.

## 14. New project proposal

**Recommendation: build border-waits,** an hourly pipeline over the two public border wait-time feeds
(CBSA, Canada-bound, and U.S. CBP, US-bound), modelled and tested with dbt on DuckDB, published as
Parquet to the project repo's own GitHub Pages, and queryable with SQL in the visitor's browser
through DuckDB-Wasm. It gets one homepage card and its own page. Nothing on the homepage runs or
updates. The full build brief is [`border-waits-pipeline.md`](border-waits-pipeline.md). **Default if
Brett doesn't answer: the brief stays queued and nothing is built** (Decision, §18 #21).

Facts about Brett here come from the fact sheet. Claims about data sources, GitHub and the engine
come from pages and endpoints loaded on 2026-10-21 (UTC). The raw samples, engine measurements and
prototype files were kept out of git (research README, "What is not committed"). The measured values
are recorded here and in the brief.

### 14.1 The gap it closes

- **Brett's two most recent roles are pipelines, SQL, CI/CD and BI** [src: index.html:31-33, :45]:
  Airflow DAGs on Cloud Composer, production BigQuery SQL and GitLab CI/CD at Geotab; Power BI on
  Azure Databricks and SQL at MTO. Earlier, at CIMCO, Brett wrote "a Python pipeline that aggregates
  data from two APIs into structured JSON" [src: index.html:59].
- **Both live demos are something else:** neuronet-arena is an ML simulation and mcp-surface is
  developer tooling (fact sheet §7.1–7.2).
- **What the research shows:** among the 10 data sites, pipeline work is shown mostly as case studies
  (deshraj-jogiya, shaundataanalytics, beingbrute, pahasara). Running artefacts are rarer
  (robinlinacre's in-browser model, deshraj-jogiya's hosted product, beingbrute's Tableau
  dashboards). **No verified claim in the 29-site set describes a scheduled pipeline whose freshness
  or test status a visitor can see** (appendix, ["Set-level check for the new
  project"](research/appendix.md#set-level-check-for-the-new-project)). That is a statement about the
  set, not the web.
- **What hiring evidence asks for:** one or two end-to-end projects (S07), a diagram up top ("No
  diagram means no project", S06), tests and data-quality controls (S10), and no tutorial datasets
  (S06, H6).

### 14.2 Candidates, ranked

| | **A. border-waits** (recommended) | B. claims-check | C. Bank of Canada rates mart |
|---|---|---|---|
| What it is | Hourly pipeline over CBSA and CBP wait times; dbt models and tests; freshness checks; Parquet on Pages; SQL in the browser | A pipeline that checks the site's and résumé's factual lines against Brett's repos and npm | A daily batch over the Bank of Canada Valet API (USD/CAD, overnight rate) with a BI-style page |
| Skills shown | Orchestration, SQL modelling, data tests and unit tests, freshness, CI/CD gating, failure alerts, joining two APIs, BI-style output, a rebuild check with the same DuckDB version in CI and in the browser | CI, data tests, automation | API ingestion, modelling, BI |
| Audience fit (1–5, **estimated**) | Hiring manager 5, engineer 4, recruiter 4, business 4 | Engineer 4, hiring manager 3, recruiter 2, business 1 | Business 4, hiring manager 3, recruiter 3, engineer 2 |
| Size (**estimated**) | M, 50–65 h | S–M, 20–30 h | S, 15–25 h |
| Risk | Medium: undocumented quirks, wasm size, the 60-day schedule rule | Medium: publishing Brett's own contradicted claims is an own goal until they're fixed | Low |
| Freshness story | Hourly; source time and run time shown; a real stale source to flag | Daily, meaningful only when copy changes | Business-daily |
| Cost | $0 | $0 | $0 |
| Distinctiveness | High | Medium | Low; macro charts are common |
| Best use | Its own card and page | A CI check in the new repo later; no card | Folded into A as optional milestone M5 |

### 14.3 Why border-waits

1. **It is the only candidate that puts Brett's job skills in front of a visitor as running code:**
   scheduled pipelines, SQL, CI/CD and BI. That is the gap.
2. **It shows data-quality and freshness work** that neither existing demo shows, with real defects
   to catch from day one (measured in today's samples): invalid JSON from CBSA, crossing names that
   drift between files, and a CBSA history file whose metadata was last modified 307 days ago.
3. **It runs on what exists:** free Actions in a public repo, the project's own Pages on the same
   origin (byte ranges and CORS measured), no secrets, no change to the site's build or deploy.
4. **It keeps Brett's best demo idea.** neuronet-arena's "same bits" check becomes "same SQL, same
   result", run with the same DuckDB version in CI and in your browser. A prototype ran dbt's
   compiled model SQL unchanged in DuckDB-Wasm and got the same row count, sum and MD5 as the CI-side
   build for the monthly mart (237 rows, `md5=45cbd393329f382a01bc53fbff69e297`, **measured**). The
   shipped check rebuilds the hourly mart the page charts, from the last 14 days of readings (§14.9).
5. **It is milestone-shaped.** Each step ships on its own, and nothing on the homepage depends on it
   running.

**Research it fits:** P8 (demos on their own page behind a static card), P10 (how it was built, real
data, source link; robinlinacre runs its own record-linkage model in the browser through DuckDB-WASM
`[robinlinacre:inner-desktop-fold.png]`), P11 (the case-study outline, here with tests, results and
limits), P18 (a freshness line that stays true), I2 (say what loads before it loads), I6 (CI and
test status as proof); H4–H8.

**Honest framing, required in every line of copy:**
- The scheduler is GitHub Actions cron, not Airflow. Brett's Airflow work is at Geotab
  [src: index.html:31]; don't imply this project uses Airflow.
- The values are wait **estimates published by CBSA and CBP**, not measured crossing times.
- No source says where Brett lives (fact sheet §2), so no "my commute". Only the university is in
  Windsor [src: resume/resume.tex:89].

### 14.4 How it runs on static Pages

The page and its data live in two places, and the site repo gets no workflow.

1. **Project repo** `brettadams0/[BRETT: repo name, suggested border-waits-pipeline]`, public. Brett
   creates it and sets Pages to deploy from GitHub Actions. Its Pages site lives at
   `brettadams0.github.io/<repo>/` ("Default site location http(s)://<owner>.github.io/<repositoryname>",
   GitHub docs). The Pages docs loaded don't say what happens when a folder in the user site has the
   same name as a project site, so **the repo name must differ from the site folder `border-waits`**.
   Project sites already work on this domain: `/google-workspace-mcp-policy/` returned 200 (measured
   with `curl`, 13:37 UTC) and has no folder in this repo.
2. **Hourly workflow** at minute 17 (`cron: "17 * * * *"`), plus `workflow_dispatch`, one
   concurrency group. GitHub's docs warn that "High load times include the start of every hour" and
   that queued jobs may be dropped.
3. **Ingest:** fetch both feeds (no keys needed, measured with plain `curl`) and parse in Python.
   - **Per-run checks run inside ingest,** before anything is committed: the header contract, at
     least 25 CBSA crossings and 25 CBP Canadian-border ports, delays between 0 and 720, and
     parseable times. A failing run commits nothing and keeps its raw artifact.
   - Every CBP lane group (standard, NEXUS/SENTRI, FAST, ready) is normalized now. Staging filters to
     standard lanes for the first marts.
   - Each run writes one Parquet file per source, with `run_id` and `schema_version` columns, to an
     orphan `data` branch in the project repo. The gzipped raw CBSA CSV is committed next to it. The
     raw CBP responses go up as a 14-day workflow artifact.
   - Every read uses `hive_partitioning = true, union_by_name = true`. Measured in review with DuckDB
     1.4.3: without `union_by_name`, a column added in a newer file was silently dropped.
4. **Model and test:** `dbt build` on DuckDB (staging, a crossing crosswalk seed, deduplicated
   readings, hourly profiles, the CBSA monthly history).
   - Error-severity tests are scoped to the current run with `where: "run_id = '{{ var('run_id') }}'"`.
     Their history-wide copies are warnings. So one bad old snapshot can't block every later build.
   - An `excluded_snapshots.csv` seed (path, reason) quarantines a bad file without rewriting the
     `data` branch.
   - dbt unit tests cover the logic live data can't exercise on demand. They cover the midnight
     rollover, a wrong time-zone abbreviation, the DST fold on 2026-11-01 and the gap on 2027-03-14,
     the dedupe, and the fiscal-month mapping.
5. **Publish:** export the marts under content-hashed names. `manifest.json` points at them (run,
   versions, test counts, per-source freshness, the rebuild check, each file's URL and SHA-256).
   `actions/deploy-pages` publishes to `https://brettadams0.github.io/<repo>/data/v1/`.
   - **Deploy only if this run's error tests pass.** Freshness never blocks a deploy.
   - **On a failed run,** the workflow redeploys the last good export with a `status.json` naming
     the failing step or test and its time. It also opens, or comments on, one "pipeline failing"
     issue, and closes it on the next success.
6. **Site page** `/border-waits/` in this repo: a verbatim sync of the project's `web/` folder, like
   neuronet-arena.
   - On load it fetches `manifest.json` with `cache: 'no-cache'` and follows its URLs, then renders a
     static view with plain JS.
   - **The SQL engine loads only after a click** on a button that states its size.
   - If the fetch fails, the page shows a synced fallback snapshot and says so. The SQL button is
     then disabled with "SQL needs the live data files".

**Hosting options tested** (headers measured with `curl`, 2026-10-21 13:37–13:38 UTC):

| Option | Measured | Verdict |
|---|---|---|
| A. Project repo's own Pages, same origin | `206` for a byte range, `access-control-allow-origin: *`, `cache-control: max-age=600` | **Use**, with content-hashed data files so the 600 s cache can never mix two runs |
| B. `raw.githubusercontent.com` | `206`, CORS `*`, `max-age=300`, but `content-type: text/plain` and a sandbox CSP | Fallback only |
| C. GitHub release assets | No `Access-Control-Allow-Origin` on the redirect or the final host | Reject for the browser |
| D. jsDelivr `/gh/` mirror | `s-maxage=43200` (12 h at the CDN) | Reject for fresh data; fine for the pinned engine |
| E. Manual sync into the site repo | — | The fallback snapshot only |
| F. A workflow or bot commits in the site repo | — | Changes the site's deploy: a Decision, not proposed |

**GitHub limits that apply** (quoted from docs.github.com, loaded 2026-10-21): published sites "no
larger than 1 GB"; "soft bandwidth limit of 100 GB per month"; deployments "timeout if they take
longer than 10 minutes"; "The shortest interval you can run scheduled workflows is once every 5
minutes"; "In a public repository, scheduled workflows are automatically disabled when no repository
activity has occurred in 60 days"; "GitHub Actions usage is free … for public repositories that use
standard GitHub-hosted runners".

**Freshness: one set of lines** (all thresholds **estimated**):

| Check | What it measures | Warn | Error, or the page's badge | Blocks a deploy? |
|---|---|---|---|---|
| Source liveness, one dbt test per source | How far the newest `source_updated_at` for that source is behind the run's `fetched_at` | more than 2 h | more than 6 h | No |
| CBSA monthly history | Age of CKAN `metadata_modified` | more than 120 days | — | No |
| Pipeline liveness, on the page | Age of `manifest.generated_at` in the visitor's browser | — | "stale" badge at more than 3 h | — |

Freshness is measured on the source's own update time, per source, because `fetched_at` is written
minutes earlier by the same job and can't fall behind while the job runs. The data a visitor sees is
about 1 h of polling plus schedule delay old, so typically under 75–90 minutes (**estimated**). The
page shows "source updated", "Last successful run" and "Last attempt" times.

### 14.5 Data sources

| Source | What | Licence (quoted from the page loaded) | Privacy | Cost and rate | Notes (measured) |
|---|---|---|---|---|---|
| CBSA "Current Border Wait Times", `https://www.cbsa-asfc.gc.ca/bwt-taf/bwt-eng.csv` | Canada-bound commercial and traveller waits | Open Government Licence – Canada: "You are free to: Copy, modify, publish … for any lawful purpose." You must "Acknowledge the source … and, where possible, provide a link to this licence." Default statement: "Contains information licensed under the Open Government Licence – Canada." No logos; no implied endorsement | Aggregate estimates; no personal data | Free; 3,274 B per fetch; no published limit; hourly polling is 24 small requests a day | 29 rows although the page says "30 busiest land crossings"; UTF-8 BOM; `;;` delimiter that DuckDB's CSV sniffer rejects; mixed time-zone suffixes |
| CBSA "Historical Border Wait Times – Land Mode" (open.canada.ca dataset `000fe5aa-…`) | Monthly % of time the service standard was met, 26 ports × 79 months, Apr 2019 – Oct 2025 | OGL – Canada | None | Free; 80,693 B | Declared monthly, but metadata last modified 2025-12-18: a real stale source for the freshness check to flag |
| U.S. CBP Border Wait Times, `https://bwt.cbp.gov/xml/bwt.xml` (documented); `/api/bwtnew` (JSON, undocumented) | US-bound waits; 30 Canadian-border ports including Ambassador Bridge, Windsor Tunnel and Gordie Howe International Bridge | cbp.gov Copyright Notice: "information on the U.S. Customs and Border Protection website is in the public domain … We request only that the CBP be cited as the source". bwt.cbp.gov links to that notice from its footer | Aggregate; none | Free; `cache-control: no-store`; no published limit | A trailing space in "Gordie Howe International Bridge "; "no delay" with `delay_minutes` 5; empty fields when lanes are closed |
| CBSA JSON (don't use) | — | not listed on open.canada.ca | — | — | Not valid JSON: it ends in a `//` comment line |

Required on the page: the OGL attribution line with a link to the licence, and a CBP citation. No
CBSA or CBP logos. The published data folder carries the same attribution in its `index.html` and
in `manifest.json`, because the Parquet files can be reached without the page. The fetcher sends a
User-Agent naming the project repo.

### 14.6 Engine weights (measured)

**DuckDB-Wasm `@duckdb/duckdb-wasm@1.32.0`** (MIT; published 2025-12-16, the last non-dev release;
npm's `latest` tag points at a dev build, so pin exactly). Files from cdn.jsdelivr.net, measured with
`curl` at 13:38 UTC:

| File | brotli | uncompressed |
|---|---|---|
| `+esm` entry | 7,410 B | 28,019 B |
| `duckdb-browser-eh.worker.js` | 188,628 B | 772,759 B |
| `duckdb-eh.wasm` (what Chromium picked) | 6,764,975 B | 34,242,586 B |

**Real page load in Chromium** (Playwright `request.sizes()`, two runs):

| Moment | Requests | Bytes over the wire |
|---|---|---|
| Page load, before the click | 0 to any CDN | 0 |
| After one click: entry, three small dependencies, worker, wasm | 6 | 7,014,945 B |
| Plus the Parquet extension, auto-loaded from `extensions.duckdb.org` | 1 | 667,153 B |
| **Total** | **7** | **7,682,098 B** (about 7.7 MB); JS 249,970 B |

- The engine reports version v1.4.3, so the pipeline pins Python `duckdb==1.4.3` to match.
- Click to first result: 4.49 s and 5.98 s in two runs, unthrottled desktop through the session
  proxy. **Not representative of phones.** On this spec's throttled mobile profile (1.6 Mbps down,
  §2.1), the transfer alone takes about 38 s (**estimated**: 7,682,098 × 8 / 1,600,000), before any
  CPU cost. So the button shows progress while it loads, for example "2.1 of 7.7 MB".
- Only Chromium is installed here, so DuckDB-Wasm on iOS Safari is untested.
- **sql.js 1.15.0** for comparison: 289,879 B brotli wasm plus 14,911 B JS, about 25× lighter, but it
  reads only SQLite files, not Parquet. Wrong for a growing history.
- The button label states the size: "Load DuckDB to run SQL (about 7.7 MB)". No site in the 29-site
  set labels a demo's download size; this extends idea I2 (§3.5).

### 14.7 Size and milestones

**Size M, about 50–65 hours (estimated).** At two evenings a week plus a weekend day, about 10–12
hours a week (**estimated**), M0–M3 fit in about 4–5 weeks of a co-op term and M4 in about 1 more.
Data starts accumulating at M1.

**PR 12 is gated on at least 21 days of data in the `data` branch.** The chart groups readings by
local hour over all days, and an hour gains at most about one reading a day. So the median line
(drawn at n ≥ 7) needs a week, and the p90 band (drawn at n ≥ 20) needs about three weeks
(thresholds **estimated**).

| # | Milestone | Ships | Hours (estimated) |
|---|---|---|---|
| M0 | Repo, parsers for the CBSA CSV and CBP XML/JSON, pytest on the real responses fetched 2026-10-21 as fixtures, CI | A tested library; nothing scheduled | 6–8 |
| M1 | Hourly workflow: per-run checks in ingest, normalized Parquet per run (all CBP lane groups, `run_id`, `schema_version`) and the gzipped raw CBSA CSV to the `data` branch; raw CBP responses as 14-day artifacts; failure issue | A growing dataset, replayable from normalized readings and raw CBSA (raw CBP responses kept 14 days) | 8–10 |
| M1.5 | Burn-in: 14 days of unattended hourly runs with no manual fix, before any data is deployed publicly. A fix restarts the count | Confidence in the feeds' quirks | calendar time only |
| M2 | dbt project (staging, crosswalk seed, `excluded_snapshots` seed, marts, run-scoped data tests, unit tests, per-source freshness), content-hashed export, `status.json`, deploy to the project's Pages, dbt docs | Public Parquet, `manifest.json`, `summary` file, lineage docs | 14–20 |
| M3 | Site page `/border-waits/` static-first view (synced from `web/`), homepage card and screenshot | What visitors see first | 12–15 |
| M4 | "Query with SQL": DuckDB-Wasm on click with progress, preset queries, compiled model SQL, the hourly rebuild check | The live demo proper | 10–12 |
| M5 (optional) | CBSA monthly service-standard mart with its own freshness monitor; optional Bank of Canada FX join | Extra analysis | 4–8 |

**Prototype already run** (measured, 2026-10-21): a dbt-duckdb project (dbt-core 1.12.6, dbt-duckdb
1.11.0, DuckDB 1.4.3) built a staging model over the real CBSA history, a crosswalk seed, a mart and
7 tests: `PASS=10 WARN=0 ERROR=0`. It also showed that `dbt source freshness`, set up with
`loaded_at_field`, `warn_after` and `error_after`, flags a static 12-hour-old sample as `ERROR STALE`.
That proves the configuration, not the scheduled case: in the hourly job `fetched_at` is always minutes
old, which is why the shipped check measures each source's own update time (§14.4). In Chromium,
DuckDB-Wasm 1.32.0 ran the compiled mart SQL unchanged and matched the CI build exactly.

### 14.8 Risks

| # | Risk | Likelihood / impact (estimated) | Mitigation |
|---|---|---|---|
| R1 | A feed changes format or names (today's samples already show name drift) | medium / medium | Per-run contract checks in ingest, so a bad run commits nothing; unknown names go to a quarantine model and warn; error tests scoped to the run; `excluded_snapshots.csv` for a bad file already stored; a CI fixture case where one bad old snapshot plus one good new run still deploys |
| R2 | The schedule is auto-disabled after 60 days without repository activity; whether bot commits count is not stated in the docs | medium / medium. During a job search, a "stale" badge on the flagship data demo reads as an unmaintained pipeline | A failure issue opens on any failed run. A day-61 check records the workflow's state (`gh api repos/{owner}/{repo}/actions/workflows/pipeline.yml --jq .state`); if bot commits didn't keep it active, the fallback is a monthly reminder for Brett. The README says how to re-enable. The homepage chip says `Tests gate every deploy`, which stays true when the schedule stops |
| R3 | Delayed or dropped runs near the top of the hour | high / low | Minute 17; dedupe on the source's own update time, so a missed poll loses at most one reading |
| R4 | Licence terms: attribution, no logos, no implied endorsement; CBP's notice scope | low / medium | Footer attribution with the licence link; the same attribution on the data folder and in the manifest; plain "Data: CBSA and U.S. CBP" wording |
| R5 | Third-party hosts on click: jsDelivr and `extensions.duckdb.org` (667 KB extension) | low / medium | Pinned versions; the static view never depends on them; a plain error with the static result still shown |
| R6 | Wasm weight on phones: 7.7 MB over the wire, 34 MB uncompressed, about 38 s on the throttled profile (estimated); iOS Safari untested | medium / medium | Never auto-load; size and progress on the button; the static view is complete on its own; test at 390 px |
| R7 | Git growth on the `data` branch: 4,039 B of Parquet per run (measured), about 35–70 MB a year (estimated), plus the raw CBSA CSV (3,274 B per run before gzip, measured), at most about 29 MB a year (estimated) | low / low | Shallow checkout; yearly compaction if needed |
| R8 | Maintenance when Brett is busy | medium / low | A failed run commits nothing and redeploys the last good export with `status.json`; the failure issue alerts Brett; the page shows "Last successful run" and "Last attempt"; the homepage is static |
| R9 | Visitors misread the values | low / medium | The page says they are published wait estimates, not measured crossing times |
| R10 | Schema skew between the synced page and newer data | low / medium | A `schema_version` column in every file and in the manifest; reads with `union_by_name = true`; fall back to the synced snapshot on a mismatch |
| R11 | Local times at DST changes: 01:00–01:59 happens twice on 2026-11-01, and 02:00–02:59 doesn't exist on 2027-03-14 | high / medium (the first fold falls inside M1) | dbt unit tests for both dates; a written rule (the candidate instant not after `fetched_at` and closest to it); `tz_resolved_by` records which input decided |

### 14.9 How it appears on the site

**Homepage: one card, static.** Placed as the second full-row featured card, directly after
neuronet-arena (§5.5), so the half-width count stays even and no card is demoted. That placement
answers the odd-card question without a Decision.

- `article.project.project-featured` with `data-project="border-waits"`.
- A still screenshot in §7.9's sizes, `assets/img/border-waits-800.webp` and
  `assets/img/border-waits-1600.webp` (1600×1000), with `srcset` and `loading="lazy"`. Refreshed by
  hand when the page changes; never generated by a workflow.
- `.project-kind`: the Live demo badge + "SQL in your browser" (§8.8), so the line reads "Live demo ·
  SQL in your browser". The `.badge-live` sits outside the `h3` (§8).
- `h3`: "border-waits".
- One line: "An hourly pipeline over the wait estimates the Canadian and U.S. border agencies
  publish, tested with dbt. Query it with SQL in your browser." CBSA and CBP are named on the page
  and in its footer, not in the line.
- Chips: `dbt + DuckDB` · `Tests gate every deploy` · `CBSA + U.S. CBP open data`.
- Links: "Live demo" (`/border-waits/`, a running page, A12) then "Source" (the project repo).
- Nothing on the homepage fetches data, loads the engine or changes between visits. The card ships in
  the same PR as the page, never before.

**The page `/border-waits/`, top to bottom:**
1. The shared `.demo-shell` header (§8): "← Brett Adams" → `/#projects`, "border-waits", Résumé,
   Source, plus "Data" (full `https://` URL to the project's Pages).
2. The question first, as the line under the `h1`: "How long are the waits at the Windsor–Detroit
   crossings, by hour and direction?" Then one line on what it is, then a diagram of the flow (fetch
   → model → test → publish), per S06.
3. **Windsor–Detroit now:** a table for Ambassador Bridge, the Detroit–Windsor Tunnel and the Gordie
   Howe International Bridge, both directions, travellers and commercial, with "source updated"
   times. A closed lane reads "closed"; a missing value is never shown as 0.
4. **Typical wait by hour:** a small SVG drawn with plain JS from the all-days by-hour mart, with
   crossing, direction and lane pickers. The median line is drawn at n ≥ 7 readings and the p90 band
   at n ≥ 20 (**estimated**). Under the chart, "Based on N days of readings". A `<details>` data
   table holds the same numbers, and cells with too few readings say so.
5. **Pipeline status:** "Last successful run" and "Last attempt" (from `status.json`), test counts
   with warning messages, each source's freshness, and the "stale" badge rule.
6. **How it works:** four short blocks (Fetch, Model, Test, Publish), each with real numbers from
   `manifest.json`. Model links "Lineage and column docs" (dbt's static docs on the project's
   Pages). Publish says what a run costs, for example "Each run takes N s on a free runner and adds
   N KB" (H8).
7. **Query with SQL:** the sized button with a progress line. The preset queries are phrased as
   plain questions, and the first one is the page's question. "Show model SQL" loads the compiled
   SQL. The rebuild check rebuilds the hourly mart from the last 14 days of readings and shows
   "Matches the pipeline's build" or the difference.
8. Footer: "Built by Brett Adams · Data: CBSA and U.S. CBP · Contains information licensed under the
   Open Government Licence – Canada" with the licence link.

**Standing rules check**

| Rule | How it is met |
|---|---|
| Built from the project's actual code, never a mock-up | Every number on the page is pipeline output; the SQL runs over the published Parquet; the rebuild check runs dbt's compiled SQL |
| Nothing live on the homepage; each project once | One static card; the engine and data load only on `/border-waits/` |
| One line per project, facts as chips | One line, three chips |
| No blog | The page is a demo with short labels, not a post |
| Dark only, Plex, shared tokens | The `.demo-shell` and §7 tokens, copied into `web/styles.css` (§7.11) |
| No build step, plugin or deploy change in the site repo | A synced static folder and a card; the workflow lives in the project repo |

### 14.10 Decisions it raises (all in §18)

| Question | Default if Brett doesn't answer |
|---|---|
| Build a new data project, and which one | Brief queued; nothing built |
| Repo name | `[BRETT: repo name]`, suggested `border-waits-pipeline`; it must differ from the site folder `border-waits` |
| May the page fetch live data from the project's Pages at runtime? | Yes, with the synced fallback snapshot; nothing in the site's build or deploy changes. Alternative: snapshot only, re-synced by hand |
| Page in the site repo or wholly on the project's Pages | In the site repo, synced from `web/` |
| Pinned third-party hosts on click (jsDelivr, `extensions.duckdb.org`) | Accept; never self-host the 34 MB wasm in the site repo |
| Scope | All Canada–US crossings in both feeds, the three Windsor–Detroit crossings featured |
| Optional Bank of Canada FX join (M5) | No |
| Résumé line once it ships | None; the résumé is unchanged |
| Any personal line about why these crossings | None; `[BRETT: …]` only if Brett offers a true one |
| claims-check (candidate B) as a CI job | Not built |

### 14.11 MazeFinder and Sift

Both are placed in §5.5. In short: `/mazefinder/` follows its own handoff, becomes a half-width card
when it ships, and shares the demo shell; MazeFinder and border-waits are independent, and either can
ship first. Sift stays off the site unless Brett says yes, and then gets one `ul.also` line.

### 14.12 The full brief

[`handoff/border-waits-pipeline.md`](border-waits-pipeline.md) has the build plan for both repos: the
normalized schema, parsers, storage layout, dbt models and tests, export files, workflows, the page,
the homepage card, verification steps and the Decisions. It was checked against the fact sheet and the
standing rules for this spec. Delete it in the PR that adds the page.

## 15. File-by-file change list

Every file created, changed or deleted, by PR. The PRs are defined in §16. Paths are relative to the
repository root unless they name another repo. "Gated" PRs are opened only if Brett's answer calls
for them (§18). Their defaults leave every file in their rows untouched.

### 15.1 By PR

**PR 0. Measurement tools**

| Path | Action | What changes | Spec |
|---|---|---|---|
| `handoff/tools/measure.mjs` | change | `--a11y` mode (focus walk, target sizes, heading names from CDP, reflow at 320, skip link, obscured focus, `page_height`, `key_info`, the field formats in §16 PR 0); `--motion` mode (§9.5 checks 1–2); 320×640 in `SHOT_SIZES`; `EXACT_SECTIONS` replacing `DEFAULT_SECTIONS`; `desktop_cls` budget key; `origins` in perf results; usage comment | §10.15, §11.7, §13.3, §16 PR 0 |
| `handoff/tools/budget.json` | create | per-page budget, PR 0 values | §11.7 |
| `handoff/tools/contrast.py` | create | the 81-pair WCAG contrast script, verbatim | §7.3 |

**PR 1. Tokens, type scale and visual-only prefixes**

| Path | Action | What changes | Spec |
|---|---|---|---|
| `assets/css/site.css` | change | `:root` (`:4-24`) replaced by §7.1's token block at the top, between two marker comments: `:root` tokens, the ≤ 640 gutter, reduced-motion durations, the three metric-matched fallback faces (no Plex `@font-face` yet). Below it: literals → tokens; type, spacing, radius, z-index and motion tokens; smooth scroll opt-in; prefix `::before` rules deleted; `.prefix`, `.nowrap`, `.sr-only`; focus rule | §7.1, §7.2, §7.4, §7.6, §7.10, §9.1–9.2, §10.6 |
| `_layouts/default.html` | change | brand prefix span | §7.10 |
| `index.html` | change | prefix spans on the kicker, five section titles and four employers; `aria-hidden` arrow on "More on GitHub" | §7.10 |
| `mcp/index.html` | change | prefix spans on four h2s; `aria-hidden` arrow on the back link | §7.10 |

Only under §18 #49 (a), which Brett answers together with updating `CLAUDE.md:12`:
`assets/css/tokens.css` is created with the token block instead, `site.css` keeps no `:root`,
`_layouts/default.html` links `tokens.css` before `site.css`, and `README.md`'s layout table gains a
`tokens.css` row. By default none of these happen, and `CLAUDE.md:12` ("the tokens in
`assets/css/site.css`") stays true.

**PR 2. Self-hosted IBM Plex** (Decision; default ship)

| Path | Action | What changes | Spec |
|---|---|---|---|
| `assets/fonts/ibm-plex-sans-latin-var.woff2` | create | 40,240 B, byte for byte from Google Fonts (mobile UA) | §7.5 |
| `assets/fonts/ibm-plex-mono-latin-400.woff2` | create | 10,052 B | §7.5 |
| `assets/fonts/ibm-plex-mono-latin-500.woff2` | create | 10,060 B | §7.5 |
| `assets/fonts/ibm-plex-mono-latin-600.woff2` | create | 10,120 B | §7.5 |
| `assets/fonts/OFL.txt` | create | the SIL OFL 1.1 licence, 4,456 B | §7.5 |
| `assets/css/site.css` | change | the four Plex `@font-face` rules, inside the token block (`tokens.css` under §18 #49 (a)) | §7.1 |
| `_layouts/default.html` | change | Google preconnects and stylesheet removed (`:31-33`); Sans preload added | §7.5 |
| `README.md` | change | layout row for `assets/fonts/` | §16 PR 2 |

**PR 3. Header and phone section nav**

| Path | Action | What changes | Spec |
|---|---|---|---|
| `_layouts/default.html` | change | header markup (`:38-53`); `nav.nav-sections` row after the header; `main tabindex="-1"` | §8.1, §6.4 |
| `assets/css/site.css` | change | header and `.nav-sections` rules copied from the mock, which is authoritative (44 px links, 36 px header Résumé); backdrop 0.92; old hide rule (`:859-862`) replaced; `main:focus` | §8.1, §6.5 |

**PR 4. Hero and At a glance**

| Path | Action | What changes | Spec |
|---|---|---|---|
| `index.html` | change | hero (`:4-19`) rebuilt from the mock: status, kicker, h1, lede, buttons (new order), jump link, `div.glance` with Now, Before, Studying, Results and Works with ("Looking for" and "Based in" only once Brett answers) | §6.3–6.4, §8.2 |
| `assets/css/site.css` | change | hero grid, glance (Now and Works with hidden at ≤ 640), `.hero .actions` grid, `.hero-jump` hidden at ≤ 860, `.btn` `min-height: 44px` at every width; hero grid overlay (`:231-243`) removed | §6.5, §7.2, §8.11 |

**PR 5. Experience, tightened** (Decision; default ship)

| Path | Action | What changes | Spec |
|---|---|---|---|
| `index.html` | change | timeline (`:21-79`): `.job-head`, `.job-points`, and `details.job-more` with `data-item` only where PR 5's fit loop moves a bullet; the same 13 bullets as today, `:58` trimmed (§4.5). A `.section-lede` sentence only if Brett chooses §18 #6 (c) | §4.5, §8.4 |
| `assets/css/site.css` | change | `.job-points` replaces `.job > ul:not(.chips)`; `.job-more`; job and section padding | §8.4 |

**PR 6. Project cards and screenshots**

| Path | Action | What changes | Spec |
|---|---|---|---|
| `index.html` | change | five cards and `ul.also` (`:84-157`): kind label (§18 #6), badge out of the h3, `srcset`, named links with `sr-only` suffixes, MCP screenshot → `/mcp/`, new chips; the mcp-surface and PromptPal lines and the MCP servers chip (§18 #51); the NHL Arena line (§18 #36) | §8.5–8.12 |
| `assets/css/site.css` | change | 16:10 `.project-shot`, featured grid, `.project-kind`, `.links`, `.also` link targets and row padding, badge, `.also` grid, chips | §7.9, §8.5–8.10 |
| `assets/img/neuronet-arena-800.webp` | create | 800×500 | §7.9 |
| `assets/img/mcp-servers-800.webp`, `assets/img/mcp-servers-1600.webp` | create | 800 and 1600 wide | §7.9 |
| `assets/img/mcp-surface-800.webp`, `assets/img/mcp-surface-1600.webp` | create | 800 and 1600 wide | §7.9 |
| `assets/img/promptpal-800.webp`, `assets/img/promptpal-1600.webp` | create | 800 and 1600 wide | §7.9 |
| `assets/img/technest-800.webp`, `assets/img/technest-1600.webp` | create | 800 and 1600 wide | §7.9 |
| `assets/img/mcp-servers.webp` | delete | replaced by the two variants | §7.9 |
| `assets/img/mcp-surface.webp` | delete | same | §7.9 |
| `assets/img/promptpal.webp` | delete | same | §7.9 |
| `assets/img/technest.webp` | delete | same | §7.9 |
| `assets/img/neuronet-arena.webp` | unchanged | stays as the 1280w candidate | §7.9 |
| `handoff/tools/budget.json` | change | `/` `total_kb` 240, `image_kb` 130 | §11.7 |

**PR 7. Skills, education, contact, footer**

| Path | Action | What changes | Spec |
|---|---|---|---|
| `index.html` | change | skills, education (still `div#education`; date line "2022 – 2027 · Windsor, ON") and contact (`:161-191`) | §8.13–8.15 |
| `_layouts/default.html` | change | footer (`:57-66`): `p.footer-links`, `data-contact` on three links (Decision; default add) | §8.16, §13.7 |
| `assets/css/site.css` | change | footer and contact targets and layout | §8.15–8.16 |
| `assets/js/analytics-events.js` | change | **comment lines 5–7 only** ("#education" → "#skills", fact sheet C6). No code change | §13.7 |

**PR 8. `/mcp/` case-study template and the 404 page**

| Path | Action | What changes | Spec |
|---|---|---|---|
| `mcp/index.html` | change | the diagram moved up as a two-row `.flow-figure` ("At runtime", "In CI"); `.case-facts` chips; `.case-for` (the For line in §18 #14), the reworded sentence tail, "Limits", `.post-next` (copy Decision; default ship); 0.3.0 clause (Decision; default reword, after an `npm view` check); `pre` as a focusable region. Every other block keeps its place | §8.18–8.19, §16 PR 8 |
| `404.html` | change | three buttons; `aria-labelledby`; front-matter `description` | §8.17, §12.1 |
| `assets/css/site.css` | change | `.case-for`, `.flow-figure`, `.flow-row`, `.flow-label`, `.flow`, `.post-next`, `/mcp/` link targets, `pre[tabindex]` focus, `.not-found` padding | §8.17–8.19 |

**PR 9. mcp-surface shell and fixes**

| Path | Action | What changes | Spec |
|---|---|---|---|
| `assets/css/demo-shell.css` | create | §8.20's block, 4,640 B, 1,421 B gzipped (**measured**, §8.20): shell layout only, no tokens (it reads the page's own `:root`). `.skip-link`, `.sr-only`, `.prefix`, `:focus-visible`, `.demo-shell` and its parts, `.demo-how`, `.demo-foot`, `.footer-links`, `.chips`, and the ≤ 640 rules | §8.20 |
| `mcp-surface/index.html` | change | head: title, `og:title`, canonical, fonts, `demo-shell.css`, two `modulepreload` (`tokens.css` only under §18 #49 (a)); shell header; `main#main`; `.term` region; `#verdict` status; examples group; `.demo-how`; demo footer with `data-contact`; `<noscript>` inside `main`; GA include only if approved | §8.19–8.20, §11.5, §12.1, §13.7 |
| `mcp-surface/styles.css` | change | `:root` (`:4-22`) replaced by a copy of `site.css`'s token block (with the Plex `@font-face` rules and `/assets/fonts/…` URLs if PR 2 shipped); deleted, because `demo-shell.css` provides them: `:focus-visible` (`:30`), `.sr-only` (`:31`), the old header `.top`, `.back`, `.top-links` (`:35-42`) and footer `.foot` (`:232-233`); `h1::before` (`:46`) deleted for a span; literals → tokens; `--dim`; colour states instead of opacity; control edges; `.editor:focus-within`; 44 px controls at ≤ 640 | §7.3, §7.11, §8.20 |
| `mcp-surface/app.js` | change | `aria-pressed`; focus kept after rule re-render; "skipped" and "--call only" words | §8.20 fixes 3–5 |
| `README.md` | change | mcp-surface rebuild note (update the version text too; `styles.css` carries a copy of the token block); layout row for `demo-shell.css` | §16 PR 9 |
| `mcp-surface/checks.js` | **unchanged** | generated; never hand-edited | §0 |

**PR 10. SEO and sharing**

| Path | Action | What changes | Spec |
|---|---|---|---|
| `_config.yml` | change | `description` (`:3-5`) only; GA lines `:9-11` untouched | §12.1 |
| `_layouts/default.html` | change | JSON-LD block (`:12-29`): `affiliation` (D19), `worksFor.url`, `knowsAbout`, `email` | §12.3 |
| `assets/img/social-card.png` | change | 256-colour re-encode, same pixels to the eye (211,878 → 84,198 B, measured on a copy) | §12.2 |
| `mcp-surface/og.png` | change | same re-encode (109,961 → 39,783 B) | §12.2 |
| `assets/img/favicon.svg` | change | three colours → tokens | §12.5 |
| `assets/img/apple-touch-icon.png` | change | redrawn from the recoloured SVG, 180×180 | §12.5 |

**PR 11a. Upstream, in `brettadams0/neuronet-arena`** (Decision; default: a draft PR there)

| Path (that repo) | Action | What changes | Spec |
|---|---|---|---|
| `web/index.html` | change | shell header (absolute URLs), skip link, `main#main`, fonts and preload, title, canonical, `<th>` → `<td>`, prefix spans, footer order; optionally the "How it was built" link removed | §7.11, §8.20, §12.1 |
| `web/styles.css` | change | a copy of `site.css`'s token block (keeps demo-only tokens), shell rules copied, `@font-face`, focus on links, `summary` and canvas, `td.pending` → `--dim`, literals → tokens, `summary` ≥ 24 px | §7.3, §7.11, §8.20 |
| `web/og.png` | change | 256-colour re-encode (144,842 → 78,033 B, measured on a copy) | §12.2 |

**PR 11b. Re-sync `neuronet-arena/`**

| Path | Action | What changes | Spec |
|---|---|---|---|
| `neuronet-arena/**` | change | `rsync` from the merged upstream `web/` (`README.md:69-73`); the one local difference (`README.md:75-76`) re-applied only if it still exists, skipped if 11a's removal commit merged | §0, §16 PR 11b |
| `README.md` | change | only if the local difference is gone (`:75-76`) | §16 PR 11b |
| `handoff/tools/budget.json` | change | `/neuronet-arena/` `cls` 0.05 | §11.7 |

**PR 12. border-waits page and card** (gated; default not built)

| Path | Action | What changes | Spec |
|---|---|---|---|
| `border-waits/**` | create | copied from the project's `web/` with `rsync -a --delete --exclude node_modules --exclude test --exclude package.json --exclude package-lock.json --exclude tsconfig.json --exclude Makefile ../<repo>/web/ border-waits/`: `index.html`, `styles.css` (with its copy of the token block and shell rules, §7.11), `app.js`, `sql-console.js`, `fallback/summary.json`, `fallback/manifest.json`, `og.png` | §14.9; brief §2 |
| `assets/img/border-waits-800.webp`, `assets/img/border-waits-1600.webp` | create | card screenshot | §7.9 |
| `index.html` | change | second featured card, `data-project="border-waits"`, kind `Live demo · SQL in your browser` | §5.5, §8.5 |
| `README.md` | change | layout row and a sync section quoting the same `rsync` line | brief §2 |
| `handoff/tools/budget.json` | change | `/border-waits/` line; `/` image and total raised by the new file | §11.4, §11.7 |
| `handoff/border-waits-pipeline.md` | delete | the brief, once built | §14.12 |
| Project repo `brettadams0/[BRETT: repo name]` | create (Brett) | the pipeline, tests, workflow and `web/` | §14.4, §14.7 |

**PR 13. MazeFinder page and card** (gated on the MazeFinder repo's walls PR)

| Path | Action | What changes | Spec |
|---|---|---|---|
| `mazefinder/index.html`, `mazefinder/styles.css`, `mazefinder/app.js`, `mazefinder/og.png` | create | written here per `handoff/mazefinder-live-demo.md` part 2, with `demo-shell.css` and the shell; `styles.css` opens with a copy of the token block | §8.20, §12.1; the handoff |
| `mazefinder/wasm/<name>.js`, `mazefinder/wasm/<name>.wasm` | create | the only files copied from the MazeFinder repo's `web/` build output, by name; no `Makefile`, test or package file | §16 PR 13 |
| `assets/img/mazefinder-800.webp`, `assets/img/mazefinder-1600.webp` | create | card screenshot | §7.9 |
| `index.html` | change | MazeFinder card after mcp-surface; TechNest gets `project-wide`; MazeFinder's `ul.also` row removed | §5.5, §8.7, §8.9 |
| `assets/css/site.css` | change | `.project-wide`, which lands in this PR | §8.7, §8.21 |
| `README.md` | change | layout row and a sync section naming the two copied files (the handoff) | the handoff |
| `handoff/tools/budget.json` | change | `/mazefinder/` line; `/` image raise | §11.4 |
| `handoff/mazefinder-live-demo.md` | delete | the handoff, once built (`:4-5`) | the handoff |

**PR 14. Analytics additions** (gated; default none)

| Path | Action | What changes | Spec |
|---|---|---|---|
| `index.html` | change | N1: `id="hero"`; N2: `section#education`; N3: `data-nav="hero"` on `p.hero-jump` | §13.8 |
| `_layouts/default.html` | change | N3: `data-nav` on both navs | §13.8 |
| `assets/js/analytics-events.js` | change | N3 `nav_click` (`target_section`, `nav`), N4 `job_more_open` (`item`); N2 restores the `#education` comment | §13.8 |
| `handoff/tools/measure.mjs` | change | `EXACT_SECTIONS` gains approved ids; expected events; clicks on `[data-nav]` links and `details.job-more` summaries | §13.8, §16 PR 14 |
| `README.md` | change | Analytics paragraph: new events and parameters | §13.9 |

**PR 15. Brett's answers into site copy** (gated, repeatable)

| Path | Action | What changes | Spec |
|---|---|---|---|
| `index.html` | change | only the lines an answer touches (status, glance rows, the bullet each number measures, titles, degree, education dates) | §4.5, §6.3, §8.2–8.4 |
| `_config.yml` | change | `tagline` / `description`, only if the target-roles answer changes them | §12.1 |
| `_layouts/default.html` | change | JSON-LD `jobTitle` / `worksFor` when the Geotab dates change | §12.3 |
| `assets/img/social-card.png` | change | regenerated only when its text changes | §12.2 |

**PR 16. Résumé wording** (gated; default none)

| Path | Action | What changes | Spec |
|---|---|---|---|
| `resume/resume.tex` | change | only the approved lines | §18 #42–47 |
| `assets/resume/Resume_BrettAdams.pdf` | change | recompiled by Brett on Overleaf; **same path** | §0, §13.4 |

**PR 17. Sift line** (gated; default no)

| Path | Action | What changes | Spec |
|---|---|---|---|
| `sift/**` | change or delete | re-sync from upstream, or remove the mirror (sub-decision) | §5.5; fact sheet C5 |
| `index.html` | change | one `li[data-project="sift"]` row in `ul.also` | §8.9 |

**PR 18. Budget in CI** (gated; default no)

| Path | Action | What changes | Spec |
|---|---|---|---|
| `.github/workflows/ci.yml` | change | the build job uploads `_site` as an artifact; a second job, `budget` (`needs: build`), downloads it, installs Playwright (pinned) and runs `measure.mjs --budget … --runs 1` | §16 PR 18 |
| `handoff/tools/budget-ci.json` | create | size keys only | §16 PR 18 |

### 15.2 Files that no PR touches

- **Never hand-edited:**
  - `mcp-surface/checks.js`, which is generated (`README.md:78-92`);
  - every file under `neuronet-arena/`, except through PR 11b's `rsync` and re-applying the one
    local difference documented in `README.md:75-76`;
  - `CLAUDE.md`, which is Brett's to change (§18 #49, D22).
- **Unchanged by default:**
  - `resume/resume.tex` and the PDF (PR 16 only);
  - `sift/**` (PR 17 only);
  - `.github/workflows/ci.yml` (PR 18 only).
- **Unchanged in every PR:**
  - `_includes/analytics.html`, `.github/scripts/check-links.mjs`, `robots.txt`;
  - `mcp-surface/examples.js` and `mcp-surface/THIRD_PARTY_LICENSES.txt`;
  - the `_config.yml` lines other than `description` (and `tagline` in PR 15);
  - everything under `handoff/research/` and `handoff/mocks/`.
- **Never created:**
  - a `Gemfile` or `Gemfile.lock`;
  - a `package.json` or lockfile outside `handoff/` (none is needed, even there);
  - `node_modules`;
  - a folder named like one of Brett's project repos that has its own Pages site, such as
    `google-workspace-mcp-policy/` or the border-waits data repo's name (§5.1).

### 15.3 New files at a glance

| New path | PR | Size | Published? |
|---|---|---|---|
| `handoff/tools/budget.json` | 0 | under 2 KB (**estimated**) | no (`handoff/` excluded) |
| `handoff/tools/contrast.py` | 0 | about 6 KB (**estimated**, §7.3's script) | no |
| `assets/fonts/*.woff2` (4) and `OFL.txt` | 2 | 70,472 B and 4,456 B (measured) | yes |
| `assets/img/*-800.webp` (5), `*-1600.webp` (4) | 6 | 105,646 B and 223,240 B (measured, §7.9) | yes |
| `assets/css/demo-shell.css` | 9 | 4,640 B, 1,421 B gzipped (**measured**, §8.20) | yes |
| `border-waits/**`, two images | 12 | per the brief | yes, if built |
| `mazefinder/**`, two images | 13 | per the handoff | yes, if built |
| `handoff/tools/budget-ci.json` | 18 | under 1 KB (**estimated**) | no |

**Not a new file by default: the token block.** PR 1 puts it at the top of `assets/css/site.css`, and
it adds about 5.4 KB to that file (measured as a separate file: 5,415 B, 1,788 B gzipped, §7.1).
`assets/css/tokens.css` is created only under §18 #49 (a). `mcp-surface/styles.css`,
`mazefinder/styles.css` and the two synced demos carry copies of the block.

## 16. Implementation plan

The redesign ships as small PRs against `main`. Each one leaves CI green and the site whole, so the
work can stop after any of them. Tools and tokens come first, then the homepage from the top down,
then the inner pages, then the upstream demo. The gated PRs (12–18) wait for an answer from Brett.
Every file each PR touches is listed again in §15.

### 16.1 The PRs

| PR | Title | Goal | Depends on | Gate (§18; the default lets it proceed) | Size (estimated) |
|---|---|---|---|---|---|
| 0 | Measurement tools | `budget.json`, `--a11y`, `--motion`, `desktop_cls`, `origins`, exact `section_id` lists, 320 px shots, `contrast.py`, so every later PR is measured the same way | — | — | M, 6–8 h |
| 1 | Tokens, type scale and visual-only prefixes | the new tokens at the top of `site.css`; no literals; screen readers stop hearing `## `, `> `, `// `, `@ ` | 0 | #2 visual system (default ship); #49 where the tokens live (default `site.css`) | M, 6–8 h |
| 2 | Self-hosted IBM Plex | no third-party font requests; no font-swap shift | 1 | #3 fonts (default self-host) | S, 2–3 h |
| 3 | Header and phone section nav | four section links on phones, 44 px targets, no JS | 1 | — | S–M, 3–4 h |
| 4 | Hero and At a glance | the first screen as a résumé header, built from the mock | 1, 3 | — | M, 4–6 h |
| 5 | Experience, tightened | Projects no lower than today at 390 with Experience still first | 4 | #6 Experience trim (default ship; summary sentence default no), #7 names (default ship) | M, 4–6 h |
| 6 | Project cards and screenshots | kind label, named links, `srcset`; images −66% | 1 | #6 kind labels, #36 NHL Arena line, #51 card copy (default ship) | M–L, 6–8 h |
| 7 | Skills, education, contact, footer | 44 px footer targets; footer contact links measured | 1 | #12 footer `data-contact` (default add), #13 | S, 2–3 h |
| 8 | `/mcp/` case-study template and the 404 page | diagram in the first screen, axe clean, a 404 that leads somewhere | 1 | #14 `/mcp/` copy (default ship); #15 npm 0.3.0 wording (default reword) | M, 4–5 h |
| 9 | mcp-surface shell and fixes | the shared demo shell; axe clean; keyboard fixes | 1, and 2 if #3 is yes | #17 GA on demo pages (default none); #18 version chip (default chip); #49 (default: a copy of the tokens in `styles.css`) | M–L, 6–8 h |
| 10 | SEO and sharing | homepage description, JSON-LD, lighter social images, token favicon | 1 | #19 D19 (default `affiliation`) | S, 2–3 h |
| 11a | Upstream: neuronet-arena shell, fonts, focus and contrast (in the neuronet-arena repo) | the favourite demo gets the shell and loses its 0.1643 CLS | 2, 9 | #20 upstream change (default: draft PR for Brett) | M, 5–7 h |
| 11b | Re-sync `neuronet-arena/` | bring 11a here, tighten its budget | 11a merged | — | S, 1–2 h |
| 12 | border-waits page and card | the data-engineering demo, once it exists | 6, 9; 21 days of data in the project's `data` branch | #21 new project (**default: not built**) | site S–M, 3–5 h |
| 13 | MazeFinder page and card | the queued handoff, fitted to this spec | 6, 9; MazeFinder repo walls PR | #22, #23 (default `.project-wide`, sourced line) | site M, 6–10 h |
| 14 | Analytics additions | only the items Brett approves from §13.8 | 3–5 | #24–27 (**default: none**) | S, 1–3 h |
| 15 | Brett's answers into site copy | replace fallbacks with Brett's facts | 4, 5 | #30–36 Brett's facts (default: fallbacks stay) | S, 1–2 h per batch |
| 16 | Résumé wording | approved wording changes to `resume.tex` and the PDF | — | #42–47 résumé points (**default: none**) | S, 1–2 h, plus Brett's Overleaf compile |
| 17 | Sift line | one `ul.also` row | 6 | #29 Sift (**default: no**) | S, 1–2 h |
| 18 | Budget in CI | run the size budget on every PR | 0 | #28 CI change (**default: no**) | S, 2–3 h |

- **Total for PRs 0–11b:** about 51–71 hours (**estimated**, the sum of the rows).
- **Merge order:** by number. PR 9 touches no homepage file, so it may merge any time after PR 1, or
  after PR 2 if §18 #3 is yes (its font preload needs the files). It fixes the only High
  accessibility issue (audit issue 3), so moving it up is fine.
- **Numbering:** PRs 1–11 keep the numbers §7–§10 cite. Two additions:
  - PR 0 holds the measurement tools (`measure.mjs --a11y`, `contrast.py`), so PR 1 can be measured
    with them. §7.3, §10.1, §10.15 and §10.16 place them in PR 0 too;
  - PR 11 is split into 11a (the neuronet-arena repo) and 11b (the re-sync here).
- **`.project-wide`** lands in PR 13, the first PR with an odd number of half-width cards (§8.7,
  §8.21).

### 16.2 What every PR does

**Branch and scope.**
- Branch from an up-to-date `origin/main`. Open the PR against `main`, which is the only branch CI
  runs on (`.github/workflows/ci.yml:3-7`).
- Change only the files the PR lists (§15). Never:
  - edit `neuronet-arena/` (except PR 11b's `rsync` and re-applying the one local difference
    documented in `README.md:75-76`) or `mcp-surface/checks.js`;
  - add a `Gemfile`, a `package.json` outside `handoff/`, or `node_modules`;
  - edit `CLAUDE.md`;
  - change the GA lines of `_config.yml` (`:9-11`) or the résumé path.

**The before state.** Build `main` once per PR, and measure it with the branch's own copy of
`measure.mjs`:

```sh
git fetch origin && git worktree add /tmp/before origin/main
(cd /tmp/before && jekyll build -d /tmp/site-before)
node handoff/tools/measure.mjs --site /tmp/site-before --path / --path /mcp/ --path /404.html \
  --path /mcp-surface/ --path /neuronet-arena/ --shots --a11y --out /tmp/shots-before --json /tmp/before.json
git worktree remove /tmp/before
```

PR 0 has no `--a11y` on `main` yet, so its before state is `handoff/research/baseline/measure-*.json`.

**Comparing two builds.** jekyll-sitemap writes each static file's `<lastmod>` from its mtime (the
résumé PDF, `mcp-surface/index.html` and `neuronet-arena/index.html` today), and a fresh
`git worktree` gives every file a new mtime. So two builds of the same commit always differ in those
`sitemap.xml` lines. Every before/after build comparison in this plan uses this pair, and both
halves print nothing when no published file changed:

```sh
diff -r -x sitemap.xml /tmp/site-before _site && diff <(grep -v '<lastmod>' /tmp/site-before/sitemap.xml) <(grep -v '<lastmod>' _site/sitemap.xml)
```

**The standard verification (V).** Every PR runs all of it and pastes the summary lines into the
PR description.

```sh
# V1  what CI runs
jekyll build && node .github/scripts/check-links.mjs

# V2  screenshots, axe, a11y, budget and analytics on the PR's build
node handoff/tools/measure.mjs --site _site --path / --path /mcp/ --path /404.html \
  --path /mcp-surface/ --path /neuronet-arena/ \
  --shots --axe --a11y --perf --budget handoff/tools/budget.json --analytics --dnt --runs 3 \
  --out /tmp/shots-after --json /tmp/after.json

# V3  static checks: each prints nothing
grep -rn "\[BRETT" _site
grep -rn "G-EBYB5MK388" _site
grep -rnoE 'Resume_BrettAdams\.pdf[^"]+"' _site --include=*.html
grep -nE "@keyframes|animation:|transition:[^;]*(all|width|height|margin|padding|top|left|transform)|transition:[^;]*[0-9](ms|s)[ ,;]" assets/css/*.css mcp-surface/styles.css   # from PR 1 on; until PR 9 the one allowed hit is mcp-surface/styles.css:206
git diff --name-only --diff-filter=A origin/main...HEAD | grep -E '(^|/)(Gemfile(\.lock)?|package(-lock)?\.json)$' | grep -v '^handoff/'
git ls-files | grep -E '(^|/)node_modules/'
find . -path ./.git -prune -o -path ./handoff -prune -o \( -name 'package*.json' -o -name 'Gemfile*' -o -name node_modules \) -print

# V5  motion (§9.5 checks 1 and 2, at 390 and 1440)
node handoff/tools/measure.mjs --site _site --path / --path /mcp/ --path /404.html --motion --json /tmp/motion.json
```

- **V2's exit code is 1 until PR 11b,** because the known `--a11y` failures below remain until then.
  Read its summary lines instead:
  - the `budget` line says `PASS`;
  - every `analytics` and `dnt` line says `PASS`;
  - each `a11y FAIL <path> <width> <check>: <detail>` line matches a row of the known list.
- **V3's last three lines** check the branch and the tree, not the working copy: a package file
  added through a commit or an `rsync`, or a tracked `node_modules/`, shows up even after the
  builder commits. (`node_modules/` is in `.gitignore:5`, so `git status` never shows it.) The
  first `git diff` needs the branch to have at least one commit.
- **V4 Screenshots.** Compare `/tmp/shots-before` and `/tmp/shots-after` side by side, at 320, 390,
  768 and 1440, fold and full page. These are the sizes `measure.mjs --shots` takes once PR 0 adds
  320×640 (`handoff/tools/measure.mjs:81`). List every visible difference in the PR description. A
  difference the PR's goal doesn't explain is a bug.
- **V5 Motion.** `--motion` (PR 0) runs §9.5's two checks: `getAnimations()` returns 0 after load,
  and under `reducedMotion: 'reduce'` nothing has a transition or animation and `scroll-behavior`
  is `auto`. It prints `motion PASS` or one `motion FAIL` line per page and width.
- **V6 Analytics.** The counts in §13.5 for the PR's stage. GA requests stay blocked: never
  `--allow-ga`. GA DebugView is Brett's check after a deploy (§13.10).

**Common acceptance**, in addition to each PR's own list:
1. CI is green: `actions/jekyll-build-pages` plus `check-links.mjs`.
2. V3 prints nothing, apart from the one motion-grep line allowed until PR 9.
3. Budget `PASS` with the PR's `budget.json`.
4. axe:
   - 0 critical and 0 serious on `/` and `/404.html` at both sizes;
   - on `/mcp/` and `/mcp-surface/`, no violation beyond the baseline until PRs 8 and 9 clear them;
   - no new moderate or minor violation on a page that had none (§10.1).
5. `analytics PASS` and `dnt PASS` on `/`, `/mcp/` and `/404.html`.
6. On `/`, the `section[id]` list is exactly `experience, projects, skills, contact`, and on `/mcp/`
   and `/404.html` it is empty (§13.3). From PR 0 on, `--analytics` fails the run on any other id.
7. `--a11y` and `--motion` failures are a subset of the known list below, minus the rows already
   cleared.
8. At 320, 390, 768 and 1440: `document.documentElement.scrollWidth <= innerWidth` on every gated
   page (`reflow_320` and the screenshots).

**Known `--a11y` and `--motion` failures on today's site.** These are allowed until the PR that
clears them (measured in the baseline, `[brettadams0:measure-<page>.json.viewport.<size>.small_targets_24]`,
except where a row says otherwise):

| Failure | Pages | Cleared by |
|---|---|---|
| Heading names with `## ` or ` @ ` ("## Experience", "Data Analyst Intern @ Geotab") | `/`, `/mcp/` | PR 1 |
| `--motion` check 2: a raw-duration transition under `reducedMotion: 'reduce'` (`site.css:172`, `:436`), if it fails on `main` (not measured in the baseline) | `/`, `/mcp/`, `/404.html` | PR 1 |
| `a.site-name` 19 px tall; desktop `a.nav-section` ×4 22 px | `/`, `/mcp/`, `/404.html` | PR 3 |
| `skip_link`: after Enter, focus stays on `body`, because `main` has no `tabindex` (measured with Playwright on a local build, 390 and 1440) | `/`, `/mcp/`, `/404.html` | PR 3 |
| `ul.also` "MazeFinder" and "More on GitHub →" 19 px | `/` | PR 6 |
| Footer "GitHub", "LinkedIn" 18 px | `/`, `/mcp/`, `/404.html` | PR 7 |
| "← Brett Adams" 17 px; `.also-name` links ×3 19 px at 390 | `/mcp/` | PR 8 |
| `a.back` 17 px, "npm" and "Source on GitHub" 22 px; h1 name starts with ">"; no skip link | `/mcp-surface/` | PR 9 |
| `a.back`, "Source on GitHub", `summary` "Data table" 19 px; h1 name starts with ">" (`neuronet-arena/styles.css:68`'s `h1::before`); no skip link; links without the 2 px ring | `/neuronet-arena/` | PR 11b (not gated before) |
| `reflow_320`, if it fails on `main` (not measured in the baseline) | any | PR 3 or PR 4 must clear it |

**The PR description** holds:
- the goal, in one line;
- the files changed;
- the measured numbers (V1–V2 summary lines, with before and after for every budget key that
  moved);
- the screenshot differences;
- the known `--a11y` and `--motion` failures that remain;
- each §18 Decision the PR applies, and the answer used: Brett's, or the default.

**Rollback.** Every PR is one squash-merged commit, so `git revert <sha>` on `main` undoes it. Pages
rebuilds within minutes (**estimated**). Cached CSS and HTML can mix for up to 10 minutes
(`max-age=600`, measured, §7.5). A revert has no data step:
- GA keeps the same ids and events;
- no PR deletes anything a revert can't restore, since deleted images come back with the revert.

One exception: a file that a later page links by root-relative URL.
- The four `assets/fonts/*.woff2` files (PR 2) are linked from `mcp-surface/` (PR 9), the synced
  `neuronet-arena/` (PR 11b), `/border-waits/` and `/mazefinder/` (PRs 12–13).
- `assets/css/demo-shell.css` (PR 9), and `assets/css/tokens.css` (PR 1) only under §18 #49 (a), are
  linked from `mcp-surface/` and `/mazefinder/`.
- Once a later page links the file, a revert keeps the file and undoes only the other hunks, or
  `check-links.mjs` fails CI. The synced `neuronet-arena/` can't be fixed in this repo. The PR 1, 2
  and 9 rollback notes say which hunks.

---

### PR 0. Measurement tools

**Goal.** Commit the tools that every later PR uses, before any site change.

**Files.**
- `handoff/tools/measure.mjs` (change)
- `handoff/tools/budget.json` (create)
- `handoff/tools/contrast.py` (create)

**Changes.**
1. **`--a11y` mode,** with §10.15's checks. This PR fixes the field formats; §10.15 lists what is
   checked.
   - **Fields,** with the same names as the baseline JSON, in these formats:

     ```json
     {
       "page": { "headings": ["H1 Brett Adams", "H2 EXPERIENCE", "H3 Data Analyst Intern at Geotab"] },
       "viewport": { "390": {
         "focus_walk": ["Skip to content [solid 2px rgb(255, 180, 84)]", "brett adams [solid 2px rgb(255, 180, 84)]"],
         "small_targets_24": [{ "el": "a.site-name", "text": "brett adams", "w": 117, "h": 19, "inline": false, "top": 20, "bottom": 39, "height": 19 }],
         "targets_under_44": 27, "targets_total": 34,
         "page_height": 6346,
         "key_info": { "resume": { "el": "a.nav-resume", "text": "Résumé", "top": 11, "bottom": 47, "height": 36, "screens_to_reach": 0, "in_first_screen": true }, "shipped_work": null },
         "checks": { "reflow_320": { "pass": true }, "skip_link": { "pass": false, "detail": "activeElement BODY after Enter" }, "obscured": { "pass": true, "detail": [] } }
       } }
     }
     ```

     The `viewport` values are today's, from `[brettadams0:measure-home.json.viewport.390]`
     (**measured**), except `shipped_work`, which shows a `null` item, and `checks`, which shows the
     shape. `page.headings` shows the CDP form after PR 1.
     - `page.headings`: `"H<level> "` plus the accessible name from CDP
       `Accessibility.getFullAXTree`, the source of truth that §7.10 and PR 4 use. Not
       `ariaSnapshot()`, which computes names its own way (measured on today's build: `## Experience`
       from `ariaSnapshot()`, `## EXPERIENCE` from CDP). CDP applies CSS `text-transform`, so
       compare section titles case-insensitively.
     - `focus_walk`: one string per Tab stop, `"<CDP name> [<outline-style> <outline-width> <outline-color>]"`,
       the baseline's format. Tab is pressed up to 120 times.
     - `small_targets_24`: every visible link, button, `summary`, `select`, `textarea` and
       `[tabindex="0"]` with `w < 24` or `h < 24`. `inline` is `true` for an `a` whose parent `p`
       or `li` holds other text nodes. Inline targets are listed but never fail (SC 2.5.8's
       inline exception).
     - `targets_under_44` and `targets_total` stay integers, as in the baseline.
     - `key_info.<item>` for `current_role` (`.hero .lede`), `resume` (`.site-header .nav-resume`),
       `contact` (first `[data-contact]`), `best_project`
       (`[data-project="neuronet-arena"] .project-shot`), `shipped_work` (first `.badge-live`) and
       `projects_heading` (`#projects-title`). Each item is
       `{el, text, top, bottom, height, screens_to_reach, in_first_screen}`, computed with §4.3's
       formula (the baseline's names, `[brettadams0:measure-home.json.viewport.390.key_info]`).
       An item is `null` when its selector matches nothing, as on every demo page.
     - `page_height`, which PR 5 and §13.6 need.
     - `checks.reflow_320`, `checks.skip_link` and `checks.obscured`, each `{pass, detail}`, as
       §10.15 defines them.
   - **The baseline's `page.headings` are `innerText`** ("H2 EXPERIENCE", "H3 Data Analyst Intern
     Geotab"), so they won't diff equal with CDP names. Compare those by hand. Every other field
     diffs directly.
   - **Output:** each failure prints as one line, `a11y FAIL <path> <width> <check>: <detail>`, so
     it can be matched against 16.2's known list.
   - **Exit code 1** on the §10.15 conditions.
   - **Mode list:** `--a11y` joins the modes accepted by the "at least one mode" check
     (`measure.mjs:111`).
2. **`--motion` mode.** §9.5 checks 1 and 2 at 390×844 and 1440×900, writing
   `motion.<width>.animations` (`document.getAnimations().length` after `networkidle` and
   `document.fonts.ready`), `motion.<width>.reduced_moving` (§9.5's list, under
   `reducedMotion: 'reduce'`) and `motion.<width>.reduced_scroll_behavior`. It prints `motion PASS`,
   or one `motion FAIL <path> <width>: <detail>` line, and exits 1 unless `animations` is 0,
   `reduced_moving` is `[]` and the scroll behaviour is `auto`. It also joins the "at least one
   mode" check.
3. **320×640 screenshots.** Add `[320, 640]` to `SHOT_SIZES` (`:81`), so `--shots` covers §16.2's
   reflow width and PR 3's row-fit check without a scratch script.
4. **Exact `section_id` lists** (§13.3). Replace `DEFAULT_SECTIONS` (`:84`) with
   `const EXACT_SECTIONS = { '/': ['experience', 'projects', 'skills', 'contact'], '/mcp/': [], '/404.html': [] };`.
   `--expect-sections` overrides it for one run. In `analytics()` (`:364-430`), for a path in the
   map, set `pass = false` and print `unexpected section_id: <id>` when `raw.dom.tracked_sections`
   (compared order-insensitively) differs from the list, or when `section_view` fires an id outside
   it. Missing ids still fail as today (`missing_sections`). A path not in the map, such as a demo,
   is not checked.
5. **`desktop_cls` budget key:** the three-line change in §11.7.
6. **`origins` field.** In `tally()` (`:287-301`), add
   `origins: [...new Set(list.map((r) => new URL(r.url).origin))]` to the returned object. `list`
   holds only successful requests, so blocked GA requests are not in it.
7. **Usage comment** (`:25-59`): add `--a11y`, `--motion`, the `desktop_cls` key, the 320×640 size,
   `EXACT_SECTIONS`, and an example line `node handoff/tools/measure.mjs --site _site --path / --a11y`.
   Note there that `--path /no/such/page/` against `--site` serves `404.html` with status 404
   (`:158`), which PR 8 uses.
8. **`handoff/tools/budget.json`:** §11.7's file with the PR 0 values (`/` `total_kb` 420 and
   `image_kb` 330; `/neuronet-arena/` `cls` 0.2).
9. **`handoff/tools/contrast.py`:** §7.3's script, verbatim, under a first line
   `# Spec section 7.3. Run: python3 -I handoff/tools/contrast.py`.

**Acceptance.**
- `node handoff/tools/measure.mjs --help` lists `--a11y` and `--motion`, and the budget key list
  includes `desktop_cls`.
- On today's `main` build:
  - the V2 command prints `budget (handoff/tools/budget.json): PASS`;
  - `--a11y` writes every field above, in the formats above;
  - its `a11y FAIL` lines, and V5's `motion FAIL` lines, match 16.2's known list row for row: no
    line outside the list, and no row without a line except the two marked "if it fails";
  - `key_info.projects_heading.bottom` is 2,513 at 390 and 1,720 at 1440 (±2 px of the baseline);
  - `--analytics` passes on `/`, `/mcp/` and `/404.html`;
  - on a scratch copy of `_site` whose `index.html` gains `<section id="x">`, `--analytics` prints
    `unexpected section_id: x` and exits 1.
- `--shots` writes 320×640 files next to the other three sizes.
- `python3 -I handoff/tools/contrast.py` prints 81 rows and `6 pairs below their bar`, all of them
  TODAY rows (§7.3).
- `_site` is byte-identical before and after, because `handoff/` is excluded (`_config.yml:30`):
  16.2's build comparison prints nothing, and so does
  `git diff --name-only origin/main...HEAD | grep -v '^handoff/'`.
- V3's three tree checks print nothing.

**Verification.** V1, V2, V3 and V5 on `main`'s build. Compare the `--a11y` JSON with
`handoff/research/baseline/measure-home.json.viewport`, except `page.headings` (see above).

**Rollback.** Revert. Nothing on the site uses these files.

**Size.** M, 6–8 h (**estimated**).

---

### PR 1. Tokens, type scale and visual-only prefixes

**Goal.**
- The new tokens sit at the top of `assets/css/site.css`, where `CLAUDE.md:12` says the tokens live
  (§18 #49, default).
- `site.css` loses every literal §7 replaces.
- The terminal prefixes stay on screen but leave every accessible name (audit issue 6).

**Gate.** §18 #2 (default ship) and §18 #49 (default: the tokens stay in `site.css`).

**Files.** `assets/css/site.css`, `_layouts/default.html`, `index.html`, `mcp/index.html` (change).
Under §18 #49 (a) only: `assets/css/tokens.css` (create) and `README.md` (change).

**Changes.**
1. **The token block, at the top of `site.css`.** Replace `:root` (`:4-24`) with §7.1's block
   **without** the four `IBM Plex …` `@font-face` rules, which PR 2 adds. Put it between two marker
   lines, which the checks below and the demo copies (PR 9, PR 13, §7.11) use to find it:
   `/* ---- tokens (§7.1). Demo pages carry a copy of this block (§7.11). ---- */` and
   `/* ---- end tokens ---- */`.
   - It keeps the `:root` block, the `≤ 640 px` gutter rule, the reduced-motion durations and the
     three `… Fallback` faces.
   - Those faces use `local()` only, so they shorten the font swap even while Google Fonts still
     loads.
   - The `:root` block and font faces add about 5.4 KB to `site.css` (measured as a separate file,
     5,415 B, 1,788 B gzipped, §7.1).
   - `CLAUDE.md:12` stays true, and `README.md`'s `site.css` row ("All homepage styles (dark only,
     same tokens as the demos)") needs no change.
   - **Only if Brett answers §18 #49 with (a):** the same block goes into a new
     `assets/css/tokens.css` instead, and `site.css` keeps no `:root`.
     - `_layouts/default.html` links it directly before the `site.css` link (`:34`):
       `<link rel="stylesheet" href="{{ '/assets/css/tokens.css' | relative_url }}" />`.
     - `README.md`'s layout table gains
       `| assets/css/tokens.css | Shared tokens and font faces, linked by the site and the demos |`.
     - Brett updates `CLAUDE.md:12` in the same change. The builder never edits `CLAUDE.md`.
2. **`_layouts/default.html`:** the brand (`:42`) becomes
   `<a class="site-name" href="{{ '/' | relative_url }}"><span class="prefix" aria-hidden="true">&gt; </span>brett adams</a>`.
3. **`assets/css/site.css`,** below the token block:
   - Replace literals with tokens per the §7.2 table: `--good-text`, `--on-amber`, `--amber-line`,
     `--amber-wash`, `--good-line`, `--good-wash`, `--radius` (now 10 px), `--radius-sm`,
     `--radius-pill`, `--z-header`, `--z-skip`, `--container`, `--container-post`, `--gutter`.
   - `scroll-padding-top: calc(var(--header-h) + var(--space-4))` replaces `72px` (`:35`).
   - Font sizes per §7.4's "Replaces" column. Line heights and tracking per §7.2.
   - Layout spacing: same-pixel renames only (§7.6's PR 1 rule); every other literal waits for its
     owner PR in §7.6's table (PRs 3–8).
   - Transitions (`:172`, `:436`) per §9.1–9.2, for example
     `transition: background-color var(--dur-fast) var(--ease-out), border-color var(--dur-fast) var(--ease-out);`.
     Never `all`, never a raw duration.
   - Smooth scrolling: replace `:34` and `:38-42` with §9.2's
     `@media (prefers-reduced-motion: no-preference) { html { scroll-behavior: smooth; } }`.
   - Delete the prefix `::before` rules: `:121-124` (brand), `:278-281` (kicker), `:323-326`
     (section titles), `:394-397` (`.job-org`) with its `≤ 640` override (`:917-919`), and
     `:683-688` (`/mcp/` h2).
   - Add §7.10's rules: `.prefix`, `.kicker .prefix`, `.post-body h2 .prefix`,
     `@media (max-width: 640px) { .job-org .prefix { display: none; } }`, `.nowrap`, `.sr-only`.
   - Focus: §10.6's block, using `--focus`.
4. **`index.html`, prefix spans** (§7.10, §8.0: an `aria-hidden` `span.prefix` with
   `white-space: pre`):
   - kicker `:8` → `<p class="kicker"><span class="prefix" aria-hidden="true">// </span>data &amp; software engineering</p>`;
   - section titles `:23`, `:83`, `:164`, `:173`, `:185`: put `<span class="prefix" aria-hidden="true">## </span>`
     before each heading's text;
   - employers `:27`, `:41`, `:54`, `:68`:
     `<span class="job-org"><span class="prefix" aria-hidden="true">@ </span><span class="sr-only">at </span>Geotab</span>`
     (and the same for each employer);
   - `:156`: `More on GitHub &rarr;` → `More on GitHub<span aria-hidden="true">&nbsp;→</span>`.
5. **`mcp/index.html`:**
   - `:10`: `&larr; Brett Adams` → `<span aria-hidden="true">←&nbsp;</span>Brett Adams`;
   - h2s `:28`, `:68`, `:84`, `:152`: the `## ` span before the text.

**Acceptance.**
- `site.css` opens with the token block: `sed -n '/^\/\* ---- tokens/,/end tokens/p' assets/css/site.css`
  prints §7.1's block minus the four Plex faces.
- Below the block, no `:root` is left: `sed '1,/end tokens/d' assets/css/site.css | grep -c ':root'`
  prints `0`. (Under §18 #49 (a): `grep -c ':root' assets/css/site.css` prints `0`, and
  `tokens.css` holds the block.)
- `sed '1,/end tokens/d' assets/css/site.css | grep -nE '#[0-9a-fA-F]{3,8}\b'` prints only four
  lines (the token values above the marker are the one allowed place for hex):
  - the grid overlay's two `mask-image` `#000` stops (`:240-241` today), which PR 4 deletes;
  - the `@media print` block's `#fff` and `#000` (`:933-934` today), which stay, because print is
    not the dark screen theme.
  The literals §7.2 keeps (washes, glows, header backdrop) are all `rgba()`.
- `page.headings` on `/`: "Brett Adams", "Experience", "Data Analyst Intern at Geotab", "Data
  Management Associate, Co-op at Ontario Ministry of Transportation", "Digital Solutions, Co-op at
  CIMCO Refrigeration", "Digital Solutions at CIMCO Refrigeration", "Projects", …
  - Chrome may report the section titles uppercased; that is CSS, not text.
  - None starts with `##`, `>` or `//`, or contains ` @ ` (§10.11).
  - "neuronet-arena Live demo" stays until PR 6.
- `/mcp/` h2 names have no `## `. The brand link is named "brett adams". The also-list link is named
  "More on GitHub".
- In the screenshots, every prefix still shows, with its space after it ("> brett adams", not
  ">brett adams"). `@ ` is still hidden at ≤ 640. The kicker prefix computes to `rgb(123, 138, 156)`
  (`--dim`).
- Visible differences are limited to:
  - type sizes ±1 px (the 404 h1 is 40 → 44 px, §7.4);
  - the 10 px radius.
  Each one is listed in the PR. Spacing moves no pixel in PR 1.
- `contrast.py` output unchanged. V3's motion grep prints only `mcp-surface/styles.css:206`, which
  PR 9 converts (§7.11).
- Analytics are unchanged from §13.5's "before PR 7" column.

**Verification.** V1–V6. Also compare `page.headings` with §10.4.

**Rollback.**
- **Default (tokens in `site.css`):** revert. PR 1 adds no file, so no other page loses a link.
  Later homepage PRs use its tokens, so revert those first, newest first.
- **Under §18 #49 (a):** before PR 9, revert. After PR 9 (or PR 13), `mcp-surface/index.html` (or
  `mazefinder/index.html`) links `/assets/css/tokens.css`. Revert only the `_layouts/default.html`
  and `site.css` hunks and keep `tokens.css`, or `check-links.mjs` fails CI on those pages.

**Size.** M, 6–8 h.

---

### PR 2. Self-hosted IBM Plex

**Gate.** The fonts Decision (§18 #3), default self-host. If Brett says no, skip this PR. PR 1's
fallback faces stay.

**Files.**
- `assets/fonts/ibm-plex-sans-latin-var.woff2`, `ibm-plex-mono-latin-400.woff2`,
  `ibm-plex-mono-latin-500.woff2`, `ibm-plex-mono-latin-600.woff2`, `assets/fonts/OFL.txt` (create)
- `assets/css/site.css` (`assets/css/tokens.css` under §18 #49 (a)), `_layouts/default.html`,
  `README.md` (change)

**Changes.**
1. **Fetch the five files** exactly as §7.5 says:
   - the Android Chrome UA `css2` request;
   - the four `/* latin */` URLs;
   - `OFL.txt` from google/fonts.
   Record each file's bytes and `sha256sum` in the PR. Expected sizes: 40,240, 10,052, 10,060,
   10,120 and 4,456 B (measured 2026-10-22). If Google has moved versions, record the new sizes.
   Never re-subset, convert or edit a file (OFL reserved name, §7.5).
2. **The token block in `site.css`** (PR 1; `tokens.css` under §18 #49 (a)): add §7.1's four
   `@font-face` rules verbatim, inside the markers, above the fallback faces.
3. **`_layouts/default.html`:**
   - delete `:31-33` (two preconnects and the Google stylesheet);
   - add, as the first `<link>` after the meta tags:
     `<link rel="preload" href="{{ '/assets/fonts/ibm-plex-sans-latin-var.woff2' | relative_url }}" as="font" type="font/woff2" crossorigin>`.
     `crossorigin` is required, or the file downloads twice.
4. **`README.md`:** a layout row for `assets/fonts/`: "IBM Plex, self-hosted latin files (SIL OFL
   1.1, see OFL.txt)".

**Acceptance.**
- `perf.mobile.origins` and `perf.desktop.origins` on `/`, `/mcp/` and `/404.html` are the local
  origin only (§11.6).
- `render_blocking` lists only `site.css` (`tokens.css` and `site.css` under §18 #49 (a)).
- `by_type.font.count` is 4 or fewer on each page. `font_kb` is under 80 (§11.7).
- `perf.mobile.fcp_ms` on `/` is at or below the before state's value (16.2), both from
  `--runs 3`. The baseline records no local FCP, so the before state is the number to beat. CLS:
  - `/mcp/` mobile ≤ 0.0205 and desktop ≤ 0.0423 (today, local);
  - expected under 0.01 (**estimated**).
- After load, `document.fonts.check('16px "IBM Plex Sans"')` and
  `document.fonts.check('600 16px "IBM Plex Mono"')` are both `true`. Screenshots show Plex.
- `check-links.mjs` passes, which proves the preload `href` resolves.
- **Windows check** (Brett, or anyone with a Windows machine): `/` at 100% zoom in Chrome or Edge,
  compared with today. If small text looks worse, swap in the hinted desktop files under the same
  names (90,928 B) and raise `font_kb` to 100 (§11.7).

**Verification.** V1–V6.

**Rollback.**
- **Before PR 9:** revert. That restores the Google Fonts link. The font files are inert without
  it.
- **After PR 9:** `mcp-surface/index.html` preloads `/assets/fonts/ibm-plex-sans-latin-var.woff2`
  and its `styles.css` names all four files. After PR 11b, so does the synced
  `neuronet-arena/index.html`, which can't be changed here; PRs 12–13 add two more pages. Revert only
  the `_layouts/default.html` and `site.css` hunks (the Google link comes back, the four
  `@font-face` rules go) and keep the five files in `assets/fonts/`, or `check-links.mjs` fails CI on
  those pages.

**Size.** S, 2–3 h, plus the Windows check.

---

### PR 3. Header and phone section nav

**Goal.** Phones get the four section links in a plain row under a sticky brand-and-résumé row.
This is audit issue 1, High.

**Files.** `_layouts/default.html`, `assets/css/site.css` (change).

**Changes.**
1. **Markup.** Replace `_layouts/default.html:38-53` with the mock's header and phone row (§6.4,
   §8.1; in `handoff/mocks/first-screen.html`, the `header.site-header` and `nav.nav-sections`
   elements):
   - The brand keeps PR 1's `span.prefix`.
   - The phone row is a sibling **after** `header.site-header`:
     `<nav class="nav-sections" aria-label="Sections"><div class="wrap nav-sections-inner">` plus
     the four links, with hrefs as §5.3 (`{{ '/#experience' | relative_url }}` …).
   - `<main id="main" tabindex="-1">` (§8.1, §10.2).
2. **CSS. The mock's rules are authoritative.** Port its header rules (`first-screen.html:175-195`,
   the "8.1 Header" block) and their breakpoint lines (`:291`, and the header lines `:294-301` of
   the ≤ 860 block, `:293-305`) into `site.css` as they are. (Line numbers are the mock's as of this spec; if
   they move, the block names hold.) §8.1's CSS and §10.7's sizes copy the mock; if any
   other text disagrees, the mock wins. The rules include:
   - `.site-header { background: rgba(11, 15, 20, 0.92); }`, raised from 0.82 (`site.css:101`) so
     nav text over scrolled content holds 5.29:1 (§7.3);
   - `.site-name, .site-nav a { min-height: 44px; }`, and `min-height: 36px` on `.nav-resume`;
   - at ≤ 860, `.nav-sections a` with `flex: 1 1 0`, `min-height: 44px`, colour `--soft` and
     `500 var(--fs-sm)`, so the four links share the row equally;
   - `.site-nav` gap `var(--space-6)`, and `var(--space-4)` at ≤ 1000 px;
   - delete the old hide rule (`site.css:859-862`); the mock's `.nav-sections { display: none; }`
     plus its ≤ 860 block replace it;
   - `main:focus { outline: none; }` (§10.2, the one allowed outline removal).

**Acceptance.**
- **390 and 768:**
  - `.nav-sections` is visible with four links, each 44 px tall and of equal width (§6.2's table);
  - the four links sit on one row (equal `top`);
  - `scrollWidth === innerWidth` at 320, 390 and 768.
- **1440:**
  - `.nav-sections` computes `display: none`;
  - the four `.nav-section` links are visible and 44 px tall (§6.2).
- **Scrolling:** after `window.scrollTo(0, 400)`, the header's `top` is 0 and the
  `.nav-sections` bottom is below 0 (it scrolled away). No JS is involved: the page has no new
  script.
- **Tab order (§10.5):**
  - desktop: skip link, brand, Experience, Projects, Skills, Contact, Résumé;
  - ≤ 860: skip link, brand, Résumé, then the four row links.
- **Skip link (§10.2):** Tab once and the skip link is focused, inside the viewport. Press Enter and
  `document.activeElement.id === 'main'`. This holds on `/`, `/mcp/` and `/404.html`, so the
  `skip_link` row leaves 16.2's known list.
- **`--a11y`:**
  - the brand and the desktop nav links leave `small_targets_24`;
  - `obscured` is empty: no focused element is under the sticky header (SC 2.4.11; §10.15 defines
    the check).
- **Analytics:** `resume_download` ×3 on `/` (the header link is unchanged).

**Verification.** V1–V6. The 320×640 shots from `--shots` (PR 0) show the row fit.

**Rollback.** Revert.

**Size.** S–M, 3–4 h.

---

### PR 4. Hero and At a glance

**Goal.** The first screen reads like a résumé header at 390 and 1440, built from the mock (§6),
with nothing live and no project on it.

**Files.** `index.html`, `assets/css/site.css` (change).

**Changes.**
1. **Markup.** Replace `index.html:4-19` with the mock's `section.hero` element
   (`first-screen.html:387-422`; the glance card is `:406-420`), minus every mock-only part:
   - remove every `span.mk`, `.mk-host` and `.ph` (`first-screen.html:8`), and the HTML comment that
     holds the two unanswered rows (`:412-415`);
   - the hero keeps **no `id`**.
   - The structure is §6.4's: `section.hero[aria-labelledby=hero-name]` > `div.wrap.hero-grid` >
     `div.hero-text` and `div.glance`.
   - The jump link is `p.hero-jump > a[href="#projects"]` with
     `<span class="nowrap" aria-hidden="true">&nbsp;↓</span>`.
   - The primary button keeps the mock's classes `btn btn-primary btn-resume`.
   - The `href`s use `relative_url` as in §8.2.
2. **Copy** exactly as §6.3:
   - **Status line:** `Graduating 2027 · Open to full-time roles` [src: index.html:7], as today. Once
     Brett answers, PR 15 changes it (§18 #30–31).
   - **Glance rows, in this order:** Now (hidden at ≤ 640), Before, Studying, Results, Works with
     (hidden at ≤ 640, where Results takes its place).
   - **Results** (§18 #4's first-screen copy option, default ship):
     `<dt>Results</dt><dd>Power BI dashboards that replaced manual reporting (MTO) · Processing time cut from hours to minutes (MTO) · Python training for over 20 employees (CIMCO)</dd>`
     [src: index.html:45], [src: index.html:46], [src: index.html:61].
   - **"Looking for" and "Based in" are left out** until Brett answers (§18 #31, #32). PR 15 adds
     them after Studying, in that order.
   - The "Works with" chips use "Apache Airflow" (D7's default, the name Experience uses). Date
     ranges go in `span.nowrap`.
3. **CSS.** Port the mock's "8.2 Hero" and "8.3 At a glance" blocks (`first-screen.html:197-257`),
   but **not** its `.chips` rules (`:258-262`): chips keep §8.10's rule. Port their breakpoint lines
   too: `:292`, the hero and glance lines of the ≤ 860 block (`:302-304`) and of the ≤ 640 block
   (`:307-316`). (Line numbers as of this spec; the block names hold if they move.) These include:
   - `@media (max-width: 860px) { .hero-jump { display: none; } }` (§6.5, §8.2). The phone row's
     "Projects", higher on the same screen, does the same job;
   - `.glance-list > .glance-now, .glance-list > .glance-tools { display: none; }` at ≤ 640;
   - `.actions .btn-resume { grid-column: 1 / -1; }` at ≤ 640. The button is 44 px tall at every
     width (§8.11): there is no 48 px rule.
   Then:
   - scope the 3-column button grid as `.hero .actions` (§8.2);
   - delete the hero grid overlay (`site.css:231-243`, §7.2) and keep the two washes;
   - `.btn`: `height: 42px` → `min-height: 44px` (`site.css:164`, §8.11).

**Acceptance (the mock's measured checks, §6.2, repeated on the real page).**
- **1440:**
  - two columns;
  - the glance card's top is level with the status line (±4 px);
  - the jump link is visible;
  - `#experience-title` bottom < 900;
  - the glance card's bottom < 900. If it isn't, drop the third Results item, as §6.3 says, and
    record that in the PR;
  - no element overlaps another in the hero.
- **390:**
  - status, kicker, h1, lede and "Résumé (PDF)" are in the first 844 px, and the `.status` text
    starts with "Graduating";
  - Email, LinkedIn and GitHub share one `top` and are each ≥ 44 px tall;
  - "Résumé (PDF)" is full width and 44 px tall;
  - `.glance-now` and `.glance-tools` compute `display: none`, and the Results row is shown;
  - `.hero-jump` computes `display: none` (also at 768);
  - the element positions match §6.2's 390 column (±4 px).
- The glance card's `dt` labels are §6.3's shipped rows, in order. There is no "Looking for" or
  "Based in" row.
- `scrollWidth === innerWidth` at 320, 390, 768 and 1440.
- `key_info` (`--a11y`): `current_role`, `resume` and `contact` all at `screens_to_reach: 0` at both
  sizes (§4.3 (a)–(c)).
- PR 4 records `key_info.projects_heading.bottom` at 390 and 1440 as PR 5's starting point. §4.3's
  limit applies from PR 5.
- `document.querySelectorAll('.hero img, .hero picture, .hero canvas, .hero video, .hero iframe').length === 0`
  (no project on the first screen, §5.2).
- `document.getAnimations().length === 0`. LCP is a text element (`lcp.url` is `null`).
- **Accessible names (CDP accessibility tree, as §7.10 measured):**
  - at 1440, the link "Projects and live demos", without the arrow;
  - heading "At a glance" (CDP reports "AT A GLANCE"; compare ignoring case);
  - kicker text "data & software engineering".
- **Analytics:**
  - `contact_click` ×6 on `/`, with the hero ones in the order email, linkedin, github;
  - `resume_download` ×3;
  - clicking the jump link (at 1440) sends no event.
- axe: 0 at both sizes. The `color-contrast` "incomplete" items on the hero washes are expected
  (§7.3).
- With the grid overlay gone, `sed '1,/end tokens/d' assets/css/site.css | grep -nE '#[0-9a-fA-F]{3,8}\b'`
  prints only the print block's two lines.

**Verification.** V1–V6. Put the 390 and 1440 folds next to `handoff/mocks/first-screen-390.webp`
and `-1440.webp` in the PR. They should match, except that the marker circles and placeholder boxes
are gone, and the start of Experience at 1440 keeps today's markup until PR 5. Where the mock's text
differs from §6.3, §6.3 wins.

**Rollback.** Revert.

**Size.** M, 4–6 h.

---

### PR 5. Experience, tightened

**Goal.** Experience stays first, compact and dated, and Projects start no lower than today:
`#projects-title` bottom ≤ 2,532 px at 390 and ≤ 1,800 px at 1440 (§4.3).

**Gate.** The Experience-trim Decision (§18 #6), default (a): the trim below. There is no summary
sentence under `## Experience` by default. Only if Brett chooses §18 #6 (c) does PR 5 add #6's
sentence as `<p class="section-lede">` directly under `## Experience`, with
`.section-lede { max-width: 54em; margin: calc(-1 * var(--space-4)) 0 var(--space-7); color: var(--soft); }`
and `@media (max-width: 640px) { .section-lede { display: none; } }`, and re-measure. It costs about
67 px at 1440 (measured on an earlier build of the mock) and nothing at 390.

**Files.** `index.html`, `assets/css/site.css` (change).

**Changes.**
1. **Markup.** Each role becomes §8.4's `li.job`:
   - `div.job-head` with `h3.job-title` and `p.job-when`;
   - `ul.job-points`;
   - a `details.job-more` with a `summary` and a second `ul.job-points`, only for a role that step 3
     moves a bullet from;
   - `ul.chips[role=list]`.
   The employer span uses PR 1's `span.prefix` plus `span.sr-only` "at " (the mock's form, the
   Geotab `h3` at `first-screen.html:430`). The Geotab `li` keeps `job-current`.
2. **The homepage carries the same 13 bullets as today, in §4.5's order** (the résumé keeps every
   bullet, in its own wording):
   - **Geotab:**
     - `:31` in the résumé's wording, "Maintain and debug production Apache Airflow DAGs on Cloud
       Composer that run the SalesOps data pipelines." [src: resume/resume.tex:106] (D6's default);
     - `:32` trimmed to "Write and deploy production BigQuery SQL for Revenue Operations tables."
       [src: index.html:32, trimmed, §18 #7];
     - `:33`;
     - `:34`.
   - **MTO:** `:45`, `:46`, `:47`.
   - **CIMCO 2024:**
     - `:58`, the role's first bullet, trimmed: "Built Flask web applications that deliver real-time
       data and alarm information to NHL arena operations teams." [src: index.html:58]. The
       `ul.also` NHL Arena row stays as well (§8.9);
     - `:61`;
     - `:59`;
     - `:60` as "Automated controller data collection with a Python tool, replacing manual data
       entry." until `[BRETT: what does "CAG" stand for?]` is answered.
   - **CIMCO 2023:** `:72`, `:73`.
   - **Chips** per §4.5 item 5: Geotab "BigQuery · Apache Airflow · Cloud Composer · SQL · GitLab
     CI/CD"; the others unchanged.
   - If Brett chooses §18 #6 (b), `:31` ships as the site-only reorder instead: "Maintain and debug
     the SalesOps data pipelines in production, and the Apache Airflow DAGs on Cloud Composer that
     run them." [src: resume/resume.tex:106].
3. **Fit the height, by measurement:**
   1. Build with all 13 bullets shown and no `details`. Measure
      `key_info.projects_heading.bottom` at 390 and 1440 (`--a11y`).
   2. If over a limit, move bullets into their role's `details.job-more` one at a time, in this
      order, re-measuring after each: `:33`, `:59`, `:47`, `:34`, `:32`, `:61`, `:73`, `:60`,
      `:46`. "Moved" means into the disclosure (§8.4), never deleted. Outcome bullets go last.
      §4.5 and §8.4 use the same order.
   3. Never move `:31`, `:45`, `:58` or `:72` (each role's lead bullet), a role's dates or its
      chips.
   4. If all nine are moved and a limit is still missed, stop and report to Brett. The next lever is
      §6's glance card, which is a §6 change (§8.4). The jump link is already hidden at ≤ 860 from
      PR 4, so it is not a lever here.
4. **Summaries.** One per role that has a `details.job-more` after step 3. Each reads "<N> more",
   where N is the number of bullets moved from that role, plus
   `<span class="sr-only"> from <employer></span>`, with ", 2024" and ", 2023" for the CIMCO roles
   (§8.4). The accessible names are "<N> more from <employer>[, year]". Each `details` gets
   `data-item="geotab"` / `"mto"` / `"cimco-2024"` / `"cimco-2023"`. Nothing reads it yet; PR 14
   can.
5. **CSS:**
   - `.job > ul:not(.chips)` (`site.css:405-418`) becomes `.job-points`;
   - `.job-more` per §8.4: summary in Mono `--fs-xs` `--amber`, `min-height: 24px`, 32 px at
     ≤ 640, `display: list-item`, no animation;
   - `.job` padding-bottom 32 px;
   - `.section` padding 64 px, and 48 px at ≤ 640.

**Acceptance.**
- `projects_heading.bottom` ≤ 2,532 at 390 and ≤ 1,800 at 1440 (**measured** in the PR, three
  loads, all within).
  - What to expect (§8.4's height check, **estimated** on a scratch build): the loop stops after
    four or five moves at 390. After four (`:33`, `:59`, `:47`, `:34`) `#projects-title` sits at
    about 2,521, 11 px inside the limit, with three summaries. 1440 needs no move (about 1,677 with
    all 13 shown).
  - A result far from that, in either direction, means the markup or CSS differs from §8.4. Check
    before moving more bullets.
- The PR lists the final state: which bullets are shown and which sit in each `details`. Shown and
  moved add up to 13: `document.querySelectorAll('#experience .job-points li').length === 13`, and
  `document.querySelectorAll('#experience details.job-more .job-points li').length` equals the
  number moved.
- Every role shows its title, employer, dates, place, lead bullet (`:31`, `:45`, `:58`, `:72`) and
  chips. No `details` contains a lead bullet.
- `details` opens with Enter and with Space. No height transition. Each `summary` is ≥ 24 px tall,
  and ≥ 32 at ≤ 640.
- Heading names as §10.4. Each summary is named "<N> more from <employer>[, year]", and each name
  is unique on the page.
- The focus walk on `/` matches §10.5: one stop per summary. With a disclosure in all four roles
  that is 38 stops at ≥ 861 and 37 at ≤ 860; each role without one has one stop fewer (so 37 and
  36 with the three summaries expected above).
- `section_view` still lists `experience`. No new event.
- By default there is no summary sentence: `document.querySelector('#experience .section-lede')` is
  `null`, unless Brett chose §18 #6 (c).
- §13.6's `scroll_depth` mark table is in the PR (page heights before and after).

**Verification.** V1–V6, plus the measurement loop above, recorded.

**Rollback.** Revert. Or move bullets back out of `details` in the reverse order (`:46`, `:60`,
`:73`, `:61`, `:32`, `:34`, `:47`, `:59`, `:33`), re-measuring after each, as §8.4 says.

**Size.** M, 4–6 h.

---

### PR 6. Project cards and screenshots

**Goal.**
- Every card says what it is and what each link opens.
- The "Live demo" badge leaves the heading.
- Screenshots ship at the size they render, in 16:10 boxes.

**Files.**
- **change:** `index.html`, `assets/css/site.css`, `handoff/tools/budget.json`
- **create:** `assets/img/neuronet-arena-800.webp`, `mcp-servers-800.webp`, `mcp-servers-1600.webp`,
  `mcp-surface-800.webp`, `mcp-surface-1600.webp`, `promptpal-800.webp`, `promptpal-1600.webp`,
  `technest-800.webp`, `technest-1600.webp`
- **delete:** `assets/img/mcp-servers.webp`, `mcp-surface.webp`, `promptpal.webp`, `technest.webp`

**Changes.**
1. **Images** (§7.9). Encode from today's files with Lanczos at WebP quality 80, `method=6`:

   ```python
   # run from the repo root: python3 -I encode.py  (keep the script out of the repo)
   from PIL import Image
   for name in ("neuronet-arena", "mcp-servers", "mcp-surface", "promptpal", "technest"):
       im = Image.open(f"assets/img/{name}.webp")
       for w in (800, 1600):
           if w >= im.width:
               continue            # neuronet-arena is 1280 wide: it gets only -800
           im.resize((w, round(im.height * w / im.width)), Image.LANCZOS).save(
               f"assets/img/{name}-{w}.webp", "WEBP", quality=80, method=6)
   ```

   - Expected bytes are §7.9's table: the five `-800` files total 105,646 B (measured).
   - Then delete the four old files. `neuronet-arena.webp` stays: it is the 1280w candidate.
2. **Cards.**
   - neuronet-arena per §8.5; MCP servers, mcp-surface, PromptPal and TechNest per §8.6.
   - Order per §5.5, which is `index.html`'s: neuronet-arena, MCP servers, mcp-surface, PromptPal,
     TechNest.
   - Each card has:
     - an `aria-hidden` `.project-shot` link whose `href` equals the card's first `.links a`;
     - `h3.project-name` with the name only;
     - `p.project-kind` per §8.8, a label from its fixed list of at most four words after the badge
       (§18 #6), with `span.badge-live` only on neuronet-arena and mcp-surface;
     - `p.project-line`;
     - `ul.chips[role=list]` per §8.5–8.6's copy tables;
     - `p.links`, with `sr-only` suffixes (§8.0).
   - The MCP card's screenshot goes to `/mcp/` (audit issue 24).
   - **The mcp-surface card** (§18 #51, default ship):
     - line: "Checks an MCP server the way an AI assistant sees it, and fails the build when a tool
       changes, so every change gets a review." [src: index.html:117; mcp/index.html:20-21, :40-42];
     - kind: `Live demo · runs in your browser` (the badge, then the label);
     - the npm link stays a named link in `.links`.
   - **The PromptPal card** (§18 #51 and #6, default ship):
     - line: "Write an AI prompt once as a template with fill-in fields. It flags mistakes that
       quietly ruin answers and exports a ready-to-run Claude API call."
       [src: promptpal@82ffb5d:README.md:6-8];
     - kind: "Web app on Vercel" [src: promptpal@82ffb5d:README.md:124-125].
   - Chip changes:
     - "13.7× the food with selection on" replaces "13.4 food vs 1.0 for the control"
       [src: neuronet-arena@07bc91f:README.md:46, "13.7× as much" (stated); 13.39 / 0.98 = 13.66
       (computed, fact sheet §7.1)];
     - "JS matches Python bit for bit" replaces "Python + JavaScript";
     - "19 checks" replaces "npm";
     - "E2E + accessibility tests" replaces "E2E + a11y tests";
     - on the MCP servers card, "Tools checked on every change" replaces "Snapshot-checked in CI"
       [src: mcp/index.html:40-42] (§18 #51, default ship);
     - "200+ tests" stays (D14's default, §18 #9).
3. **`ul.also`** per §8.9:
   - `.also-name`, `.also-desc` and `.also-links`;
   - the NHL Arena row's `.also-desc` (§18 #36, default ship): "Live refrigeration data, alarms and
     a thermal imagery viewer for NHL arena operations teams and technicians. Built at CIMCO;
     internal." [src: index.html:58, :154];
   - MazeFinder's link becomes "Source" with the same URL;
   - "More on GitHub" keeps PR 1's `aria-hidden` arrow;
   - grid columns `240px minmax(0, 1fr) auto` at ≥ 641, stacked below that;
     `.also li { padding: var(--space-4) 0; }`.
4. **CSS:**
   - §7.9's `.project-shot` (16:10, `object-position: left top`);
   - §8.5's `.project-featured` grid;
   - `.project-kind` (§8.8), `.links` (§8.6), the `.also` link targets (§8.9), the badge (§8.12)
     and chips with
     `overflow-wrap: anywhere` (§8.10);
   - `:focus-within` on cards → `--border-strong`.
5. **PromptPal:** load https://prompt-pal-gules.vercel.app once (Playwright or `curl -sI`). If it
   doesn't return 200 with the app, apply §8.6's fallback and say so in the PR:
   - drop "Live app";
   - point the screenshot at Source;
   - kind "Source code".
6. **`budget.json`:** `/` `total_kb` 420 → 240 and `image_kb` 330 → 130 (§11.7).

**Acceptance.**
- **Every `srcset` URL exists** (`check-links.mjs` doesn't read `srcset`):

  ```sh
  node -e "const fs=require('fs');const h=fs.readFileSync('_site/index.html','utf8');const u=[...h.matchAll(/srcset=\"([^\"]+)\"/g)].flatMap(m=>m[1].split(',').map(s=>s.trim().split(/\s+/)[0]));const miss=u.filter(x=>!fs.existsSync('_site'+decodeURI(x)));console.log(u.length+' srcset URLs; missing: '+JSON.stringify(miss));process.exit(miss.length?1:0)"
  ```

  It prints `10 srcset URLs; missing: []`.
- At 390 (DPR 2) and at 1440 (DPR 1), every card's `img.currentSrc` ends in `-800.webp`.
  `image_kb` < 130 and `total_kb` < 240 (budget). Expected: about 104 and 190 (§11.2).
- `grep -rnE '(mcp-servers|mcp-surface|promptpal|technest)\.webp' _site --include=*.html` prints
  nothing.
- Heading names "neuronet-arena", "MCP servers for Google Workspace and Reddit", "mcp-surface",
  "PromptPal", "TechNest", with no "Live demo" in any (§10.4).
- Each `.project-shot` `href` equals its card's first `.links a` `href`.
- Every link whose text is "Live demo" points to `/neuronet-arena/` or `/mcp-surface/`.
- The card text matches §8.5–8.9, including the mcp-surface and PromptPal lines, the "Tools checked
  on every change" chip and the NHL Arena line (or today's strings where Brett answers no to §18 #51
  or #36). Every chip is at most 32 characters (§8.10), and every `.project-kind` label is one from
  §8.8's list.
- `.links a` and `.also` links ≥ 24 px tall, and ≥ 44 at ≤ 640 (`--a11y`). "MazeFinder" and "More
  on GitHub" leave the known list.
- CLS < 0.05 at both sizes. Every `.project-shot` computes `aspect-ratio: 16 / 10`.
- At 1440, the four half cards fill two rows, with no empty cell.
- **Analytics:** `project_click` ×18 (×17 if the PromptPal fallback applies). The MCP screenshot's
  `link_url` is `https://…/mcp/` (`http://127.0.0.1:<port>/mcp/` locally). The MazeFinder Source click sends
  `project_name: "mazefinder"`.
- `#projects-title` still meets §4.3's limits. The taller 16:10 boxes are below it, but the PR
  re-measures.

**Verification.** V1–V6, plus the `srcset` one-liner.

**Rollback.** Revert. That restores the four deleted files and the old markup together.

**Size.** M–L, 6–8 h.

---

### PR 7. Skills, education, contact, footer

**Goal.**
- The rest of the homepage gets the new components.
- Footer targets reach 44 px on phones.
- The footer's contact links are measured.

**Gate.** Footer `data-contact` (§18 #12), default add. The C6 comment fix (§18 #13) is default yes.

**Files.** `index.html`, `_layouts/default.html`, `assets/css/site.css`, `assets/js/analytics-events.js`
(comment only).

**Changes.**
1. **`index.html:161-191`** per §8.13 (Skills, copy unchanged per D8/D9's default), §8.14 (Education
   stays `div#education`) and §8.15 (Contact: the email written out, then LinkedIn, GitHub and
   "Résumé (PDF)" in a wrapping row).
   - Education's date line becomes "2022 – 2027 · Windsor, ON" [src: index.html:177;
     resume/resume.tex:89] (§8.14), in the same "dates · place" form as each role.
2. **Footer** (`_layouts/default.html:57-66`) per §8.16: `p.footer-links`, with
   `data-contact="github"`, `"linkedin"` and `"email"` on the three links. If Brett says no, ship
   the same markup without the three attributes.
3. **CSS:**
   - footer links `display: inline-flex; min-height: 44px` at ≤ 640, and ≥ 24 px above that;
   - contact buttons per §8.11 (wrapping row);
   - skills `dt` above `dd` at ≤ 640 (as today, `site.css:898-903`).
4. **`assets/js/analytics-events.js:5-7`**, comment only. "cannot say whether anyone reached
   #education or opened a project" becomes "cannot say whether anyone reached #skills or opened a
   project" (fact sheet C6). No code line changes. If Brett approves N2 (`education`) in PR 14, that
   PR restores the word.

**Acceptance.**
- `git diff assets/js/analytics-events.js` shows only lines 5–7.
- **Analytics (§13.5, "after" column):**
  - `contact_click` ×9 on `/`, ×3 on `/mcp/`, ×3 on `/404.html`;
  - `failed_clicks` empty on all three.
- `document.getElementById('education').tagName === 'DIV'`. The `section[id]` list is unchanged.
- Education's date line reads "2022 – 2027 · Windsor, ON".
- Footer links ≥ 44 px at 390, and ≥ 24 at 1440. They leave the known list.
- Headings "Skills", "Education", "University of Windsor", "Contact" (§10.4).
- The contact email wraps inside 390 with no horizontal scroll.

**Verification.** V1–V6.

**Rollback.** Revert. To undo only the analytics change, delete the three attributes.

**Size.** S, 2–3 h.

---

### PR 8. `/mcp/` case-study template and the 404 page

**Goal.**
- `/mcp/` follows the case-study outline, with the diagram in the first screen at 1440.
- It passes axe at 390.
- The 404 page leads to the homepage, the projects and the résumé.

**Gate.**
- `/mcp/` copy changes (§18 #14), default ship.
- The npm 0.3.0 sentence (§18 #15), default reword.

**Files.** `mcp/index.html`, `404.html`, `assets/css/site.css` (change).

**Changes.**
1. **`/mcp/` order and markup exactly as §8.18's outline table and markup sketch.** §8.18 owns the
   order of the blocks and the `.flow` figure's content. Only the diagram moves; every other block,
   "The repositories" included, keeps today's place. From the top:
   - back link, eyebrow "Case study · October 2026", h1, and `ul.chips.case-facts` with the four
     facts from `mcp/index.html:13`;
   - **`p.case-for`** (new copy, §18 #14): "For anyone who lets an AI assistant send email, edit a
     calendar or post on their behalf, and needs to know when what it can do changes."
     [src: mcp/index.html:17-19, :40-42];
   - **`figure.flow-figure`**, moved up from `:35-39` (§18 #14): two `div.flow-row`s, each a
     `p.flow-label` plus an `ol.flow[aria-labelledby]` naming that label. Row 1 is "At runtime"
     (new); row 2 is "In CI" (today's three steps, unchanged). The figcaption is "How the servers
     run, and what checks them at the end of each server's CI";
   - then `div.post-body`, in today's order, so "The repositories" stays the second h2. The body
     sentence at `:33` now ends "…runs mcp-surface, in the "In CI" row at the top of this page." The
     CI figure's `pre` becomes
     `tabindex="0" role="region" aria-label="Terminal output of npm run surface"` (§8.19), and
     **h2 "Limits"** with §8.18's two bullets is new, after "What both servers now have";
   - `p.post-next` with the two links.
   No paragraph's wording changes except the four items §8.18 marks new or reworded, and item 2
   below.
2. **The npm 0.3.0 sentence** (`mcp/index.html:146-149`; §18 #15's default).
   - **First run `npm view mcp-surface version`.** If it prints a version above 0.3.0, the release
     has shipped: keep today's sentence and take §18 #15 (a) instead (rebuild `checks.js` per
     `README.md:83-92` and update the two version strings, in PR 9 or a follow-up).
   - If it prints 0.3.0, "It now reads its version from its own package, and an end-to-end test
     checks what a server actually receives." becomes "It now reads its version from its own
     package (merged after 0.3.0, so the next npm release carries it), and an end-to-end test
     checks what a server actually receives."
   - Sources: `mcp-surface@ea93b87:CHANGELOG.md:8-16` ("[Unreleased]"); npm `latest` 0.3.0
     (fact sheet §7.2). Paste the `npm view` output into the PR.
3. **No `section[id]`** on the page. If the builder wraps parts in `section`, they get no id.
4. **`404.html`** per §8.17:
   - `section.not-found.wrap[aria-labelledby=not-found-title]` with no `id`;
   - three buttons: "Back to the homepage", "Projects", "Résumé (PDF)", each `href` through
     `relative_url`;
   - front matter `description:` with §12.1's 404 text.
5. **CSS:**
   - `.case-for` per §8.18;
   - `.flow-figure` and `.flow` per §8.18: boxes in a row at ≥ 641 and stacked at ≤ 640, step
     numbers from `::marker`, chevrons as `li + li::before` with empty `content`;
   - `.post-next`;
   - `.post-back a`, `.post-next a` and `/mcp/`'s `.also-name` links at ≥ 24 px, and 44 at ≤ 640;
   - `pre[tabindex]:focus-visible` with offset 2 px (§10.6);
   - `.not-found` padding 120 px, and 64 at ≤ 640.

**Acceptance.**
- axe 0 critical and 0 serious at 390 and 1440 on `/mcp/`: `scrollable-region-focusable` is gone.
- At 1440, both rows of `.flow-figure` end inside the 900 px fold (§4.4's engineer check;
  estimated ≈ 700 px, §8.18). If Brett declines §18 #14, nothing on the page moves, the CI steps
  stay at `:35-39`, and this check doesn't apply.
- Each `ol.flow` has an accessible name from its row label ("At runtime", "In CI").
- `/mcp/` heading outline exactly as §10.4, five h2s with the eight h3s under "What the review found".
  If the copy Decision is no, there is no "Limits" h2.
- The `.case-for` text is the For line above, character for character.
- `document.querySelectorAll('section[id]').length === 0` on `/mcp/` and on `/404.html`
  (`--analytics` also checks this from PR 0).
- **The diff of `mcp/index.html`** shows moved blocks plus exactly:
  - the For line;
  - the `.flow` figure's new "At runtime" row, its two row labels and its figcaption;
  - the sentence tail;
  - the Limits section;
  - the 0.3.0 clause (or no change, if `npm view` showed a newer release);
  - the new markup.
  The PR lists them.
- **404 at a deep path:** `measure.mjs --site _site --path /no/such/page/ --a11y --analytics` (the
  local server serves `404.html` with status 404 on a miss, `measure.mjs:158`). Every button `href`
  starts with `/` and resolves (`ls _site/...`). `resume_download` ×2 on `/404.html` (header and
  button).
- `/mcp/` targets leave the known list: "← Brett Adams" and the three `.also-name` links.
- Title and description of `/404.html` as §12.1.

**Verification.** V1–V6.

**Rollback.** Revert. The four copy items and the 0.3.0 clause are separate lines, so a follow-up
can drop any of them alone.

**Size.** M, 4–5 h.

---

### PR 9. mcp-surface shell and fixes

**Goal.**
- `/mcp-surface/` gets the shared demo shell: whose work it is, the résumé, the source and how it
  works.
- It passes axe (audit issue 3, High).
- Keyboard use works.

**Gate.**
- GA on demo pages (§18 #17), default none.
- Version chip vs date (§18 #18), default chip.
- Where the tokens live (§18 #49), default: a copy of `site.css`'s token block in `styles.css`.

**Depends on.** PR 1, and PR 2 if §18 #3 is yes.

**Files.**
- **create:** `assets/css/demo-shell.css`
- **change:** `mcp-surface/index.html`, `mcp-surface/styles.css`, `mcp-surface/app.js`, `README.md`
- **not touched:** `mcp-surface/checks.js`, `examples.js` and `THIRD_PARTY_LICENSES.txt`

**Changes.**
1. **`<head>`** (`mcp-surface/index.html:1-21`):
   - title and `og:title`: `mcp-surface · playground | Brett Adams` (§12.1);
   - add `<link rel="canonical" href="https://brettadams0.github.io/mcp-surface/">`;
   - if PR 2 shipped, replace `:16-18` with
     `<link rel="preload" href="/assets/fonts/ibm-plex-sans-latin-var.woff2" as="font" type="font/woff2" crossorigin>`;
     if it didn't, keep `:16-18`;
   - add `<link rel="stylesheet" href="/assets/css/demo-shell.css">` after `styles.css`;
   - only under §18 #49 (a): add `<link rel="stylesheet" href="/assets/css/tokens.css">` before
     `styles.css`;
   - add `<link rel="modulepreload" href="checks.js">` and `<link rel="modulepreload" href="examples.js">`
     before the `app.js` script (§11.5).
2. **Body** per §8.20's markup:
   - the skip link;
   - `header.demo-shell`: "← Brett Adams" → `/#projects`, the project name, Source, npm, Résumé;
   - `<main id="main" class="wrap" tabindex="-1">`;
   - the existing cards (`:40-83`), unchanged except the fixes below;
   - `section.demo-how` with §8.20's four items (the first, new, says in plain words why the checks
     exist), the chip "Checks from mcp-surface 0.3.0" and the source links;
   - `footer.demo-foot` with `data-contact` on GitHub, LinkedIn and Email (the same Decision as
     PR 7's footer);
   - the `<noscript>` moves inside `main` (fix 9).
3. **`assets/css/demo-shell.css`:** §8.20's ready-to-paste block, verbatim. It holds shell layout
   only and defines no tokens: it reads the page's own `:root`. Its selectors are `.skip-link`,
   `.sr-only`, `.prefix`, `:focus-visible`, `.demo-shell` and its parts (`.demo-shell-inner`,
   `.demo-crumb`, `.demo-back`, `.demo-sep`, `.demo-name`, `.demo-links`, `.demo-resume`,
   `.demo-source`), `.demo-how`, `.demo-foot`, `.demo-foot-inner`, `.footer-links`, and `.chips`
   (copied from §8.10, so the version chip doesn't render as a bulleted list), with §8.20's
   ≤ 640 rules. 4,640 B, 1,421 B gzipped (**measured** on §8.20's block).
4. **`mcp-surface/styles.css`,** per §7.11's list:
   - replace `:root` (`:4-22`) with a copy of `site.css`'s token block, markers included (PR 1):
     §7.1's `:root`, the gutter and reduced-motion rules and the three fallback faces, plus the four
     Plex `@font-face` rules with `/assets/fonts/…` URLs if PR 2 shipped (§7.11). Under §18 #49 (a),
     delete `:root` instead; `tokens.css` supplies it;
   - **delete the rules `demo-shell.css` now provides** (§7.11, §8.20): `:focus-visible` (`:30`),
     `.sr-only` (`:31`), the old header `.top`, `.back`, `.top-links` (`:35-42`, which includes the
     `.back::before` prefix at `:38`) and the old footer `.foot` (`:232-233`);
   - the literals become tokens;
   - `var(--faint)` → `var(--dim)` at `:98` and `:161` (fix 1);
   - `.wrap` → `--container-demo` / `--gutter`;
   - the opacity states (`:214`, `:229`) become §7.3's colour rules (fix 5);
   - the transition (`:206`) uses §9 tokens;
   - `.editor:focus-within { box-shadow: var(--shadow-focus); }` and form-control edges in
     `--muted` (fix 6);
   - `.seg button` and `select` `min-height: 44px` at ≤ 640 (fix 7);
   - delete the `h1::before` prefix (`:46`); the h1 gets a span instead
     (`<h1><span class="prefix" aria-hidden="true">&gt; </span>mcp-surface</h1>`). The `.prefix`
     rule comes from `demo-shell.css` (§7.10, §8.20).
5. **`mcp-surface/index.html` fixes:**
   - `.term` (`:69`) per §8.19: `tabindex="0" role="region" aria-label="Report output"`, with no
     `aria-live` (fix 2);
   - `#verdict` (`:73`) gets `role="status"`;
   - the examples group (`:44-48`): drop `role="radiogroup"` and `role="radio"`, and put
     `role="group" aria-label="Example"` on `.seg` (fix 3).
6. **`mcp-surface/app.js`:**
   - fix 3: `aria-pressed` instead of `aria-checked` at `:218`, and `styles.css:82` selects
     `[aria-pressed="true"]`;
   - fix 4: in `renderRules` (`:112-134`), give each button `data-rule="<id>"`. Before
     `replaceChildren`, keep `document.activeElement?.dataset.rule`. After rebuilding, call
     `.focus()` on that button;
   - fix 5: skipped rules show the word "skipped" in `.rule-level`, and call-only rules show
     "--call only" in `.rule-what`.
7. **GA** only if Brett approves option (b) of §13.7: an empty front matter block at the top of
   `mcp-surface/index.html` and `{% include analytics.html %}` before `</head>`. Default: neither.
8. **`README.md`**, the mcp-surface section (`:91-92`): "Then copy `checks.js` …, update the rule
   table in `app.js` if the package README's table changed, **and the version in `index.html`'s How
   it works item and chip.**" Add one sentence there: "`styles.css` starts with a copy of the token
   block at the top of `assets/css/site.css`; change both together." Also a layout row for
   `assets/css/demo-shell.css`: "Shell for demo pages: back link, résumé, how it works".

**Acceptance.**
- axe 0 critical and 0 serious at 390 and 1440: the 17 `color-contrast` nodes and
  `scrollable-region-focusable` are gone.
- `git diff --stat mcp-surface/checks.js` prints nothing.
- `grep -nE "fetch\(|XMLHttpRequest|sendBeacon|WebSocket" mcp-surface/*.js` prints nothing. That
  keeps "Nothing you type leaves this tab" true.
- The title contains "Brett Adams". A canonical link is present. The h1's accessible name is
  "mcp-surface".
- **Tab order (§10.5):**
  - the skip link is first;
  - after pressing a rule button, `document.activeElement.dataset.rule` is that rule's id;
  - the examples are buttons with `aria-pressed`, and no element has `role="radio"`.
- **Typing** in the textarea changes `#report`'s text. `#verdict` reads "exit 1 CI fails" for the
  Broken example.
- **At ≥ 861:** the shell's header row is ≥ 52 px tall.
- **At 390:**
  - the header is two rows (crumb, then links), and every shell link is 44 px tall;
  - the segmented buttons and `select` are ≥ 44 px;
  - no horizontal scroll.
- The version chip's `ul` computes `list-style: none` (the `.chips` rule from `demo-shell.css`).
- `mcp-surface/styles.css` no longer defines `.sr-only`, `:focus-visible`, `.top`, `.back`,
  `.top-links` or `.foot`.
- LCP < 2,000 ms (budget). Keep the two `modulepreload` tags only if the three-run median LCP is not
  worse than a run without them, and record both numbers.
- The version text in "How it works" and the chip equals `checks.js:1`'s header (0.3.0).
- **The token copy matches `site.css`:** every custom property in `site.css`'s first `:root` has the
  same value in `styles.css`'s. This one-liner prints `tokens match` (set `F` to the copy's path;
  PRs 12 and 13 reuse it):

  ```sh
  F=mcp-surface/styles.css node -e "const fs=require('fs');const p=f=>Object.fromEntries([...fs.readFileSync(f,'utf8').match(/:root\s*\{([^}]*)\}/)[1].matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)].map(m=>[m[1],m[2].trim()]));const a=p('assets/css/site.css'),b=p(process.env.F);const bad=Object.keys(a).filter(k=>a[k]!==b[k]);console.log(bad.length?'tokens differ: '+bad.join(' '):'tokens match');process.exit(bad.length?1:0)"
  ```

  Under §18 #49 (a), `grep -c ':root' mcp-surface/styles.css` prints `0` instead.
- `demo-shell.css` defines no custom property: `grep -c -- '--[a-z-]*:' assets/css/demo-shell.css`
  prints `0`.
- `--a11y`: `/mcp-surface/` leaves the known list.
- V3's motion grep prints nothing at all.

**Verification.** V1–V6, with `/mcp-surface/` gated from this PR on.

**Rollback.**
- **Before PR 13:** revert. Nothing else links `demo-shell.css` yet.
- **After PR 13:** `mazefinder/index.html` links `/assets/css/demo-shell.css`. Revert only the
  `mcp-surface/` and `README.md` hunks and keep `demo-shell.css`, or `check-links.mjs` fails CI on
  `/mazefinder/`.

**Size.** M–L, 6–8 h.

---

### PR 10. SEO and sharing

**Goal.**
- Accurate metadata.
- JSON-LD that fits a current student.
- Lighter social images and a token-coloured favicon (§12).

**Gate.**
- D19 (§18 #19), default `affiliation`.
- The favicon recolour is part of the visual-system Decision, default ship.

**Files.** `_config.yml` (description only), `_layouts/default.html` (the JSON-LD block),
`assets/img/social-card.png`, `mcp-surface/og.png`, `assets/img/favicon.svg`,
`assets/img/apple-touch-icon.png` (change).

**Changes.**
1. **`_config.yml:3-5`:** §12.1's homepage description. Lines `:9-11` (GA) are not touched.
2. **`_layouts/default.html:12-29`:** §12.3's block. With D19 = keep, use `alumniOf` in place of
   `affiliation`.
3. **Re-encode** `social-card.png` and `mcp-surface/og.png` with §12.2's snippet. Expected bytes are
   84,198 and 39,783 (measured on copies).
4. **`favicon.svg`:** §12.5's three colour swaps. Redraw `apple-touch-icon.png` as §12.5 says.

**Acceptance.**
- `measure.mjs` reports §12.1's title and description for `/`, `/mcp/` and `/404.html`.
- In `_site/index.html`:
  - every `application/ld+json` block parses with `JSON.parse`;
  - exactly one has `"@type":"ProfilePage"`, with `affiliation` (or `alumniOf`) and `knowsAbout` of
    6 items;
  - no `[BRETT`.
- `social-card.png` is 1200×630 and under 90 KB. `mcp-surface/og.png` is 1200×630 and under 45 KB.
  Both look the same as before side by side.
- `_site/sitemap.xml` `<loc>` values are exactly `/`, `/mcp/`, `/mcp-surface/`, `/neuronet-arena/`
  and `/assets/resume/Resume_BrettAdams.pdf` (§12.4).
- The favicon shows in the tab on `/` and `/mcp-surface/`. `apple-touch-icon.png` is 180×180.

**Verification.** V1–V6. Optional, Brett after deploy: the Schema Markup Validator on `/`.

**Rollback.** Revert. It restores the old images.

**Size.** S, 2–3 h.

---

### PR 11a. Upstream: neuronet-arena shell, fonts, focus and contrast (in the neuronet-arena repo)

**Gate.** The upstream Decision (§18 #20). Default: the builder opens this as a **draft PR in
`brettadams0/neuronet-arena`** for Brett to merge. Nothing changes in this repo until Brett does.

**Files (neuronet-arena repo).** `web/index.html`, `web/styles.css`, `web/og.png`.

**Changes.** Everything in §8.20's upstream table and §7.11's neuronet-arena list:
1. **Header** in the shell markup, with absolute URLs:
   - "← Brett Adams" → `https://brettadams0.github.io/#projects`;
   - Source;
   - Résumé → `https://brettadams0.github.io/assets/resume/Resume_BrettAdams.pdf`;
   - plus the skip link and `<main id="main" tabindex="-1">`.
2. **`web/styles.css`:**
   - a copy of `site.css`'s token block (§7.1's `:root`) in place of `:5-27`, keeping `--blue`,
     `--series-1`, `--series-2` and
     `--bad`;
   - the shell rules copied from `assets/css/demo-shell.css` (a copy, because the repo also runs on
     its own).
3. **Fonts:**
   - replace `web/index.html:16-20` with the Sans preload `/assets/fonts/ibm-plex-sans-latin-var.woff2`;
   - add §7.1's `@font-face` rules with `/assets/fonts/…` URLs and the three fallback faces.
   If PR 2 was declined, keep Google's stylesheet as a normal `<link>` (not `media="print"`) and add
   only the fallback faces.
4. **Focus:** `:focus-visible` on links, `summary` and the canvas, not only
   `.btn, .seg button, input` (`:158`).
5. **Contrast and literals:**
   - `td.pending` → `var(--dim)` (`:261`);
   - `#ffc370` → `var(--amber-hi)`;
   - `#c4ccd6` and `#b9c2cd` → `var(--soft)`.
6. **Markup fixes:**
   - empty `<th></th>` → `<td></td>` (`index.html:118`);
   - the prefixes as `aria-hidden` spans (`:58`, `:68`);
   - `summary` "Data table" ≥ 24 px tall (19 px today, measured,
     `[brettadams0:measure-neuronet-arena.json.viewport.390.small_targets_24]`).
7. **Metadata and footer:**
   - title and `og:title` `neuronet-arena · live demo | Brett Adams`;
   - `<link rel="canonical" href="https://brettadams0.github.io/neuronet-arena/">`;
   - footer contact links in the site's order (GitHub, LinkedIn, Email);
   - `og.png` re-encoded as §12.2 (78,033 B measured).
8. **Optional,** a separate commit Brett can drop: remove the "How it was built" link upstream, so
   this repo's one local difference (`README.md:75-76`) goes away.
9. **No GA** unless Brett approves §13.7 option (d). It would also need `section#parity`'s id
   removed or accepted as a new value.

**Acceptance (in that repo).**
- Its CI is green: Python 3.10–3.13 and Node 20/22 (fact sheet §7.1). The 11 `node --test` tests
  still pass.
- `node <site-repo>/handoff/tools/measure.mjs --site web --path / --axe --a11y` shows axe 0 / 0 / 0 / 0.
  `/assets/fonts/` doesn't exist in that repo, so the fallback faces render. Final CLS is checked in
  11b.
- With `reducedMotion: 'reduce'`, the arena doesn't advance in 2 s (§9.5 item 5).

**Rollback.** Close or revert the upstream PR. This repo is unaffected until 11b.

**Size.** M, 5–7 h.

### PR 11b. Re-sync `neuronet-arena/`

**Files.** `neuronet-arena/**` (by `rsync` only), `README.md` (only if 11a removed the local
difference), `handoff/tools/budget.json`.

**Changes.**
1. Run `README.md:69-73`'s `rsync` line from a sibling checkout of the merged upstream.
2. Re-apply the one local difference documented in `README.md:75-76` (remove the "How it was
   built" link) if it still exists upstream. Skip this step if 11a's removal commit merged. This
   and the `rsync` are the only edits to `neuronet-arena/` that §16.2 allows.
3. If it is gone, change `README.md:75-76` to say there is none.
4. `budget.json`: `/neuronet-arena/` `cls` 0.2 → 0.05.

**Acceptance.**
- `diff -rq ../neuronet-arena/web/ neuronet-arena/` lists only the excluded files, plus the local
  difference if kept.
- Mobile CLS < 0.05 (budget; today 0.1643). axe 0 / 0 / 0 / 0. `--a11y` clean on
  `/neuronet-arena/`.
- `check-links.mjs` passes. The preload's root-relative `/assets/fonts/…` resolves, which needs
  PR 2. Absolute links are skipped.
- The synced token copy matches `site.css`: PR 9's one-liner with `F=neuronet-arena/styles.css`
  prints `tokens match`. It checks only `site.css`'s names, so the demo-only tokens (`--blue`,
  `--series-1`, `--series-2`, `--bad`) are allowed.

**Rollback.** Revert. That restores the previous synced copy.

**Size.** S, 1–2 h.

---

### PR 12. border-waits page and card (gated)

**Gate.** All of these:
- Brett says build (the new-project Decision, §18 #21);
- the project repo exists under its agreed name;
- its M2 data and M3–M4 `web/` are published (§14.7);
- the `data` branch holds ≥ 21 days of readings, after the M1.5 burn-in (§14.7). With fewer, the
  hourly chart's median and p90 band can't draw (the brief's n ≥ 7 and n ≥ 20 rules, estimated).

**Default: not built.**

**Files.**
- **create:** `border-waits/**`, copied with the `rsync` below, `assets/img/border-waits-800.webp`,
  `assets/img/border-waits-1600.webp`
- **change:** `index.html`, `README.md`, `handoff/tools/budget.json`
- **delete:** `handoff/border-waits-pipeline.md`

**Changes.**
- **Copy the page** from a sibling checkout of the project repo with exactly this line. The
  README's new sync section quotes the same line (brief §2):

  ```sh
  rsync -a --delete --exclude node_modules --exclude test --exclude package.json --exclude package-lock.json --exclude tsconfig.json --exclude Makefile ../<repo>/web/ border-waits/
  ```

- **The page** is the brief's §2, with this spec's shell (§8.20), title (§12.1), fonts and token
  copy (§7.11). The screenshots follow §7.9's naming (`-800` and `-1600`).
- **The card** is the second `article.project.project-featured` directly after neuronet-arena
  (§5.5, §8.5):
  - `data-project="border-waits"`;
  - kind: `Live demo · SQL in your browser` (the badge, then the label, §8.8);
  - line: "An hourly pipeline over the wait estimates the Canadian and U.S. border agencies publish,
    tested with dbt. Query it with SQL in your browser." (§8.5, the brief's card section);
  - chips: `dbt + DuckDB` · `Tests gate every deploy` · `CBSA + U.S. CBP open data` (§8.5). No chip
    states a schedule, which would stay up if the schedule stopped.
  Placement follows the homepage-layout sub-decision (§18 #21e).
- **`budget.json`:** add §11.4's `/border-waits/` line. Raise `/` `image_kb` and `total_kb` by the
  new `-800` file's size, rounded up to 10 KB.

**Acceptance.**
- The brief's "Verify before pushing" list, with its post-click checks run as below.
- **No package or build files were copied:**
  `find border-waits \( -name 'package*.json' -o -name node_modules -o -name tsconfig.json -o -name Makefile -o -name test \) -print`
  prints nothing.
- The token copy in `border-waits/styles.css` matches `site.css` (PR 9's one-liner with
  `F=border-waits/styles.css`).
- Before any click, `perf.*.origins` on `/border-waits/` holds only the site origin: no jsDelivr and
  no `extensions.duckdb.org`.
- **After one scripted click on the SQL button,** in Playwright, with the data path routed to the
  project's deployed data (M2 is a gate, so it exists). The local server has no data and no Range
  support, so without the route these checks can't run before merge:

  ```js
  await context.route('**/<repo>/data/v1/**', async (r) => r.fulfill({
    response: await r.fetch({ url: 'https://brettadams0.github.io' + new URL(r.request().url()).pathname }),
  }));
  ```

  `r.fetch` forwards the request's headers, `Range` included. Then:
  - the presets run, and the rebuild check shows that it matches the pipeline's build;
  - the in-browser `select version()` equals the pipeline's pinned DuckDB (the brief);
  - the click adds under 8.5 MB of transfer and at most 8 requests, and the origins are exactly the
    site, `cdn.jsdelivr.net` and `extensions.duckdb.org`. This catches a dev bundle or a double
    load;
  - the button shows load progress while the engine downloads, for example "2.1 of 7.7 MB".
- **Fallback mode:** with the data route returning 404, the page shows its fallback snapshot line
  and still renders, and the SQL button is disabled with the line "SQL needs the live data files".
- The pipeline-status block labels its times "Last successful run" and "Last attempt" (the brief).
- On `/`, `origins` is unchanged and `project_click` sends `project_name: "border-waits"`.
- The heading outline gains h3 "border-waits" after neuronet-arena. The `section[id]` list is
  unchanged. Each project appears once.
- The homepage budget still passes.

**Rollback.** Revert. The project repo is unaffected.

**Size.** Site PR S–M, 3–5 h. The project itself is M, about 50–65 h (**estimated**), in its own repo
(§14.7).

### PR 13. MazeFinder page and card (gated)

**Gate.** The MazeFinder repo's walls and WebAssembly PR is merged
(`handoff/mazefinder-live-demo.md` part 1). This PR is that handoff's site PR (part 2), with this
spec's changes.

**Files.**
- **create, written in this repo** (the handoff's part 2, modelled on `mcp-surface/`):
  `mazefinder/index.html`, `mazefinder/styles.css`, `mazefinder/app.js`, `mazefinder/og.png`
- **create, copied from the MazeFinder repo's `web/` build output:** only the built module and
  binary, `mazefinder/wasm/<name>.js` and `mazefinder/wasm/<name>.wasm`, each copied by name with
  `cp` (not an `rsync` of the folder). Nothing else from `web/` is copied: not its `Makefile`, its
  Node determinism test, or any package file.
- **create:** `assets/img/mazefinder-800.webp`, `assets/img/mazefinder-1600.webp`
- **change:** `index.html`, `assets/css/site.css` (`.project-wide`), `README.md`,
  `handoff/tools/budget.json`
- **delete:** `handoff/mazefinder-live-demo.md` (the handoff says to, `:4-5`)

**Changes, beyond the handoff.**
- **Head and shell:** `/assets/css/demo-shell.css` and §8.20's shell, not the older mcp-surface head
  pattern (`handoff/mazefinder-live-demo.md:127-129`).
  - `styles.css` opens with a copy of `site.css`'s token block, as `mcp-surface/styles.css` does
    (PR 9, §7.11). Under §18 #49 (a) it links `/assets/css/tokens.css` instead;
  - fonts: the Sans preload and the `/assets/fonts/…` faces, as PR 9;
  - title `MazeFinder · live demo | Brett Adams`;
  - a "Thread pool: native build only" line in `.demo-how`.
- **The card** per §8.7:
  - placed after mcp-surface (§5.5);
  - the badge in `.project-kind`, not in the `h3` (§8.8), which overrides the handoff's `:146-147`;
  - kind: §8.8's label for it, "C++ compiled to WebAssembly";
  - line `[BRETT: one line once walls ship]`, falling back to `index.html:155`'s sourced line;
  - chips "20x20 walled mazes" (only once walls exist), "C++ → WebAssembly", "Seeded, reproducible".
- **`.project-wide` lands here** (§8.7, §8.21): add its CSS to `site.css`, and put the class on
  TechNest, now the fifth and last half card. This answers the handoff's open question
  (`:150-152`) unless Brett chooses to demote a card (§18 #22).
- **The list:** remove MazeFinder's `ul.also` row. `data-project="mazefinder"` moves to the card.
- **`README.md`:** the layout row and the sync section the handoff asks for (`:153-154`), naming the
  two copied files.
- **`budget.json`:** §11.4's `/mazefinder/` line, and the `/` image raise.

**Acceptance.**
- The handoff's verify list.
- **Only the listed files were added:** `find mazefinder -type f | sort` prints the four written
  files and the two `wasm/` files, and
  `find mazefinder \( -name 'package*.json' -o -name node_modules -o -name tsconfig.json -o -name Makefile -o -name test \) -print`
  prints nothing.
- The token copy in `mazefinder/styles.css` matches `site.css` (PR 9's one-liner with
  `F=mazefinder/styles.css`).
- At 1440, no empty grid cell. TechNest computes the `.project-wide` layout (§8.7).
- `document.querySelectorAll('[data-project="mazefinder"]').length === 1`.
- No copy says the program solves mazes unless walls have shipped (fact sheet C2).
- The homepage budget passes.

**Rollback.** Revert. The MazeFinder repo is unaffected.

**Size.** Site part M, 6–10 h.

---

### PR 14. Analytics additions (gated)

**Gate.** Only the items Brett approves from §13.8 (§18 #24–27): N1 `hero`, N2 `education`, N3
`nav_click`, N4 `job_more_open`. **Default: none, and this PR isn't opened.**

**Files.**
- `index.html` (N1, N2, and N3's `data-nav="hero"` on `p.hero-jump`);
- `_layouts/default.html` (N3's `data-nav` on `nav.site-nav` and `nav.nav-sections`);
- `assets/js/analytics-events.js` (N3, N4; N2 restores the `#education` comment);
- `handoff/tools/measure.mjs` (`EXACT_SECTIONS`, the expected events, and the two new clicks below);
- `README.md` (the Analytics paragraph lists the new events and parameters).

**Changes.**
- Exactly §13.8's markup and code for each approved item. N3 sends `target_section` and `nav`; N4
  sends `item`. Neither sends `section_id`, so the Section dimension keeps counting only
  `section_view` and `section_dwell` (§13.9).
- **`measure.mjs` `analytics()`** (`:364-430`), for each approved item, with navigation prevented as
  today:
  - N3: also click every `[data-nav] a[href*="#"]`, and expect `nav_click`;
  - N4: also click each `details.job-more > summary` once, and expect `job_more_open`;
  - N1, N2: add the approved id to `EXACT_SECTIONS['/']` (PR 0).

**Acceptance.**
- `--analytics` passes on `/`, `/mcp/` and `/404.html` with the updated `EXACT_SECTIONS`, with no
  `--expect-sections` override.
- Each approved event appears in the logged `dataLayer` with its parameters: `nav_click` with
  `target_section` and `nav`, `job_more_open` with `item`. Neither carries `section_id`.
- §13.5's other counts are unchanged.
- `dnt PASS`.
- Brett registers §13.9's new definitions (`target_section`, `nav`, `item`) in GA4 after merge.

**Rollback.** Revert. GA keeps the events already sent. Nothing else depends on them.

**Size.** S, 1–3 h.

### PR 15. Brett's answers into site copy (gated, repeatable)

**Gate.** An answer to any §18 fact entry (#7, #30–36). **Default: the sourced fallbacks stay, and this PR isn't
opened.**

**Changes.**
- Only the lines each answer touches, in the forms the spec gives:
  - the status line (§6.3, §18 #30–31): `Graduating [BRETT: month] 2027 · Open to full-time roles`,
    and once the role answer exists,
    `Graduating [BRETT: month] 2027 · Open to full-time [BRETT: role family] roles`. A start date
    that differs from graduation goes in the "Looking for" row, not the status line. At ≤ 640 the
    line may wrap to two lines;
  - the glance "Looking for" and "Based in" rows, added after "Studying" and before "Results", in
    that order (§6.3);
  - each number in the bullet it measures, in §4.5 item 3's form. The Geotab answers to §18 #33 go
    into `:31` or `:34`;
  - the 2023 CIMCO title;
  - the CAG expansion, which restores `:60`'s full form;
  - D3 degree wording;
  - education's date line, "2022 – [BRETT: month] 2027 · Windsor, ON";
  - the tagline;
  - the social card and JSON-LD `jobTitle` once the Geotab dates change (§12.2–12.3).
- The PR quotes Brett's answer for each changed line.

**Acceptance.**
- V3 still prints nothing.
- At 390, the `.status` text starts with "Graduating", and its bottom is inside the first 844 px.
- Re-measure the card. In the mock with one-line test answers (**measured**, §6.2): at 390,
  "Looking for" sits at y 708–750, "Based in" at 750–792 and "Results" at 792–918; at 1440 the
  card's bottom is 644. Longer answers move these down, so record the real values in the PR.
- At 1440, the glance card's bottom is still above 900. If an added row pushes it past, drop the
  third Results item first (§6.3).
- §4.3's height limits still hold (re-measure: a longer line can push Projects down).
- No line claims more than the answer says.

**Rollback.** Revert.

**Size.** S, 1–2 h per batch.

### PR 16. Résumé wording (gated)

**Gate.** Brett approves specific résumé changes (§18 #42–47; D5 is in #30). **Default:
`resume/resume.tex` and the PDF are untouched.**

**Changes.**
- Only the approved lines in `resume/resume.tex`.
- Brett compiles on Overleaf with XeLaTeX (`README.md:36-39`) and copies the output over
  `assets/resume/Resume_BrettAdams.pdf`. The path is unchanged.

**Acceptance.**
- `pdfinfo` reports 1 page, letter size, as today (37,452 B and 1 page today, fact sheet §11).
- The PDF still has the 3 link annotations (fact sheet D20).
- The extracted text differs from today's only in the approved lines.
- `resume_download` ×3 on `/`.

**Rollback.** Revert. That restores the previous PDF.

**Size.** S, 1–2 h, plus Brett's compile.

### PR 17. Sift line (gated)

**Gate.** Brett says Sift appears (§18 #29, D21). **Default: no, and this PR isn't opened.**

**Changes.**
1. Re-sync `sift/` from upstream `sift@e37fa56` or later, or delete the mirror, per the sub-decision
   (fact sheet C5). `_config.yml:31-32` keeps it out of the build either way.
2. Then add §8.9's `li[data-project="sift"]` row, using upstream facts only.

**Acceptance.**
- `project_click` sends `project_name: "sift"`.
- No `sift/` file appears in `_site`.
- The row's line matches the upstream README.

**Size.** S, 1–2 h (a mirror re-sync adds S).

### PR 18. Budget in CI (gated)

**Gate.** The CI Decision (§18 #28). **Default: no CI change, and this PR isn't opened.**

**Changes.**
- **The build job** gains one last step, so the next job can read its `_site`:
  `- uses: actions/upload-artifact@v4` with `name: site` and `path: _site`.
- **A second job, `budget`,** with `needs: build`, on `ubuntu-latest`:
  1. `actions/checkout@v4` (for `handoff/tools/`);
  2. `actions/download-artifact@v4` with `name: site` and `path: _site`;
  3. `actions/setup-node@v4` with `node-version: 20.x`, as the build job uses;
  4. `npm install -g playwright@<version>`, pinned to the exact version `npm ls -g playwright`
     reports in the builder's environment (1.56.1 on 2026-10-23, measured). Record it in the PR;
  5. `npx playwright install --with-deps chromium`. §0's "don't run `playwright install`" is about
     this preinstalled environment. PR 18's GitHub-hosted runner, pinned to the same Playwright
     version, is the only exception;
  6. `node handoff/tools/measure.mjs --site _site --path / --path /mcp/ --path /404.html --path /mcp-surface/ --budget handoff/tools/budget-ci.json --runs 1`.
     Size keys need no medians, so one run is enough.
- `budget-ci.json` holds only the size keys (`total_kb`, `js_kb`, `css_kb`, `font_kb`, `image_kb`,
  `requests`, `no_framework`). Timing keys stay local, because shared runners vary.

**Acceptance.**
- CI passes on `main`.
- A draft PR against `main`, from a test branch that adds a 300 KB image to `/`, fails the
  `budget` job. (CI runs only on pushes to `main` and pull requests to `main`,
  `.github/workflows/ci.yml:3-7`, so pushing a test branch alone runs nothing.) Close the draft
  afterwards.
- Run time is recorded in the PR: an extra 3–5 minutes (**estimated**).

**Size.** S, 2–3 h.

### 16.3 Writer's notes

- **Writer's note:** PRs 1–11 keep the numbers §7–§10 cite. This plan adds PR 0 (the measurement
  tools, so PR 1 can be measured with them), splits PR 11 into the upstream PR and its re-sync, and
  adds gated PRs 14–18 so that every Decision with a build consequence has a home.
- **Writer's note:** where sections could still disagree, the plan follows:
  - the mock's CSS for the first screen (PRs 3–4), which §8.1, §8.2 and §10.7 copy;
  - §6.4's `div.glance` / `dl.glance-list` and `nav.nav-sections > div.nav-sections-inner`;
  - the jump link hidden at ≤ 860 from PR 4 (§6.5, §8.2);
  - one bullet selection and cut order, used in §4.5, §8.4 and PR 5;
  - §5.5 for the project order (MCP servers before mcp-surface);
  - §8.1 for `main tabindex="-1"`;
  - §8.8 for the kind labels and §7.9 for the screenshot file names, over the MazeFinder handoff.
- **Writer's note:** the tokens stay at the top of `site.css` by default, as `CLAUDE.md:12` says
  (§18 #49). Demo pages built here carry a copy, as neuronet-arena already does, and PR 9's
  one-liner checks each copy. `assets/css/tokens.css` exists only under #49 (a).
- **Writer's note:** §9.5 says PR 1 replaces all three raw-duration transitions. This plan converts
  `mcp-surface/styles.css:206` in PR 9 instead, because mcp-surface only gets the motion tokens when
  PR 9 copies the token block into its `styles.css`. A `var(--dur-fast)` there before then would
  silently drop the transition.
- **Writer's note:** PR 0 extends `measure.mjs` beyond §10.15 with `--motion`, the 320×640 size,
  `EXACT_SECTIONS`, `desktop_cls`, `origins`, `page_height` and `key_info`, all of which later PRs'
  acceptance criteria use.

## 17. Risks and open questions

Likelihood and impact are **estimated**. "Owner" is who acts: the builder (in the PR named), or
Brett. The new project's own risks are in §14.8. K21 and K22 summarise the two that reach this
site, with the same ratings as §14.8.

### 17.1 Risks

| # | Risk | Likelihood / impact | Where it shows | Mitigation | Owner |
|---|---|---|---|---|---|
| K1 | **Local Jekyll 4.x passes but Pages' 3.x fails,** or renders differently | low / medium | any PR with Liquid | Use only filters already in the site (`relative_url`, `date`). Treat CI (`actions/jekyll-build-pages`) as the final word (§0). After each deploy, view the source of `/` once | builder, every PR |
| K2 | **Projects end up lower than today at 390** after the hero grows | medium / medium | PR 5 | §16 PR 5's measured loop: up to nine bullets moved into disclosures, one at a time, in the one cut order §4.5, §8.4 and PR 5 share. The jump link is already hidden at ≤ 860 from PR 4. Report to Brett before changing §6's card | builder, PR 5 |
| K3 | **Unhinted fonts look soft on Windows** | low–medium / low | PR 2 | The Windows check before merge. If it fails, use the hinted files (90,928 B) and `font_kb` 100 (§7.5, §11.7) | Brett or builder, PR 2 |
| K4 | **The fallback isn't matched on Android** (no Arial; Roboto with no override), so a swap may still re-wrap text there | medium / low | PR 2 | Sans is preloaded, so the fallback shows only briefly on a first visit (§7.5). Not measured: no Android device here | open question Q2 |
| K5 | **A font is fetched twice** (preload without `crossorigin`) | low / low | PR 2, PR 9, PR 11a | `font_kb < 80` fails the budget if it happens (§11.5) | builder |
| K6 | **A broken asset passes CI.** `check-links.mjs` doesn't check `srcset`, CSS `url()`, JS imports or `#fragment` targets (fact sheet §12) | medium / medium | PR 2 (font URLs in CSS), PR 6 (`srcset`), PR 9 (module imports) | §11.6's `failed_requests == ga_requests_blocked` check, the `srcset` one-liner (§16 PR 6), and a load of every gated page in V2 | builder |
| K7 | **An analytics carrier breaks silently,** for example a `section` id renamed, a query string on the résumé link, or a `data-project` lost | low / high | PRs 3–8 | V2's `--analytics` compares against §13.5's counts; V3 greps the résumé `href`; common acceptance 6 pins the four ids | builder, every PR |
| K8 | **GA reports change at the deploy dates** (heights, footer clicks, the MCP screenshot's `link_url`), and someone reads it as a change in behaviour | high / low | PRs 4–8 | §13.6: per-PR page-height tables; Brett keeps the merge dates of PRs 4–7; reach is the steadier series | Brett |
| K9 | **A `[BRETT: …]` placeholder ships** | low / high | PRs 4, 5, 15 | V3's `grep -rn "\[BRETT" _site` in every PR; the fallback copy is written next to every placeholder (§6.3, §4.5) | builder |
| K10 | **"Present" and the social card go stale** when the Geotab internship ends (A19) | high, at a date only Brett knows / medium | `/`, JSON-LD, social card | `[BRETT: Geotab internship end date]` (§18); PR 15 changes the hero, the glance "Now" row, the JSON-LD and the card together | Brett |
| K11 | **A hidden bullet is missed.** `details.job-more` hides bullets, and find-in-page may not search a closed `details` in every browser (not tested here) | medium / low | PR 5 | Outcome bullets are moved last, and each role's lead bullet never moves. Every bullet stays in the résumé PDF. The summaries say how many are hidden ("N more"). N4 (`job_more_open`) can measure use if Brett approves it | Brett (N4), builder |
| K12 | **The upstream neuronet-arena PR doesn't merge,** so the favourite demo keeps CLS 0.1643 and its old header | medium / medium | PR 11a/11b | The budget guards it at `cls < 0.2`, so it can't get worse. Nothing in this repo waits on it | Brett |
| K13 | **Sync drift.** A later neuronet-arena re-sync forgets the local difference, or a mcp-surface `checks.js` rebuild forgets the version text | medium / low | after PR 11b; after any package release | README notes updated in PR 9 and PR 11b. The version chip comes from `checks.js:1` (§8.20). PR 11a can remove the local difference altogether | builder, Brett |
| K14 | **The PromptPal "Live app" is down** when PR 6 ships | low / low | PR 6 | Load it once; §8.6's fallback (no "Live app" link, kind "Source code"); tell Brett | builder |
| K15 | **Front matter on `mcp-surface/index.html`** (only if GA is approved there) puts the file through Liquid | low / medium | PR 9 | It has no `{{` or `{%` today (measured). V1's build and a load of `/mcp-surface/` catch any change | builder |
| K16 | **Mixed versions for up to 10 minutes after a deploy.** Pages sends `max-age=600` (measured), so a returning visitor can get new HTML with cached old CSS | medium / low | PRs 3–9 | Accepted: the window is short and the content stays readable. Optional: a `?v={{ site.time \| date: '%s' }}` query on the two CSS links only, **never** on the résumé link. Not in the plan | — |
| K17 | **Timing noise** makes the LCP or CLS budget flaky between machines or Chromium versions | medium / low | every PR | `--runs 3` medians, about 2.5× headroom on LCP (§11.5), one re-run with `--runs 5` before calling a regression, the Chromium version recorded | builder |
| K18 | **Committed third-party fold shots stay in git history** even if removed later | certain once merged / low–medium | the spec PR | Decided before the spec PR merges (§18 item 1). A removal after merge doesn't shrink `.git` | Brett |
| K19 | **Unrequested design drift** from porting the mock's CSS | low / medium | PRs 3–4 | V4 screenshot comparison against `handoff/mocks/first-screen-390.webp` and `-1440.webp`; §6.2's measured positions as acceptance | builder |
| K20 | **A test sends hits to Brett's GA property** | low / medium | any measurement | `measure.mjs` blocks GA by default; `--allow-ga` is never used (§2.1 records the one probable test hit) | builder |
| K21 | **border-waits stops updating during a job search** (§14.8 R2). GitHub disables a schedule after 60 days without repository activity, and the docs don't say whether bot commits count. A failed run could also go unnoticed. A "stale" badge on the flagship data demo then reads as an unmaintained pipeline | medium / medium | PR 12 and after | The project repo's design (§14.4, the brief): an `if: failure()` step opens one "pipeline failing" issue and closes it on the next success; a day-61 check records the workflow's state with `gh api …/actions/workflows/pipeline.yml --jq .state`, with a monthly reminder for Brett as the fallback; a failed run commits nothing and redeploys the last good export with a `status.json`; the page labels its times "Last successful run" and "Last attempt". The homepage chip says `Tests gate every deploy`, which stays true when the schedule stops | Brett (project repo), builder (PR 12 checks the labels) |
| K22 | **Local times are wrong at a DST change** (§14.8 R11): 01:00–01:59 happens twice on 2026-11-01, and 02:00–02:59 doesn't exist on 2027-03-14. The first falls inside M1 | high / medium | the project repo, before PR 12 | dbt unit tests for both dates, a written rule (the candidate instant not after `fetched_at` and closest to it), and `tz_resolved_by` recording which input decided (§14.4, the brief). PR 12's gate needs the project's CI green, so the tests have passed | Brett (project repo) |
| K23 | **Token copies drift.** By default the tokens live once in `site.css` and each demo carries a copy (§18 #49), so a token change in `site.css` can miss a copy | medium / low | any PR that changes the token block; PRs 9, 11b, 12, 13 | PR 9's one-liner compares a copy with `site.css` and prints `tokens match`. PRs 9, 11b, 12 and 13 run it; any later PR that edits the token block runs it on every copy. The README's mcp-surface note says to change both together | builder |

### 17.2 Open questions

The builder can't settle these from here. Each one is resolved in the PR named, or is Brett's.

| # | Question | How it gets answered | When |
|---|---|---|---|
| Q1 | Does Pages serve `.woff2` with `content-type: font/woff2` and the same `max-age=600`? Not measured, because no `.woff2` is on the site yet | `curl -sI https://brettadams0.github.io/assets/fonts/ibm-plex-sans-latin-var.woff2` after PR 2 deploys | PR 2, after deploy |
| Q2 | How much does the font swap shift text on Android, where the fallback is Roboto with no override? | a real Android phone, or Chromium with Roboto as the only sans font; compare the five paragraphs §7.5 used | PR 2, optional |
| Q3 | How much does the Sans preload move LCP on the throttled profile, against no preload? | two V2 runs, with and without the `<link rel="preload">` | PR 2 |
| Q4 | Do the `modulepreload` tags make mcp-surface's report paint sooner? (estimated 150–300 ms) | PR 9's with-and-without runs | PR 9 |
| Q5 | Do recruiters open these links on a phone? No source measures it (§3.8), so every mobile requirement is a hedge | not answerable from the research; GA's device split after launch gives Brett's own number | after launch |
| Q6 | Is anyone opening the "N more" disclosures? | only measurable with N4 (§13.8), if Brett approves it | PR 14 |
| Q7 | Does the 390 fold still show the glance rows §6.2 measured in the mock once real copy ships, with the Results row in place of Works with? | PR 4's 390 screenshot against §6.2's table | PR 4 |
| Q8 | Will Google Fonts have moved to new file versions by the time PR 2 runs? | §7.5's reproduce step; record the new sizes | PR 2 |
| Q9 | Does a bot commit reset GitHub's 60-day schedule auto-disable for the border-waits workflow? (§14.8 R2, K21) | the day-61 `gh api` check in the project repo records the workflow's state | day 61 of the schedule, only if the project is built |
| Q10 | Which site wins when a user-site folder and a project site share a name? GitHub's docs don't say (§5.1) | avoided by rule rather than tested | never needed if the rule holds |
| Q11 | Should `handoff/site-redesign.md` and its research stay in the repo after the last PR, like a record, or be deleted the way the other handoffs are? | Brett | after PR 11b |
| Q12 | Does DuckDB-Wasm on `/border-waits/` work on iOS Safari, and how long does the 7.7 MB load take on a real phone? Only Chromium is installed here, and a 390 px window in desktop Chromium checks layout, not a phone. The throttled profile gives about 38 s (**estimated**, §11.4) | a real iPhone, after PR 12 deploys | only if PR 12 is built |

## 18. Decisions for Brett

Every question this spec can't answer from the sources. Each entry gives:
- the question;
- why it matters, with evidence;
- the options;
- a recommendation;
- what changes in the plan for each answer;
- **the default the builder uses if Brett doesn't answer.**

Where an option would change a standing rule (`CLAUDE.md`, §0), the default keeps the rule.

**Order.** The list runs from the decisions that shape the earliest PRs to those that block nothing.
"Affects" names the PRs (§16) whose content depends on the answer. Every default lets those PRs
proceed, except the gated PRs 12–18, which aren't opened without a yes.

**Numbers never change,** because the whole spec cites them. So two entries sit out of order: #49
(where the tokens live) shapes PR 1 and is best answered with #2, and #51 (card copy) was added
last but shapes PR 6.

### 18.1 Summary

| # | Decision | Affects | Default if unanswered |
|---|---|---|---|
| 1 | Keep the third-party captures in the public repo | the spec PR's merge | fold shots and trimmed records, as committed |
| 2 | Visual-system changes inside the standing rules (answer with #49) | PRs 1, 3, 4, 10 | ship |
| 3 | Self-hosted fonts (not a build step; changes third-party requests) | PRs 2, 9, 11a | self-host Google's unhinted latin files |
| 4 | **Standing rule:** section order and the first screen; the At-a-glance "Results" row | PRs 3–6, §13.6 | today's order; no project on the first screen; Results row ships |
| 5 | D6, D7: "Cloud Composer" and "Apache Airflow" | PRs 4, 5, 10 | "Cloud Composer"; "Apache Airflow" in bullets and chips; lede unchanged |
| 6 | Homepage site copy: Experience trim, `:31` reorder, summary sentence; project kind labels | PRs 5, 6 | (a): all 13 bullets kept, lower ones moved into disclosures only as height needs; no reorder; no summary sentence. Kind labels ship |
| 7 | Internal names (SalesOps, Revenue Operations, datasets, Data Broker Tool) and "CAG" | PRs 5, 15, 16 | names stay as published; homepage trims `:32` and `:60` |
| 8 | D1: 2 featured servers and 35 tools vs 5 servers and 69 tools | PRs 6, 8 | site keeps 2 / 35; résumé keeps 5 / 69 |
| 9 | D14: TechNest "200+ tests" vs "250+" | PR 6 | "200+ tests" |
| 10 | D17: "19 checks" vs "19 rules" | PRs 6, 8, 9 | "19 checks" |
| 11 | D16: "for Google Workspace and Reddit" vs "for Claude Code" | PRs 6, 8 | site wording unchanged |
| 12 | Footer `data-contact` (analytics) | PRs 7, 9 | add |
| 13 | C6: the analytics script's `#education` comment | PR 7 | fix the comment only |
| 14 | `/mcp/` copy changes: For line, `.flow` figure text, Limits, one sentence tail | PR 8 | ship |
| 15 | npm `mcp-surface` 0.3.0 vs "It now reads its version from its own package" | PRs 8, 9 | reword the tense on `/mcp/`, after an `npm view` check |
| 16 | D15: "all 30 unit tests" on `/mcp/` | PR 8 | keep |
| 17 | GA on demo pages (analytics) | PRs 9, 11a, 12, 13 | none, as today |
| 18 | Version chip or "Updated" date on demo pages | PRs 9, 12, 13 | version chip |
| 19 | D19: JSON-LD `alumniOf`, and the other JSON-LD fields | PR 10 | `affiliation` plus `knowsAbout`, `email`, `worksFor.url` |
| 20 | Upstream neuronet-arena PR | PRs 11a, 11b | builder opens a draft PR upstream; nothing here until Brett merges |
| 21 | New project: border-waits, and its sub-decisions (includes data hosting on the project repo's Pages) | PR 12 | **brief queued; nothing built** |
| 22 | Odd number of half-width cards: `.project-wide` (lands in PR 13) or demote a card | PR 13 | `.project-wide`; no demotion |
| 23 | MazeFinder card line and dates | PRs 13, 16 | today's sourced line; no dates |
| 24 | New `section_id` value `hero` | PR 14 | not added |
| 25 | New `section_id` value `education` | PR 14 | not added |
| 26 | New event `nav_click` (parameters `target_section`, `nav`) | PR 14 | not added |
| 27 | New event `job_more_open` (parameter `item`) | PR 14 | not added |
| 28 | Run the budget's size keys in CI (CI change) | PR 18 | no CI change (recommendation: yes) |
| 29 | Sift on the site, and the stale `sift/` mirror (D21, C5) | PR 17 | Sift not shown; mirror untouched |
| 30 | Availability: graduation month, start date, Geotab end date, further co-op (D4, D5) | PR 15 | sourced lines: "Graduating 2027 · Open to full-time roles" |
| 31 | Target roles wording, and where it goes | PR 15 | no "Looking for" row; status line unchanged |
| 32 | Location, relocation or remote; work authorization | PR 15 | rows left out |
| 33 | Impact and outcome facts per role, including five Geotab questions | PR 15 | sourced wording, no numbers |
| 34 | The 2023 CIMCO title | PR 15 | "Digital Solutions" |
| 35 | D3: canonical degree wording | PRs 15, 16 | each source keeps its own |
| 36 | D18: NHL Arena Web Interface detail or screenshot | PR 15 | one internal line, no link, no image |
| 37 | D12: project dates on the site | none | no dates on cards |
| 38 | Headshot | none | none |
| 39 | Phone number on the site | none | not shown |
| 40 | Custom domain | none | none |
| 41 | Team-project "my part" lines (I7) and an employer post about the internship (I8) | none | not used |
| 42 | C1 / D2: résumé "CI on Node 20 and 22" | PR 16 | résumé unchanged |
| 43 | C2, C3, C4 / D13: résumé MazeFinder lines | PR 16 | résumé unchanged |
| 44 | D11: projects on the résumé | PR 16 | unchanged |
| 45 | D8, D9: skills lists and group names | PR 16 | unchanged |
| 46 | D10: date format | PR 16 | unchanged |
| 47 | D20: "PROȷECTS" in the PDF's text layer | PR 16 | unchanged |
| 48 | Résumé PDF path and `section_id` renames | none | **none proposed**: path and ids unchanged |
| 49 | D22: where the tokens live, and `CLAUDE.md:12` (answer with #2) | PRs 1, 2, 9, 11a, 12, 13 | tokens stay at the top of `site.css`; demos carry a copy; `CLAUDE.md:12` stays true |
| 50 | The GA test hit on 2026-10-21 | none | no action |
| 51 | Card copy in plain words: mcp-surface line and kind, MCP servers chip, PromptPal line | PR 6 | ship |

### 18.2 The decisions

#### 1. Keep the committed third-party captures in the public repo?
*Affects: the spec PR itself. Decide before it merges.*
- **Question.** `handoff/research/sites/<slug>/` holds fold screenshots and trimmed records for 29
  other people's sites. Do they stay in this public repo?
- **Why it matters.**
  - 58 WebP fold shots and 29 trimmed records come to 3.1 MB. The whole of `handoff/research/` is
    4.5 MB, inside the 8 MB allowance (both **measured** with `du`, 2026-10-22).
  - §3 and the appendix cite them.
  - They are other people's pages, shown for comment. They are downscaled fold shots only, with no
    full pages and no code.
  - Git keeps committed binaries in history for good, so a removal after the merge doesn't shrink
    `.git` (PROMPT §4.2: about 4.2 MB today).
- **Options.**
  - (a) Keep the fold shots and records, as committed.
  - (b) Keep the records and text; drop the images.
  - (c) Remove all third-party material from the repo.
- **Recommendation.** (a). The images are what make the appendix checkable.
- **What changes.**
  - (a) Nothing.
  - (b) The spec PR deletes `handoff/research/sites/*/*.webp` before it merges and removes the
    appendix's image tags. Citations of `desktop-fold.webp` then point at nothing in git; the record
    fields remain.
  - (c) The spec PR deletes `handoff/research/sites/`. The appendix keeps slugs, text and links to
    the live sites.
- **Default.** (a): fold shots and trimmed records only, as committed.

#### 2. Visual-system changes inside the standing rules
*Affects: PR 1 (tokens, radius, prefixes), PR 3 (header backdrop), PR 4 (grid overlay), PR 10
(favicon). Answer with #49, which decides where the tokens live.*
- **Question.** Ship §7's changes?
  - a new text-safe grey, `--dim` #7b8a9c;
  - one 10 px radius everywhere (the site uses 12 px, the demos 10 px);
  - the header backdrop at 0.92 instead of 0.82;
  - the hero's faint grid removed;
  - the terminal prefixes as `aria-hidden` spans;
  - type sizes rounded by at most 1 px onto 9 tokens;
  - the favicon recoloured to tokens;
  - where the new tokens live: **#49**. By default they stay at the top of `site.css`.
  (The project kind labels are site copy, so they are in #6.)
- **Why it matters.**
  - **This is not a standing-rule change.** `CLAUDE.md:12` names dark only, the tokens in
    `assets/css/site.css` (shared with the demos), and IBM Plex Sans and Mono. Brett's brief (§3,
    restated in §0) adds the seven palette values and the `> `, `## `, `// ` touches. All of them
    stay, on screen and in the demos. With #49's default the tokens also stay in `site.css`.
  - **The evidence for each change:**
    - `--faint` is 3.37:1 on `--bg`, under 4.5:1, wherever it carries words (audit issue 16);
    - nav text over scrolled content falls to 3.92:1 at 0.82 (§7.3);
    - screen readers hear "## Experience" and "Data Analyst Intern @ Geotab" (issue 6);
    - the site and the demos disagree on radius (fact sheet D22);
    - the favicon's `#15181c` and `#67d2e6` aren't tokens (`assets/img/favicon.svg:2-4`).
- **Options.**
  - (a) Ship all.
  - (b) Ship only the accessibility fixes (`--dim`, 0.92, prefix spans); keep 12 px, the grid and the
    favicon.
  - (c) Ship all except the items Brett names.
- **Recommendation.** (a).
- **What changes.**
  - (a) §16 as written.
  - (b) PR 1 keeps `--radius: 12px` on site pages, so the demos stay at 10 and D22's drift remains.
    PR 4 keeps `site.css:231-243`. PR 10 drops §12.5.
  - (c) The named items come out of PRs 1, 4 and 10.
- **Default.** (a), ship. No standing rule changes.

#### 3. Self-host IBM Plex?
*Affects: PR 2, PR 9's head, PR 11a's fonts. Listed because it changes the third-party requests,
although it isn't a build step.*
- **Question.** Serve the four Plex font files from this site instead of Google Fonts?
- **Why it matters.**
  - Google's stylesheet blocks rendering on every default-layout page. FCP = LCP = 744 ms on `/` and
    844 ms on `/mcp/`, throttled (audit issue 7).
  - Desktop visitors download 90,928 B of hinted files, against 70,472 B for the unhinted ones
    (measured, §7.5).
  - Every page view contacts Google's two font hosts.
  - neuronet-arena's CLS of 0.1643 comes from its font swap (issue 5).
  - Self-hosting is five static files: no plugin and no build step. The licence is SIL OFL 1.1 with
    the reserved name "Plex", so the files ship unmodified with `OFL.txt` (§7.5).
- **Options.**
  - (a) Self-host Google's unhinted (mobile-UA) latin files, 70,472 B.
  - (b) Self-host the hinted (desktop-UA) files, 90,928 B.
  - (c) Self-host files from IBM's own release (sizes not measured here).
  - (d) Keep Google Fonts.
- **Recommendation.** (a), with the Windows rendering check in PR 2, falling back to (b) if small
  text looks worse.
- **What changes.**
  - (a) PR 2 as written.
  - (b) The same files in their hinted form; `font_kb` 100 in `budget.json`.
  - (c) PR 2 takes latin WOFF2 files from IBM's release, re-measures bytes and fallback metrics, and
    updates §7.1's `unicode-range` if the subsets differ.
  - (d) Skip PR 2. PR 1's metric-matched fallbacks still apply. PR 9 keeps `mcp-surface/index.html:16-18`.
    PR 11a keeps Google's stylesheet as a normal `<link>` plus the fallback faces.
- **Default.** (a).

#### 4. Section order and the first screen (standing rule)
*Affects: PR 3 (nav order), PR 4 (jump link, Results row), PR 5 (height limit), PR 6, §13.6.*
- **Question.**
  - Keep today's order, hero → Experience → Projects, with nothing about any project on the first
    screen except a text link to `#projects`? Or move Projects above Experience, or put a project
    screenshot or "selected work" strip in the hero?
  - **First-screen copy option:** ship the At-a-glance card's "Results" row (§6.3)?
    `Power BI dashboards that replaced manual reporting (MTO) · Processing time cut from hours to minutes (MTO) · Python training for over 20 employees (CIMCO)`
    [src: index.html:45], [src: index.html:46], [src: index.html:61].
- **Why it matters.**
  - **The rule:** nothing live on the homepage, each project once. The hero demo preview and the
    separate demos section were removed as duplicates (`CLAUDE.md`).
  - **For today's order:**
    - for new grads, an internship in the industry is the strongest tie-breaker (H4, S04);
    - the same order keeps `scroll_depth` and dwell comparable (§13.6).
  - **For moving Projects up:** proof of work is 3 scrolls down on a phone today (audit issue 2),
    and the set's median to shipped work is 1 step (§4.3).
  - **The redesign already brings proof to 0 scrolls and 1 tap** without moving a section: the
    phone section row's "Projects" at ≤ 860 px, and the nav or the jump link above that (§6.2,
    measured in the mock). The jump link is hidden at ≤ 860, where it would repeat the row.
  - **For the Results row:** without it, every line on the first screen is a title, employer,
    school, date or tool name. The business reader is the site's weakest persona score, 3 (audit
    §2), and §4.2 says that reader must find what changed. The three outcomes are sourced text,
    not stat tiles (A11). At ≤ 640 the row takes the place of "Works with", whose tools repeat in
    every job's chips and in Skills. If the 1440 card's bottom passes the 900 px fold, the third item
    is dropped (§6.3). Brett's numbers from #33 can go into it later.
- **Options.**
  - (a) Keep the order, with the text jump link.
  - (b) Projects above Experience.
  - (c) A project image or "selected work" strip in the hero. This brings back the removed preview,
    a standing-rule change.
  - Results row: ship, or leave it out.
- **Recommendation.** (a), and ship the Results row.
- **What changes.**
  - (a) As planned.
  - (b) A reorder PR after PR 6:
    - move `section#projects` before `section#experience` and reorder the nav to match (Projects ·
      Experience · Skills · Contact);
    - the jump link becomes "Experience ↓" or is removed;
    - §4.3's height limit and PR 5's cuts relax, so bullets can come back out of `details`;
    - the section ids stay the same, and §13.6's break-in-series note applies.
  - (c) Not planned. It would need its own spec section, and it breaks §5.2's rule.
  - Results row left out: PR 4 drops the `div.glance-results` row, and "Works with" shows at every
    width.
- **Default.** (a), and the Results row ships. Both keep the standing rules.

#### 5. D6 and D7: "Cloud Composer" or "Google Cloud Composer"; "Airflow" or "Apache Airflow"
*Affects: PR 4 (the glance chips), PR 5 (bullets and chips), PR 10 (`knowsAbout`).*
- **Question.** Which names do the bullets, chips, the At-a-glance card and the JSON-LD use?
- **Why it matters.**
  - The two sources disagree:
    - the site's bullet says "Google Cloud Composer" (`index.html:31`), while its chip and the résumé
      say "Cloud Composer" (`:36`, `resume/resume.tex:106`);
    - the job chip says "Airflow" (`index.html:36`), while the Skills list and the résumé say "Apache
      Airflow" (`:167`, `resume.tex:97`, `:106`).
  - Full tool names are what a recruiter matches (P6; darshshah `[darshshah:desktop-fold.webp]`).
  - Both forms are sourced.
- **Options.**
  - D6: (a) "Cloud Composer"; (b) "Google Cloud Composer".
  - D7: (a) "Apache Airflow" in bullets, chips, the card and the JSON-LD, with the lede keeping
    "Airflow" in its sentence (`index.html:10`); (b) "Airflow" everywhere; (c) "Apache Airflow"
    everywhere, the lede included.
- **Recommendation.** D6 (a), D7 (a).
- **What changes.** Only the strings in PRs 4, 5 and 10. The résumé changes only through PR 16.
- **Default.** D6 (a), D7 (a). This is what §4.5 and §8.4 already use.

#### 6. Homepage site copy: Experience trim, reorder, summary sentence; project kind labels
*Affects: PR 5 (Experience), PR 6 (kind labels).*
- **Question.** May the homepage tighten Experience, site only? And may every project card carry a
  short kind label?
  - Keep all 13 of today's bullets, with three trims: `:32` without the dataset names; `:58`
    without the thermal-viewer clause ("Built Flask web applications that deliver real-time data
    and alarm information to NHL arena operations teams." [src: index.html:58]); `:60` without
    "for CAG engineers" until "CAG" is explained (decision 7).
  - Move lower bullets behind native "N more" disclosures, one at a time and only as far as §4.3's
    height limit needs, in one order: `:33`, `:59`, `:47`, `:34`, `:32`, `:61`, `:73`, `:60`, `:46`.
    Outcome bullets go last. Each role's lead bullet (`:31`, `:45`, `:58`, `:72`) never moves.
  - Two add-ons, each separate from the trim: a site-only reorder of `:31`, and a one-sentence
    summary under `## Experience`.
  - **Kind labels (§8.8):** one short line under each project name that says what opens first,
    from a fixed list of at most four words after the badge: `Live demo · runs in your browser`
    (neuronet-arena, mcp-surface), "Case study" (MCP servers), "Web app on Vercel" (PromptPal),
    "Source code" (TechNest), and later `Live demo · SQL in your browser` (border-waits) and
    `Live demo · C++ compiled to WebAssembly` (MazeFinder). It also takes the badge out of the
    `h3`.
- **Why it matters.**
  - §4.3 needs `#projects-title` no lower than today at 390 (≤ 2,532 px) while the hero grows.
    Today Experience is about 2.3 phone screens (audit §2 #4).
  - `:58` is CIMCO 2024's only line that names its users, NHL arena operations teams. It stays as
    that role's lead bullet. The `ul.also` NHL Arena row stays too, as it is on the site today
    (`index.html:58`, `:154`; fact sheet D18).
  - The résumé keeps every bullet, in its exact format.
  - **The reorder (b)** puts the business object first and keeps every sourced word, but its word
    order differs from the résumé's. It is the current role's lead bullet and the most jargon-dense
    line on the page as sourced.
  - **The summary sentence (c)** tells a business reader the whole story in one line, but it repeats
    the four dated entries directly below it, and `CLAUDE.md:10-11` asks for short, factual copy.
  - **The kind labels** fix audit issue 23 (heading names read "neuronet-arena Live demo") and say
    what each card's first link opens. They are a fixed label, not a second project line, so each
    card still has one line plus chips (`CLAUDE.md:10-11`).
- **Options.**
  - (a) The trim as above, with no reorder and no summary sentence.
  - (b) As (a), plus the site-only reorder of `:31`: "Maintain and debug the SalesOps data
    pipelines in production, and the Apache Airflow DAGs on Cloud Composer that run them."
    [src: resume/resume.tex:106].
  - (c) As (a), plus a summary sentence under `## Experience`, shown at ≥ 641 px only: "Four roles
    since 2023: automating manual workflows and training staff in Python at CIMCO Refrigeration,
    Power BI dashboards that replaced manual reporting at the Ontario Ministry of Transportation, and
    production data pipelines at Geotab." [src: index.html:27, :41, :54, :68; :60-61, :72-73; :45;
    :31-33]. (b) and (c) can both be chosen.
  - (d) Show every bullet with no disclosures. Projects land lower than today, about 3 scrolls at
    390.
  - (e) Delete the moved bullets from the page instead of hiding them.
  - Kind labels: ship, or leave them out.
- **Recommendation.** (a). Add (b) if Brett is happy for the site's word order to differ from the
  résumé's. (c) only if Brett wants the extra line. Ship the kind labels.
- **What changes.**
  - (a) PR 5 as written.
  - (b) PR 5 ships `:31` in the reordered form. The résumé is unchanged.
  - (c) PR 5 adds `<p class="section-lede">` with the sentence, under `## Experience`, hidden at
    ≤ 640 (§8.4), and re-measures the 1440 limit.
  - (d) PR 5 keeps all 13 bullets shown and drops the §4.3 limit. Proof stays one tap away through
    the nav.
  - (e) PR 5 removes the moved bullets instead of wrapping them: simpler markup, but the text leaves
    the page.
  - Kind labels left out: PR 6 drops `p.project-kind`; the badge stays in each card, beside the name
    as today.
- **Default.** (a): no reorder, no summary sentence. The kind labels ship.

#### 7. Internal names, and what "CAG" means
*Affects: PR 5 (fallback trims), PR 15, PR 16.*
- **Question.**
  - `[BRETT: OK to name SalesOps, Revenue Operations and the device status / database dimension / accounts master datasets?]`
  - `[BRETT: OK to name the Data Broker Tool?]`
  - `[BRETT: what does "CAG" stand for, and may it be expanded?]`
- **Why it matters.**
  - The names are already public on the site and in the résumé (`index.html:31-32`, `:47`, `:60`;
    `resume/resume.tex:106-107`, `:118`, `:127`). The redesign keeps them, except the dataset list.
  - "CAG" is never expanded, and a recruiter can't parse it (fact sheet §5.3).
- **Options.**
  - Keep the names, or remove some on the site (and in the résumé through PR 16).
  - Expand CAG, or keep the trim.
- **Recommendation.** Keep the names if the employers allow it. Expand CAG if it is a team name
  that may be public.
- **What changes.**
  - A "no" on a name: PR 15 rewords that bullet without it, and PR 16 does the same in the résumé if
    Brett wants.
  - A CAG answer: PR 15 restores `:60`'s full form with the expansion.
- **Default.** The names stay as published. The homepage ships `:32` trimmed and `:60` as
  "Automated controller data collection with a Python tool, replacing manual data entry." (§4.5).

#### 8. D1: how the site frames the MCP work
*Affects: PR 6 (the card), PR 8 (`/mcp/` facts).*
- **Question.** Two featured servers with 35 tools (the site and `/mcp/` today), five servers with
  69 tools (the résumé), or both? Should the other three servers be linked anywhere?
- **Why it matters.**
  - The sources:
    - `index.html:102-103` says "Two servers … 23 tools … 12 for Reddit";
    - `mcp/index.html:13` says "2 servers · 35 tools · 3 repositories · 114 offline tests";
    - `resume/resume.tex:151` says "Five Node.js servers exposing 69 tools".
  - 23 + 12 + 10 + 15 + 9 = 69 (measured, fact sheet §7.3), and all five run mcp-surface in CI.
  - Both are true in their own scope (PROMPT §1). Numbers must sit beside their source (P13), and
    inflation gets noticed (S10).
- **Options.**
  - (a) The site keeps 2 / 35, and the résumé keeps 5 / 69.
  - (b) "Two featured, five in all":
    - one MCP chip becomes "5 servers, 69 tools in all";
    - `/mcp/`'s "The repositories" list gains the other three, with facts from their READMEs (fact
      sheet §7.3).
  - (c) The site switches to 5 / 69: the card line, `/mcp/`'s facts, title and description.
- **Recommendation.** (a). The featured two carry the case study, and the other three are read-only
  or game APIs that add little to the pipeline and SQL work the site leads with.
- **What changes.**
  - (a) Nothing.
  - (b) PR 6 swaps "OAuth 2.0" for the new chip, and PR 8 adds three rows.
  - (c) PRs 6, 8 and 10 rewrite those lines.
- **Default.** (a).

#### 9. D14: TechNest "200+ tests" or "250+ tests"
*Affects: PR 6.*
- **Why it matters.** The site says "200+ tests" (`index.html:146`). The project README says "250+
  tests" (`technest@c7e590a:README.md:189`). A static count finds 262 test cases; PHPUnit wasn't
  run (fact sheet §7.5). "200+" is true but understated.
- **Options.** (a) "200+ tests"; (b) "250+ tests".
- **Recommendation.** (b): the README and the count both support it.
- **What changes.** One chip in PR 6.
- **Default.** (a), the site's current sourced wording.

#### 10. D17: "19 checks" or "19 rules"
*Affects: PR 6 (the mcp-surface chip), PR 8 (the Limits bullet), PR 9.*
- **Why it matters.** The résumé and `/mcp/` both say "19 checks" (`resume/resume.tex:145`,
  `mcp/index.html:80`). The package has 19 rules from 11 check modules (measured, fact sheet §7.2).
- **Options.** (a) "19 checks"; (b) "19 rules".
- **Recommendation.** (a): both sources already agree.
- **Default.** (a).

#### 11. D16: "MCP servers for Google Workspace and Reddit" or "MCP Servers for Claude Code"
*Affects: PRs 6 and 8 (no change by default).*
- **Why it matters.** The site and `/mcp/` say "AI assistant" (`index.html:102-103`,
  `mcp/index.html:18`). The résumé says "MCP Servers for Claude Code" (`resume/resume.tex:150`). The
  READMEs register with Claude Code and Claude Desktop (fact sheet D16).
- **Options.** (a) Keep the site's wording; (b) name Claude Code on the site.
- **Recommendation.** (a). It is true for every client the READMEs name.
- **Default.** (a).

#### 12. Footer `data-contact`
*Affects: PR 7, PR 9's demo footer.*
- **Question.** Add `data-contact` to the footer's GitHub, LinkedIn and Email links, so
  `contact_click` counts them?
- **Why it matters.**
  - On `/mcp/` and `/404.html` the footer is the only contact path, and it is unmeasured today
    (audit issue 12).
  - It needs no new parameter and no custom definition.
  - `contact_click` totals rise from the deploy date. That is extra clicks counted, not a change in
    meaning (§13.7).
- **Options.** (a) Add; (b) don't.
- **Recommendation.** (a).
- **What changes.**
  - (a) PR 7 and PR 9 as written.
  - (b) The same markup without the three attributes.
- **Default.** (a).

#### 13. C6: the analytics script's comment about `#education`
*Affects: PR 7.*
- **Why it matters.** `assets/js/analytics-events.js:5-7` says the events tell whether anyone
  reached `#education`. They can't: `#education` is a `div` (`index.html:172`), and only
  `section[id]` is observed (`:68`). This is fact sheet C6.
- **Options.**
  - (a) Fix the comment to say `#skills` (comment only).
  - (b) Make it true by adding the `education` value (decision 25).
  - (c) Leave it.
- **Recommendation.** (a), or (b) if Brett wants decision 25.
- **Default.** (a).

#### 14. `/mcp/` copy changes
*Affects: PR 8.*
- **Question.** Add four things to Brett's merged long-form page (§8.18)?
  - a "For" line: "For anyone who lets an AI assistant send email, edit a calendar or post on their
    behalf, and needs to know when what it can do changes." [src: mcp/index.html:17-19, :40-42];
  - the diagram in two labelled rows, moved under the For line: "At runtime" (an AI assistant, the
    MCP server, Google Workspace or Reddit over OAuth, [src: mcp/index.html:17-19, :72, :76]) and
    "In CI" (today's three CI steps, unchanged);
  - a reworded sentence tail, "…runs mcp-surface, in the "In CI" row at the top of this page.";
  - a two-bullet "Limits" section.
  - "The repositories" stays the second h2, where it is today. It holds the page's one plain cost
    result: the Google server asks for no restricted scopes, which keeps it out of Google's paid
    security assessment (`mcp/index.html:72`).
- **Why it matters.**
  - Case studies that work follow one outline, from problem to limits (P11: deshraj-jogiya,
    shaundataanalytics, beingbrute, shao).
  - A hiring manager wants the system flow in five seconds (S06). Today's three CI steps show the
    check, not the system the page covers ("how the three fit together", `mcp/index.html:24`).
  - "For:" lines (I3) and honest limits (I20) are ideas the plan adopts. The For line names who the
    page is for and the problem without MCP or CI shorthand, for the business reader (§4.4).
  - Each line is sourced (§8.18), but each is new wording on Brett's page.
- **Options.**
  - (a) Ship all four.
  - (b) Ship none: the CI steps stay where they are today, at `:35-39`, as a plain list with no
    "At runtime" row; the sentence keeps its colon; no For line and no "Limits".
  - (c) Ship the items Brett names.
- **Recommendation.** (a).
- **What changes.**
  - (a) PR 8 as written.
  - (b) PR 8 ships only the markup and accessibility fixes (`.case-facts` chips, the `pre` region,
    `.post-next`, link targets). Nothing on the page moves.
  - (c) Only the named items.
- **Default.** (a).

#### 15. npm mcp-surface is still 0.3.0, but `/mcp/` says "It now reads its version from its own package"
*Affects: PR 8 (the sentence), PR 9 (the version chip), and a possible checks.js rebuild.*
- **Why it matters.**
  - `mcp/index.html:146-149` says the checker now reads its version from its own package. The fix is
    in the repo's `[Unreleased]` section (`mcp-surface@ea93b87:CHANGELOG.md:8-16`).
  - npm's `latest` is still 0.3.0 (fact sheet §7.2: registry fetched 2026-10-21 13:43 UTC,
    measured; re-checked with `npm view mcp-surface version` on 2026-10-23, still 0.3.0, measured),
    so a user who installs from npm still sends the hard-coded 0.1.0.
  - The playground's bundled checks are 0.3.0 too (`mcp-surface/checks.js:1`).
  - A claim the code contradicts is never repeated as fact (§0).
- **Options.**
  - (a) Brett publishes the next release. The sentence becomes true. Rebuild `checks.js` per
    `README.md:83-92`, and update the version in the How-it-works item and the chip.
  - (b) Reword the tense until then (§16 PR 8 item 2): "It now reads its version from its own
    package (merged after 0.3.0, so the next npm release carries it), and an end-to-end test checks
    what a server actually receives."
  - (c) Leave it.
- **Recommendation.** (a). The same `[Unreleased]` section also clears every open `npm audit`
  advisory (`CHANGELOG.md:18-22`). Use (b) until the release lands.
- **What changes.**
  - (a) A follow-up PR regenerates `checks.js` and updates two version strings. `/mcp/` keeps its
    sentence.
  - (b) PR 8 changes one sentence.
  - (c) Nothing. Not recommended.
- **The builder checks first.** PR 8 runs `npm view mcp-surface version` before rewording. If it
  prints a version above 0.3.0, the release has happened: PR 8 keeps today's sentence and the plan
  takes (a).
- **Default.** (b), unless that check shows a newer release.

#### 16. D15: "all 30 unit tests" on `/mcp/`
*Affects: PR 8 (no change by default).*
- **Why it matters.** `mcp/index.html:62` says the Zod upgrade passed "all 30 unit tests". The suite
  has 33 today (measured). 30 is what mcp-surface's README states for the time of the upgrade
  (`mcp-surface@ea93b87:README.md:94`); it can't be checked from the shallow clone (fact sheet
  §7.3).
- **Options.** (a) Keep; (b) drop the number.
- **Recommendation.** (a): both sources agree on it.
- **Default.** (a).

#### 17. GA on demo pages
*Affects: PR 9, PR 11a, PR 12, PR 13.*
- **Question.** Should `/mcp-surface/` and the future demos load GA? `/neuronet-arena/` and
  `/mcp-surface/` load none today (fact sheet §11).
- **Why it matters.**
  - A visit that starts from a shared demo link is invisible (audit issue 11). The homepage's
    `project_click` only counts visits that start on the homepage.
  - DNT behaviour would match the site's.
- **Options** (§13.7):
  - (a) None.
  - (b) mcp-surface only, through front matter and `{% include analytics.html %}`. It sends no
    `section_view`, since the page has no `section[id]`.
  - (c) Also `/mazefinder/`, which is built in this repo.
  - (d) Also neuronet-arena. That needs an upstream snippet, and its `section#parity` would start a
    new `section_id` value, `parity`.
- **Recommendation.** (b) plus (c).
- **What changes.**
  - (b)/(c) PR 9 and PR 13 add two lines each. Brett sees the demo pages in GA from the deploy.
  - (d) PR 11a also removes or renames `id="parity"` upstream.
- **Default.** (a), none, as today.

#### 18. Version chip or "Updated" date on demo pages
*Affects: PR 9; PRs 12 and 13 follow the same rule.*
- **Why it matters.**
  - §5.4 gives demo pages an "Updated" date, and §8.20 uses a version chip instead ("Checks from
    mcp-surface 0.3.0").
  - A stale date is worse than none: darshshah's "Present" role still read "3 mos" ten months on
    `[darshshah:mobile-fold.webp]` (A19).
- **Options.**
  - (a) A version chip, changed in the same step as a `checks.js` rebuild.
  - (b) "Updated <Month YYYY>", set in the PR that last changed the folder.
  - (c) Neither.
- **Recommendation.** (a).
- **What changes.**
  - (b) PR 9 writes the month instead.
  - border-waits shows its own "Last successful run" and "Last attempt" lines from `manifest.json`
    and `status.json` either way (§14.9, the brief).
- **Default.** (a).

#### 19. D19: JSON-LD `alumniOf`, and the other JSON-LD fields
*Affects: PR 10.*
- **Why it matters.** The Person block says `alumniOf` University of Windsor
  (`_layouts/default.html:21`) while the page says "Graduating 2027" (`index.html:7`). schema.org's
  `alumniOf` means a former member. The block also lacks fields the page already shows (audit issue
  22).
- **Options.**
  - (a) `affiliation` now and `alumniOf` after graduation, plus `knowsAbout` (the six glance tools),
    `email` and `worksFor.url` (§12.3).
  - (b) Keep `alumniOf`, and add the rest.
  - (c) Leave the block as it is.
- **Recommendation.** (a).
- **Default.** (a).

#### 20. The upstream neuronet-arena PR
*Affects: PR 11a (in `brettadams0/neuronet-arena`) and PR 11b (the re-sync here).*
- **Question.** May the builder open a PR in the neuronet-arena repo with §8.20's upstream list?
  - the shell header with absolute URLs, the skip link and `main`;
  - self-hosted fonts and fallback faces;
  - a focus ring on links;
  - `td.pending` in `--dim`;
  - `<th></th>` → `<td></td>`;
  - the title naming Brett, and a canonical link;
  - footer order;
  - `og.png` re-encoded;
  - the `summary` target ≥ 24 px.
  Optionally, it also removes the "How it was built" link, so this repo's one local difference
  (`README.md:75-76`) ends.
- **Why it matters.**
  - `neuronet-arena/` is synced and never edited here (`CLAUDE.md:13`).
  - It is Brett's favourite demo. It has the site's worst CLS, 0.1643 on mobile (audit issue 5), a
    header that doesn't say whose work it is (issues 13, 20), and an axe finding (issue 27).
- **Options.**
  - (a) Everything, including removing the link.
  - (b) Everything except removing the link.
  - (c) Only the font and CLS fix.
  - (d) No upstream change.
- **Recommendation.** (a).
- **What changes.**
  - (a) PR 11a, then PR 11b re-syncs and drops the README's local-difference note.
  - (b) The same; the note stays.
  - (c) A smaller PR 11a. `budget.json` still tightens CLS to 0.05.
  - (d) Neither PR. The CLS guard stays at 0.2, and `--a11y` on `/neuronet-arena/` stays
    informational.
- **Default.** The builder opens (a) as a **draft** PR in the neuronet-arena repo. Nothing in this
  repo changes until Brett merges it.

#### 21. The new project: build border-waits?
*Affects: PR 12 (and work in a new repo). Includes the deploy change of hosting data on the project
repo's own Pages.*
- **Question.** Build the recommended data project (§14)? It is an hourly pipeline over CBSA and
  U.S. CBP border wait times, modelled and tested with dbt on DuckDB, published as Parquet to its
  own repo's Pages, and queryable in the browser with DuckDB-Wasm.
- **Why it matters.**
  - Brett's two most recent roles are pipelines, SQL, CI/CD and BI (`index.html:31-33`, `:45`), and
    both live demos are something else (§14.1).
  - No verified claim in the 29-site set describes a scheduled pipeline whose freshness or test
    status a visitor can see (appendix, "Set-level check for the new project"; §3.9).
  - Hiring evidence asks for an end-to-end project with a diagram, tests and data-quality controls
    (S06, S07, S10).
  - A prototype matched dbt's compiled SQL in DuckDB-Wasm exactly, with the same DuckDB version in
    CI and in the browser (237 rows, same MD5; measured, §14.3).
  - Size M, 50–65 h (**estimated**, §14.7), including a two-week burn-in before the data goes public.
    Free to run (§14.5).
  - Its operations are designed in (§14.4, §14.8 R2): a failed run opens an issue, commits nothing
    and redeploys the last good export; the page says "Last successful run" and "Last attempt".
- **Options.** (A) border-waits; (B) claims-check; (C) a Bank of Canada rates mart; none (§14.2).
- **Recommendation.** (A).
- **What changes.** Yes: Brett creates the repo, and the project follows §14.7's milestones. PR 12
  lands after M3–M4, once the `data` branch holds ≥ 21 days of readings after the M1.5 burn-in
  (§14.7). No: nothing is built, and the brief stays queued.
- **Default.** The brief stays queued. **Nothing is built.**

**Sub-decisions, if Brett says build** (from §14.10 and the brief's Decisions):

| # | Question | Options | Recommendation | Default |
|---|---|---|---|---|
| 21a | Repo name `[BRETT: repo name, suggested border-waits-pipeline]` | any name **other than** `border-waits` (collision behaviour undocumented, §5.1) | `border-waits-pipeline` | nothing created until Brett names it |
| 21b | **Data hosting (deploy change):** may `/border-waits/` fetch live data from the project repo's own Pages at runtime? | (a) yes, with a synced fallback snapshot; (b) snapshot only, re-synced by hand; (c) a workflow or bot that commits in this repo (changes this site's deploy) | (a): the data is deployed by the project repo's own workflow, and this repo's build and deploy don't change | (a); (c) never without sign-off |
| 21c | Where the page lives | (a) this repo's `/border-waits/`, synced from the project's `web/`; (b) wholly on the project's Pages, with the card linking out | (a): the shared shell, like neuronet-arena | (a) |
| 21d | Third-party hosts on click: jsDelivr (pinned `@duckdb/duckdb-wasm@1.32.0`) and `extensions.duckdb.org` | accept, pinned; or self-host | accept; never self-host the 34 MB wasm here | accept |
| 21e | Homepage layout | (a) a second full-row featured card after neuronet-arena; (b) a half-width card, with `.project-wide` covering the odd count | (a): no card is demoted (§5.5) | (a) |
| 21f | Scope | all Canada–US crossings in both feeds, with the three Windsor–Detroit crossings featured; or Windsor–Detroit only | all, featured three | all |
| 21g | Bank of Canada FX join (milestone M5) | yes / no | later | no |
| 21h | A résumé line once it ships | add / none | add once M4 ships | none (résumé unchanged) |
| 21i | `[BRETT: any personal line about why these crossings, if true]` | a true line from Brett / none | none unless Brett offers one; no "my commute", since no source says where Brett lives (fact sheet §2) | none |
| 21j | `[BRETT: licence for the new repo, suggested MIT]` | MIT / other | MIT, matching Brett's other repos (`mcp-surface/index.html:87`) | no licence line on the page until set |
| 21k | claims-check (candidate B) as a CI job in the new repo | build / not | later, in the new repo only | not built |
| 21l | GA on the page | follows decision 17 | — | none |

#### 22. An odd number of half-width cards: `.project-wide`, or demote a card?
*Affects: PR 13, which is where `.project-wide` lands (§8.7, §8.21).*
- **Why it matters.** The MazeFinder handoff leaves open which card drops to `ul.also` when a fifth
  half-width card leaves a gap (`handoff/mazefinder-live-demo.md:150-152`). "Each project appears
  once" holds either way.
- **Options.**
  - (a) The last half card spans the row in the compact layout (`.project-wide`, §8.7), with no
    demotion.
  - (b) Demote a card Brett names to one `ul.also` line.
  - (c) MazeFinder becomes a third featured card.
- **Recommendation.** (a).
- **What changes.**
  - (a) PR 13 adds the `.project-wide` CSS to `site.css` and puts the class on TechNest.
  - (b) PR 13 turns that card into a `ul.also` row that keeps its `data-project`, and adds no
    `.project-wide` CSS.
  - (c) PR 13 uses `.project-featured`, and adds no `.project-wide` CSS.
- **Default.** (a).

#### 23. MazeFinder: the card line and the dates
*Affects: PR 13, PR 16.*
- **Question.**
  - `[BRETT: one line once walls ship]` (§8.7).
  - `[BRETT: MazeFinder dates Mar-Sep 2024 confirmed?]`
- **Why it matters.**
  - The card line must never say "solves mazes" before walls exist (fact sheet C2).
  - The résumé's dates (`resume/resume.tex:159`) can't be checked: the clone has one commit, dated
    2026-07-31 (fact sheet §7.6).
- **Default.** The card uses today's sourced line, "C++ genetic algorithm with fitness evaluation on
  a hand-rolled thread pool." (`index.html:155`). No dates on the site. The résumé is unchanged.

#### 24. New `section_id` value `hero`
*Affects: PR 14.*
- **Why it matters.** It gives first-screen dwell, but `section_view` would fire on nearly every page
  view. GA4's engagement time already covers most of this (§13.8 N1).
- **Options.** Add / don't.
- **Recommendation.** Don't.
- **What changes.** Add: `id="hero"` on `section.hero`, and `measure.mjs`'s `EXACT_SECTIONS['/']`
  gains it.
- **Default.** Not added.

#### 25. New `section_id` value `education`
*Affects: PR 14.*
- **Why it matters.**
  - It answers whether anyone reaches Education, the question the script's comment asks (C6).
  - At ≥ 981 px it fires whenever Skills does, since they sit side by side. The signal comes from
    phones and tablets (§13.8 N2).
- **Options.** Add / don't.
- **Recommendation.** Optional. Add it if the phone question matters to Brett.
- **What changes.** Add: `div#education` becomes `section#education[aria-labelledby=education-title]`,
  the comment keeps "#education", and `measure.mjs`'s `EXACT_SECTIONS['/']` gains it.
- **Default.** Not added.

#### 26. New event `nav_click`
*Affects: PR 14.*
- **Why it matters.** It shows whether the new phone section row (audit issue 1, High) and the jump
  link are used. Its parameters are `target_section` (the link's target) and `nav` (`header`,
  `sections`, `hero`). It sends no `section_id`, so click counts stay out of the Section dimension
  that reach and dwell reports use. It needs two new custom definitions, `target_section` and `nav`
  (§13.8 N3, §13.9).
- **Options.** Add / don't.
- **Recommendation.** Add, for a few months after PR 3.
- **Default.** Not added.

#### 27. New event `job_more_open`
*Affects: PR 14.*
- **Why it matters.** It shows whether hiding bullets behind "N more" hides something people want.
  That is the main cost of decision 6. Its one parameter is `item` (the role: `geotab`, `mto`,
  `cimco-2024`, `cimco-2023`); it sends no `section_id`, for the same reason as #26. It needs one
  new custom definition, `item` (§13.8 N4).
- **Options.** Add / don't.
- **Recommendation.** Add, for a few months after PR 5.
- **Default.** Not added.

#### 28. Run the performance budget's size keys in CI
*Affects: PR 18. This is a CI change.*
- **Why it matters.**
  - CI only builds and checks links (`.github/workflows/ci.yml:25-36`), so a heavy image or a new
    script passes CI today.
  - Without it, the whole budget depends on every builder running `measure.mjs` by hand. An
    engineer reading the repo sees a budget file that nothing enforces.
  - Size keys are deterministic, so they are the cheap part to automate. Timing on shared runners
    varies, so only size keys would gate.
  - Running `measure.mjs` there needs Playwright and Chromium installed on every run: an extra 3–5
    minutes (**estimated**).
- **Options.**
  - (a) No CI change; every PR runs the budget locally (§16.2).
  - (b) A second CI job with size keys only (`handoff/tools/budget-ci.json`, `--runs 1`), as §16
    PR 18 specifies.
- **Recommendation.** (b).
- **What changes.** (b) PR 18 as written. (a) Nothing; PR 18 isn't opened.
- **Default.** (a), because a CI change needs Brett's sign-off (§0).

#### 29. Sift on the site, and the stale `sift/` mirror (D21, C5)
*Affects: PR 17.*
- **Question.** Should Sift appear on the site? Separately, what happens to the `sift/` mirror?
- **Why it matters.**
  - Sift is an Android app with no in-browser path, so it could only ever be one `ul.also` line
    (§5.5).
  - The mirror is excluded from the build (`_config.yml:31-32`), but it is public on GitHub and
    holds 92 of the repo's 141 tracked files (measured, fact sheet §12).
  - It is stale (C5): it says "Current build: 0.2.0" against upstream 0.2.8, calls approve-and-trash
    "still unproven" though upstream now has device tests for it, and links a 0.2.0 APK that likely
    no longer exists upstream.
- **Options.**
  - Sift on the site: (a) no; (b) one line, with upstream facts only.
  - The mirror: (i) leave it; (ii) re-sync it to upstream; (iii) delete it, since Sift has its own
    repo.
- **Recommendation.** Sift (a) unless Brett wants an Android project shown. Mirror (iii).
- **What changes.** (b) PR 17 adds the row after the mirror is handled. (ii)/(iii) PR 17 also
  re-syncs or removes `sift/`.
- **Default.** Sift (a), not shown. Mirror (i), untouched.

#### 30. Availability and dates (D4, D5)
*Affects: PR 15. The status line, the education dates, the glance card, the JSON-LD and the social
card.*
- **Question.** `[BRETT: graduation month 2027, earliest full-time start, Geotab internship end date, any further co-op term?]`
- **Why it matters.**
  - What the sources say:
    - the site gives only the year (`index.html:7`, `:177`);
    - the résumé says "Sep 2022 - Present" and nothing about availability (`resume/resume.tex:88`;
      D4, D5).
  - Recruiters check timing early (H2: S02).
  - "Graduating" is the recruiter's level signal, the one fact that says "new grad", so the
    answered status line keeps it (§4.4's recruiter check needs it on the 390 first screen).
  - "Present" goes stale when the internship ends (A19).
- **What changes.** PR 15:
  - the status line becomes `Graduating [BRETT: month] 2027 · Open to full-time roles` (with #31's
    role answer, `Graduating [BRETT: month] 2027 · Open to full-time [BRETT: role family] roles`).
    At ≤ 640 the line may wrap to two lines;
  - an earliest start that differs from graduation goes in the glance "Looking for" row, not the
    status line;
  - education becomes "2022 – [month] 2027 · Windsor, ON";
  - the glance "Now" row, the JSON-LD `jobTitle` and `worksFor`, and the social card change when
    Geotab ends.
  - The résumé changes only through PR 16, for example "Expected [month] 2027".
- **Default.** The sourced lines: "Graduating 2027 · Open to full-time roles", "2022 – 2027 ·
  Windsor, ON", "Present". The résumé is unchanged.

#### 31. Target roles, and where they go
*Affects: PR 15.*
- **Question.** `[BRETT: target roles wording: data engineering, data analytics, software engineering?]`
  Where should it show?
- **Why it matters.**
  - No site or résumé line names target roles (fact sheet §4). The examples in the question come
    from Brett's brief, which gives them for orientation only, so the spec treats them as unconfirmed.
  - Four sites put the target role and availability right next to the résumé button (P2: beingbrute,
    deshraj-jogiya, richardshuai, mujtabasaqib).
  - The design puts a "Looking for" row in the glance card, right after "Studying" (§6.3). Until
    Brett answers, the row would only repeat the status line's "Open to full-time roles", so it is
    left out.
- **Options.**
  - (a) The glance row only.
  - (b) The glance row and the status line:
    `Graduating [BRETT: month] 2027 · Open to full-time [BRETT: role family] roles`.
  - (c) Also the kicker and the site tagline, which drives the homepage title.
- **Recommendation.** (b), once the wording exists.
- **What changes.** PR 15 adds the "Looking for" row after "Studying" (§6.3), and for (b) the status
  line's role family. (c) also changes `_config.yml`'s `tagline` (§12.1).
- **Default.** No "Looking for" row. The status line ("Graduating 2027 · Open to full-time roles",
  `index.html:7`) and the kicker are unchanged.

#### 32. Location, relocation or remote; work authorization
*Affects: PR 15.*
- **Question.**
  - `[BRETT: where are you based, and open to relocation or remote?]`
  - `[BRETT: work authorization (Canada; US or elsewhere?)]`
- **Why it matters.**
  - Hiring staff on forums say location and right to work are checked before skills (H10: S11, S13,
    Tier 2).
  - No source gives a personal location, only job cities and the university's (fact sheet §2).
- **What changes.** PR 15 adds the glance "Based in" row after "Looking for" (§6.3), and optionally
  a line in `#contact` (§8.15). Border-waits copy never implies where Brett lives (§14.3).
- **Default.** Both rows are left out.

#### 33. Impact numbers and outcomes for each role
*Affects: PR 15.*
- **Question.**
  - **Geotab, what changed** (asked first; §4.5):
    - `[BRETT: what do the SalesOps and Revenue Operations tables feed (which reports or teams rely on them), and what improved after your work?]`
    - `[BRETT: one DAG failure you debugged: cause and fix?]`
    - `[BRETT: what does "staging validation" check before production (row counts, schema diff, tests)?]`
    - `[BRETT: did a SQL change cut runtime or bytes scanned, and by how much?]`
    - `[BRETT: how many tables migrated, and was there downtime?]`
  - **Geotab, volume:** `[BRETT: DAGs maintained, run frequency, tables/datasets migrated, rows/day, deadlines met?]`
  - **MTO:** `[BRETT: dashboards built, business units, one hours-to-minutes example, teams onboarded to the Data Broker Tool?]`
  - **CIMCO 2024:** `[BRETT: how many arenas or teams used the Flask apps; technicians using the thermal viewer?]`
  - **CIMCO 2023:** `[BRETT: how many departments adopted the Power Platform tools; turnaround before and after?]`
- **Why it matters.**
  - Only "over 20 employees" is a number today (`index.html:61`).
  - The business persona is the site's weakest score, 3 (audit §2).
  - Geotab is the current role, and by default its four bullets are the site's only data-engineering
    evidence. As sourced they read as duties, and none says what changed. Volume counts (DAGs,
    rows/day) tell a hiring manager or a business reader the least. Who relies on the data, a failure
    debugged, what validation checks, and a runtime or cost change say more (H5, H8, S06).
  - "Short, with numbers" (S09); but inflation gets noticed (S10), and unsourced stat tiles are an
    anti-pattern (A11).
- **What changes.** PR 15 puts each answer into the bullet it measures, in §4.5 item 3's form; the
  Geotab answers go into `:31` or `:34`. It adds no new bullet and no tile. A number may also
  replace an item's wording in the glance "Results" row (#4).
- **Default.** The sourced wording, with no numbers. The five Geotab questions have the same
  fallback: each bullet ships as sourced (§4.5).

#### 34. The 2023 CIMCO title
*Affects: PR 15.*
- **Question.** `[BRETT: the 2023 CIMCO title: "Digital Solutions" what (intern? co-op? summer student?)]`
- **Why it matters.** Both sources give a team-like name with no role noun and no "Co-op"
  (`index.html:68`, `resume/resume.tex:132`). Internships read best as real roles with their term
  (P7).
- **Default.** "Digital Solutions". The glance "Before" row gives only the year for the 2023 term,
  and "co-op" for 2024, where the source says so (§6.3).

#### 35. D3: the canonical degree wording
*Affects: PR 15, PR 16.*
- **Why it matters.**
  - The two sources word it differently:
    - the site says "Honours Computer Science, Software Engineering Specialization (Co-op)"
      (`index.html:176`);
    - the résumé says "Honours Computer Science with Software Engineering Specialization Co-op"
      (`resume/resume.tex:87`).
  - Education dates are decision 30.
- **Options.** The site's wording, the résumé's, or one new form in both.
- **Default.** Each source keeps its own wording. The glance card uses the short form "Honours
  Computer Science, University of Windsor" (§6.3).

#### 36. D18: the NHL Arena Web Interface
*Affects: PR 6 (the `ul.also` line's wording), PR 15.*
- **Question.**
  - `[BRETT: NHL Arena Web Interface: any screenshot or detail you may show publicly?]` Keep it as a
    project entry?
  - Ship the `ul.also` line's new wording (§8.9)? "Live refrigeration data, alarms and a thermal
    imagery viewer for NHL arena operations teams and technicians. Built at CIMCO; internal."
    [src: index.html:58, :154]. Today's line is "Live refrigeration data and alarms for NHL arenas.
    Built at CIMCO; internal." (`index.html:154`).
- **Why it matters.** The site lists it as an internal project (`index.html:154`) and as a CIMCO 2024
  bullet (`index.html:58`). The résumé has it as a CIMCO bullet (`resume/resume.tex:125-126`). The
  redesign keeps both on the site: `:58`, trimmed, is CIMCO 2024's lead bullet (#6). There is no
  repo or public material. The new wording names who used the work, the clearest sign for a
  business reader that real people relied on it; `index.html:58` and `resume/resume.tex:125` agree
  on those users.
- **Options.** The new wording, or today's line. Separately, a detail or screenshot only if Brett
  supplies one.
- **What changes.** The wording is one string in PR 6. A detail or screenshot is a PR 15 change.
- **Default.** One `ul.also` line with the new wording: "internal", no link, no image.

#### 37. D12: project dates on the site
*Affects: none by default.*
- **Question.** `[BRETT: project dates for neuronet-arena, PromptPal, TechNest if the redesign shows dates]`.
  Show dates on the cards?
- **Why it matters.** Only the résumé's three projects have dates (`resume/resume.tex:141`, `:150`,
  `:159`). A date that goes stale is worse than none (P18's caution, darshshah).
- **Default.** No dates on cards. `/mcp/`'s eyebrow and the demo version chips carry the dates that
  stay true.

#### 38. A headshot
*Affects: none by default.*
- **Question.** `[BRETT: headshot (yes/no; file)?]`
- **Why it matters.** There is none in `assets/img/` today. No pattern in §3.4 depends on one.
- **What changes.** Yes: a later PR specifies where it goes, since the hero has no image slot (§6),
  with `alt="Brett Adams"` (§10.8) and a JSON-LD `image` (§12.3).
- **Default.** None.

#### 39. The phone number on the site
*Affects: none.*
- **Question.** `[BRETT: show the phone number on the site?]`
- **Why it matters.** It is in the résumé PDF (`resume/resume.tex:79`), which the site links, but not
  on the site.
- **Default.** Not shown.

#### 40. A custom domain
*Affects: none by default.*
- **Question.** `[BRETT: custom domain (yes/no; which)?]`
- **Why it matters.** A domain change also moves:
  - the Google OAuth consent screen's policy URL (`google-workspace-mcp@1f1fc45:README.md:212`);
  - the synced demo's absolute links (`neuronet-arena/index.html:12-13`, `:28`, `:147`);
  - every canonical and `og:url`;
  - the hostname GA reports.
- **Default.** None (§5.6).

#### 41. Team-project lines (I7) and an employer post about the internship (I8)
*Affects: none.*
- **Question.**
  - `[BRETT: was any project team work, and what was your part?]`
  - `[BRETT: is there an employer post about your intern work that may be linked?]`
- **Why it matters.** Both are ideas seen on one site each: shud's "my part" line and florea's
  employer post (§3.5).
- **Default.** Not used.

#### 42. C1 / D2: the résumé's "CI on Node 20 and 22"
*Affects: PR 16.*
- **Why it matters.**
  - `resume/resume.tex:155` says each MCP server has CI on Node 20 and 22. None does:
    - google-workspace-mcp runs 22.x and 24.x and requires Node ≥ 22;
    - the other four run 20.x, 22.x and 24.x (fact sheet C1).
  - The site doesn't repeat the line: `/mcp/` says "on Node 22 and 24", which is true for both
    featured servers (`mcp/index.html:154`).
- **Options.** (a) "CI on Node 22 and 24", true for all five; (b) "Node 20–24"; (c) leave it.
- **Recommendation.** (a).
- **Default.** The résumé is unchanged. The site never repeats the claim.

#### 43. C2, C3, C4 / D13: the résumé's MazeFinder lines
*Affects: PR 16.*
- **Why it matters.** Three lines in `resume/resume.tex:160-164` are contradicted by the code:
  - **C2.** "solving 20x20 mazes" and "Evolves paths through 20x20 mazes". The grid has no walls
    (`MazeFinder.cpp:152`; README "Honest scope"; `handoff/mazefinder-live-demo.md:42-43`).
  - **C3.** "dynamic mutation and crossover strategies". Crossover is a single cut point chosen at
    random (`MazeFinder.cpp:117-125`), mutation changes one random gene per child (`:128-135`), and
    both run the same way every generation (`:203-207`); nothing changes rate or strategy over time
    (fact sheet C3).
  - **C4.** "so new maze configurations load without code changes". The maze is built in `main`,
    with no loader.
- **Options.**
  - Reword now: "on a 20x20 grid", "single-point crossover and mutation", and drop C4's second half.
  - Wait for the walls PR, which makes C2 and C4 true but not C3.
  - Leave them.
- **Recommendation.** Reword C3 now, since it stays false after the walls PR. Reword C2 and C4 now,
  or with the walls PR.
- **Default.** The résumé is unchanged. The site never repeats any of the three (§8.7, §8.9).

#### 44. D11: which projects the résumé lists
*Affects: PR 16.*
- **Why it matters.** The résumé lists mcp-surface, the MCP servers and MazeFinder only
  (`resume/resume.tex:140-165`). The site also shows neuronet-arena (Brett's favourite), PromptPal,
  TechNest and the NHL arena work.
- **Default.** Unchanged.

#### 45. D8, D9: the skills lists and their group names
*Affects: PR 16 (the site's Skills copy is unchanged either way, §8.13).*
- **Why it matters.**
  - Only the site lists PHP, Linux and Next.js. Only the résumé's skills list VBA and Power Platform.
  - The groups differ: Languages / Data / Tools / Web against Programming Languages / Data & Cloud /
    Technologies (`index.html:166-169`, `resume/resume.tex:96-98`).
- **Default.** Both unchanged.

#### 46. D10: date format
*Affects: PR 16.*
- **Why it matters.** The site writes "Jan – Aug 2025"; the résumé writes "Jan 2025 - Aug 2025". The
  dates agree (fact sheet §5).
- **Default.** No change.

#### 47. D20: "PROȷECTS" in the PDF's text layer
*Affects: PR 16.*
- **Why it matters.** Text extraction reads the heading as "NOTABLE PROȷECTS", with a dotless j
  (U+0237). A résumé parser may read the same (measured with `pdftotext` and `pypdf`, fact sheet D20).
  The PDF's format is to be kept exactly.
- **Options.** Brett tests a fix on Overleaf that keeps the look; or leave it.
- **Default.** No change.

#### 48. The résumé PDF path and `section_id` renames: none proposed
*Affects: none.*
- **Why it matters.**
  - `resume_download` matches `/Resume_BrettAdams\.pdf$/` (`assets/js/analytics-events.js:108`).
  - `section_view` continuity depends on `experience`, `projects`, `skills` and `contact`
    (`:68`).
  - This spec renames, merges or moves none of them, and never adds a query string, fragment or
    `download` attribute to a résumé link (§13.3–13.4). Its only new values are decisions 24 and 25.
- **Default.** The path stays `assets/resume/Resume_BrettAdams.pdf`, and the four ids stay as they
  are. Any later proposal to change either needs its own decision.

#### 49. D22: where the tokens live, and `CLAUDE.md:12`
*Affects: PRs 1, 2, 9, 11a, 12, 13. Numbered out of order; answer it with #2.*
- **Question.** Where do §7.1's tokens and font faces live: at the top of `assets/css/site.css`, as
  `CLAUDE.md:12` says today, or in a new shared `assets/css/tokens.css`?
- **Why it matters.**
  - `CLAUDE.md:12` is Brett's standing-rule file. It says "the tokens in `assets/css/site.css`
    (shared with the demos)". A change to a standing rule's wording defaults to keeping the rule, and
    builders never edit `CLAUDE.md`.
  - The sources already disagree a little: the MazeFinder handoff points at
    `mcp-surface/styles.css` (`handoff/mazefinder-live-demo.md:16-18`), and the site and the demos
    differ on radius (fact sheet D22).
  - A separate file would be one place for the site and the demos built here, but costs one more
    render-blocking request on every page (the block measured as a separate file: 5,415 B, 1,788 B
    gzipped, §7.1). The synced demos (neuronet-arena, border-waits) carry a copy either way, because
    their repos also run on their own (§7.11).
- **Options.**
  - (a) Create `assets/css/tokens.css` with the block, link it from `_layouts/default.html` before
    `site.css` and from the demos built here (`mcp-surface/`, `/mazefinder/`), and Brett updates
    `CLAUDE.md:12` to name it in the same change.
  - (b) Keep the tokens at the top of `site.css`, between two marker comments. `mcp-surface/styles.css`
    and `mazefinder/styles.css` carry a copy of the block in their own `:root`, as neuronet-arena
    does. `demo-shell.css` holds shell layout only and reads each page's own `:root`.
- **Recommendation.** (b), unless Brett prefers one shared file. It keeps `CLAUDE.md:12` true as
  written, saves a request per page, and PR 9's one-liner catches a copy that drifts (§17 K23).
- **What changes.**
  - (a) PR 1 creates `tokens.css` and its README row, PR 2 adds the font faces there, PR 9 and PR 13
    link it instead of copying, and the rollback notes for PRs 1, 2 and 9 apply (§16.2). PR 11a and
    PR 12 are the same either way.
  - (b) §16 as written.
- **Default.** (b): the tokens stay in `site.css`, and `CLAUDE.md:12` stays true. The builder never
  edits `CLAUDE.md`.

#### 50. The GA test hit on 2026-10-21
*Affects: none.*
- **Why it matters.** One development run of `measure.mjs` with `--allow-ga`, at 03:57 UTC against
  the local build, probably sent one `page_view` with a `127.0.0.1` page location to `G-Z3KW627PQQ`.
  Every later run blocked GA (§2.1, research README).
- **Options.**
  - (a) Ignore it: one hit.
  - (b) Exclude hostname `127.0.0.1` in the reports Brett uses.
  - (c) Ask GA to delete it.
- **Recommendation.** (a).
- **Default.** (a), no action. The tool's default blocks GA, and §16 never uses `--allow-ga`.

#### 51. Card copy in plain words: mcp-surface, MCP servers, PromptPal
*Affects: PR 6. Numbered out of order, because it was added after the list was set.*
- **Question.** Ship four copy changes on the homepage's project cards (§8.6, §8.8)?
  - **mcp-surface line:** "Checks an MCP server the way an AI assistant sees it, and fails the build
    when a tool changes, so every change gets a review." [src: index.html:117; mcp/index.html:20-21,
    :40-42]
  - **mcp-surface kind:** `Live demo · runs in your browser`, as on neuronet-arena. The npm package
    stays a named link in the card's links.
  - **MCP servers chip:** `Snapshot-checked in CI` becomes `Tools checked on every change` (29
    characters) [src: mcp/index.html:40-42: a tool change "fails the build until the snapshot is
    updated"]. "OAuth 2.0" stays, since recruiters match it.
  - **PromptPal line:** "Write an AI prompt once as a template with fill-in fields. It flags
    mistakes that quietly ruin answers and exports a ready-to-run Claude API call."
    [src: promptpal@82ffb5d:README.md:6-8, fact sheet §7.4]. Its kind label is "Web app on Vercel"
    [src: promptpal@82ffb5d:README.md:124-125] (#6), and its chips keep the engineering facts.
- **Why it matters.**
  - The business reader is the site's weakest persona score, 3, and one reason is that project lines
    are technical (audit §2, `index.html:103-104`).
  - Today's mcp-surface line ("Probes an MCP server like a client would, validates its schemas, and
    fails CI when its tools change.", `index.html:117`) uses four terms a business reader can't
    follow, and the kind label first drafted for it added "npm package".
  - Today's PromptPal line ("Prompt templates in a small language with its own parser. Exports text
    or a ready-to-run Claude API call.", `index.html:132`) says how it is built, not what it is for.
    The project's own README opens with the plainer description.
  - Every new word restates a sourced line on `/mcp/`, the card or the project README. Nothing
    claims more.
- **Options.**
  - (a) Ship all four.
  - (b) Keep today's lines (`index.html:117`, `:132`) and chip (`:104`). The kind labels stay as #6
    sets them, because §8.8's fixed list allows no longer label.
  - (c) Ship the items Brett names.
- **Recommendation.** (a).
- **What changes.** (a) PR 6 as written. (b) and (c) change only those strings in PR 6.
- **Default.** (a), ship.
