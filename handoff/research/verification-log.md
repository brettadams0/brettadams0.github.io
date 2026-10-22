# Verification log

Independent verification of every claim the capture agents made about another site (PROMPT.md §4.4).

- **Verifiers** were agents that did not capture the sites they checked. Each reloaded every site
  with its own fresh capture (desktop 1440×900 and mobile 390×844, homepage and inner page) and
  tried to disprove each claim: what works / what doesn't bullets, pattern tags, time to key info,
  the memorable thing, the role quote and the headline measured numbers (with tolerances:
  bytes and requests ±30%, timings ±50% or ±500 ms, axe counts ±1 per impact).
- **Outcomes:** `confirmed` claims stay; `corrected` claims are replaced by the verifier's text;
  `removed` and `unverifiable` claims are dropped. The appendix and the spec use only the result.
- **Blind re-score:** 8 of 29 sites (25%, rounded up), drawn with `random.Random(seed).sample(sorted(all 29 slugs), ceil(29*0.25)=8), Python 3`, seed 20261021: antfu, eugeneyan, henryheffernan, pahasara, rauno, richardshuai, shaundataanalytics, taniarascia. Re-scorers never saw the original scores.
- **Tie-breaks:** where any criterion differed by 2 or more points, a third agent that had scored
  neither version looked at both sets of evidence and the screenshots, and set the final score.

**Totals:** 802 confirmed, 74 corrected, 1 removed, 0 unverifiable, across 19 sites.

**Not yet verified:** beingbrute, damian, darshshah, florea, henryheffernan, kushalgoel, mujtabasaqib, richardshuai, shao, ssp

## Per site

| Site | Verifier | Reloaded (UTC) | Confirmed | Corrected | Removed | Unverifiable |
|---|---|---|---|---|---|---|
| antfu | V3.3 | 2026-10-21T16:29:49.154Z | 49 | 2 | 0 | 0 |
| brittanychiang | V3.1 | 2026-10-21T16:25:47.965Z | 47 | 4 | 0 | 0 |
| bruno-simon | V3.3 | 2026-10-21T21:33:55.978Z | 34 | 5 | 0 | 0 |
| ciechanow | V3.2 | 2026-10-21T16:25:57.760Z | 43 | 3 | 0 | 0 |
| danluu | V3.1 | 2026-10-21T16:26:56.289Z | 39 | 3 | 0 | 0 |
| deshraj-jogiya | V3.4 | 2026-10-21T21:34:50.556Z | 43 | 5 | 0 | 0 |
| emilkowal | V3.1 | 2026-10-21T16:26:54.158Z | 43 | 4 | 0 | 0 |
| eugeneyan | V3.4 | 2026-10-21T21:37:03.576Z | 40 | 6 | 0 | 0 |
| madebyevan | V3.2 | 2026-10-21T16:25:57.899Z | 44 | 1 | 0 | 0 |
| mehdio | V3.3 | 2026-10-21T16:29:38.486Z | 31 | 10 | 0 | 0 |
| pahasara | V3.4 | 2026-10-21T16:29:38.559Z | 41 | 4 | 0 | 0 |
| rauno | V3.3 | 2026-10-21T16:29:48.411Z | 40 | 7 | 0 | 0 |
| redblobgames | V3.2 | 2026-10-21T16:25:58.024Z | 47 | 0 | 0 | 0 |
| robinlinacre | V3.2 | 2026-10-21T16:25:57.907Z | 41 | 3 | 0 | 0 |
| samwho | V3.2 | 2026-10-21T16:29:49.110Z | 46 | 4 | 0 | 0 |
| shaundataanalytics | V3.4 | 2026-10-21T21:36:13.279Z | 45 | 3 | 0 | 0 |
| shud | V3.1 | 2026-10-21T16:26:56.573Z | 40 | 3 | 1 | 0 |
| taniarascia | V3.1 | 2026-10-21T16:26:55.409Z | 44 | 4 | 0 | 0 |
| wisl | V3.3 | 2026-10-21T16:29:49.265Z | 45 | 3 | 0 | 0 |

## Corrections and removals

### antfu

- **P1** (pattern) corrected: “role-and-level-in-hero” → “role-in-hero: 'design engineer' is stated; level is not stated in words, only implied by creator and core-team chips.” — Hero: 'a fanatical open sourceror and design engineer. Working at Vercel / Nuxt'. No seniority word. [antfu:verify/desktop-fold.png]
- **N1** (measured_number) corrected: “js_bytes_desktop = 277512” → “js_bytes_desktop varies between loads: 131,667 B (9 scripts) in the verifier reload vs 277,512 B (18 scripts) originally; report as about 130–280 KB.” — Reload 131,667 is -53%, outside ±30%. Script count halved, so lazily loaded chunks differ between loads. [antfu:verify.record.summary.js_bytes_desktop]

### brittanychiang

- **W2** (what_works) corrected: “Each job is one compact block: date range, 'Role · Company' link, two-line summary, tech chips; co-op internships share the same timeline as senior roles.” → “Each job is one compact block: date range, 'Role · Company' link, a short 2–7 line summary, tech chips; co-op internships share the same timeline as senior roles.” — Summaries are not two lines: Klaviyo 5 lines, Upstatement 7 lines on desktop. Apple and Starry co-op rows sit in the same dated list with chips. [brittanychiang:verify/tiles/d-00.png]
- **D3** (what_doesnt) corrected: “No email or contact form; contact means guessing between unlabeled social icons.” → “No email or contact form; contact is a row of five icon-only social links (GitHub, LinkedIn, CodePen, Instagram, Goodreads) with no visible text labels.” — No mailto links in probe; contact_forms 0. Icons carry aria-labels and title tooltips, so they are visually unlabeled but not unnamed. [brittanychiang:verify/v31-tools/sticky.out.json]
- **P18** (pattern) corrected: “unlabeled-icons” → “icon-only-links” — Social icons show no visible text, but each has an aria-label and title tooltip (probe), so they are not unlabeled for screen readers. [brittanychiang:verify/v31-tools/sticky.out.json]
- **K5** (time_to_key_info) corrected: “mobile shipped_work: {"scrolls": 3, "clicks": 0, "note": "Project rows with star counts and install count; job rows (from y\u2248850) also link to shipped products"}” → “mobile shipped_work: {"scrolls": 3, "clicks": 0, "note": "Project rows with star and install counts from y≈3086; job rows start y≈1218 and some link to shipped work"}” — My reload puts the first job row at y=1218 on mobile, not y≈850; project section at 3086 (3 scrolls) holds. [brittanychiang:verify.record.home.mobile.dom.role_candidates]

### bruno-simon

- **W2** (what_works) corrected: “A behind-the-scenes panel credits each library, the music licence, and links the full source under MIT, which gives engineers something to verify.” → “A hidden behind-the-scenes panel credits Three.js and its authors and the composer (Kounine), links the music files, and links the full source on GitHub under MIT.” — DOM: 'Behind the scene', 'The code is available on GitHub under MIT license', github.com/brunosimon/folio-2025; music text credits Kounine. The music licence wording itself was not read in the reload. [bruno-simon:verify.v33work/bruno-check.json.dom.mit_ctx]
- **W4** (what_works) corrected: “Unmistakable identity: nobody would confuse it with a template.” → “Distinctive even while loading (glowing ring on a perspective grid), but the world itself never rendered in two reloads, so only the loader's identity was seen.” — 0 visible characters and only the ring at 39 s; no scene, car or landmarks rendered to judge the full identity from. [bruno-simon:verify.v33work/bruno-35s.png]
- **D2** (what_doesnt) corrected: “Heavy: 1.23 MB JS, 3.8 MB desktop and 7.6 MB mobile transfer, 97 to 147 requests (measured).” → “Heavy: 1.23 MB JS; 5.5 MB desktop and 7.6 MB mobile transfer on load, 97 to 146 requests (verifier reload).” — Reload: js 1,230,674 B; total 5,503,256 B desktop (+45% vs 3.8 MB), 7,644,412 B mobile; 97 and 146 requests. [bruno-simon:verify.record.summary.total_bytes_desktop]
- **D3** (what_doesnt) corrected: “Name, role, projects and contact are only in hidden panels or inside the 3D scene; no résumé at all.” → “Name, role and contact sit only in hidden panels (Welcome; Discord / Private messages); no projects heading or list in the page text; no résumé.” — hero_text empty; 'My name is Bruno Simon...' and 'Private messages Contact me directly' only in hidden DOM; headings are behind-the-scenes ones; no résumé/CV match. Scene contents not seen. [bruno-simon:verify.v33work/bruno-check.json.dom]
- **N2** (measured_number) corrected: “total_bytes_desktop = 3804549” → “total_bytes_desktop = 5503256 in the verifier reload (3804549 originally); report as about 3.8–5.5 MB.” — +45%, outside ±30%; fetch requests carried 4.1 MB of world assets this time. [bruno-simon:verify.record.summary.total_bytes_desktop]

### ciechanow

- **D2** (what_doesnt) corrected: “The page never says who the author is or what they do; that line lives only in the meta description.” → “The page shows only the author's name; it never says what they do. 'Interactive articles about science and engineering' exists only in the meta description.” — Name is in the header on both folds, so 'never says who' is wrong; the role phrase appears only in meta_description fields of the record. [ciechanow:verify.record.summary.meta_description]
- **P2** (pattern) corrected: “demo-autostarts” → “Widgets initialise and render WebGL on page load (Moon visible at the fold), but animated simulations wait for a play button.” — canvas_contexts experimental-webgl at load and the Moon is drawn without input, yet d-03 shows a ▶ button gating the simulation, so 'autostarts' is only half true. [ciechanow:verify.record.home.desktop.perf_api_initial]
- **R1** (role_confirmed) corrected: “Interactive articles about science and engineering.” → “'Interactive articles about science and engineering.' is the meta description only; no visible text on the page states a role.” — Phrase found only in meta_description fields; hero_text and fold shots show name, nav and article. [ciechanow:verify.record.summary.meta_description]

### danluu

- **W1** (what_works) corrected: “Weight floor for comparison: about 20 KB and 5 requests per page, LCP under 0.8 s, zero layout shift (measured).” → “Weight floor for comparison: about 20 KB and 5 requests per page, LCP about 0.9 s on the homepage (1.4 s on an essay), zero layout shift (measured).” — Bytes and requests hold (19,697 B, 5 req; essay 19,602 B). LCP not under 0.8 s in my reload: 861 ms desktop, 963 ms mobile, 1,363 ms essay. [danluu:verify.record.summary.lcp_ms_desktop]
- **W3** (what_works) corrected: “Essays open with concrete facts (company size, team size, exact stack) before any argument.” → “The captured essay opens with concrete facts ($1.7B company, 70 engineers, a Python monolith on Postgres) before its argument; only one essay was checked.” — Holds for 'In defense of simple architectures', but the claim generalises to all essays from one page. [danluu:verify/inner-desktop-fold.png]
- **W4** (what_works) corrected: “Titles are plain and specific, so the list works as its own table of contents.” → “Most titles are plain and specific, so the list largely works as its own table of contents; a few are coined or vague ('The benchmarkpocalypse', 'Attrition').” — Many titles are specific, but some are not ('Bug blindness', 'Cocktail party ideas', 'Attrition', 'Relative vs. absolute'). [danluu:verify/tiles/d-03.png]

### deshraj-jogiya

- **W3** (what_works) corrected: “Case-study pages share one outline: how it works, data, problems and decisions, testing, results, limits and next steps, stack.” → “The Lakehouse case study follows how it works, data, problems and decisions, testing, results, limits and next steps, stack; the homepage promises this outline for all four.” — Inner h2s: How it works, Data, Problems found and decisions made, Testing and reliability, Results, Limits and next steps, Stack. Only one case study loaded; homepage intro lists the same parts for 'Four projects in depth'. [deshraj-jogiya:verify.record.inner.desktop.dom.headings]
- **W4** (what_works) corrected: “Each case study opens with a dated 'For:' line naming the user and the question the pipeline answers, and says the data is synthetic.” → “The Lakehouse case study opens with a dated 'For:' line naming the user and their question, and its summary says the 60,000 postings are synthetic.” — Fold: 'Sep 2026 · For: Analysts asking how salaries vary by industry and seniority...' and '60,000 synthetic job postings'. Only one case study loaded, so 'each' is not shown; Career Pilot runs on real data. [deshraj-jogiya:verify/inner-desktop-fold.png]
- **D4** (what_doesnt) corrected: “Over 30 project cards on one page (19.8k px tall on desktop) bury the four strong case studies.” → “30 project cards on one desktop page (4 featured, 21 more, 5 team; 19.8k px tall) bury the four strong case studies.” — h3 count: 4 featured case studies, 21 under 'More projects', 5 under 'Team projects' = 30, not over 30. scroll_height 19,762 desktop. Mobile shows only 6 more-project cards. [deshraj-jogiya:verify.record.home.desktop.dom.headings]
- **P7** (pattern) corrected: “project-cards-with-visuals” → “project-cards-with-visuals applies only partly: 2 of 4 featured case-study cards carry a visual (pipeline diagram, feature-importance chart); the other cards are text and chips.” — Career Pilot card has a flow diagram and FinRisk a bar chart; Lakehouse, dbt Warehouse and all 'More projects' cards have no image. [deshraj-jogiya:verify/tiles/d-01.png]
- **N7** (measured_number) corrected: “has_resume_link = false” → “has_resume_link = false is a detector miss: 'Resume' and 'View Resume' are buttons opening an HTML résumé modal with Print / Save PDF, so no href exists.” — Field reproduces as false, but resume_links is empty only because both résumé controls are buttons; modal text 'Professional Resume Print / Save PDF' is in the DOM. Do not read as 'no résumé'. [deshraj-jogiya:verify.record.summary.has_resume_link]

### emilkowal

- **W5** (what_works) corrected: “Mobile is the same column scaled up, with no small targets and no overflow.” → “Mobile is the same single column at the same type sizes, with no small targets and no overflow.” — Not 'scaled up': p is 14px and body 16px at both widths. small_targets_mobile 0, overflow false hold. [emilkowal:verify.record.summary.small_targets_mobile]
- **P1** (pattern) corrected: “role-and-level-in-hero” → “role-in-hero” — Hero says 'Design Engineer' only; no level (senior, staff, etc.) anywhere on the page. [emilkowal:verify/desktop-fold.png]
- **P6** (pattern) corrected: “interactive-explainers” → “interactive-docs” — Sonner page is library docs with live option buttons (probe: toasts render, code updates), not an explanatory essay with interactive figures. [emilkowal:verify/v31-tools/toast.out.json]
- **N4** (measured_number) corrected: “lcp_ms_desktop = 1544” → “lcp_ms_desktop = 2492 (verifier reload; mobile 2700)” — Reload LCP 2,492 ms vs claim 1,544: +948 ms, outside both ±50% (772 ms) and ±500 ms. [emilkowal:verify.record.summary.lcp_ms_desktop]

### eugeneyan

- **W1** (what_works) corrected: “Hero summarises roles as short verb bullets where each verb links to the matching evidence page, so the intro doubles as navigation.” → “Hero lists roles as four verb bullets; Write, speak, Prototype and Send link to the matching pages ('Teach' links its two sites instead), so the intro doubles as navigation.” — Fold bullets: 'Write and speak about...', 'Prototype apps like...', 'Teach how to apply ML/LLMs effectively at ApplyingML.com & Applied-LLMs.org', 'Send a newsletter...'. 'Teach' itself is plain text. [eugeneyan:verify/desktop-fold.png]
- **W4** (what_works) corrected: “One-line output tally (posts, talks, prototypes, words) with each count linking to its index: a compact facts-as-numbers row.” → “One-line output tally ('212 posts, 31 talks, 19 prototypes, 426,673 words') where the posts, talks and prototypes counts link to their indexes: a compact facts-as-numbers row.” — Tally line above Selected Prototypes: 'posts', 'talks', 'prototypes' are blue links; '426,673 words, and countless hours' is plain text, so not every count links. [eugeneyan:verify/tiles/d-01.png]
- **D1** (what_doesnt) corrected: “Headline descriptor changes on each load and is non-informative; the real role is pushed into the paragraph below.” → “Headline descriptor rotates within and between loads ('an ML engineer', 'an applied scientist', 'an operator-angel'); some values are vague, and the current role is only in the paragraph below.” — Fold shots read 'an ML engineer.', the later full-page shot of the same load 'an operator-angel.', a probe 'an applied scientist.'. Not all values are non-informative; none names the Anthropic role. [eugeneyan:verify/tiles/d-00.png]
- **K5** (time_to_key_info) corrected: “mobile shipped_work: {"scrolls": 0, "clicks": 1, "note": "Prototyping page: GitHub and recorded-demo links per entry"}” → “mobile shipped_work: {scrolls 0, clicks 1, note: 'Prototyping nav: entries carry Github, Demo or Website links; whether Demo links are recordings was not checked.'}” — Prototyping fold shows Github, Demo (play icon) and Website links; I did not open a Demo link, so 'recorded-demo' is not verified. One entry (AI Coach) has only a phone number. [eugeneyan:verify/inner-desktop-fold.png]
- **N5** (measured_number) corrected: “cls_desktop = 0.0275” → “cls_desktop about 0.01-0.03 (0.0119 on reload, 0.0275 at capture); both well under 0.05.” — Reload CLS 0.0119 is 57% below the claimed 0.0275, outside a 50% band; the rubric bucket (<0.05) is unchanged. [eugeneyan:verify.record.summary.cls_desktop]
- **N8** (measured_number) corrected: “horizontal_overflow_mobile = false” → “horizontal_overflow_mobile = true under the current rule: no scrollbar overflow, but the mobile layout viewport is 453 px, wider than the 390 px screen (see D2).” — Reload: summary.horizontal_overflow_mobile true, mobile_layout_width 453, dom.horizontal_overflow false. The original record used an older rule that ignored layout width; booleans must match exactly. [eugeneyan:verify.record.summary.horizontal_overflow_mobile]

### madebyevan

- **P3** (pattern) corrected: “project-one-liners” → “project-short-descriptions: each entry has a 1–3 sentence description under its title and year, not a single line.” — Figma entry is two sentences; WebGL Water three; Skew two. Short, but not one-liners. [madebyevan:verify/tiles/d-00.png]

### mehdio

- **W1** (what_works) corrected: “Project cards pair a real screenshot with a what-it-does paragraph, tech chips, a live 'Visit project' button and a GitHub link showing stars.” → “Project cards pair a real screenshot with a what-it-does paragraph, tech chips and a 'Visit project' button; open-source ones add a GitHub link, some with a star count.” — /projects: Try MotherDuck and Duckify have no GitHub link; DuckDB & MotherDuck for Obsidian shows 'GitHub ★97'; Mehdio Website 'GitHub' without stars. [mehdio:verify/tiles/id-00.png]
- **W3** (what_works) corrected: “Work history on a separate About page, with employer, title, years, location and a quantified line per role, plus a one-page PDF offer.” → “Separate About page lists employer, title, years and location per role, with numbers on three of five roles, plus a 'Want a 1-pager PDF?' link to /resume.” — MotherDuck (450%, 100K+), Trade Republic (50%+), Back Market (100+ users) quantified; Klarna and AXA lines have no numbers. [mehdio:verify.v33work/mehdio-about-probe.json.text]
- **W4** (what_works) corrected: “Light and fast: 446 KB total and LCP about 1.1 s (measured, unthrottled), with a ⌘K search.” → “Light: about 446 KB total and 201 KB JS (measured); LCP 2.1 s desktop, 2.5 s mobile in the verifier reload; ⌘K search button in the header.” — Reload: total 445,637 B, LCP 2,140 ms (element h1 MEHDIO) and 2,516 ms mobile; 'about 1.1 s' not reproduced. [mehdio:verify.record.summary.lcp_ms_desktop]
- **D2** (what_doesnt) corrected: “A typewriter tagline is the only descriptive line and is mid-word whenever you look, with no reduced-motion handling.” → “A typewriter tagline is the only visible descriptive line and is mid-phrase whenever you look ('Te|' desktop, 'Build' mobile), with no reduced-motion handling.” — Desktop fold shows 'Te|', mobile shows 'Build' (a whole word, so 'mid-word' is overstated); reduced-motion mentions CSS 0, JS 0. [mehdio:verify/mobile-fold.png]
- **D3** (what_doesnt) corrected: “Contact lives only in the footer and the résumé is two clicks deep behind About.” → “The Contact page link sits only in the footer (header carries social icons on desktop), and the résumé is two clicks deep: About, then 'Want a 1-pager PDF?'.” — Contact y=1,116 desktop / 1,482 mobile, footer; header has YouTube/GitHub/LinkedIn/X icons; /about links 'Want a 1-pager PDF?' -> /resume. [mehdio:verify.v33work/mehdio-about-probe.json.links]
- **P3** (pattern) corrected: “source-links-on-cards” → “source-links-on-cards on open-source projects only; product cards (Try MotherDuck, Duckify) have a 'Visit project' link but no source link.” — GitHub buttons on 2 of the first 4 cards. [mehdio:verify/tiles/id-00.png]
- **P8** (pattern) corrected: “fast-static” → “light-homepage rather than fast-static: 446 KB, 201 KB JS in 17 scripts (Next.js), LCP 2.1–2.5 s in the reload.” — /_next/image assets, 17 script requests, LCP 2,140 / 2,516 ms. [mehdio:verify.record.summary.lcp_ms_desktop]
- **P10** (pattern) corrected: “vague-tagline” → “tagline-fragment: the visible descriptor is a typewriter fragment ('Te', 'Build'); 'Build AI workflows for…' is specific but rarely readable whole.” — Folds show 'Te|' and 'Build'; DOM caught 'Bui' / 'Build AI workflows for'. Not vague in wording, only in delivery. [mehdio:verify.record.summary.hero_text_mobile]
- **M1** (memorable_thing) corrected: “Cartoon cut-out face in an orange blob next to hand-lettered 'MEHDIO', above a row of brand-coloured audience counters” → “A photo cut-out face in an orange sticker blob next to hand-lettered 'MEHDIO', above a row of brand-coloured audience counters.” — The face is a photograph cut out sticker-style, not a cartoon; rest matches the desktop fold. [mehdio:verify/desktop-fold.png]
- **N4** (measured_number) corrected: “lcp_ms_desktop = 1068” → “lcp_ms_desktop = 2140 in the verifier reload (1068 originally); report as about 1–2 s.” — +1,072 ms, +100%: outside ±50% and ±500 ms. [mehdio:verify.record.summary.lcp_ms_desktop]

### pahasara

- **W1** (what_works) corrected: “Whole homepage is one short screen-and-a-half: intro, CV button, then a dated work index; nothing to hunt for.” → “Whole homepage is about two desktop screens (1791 px, 2.7 phone screens): intro, CV button, then a dated work index; nothing to hunt for.” — Desktop scroll_height 1791 at 900 px viewport is 2.0 screens, mobile 2247 at 844 is 2.7, so 'screen-and-a-half' understates it. Structure (intro, CV, work index, footer) matches the tile. [pahasara:verify.record.home.desktop.dom.scroll_height]
- **D1** (what_doesnt) corrected: “Zoom is disabled by the viewport meta, icon links have no names, and focus outlines are removed.” → “Zoom is disabled by the viewport meta and two icon links have no names; outlines are set to none, and the CV button shows no visible focus.” — axe: meta-viewport critical, link-name serious (2 nodes). focus_check: outline 'none 0px' on all five, but four links show some visible change; only the Download CV button shows none. [pahasara:verify.record.home.desktop.focus_check]
- **D2** (what_doesnt) corrected: “Homepage never says 'undergraduate' or a graduation year; level and availability are only on About or in the PDF.” → “Homepage never says 'undergraduate' or a graduation year; level (ICT undergraduate, BSc 2023-2026) is only on About, and no page checked states availability.” — No undergrad/graduat/student/available text in the homepage record. Probe of /about finds 'Now I'm an ICT undergraduate' and 'BSc ... 2023-2026', but no availability or 'open to' wording. PDF not opened. [pahasara:verify.record.summary.hero_text]
- **D4** (what_doesnt) corrected: “2.9 MB of page weight, mostly full-size screenshots shown as small thumbnails, and 8 failed requests.” → “2.9 MB of page weight, 2.5 MB of it images shown as small thumbnails, and 4 failed requests on load (5 after scrolling).” — total_bytes 2,895,009; image 14 requests 2,520,284 bytes. failed 4 initial, 5 after scroll, 2 on mobile: outside +/-30% of the claimed 8. [pahasara:verify.record.home.desktop.network_initial]

### rauno

- **W2** (what_works) corrected: “A dated index of work (month + year on every tile) with a single clear action per item: view production, view prototype or read essay.” → “Dated index of work (month + year on every tile); many tiles carry one action (View Production, View Prototype, Read Essay), but some, e.g. Posters, Vercel Agent, Combobox, have none.” — /craft fold: Posters, Vercel Agent and Combobox tiles show a date but no action button; DD System, History of Software Design, Novelty do. [rauno:verify/inner-desktop-fold.png]
- **W5** (what_works) corrected: “Homepage is light: 186 KB JS, 660 KB total, LCP under 1.4 s (measured).” → “Homepage is light: 186 KB JS, 660 KB total (measured); LCP about 2.0 s desktop and 2.7 s mobile in the verifier reload.” — Reload: js 186,457 B, total 660,341 B, LCP 1,988 ms desktop, 2,736 ms mobile. 'Under 1.4 s' not reproduced. [rauno:verify.record.summary.lcp_ms_desktop]
- **D1** (what_doesnt) corrected: “Every destination sits in a sideways strip with no nav bar, so most of the site is off-screen at load and contact is seven cards away.” → “No nav bar: every destination sits in a sideways strip, mostly off-screen at load; contact is on the seventh card, about five wheel-scrolls on desktop.” — nav []. Wheel test: links card (Twitter, Email, GitHub) fully in view after 5 x 900 px scrolls; Twitter enters right edge after 4. [rauno:verify.v33work/rauno-check.json.steps]
- **D3** (what_doesnt) corrected: “Work index autoplays 79 videos, 46.8 MB of media on first load, with 68 of 81 images lacking alt text (measured).” → “/craft autoplays 68 muted videos and pulled 24 MB (desktop) to 44 MB (mobile) of media on first load; 68 of 81 images lack alt (measured).” — Reload: 68 <video autoplay> elements; media 81 req/23.7 MB desktop, 78 req/43.9 MB mobile. '79' was the media request count, not videos. [rauno:verify.record.inner.desktop.network_initial.by_type.media]
- **D4** (what_doesnt) corrected: “No résumé, roles or dates anywhere on the pages seen.” → “No résumé and no dated role history; the only role information is the hero sentence. Work tiles on /craft are dated.” — has_resume_link false; hero names role and Vercel but no dates; /craft tiles all show month + year, so 'no dates anywhere' is wrong. [rauno:verify/inner-desktop-fold.png]
- **P10** (pattern) corrected: “fast-static” → “light-homepage rather than fast-static: 186 KB JS, 660 KB total, but a JS-driven Next.js app (16 scripts), LCP 2.0–2.7 s in reload.” — 16 script requests, /_next/ asset paths, wheel-to-horizontal scrolling needs JS; LCP 1,988/2,736 ms. [rauno:verify.record.home.desktop.network_initial.by_type.script]
- **K8** (time_to_key_info) corrected: “desktop contact: {"scrolls": 6, "clicks": 1, "note": "Estimated: links card is seventh in the strip, beyond the captured 6,648 px width"}” → “desktop contact: {"scrolls": 5, "clicks": 1, "note": "Measured by wheel test: links card in view after 5 scrolls (Twitter enters after 4); Email is a copy button"}” — Wheel steps of 900 px: Twitter x=1,254 at step 4, whole card (Twitter, Email, GitHub) in view at step 5. [rauno:verify.v33work/rauno-check.json.steps]

### redblobgames

All claims held.

### robinlinacre

- **W4** (what_works) corrected: “Very light homepage: no first-party JS, 237 KB total, LCP about 1 s (measured, unthrottled).” → “Light homepage: no first-party JS (174 KB third-party analytics), 237 KB total; LCP measured 1.0 s in the first capture but 1.8 s desktop / 1.5 s mobile on reload.” — Reload: js_first_party_bytes 0, js_bytes 174406 (googletagmanager 164 KB), total 237067, LCP 1824 ms desktop, 1488 ms mobile. [robinlinacre:verify.record.home.desktop.network_initial]
- **P10** (pattern) corrected: “fast-static” → “Static and light (237 KB, no first-party JS) but carries 174 KB of third-party analytics JS; LCP 1.0–1.8 s across two loads.” — js_first_party_bytes 0 holds; reload LCP 1824 ms desktop is above the 1.5 s 'fast' anchor. [robinlinacre:verify.record.home.desktop.network_initial]
- **N4** (measured_number) corrected: “lcp_ms_desktop = 1000” → “lcp_ms_desktop = 1824 on reload (claimed 1000; outside ±500 ms / ±50%). Network variance: DCL 1764 vs 985 ms.” — Reload lcp 1824 ms, same element (div, 9300 px²); +824 ms exceeds tolerance. [robinlinacre:verify.record.summary.lcp_ms_desktop]

### samwho

- **P10** (pattern) corrected: “reduced-motion-honoured” → “reduced-motion rules present (essay: 2 CSS, 1 JS matches; home: 3 CSS, 1 JS); not tested whether animations actually stop.” — Rubric treats reduced_motion mentions as a hint, not proof; no reduced-motion emulation run in this reload. [samwho:verify.record.inner.desktop.code_scan]
- **P11** (pattern) corrected: “skip-link” → “skip-link on essay pages only ('Skip to main content'); the homepage has none.” — inner skip_link 'Skip to main content'; home desktop and mobile skip_link null. [samwho:verify.record.inner.desktop.dom.a11y_manual]
- **P13** (pattern) corrected: “fast-static” → “Static and tiny (56 KB, 4.4 KB JS), but reload LCP was 3.5 s desktop / 2.9 s mobile (TTFB 1.0 s), so 'fast' did not reproduce.” — lcp_ms_desktop 3516, lcp_ms_mobile 2944; DCL 3609 ms; weight unchanged. [samwho:verify.record.summary.lcp_ms_desktop]
- **N4** (measured_number) corrected: “lcp_ms_desktop = 1160” → “lcp_ms_desktop = 3516 on reload (claimed 1160; +2356 ms, outside tolerance). Slow network on reload: TTFB 1020 ms, DCL 3609 ms.” — Same LCP element (p); bytes identical, so the gap is network timing, but it does not reproduce. [samwho:verify.record.summary.lcp_ms_desktop]

### shaundataanalytics

- **D1** (what_doesnt) corrected: “'Live Demo' buttons on 3 of 7 cards open GitHub repositories, not running demos, so the label overpromises.” → “3 of the 7 'Live Demo' buttons (across 8 project cards) open GitHub repositories, not running demos, so the label overpromises.” — demo_links: 7 'Live Demo' anchors; three go to github.com/ShaunDataAnalytics/... repos (data-science-portfolio-labs, collaborative-book-recommender, real-estate-valuation-ml). There are 8 project h3 cards, not 7. [shaundataanalytics:verify.record.home.desktop.dom.features.demo_links]
- **P13** (pattern) corrected: “buzzword-cloud” → “generic-skill-icon-grid: 'Tech Stack' is eight icon tiles with vague labels (Data Engineering, Python, Analytics, Cloud, Pipelines, Automation); generic rather than a buzzword cloud.” — Section shows a 4x2 grid of icons with one- or two-word category labels; no tool names, no word cloud. [shaundataanalytics:verify/tiles/d-00.png]
- **P19** (pattern) corrected: “horizontal-overflow” → “Inner case-study page only: content wider than 390 px makes mobile lay it out at 496 px and shrink it; no scrollbar overflow, and the homepage fits at 390.” — inner.mobile.dom.vw 496 and scroll_width 496 with horizontal_overflow false; home mobile vw 390, scroll_width 390. [shaundataanalytics:verify.record.inner.mobile.dom.vw]

### shud

- **W4** (what_works) corrected: “Smaller projects are framed as problem then outcome (built for one need, later ported or adopted).” → “Some smaller projects are framed as problem then outcome (Nextra filled a gap; Wrap Balancer was built for the Vercel blog, later ported); others are plain descriptions.” — GPU Lexer and Next View Transitions entries are plain descriptions without a problem/outcome frame. [shud:verify/tiles/id-01.png]
- **D5** (what_doesnt) removed: “1.16 MB of web fonts (measured, 3 files) for a page that is a few paragraphs of text.” — Not reproduced: my reload loads 3 font files totalling 65,194 B (not 1.16 MB); whole homepage 305,456 B. The site appears to have changed its font delivery. [shud:verify.record.home.desktop.network_initial.by_type]
- **M1** (memorable_thing) corrected: “Each project on /projects is a product wordmark followed by one first-person paragraph saying exactly what the owner personally led or built.” → “Each main /projects entry is a product wordmark plus a short paragraph, mostly first-person on what the owner led or built (a few are plain descriptions).” — Not every entry is first-person: 'GPU Lexer is a neural network-powered syntax highlighter...' is a plain description. [shud:verify/tiles/id-01.png]
- **N2** (measured_number) corrected: “total_bytes_desktop = 1391873” → “total_bytes_desktop = 305456 (verifier reload; fonts 65 KB)” — Reload 305,456 B vs claim 1,391,873 B: −78%, outside ±30%. Font bytes fell from 1.16 MB to 65 KB. [shud:verify.record.summary.total_bytes_desktop]

### taniarascia

- **W1** (what_works) corrected: “A short dated timeline under the name gives level and career path in one glance; each row links to supporting writing.” → “A four-row dated timeline under the name gives level and career path at a glance; three of the four rows link out (chef story, career-change writing, résumé).” — Not every row links: the 1998–2006 'Geocities kid' row has no link, and the 2021–now row links to /resume/, not writing. [taniarascia:verify/desktop-fold.png]
- **W3** (what_works) corrected: “Project cards carry year, one-line what, star count and consistent Article / Demo / Source links, so every card is verifiable.” → “Project cards carry year, one-line what, star count and Article / Demo / Source links where they exist; every card has Source, so each is verifiable.” — Links are not consistent: taniarascia.com and Laconia have Source only, New Moon Demo+Source, Snek Article+Source. [taniarascia:verify/inner-desktop-fold.png]
- **W4** (what_works) corrected: “Theme follows the OS (cream light / near-black dark) with the same layout and accent in both.” → “Theme follows the OS (cream light, near-black dark) with the same layout; the accent shifts from deep magenta to coral in dark mode.” — Light bg rgb(248,244,230), dark rgb(32,32,37), layout identical, but link/accent colour changes from magenta to coral. [taniarascia:verify/desktop-fold-dark.png]
- **M1** (memorable_thing) corrected: “The dated four-row career timeline from professional chef to principal software engineer, right under the name.” → “The dated four-row timeline right under the name, from Geocities kid and professional chef to principal software engineer.” — Timeline starts at 1998–2006 'Geocities kid'; professional chef is the second row, not the first. [taniarascia:verify/desktop-fold.png]

### wisl

- **D4** (what_doesnt) corrected: “The decorative fox canvas runs off the right edge on mobile, and the fade-in pitch paragraph is the LCP element (2.2 s desktop).” → “Fox canvas is cut off at the right edge on mobile; the fade-in pitch paragraph is the LCP element (5.0 s desktop, 4.4 s mobile in the reload).” — LCP el p.pitch.reveal at 5,020 ms desktop and 4,380 ms mobile; the 2.2 s figure was not reproduced. Fox visibly clipped on mobile fold. [wisl:verify.record.home.desktop.lcp]
- **P13** (pattern) corrected: “fast-static” → “static and light (12 KB JS, 296 KB total), but not reliably fast: LCP 2.2 s originally, 5.0 s in the reload, held back by the fade-in paragraph.” — 3 scripts, 11,762 B; LCP 5,020 ms desktop in reload, element p.pitch.reveal. [wisl:verify.record.summary.lcp_ms_desktop]
- **N4** (measured_number) corrected: “lcp_ms_desktop = 2168” → “lcp_ms_desktop = 5020 in the verifier reload (2168 originally); treat as 2–5 s, delayed by the fade-in paragraph.” — 5,020 vs 2,168 ms: +2,852 ms, +132%, outside ±50%/±500 ms. [wisl:verify.record.summary.lcp_ms_desktop]

## Blind re-score

Not run yet.

## Tie-break resolutions

None needed so far.

