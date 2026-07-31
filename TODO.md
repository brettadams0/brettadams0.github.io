# Outstanding tasks

Things that still need doing by hand, mostly because GitHub exposes no API for
them. Not published to the site (see `exclude` in `_config.yml`).

---

## 1. Security — rotate the TechNest database credential

`TechNest/includes/config.php` has a database username and password committed
as the fallback values behind `getenv()`. The repo is public, so treat that
credential as compromised regardless of whether anything still uses it.

- [ ] Rotate/delete the database user on whatever host it belongs to
- [ ] Replace the fallbacks with empty defaults so the app fails loudly instead
      of silently trying a real credential
- [ ] Decide about history: removing it from the current file does **not**
      remove it from git history. Either rewrite history (`git filter-repo
      --replace-text`) and force-push, or accept it as burned once the
      credential is dead. Rotating is what actually matters.

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
