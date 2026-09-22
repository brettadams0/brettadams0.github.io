# Site analytics — current state, and the plan to make it answer real questions

Not published to the site (see `exclude` in `_config.yml`).

---

## 1. What is already here

Google Analytics 4, property `G-EBYB5MK388`, is **already installed and already
collecting**. The snippet appears in two places:

| File | Covers |
|---|---|
| `index.html` (lines ~84–92) | The landing page |
| `_layouts/default.html` (lines ~50–57) | `/blog/` and every post |

`bargain-hunt/index.html` has no analytics. That is correct — it is
`noindex, nofollow`, a private tool, not a page anyone is meant to find.

So the answer to "can I track viewers" is: you already are. Log in at
[analytics.google.com](https://analytics.google.com) and the following are
waiting for you with no code changes at all:

- **Reports → Realtime** — who is on the site right now, and what they are reading.
- **Reports → Acquisition → Traffic acquisition** — where they came from.
  LinkedIn, Google, github.com, direct. This is the "where do viewers come
  from" question, fully answered today.
- **Reports → Engagement → Pages and screens** — views and **average engagement
  time** per URL. This is the "how long are they spending" question, answered
  today at page granularity.
- **Admin → Data Streams → your stream → Enhanced measurement** — if this is on
  (it is on by default), GA is *already* recording résumé PDF downloads as
  `file_download` and clicks out to GitHub/LinkedIn/npm as `click`.

**Before writing any code, check two things in the GA admin:** that the property
is actually receiving data (Realtime will show it), and that Enhanced
measurement is enabled. If data has never flowed, the problem is configuration,
not instrumentation, and none of the work below will help.

## 2. Why that still will not answer the question you actually asked

The question was "which pages do I decommission." This site has five URLs:

```
/                      ← experience + projects + skills + education + blog teaser + contact
/blog/
/blog/2026/07/26/blog-is-live/
/blog/2026/07/27/full-access-one-exception/
/blog/2026/07/28/connected-is-not-a-health-check/
```

Effectively everything you might cut — the Education section, the TechNest
card, the skills grid — lives inside one URL. GA4 will report that `/` got N
views at an average 1m20s and will tell you *nothing* about whether a single
person scrolled as far as `#education` or ever looked at TechNest.

There is a second problem specific to your audience. This is a portfolio read by
recruiters and, more to the point, by engineers. A meaningful slice of that
audience runs uBlock Origin or Firefox ETP, both of which block
`googletagmanager.com` outright. GA4 undercounts a technical audience
materially — plan on 10–40%, unknowable precisely. It undercounts
*consistently*, so trends and ratios stay usable; absolute headcounts do not.

So the gap is not "you need analytics." It is that page-level analytics is the
wrong granularity for a one-page site, and you need **section-level and
click-level events** layered on top.

## 3. What I would build

Keep GA4. It is already installed, already accumulating history you cannot
backfill, free, and the only free option with real custom-event depth. Do not
pay for Plausible or Fathom for a personal portfolio, and do not self-host
Umami — standing up a server and a database to measure a static site defeats
the reason the site is on GitHub Pages.

Add one small script that sends six custom events. Full spec in §6.

Optionally add **Cloudflare Web Analytics** as a second beacon: free, unlimited,
cookieless, one script tag, no account linkage to the site. It gives you no
custom events, but it is on fewer blocklists than GA, so comparing its pageview
count against GA's tells you roughly how much GA is losing to blockers. That
calibration is the only reason to add it — skip it if you do not care.

## 4. The honest part about decommissioning

A portfolio is not a product, and the instinct to prune it by engagement metrics
will mislead you if applied literally.

- **Absence is noticed even when presence is not measured.** A recruiter who
  skims past Education without pausing still registers that you have a degree in
  progress. Delete it and they register a gap. Low dwell is not evidence of low
  value for credential sections.
- **Deleting blog posts is close to strictly bad.** It breaks inbound links and
  discards whatever SEO the URL has accrued. A post nobody read is a signal
  about *what to write next*, not about what to delete.
- **The reversible move is demotion, not deletion.** Reorder, move below the
  fold, or collapse weak project cards into a "More projects" list. You keep the
  content, you change the emphasis, and you can undo it in one commit.
- **Your traffic will be too low for a month of data to mean anything.** Set a
  floor before you act on any of it: at minimum **100 sessions and 90 days** on a
  comparison before you treat a difference between two project cards as real.
  Below that you are reading noise.
- **One card cannot be compared to another fairly unless both have links.** The
  NHL Arena card (`index.html:384`) has no `.project-links` block at all, so it
  can never register a click. Judge it on dwell and section reach only, or give
  it a link first.

The comparisons that *are* decision-useful:

| Signal | What it actually tells you |
|---|---|
| `project_click` per card, over 90 days | Which projects a reader cared enough to open. Real relative interest. |
| `section_view` reach, `#projects` → `#contact` | How far down the page people get. If `#contact` reach is tiny, the page is too long or the hero is not earning the scroll. |
| Engagement time on `/` split by referrer | LinkedIn traffic bouncing in <10s means the hero is not landing for that audience. |
| `contact_submit` count | The only thing on this site that is unambiguously a win. Mark it a key event. |
| Post views + referrer, by post | What to write more of. Not what to delete. |

## 5. Privacy

You are in Ontario, so PIPEDA rather than GDPR, and a personal portfolio running
GA4 without a consent banner is normal practice and low practical risk. GA4
truncates IPs by default. Two cheap things worth doing anyway, given who reads
this site: honour `navigator.doNotTrack` by skipping the custom events for
those users, and keep every event parameter non-identifying — section IDs and
project names only, never form field contents. The contact form posts to
Formspree; do **not** send the name, email, or message body to GA. Fire
`contact_submit` with no parameters.

---

## 6. Master prompt — hand this to a fresh session

Everything below the line is the implementation brief. It assumes no prior
context.

---

> You are working in `brettadams0/brettadams0.github.io`, a Jekyll site published
> by GitHub Pages. Develop on a new branch off `main`, commit, push, and open a
> draft PR.
>
> **Context.** Google Analytics 4 (`G-EBYB5MK388`) is already installed, but the
> snippet is duplicated in `index.html` (~line 84) and `_layouts/default.html`
> (~line 50). The site is essentially one long landing page plus a small blog, so
> page-level analytics cannot distinguish the landing page's sections. The goal
> is section-level and interaction-level events, so the owner can judge which
> sections and project cards are earning their place. Do not add any third-party
> analytics vendor. Do not add a build step, a bundler, or an npm dependency —
> this site deliberately has none.
>
> ### Task 1 — De-duplicate the GA snippet
>
> - Add the measurement ID to `_config.yml` as `google_analytics: G-EBYB5MK388`.
> - Create `_includes/analytics.html` containing the existing gtag snippet, with
>   the hard-coded ID replaced by `{{ site.google_analytics }}`, and wrapped so
>   it emits nothing when that value is unset:
>   `{% if site.google_analytics %}...{% endif %}`.
> - Replace the inline snippet in both `index.html` and `_layouts/default.html`
>   with `{% include analytics.html %}`, in the same position in `<head>`.
>   `index.html` has front matter (`---` at the top), so Liquid is processed
>   there — this works.
> - Do not add it to `bargain-hunt/index.html`. That page is intentionally
>   `noindex, nofollow` and is not part of the public site.
>
> ### Task 2 — Add the events script
>
> Create `assets/js/analytics-events.js` — plain ES5-compatible JS, no modules,
> no dependencies — and load it with `defer` from `_includes/analytics.html` so
> both the landing page and the blog get it. It must be inert and throw nothing
> when `gtag` is undefined (ad blockers will remove it for a real share of
> visitors — guard every send).
>
> Open with an opt-out guard:
>
> ```js
> if (navigator.doNotTrack === '1' || window.doNotTrack === '1') return;
> ```
>
> Send these six events via `gtag('event', name, params)`:
>
> 1. **`section_view`** — an `IntersectionObserver` over every
>    `main > section[id], body > section[id]` (the landing page's sections are
>    `#experience`, `#projects`, `#skills`, `#education`, `#blog-teaser`,
>    `#contact`). Fire **once per section per page load**, when it has been at
>    least 50% visible for a continuous 1s — debounce with a timer that the
>    un-intersect cancels, so a fast scroll past does not count as a view.
>    Params: `{ section_id }`.
>
> 2. **`section_dwell`** — accumulate visible time per section using the same
>    observer. Flush on `visibilitychange` (to `hidden`) and on `pagehide`,
>    guarded so a page that fires both does not double-send. Send seconds
>    rounded to an integer, and drop any section under 2s.
>    Params: `{ section_id, seconds }`.
>
> 3. **`project_click`** — one delegated `click` listener on
>    `#projects`. On a click inside a `.project-row`, read the project name from
>    that row's `.project-right h3` textContent, and the destination from the
>    clicked anchor's `href`. This markup is uniform across all six cards.
>    Params: `{ project_name, link_url }`.
>
> 4. **`resume_download`** — delegated click on
>    `a[href$="Resume_BrettAdams.pdf"]`. GA's enhanced measurement also emits
>    `file_download` for this; the explicit event is for reliability and for a
>    name you control. No params.
>
> 5. **`contact_submit`** — `submit` listener on `#contact-form`.
>    **Send no parameters.** The form posts to Formspree and carries a name,
>    email, and message body; none of that may reach GA.
>
> 6. **`scroll_depth`** — fire at 25/50/75/100% of document height, once each per
>    page load. GA's built-in scroll event only fires at 90%, which is too coarse
>    to tell a skim from a read. Throttle the scroll handler with
>    `requestAnimationFrame`. Params: `{ percent }`.
>
> Implementation constraints:
>
> - Respect `prefers-reduced-motion` nowhere here — it is irrelevant to
>   analytics; do not couple to it.
> - The landing page's sections use AOS and start at `opacity: 0`. An
>   `IntersectionObserver` still reports them as intersecting, so this is fine,
>   but do not use opacity or `getBoundingClientRect` visibility as a proxy.
> - `#blog-teaser` and `#contact` exist only on the landing page; the blog pages
>   have no `section[id]`. The script must no-op cleanly there rather than error.
> - Total added JS should stay well under 4KB unminified. If it is growing past
>   that, the design is wrong.
>
> ### Task 3 — Verify
>
> - Run the existing CI checks locally before pushing:
>   ```sh
>   jekyll build --destination ./_site && node .github/scripts/check-links.mjs
>   ```
>   `check-links.mjs` walks `_site` and fails on any internal reference that does
>   not resolve, so it will catch a wrong path on the new JS file.
> - Confirm the built `_site/index.html` and a built post both contain the gtag
>   snippet exactly once and the script tag exactly once.
> - Confirm nothing under `_site/bargain-hunt/` references gtag.
>
> ### Task 4 — Document
>
> Append a short section to `README.md` under a new `## Analytics` heading: which
> events exist, where they are defined, and the one-line reason each exists. Keep
> the README's existing voice — explanatory prose, not a bullet dump.
>
> ### Out of scope — do not do these
>
> - Do not add a consent banner. Not required here, and it would cost more
>   traffic than the events are worth.
> - Do not add Cloudflare Web Analytics, Plausible, Fathom, or any second vendor.
> - Do not delete, reorder, or demote any page, section, or project card. This
>   change only measures. The owner decides what to cut, after data exists.
>
> ### What to do in the GA4 console afterwards (report this back, do not attempt it)
>
> These cannot be done from the repo:
>
> 1. Admin → Data Streams → Enhanced measurement — confirm it is **on**.
> 2. Admin → Events → mark `contact_submit` as a **key event**.
> 3. Admin → Custom definitions → register `section_id`, `project_name`, and
>    `percent` as **custom dimensions**, and `seconds` as a **custom metric**.
>    Until this is done the parameters are collected but will not appear in any
>    report — this step is not optional, and it is not retroactive.
