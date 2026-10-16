# Outstanding tasks

Things that still need doing by hand, mostly because GitHub exposes no API for
them. Not published to the site (see `exclude` in `_config.yml`).

---

## 1. Security — rotate the TechNest database credential

`TechNest/includes/config.php` used to carry a database username and password
as the fallback values behind `getenv()`. Those fallbacks have since been
removed and the app now fails with a message naming the missing environment
variables — but the values are still in git history, so treat the credential
as compromised.

- [ ] **Rotate/delete the database user** on whatever host it belongs to.
      This is the step that actually matters; nothing else undoes the exposure.
- [x] ~~Replace the fallbacks with empty defaults so the app fails loudly~~
- [ ] Decide about history: the old values remain reachable in past commits.
      Either rewrite history (`git filter-repo --replace-text`) and force-push,
      or accept it as burned once the credential is dead.

Do this one first — it is the only item with a real-world consequence.

## 2. Pinned repositories

github.com/brettadams0 → **Customize your pins**. Replace all six:

1. `mcp-surface` — published npm CLI + GitHub Action, CI, tests
2. `google-workspace-mcp` — 23 tools, least-privilege OAuth writeup
3. `reddit-mcp` — the write-capable OAuth2 server
4. `TechNest` — the complete full-stack app
5. `neuronet-arena` — from-scratch neural net + genetic algorithm
6. `brettadams0.github.io` — this site

Currently pinned, all to be removed: `CipherMaster`, `java-drawing-app`,
`MazeFinder-GeneticAlgorithm`, `Simple-Python-Blockchain`, `Spotify-Web-API`.

Note `PromptPal` is deliberately **not** in the list: its builder route does not
exist yet, so it does not survive a reviewer clicking through. Worth pinning
once that ships.

## 3. Profile bio

Settings → Public profile. The current bio predates `mcp-surface`:

> Building MCP tooling — `mcp-surface` on npm + 5 MCP servers. Data Analyst
> Intern @ Geotab (BigQuery, Airflow, SQL). CS/SE @ UWindsor.

## 4. Repo metadata

- [ ] `mcp-surface` has no `homepage` set — point it at
      https://www.npmjs.com/package/mcp-surface
- [ ] The five server repos: set **Website** to https://brettadams0.github.io/mcp/
      and add the topics `mcp`, `model-context-protocol`, `mcp-server`, `claude`.
- [ ] On all six MCP repos, Settings → Code security: turn on **Dependabot
      security updates** (the committed `dependabot.yml` only schedules version
      updates) and **Private vulnerability reporting**, which each
      `SECURITY.md` would then be able to point at.

There is no REST endpoint exposed for this; it is Settings → the "Website"
field on the repo page, or the gear next to **About**.

## 4a. The October 2026 MCP hardening PRs

One draft PR per repo, all on branch `ccr-6c6cdde7-w9q3h2`. Merge the six
code PRs first and this site's PR last, so the `/mcp/` page never describes
work that isn't on `main` yet.

- [ ] Before merging each server, run its live check once — CI has no
      credentials, so nothing below has touched a real account:
      `npm run smoke-test` (Google; also create one calendar event with an
      attendee to see the invite arrive), `npm run check-auth` (Reddit, Yahoo),
      and one call per game for Supercell.
- [ ] If `credentials/coc.json` exists on the machine, rename it to
      `credentials/clashofclans.json` — the server now reads the documented name.
- [ ] Re-run `npm run authorize` for Google only if the next call fails: PKCE
      changes the consent flow, not the stored token.
- [ ] Cut `mcp-surface` 0.3.1 so npm carries the audit fixes and the corrected
      client version: `npm version patch && git push --follow-tags`, then publish
      a GitHub release — `release.yml` takes it from there.

## 5. TechNest screenshots

The only project image still missing. It could not be captured in CI: the
seeded products reference `images.unsplash.com`, which was blocked in that
environment, so every product tile rendered broken. It works on any machine
with normal internet access.

Setup that is known to work (schema, seed, PHP and all pages verified):

```sh
mysql -u root -p -e "CREATE DATABASE technest_demo CHARACTER SET utf8mb4;
  CREATE USER 'technest'@'localhost' IDENTIFIED BY 'localdemo';
  GRANT ALL ON technest_demo.* TO 'technest'@'localhost';"
mysql -u technest -plocaldemo technest_demo < db_schema.sql
mysql -u technest -plocaldemo technest_demo < db_seed.sql

cd public_html
DB_NAME=technest_demo DB_USER=technest DB_PASS=localdemo DB_HOST=localhost \
  php -S 127.0.0.1:8088 -t .
```

Then screenshot `index.php`, `catalog.php`, `product.php?id=1` and `cart.php`
at a 1440px window, save as WebP into `dist/img/`, and add a card to the
projects section of `index.html`.

## 6. Archiving old coursework repos (optional)

Keeps them from being the first thing a reviewer sees without deleting the
history. **Only after** the README-fix PRs have merged — archived repos are
read-only, so archiving first would permanently lock in the broken instructions.

`AES-Image-Encryptor-Decryptor`, `DevEnvCLI`, `SecurePass-Generator`,
`JavaWeatherWizard`, `JavaFX-HTML-Editor`, `java-drawing-app`, `SudokuMaster`,
`TerminalWealthTracker`, `recipe-app`, `crypto-dashboard`, `TaskManagerPro`.

## 7. Longer-term

- [ ] Land 1–2 merged external OSS PRs (Airflow, dbt, or the MCP org — docs,
      a test, or a small scoped fix). This is the most visible thing missing
      for a senior-trajectory read.
- [ ] Build out PromptPal's builder route so the repo matches its own pitch.
- [ ] A blog post connecting the MCP work to the Geotab data-engineering side —
      an MCP server over BigQuery would unify the two halves of the profile.
