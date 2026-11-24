import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const live = !!process.env.BASE_URL;

// Our own pages, and the live demos published alongside them.
const pages = ['/', '/mcp/', '/projects/neuronet-arena/', '/projects/signal-lab/', '/projects/signal-lab/investigate/'];
const demos = ['/signal-lab/', '/mcp-surface/', '/neuronet-arena/', '/border-waits/', '/mazefinder/'];

/**
 * border-waits reads its data from the border-waits-pipeline repository's own Pages site
 * (/border-waits-pipeline/data/v1/), which only exists in production. Locally, serve the
 * recorded export the demo ships with at that route, as CI does.
 */
async function serveBorderWaitsData(page: Page) {
  if (live) return;
  await page.route('**/border-waits-pipeline/data/v1/**', async (route) => {
    const rel = new URL(route.request().url()).pathname.split('/border-waits-pipeline/data/v1/')[1] || 'manifest.json';
    const file = path.join('public/border-waits/fallback', decodeURIComponent(rel));
    try {
      await route.fulfill({ body: readFileSync(file), contentType: file.endsWith('.json') ? 'application/json' : 'application/octet-stream' });
    } catch {
      await route.fulfill({ status: 404, body: '' });
    }
  });
}

/** Collects console errors, uncaught exceptions and failed same-origin requests. */
function watch(page: Page, origin: string) {
  const problems: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') problems.push(`console: ${m.text()}`); });
  page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));
  page.on('requestfailed', (r) => {
    const url = r.url();
    // Aborted media/analytics requests are not errors (videos pause; GA may be blocked).
    if (url.startsWith(origin) && !/\.(webm|mp4)(\?|$)/.test(url) && r.failure()?.errorText !== 'net::ERR_ABORTED') {
      problems.push(`requestfailed: ${url} ${r.failure()?.errorText}`);
    }
  });
  page.on('response', (r) => {
    if (r.url().startsWith(origin) && r.status() >= 400) problems.push(`HTTP ${r.status()}: ${r.url()}`);
  });
  return problems;
}

for (const p of [...pages, ...demos]) {
  test(`${p} loads with no console errors or failed requests`, async ({ page, baseURL }) => {
    await serveBorderWaitsData(page);
    const problems = watch(page, new URL(baseURL!).origin);
    const res = await page.goto(p, { waitUntil: 'load' });
    expect(res?.status()).toBe(200);
    await page.waitForLoadState('networkidle').catch(() => {});
    await expect(page.locator('h1').first()).toBeVisible();
    expect(problems).toEqual([]);
  });
}

for (const p of pages) {
  test(`${p} has complete SEO metadata`, async ({ page }) => {
    await page.goto(p);
    expect((await page.title()).length).toBeGreaterThan(10);
    await expect(page.locator('meta[name="description"]')).toHaveAttribute('content', /.{50,}/);
    await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', `https://brettadams.ca${p}`);
    await expect(page.locator('link[rel="canonical"]')).toHaveCount(1);
    await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content', /^https:\/\/brettadams\.ca\//);
    await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute('content', 'summary_large_image');
    await expect(page.locator('h1')).toHaveCount(1);
    await expect(page.locator('html')).toHaveAttribute('lang', /^en/);
  });
}

test('homepage has Person JSON-LD', async ({ page }) => {
  await page.goto('/');
  const raw = await page.locator('script[type="application/ld+json"]').textContent();
  const data = JSON.parse(raw!);
  const person = data['@graph'].find((n: { '@type': string }) => n['@type'] === 'Person');
  expect(person.name).toBe('Brett Adams');
  expect(person.url).toBe('https://brettadams.ca/');
});

test('every page ships a Content-Security-Policy without unsafe-inline', async ({ page }) => {
  for (const path of ['/', '/mcp/', '/projects/neuronet-arena/', '/projects/signal-lab/', '/projects/signal-lab/investigate/', '/404.html']) {
    await page.goto(path);
    const csp = await page.locator('meta[http-equiv="content-security-policy"]').getAttribute('content');
    expect(csp).toContain("default-src 'self'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).not.toContain('unsafe-inline');
  }
});

test('case studies have Article and BreadcrumbList JSON-LD', async ({ page }) => {
  for (const [path, depth] of [['/mcp/', 2], ['/projects/neuronet-arena/', 2], ['/projects/signal-lab/investigate/', 3]] as const) {
    await page.goto(path);
    const data = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent())!);
    const types = data['@graph'].map((n: { '@type': string }) => n['@type']);
    expect(types).toEqual(['Article', 'BreadcrumbList']);
    const crumbs = data['@graph'][1].itemListElement;
    expect(crumbs).toHaveLength(depth);
    expect(crumbs.at(-1).item).toBe(`https://brettadams.ca${path}`);
  }
});

test('section navigation scrolls to each section', async ({ page, isMobile }) => {
  await page.goto('/');
  const nav = page.getByRole('navigation', { name: isMobile ? 'Sections' : 'Main' });
  for (const [label, id] of [['Experience', 'experience'], ['Contact', 'contact'], ['Projects', 'projects']]) {
    await nav.getByRole('link', { name: label }).click();
    await expect(page).toHaveURL(new RegExp(`#${id}$`));
    await expect(page.locator(`#${id} h2`).first()).toBeInViewport();
  }
});

test('keyboard: skip link is first and moves focus to main', async ({ page, isMobile }) => {
  test.skip(isMobile, 'keyboard test runs on desktop');
  await page.goto('/');
  await page.keyboard.press('Tab');
  const skip = page.getByRole('link', { name: 'Skip to content' });
  await expect(skip).toBeFocused();
  await expect(skip).toBeInViewport();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/#main$/);
});

test('every focusable element shows a visible focus indicator', async ({ page, isMobile }) => {
  test.skip(isMobile, 'keyboard test runs on desktop');
  await page.goto('/');
  const missing: string[] = [];
  for (let i = 0; i < 60; i++) {
    await page.keyboard.press('Tab');
    const info = await page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null;
      if (!el || el === document.body) return null;
      const s = getComputedStyle(el);
      const after = getComputedStyle(el, '::after');
      const visible = (s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) > 0) || (after.outlineStyle !== 'none' && parseFloat(after.outlineWidth) > 0);
      return { visible, name: `${el.tagName} ${el.textContent?.trim().slice(0, 30)}` };
    });
    if (!info) break;
    if (!info.visible) missing.push(info.name);
  }
  expect(missing).toEqual([]);
});

test('unknown paths return the custom 404 page', async ({ page }) => {
  const res = await page.goto('/this-page-does-not-exist/');
  expect(res?.status()).toBe(404);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('isn’t here');
  await expect(page.getByRole('link', { name: 'See the projects' })).toBeVisible();
});

test('no horizontal scroll and no layout shift on the homepage', async ({ page }) => {
  await page.goto('/');
  const cls = await page.evaluate(() => new Promise<number>((resolve) => {
    let total = 0;
    new PerformanceObserver((list) => {
      for (const e of list.getEntries() as unknown as { value: number; hadRecentInput: boolean }[]) if (!e.hadRecentInput) total += e.value;
    }).observe({ type: 'layout-shift', buffered: true });
    setTimeout(() => resolve(total), 1500);
  }));
  expect(cls).toBeLessThan(0.02);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
});

test('reduced motion: card loops never load', async ({ browser, baseURL }) => {
  const ctx = await browser.newContext({ reducedMotion: 'reduce', baseURL });
  const page = await ctx.newPage();
  const media: string[] = [];
  page.on('request', (r) => { if (/\.(webm|mp4)/.test(r.url())) media.push(r.url()); });
  await page.goto('/');
  await page.locator('[data-project="neuronet-arena"]').scrollIntoViewIfNeeded();
  await page.waitForTimeout(800);
  expect(media).toEqual([]);
  await ctx.close();
});

test('all links on our pages resolve', async ({ page, request, baseURL }) => {
  test.setTimeout(180_000);
  const origin = new URL(baseURL!).origin;
  const links = new Set<string>();
  const homeAnchors: string[] = [];
  for (const p of pages) {
    await page.goto(p);
    const hrefs = await page.$$eval('a[href], link[rel~="icon"], link[rel="manifest"], link[rel="apple-touch-icon"], img[src], source[data-src]', (els) =>
      els.map((e) => (e as HTMLAnchorElement).href || (e as HTMLImageElement).src || (e.getAttribute('data-src') ? new URL(e.getAttribute('data-src')!, location.href).href : '')));
    for (const h of hrefs) if (h && !h.startsWith('mailto:')) links.add(h.split('#')[0]);
    // <source srcset> (e.g. the dark-mode chart) isn't covered by img[src]; check every URL it lists.
    const srcsets = await page.$$eval('source[srcset]', (els) =>
      els.flatMap((e) => e.getAttribute('srcset')!.split(',').map((c) => new URL(c.trim().split(/\s+/)[0], location.href).href)));
    for (const h of srcsets) links.add(h);
    // Same-page anchors must exist on that page; "/#id" anchors must exist on the homepage.
    const local = await page.$$eval('a[href^="#"]', (els) => els.map((e) => e.getAttribute('href')!.slice(1)).filter(Boolean));
    for (const id of local) expect(await page.locator(`[id="${id}"]`).count(), `#${id} on ${p}`).toBeGreaterThan(0);
    const home = await page.$$eval('a[href^="/#"]', (els) => els.map((e) => e.getAttribute('href')!.slice(2)));
    homeAnchors.push(...home);
  }
  await page.goto('/');
  for (const id of new Set(homeAnchors)) expect(await page.locator(`[id="${id}"]`).count(), `/#${id}`).toBeGreaterThan(0);
  const broken: string[] = [];
  const external = live || !!process.env.LINKS_EXTERNAL;
  for (const url of links) {
    const internal = url.startsWith(origin);
    // Third-party sites are checked against the live site (and with LINKS_EXTERNAL=1), not on every PR.
    if (!internal && !external) continue;
    try {
      // npmjs.com blocks automated requests (403); check the package in the npm registry instead.
      const npm = url.match(/^https:\/\/www\.npmjs\.com\/package\/([^/?#]+)/);
      const target = npm ? `https://registry.npmjs.org/${npm[1]}` : url;
      const res = await request.get(target, { maxRedirects: 5, timeout: 20_000, headers: internal ? {} : { 'User-Agent': 'Mozilla/5.0 (link check)' } });
      const s = res.status();
      // LinkedIn answers automated requests with 999; it is not a broken link.
      if (s >= 400 && !(url.includes('linkedin.com') && s === 999) && !(s === 429)) broken.push(`${s} ${url}`);
    } catch (e) {
      broken.push(`ERR ${url} ${(e as Error).message.split('\n')[0]}`);
    }
  }
  expect(broken).toEqual([]);
});
