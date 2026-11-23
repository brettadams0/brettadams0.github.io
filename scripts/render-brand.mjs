// Renders the social card and favicon PNGs from HTML, using the site's own font.
// Run: node scripts/render-brand.mjs   (writes into public/)
import { chromium } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const font = readFileSync(path.join(root, 'public/assets/fonts/ibm-plex-sans-latin-var.woff2')).toString('base64');
const mono = readFileSync(path.join(root, 'public/assets/fonts/ibm-plex-mono-latin-400.woff2')).toString('base64');
const faces = `
@font-face{font-family:Plex;src:url(data:font/woff2;base64,${font}) format('woff2');font-weight:400 700}
@font-face{font-family:PlexMono;src:url(data:font/woff2;base64,${mono}) format('woff2')}
*{margin:0;box-sizing:border-box}`;

const card = `<!doctype html><html><head><style>${faces}
body{width:1200px;height:630px;font-family:Plex;color:oklch(95% 0.008 255);
  background:radial-gradient(90% 120% at 100% 0%, oklch(82% 0.12 180 / .16), transparent 60%),
             linear-gradient(in oklch 135deg, oklch(16% 0.012 255), oklch(21% 0.016 255));
  padding:84px 96px;display:flex;flex-direction:column}
.k{font-family:PlexMono;font-size:26px;color:oklch(82% 0.12 180);display:flex;align-items:center;gap:14px}
.dot{width:14px;height:14px;border-radius:50%;background:oklch(80% 0.16 150);box-shadow:0 0 0 6px oklch(80% 0.16 150 / .2)}
h1{margin-top:52px;font-size:118px;font-weight:650;letter-spacing:-.04em;line-height:1}
p{margin-top:30px;font-size:36px;line-height:1.35;color:oklch(85% 0.012 255);max-width:900px}
.u{margin-top:auto;display:flex;justify-content:space-between;align-items:flex-end;font-family:PlexMono;font-size:28px;color:oklch(72% 0.02 255)}
.u b{color:oklch(95% 0.008 255);font-weight:400}
.m{width:72px;height:72px;border-radius:18px;background:oklch(95% 0.008 255);color:oklch(16% 0.012 255);display:grid;place-items:center;font:650 28px Plex;position:relative}
.m:after{content:"";position:absolute;left:20px;right:20px;bottom:12px;height:4px;border-radius:2px;background:oklch(50% 0.09 180)}
</style></head><body>
<div class="k"><span class="dot"></span>software engineering · graduating 2027</div>
<h1>Brett Adams</h1>
<p>Backend services, data pipelines and developer tools. Data Analyst Intern at Geotab; Computer Science at the University of Windsor.</p>
<div class="u"><b>brettadams.ca</b><span class="m">BA</span></div>
</body></html>`;

const icon = (size, pad = 0) => `<!doctype html><html><head><style>${faces}
body{width:${size}px;height:${size}px;background:${pad ? 'oklch(16% 0.012 255)' : 'transparent'};display:grid;place-items:center}
.m{width:${size - pad * 2}px;height:${size - pad * 2}px;border-radius:${pad ? 0 : Math.round(size * 0.22)}px;background:oklch(16% 0.012 255);color:oklch(95% 0.008 255);
display:grid;place-items:center;font:650 ${Math.round((size - pad * 2) * 0.42)}px/1 Plex;letter-spacing:-.02em;position:relative}
.m:after{content:"";position:absolute;left:28%;right:28%;bottom:${pad ? '30%' : '17%'};height:${Math.max(1, Math.round(size * 0.05))}px;border-radius:9px;background:oklch(82% 0.12 180)}
</style></head><body><div class="m">BA</div></body></html>`;

const browser = await chromium.launch();
async function shot(html, w, h, out) {
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  await page.setContent(html);
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: path.join(root, out), omitBackground: true });
  await page.close();
}
await shot(card, 1200, 630, 'public/assets/img/social-card.png');
await shot(icon(180), 180, 180, 'public/apple-touch-icon.png');
await shot(icon(192), 192, 192, 'public/icon-192.png');
await shot(icon(512), 512, 512, 'public/icon-512.png');
await shot(icon(512, 80), 512, 512, 'public/icon-maskable-512.png');
await shot(icon(32), 32, 32, 'scripts/.favicon-32.png');
await browser.close();

// favicon.ico: a single 32×32 PNG wrapped in an ICO container (supported by every current browser).
const png = readFileSync(path.join(root, 'scripts/.favicon-32.png'));
const header = Buffer.alloc(22);
header.writeUInt16LE(0, 0); header.writeUInt16LE(1, 2); header.writeUInt16LE(1, 4);
header.writeUInt8(32, 6); header.writeUInt8(32, 7); header.writeUInt8(0, 8); header.writeUInt8(0, 9);
header.writeUInt16LE(1, 10); header.writeUInt16LE(32, 12);
header.writeUInt32LE(png.length, 14); header.writeUInt32LE(22, 18);
writeFileSync(path.join(root, 'public/favicon.ico'), Buffer.concat([header, png]));
console.log('rendered');
