# Research for the site redesign

This folder holds the evidence behind [`../site-redesign.md`](../site-redesign.md): who was studied,
how, what was found, and what was checked. None of it is published: `_config.yml` excludes `handoff/`
from the Jekyll build.

| File | What it is |
|---|---|
| [`appendix.md`](appendix.md) | One section per studied site (fold shots, scores, what works and what doesn't, measured numbers, time to key info), plus the hiring-side evidence |
| [`scores.csv`](scores.csv) | One row per site: group, URL, the 15 rubric scores, the mean, verification counts, a link to the record |
| [`verification-log.md`](verification-log.md) | Every claim the verifier corrected or removed, the blind re-score and the tie-break result |
| [`fact-sheet.md`](fact-sheet.md) | Phase 1: every fact about Brett and the projects, with file and line, and every disagreement between sources |
| [`baseline/`](baseline/) | The audit of Brett's current site: `audit.md`, `measure-<page>.json`, fold and full-page screenshots |
| [`sites/<slug>/`](sites/) | For each studied site: `desktop-fold.webp`, `mobile-fold.webp` and a trimmed `record.json` |

## Method

### Phases

All times are UTC. The work ran as a multi-agent workflow: separate agents gathered facts, found
candidates, captured and scored sites, verified, synthesised and wrote. No agent wrote about a site it
had not loaded in this session, and no agent verified a site it had captured.

| # | Phase | Agents | When | Output |
|---|---|---|---|---|
| 1 | Fact sheet: every role, date, tool, metric and project fact about Brett, with file and line, and every disagreement or contradicted claim | 1 | 2026-10-21, working files at 03:49; last revised 13:36 | `fact-sheet.md` |
| 2 | Baseline audit of Brett's site, live and local build, and the measurement script | 1 | 2026-10-21, 04:00–04:07; spot checks 13:35–16:25 | `baseline/`, `../tools/measure.mjs` |
| 3 | Discovery: four site pools (data; new-grad; software and design; interactive and cautionary) and one hiring-evidence pool | 5 | 2026-10-21, 03:47–04:05 and 13:35–13:44 | The pool tables below; the hiring sources in `appendix.md` |
| 4 | Capture and score, a few sites per agent | several | 2026-10-21, 13:35–13:44, 16:23–16:27 and 21:24–21:32 | `sites/`, and full captures in scratch |
| 5 | Verification and blind re-score | separate verifiers | 2026-10-21 16:25–16:30 and 21:33–21:37; 2026-10-22 07:48 | `verification-log.md` |
| 6 | Synthesis: patterns (3+ sites), ideas (1–2 sites), anti-patterns, time to key info, hiring findings | 1 | 2026-10-22, by 08:23 | Spec §3 |
| 7 | New-project exploration: candidates, data sources, licences, engine weights, a working prototype | 1 | 2026-10-21, loads 03:57–16:28; written up by 21:24 | Spec §14, `../border-waits-pipeline.md` |
| 8 | Writing the spec and the first-screen mock | 3 in parallel | 2026-10-22, from 16:25 | `../site-redesign.md`, `../mocks/` |
| 9 | Review panel: recruiter, senior data engineer, business manager, fact-checker, constraints checker | 5 | after writing | Fixes folded into the spec |

### Tools

- **Browser:** Playwright with its bundled Chromium (141.0.7390.37), headless, through the session's
  HTTPS proxy. Never `playwright install`. Desktop 1440×900 at DPR 1; mobile 390×844 at DPR 2 with
  `isMobile`.
- **Accessibility:** axe-core 4.10.2, a pinned `axe.min.js` from cdnjs injected with
  `page.addScriptTag` in a `bypassCSP` context. Manual checks (contrast samples, heading order, alt
  text, focus visibility, tap targets) where axe can't decide.
- **Performance:** CDP `encodedDataLength` for transfer sizes, the Performance API for JS weight and
  request counts, LCP and CLS observers. Third-party sites were measured unthrottled, one load each,
  so their numbers are indicative and compared only with each other. Brett's site was also measured
  under throttled mobile emulation (`baseline/audit.md`).
- **Scripts (scratch, not committed):** a page probe (`probe.mjs`: status, title, description, nav,
  visible text), the capture script (`capture.mjs`: four page loads per site plus `robots.txt`), an
  exporter (`export.mjs`: fold shots to WebP of about 80 KB or less, and a trimmed record), and two
  Python scripts that apply the verifier's verdicts and generate `scores.csv` and the per-site part of
  `appendix.md`.
- **Committed:** [`../tools/measure.mjs`](../tools/measure.mjs), the script later PRs re-run. It
  screenshots at 390, 768 and 1440, runs axe, checks a performance budget, and logs GA events with GA
  requests blocked.
- **WebSearch and WebFetch** were used only to find candidates and linking pages. Every claim about a
  site comes from a page loaded in Chromium.

### Interruptions, and how work resumed

The session stopped several times on usage rate limits. File modification times in the scratch folder
show five gaps longer than 30 minutes (measured): 2026-10-21 04:07–13:35, 13:45–16:21, 16:32–21:23,
21:45 to 2026-10-22 07:47, and 2026-10-22 08:32–16:25. The agents' own notes name rate limits for two
stops during the new-project evaluation and for verifier runs cut off around 16:29 and 21:35–21:37 on
2026-10-21; the cause of the other gaps is not recorded.

Every phase wrote its results to files as it went, so each restart picked up from disk:
- **Capture** continued with the sites not yet captured, in three batches (13:35–13:44, 16:23–16:27,
  21:24–21:32). Each site's capture time is in its `record.json` (`captured_utc`).
- **Verifiers** whose runs were cut off reused their own earlier verification captures (from 16:29,
  21:35 or 21:37) instead of reloading the site, plus a few single probes. Each case is noted in the
  verdict files, and `verification-log.md` gives the reload time per site.
- **Generated files** (`scores.csv` and the per-site appendix) were regenerated from the verified
  score files after the last verdicts, so they hold only claims that survived verification.
- The new-project evaluation lists sources it did not reach because of the stops (Ontario 511,
  Statistics Canada, Environment Canada, city open-data portals); nothing is claimed about them.

### Evidence rules

- **Citations.** Every statement about another site cites its slug and a file or field:
  `[eugeneyan:desktop-fold.webp]` or `[samwho:record.home.desktop.network_initial]`. Fold shots are
  committed in `sites/<slug>/`; other cited files (`tiles/…`, `inner-*.png`, `*-full.png`, `verify/…`)
  are scratch-only.
- **Measured or estimated.** Measured numbers say how they were measured; anything else is labelled
  estimated.
- **Memory check.** Before opening the capture of a seed site, the capture agent wrote what it
  expected from prior knowledge, then compared it with the capture (`memory_check` in each score
  file). Descriptions come from the capture, not from the expectation.
- **Independent verifier.** An agent that did not capture a site reloaded it and tried to disprove
  every claim (what works and what doesn't, pattern tags, time to key info, the memorable thing, the
  role quote, headline numbers with set tolerances). Result: 1,212 confirmed, 121 corrected, 1
  removed, 0 unverifiable.
- **Blind re-score.** 8 of 29 sites (25%, rounded up), drawn with a fixed seed (20261021): antfu,
  eugeneyan, henryheffernan, pahasara, rauno, richardshuai, shaundataanalytics, taniarascia. Of 120
  criterion pairs, none differed by 2 or more, so no tie-break was needed.
- **Patterns need 3 sites.** Anything seen on 1 or 2 sites is labelled an idea.
- **Politeness.** Public pages only, a handful of loads per site, `robots.txt` fetched and respected.
  Patterns were taken, never code, assets, copy or visual identity.

### A note on Google Analytics

One development run of `measure.mjs`, with `--allow-ga`, against the local build at 03:57 UTC on
2026-10-21 let gtag.js load from `http://127.0.0.1`. It probably sent **one `page_view` with a
`127.0.0.1` page location** to `G-Z3KW627PQQ`. Every later run blocked GA (requests to
googletagmanager.com, google-analytics.com and analytics.google.com are aborted by default).

## Candidate pool

79 entries from four discovery agents, 77 distinct sites (henryheffernan and mitchivin were found by
two agents). **29 were captured in full**, within the brief's 20–30. Status:
- **Captured:** in the full set; scored and verified; in `appendix.md`.
- **Kept, not captured:** live and relevant, but an alternate or outside the capture set.
- **Reserve:** live and verified, but outside a discovery agent's cap.
- **Dropped:** excluded, with the reason.

The hiring-side sources (18, plus 13 supplementary) are listed in
[`appendix.md#hiring-side-evidence`](appendix.md#hiring-side-evidence).

### D1 data, analytics and ML (`research/pool/d1-data.jsonl` in scratch)

| Slug | URL | Group | How found | Status | Why |
|---|---|---|---|---|---|
| deshraj-jogiya | <https://deshraj-jogiya.github.io/> | data | colorlib.com roundup of data portfolios (found by WebSearch) | **Captured** | Early-career DE; hero gives role, location, résumé and a live demo; four case studies |
| shaundataanalytics | <https://shaundataanalytics.github.io/> | data | colorlib.com roundup of data portfolios (found by WebSearch) | **Captured** | Airflow + BigQuery + dbt stack like Brett's; case-study pages; "Live Demo" buttons worth checking |
| pahasara | <https://www.pahasara.me/> | data | `emmabostian/developer-portfolios` README, searched for data titles | **Captured** | Undergraduate DE with a dated project index and a CV download |
| mujtabasaqib | <https://www.mujtabasaqib.com/> | data | `emmabostian/developer-portfolios` README, searched for data titles | **Captured** | Early-career DE with a dated timeline and chips; also a splash intro to weigh |
| wisl | <https://wisl.dev/> | data | `emmabostian/developer-portfolios` README, searched for data titles | **Captured** | Senior DE on GCP, Airflow and BigQuery; the minimal DE reference |
| mehdio | <https://www.mehdio.com/> | data | colorlib.com roundup of data portfolios (found by WebSearch) | **Captured** | DE and developer advocate; projects page and linked counters |
| ssp | <https://www.ssp.sh/> | data | colorlib.com roundup of data portfolios (found by WebSearch) | **Captured** | DE author with an interactive graph landing |
| kellyjadams | <https://www.kellyjadams.com/> | data | colorlib.com roundup of data portfolios (found by WebSearch) | Kept, not captured | D1 alternate: analytics engineer, but the hero promotes courses. Lower capture priority |
| yerradouani | <https://yerradouani.me/> | data | `emmabostian/developer-portfolios` README, searched for data titles | Kept, not captured | D1 alternate: WebGL landing that showed only an error screen in default headless Chromium |
| rmoff | <https://rmoff.net/> | data | Seed list (PROMPT §4.1) | Kept, not captured | D1 alternate: blog-first seed; the role is missing from the first screen |
| samueltyh | <https://samueltyh.github.io/> | data | colorlib.com roundup of data portfolios (found by WebSearch) | Dropped | Blog-first theme layout (HOME/CATEGORIES/TAGS/ARCHIVES) with no project or portfolio section. The role is stated only in the meta description. Little to learn beyond the other DE sites. |
| beingbrute | <https://beingbrute.github.io/> | data | colorlib.com roundup of data portfolios (found by WebSearch) | **Captured** | Early-career data analyst (Brett's current title); results-first case studies |
| lorenzomiguelbo | <https://lorenzomiguelbo.github.io/> | data | colorlib.com roundup of data portfolios (found by WebSearch) | Dropped | Lower priority: generic tagline and a services list where the projects should be. beingbrute covers the early-career analyst case far better. |
| eugeneyan | <https://eugeneyan.com/> | data | Seed list (PROMPT §4.1) | **Captured** | Seed; the strongest navigation reference among the writers |
| huyenchip | <https://huyenchip.com/> | data | Seed list (PROMPT §4.1) | Kept, not captured | D1 alternate: blog-first seed; useful only for prose voice |
| karpathy | <https://karpathy.ai/> | data | Seed list (PROMPT §4.1) | Kept, not captured | In D1's recommended ten, but not captured; the full set reached 29 without it. No reason was recorded |
| simonwillison | <https://simonwillison.net/> | data | Seed list (PROMPT §4.1) | Kept, not captured | D1 alternate: blog-first; a "Tools" nav item to live tools |
| sebastianraschka | <https://sebastianraschka.com/> | data | Seed list (PROMPT §4.1) | Dropped | Lower priority: a blog and book hub with the same pattern as eugeneyan/huyenchip. Nav has 9+ items plus 'More', so it adds little for a project portfolio. |
| vickiboykis | <https://vickiboykis.com/> | data | Seed list (PROMPT §4.1) | Dropped | The homepage is only a dated list of blog posts, with no role line and no project presentation. Nothing for a portfolio redesign beyond the minimal list. |
| lilianweng | <https://lilianweng.github.io/> | data | Seed list (PROMPT §4.1) | Dropped | A pure long-form research blog of learning notes, with no role statement, projects or résumé on the first screen. |
| jalammar | <https://jalammar.github.io/> | data | Seed list (PROMPT §4.1) | Dropped | The site is frozen. The top post says: 'I'm freezing this blog and starting to post on my Substack instead.' |
| juliasilge | <https://juliasilge.com/> | data | Seed list (PROMPT §4.1) | Dropped | The landing page is a one-tagline splash on a third-party theme ('Made with Hugo Apéro. Based on Blogophonic by Formspree.'). It shows no work, so there is little to study. |
| jvns | <https://jvns.ca/> | data | Seed list (PROMPT §4.1) | Dropped | Not a data/analytics role on the pages loaded. The homepage is a full post index. It fits the software/writing group better than D1. |
| emily | <https://emily.rbind.io/> | data | WebSearch for the owner's site | Dropped | Lower priority. The owner is a data scientist, not DE/AE, although the about page mentions data-engineering work. The homepage is posts-first with a cookie-consent banner at the top of the first screen. |
| hamel | <https://hamel.dev/> | data | colorlib.com roundup of data portfolios (found by WebSearch) | Dropped | Lower priority: a blog-first ML site whose top bar is a course promotion ('Enroll by Oct 24 25% off'). Same pattern as the seeds. |
| tylerjrichards | <https://www.tylerjrichards.com/> | data | colorlib.com roundup of data portfolios (found by WebSearch) | Dropped | Lower priority: a plain link list that ends with an employer marketing block ('Visit Snowflake.com'). Use karpathy instead for the minimal one-page reference. |

### D2 new-grad and intern (`research/pool/d2-newgrad.jsonl` in scratch)

| Slug | URL | Group | How found | Status | Why |
|---|---|---|---|---|---|
| richardshuai | <https://www.richardshuai.app/> | newgrad | Waterloo CS webring, then a keyword scan of member homepages | **Captured** | Same 2027 new-grad cohort; data-infra internships; states the job ask |
| kushalgoel | <https://www.kushalgoel.com/> | newgrad | Waterloo CS webring, then a keyword scan of member homepages | **Captured** | Data-pipeline internship (BigQuery, NiFi); quantified bullets; résumé PDF |
| florea | <https://www.florea.ca/> | newgrad | Waterloo SE webring, then a keyword scan of member homepages | **Captured** | Intern-to-full-time conversion on a plain page; tests content over craft |
| shao | <https://shao.zip/> | newgrad | Waterloo CS webring, then a keyword scan of member homepages | **Captured** | Very short new-grad page; case-study inner pages |
| damian | <https://damian.bio/> | newgrad | Waterloo SE webring, then a keyword scan of member homepages | **Captured** | One-screen, résumé-first page built as a student; landed a full-time role |
| darshshah | <https://darshshah.ca/> | newgrad | Waterloo SE webring, then a keyword scan of member homepages | **Captured** | The only peer whose stack chips match Brett's (Airflow, dbt) |
| evanzhang | <https://evanzhang.ca/> | newgrad | Waterloo SE webring, then a keyword scan of member homepages | Kept, not captured | D2 alternate: says they are not looking for roles; little data work |
| henryheffernan | <https://henryheffernan.com/> | newgrad | HN Show HN search (item 31313187) | Captured (same site as the D4 entry) | Duplicate of the D4 entry: one capture, scored in the cautionary group |
| vivek9patel | <https://vivek9patel.github.io/> | newgrad | HN Show HN search (item 27087516) | Kept, not captured | D2 alternate: an OS-simulation portfolio (cautionary for a 6-second scan) |
| zxh | <https://portfolio.zxh.me/> | newgrad | HN Show HN search (item 27084995) | Kept, not captured | D2 candidate, not recommended: ML researcher; "Click to enter" lock screen |
| soumyajit | <https://soumyajit.vercel.app/> | newgrad | GitHub repo search, most-starred "portfolio" repos | Kept, not captured | D2 weak candidate: a widely copied template |
| stevengong | <https://stevengong.co/> | newgrad | Waterloo SE webring, then a keyword scan of member homepages | Kept, not captured | D2 weak candidate: notes-first, not a project portfolio |
| matthew-li | <https://www.matthew-li.dev/> | newgrad | Waterloo CS webring, then a keyword scan of member homepages | Dropped | 'Worked' evidence failed adversarial check: the press links on the site credit another student. Could be a collaborator, but unverified. |
| freemanjiang | <https://freemanjiang.com/> | newgrad | Waterloo CS webring, then a keyword scan of member homepages | Dropped | Homepage is a short link list (4 projects, no experience or resume); the widely-shared evidence is for one project, not the portfolio. Low value as a full capture. |
| mitchivin | <https://mitchivin.com/> | newgrad | HN Show HN search (item 45154609) | Dropped | Owner is a freelance designer, not a new grad or intern. Better suited to the interactive/cautionary pool. |
| guycombinator | <https://www.guycombinator.net/> | newgrad | HN Show HN search (item 41133736) | Dropped | No evidence the owner is a student, intern or new grad. |
| arielroffe | <https://arielroffe.quest/> | newgrad | HN Show HN search (item 30656961) | Dropped | Page is a Phaser game with no readable role text in the DOM; level could not be confirmed. |
| aryaman | <https://www.aryaman.dev/> | newgrad | Waterloo CS webring, then a keyword scan of member homepages | Dropped | Site is a '[wip]' stub with two sentences and links; nothing to capture. |
| richardhua | <https://richardhua.dev/> | newgrad | Waterloo CS webring, then a keyword scan of member homepages | Dropped | No 'worked' evidence: still job-seeking, no landed role or sharing signal found. |
| kuber | <https://kuber.studio/> | newgrad | HN Show HN search (item 47205127) | Dropped | Level (student/intern/new grad) not confirmed from loaded text; terminal UI hides content behind typed commands. Not pursued further. |
| portfolio-zxh-io | <https://portfolio.zxh.io/> | newgrad | HN Show HN search (item 27084995) | Dropped | Dead/blocked: proxy CONNECT 502 for portfolio.zxh.io and zxh.io (2026-10-21T03:51Z). Replaced by the live portfolio.zxh.me entry (slug zxh). |

### D3 software and design (`research/pool/d3-software.jsonl` in scratch)

| Slug | URL | Group | How found | Status | Why |
|---|---|---|---|---|---|
| brittanychiang | <https://brittanychiang.com/> | software-design | Seed list (PROMPT §4.1) | **Captured** | Design-forward; co-ops in the same timeline as senior roles |
| antfu | <https://antfu.me/> | software-design | Seed list (PROMPT §4.1) | **Captured** | Minimal; a long project list scaled with categories |
| emilkowal | <https://emilkowal.ski/> | software-design | Seed list (PROMPT §4.1) | **Captured** | Design-forward; a homepage of lists, demos on project pages |
| rauno | <https://rauno.me/> | software-design | Seed list (PROMPT §4.1) | **Captured** | Design-forward craft reference |
| paco | <https://paco.me/> | software-design | Seed list (PROMPT §4.1) | Kept, not captured | In D3's pool of 12; not captured (overlaps emilkowal in pattern). No other reason recorded |
| jakub | <https://jakub.kr/> | software-design | brianlovin.com/sites, a curated list of sites | Kept, not captured | In D3's pool of 12; not captured. No reason recorded |
| brianlovin | <https://brianlovin.com/> | software-design | A linking page on guochen.substack.com (found by WebSearch) | Kept, not captured | In D3's pool of 12; not captured (used as a linking page). No other reason recorded |
| shud | <https://shud.in/> | software-design | brianlovin.com/sites, a curated list of sites | **Captured** | Minimal; a résumé-like bio and first-person project lines |
| mitchellh | <https://mitchellh.com/> | software-design | Seed list (PROMPT §4.1) | Kept, not captured | In D3's pool of 12; not captured (its inner page held three links). No other reason recorded |
| danluu | <https://danluu.com/> | software-design | Seed list (PROMPT §4.1) | **Captured** | Seed; the minimal counterexample |
| taniarascia | <https://www.taniarascia.com/> | software-design | Seed list (PROMPT §4.1) | **Captured** | Content-heavy; a dated timeline under the name |
| delba | <https://delba.dev/> | software-design | Seed list (PROMPT §4.1) | Kept, not captured | In D3's pool of 12; not captured (borderline group fit: DX and education). No other reason recorded |
| leerob | <https://leerob.com/> | software-design | Seed list (PROMPT §4.1) | Reserve | Outside the 12 cap: homepage is writing-first with no project section. The one transferable idea is the short/long bio switch. |
| joshwcomeau | <https://www.joshwcomeau.com/> | software-design | Seed list (PROMPT §4.1) | Reserve | Outside the 12 cap: blog/course-first with no project section; interactive-article patterns fit the interactive group (D4) better. The current role is "solo indie hacker" (about page). |
| cassidoo | <https://cassidoo.co/> | software-design | Seed list (PROMPT §4.1) | Reserve | Outside the 12 cap: blog-first with no project presentation; current role is developer-advocacy leadership. |
| brandur | <https://brandur.org/> | software-design | Seed list (PROMPT §4.1) | Reserve | Outside the 12 cap: writing-first, no project section. The content-type taxonomy is the one transferable idea. |
| alexwidua | <https://alexwidua.com/> | software-design | A linking page on guochen.substack.com (found by WebSearch) | Dropped | Site reduced to name, title and email; no work shown. Owner is a designer, not an engineer. |
| joseph | <https://joseph.cv/> | software-design | brianlovin.com/sites, a curated list of sites | Dropped | Owner is an interaction designer / design lead, not a software or design engineer. Strong case-study grid; could serve another group as a case-study reference. |
| mikes | <https://www.mikes.cv/> | software-design | brianlovin.com/sites, a curated list of sites | Dropped | Product designer (every listed role is Product Designer / Lead Product Designer), not an engineer. |
| pedro | <https://ped.ro/> | software-design | brianlovin.com/sites, a curated list of sites | Dropped | Current role is content/brand/community at Raycast (background in UI development); no project presentation. |
| coryetzkorn | <https://www.coryetzkorn.com/> | software-design | WebSearch for design-engineer portfolios | Dropped | Not a project-presentation example as loaded: homepage is a writing list with no work section. |
| paulstamatiou | <https://paulstamatiou.com/> | software-design | HN "Ask HN: Share your personal site" thread | Dropped | Head of Design (a designer who codes), and the homepage is blog / gear / photos-first with no project section. |

### D4 interactive and cautionary (`research/pool/d4-interactive.jsonl` in scratch)

| Slug | URL | Group | How found | Status | Why |
|---|---|---|---|---|---|
| redblobgames | <https://www.redblobgames.com/> | interactive | Seed list (PROMPT §4.1) | **Captured** | Explain-then-interact pages; a static homepage catalogue |
| madebyevan | <https://madebyevan.com/> | interactive | WebSearch to locate and confirm a known site | **Captured** | Static project list with labelled demo links; demo pages state requirements |
| robinlinacre | <https://www.robinlinacre.com/> | interactive | WebSearch on interactive record linkage, then a listing that links the site | **Captured** | Data engineer running their own library in the browser through DuckDB-WASM |
| ciechanow | <https://ciechanow.ski/> | interactive | Seed list (PROMPT §4.1) | **Captured** | Inline instructions per widget; also the demo-on-homepage counterexample |
| samwho | <https://samwho.dev/> | interactive | Seed list (PROMPT §4.1) | **Captured** | D4 alternate, captured: essay demos on their own pages; a tiny homepage |
| wattenberger | <https://wattenberger.com/> | interactive | Seed list (PROMPT §4.1) | Kept, not captured | D4 alternate: video-preview cards |
| maggieappleton | <https://maggieappleton.com/> | interactive | Seed list (PROMPT §4.1) | Kept, not captured | D4 alternate: writing-first, weak fit for live demos |
| bruno-simon | <https://bruno-simon.com/> | cautionary | Seed list (PROMPT §4.1) | **Captured** | Seed, cautionary: the 3D landing Brett named |
| henryheffernan | <https://henryheffernan.com/> | cautionary | HN Show HN search (item 31313187) | **Captured** | Cautionary 3D portfolio that the owner built for a job search |
| mitchivin | <https://mitchivin.com/> | cautionary | HN Show HN search (item 45154609) | Dropped | Role not relevant: the owner's HN post describes freelance visual design, built 'without coding experience' using AI tools. henryheffernan already covers the OS-simulation cautionary case for an engineer. |

## Rubric

Every site, and Brett's current site, was scored 1–5 on each criterion, with at least one citation
per score. Half points were not allowed. Fit was judged against Brett's goals: a CS student
graduating 2027, a Data Analyst Intern, wanting a full-time data engineering, analytics or software
engineering role.

| # | Criterion (`scores.csv` column) | 5 | 3 | 1 |
|---|---|---|---|---|
| 1 | First-screen clarity (`first_screen_clarity`): in 5 seconds, who, what, level, what they want | Name, role or level and what they do on both first screens in plain words, plus a clear next step | Name and a vague role; level or intent missing; or only clear on desktop | Can't tell who or what (splash, art piece, empty, slogan only) |
| 2 | Proof of skill (`proof_of_skill`) | Shipped work, demos or code reachable from the first screen or in ≤ 1 scroll, verifiable | Real work exists but takes 2–4 scrolls or clicks, or is described more than shown | Claims only |
| 3 | Project presentation (`project_presentation`) | Each project has a visual or artefact, a one-line what and why, tech facts, direct links | A list of titles with descriptions; links inconsistent | No projects, or a wall of logos or thumbnails |
| 4 | Experience presentation (`experience_presentation`) | Roles with employer, dates and outcomes, scannable in seconds (or a clear résumé one tap away and a summary) | Roles without dates or impact, or only in an easy-to-find PDF | No experience information and no résumé |
| 5 | Visual craft (`visual_craft`) | Deliberate type scale, rhythm and colour; looks like nobody else's | Clean but generic, or nice but inconsistent | Cluttered, broken, default |
| 6 | Interaction and motion (`interaction_motion`) | Motion explains or gives feedback, never blocks content, honours reduced motion | Little motion, or decorative but harmless (a static site that never gets in the way scores 3–4) | Splash intros, scroll-jacking, motion that hides or delays content |
| 7 | Navigation and IA (`navigation_ia`) | Small obvious nav, plain labels, every key destination ≤ 1 click, works on mobile | Workable but vague labels or buried items | Hidden or confusing |
| 8 | Mobile quality (`mobile_quality`) | Designed for mobile, targets ≥ 24 px, no overflow, key info early | Responsive but cramped; small targets or a long scroll | Broken, overflowing or desktop-only |
| 9 | Performance (`performance`) | JS < 100 KB, total < 1 MB, LCP < 1.5 s, CLS < 0.05 (4: JS < 300 KB, LCP < 2.5 s, CLS < 0.1) | JS < 800 KB or LCP < 4 s (2: JS < 2 MB) | Heavier, or a long blank or blocking load |
| 10 | Accessibility (`accessibility`) | 0 serious or critical axe violations, visible focus, skip link or trivial nav, alt text (4: ≤ 1 serious, focus visible) | 2–3 serious, or focus not visible (2: 4+ serious or any critical) | Largely unusable by keyboard or screen reader |
| 11 | Copy (`copy`) | Concrete and specific, sounds like a person; numbers and nouns | Fine but generic in places | Buzzword walls, inflated claims, AI-sounding |
| 12 | Persona fit, three scores (`persona_recruiter`, `persona_engineer`, `persona_business`) | Recruiter: current role, graduation and availability, résumé and contact in a 6-second phone scan and a 30-second skim. Engineer: depth, code, decisions, and a well-built site. Business: problem, outcome, plain language, credibility | | |
| 13 | Memorability (`memorability`): the one thing you'd remember tomorrow | Memorable and it serves the content | Forgettable but fine, or memorable but neutral | Memorable for something that works against the content, or nothing at all |

**Time to key info.** For 390×844 and 1440×900: scrolls (viewport heights) and clicks from landing to
(a) current role, (b) résumé, (c) contact, (d) best project and (e) evidence of shipped work. Record
positions were used as hints and confirmed on the screenshots; an item that doesn't exist is `null`
("not on site").

**Pattern tags.** Each score file tags patterns from a shared vocabulary (for example
`resume-in-hero`, `role-and-level-in-hero`, `demo-on-own-page`, `experience-timeline`,
`contact-email-visible`, `dark-only`, `monospace-accents`) and anti-pattern candidates (for example
`splash-intro`, `scroll-jacking`, `webgl-landing`, `autoplay-media`, `no-resume`, `vague-tagline`,
`ai-sounding-copy`, `unlabeled-icons`, `low-contrast-text`, `no-dates-on-roles`, `stale-content`,
`heavy-js`). Every tag carries a citation. The pattern counts in spec §3 are counted from these tags
and from record fields.

## File layout

```
handoff/research/
├── README.md               this file
├── appendix.md             per-site sections and hiring-side evidence
├── scores.csv              one row per site, plus Brett's current site
├── verification-log.md     verifier outcomes, blind re-score, tie-breaks
├── fact-sheet.md           facts about Brett, with sources
├── baseline/
│   ├── audit.md            audit of the current site
│   ├── measure-<page>.json trimmed measure.mjs reports: home, mcp, neuronet-arena, mcp-surface, 404
│   └── <page>-<1440|390>-<fold|full>.webp
└── sites/<slug>/           29 folders
    ├── desktop-fold.webp   1440×900 first screen, WebP, about 80 KB or less
    ├── mobile-fold.webp    390×844 first screen
    └── record.json         trimmed capture record (hero text ≤ 25 words)
```

## What is not committed

The repo is public and binaries stay in its history, so **full captures were kept out of git** in the
session scratchpad: full-page screenshots, inner-page screenshots, screenshot tiles, the verifiers'
own captures, the full `record.json` files, the score and verdict files, discovery notes and raw probe
output, and the new-project samples, engine measurements and prototype. Only the fold shots and
trimmed records under `sites/`, and Brett's own baseline captures, are committed.

Size of `handoff/research/` when this file was written: **4.5 MB** (measured with `du -sh` on 2026-10-22, after `appendix.md` was added), against
a budget of about 8 MB. Whether to keep even the third-party fold shots in a public repo is a
Decision for Brett (spec §18); the default keeps fold shots only, as committed.
