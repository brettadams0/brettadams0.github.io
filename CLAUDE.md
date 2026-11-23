# Notes for working on this site (brettadams.ca)

- Stack: Astro (static output) built and deployed by `.github/workflows/site.yml` to GitHub Pages.
  Pages live in `src/pages/`; content (projects, jobs, skills) is data in `src/data/site.ts`;
  design tokens are in `src/styles/global.css`. Everything in `public/` is copied verbatim.
- The live demos (`public/signal-lab/`, `public/mcp-surface/`, `public/neuronet-arena/`,
  `public/border-waits/`, `public/mazefinder/`) are built or synced from their own repositories;
  see README before editing them.
- Brett's favourite thing on the site is the neuronet-arena live demo. More interactive, in-browser
  showcases of real projects are welcome. Build them from the project's actual code, not a mock-up.
- Each project appears once on the homepage; a demo starts only on its own page.
- No blog posts (removed as AI-sounding). Copy stays short and factual: one line per project, facts as
  chips. Every claim needs a repo or a job behind it.
- Run `npm test` (Playwright) before shipping. Local builds need Node ≥ 22.23.4.
