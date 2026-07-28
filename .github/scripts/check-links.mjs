#!/usr/bin/env node
// Verifies that every internal link and asset reference in the built site
// resolves to a file that actually exists. GitHub Pages reports Jekyll build
// failures but is perfectly happy to publish a page pointing at a renamed
// image or a deleted post, which is the failure mode this catches.
//
// No dependencies and no network: it only reads ./_site.

import { readdirSync, statSync, existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const SITE = path.resolve('_site');

if (!existsSync(SITE)) {
  console.error('No _site directory — run the Jekyll build first.');
  process.exit(1);
}

function walk(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const full = path.join(dir, entry);
    return statSync(full).isDirectory() ? walk(full) : [full];
  });
}

// href="..." / src="..." on any element, single or double quoted.
const ATTR = /(?:href|src)\s*=\s*["']([^"']+)["']/gi;

const isExternal = (url) =>
  /^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(url) || url.startsWith('#');

/** Resolve a site-relative or document-relative URL to a path inside _site. */
function resolveTarget(url, fromFile) {
  const clean = url.split('#')[0].split('?')[0];
  if (!clean) return null;

  const base = clean.startsWith('/')
    ? path.join(SITE, clean)
    : path.resolve(path.dirname(fromFile), clean);

  // A directory URL is served by its index.html.
  if (existsSync(base) && statSync(base).isDirectory()) {
    return path.join(base, 'index.html');
  }
  // Jekyll's pretty permalinks mean /blog/post/ may be written without a slash.
  if (!path.extname(base) && existsSync(`${base}.html`)) {
    return `${base}.html`;
  }
  return base;
}

const htmlFiles = walk(SITE).filter((f) => f.endsWith('.html'));
const broken = [];
let checked = 0;

for (const file of htmlFiles) {
  const html = readFileSync(file, 'utf-8');
  for (const [, url] of html.matchAll(ATTR)) {
    if (isExternal(url)) continue;

    const target = resolveTarget(url, file);
    if (!target) continue;

    checked++;
    if (!existsSync(target)) {
      broken.push({
        page: path.relative(SITE, file),
        url,
        expected: path.relative(SITE, target),
      });
    }
  }
}

console.log(
  `Checked ${checked} internal reference(s) across ${htmlFiles.length} page(s).`
);

if (broken.length) {
  console.error(`\n${broken.length} broken reference(s):\n`);
  for (const b of broken) {
    console.error(`  ${b.page}\n    -> ${b.url}  (expected _site/${b.expected})`);
  }
  process.exit(1);
}

console.log('All internal references resolve.');
