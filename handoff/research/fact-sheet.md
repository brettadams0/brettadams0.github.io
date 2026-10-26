# Fact sheet: Brett Adams and the projects on brettadams0.github.io

Phase 1 output. Every later agent takes facts about Brett and the projects from this file only.
Compiled 2026-10-21 from the site repo at `f1bfc5b` (branch `ccr-fbafa18f-2pn0ks`) and shallow clones of
Brett's project repos. Site files are cited as `file:line`; other repos as `repo@sha:path:line`.

Repo SHAs used (`git -C <dir> rev-parse --short HEAD`):

| Clone | SHA | Public URL |
|---|---|---|
| neuronet-arena | `07bc91f` | https://github.com/brettadams0/neuronet-arena |
| mcp-surface | `ea93b87` | https://github.com/brettadams0/mcp-surface |
| google-workspace-mcp | `1f1fc45` | https://github.com/brettadams0/google-workspace-mcp |
| reddit-mcp | `0944987` | https://github.com/brettadams0/reddit-mcp |
| yahoo-fantasy-mcp | `d5907c4` | https://github.com/brettadams0/yahoo-fantasy-mcp |
| supercell-mcp | `bcc91c3` | https://github.com/brettadams0/supercell-mcp |
| chesscom-mcp | `cddf365` | https://github.com/brettadams0/chesscom-mcp |
| promptpal | `82ffb5d` | https://github.com/brettadams0/PromptPal |
| technest | `c7e590a` | https://github.com/brettadams0/TechNest |
| mazefinder-geneticalgorithm | `3184019` | https://github.com/brettadams0/MazeFinder-GeneticAlgorithm |
| sift | `e37fa56` | https://github.com/brettadams0/sift |

Labels: **measured** means counted or computed in this session, with the command given; **stated**
means a source says it and it was not re-measured; **not checkable** means no source in reach can
confirm or deny it. No page on the web was loaded for this sheet.

---

## 1. How to use this sheet

1. Source order (PROMPT.md section 1): about-me facts (roles, employers, titles, dates, education,
   availability) come only from `index.html` and `resume/resume.tex`; project facts come from
   `index.html`, `resume.tex`, `mcp/index.html` (MCP facts) and the project's own README, code or
   handoff file.
2. Where `index.html` and `resume.tex` disagree, the disagreement is in section 8 as a question for
   Brett. Do not pick a side in copy; cite the source you use.
3. Claims in section 9 are contradicted by code, a README or the handoff file. Never repeat them as
   fact; they appear nowhere else in this sheet except as flagged.

---

## 2. Identity and contact

| Fact | Value | Sources |
|---|---|---|
| Name | Brett Adams | `index.html:9`, `resume/resume.tex:77`, `_config.yml:1`, `_config.yml:8`, `_layouts/default.html:18` |
| Email | adamsbrett00@gmail.com | `index.html:15`, `index.html:186`, `_layouts/default.html:63`, `resume/resume.tex:79` |
| Phone | 289-783-1500 | `resume/resume.tex:79` and the public PDF (extracted text line 2). **Not on the site.** The PDF is linked from the site, so the number is already public through it. |
| LinkedIn | https://www.linkedin.com/in/bretta/ | `index.html:14`, `index.html:188`, `_layouts/default.html:25`, `_layouts/default.html:62`, `_config.yml:19`, `resume/resume.tex:80` |
| GitHub | https://github.com/brettadams0 | `index.html:13`, `index.html:189`, `_layouts/default.html:24`, `_layouts/default.html:61`, `_config.yml:18`, `resume/resume.tex:81` |
| Site URL | https://brettadams0.github.io | `_config.yml:6`, `resume/resume.tex:82`, `robots.txt:4` |
| Custom domain | None. No `CNAME` file in the repo (measured: `ls CNAME` fails). | repo root |
| Site title / tagline | "Brett Adams" / "Data & Software Engineering" | `_config.yml:1-2` |
| Site description | "Data Analyst Intern at Geotab and Computer Science student at the University of Windsor. Experience, projects and résumé." | `_config.yml:3-5` |
| Hero kicker | "data & software engineering" | `index.html:8` |
| Brand in header | "brett adams", rendered with a CSS `"> "` prefix | `_layouts/default.html:42`, `assets/css/site.css:121-124` |
| Personal location | **No source states where Brett lives.** Only job locations (section 5) and the university's city, "Windsor, ON" (`resume/resume.tex:89`). | — |
| Headshot | None in `assets/img/` (measured: `ls assets/img`). | — |
| Social card text | "// data & software engineering", "Brett Adams", "Data Analyst Intern at Geotab. Computer Science, University of Windsor.", "> brettadams0.github.io" (1200×630 PNG, viewed this session) | `assets/img/social-card.png`, `_config.yml:20-25` |

**JSON-LD** (`_layouts/default.html:11-30`, emitted only when `page.url == "/"`):

| Field | Value | Line |
|---|---|---|
| `@type` | `ProfilePage` with `mainEntity` `Person` | `_layouts/default.html:15-17` |
| `name` | Brett Adams | `_layouts/default.html:18` |
| `jobTitle` | Data Analyst Intern | `_layouts/default.html:19` |
| `worksFor` | Organization "Geotab" | `_layouts/default.html:20` |
| `alumniOf` | CollegeOrUniversity "University of Windsor" | `_layouts/default.html:21` |
| `url` | `{{ site.url }}/` | `_layouts/default.html:22` |
| `sameAs` | GitHub, LinkedIn | `_layouts/default.html:23-26` |

---

## 3. Education

| Source | Wording | Dates | Location |
|---|---|---|---|
| `index.html:175-177` | "University of Windsor" / "Honours Computer Science, Software Engineering Specialization (Co-op)" | "2022 – 2027" | not given |
| `resume/resume.tex:87-89` | "Honours Computer Science with Software Engineering Specialization Co-op" / "University of Windsor" | "Sep 2022 - Present" | "Windsor, ON" |
| `index.html:10` (hero lede) | "Computer Science at the University of Windsor." | — | — |
| `_config.yml:4-5` | "Computer Science student at the University of Windsor" | — | — |
| `_layouts/default.html:21` | JSON-LD `alumniOf` University of Windsor | — | — |

- Education sits in `<div id="education">` inside `section#skills` (`index.html:172`), with its own `h2`
  (`index.html:173`). It is not in the nav (`_layouts/default.html:44-48`).
- No GPA, awards or coursework appear in any source (read in full: `index.html:1-193`, `resume/resume.tex:1-167`). Do not add them.

---

## 4. Availability and goals

| Fact | Exact wording | Source |
|---|---|---|
| Status line | "Graduating 2027 · Open to full-time roles" | `index.html:7` |
| Expected end year | "2022 – 2027" | `index.html:177` |
| Résumé | No graduation date, availability or target role. Education reads "Sep 2022 - Present". | `resume/resume.tex:88` |
| Target roles | No site or résumé line names target roles. The nearest is the kicker "data & software engineering" (`index.html:8`) and tagline "Data & Software Engineering" (`_config.yml:2`). PROMPT.md section 1 says "data engineering, data analytics or software engineering" but is marked "for orientation only". | — |

---

## 5. Experience

Wording matches between `index.html` and `resume.tex` for 12 of 13 bullets (**measured**: Python
script stripping HTML/LaTeX markup and comparing strings). Date formats differ throughout (site:
"Jan – Aug 2025", en dash, year once; résumé: "Jan 2025 - Aug 2025", hyphen); the dates themselves
agree. The résumé has no per-job chips; the site has chips per job.

### 5.1 Data Analyst Intern, Geotab

| | index.html | resume.tex |
|---|---|---|
| Title / employer | "Data Analyst Intern" / "Geotab" (`:27`) | "Data Analyst Intern" / "Geotab" (`:103-104`) |
| Dates | "May 2026 – Present" (`:28`) | "May 2026 - Present" (`:103`) |
| Location | "Oakville, ON" (`:28`) | "Oakville, ON" (`:104`) |
| Bullet 1 | "Maintain and debug production Apache Airflow DAGs on **Google Cloud Composer** that run the SalesOps data pipelines." (`:31`) | "Maintain and debug production Apache Airflow DAGs on **Cloud Composer** that run the SalesOps data pipelines." (`:106`) — **WORDING DIFFERS** |
| Bullet 2 | "Write and deploy production BigQuery SQL for Revenue Operations tables, including the device status, database dimension and accounts master datasets." (`:32`) | same (`:107`) |
| Bullet 3 | "Ship changes through GitLab CI/CD, from branch development through staging validation to production." (`:33`) | same (`:108`) |
| Bullet 4 | "Carry out live table migrations against fixed infrastructure deadlines, coordinating with dataset owners." (`:34`) | same (`:109`) |
| Chips | BigQuery, Airflow, Cloud Composer, SQL, GitLab CI/CD (`:36`) | — |

- Site-only summary: "Data Analyst Intern at Geotab, on production pipelines in BigQuery and Airflow."
  with a link to https://www.geotab.com (`index.html:10`).
- Geotab is marked current with class `job-current` (`index.html:25`).
- No numbers (DAG count, tables, rows, users, hours) in any source (`index.html:31-34`, `resume/resume.tex:106-109`).
- Internal names used: "SalesOps", "Revenue Operations", "device status, database dimension and
  accounts master datasets" (`index.html:31-32`, `resume/resume.tex:106-107`).

### 5.2 Data Management Associate, Co-op, Ontario Ministry of Transportation

| | index.html | resume.tex |
|---|---|---|
| Title / employer | "Data Management Associate, Co-op" / "Ontario Ministry of Transportation" (`:41`) | same (`:113-114`) |
| Dates | "Jan – Aug 2025" (`:42`) | "Jan 2025 - Aug 2025" (`:113`) |
| Location | "St. Catharines, ON" (`:42`) | "St. Catharines, ON" (`:114`) |
| Bullet 1 | "Built Power BI dashboards on Azure Databricks and SQL that replaced manual reporting across business units." (`:45`) | same (`:116`) |
| Bullet 2 | "Automated legacy workflows with SQL queries and VBA scripts, cutting processing time from hours to minutes." (`:46`) | same (`:117`) |
| Bullet 3 | "Led the rollout of the internal Data Broker Tool, onboarding teams and standardizing the data vocabulary." (`:47`) | same (`:118`) |
| Chips | Power BI, Azure Databricks, SQL, VBA (`:49`) | — |

### 5.3 Digital Solutions, Co-op, CIMCO Refrigeration (2024)

| | index.html | resume.tex |
|---|---|---|
| Title / employer | "Digital Solutions, Co-op" / "CIMCO Refrigeration" (`:54`) | same (`:122-123`) |
| Dates | "Apr – Sep 2024" (`:55`) | "Apr 2024 - Sep 2024" (`:122`) |
| Location | "Burlington, ON" (`:55`) | "Burlington, ON" (`:123`) |
| Bullet 1 | "Built Flask web applications that deliver real-time data and alarm information to NHL arena operations teams, including a thermal imagery viewer used by arena technicians." (`:58`) | same (`:125`) |
| Bullet 2 | "Wrote a Python pipeline that aggregates data from two APIs into structured JSON for the arena interface." (`:59`) | same (`:126`) |
| Bullet 3 | "Automated controller data collection for CAG engineers with a Python tool, replacing manual data entry." (`:60`) | same (`:127`) |
| Bullet 4 | "Designed and ran hands-on Python training sessions for over 20 employees." (`:61`) | same (`:128`) |
| Chips | Python, Flask, JavaScript (`:63`) | — |

- "over 20 employees" is the only number in any experience bullet (`index.html:61`,
  `resume/resume.tex:128`). "hours to minutes" (MTO) is qualitative.
- "CAG" is not expanded anywhere (`index.html:60`, `resume/resume.tex:127`).

### 5.4 Digital Solutions, CIMCO Refrigeration (2023)

| | index.html | resume.tex |
|---|---|---|
| Title / employer | "Digital Solutions" / "CIMCO Refrigeration" (`:68`) — no "Co-op" | same (`:132-133`) — no "Co-op" |
| Dates | "Apr – Sep 2023" (`:69`) | "Apr 2023 - Sep 2023" (`:132`) |
| Location | "Burlington, ON" (`:69`) | "Burlington, ON" (`:133`) |
| Bullet 1 | "Built Power Platform tools adopted by several departments to automate repetitive manual workflows." (`:72`) | same (`:135`) |
| Bullet 2 | "Automated SharePoint document routing and approvals, cutting turnaround time between departments." (`:73`) | same (`:136`) |
| Chips | Power Platform, SharePoint (`:75`) | — |

### 5.5 Internship story raw material (for spec section 4)

Only these facts exist:

- Pipelines: Airflow DAGs on Composer, BigQuery SQL, GitLab CI/CD, live table migrations at Geotab (`index.html:31-34`, `resume/resume.tex:106-109`).
- BI: Power BI dashboards on Azure Databricks and SQL at MTO (`index.html:45`, `resume/resume.tex:116`).
- Automation: SQL + VBA workflows at MTO (`index.html:46`, `resume/resume.tex:117`); controller data collection tool at CIMCO 2024 (`index.html:60`, `resume/resume.tex:127`); Power Platform tools and SharePoint routing at CIMCO 2023 (`index.html:72-73`, `resume/resume.tex:135-136`).
- Rollout and training: Data Broker Tool rollout at MTO (`index.html:47`, `resume/resume.tex:118`); Python training for over 20 employees at CIMCO 2024 (`index.html:61`, `resume/resume.tex:128`).
- Web apps: Flask apps and a thermal imagery viewer for NHL arena teams at CIMCO 2024 (`index.html:58-59`, `resume/resume.tex:125-126`).

---

## 6. Skills

| Item | Site (`index.html:165-169`) | Résumé (`resume/resume.tex:96-98`) | Note |
|---|---|---|---|
| Python, SQL, Java, C++, C, JavaScript, TypeScript | Languages (`:166`) | Programming Languages (`:96`) | both |
| PHP | Languages (`:166`) | — | **site only** |
| HTML/CSS | Web (`:169`) | Programming Languages (`:96`) | both, different group |
| VBA | — (job chip only, `:49`) | Programming Languages (`:96`) | **résumé skills only** |
| BigQuery, Apache Airflow, Cloud Composer, Azure Databricks, Power BI, MySQL | Data (`:167`) | Data & Cloud (`:97`) | both, same order |
| Git, GitLab CI/CD, GitHub Actions, Docker, Node.js | Tools (`:168`) | Technologies (`:98`) | both |
| Linux | Tools (`:168`) | — | **site only** |
| Flask, React | Web (`:169`) | Technologies (`:98`) | both |
| Next.js | Web (`:169`) | — | **site only** |
| Power Platform | — (job chip only, `:75`) | Technologies (`:98`) | **résumé skills only** |
| SharePoint | — (job chip only, `:75`) | — | job chip only |

- Group names differ: site Languages / Data / Tools / Web (`index.html:166-169`); résumé Programming
  Languages / Data & Cloud / Technologies (`resume/resume.tex:96-98`).
- Language order differs (site: TypeScript, JavaScript before PHP, Java, `index.html:166`; résumé: Java,
  C++, C, JavaScript, TypeScript, `resume/resume.tex:96`).
- Job chip "Airflow" (`index.html:36`) vs skill "Apache Airflow" (`index.html:167`, `resume.tex:97`).

---

## 7. Projects

### 7.1 neuronet-arena (live demo, featured card)

- **What:** evolution simulator; 40 agents with their own neural networks forage in a 2D arena; Python
  engine and a bit-for-bit JavaScript port that runs the demo (`neuronet-arena@07bc91f:README.md:6-10`).
- **Links:** demo `/neuronet-arena/` (`index.html:86`, `index.html:93`); source
  https://github.com/brettadams0/neuronet-arena (`index.html:94`). Badge "Live demo" (`index.html:89`).
  Card is `project-featured` (`index.html:85`); screenshot `assets/img/neuronet-arena.webp` 1280×800
  (`index.html:87`; file measured 1280×800, 30,948 B).
- **Dates:** 1.0.0 2025-04-14, 2.0.0 2026-10-16 (`neuronet-arena@07bc91f:CHANGELOG.md:40`, `:3`).
  Not on the résumé.
- **Tech:** Python ≥3.10, no runtime dependencies (`neuronet-arena@07bc91f:pyproject.toml:13-14`);
  optional pygame viewer (`:22`); JavaScript ES modules, no build step (`neuronet-arena/index.html:147`).
- **On the résumé:** no (`resume/resume.tex:140-165` lists mcp-surface, MCP servers, MazeFinder only).

| Claim | Where stated | Check | Result |
|---|---|---|---|
| "Forty agents with their own neural networks evolve to forage." | `index.html:90`; `neuronet-arena/index.html:38` | config `population: 40` (`neuronet-arena@07bc91f:src/neuronet/config.py:21`, `web/src/config.js:38`) | **confirmed** |
| "Watch any agent's network fire as it moves." | `index.html:90` | demo has a per-agent network canvas, "Click an agent to inspect its network" (`neuronet-arena/index.html:44`, `:107`) | **confirmed** |
| Chip "13.4 food vs 1.0 for the control" | `index.html:91` | README table 13.39 vs 0.98 (`neuronet-arena@07bc91f:README.md:41`). **Measured:** recomputed from `results/experiment.json` (8 seeds × 150 gens, mean of last 10 gens per seed, then mean across seeds): 13.39 selection on, 0.98 selection off; ranges 12.75-13.76 and 0.595-1.49 | **confirmed** (rounded) |
| "13.7× as much" | `neuronet-arena@07bc91f:README.md:46` | 13.39 / 0.98 = 13.66 (**measured**, arithmetic) | **confirmed** |
| "112-weight neural network" | `neuronet-arena/index.html:7`, `:38` | 8×10+10 + 10×2+2 = 112 (`src/neuronet/brain.py:25-46`, `config.py:47` hidden 10). **Measured:** `web/data/trained.json` holds 40 genomes of length 112 | **confirmed** |
| Chip "Python + JavaScript" | `index.html:91` | `src/neuronet/*.py`, `web/src/*.js` | **confirmed** |
| Chip "No ML libraries" | `index.html:91` | `dependencies = []` (`pyproject.toml:14`); "no NumPy, no PyTorch, no `random`" (`README.md:9`) | **confirmed** |
| JS engine reproduces Python "bit for bit" | `neuronet-arena/index.html:38`; `README.md:96-125` | **Measured:** `node --test web/test/{golden,snapshot,engine}.test.js` on Node v22.22.0: 11 tests, 11 pass, 0 fail. Python half (`tests/test_golden.py`) not run: pytest not installed | **confirmed (JS half)** |
| Trained for 200 generations in Python | `neuronet-arena/index.html:116` | `trained.json` `generation: 200`, `seed: 2026` (**measured**) | **confirmed** |
| CI on Python 3.10-3.13 and Node 20/22 | `neuronet-arena@07bc91f:CHANGELOG.md:37-38` | `.github/workflows/ci.yml:18`, `:37` | **confirmed** |
| Test counts (not quoted on site) | — | **Measured:** 77 `def test_` functions in `neuronet-arena@07bc91f:tests/*.py` (before parametrize expansion); 11 node:test tests in `web/test/*.test.js` | info only |

### 7.2 mcp-surface (live playground, npm package)

- **What:** TypeScript CLI and GitHub Action that connects to an MCP server like a client, checks what
  it exposes and diffs it against a committed snapshot (`mcp-surface@ea93b87:README.md:8`, `:85-92`,
  `:117-124`).
- **Links:** playground `/mcp-surface/` (`index.html:113`, `:120`); case study `/mcp/` (`index.html:121`);
  npm https://www.npmjs.com/package/mcp-surface (`index.html:122`); source
  https://github.com/brettadams0/mcp-surface (`index.html:123`). Badge "Live demo" (`index.html:116`).
  Screenshot `assets/img/mcp-surface.webp` 2200×1376 (measured; `index.html:114`).
- **Dates:** "Jul 2026 - Present" (`resume/resume.tex:141`); 0.1.0-0.3.0 all dated 2026-07-28
  (`mcp-surface@ea93b87:CHANGELOG.md:24-57`).
- **Version:** package 0.3.0 (`mcp-surface@ea93b87:package.json:3`); the site's bundled checks are
  "mcp-surface 0.3.0" (`mcp-surface/checks.js:1`). `[Unreleased]` entries exist
  (`mcp-surface@ea93b87:CHANGELOG.md:8-22`).
- **Tech:** TypeScript, Node ≥20.19 (`package.json:40`, `README.md:44`), Ajv, MCP SDK 1.x
  (`mcp-surface@ea93b87:package.json:50-56`).

| Claim | Where stated | Check | Result |
|---|---|---|---|
| "Probes an MCP server like a client would, validates its schemas, and fails CI when its tools change." | `index.html:117` | `README.md:8`, `:85-92` | **confirmed** |
| Chips TypeScript, npm, GitHub Action | `index.html:118` | `src/*.ts`; `package.json`; `action.yml:1-2` | **confirmed** |
| "Connects ... over stdio, Streamable HTTP or SSE" | `resume/resume.tex:144` | `README.md:48-55` | **confirmed** |
| "19 checks" | `resume/resume.tex:145`; `mcp/index.html:80` | **Measured:** 19 rule rows in `README.md:157-175`; 19 entries in `mcp-surface/app.js:10-28`; all 19 ids found in `mcp-surface/checks.js` by grep; in source, 17 literal `rule:` ids plus 2 from `src/checks/calls.ts:23`. They come from 11 check modules (`src/checks/index.ts:14-26`); 3 of the 19 run only with `--call` (`README.md:169-171`) | **confirmed** as 19 rules (wording note: 11 check functions) |
| "such as invalid JSON Schemas, unsatisfiable required fields and duplicate tool names" | `resume/resume.tex:145` | rules `schema-valid`, `schema-required-props`, `tool-duplicate-name` (`README.md:159`, `:162-163`) | **confirmed** |
| "Snapshots the tool surface ... also ships as a GitHub Action" | `resume/resume.tex:146` | `README.md:85-124`; `action.yml` | **confirmed** |
| "published on npm with build provenance" | `mcp/index.html:80` | Trusted Publishing release workflow, provenance noted (`.github/workflows/release.yml:38-56`, `:103`; `CHANGELOG.md:54-57`). npm page not loaded | **confirmed by repo**; npm page not checked |
| Playground runs "the checks from the npm package" | `mcp-surface/index.html:7`, `:37` | `mcp-surface/app.js:1-5` imports `runChecks` from generated `checks.js` | **confirmed** |
| Checker sent a hard-coded 0.1.0 and "now reads its version from its own package" | `mcp/index.html:145-149` | fix in `src/version.ts:6-22`, listed under `[Unreleased]` (`CHANGELOG.md:8-16`); package still 0.3.0 | **confirmed in repo; not checkable whether released to npm** |
| CI matrix (not quoted on site) | — | Node 20.19, 22, 24 (`.github/workflows/ci.yml:14`) | info only |
| Test count | part of "114 offline tests" (`mcp/index.html:13`) | **Measured:** `grep -cE '^\s*(it|test)\('`: 42 + 5 + 7 = 54 in `test/*.ts` | see 7.3 |
| npm `latest` for mcp-surface (added after phase 1) | — | https://registry.npmjs.org/mcp-surface fetched 2026-10-21 13:43 UTC; re-checked with `npm view mcp-surface version dist-tags` on 2026-10-23 23:33 UTC | 0.3.0 (**measured**), both times. That fits the version fix above still being `[Unreleased]` (inferred) |

### 7.3 MCP servers (Google Workspace and Reddit featured; five in total)

- **Site card:** "MCP servers for Google Workspace and Reddit" / "Two servers that let an AI assistant
  act on real accounts: 23 tools for Gmail, Calendar, Drive, Sheets and YouTube, 12 for Reddit."
  (`index.html:102-103`); chips Node.js, OAuth 2.0, Snapshot-checked in CI (`index.html:104`); links
  Case study, Google Workspace server, Reddit server (`index.html:106-108`); screenshot links to
  google-workspace-mcp (`index.html:99`); image `mcp-servers.webp` 2200×1376 (measured) showing both
  servers' `npm run surface` output with "No findings" and "matches" (viewed).
- **Résumé:** "MCP Servers for Claude Code", "Jul 2026 - Present", "Five Node.js servers exposing 69
  tools to AI assistants" (`resume/resume.tex:150-151`), bullets `:153-155`.
- **/mcp/:** "2 servers · 35 tools · 3 repositories · 114 offline tests" (`mcp/index.html:13`), title
  "Two MCP servers that act on real accounts, and the tool that checks them" (`mcp/index.html:12`),
  eyebrow "Case study · October 2026" (`mcp/index.html:11`).

**Tool counts (measured).** Method: `grep -rnE "registerTool\(" src | wc -l` and the length of
`tools` in each repo's committed `mcp-surface.json`; both agree for every repo.

| Server | Tools | Auth | Version / first release | CI Node matrix | Offline tests (measured) | Size vs 16 KB budget (measured) |
|---|---|---|---|---|---|---|
| google-workspace-mcp | **23** (gmail 3, calendar 4, drive 1, sheets 2, youtube 13) | own Google OAuth client, no restricted scopes (`README.md:24-36`) | 2.0.0 (`package.json:3`); 1.0.0 2026-07-28 (`CHANGELOG.md:70`) | **22.x, 24.x** (`.github/workflows/ci.yml:19`); engines `>=22` (`package.json:51`) | 33 `test(` in `test/tools.test.js` | 16,337 B by the checker's formula (name + description + inputSchema, `mcp-surface@ea93b87:src/checks/surface.ts:91-97`), 47 B under 16,384 |
| reddit-mcp | **12** (7 read, 5 write) | OAuth2, non-rotating refresh token (`README.md:81-85`) | 1.1.0 (`package.json:3`); 1.0.0 2026-07-28 (`CHANGELOG.md:51`) | 20.x, 22.x, 24.x (`ci.yml:19`) | 27 | 6,409 B |
| yahoo-fantasy-mcp | **10**, all read-only (`README.md:9-12`, `:31-32`) | OAuth2, rotating refresh token (`README.md:64-69`) | 1.1.0; 1.0.0 2026-07-28 (`CHANGELOG.md:44`) | 20.x, 22.x, 24.x (`ci.yml:19`) | 19 | — |
| supercell-mcp | **15** across Clash of Clans, Clash Royale, Brawl Stars (`README.md:8-15`) | API keys bound to IP (`README.md:44-48`) | 1.1.0; 1.0.0 2026-07-28 (`CHANGELOG.md:42`) | 20.x, 22.x, 24.x (`ci.yml:19`) | 19 | — |
| chesscom-mcp | **9**, read-only, no auth (`README.md:8-12`) | none | 1.1.0; 1.0.0 2026-07-28 (`CHANGELOG.md:38`) | 20.x, 22.x, 24.x (`ci.yml:19`) | 14 | — |
| **Total** | **69** | | | | 112 | |

Repo paths in the table are relative to each server's clone at the SHA in the header table.

| Claim | Where stated | Check | Result |
|---|---|---|---|
| 23 Google tools for Gmail, Calendar, Drive, Sheets, YouTube | `index.html:103`; `mcp/index.html:72`; `resume/resume.tex:154` | measured 23 (above); `google-workspace-mcp@1f1fc45:README.md:10` | **confirmed** |
| 12 Reddit tools | `index.html:103`; `mcp/index.html:76` | measured 12; `reddit-mcp@0944987:README.md:12` | **confirmed** |
| "five of which post, comment, vote, message or mark mail read" | `mcp/index.html:76` | **Measured:** 5 tools in `mcp-surface.json` without `readOnlyHint` (mark_read, send_message, submit_comment, submit_post, vote) | **confirmed** |
| "35 tools" | `mcp/index.html:5`, `:13` | 23 + 12 | **confirmed** |
| "Five Node.js servers exposing 69 tools" | `resume/resume.tex:151` | 23+12+10+15+9 = 69 (measured) | **confirmed** |
| "three Supercell game APIs" | `resume/resume.tex:153` | `supercell-mcp@bcc91c3:README.md:8` | **confirmed** |
| "handling OAuth 2.0 and API-key auth" | `resume/resume.tex:153` | OAuth2 (Google, Reddit, Yahoo), API keys (Supercell); Chess.com needs none (`chesscom-mcp@cddf365:README.md:9-10`) | **confirmed** |
| "self-registered OAuth client that requests only non-restricted scopes" | `resume/resume.tex:154`; `mcp/index.html:72` | `google-workspace-mcp@1f1fc45:README.md:24-36` | **confirmed** |
| "3 repositories" | `mcp/index.html:13` | google-workspace-mcp, reddit-mcp, mcp-surface (`mcp/index.html:69-82`) | **confirmed** |
| "114 offline tests" | `mcp/index.html:13` | **Measured:** 33 (google) + 27 (reddit) + 54 (mcp-surface) = 114. Server counts by `grep -cE '^\s*test\('` (all top level, none in loops); mcp-surface by `grep -cE '^\s*(it|test)\('` | **confirmed** |
| Test suites run "with no network access or credentials" | `resume/resume.tex:155`; `mcp/index.html:154` | each README's `npm test` line says "no network" (google `:167`, reddit `:137`, yahoo `:112`, supercell `:116`, chess `:99`) | **confirmed by READMEs** (suites not run: no `node_modules`) |
| "CI on Node 20 and 22" | `resume/resume.tex:155` | see table | **contradicted**, section 9 |
| "on Node 22 and 24" (both featured servers) | `mcp/index.html:154` | google 22/24, reddit 20/22/24 | **confirmed** (Reddit also runs 20) |
| "Snapshot-checked in CI" | `index.html:104`; `mcp/index.html:36-43`, `:155` | `npm run surface` step in every server's CI (google `ci.yml:50-51`, reddit `:43-44`, yahoo/supercell/chess `:43-44`), `--fail-on warn` (`package.json` `surface` script) | **confirmed** (all five, not only two) |
| Zod 3→4 upgrade passed "all 30 unit tests" | `mcp/index.html:62`; `mcp-surface@ea93b87:README.md:94` | google suite has 33 tests today (measured); the 30 refers to the time of the upgrade | **not checkable** (historical; shallow clone) |
| Zod 4 "added a 120-byte pattern to every email field" | `mcp/index.html:63` | README says "a long pattern" (`mcp-surface@ea93b87:README.md:94`) | **not checkable** |
| "past their 16 KB size budget" | `mcp/index.html:64` | default 16384 (`mcp-surface@ea93b87:README.md:68`, `:173`); README output shows ~16.6 KB (`:102`) | **confirmed by README** |
| "I kept Zod 3" | `mcp/index.html:65` | `"zod": "^3.25.76"` (`google-workspace-mcp@1f1fc45:package.json:25`) | **confirmed** |
| "282 MB of dependencies, cut to 45 MB"; "more than 80%" | `mcp/index.html:139-142` | `google-workspace-mcp@1f1fc45:README.md:206`, `CHANGELOG.md:38-41`; five `@googleapis/*` packages (`package.json:18-22`). 1 − 45/282 = 84.0% (**measured**, arithmetic) | **confirmed by README/CHANGELOG**; sizes not re-measured (no install) |
| Header injection, formula injection fixes with tests | `mcp/index.html:92-105` | `google-workspace-mcp@1f1fc45:README.md:42-46`, `CHANGELOG.md:12-18`; tests at `test/tools.test.js:256-290`, `:472` | **confirmed** |
| Path traversal, 200-means-no, typed ids (Reddit) | `mcp/index.html:107-122` | `reddit-mcp@0944987:README.md:48-71`, `CHANGELOG.md:12-30` | **confirmed by README/CHANGELOG** |
| Calendar invites sent by default | `mcp/index.html:124-129` | `google-workspace-mcp@1f1fc45:README.md:58-60`, test `test/tools.test.js:435-470` | **confirmed** |
| Loopback-only consent listener, state + PKCE (Google), 0600 token files | `mcp/index.html:131-137` | google `CHANGELOG.md:19-22`, `README.md:73`; reddit `CHANGELOG.md:15-17` | **confirmed by CHANGELOG** |
| 30-second timeout on every request | `mcp/index.html:157` | `REQUEST_TIMEOUT_MS = 30_000` in each `src/shared.js` (google `:14`, reddit `:15`) | **confirmed** |
| "ESLint, Dependabot, a changelog and a security policy" | `mcp/index.html:158` | `eslint.config.js`, `.github/dependabot.yml`, `CHANGELOG.md`, `SECURITY.md` present in both (measured: `ls`) | **confirmed** |
| "No open npm audit advisories at release" | `mcp/index.html:158` | google `CHANGELOG.md:25-26`, reddit `CHANGELOG.md:18-19` | **confirmed by CHANGELOG** |
| Read-only/destructive/idempotent annotations on every tool | `mcp/index.html:156` | **Measured:** 23/23 Google and 12/12 Reddit tools carry `annotations` in their committed `mcp-surface.json`; declared in code, e.g. `google-workspace-mcp@1f1fc45:src/calendar.js:87`, `:103`; `README.md:54`; `reddit-mcp@0944987:README.md:40-42`. No unit test asserts them (grep `Hint` in `test/tools.test.js`: none); the snapshot check covers them | **confirmed** |
| Figure "google-workspace 2.0.0 (279ms)" output | `mcp/index.html:45-57` | version 2.0.0 matches; timing and diff not reproducible here | **not checkable** |

- mcp-surface's own README lists only the two featured servers under "Used by"
  (`mcp-surface@ea93b87:README.md:209-219`), although all five run it in CI (scope difference).
- Google's privacy policy for the OAuth consent screen lives at
  https://brettadams0.github.io/google-workspace-mcp-policy/ (`google-workspace-mcp@1f1fc45:README.md:212`),
  a separate project-site repo on the same domain (not cloned, not loaded).

### 7.4 PromptPal

- **What:** prompt-template builder with its own small template language, linter and exports
  (`promptpal@82ffb5d:README.md:6-8`, `:15-35`).
- **Links:** live app https://prompt-pal-gules.vercel.app (`index.html:128`, `:135`;
  `promptpal@82ffb5d:README.md:8`); source https://github.com/brettadams0/PromptPal (`index.html:136`).
  No "Live demo" badge (external app, `index.html:131`). Screenshot `promptpal.webp` 1600×1000 (measured; `index.html:129`). Live app not
  loaded this session.
- **Dates:** none in any source; last commit 2026-10-16 (`promptpal@82ffb5d`, `git log -1`). Not on the résumé (`resume/resume.tex:140-165`).
- **Tech:** Next.js 16, React 19, TypeScript, Tailwind CSS 4, Radix UI, zod, Vitest, Playwright, Vercel
  (`promptpal@82ffb5d:README.md:124-125`; `promptpal@82ffb5d:prompt-builder/package.json:27-28`).

| Claim | Where stated | Check | Result |
|---|---|---|---|
| "Prompt templates in a small language with its own parser." | `index.html:132` | hand-written tokenizer and recursive parser (`README.md:17-20`) | **confirmed** |
| "Exports text or a ready-to-run Claude API call." | `index.html:132` | text, Markdown, Messages API call as JSON/cURL/Python/TypeScript (`README.md:27-28`) | **confirmed** |
| Chips TypeScript, Next.js | `index.html:133` | `README.md:124` | **confirmed** |
| Chip "E2E + a11y tests" | `index.html:133` | `prompt-builder/e2e/builder.spec.ts`, `e2e/a11y.spec.ts`; CI e2e job (`.github/workflows/ci.yml:42-55`) | **confirmed** |
| "142 Vitest tests" (README only) | `README.md:94` | **Measured:** 76 `it(`/`test(` calls across 8 unit files; `templates.test.ts` runs 6 tests per template via `describe.each(TEMPLATES)` over 12 templates plus 1 catalogue test: 69 + 72 + 1 = 142 | **confirmed** |
| "12 templates" (README only) | `README.md:29` | 12 `id:` entries in `TEMPLATES` (`src/lib/templates.ts:22-454`), plus a separate `blank` (`:495`) | **confirmed** |
| "zero WCAG 2.1 A/AA violations" (README only) | `README.md:96` | e2e axe spec exists; not run | **not checkable** |
| CI on Node 20 and 22 | `README.md:100` | `.github/workflows/ci.yml:23` | **confirmed** |

### 7.5 TechNest

- **What:** electronics store in framework-free PHP with a transactional checkout, order lifecycle and
  admin dashboard; rebuilt from university coursework (`technest@c7e590a:README.md:9-15`).
- **Links:** source https://github.com/brettadams0/TechNest (`index.html:141`, `:148`). No live demo.
  Screenshot `technest.webp` 1600×1000 (measured; `index.html:142`).
- **Dates:** 2.0.0 "2026-10" (`technest@c7e590a:CHANGELOG.md:3`). Not on the résumé.
- **Tech:** PHP 8.2-8.5, SQLite and MySQL 8, PHPStan level max, Docker (`technest@c7e590a:README.md:4-6`,
  `:42`; CI `technest@c7e590a:.github/workflows/ci.yml:43`, `:70-75`).

| Claim | Where stated | Check | Result |
|---|---|---|---|
| "A PHP store with no framework." | `index.html:145` | "no framework and no runtime dependency" (`README.md:9-12`); `composer.json:12-16` requires only PHP extensions | **confirmed** |
| "Checkout is one transaction, so the last unit can't sell twice." | `index.html:145` | `src/Checkout/CheckoutService.php:17`, `:47` (transaction), `:70` (`... AND stock >= ?`); `README.md:70-77` | **confirmed** |
| Chips PHP, MySQL | `index.html:146` | MySQL 8.4 CI job (`ci.yml:70-104`); SQLite is the default (`README.md:98-100`) | **confirmed** |
| Chip "200+ tests" | `index.html:146` | README says "250+ tests" (`README.md:189`). **Measured (static, PHPUnit not run: no `vendor/`):** 186 `public function test*` methods; 11 use data providers that yield 87 cases (25 from `everyPair` over 5 `OrderStatus` cases), so 186 − 11 + 87 = 262 test cases | **confirmed** (understated vs README) |
| "all 25 pairs are tested" (README only) | `README.md:78-81` | `tests/Unit/OrderStatusTest.php:33-40`; 5 cases in `src/Order/OrderStatus.php:20-24` | **confirmed** |

### 7.6 MazeFinder

- **What (code):** single 236-line C++11 file (`wc -l`, **measured**); a GA evolves 100-move genomes on a
  20x20 grid with fitness on a 4-worker thread pool (`mazefinder-geneticalgorithm@3184019:README.md:3-13`,
  `MazeFinder.cpp:13-17`, `:156`, `:172`).
- **Links:** source https://github.com/brettadams0/MazeFinder-GeneticAlgorithm (`index.html:155`). Listed
  in `ul.also` with `data-project="mazefinder"` (`index.html:155`). No demo yet.
- **Dates:** "Mar 2024 - Sep 2024" (`resume/resume.tex:159`). Repo clone has one commit, 2026-07-31
  (shallow). **Not checkable.**
- **Queued work:** `handoff/mazefinder-live-demo.md` (walls, bug fixes, WebAssembly demo at
  `/mazefinder/`, homepage card). Its homepage plan: move MazeFinder from `ul.also` to a card with
  `data-project="mazefinder"`, 1600×1000 screenshot `assets/img/mazefinder.webp`, chips such as
  "20x20 walled mazes" (only once walls exist) (`handoff/mazefinder-live-demo.md:144-152`); open
  question which project drops to `ul.also` because a fifth half-width card leaves a gap (`:150-152`).

| Claim | Where stated | Check | Result |
|---|---|---|---|
| "C++ genetic algorithm with fitness evaluation on a hand-rolled thread pool." | `index.html:155` | `MazeFinder.cpp:32` (ThreadPool), `:156`, `:172` | **confirmed** |
| "Parallelized genome fitness evaluation with a custom thread pool." | `resume/resume.tex:163` | same | **confirmed** |
| "C++ application for solving 20x20 mazes" / "Evolves paths through 20x20 mazes" | `resume/resume.tex:160`, `:162` | no walls | **contradicted**, section 9 |
| "dynamic mutation and crossover strategies" | `resume/resume.tex:162` | fixed strategies | **contradicted**, section 9 |
| "Stores mazes as 2D vectors so new maze configurations load without code changes" | `resume/resume.tex:164` | maze built in `main` | **contradicted**, section 9 |

### 7.7 NHL Arena Web Interface

- Site only, in `ul.also`: "Live refrigeration data and alarms for NHL arenas. Built at CIMCO; internal."
  (`index.html:154`), `data-project="nhl-arena"`, no link.
- Résumé covers the same work as a CIMCO 2024 bullet, not as a project (`resume/resume.tex:125-126`).
- No repo, screenshot or public material. **Not checkable** beyond the two sources.

### 7.8 Sift (not on the site or résumé)

- **What:** Android camera-roll triage and on-device photo grading app; "The app ships with no
  `INTERNET` permission at all." (`sift@e37fa56:README.md:1-8`; mirror `sift/README.md:9-14`).
- **Where:** source mirror `sift/` in this repo, excluded from the build (`_config.yml:31-32`; README
  `README.md:60-61`); upstream https://github.com/brettadams0/sift (`sift/README.md:3-7`).
- **Not on the homepage or résumé** (measured: `grep -ci sift` returns 0 for `index.html:1-193` and `resume/resume.tex:1-167`).
- **Mirror is stale:** mirror says build 0.2.0, versionCode 6 (`sift/README.md:24`); upstream says
  0.2.8, versionCode 14 (`sift@e37fa56:README.md:18`). Last mirror sync "Sync the Sift mirror to 0.2.0
  (#30)", 2026-08-16 (site commit `00edbca`). `diff -rq` shows many differing files and upstream-only
  `androidTest/`, `dist/`, `.github/` (**measured**).
- Facts common to both versions: 3.0 MB signed APK, installable across API 30-35 (`sift@e37fa56:README.md:18-20`,
  `sift/README.md:24-26`); "Of 48 tests, 46 pass and 2 are skipped by design" (`sift@e37fa56:README.md:35`,
  `sift/README.md:41`); pure-Kotlin imaging pipeline, no OpenCV (`sift/README.md:67-69`); 12MP grade
  budget 2.5 s missed, 5.4 s warm median on a 4-core JVM (`sift/README.md:209-218`; upstream reworded
  this section, `sift@e37fa56:README.md:253`).
- Upstream adds instrumented tests on API 30 and 35 (`sift@e37fa56:.github/workflows/ci.yml:111`,
  `README.md:163-167`); the mirror says approve-and-trash is "still unproven" (`sift/README.md:38`).
- Whether Sift appears on the site is a decision for Brett (PROMPT.md section 6 item 18).

### 7.9 Other project mentions

- "More on GitHub →" link to https://github.com/brettadams0?tab=repositories (`index.html:156`), no
  `data-project`.
- Removed projects: "The Bargain Hunt" (`bargain-hunt/` deleted in `2d7ad61`, 2026-10-19: "The daily
  brief is retired and the app went unused"); the blog and its posts (`0986a13`, `a5def91`). Neither is
  in any current source. Do not bring them back.

---

## 8. Disagreements between sources

Type: **scope** = both true in their own frame; **wording** = same fact, different words;
**contradiction** = code/README/handoff says otherwise (also in section 9); **format** = PDF/markup only.

| # | Topic | index.html says | resume.tex says | /mcp/ says | Project README/code says | Type | Question for Brett |
|---|---|---|---|---|---|---|---|
| D1 | MCP scope | "MCP servers for Google Workspace and Reddit"; "Two servers ... 23 tools ... 12 for Reddit" (`:102-103`) | "Five Node.js servers exposing 69 tools" incl. Yahoo Fantasy, Chess.com, Supercell (`:151-153`) | "2 servers · 35 tools · 3 repositories · 114 offline tests" (`:13`) | 23+12+10+15+9 = 69 (measured); all five run mcp-surface in CI; mcp-surface README "Used by" lists two (`mcp-surface@ea93b87:README.md:216-219`) | scope | Frame the site as 2 featured servers / 35 tools (now), 5 servers / 69 tools (résumé), or "two featured, five in all"? Should the other three be linked anywhere? |
| D2 | Node versions in CI | — | "CI on Node 20 and 22" (`:155`) | "on Node 22 and 24" (`:154`) | google 22.x/24.x only, engines `>=22` (`google-workspace-mcp@1f1fc45:.github/workflows/ci.yml:19`, `package.json:51`, `CHANGELOG.md:30-31`); other four 20.x/22.x/24.x (`ci.yml:19` each). "Node 20 and 22" matches neuronet-arena (`ci.yml:37`) and PromptPal (`ci.yml:23`) | contradiction (résumé) | Change the résumé to "Node 22 and 24" (true for all five) or "Node 20-24"? |
| D3 | Degree wording | "Honours Computer Science, Software Engineering Specialization (Co-op)" (`:176`) | "Honours Computer Science with Software Engineering Specialization Co-op" (`:87`) | — | — | wording | Which wording is canonical? |
| D4 | Education dates | "2022 – 2027" (`:177`) | "Sep 2022 - Present" (`:88`) | — | — | scope | Confirm the 2027 end (month?). Should the résumé say "Expected 2027"? |
| D5 | Availability | "Graduating 2027 · Open to full-time roles" (`:7`) | nothing | — | — | scope | Add availability to the résumé? Exact month/term? |
| D6 | Composer name | "Google Cloud Composer" (`:31`); chip "Cloud Composer" (`:36`) | "Cloud Composer" (`:106`) | — | — | wording | Which name in both? |
| D7 | Airflow name | chip "Airflow" (`:36`); skill "Apache Airflow" (`:167`) | "Apache Airflow" (`:97`, `:106`) | — | — | wording | Use "Apache Airflow" everywhere? |
| D8 | Skills lists | PHP, Linux, Next.js listed; VBA and Power Platform only as job chips (`:166-169`, `:49`, `:75`) | VBA, Power Platform listed; no PHP, Linux, Next.js; HTML/CSS under languages (`:96-98`) | — | PHP used in TechNest; Next.js in PromptPal | scope | Should the two lists match? Which groups? |
| D9 | Skill group names | Languages / Data / Tools / Web (`:166-169`) | Programming Languages / Data & Cloud / Technologies (`:96-98`) | — | — | wording | Pick one set? |
| D10 | Date format | "Jan – Aug 2025" etc. (`:28`, `:42`, `:55`, `:69`) | "Jan 2025 - Aug 2025" etc. (`:103`, `:113`, `:122`, `:132`) | — | — | wording | None needed unless Brett wants them identical. |
| D11 | Projects shown | neuronet-arena, MCP servers, mcp-surface, PromptPal, TechNest, NHL Arena, MazeFinder (`:85-155`) | mcp-surface, MCP servers, MazeFinder only (`:140-165`) | MCP servers + mcp-surface | — | scope | Add neuronet-arena (Brett's favourite), PromptPal or TechNest to the résumé? |
| D12 | Project dates | none | mcp-surface "Jul 2026 - Present" (`:141`), MCP "Jul 2026 - Present" (`:150`), MazeFinder "Mar 2024 - Sep 2024" (`:159`) | "October 2026" for the case study (`:11`) | CHANGELOG dates for neuronet-arena, mcp-surface, servers, TechNest (section 7) | scope | Show project dates on the site? |
| D13 | MazeFinder description | "C++ genetic algorithm with fitness evaluation on a hand-rolled thread pool." (`:155`) | "solving 20x20 mazes", "dynamic mutation and crossover strategies", "load without code changes" (`:160-164`) | — | no walls, fixed operators, maze built in code (`MazeFinder.cpp:117-135`, `:152`; `README.md:39-45`; `handoff/mazefinder-live-demo.md:42-43`, `:90-91`) | contradiction (résumé) | Reword the résumé now, or after the walls PR in the handoff lands? |
| D14 | TechNest test count | "200+ tests" (`:146`) | — | — | "250+ tests" (`technest@c7e590a:README.md:189`); 262 cases by static count | scope | Keep "200+" or move to "250+"? |
| D15 | Google unit-test count | — | — | "all 30 unit tests still passed" (`:62`) | 30 in `mcp-surface@ea93b87:README.md:94`; 33 in the suite today (measured) | scope (historical) | Keep "30" (true at the time) or drop the number? |
| D16 | MCP project title | "MCP servers for Google Workspace and Reddit" (`:102`); "AI assistant" (`:103`) | "MCP Servers for Claude Code"; "to AI assistants" (`:150-151`) | "AI assistant" (`:18`) | READMEs register with Claude Code and Claude Desktop | wording | Name Claude Code in the title, or "AI assistants"? |
| D17 | mcp-surface "checks" | chips only | "Runs 19 checks" (`:145`) | "19 checks" (`:80`) | 19 rules from 11 check modules (measured) | wording | "19 checks" or "19 rules"? (Both sources already agree.) |
| D18 | NHL arena work | listed as project "NHL Arena Web Interface", "internal" (`:154`) | a CIMCO bullet, not a project (`:125-126`) | — | no repo | scope | Keep it as a project entry? Any public detail allowed? |
| D19 | JSON-LD `alumniOf` | `alumniOf` University of Windsor (`_layouts/default.html:21`) while the page says "Graduating 2027" (`:7`) | "Sep 2022 - Present" (`:88`) | — | — | wording (semantic) | Keep `alumniOf`, or use a current-student property? |
| D20 | Résumé PDF vs tex | — | text identical (`resume/resume.tex:77-164`) | — | **Measured** (`pdftotext`, `pypdf` on `assets/resume/Resume_BrettAdams.pdf`): 22/22 bullets, all headings and skills match `resume.tex`; PDF CreationDate 2026-10-16 = commit `0986a13`. Differences in the PDF only: heading extracts as "NOTABLE PROȷECTS" (U+0237 dotless j in place of J); contact line extracts icon glyphs U+F08C/U+F092/U+F0C1; the email is plain text, not a link (3 link annotations: LinkedIn, GitHub, website) | format | The format is to be kept exactly. Is the "PROȷECTS" glyph (what a résumé parser reads) worth fixing in the .tex? |
| D21 | Sift | not shown | not shown | — | mirror 0.2.0 (`sift/README.md:24`) vs upstream 0.2.8 (`sift@e37fa56:README.md:18`); excluded from build (`_config.yml:31-32`) | scope | Should Sift appear? Resync or remove the mirror? |
| D22 | Token location in handoff | — | — | — | handoff says tokens are "at the top of `mcp-surface/styles.css`" (`handoff/mazefinder-live-demo.md:16-18`); CLAUDE.md says `assets/css/site.css` (`CLAUDE.md:12`). The values listed match both files; radius differs (12px `assets/css/site.css:21` vs 10px `mcp-surface/styles.css:19`) | wording | None for Brett; builder note. |

---

## 9. Claims contradicted by code, a README or a handoff file

Never restate these as fact. Each one is a résumé or site wording decision.

| # | Claim (source) | Evidence | Wording decision it implies |
|---|---|---|---|
| C1 | "Each server has a test suite that runs with no network access or credentials, and CI on **Node 20 and 22**." (`resume/resume.tex:155`) | No MCP server's CI runs exactly 20 and 22. google-workspace-mcp runs 22.x and 24.x and requires Node ≥22 (`google-workspace-mcp@1f1fc45:.github/workflows/ci.yml:19`, `package.json:51`, `CHANGELOG.md:30-31` "Node 20 reached end of life in April 2026"); reddit, yahoo-fantasy, supercell and chesscom run 20.x, 22.x and 24.x (`ci.yml:19` in each). | Résumé: "CI on Node 22 and 24" (true for all five) or "Node 20-24"; the offline-tests half of the bullet is confirmed. |
| C2 | "C++ application for solving **20x20 mazes**" and "Evolves paths through **20x20 mazes**" (`resume/resume.tex:160`, `:162`) | The grid is allocated as all zeroes and nothing writes a wall (`mazefinder-geneticalgorithm@3184019:MazeFinder.cpp:152`; the only maze access is the read at `:111`). README "Honest scope": "what the population actually solves is 'reach the bottom-right corner of an open field'" (`README.md:39-45`). Handoff: "No walls." (`handoff/mazefinder-live-demo.md:42-43`). | Résumé: "20x20 grid" / "open grid" until walls ship; site copy must not say "mazes" as solved. |
| C3 | "a genetic algorithm using **dynamic mutation and crossover strategies**" (`resume/resume.tex:162`) | Crossover is one random cut point (`MazeFinder.cpp:117-125`); mutation changes one random gene per child (`:128-135`); both are called the same way every generation (`:203-207`). Nothing changes rate or strategy over time (measured: read all 236 lines). | Résumé: "single-point crossover and mutation" or drop "dynamic". |
| C4 | "Stores mazes as 2D vectors **so new maze configurations load without code changes**." (`resume/resume.tex:164`) | The maze is a `vector<vector<int>>` built in `main` (`MazeFinder.cpp:152`); there is no loader or input file. The handoff says shipping mazes as files "also makes the résumé line ... literally true" (`handoff/mazefinder-live-demo.md:90-91`), i.e. it is not true today. | Résumé: drop the "load without code changes" half until the handoff's `mazes/*.txt` loader exists. |
| C5 | Sift mirror: "synced on release" (`sift/README.md:6-7`), "Current build: 0.2.0" (`:24`), "approve-and-trash still unproven" (`:38`), download link to `sift-0.2.0-release.apk` (`:27`) | Upstream is 0.2.8 (`sift@e37fa56:README.md:18`), has device tests for approve-and-trash (`:32`, `:163-167`), and its `dist/` holds only `sift-0.2.8-release.apk` (measured: `ls dist`), so the mirror's 0.2.0 link likely 404s (not loaded). | Not site copy today (excluded from build). If Sift appears, use upstream facts; decide whether to resync or delete the mirror. |
| C6 | Comment: per-URL reporting "cannot say whether anyone reached #education" and "These events answer that." (`assets/js/analytics-events.js:5-7`) | `#education` is a `div` (`index.html:172`); the observer only watches `section[id]` (`analytics-events.js:68`), so no event fires for it. | Analytics: add an `education` value explicitly if wanted (section 11); fix or keep the comment. |

---

## 10. Facts only Brett has

Each is needed by a sourced line or a likely redesign element. Write them as visible placeholders.

| Placeholder | Why it is needed |
|---|---|
| `[BRETT: Geotab impact numbers: DAGs maintained, tables/datasets migrated, rows/day, deadlines met?]` | All four Geotab bullets have no numbers (`index.html:31-34`, `resume/resume.tex:106-109`); the internship story (spec section 4) leads with this role. |
| `[BRETT: OK to name SalesOps, Revenue Operations and the device status / database dimension / accounts master datasets?]` | Internal names at `index.html:31-32`, `resume/resume.tex:106-107`; already public on both, but the redesign may surface them more. |
| `[BRETT: MTO numbers: dashboards built, business units, hours-to-minutes example, teams onboarded to the Data Broker Tool?]` and `[BRETT: OK to name the Data Broker Tool?]` | `index.html:45-47`, `resume/resume.tex:116-118` are qualitative. |
| `[BRETT: what does "CAG" stand for, and may it be expanded?]` | `index.html:60`, `resume/resume.tex:127`; a recruiter cannot parse it. |
| `[BRETT: CIMCO numbers: how many arenas/teams used the Flask apps, technicians using the thermal viewer?]` | `index.html:58`, `resume/resume.tex:125`; only "over 20 employees" (`:61`, `:128`) is quantified. |
| `[BRETT: CIMCO 2023: how many departments adopted the Power Platform tools; turnaround before/after?]` | "several departments" (`index.html:72`), "cutting turnaround time" (`:73`). |
| `[BRETT: the 2023 CIMCO title: "Digital Solutions" what (intern? co-op? summer student?)]` | Both sources give a team-like name with no role noun and no "Co-op" (`index.html:68`, `resume/resume.tex:132`). |
| `[BRETT: NHL Arena Web Interface: any screenshot or detail you may show publicly?]` | Listed as "internal" (`index.html:154`); a card or image needs permission. |
| `[BRETT: show the phone number on the site?]` | Phone is on the résumé (`resume/resume.tex:79`) and so in the public PDF, but not on the site. |
| `[BRETT: where are you based, and open to relocation or remote?]` | No source gives a personal location; only job cities (`index.html:28`, `:42`, `:55`, `:69`) and the university's "Windsor, ON" (`resume/resume.tex:89`). Recruiters filter on it. |
| `[BRETT: work authorization (Canada; US or elsewhere?)]` | Not in `index.html` or `resume/resume.tex`; all four roles are in Ontario (`resume/resume.tex:104`, `:114`, `:123`, `:133`). Relevant if the site targets roles outside Canada. |
| `[BRETT: graduation month 2027, earliest full-time start, Geotab internship end date, any further co-op term?]` | Site says only "Graduating 2027 · Open to full-time roles" (`index.html:7`); Geotab is "Present" (`index.html:28`, `resume/resume.tex:103`). |
| `[BRETT: target roles wording: data engineering, data analytics, software engineering?]` | Only PROMPT.md's orientation summary names them; the fact sources say only "Open to full-time roles" (`index.html:7`) and "data & software engineering" (`index.html:8`, `_config.yml:2`). |
| `[BRETT: headshot (yes/no; file)?]` | None in `assets/img/` (measured `ls`); the hero has no image (`index.html:4-19`). |
| `[BRETT: custom domain (yes/no; which)?]` | None (no `CNAME`). A domain change also moves https://brettadams0.github.io/google-workspace-mcp-policy/, the policy URL on the Google OAuth consent screen (`google-workspace-mcp@1f1fc45:README.md:212`), and the absolute links in the synced demo (`neuronet-arena/index.html:12-13`, `:28`, `:147`). |
| `[BRETT: canonical degree wording and education dates]` | D3, D4: `index.html:176-177` vs `resume/resume.tex:87-88`. |
| `[BRETT: project dates for neuronet-arena, PromptPal, TechNest if the redesign shows dates]` | Only the résumé's three projects have dates (`resume/resume.tex:141`, `:150`, `:159`); others have only CHANGELOG release dates (section 7). |
| `[BRETT: MazeFinder dates Mar-Sep 2024 confirmed?]` | `resume/resume.tex:159`; the clone has one 2026-07-31 commit, so nothing confirms 2024. |

---

## 11. Analytics facts

| Fact | Value | Source |
|---|---|---|
| GA4 measurement ID | `G-Z3KW627PQQ` | `_config.yml:11` |
| Old ID, never restore | `G-EBYB5MK388` ("belongs to someone else's property") | `_config.yml:9-10` |
| Current property since | `106860c` "Point analytics at a property we actually own (#36)", 2026-09-28; the ID was changed in `_config.yml:9-11` | site commit `106860c` |
| Loader | gtag.js async + `gtag('config', ...)`, then `analytics-events.js` with `defer`; nothing emitted if `site.google_analytics` is unset | `_includes/analytics.html:1-12` |
| Pages that load GA | every page using `_layouts/default.html`: `/`, `/mcp/`, `/404.html` | `_layouts/default.html:35` |
| Pages with no GA | `/neuronet-arena/`, `/mcp-surface/` (standalone HTML, no front matter, no gtag) | `neuronet-arena/index.html:1-23`, `mcp-surface/index.html:1-21` |
| DNT | `analytics-events.js` returns before doing anything if `navigator.doNotTrack === '1' \|\| window.doNotTrack === '1'`. `analytics.html` has no DNT check, so gtag's own `config` page view still runs for DNT visitors; only the custom events are suppressed. | `assets/js/analytics-events.js:12`; `_includes/analytics.html:3-8` |
| Send guard | every send checks `typeof window.gtag === 'function'` and wraps in try/catch | `assets/js/analytics-events.js:14-19` |
| Custom definitions to register | `section_id`, `project_name`, `method`, `percent`, `seconds` (README); `link_url` is also sent but not in the README list | `README.md:55-56`; `assets/js/analytics-events.js:103` |

**Events**

| Event | Trigger | Params | Lines |
|---|---|---|---|
| `section_view` | a `section[id]` is "at least half visible" (≥50% of it, or it fills ≥half the viewport) continuously for 1 s; once per section per page view | `section_id` | `assets/js/analytics-events.js:31-47`, `:49-73` |
| `section_dwell` | on the first `visibilitychange` to hidden or `pagehide`, one event per section with total visible time ≥2 s (rounded seconds); flushed once | `section_id`, `seconds` | `:76-91` |
| `project_click` | click on any `<a>` inside an element with `[data-project]` | `project_name` (attribute value), `link_url` | `:95-105` |
| `resume_download` | click on an `<a>` whose raw `href` attribute matches `/Resume_BrettAdams\.pdf$/` (a query string or fragment breaks it) | none | `:107-108` |
| `contact_click` | click on an `<a>` with `data-contact` | `method` (attribute value) | `:110-111` |
| `scroll_depth` | 25, 50, 75, 100 % of `scrollHeight` reached, rAF-throttled scroll listener; also measured once on load | `percent` | `:114-137` |

**Homepage `section[id]` today (the only `section_view`/`section_dwell` values)**

| `section_id` | Element | Line |
|---|---|---|
| `experience` | `<section id="experience" class="section">` | `index.html:21` |
| `projects` | `<section id="projects" class="section">` | `index.html:81` |
| `skills` | `<section id="skills" class="section">` (also contains Education) | `index.html:161` |
| `contact` | `<section id="contact" class="section contact">` | `index.html:183` |

- Hero: `<section class="hero" aria-labelledby="hero-name">` has **no id**, so it never fires (`index.html:4`).
- Education: `<div id="education">` inside `#skills`, so it never fires (`index.html:172`).
- `/mcp/` has no `section[id]` (`mcp/index.html:9-161`); `/404.html` has `section.not-found` with no id
  (`404.html:7`). Only clicks and `scroll_depth` fire there.

**Attribute carriers today**

| Attribute | Elements | Lines |
|---|---|---|
| `data-project` | `neuronet-arena`, `mcp-servers`, `mcp-surface`, `promptpal`, `technest` (articles); `nhl-arena`, `mazefinder` (`li` in `ul.also`) | `index.html:85`, `:98`, `:112`, `:127`, `:140`, `:154`, `:155` |
| — | `nhl-arena` `li` has no `<a>`, so `project_click` can never fire for it | `index.html:154` |
| `data-contact` | hero `github`, `linkedin`, `email`; contact `email`, `linkedin`, `github` | `index.html:13-15`, `:186`, `:188-189` |
| — | footer GitHub/LinkedIn/Email links have **no** `data-contact` | `_layouts/default.html:61-63` |
| Résumé link (`resume_download`) | hero button, nav "Résumé", contact button; all `{{ '/assets/resume/Resume_BrettAdams.pdf' \| relative_url }}` | `index.html:12`, `_layouts/default.html:48`, `index.html:190` |
| PDF path | `assets/resume/Resume_BrettAdams.pdf` (37,452 B, 1 page, letter) | `README.md:22`; `pdfinfo` (**measured**) |

---

## 12. Stack and constraint facts

| Fact | Value | Source |
|---|---|---|
| Generator | Jekyll on GitHub Pages; `markdown: kramdown` | `_config.yml:12`; `README.md:8` |
| Plugins | `jekyll-seo-tag`, `jekyll-sitemap` only | `_config.yml:13-15` |
| No Gemfile | on purpose: "adding one changes how GitHub Pages resolves dependencies" (measured: no `Gemfile`, `Gemfile.lock` or root `package.json`) | `README.md:34`; `.github/workflows/ci.yml:22-24` |
| Local build | `gem install jekyll jekyll-seo-tag jekyll-sitemap`; `jekyll serve` | `README.md:29-32` |
| CI triggers / permissions | push and PR to `main`; `contents: read`; the live site is published by the built-in pages-build-deployment flow, CI only builds a throwaway copy | `.github/workflows/ci.yml:3-13`, `:9-11` |
| CI steps | `actions/checkout@v4` → `actions/jekyll-build-pages@v1` (source `./`, destination `./_site`) → `actions/setup-node@v4` Node `20.x` → `node .github/scripts/check-links.mjs` | `.github/workflows/ci.yml:19-36` |
| Link checker | reads `_site/**/*.html`; checks every `href`/`src` attribute; skips anything with a scheme, `//` or a bare `#`; strips `?` and `#`; a directory resolves to `index.html`, an extensionless path to `.html`; exits 1 on any missing target. It does not check CSS `url()`, JS imports, or that `#fragment` ids exist. No dependencies, no network. | `.github/scripts/check-links.mjs:1-87`, `:27`, `:29-30`, `:34`, `:41-48`, `:79-85` |
| Build excludes | `README.md`, `CLAUDE.md`, `resume`, `handoff`, `sift` | `_config.yml:26-32` |
| Social image default | `/assets/img/social-card.png` (1200×630, 211,878 B, measured) for every page | `_config.yml:20-25` |
| SEO titles (local `_site`, built 03:49 by another agent, not rebuilt for this sheet) | `/`: "Brett Adams \| Data & Software Engineering"; `/mcp/`: "MCP servers, and the tool that checks them \| Brett Adams"; `/404.html`: "Page not found \| Brett Adams" (404 reuses the site description) | `_site/*.html` |
| Sitemap (same local build) | `/mcp/`, `/`, the résumé PDF, `/mcp-surface/`, `/neuronet-arena/`; `/404.html` excluded by `sitemap: false` | `_site/sitemap.xml`; `404.html:5` |
| robots.txt | allow all; sitemap URL | `robots.txt:1-4` |
| Fonts | Google Fonts: IBM Plex Mono 400/500/600 + IBM Plex Sans 400/500/600/700, `display=swap`, render-blocking stylesheet | `_layouts/default.html:31-33` |
| Tokens | `:root` in `assets/css/site.css:4-25`: `--bg #0b0f14`, `--bg-2 #0e141b`, `--surface #131a22`, `--surface-2 #18212b`, `--border #222c38`, `--border-strong #2f3b4a`, `--text #e7ecf2`, `--soft #c4ccd6`, `--muted #8695a7`, `--faint #5a6878`, `--amber #ffb454`, `--amber-hi #ffc97f`, `--teal #4fd6c4`, `--red #ef6a5f`, `--good #3ccf6e`, `--radius 12px` | `assets/css/site.css:4-25` |
| Demo token drift | demos use `--radius: 10px` (`mcp-surface/styles.css:19`; neuronet-arena same); site-only `--bg-2`, `--soft`, `--amber-hi`; mcp-surface-only `--term` (= `#0e141b`); neuronet-only `--blue`, `--series-1`, `--series-2`, `--bad` | `:root` blocks of the three stylesheets (measured) |
| Terminal touches | `> ` before the brand (`assets/css/site.css:121-124`), `// ` kicker (`:278-279`), `## ` section titles (`:323-324`) | `assets/css/site.css` |
| Favicon | SVG "BA" on `#15181c` with a `#67d2e6` bar (not site tokens) | `assets/img/favicon.svg:2-4` |
| Images | `<img>` width/height match the files (measured): neuronet-arena 1280×800, mcp-servers 2200×1376, mcp-surface 2200×1376, promptpal 1600×1000, technest 1600×1000; all `loading="lazy"` | `index.html:87`, `:100`, `:114`, `:129`, `:142` |
| neuronet-arena sync rule | verbatim copy of upstream `web/` via `rsync -a --delete` excluding `node_modules`, `test`, `package.json`, `package-lock.json`, `tsconfig.json`; edit upstream, not here | `README.md:63-73`; `CLAUDE.md:13` |
| neuronet-arena local difference | header "How it was built" link removed (post taken down). **Measured** with `diff -rq` against `neuronet-arena@07bc91f:web/`: only `index.html` differs, by that one line (`web/index.html:31`); upstream-only files are the excluded ones | `README.md:75-76` |
| mcp-surface `checks.js` | generated from the npm package with esbuild, "Generated, do not edit"; header says **mcp-surface 0.3.0**; `Buffer.byteLength` shimmed with `TextEncoder`; rebuild steps in README; update the rule table in `app.js` if the package README's table changes | `mcp-surface/checks.js:1-5`; `README.md:78-92`; `mcp-surface/app.js:8-29` |
| Third-party licences | `mcp-surface/THIRD_PARTY_LICENSES.txt`, linked from the playground footer | `mcp-surface/index.html:87` |
| Demo shells | both demos have a `header.top` with a back link "brettadams0.github.io" (neuronet absolute URL, mcp-surface `/`) and "Source on GitHub"; footers "Built by Brett Adams" | `neuronet-arena/index.html:26-33`, `:146-148`; `mcp-surface/index.html:24-32`, `:86-88` |
| Reserved path | `/google-workspace-mcp-policy/` is served by another repo's project site on this domain; do not create that path here | `google-workspace-mcp@1f1fc45:README.md:212` |
| Tracked files | 141 tracked files, 92 of them under `sift/` (measured: `git ls-files \| wc -l`, `git ls-files sift \| wc -l`; `sift/` excluded at `_config.yml:31-32`) | — |
