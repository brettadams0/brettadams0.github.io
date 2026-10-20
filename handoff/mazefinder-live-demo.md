# Handoff: give MazeFinder real walls and make it a live demo

Brett asked for two things: add walls to MazeFinder, and turn it into a live in-browser demo on
brettadams0.github.io like neuronet-arena. This file has everything a fresh session needs. Delete it
in the PR that adds the demo.

## Ground rules (from Brett, and in `CLAUDE.md`)

- Build the demo from MazeFinder's actual code, not a JavaScript imitation of it.
- **Nothing live on the homepage.** The visitor clicks through to a demo's own page before anything
  runs. The homepage card is a still screenshot and a link. Once on its page, the demo may start on
  its own, as neuronet-arena does.
- Copy stays short and factual: one line per project, facts as chips. No blog-style prose. Brett has
  removed AI-sounding writing before.
- Visual style: dark only, IBM Plex Sans/Mono, the tokens at the top of `mcp-surface/styles.css`
  (`--bg #0b0f14`, `--surface #131a22`, `--border #222c38`, `--amber #ffb454`, `--teal #4fd6c4`,
  `--red #ef6a5f`, `--good #3ccf6e`).

## Repos and access

| Repo | What changes |
|---|---|
| `brettadams0/MazeFinder-GeneticAlgorithm` | Walls, bug fixes, core/native split, the WebAssembly build, tests and CI |
| `brettadams0/brettadams0.github.io` | The `/mazefinder/` page, a homepage card, README |

The session needs **push** access to both. Open one PR in each, as drafts. Do the MazeFinder PR first,
because the site copies its build output.

## What MazeFinder is today

A single file, `MazeFinder.cpp` (236 lines, C++11 with pthreads).

- **Grid:** 20x20, `Maze = vector<vector<int>>`.
- **Genome:** 100 moves over `{UP, DOWN, LEFT, RIGHT, STAND}`.
- **GA:** population 100, 1000 generations, keeps the best half, refills with crossover and mutation.
- **Thread pool:** fitness runs on a hand-rolled pool of 4 workers.
- **Output:** prints everything to stdout.

Problems to fix along the way. Read the code to confirm each one before changing it.

1. **No walls.** `main` allocates the maze as all zeroes and nothing ever writes a 1, and the README's
   "Honest scope" section says so. This is the main ask.
2. **The wall check is wrong anyway.** `evaluateFitness` moves onto the cell first, then checks
   `maze[y][x] == 1` and `break`s. The walker ends up standing inside the wall and stops reading its
   genome. Instead, a move into a wall or off the edge should be blocked (stay put), and the walk should
   continue.
3. **Manhattan fitness doesn't work with walls.** Distance to the corner rewards dead ends that happen
   to be close as the crow flies. Precompute a BFS distance from the goal to every open cell once per
   maze, and score the cell where the walker ends up.
   - Lower is better.
   - Give a bonus or tie-break for reaching the goal in fewer moves, so the GA keeps shortening a path
     that already works.
4. **Selection is broken (undefined behaviour).**
   - The comparator in `std::partial_sort` looks fitness up with `&a - population.data()`. `partial_sort`
     moves elements around, so those indices go stale.
   - libstdc++'s heap code also compares against a temporary moved out of the array, so the pointer
     difference can index outside `fitness`.
   - Fix: sort a vector of indices by fitness, then build the next population from it.
5. **RNG isn't reproducible.**
   - Every function seeds its own `mt19937` from `random_device`.
   - `std::uniform_int_distribution` also returns different numbers under libstdc++ (g++) and libc++
     (Emscripten).
   - Fix: use one seeded generator in the core, and map raw `mt19937` output to a range yourself, or use
     a small generator such as splitmix64. The same seed then gives the same run natively and in the
     browser, so the demo can be tested against the native build.
6. **The genome may be too short.** A walled 20x20 maze can need well over 100 moves. Check the BFS
   shortest-path length for each maze you ship. Keep `GENOME_LENGTH` comfortably above it (about 1.5x),
   or derive it per maze.

## Plan

### 1. MazeFinder repo

- **Split the file:**
  - `src/maze.{h,cpp}`: load, validate, BFS distances.
  - `src/ga.{h,cpp}`: genome, walk, fitness, selection, crossover, mutation, seeded RNG. No threads, no
    I/O.
  - `src/main.cpp`: native CLI. Keeps the thread pool for fitness evaluation.
  - Make output quiet by default: one line per N generations with the best fitness. Add `--verbose` for
    the old dump.
- **Mazes as data:**
  - Put them in `mazes/*.txt`, one row per line: `#` is a wall, `.` is open.
  - Start is the top-left corner, goal is the bottom-right.
  - Ship 3 or 4: an open field (the old behaviour, a useful baseline), a few-obstacles maze, and one or
    two proper mazes.
  - Generate the proper mazes once with a seeded recursive backtracker and commit the text. Don't
    generate them at runtime.
  - The loader rejects a maze with no path from start to goal (use BFS).
  - Shipping mazes as files also makes the résumé line "Stores mazes as 2D vectors so new maze
    configurations load without code changes" literally true.
- **Fix bugs 2 to 6 above.**
- **Tests:** a small test binary (plain asserts are fine) checking:
  - The loader rejects an unsolvable maze.
  - The BFS distances are correct on a hand-made maze.
  - A known genome walks to the expected cell and fitness.
  - The same seed gives the same best fitness after N generations.
- **CI:** a GitHub Actions workflow that builds with g++ (`-Wall -Wextra -Werror`), runs the tests, and
  runs the CLI on each maze for a few generations.
- **WebAssembly build:**
  - Add a C API in `src/wasm_api.cpp`, for example `mf_create(maze_text, seed)`, `mf_step()`,
    `mf_generation()`, `mf_best_fitness()`, `mf_reached_goal()`, and accessors for the maze and for the
    best and all walked paths as typed-array views.
  - Compile `maze.cpp` + `ga.cpp` + `wasm_api.cpp` with Emscripten:
    `emcc -O2 -sMODULARIZE -sEXPORT_ES6 -sENVIRONMENT=web -sALLOW_MEMORY_GROWTH`, with
    `-sEXPORTED_FUNCTIONS` and `-sEXPORTED_RUNTIME_METHODS` as needed.
  - Install Emscripten through emsdk if it isn't there:
    `git clone https://github.com/emscripten-core/emsdk && ./emsdk install latest && ./emsdk activate latest`.
  - **No pthreads in the browser build.** WASM threads need `SharedArrayBuffer`, which needs COOP/COEP
    headers, and GitHub Pages can't send them. The browser evaluates fitness on one thread. That's
    trivial work at this size: 100 genomes x about 150 moves.
  - Say this on the page in one line, for example "Thread pool: native build only", rather than
    implying otherwise.
  - Put the output and a `Makefile`/script target in `web/`, and commit the built `.js` and `.wasm`.
    The site has no build step, so it copies these files.
  - Add a determinism test: the same seed and maze give the same best fitness after N generations in
    native and in Node running the WASM build.
- **README:**
  - Replace "Honest scope" with what's true now.
  - Document the maze format, build commands, the WASM build and the tests.
  - Keep it short.

### 2. Site repo: the `/mazefinder/` demo

- **Files:** model it on `mcp-surface/`: a standalone `index.html`, `styles.css`, `app.js`, and the
  copied `web/` build output (for example in `mazefinder/wasm/`).
  - Copy the `<head>` pattern from `mcp-surface/index.html`: title `MazeFinder · live demo`,
    description, og tags, `og.png`, favicon `/assets/img/favicon.svg`, Plex fonts.
  - Include the header with a back link and Source on GitHub, matching the other demos.
- **Canvas:**
  - Walls in a border colour, open cells on `--bg`, start teal, goal amber.
  - Every genome's path drawn faintly, the best path in amber.
  - When the best genome reaches the goal, mark it.
- **Controls:**
  - Run/Pause, Step (one generation), Reset (new seed), a speed control (generations per frame), and a
    maze picker.
  - Optional, if the core exposes them: mutation rate and population size.
  - It may start running on load, like neuronet-arena.
  - Pause when the tab is hidden.
  - Respect `prefers-reduced-motion`: start paused.
- **Stats:** generation, best fitness, BFS shortest-path length versus the best path found, and a small
  best-fitness-per-generation line chart.
- **Mobile:** works at 390px wide with no horizontal scroll. The canvas scales to width.
- **Homepage:**
  - Add a third card in `#demos` in `index.html`. Use the same markup as the two existing cards, with
    `data-project="mazefinder"`, a 1600x1000 screenshot at `assets/img/mazefinder.webp`, one factual
    line and three chips (for example `20x20 walled mazes`, `C++ → WebAssembly`, `Seeded, reproducible`).
  - Remove MazeFinder from the `ul.also` list.
  - The `.demos` grid is two columns, so check that three cards still look right at 1440px and 390px,
    and adjust the grid if not.
- **README:** add `mazefinder/` to the layout table, and a short section on how to re-sync it from the
  MazeFinder repo's `web/` output, as done for `neuronet-arena/`.
- **Résumé (optional):**
  - The MazeFinder bullets in `resume/resume.tex` can now say walled mazes. Run any change past Brett
    first.
  - Recompile with XeLaTeX (`apt-get install texlive-xetex texlive-fonts-recommended`), check it is
    still one page, and copy the PDF over `assets/resume/Resume_BrettAdams.pdf`.

## Verify before pushing

- **MazeFinder:** CI green, tests pass, and native and WASM agree for the same seed.
- **Site build:**
  `gem install jekyll jekyll-seo-tag jekyll-sitemap && jekyll build && node .github/scripts/check-links.mjs`.
  It must pass.
- **Browser:** Playwright (Chromium is preinstalled; don't run `playwright install`) at 1440 and 390px.
  Check:
  - No console errors and no horizontal overflow.
  - Nothing MazeFinder-related loads or runs on the homepage.
  - The page evolves, and the best fitness falls over generations.
  - On a proper maze, the best path reaches the goal within a reasonable number of generations. If it
    doesn't, tune the GA in the MazeFinder repo, not on the page.
- **Analytics:** clicking the homepage card still sends `project_click` (via `data-project`).
- **Screenshots:** send them to Brett with the PR links.
