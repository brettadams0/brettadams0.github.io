// Records where the bundled woff2 files came from, and re-fetches any that are
// missing. The files are committed, so this is a no-op on a normal checkout;
// it exists so the exact subsets are reproducible and so a build from a
// source tree without them still produces the right panel type.
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const DIR = 'public/fonts';

// Google Fonts latin subsets. The version segment (v13, v20) pins the file, so
// these URLs return the same bytes every time.
const FONTS = {
  'barlow-condensed-400.woff2':
    'https://fonts.gstatic.com/s/barlowcondensed/v13/HTx3L3I-JCGChYJ8VI-L6OO_au7B6xHT2g.woff2',
  'barlow-condensed-600.woff2':
    'https://fonts.gstatic.com/s/barlowcondensed/v13/HTxwL3I-JCGChYJ8VI-L6OO_au7B4873z3bWuQ.woff2',
  'ibm-plex-mono-400.woff2':
    'https://fonts.gstatic.com/s/ibmplexmono/v20/-F63fjptAgt5VM-kVkqdyU8n1i8q1w.woff2',
  'ibm-plex-mono-600.woff2':
    'https://fonts.gstatic.com/s/ibmplexmono/v20/-F6qfjptAgt5VM-kVkqdyU8n3vAOwlBFgg.woff2',
};

mkdirSync(DIR, { recursive: true });

const missing = Object.entries(FONTS).filter(
  ([name]) => !existsSync(join(DIR, name)),
);

if (missing.length === 0) {
  console.log('fonts: all present');
} else {
  for (const [name, url] of missing) {
    const res = await fetch(url, {
      // gstatic serves woff2 only to clients that advertise support for it.
      headers: { 'User-Agent': 'Mozilla/5.0 Chrome/120.0.0.0' },
    });
    if (!res.ok) throw new Error(`${name}: ${res.status} ${res.statusText}`);
    const buf = Buffer.from(await res.arrayBuffer());
    if (buf.subarray(0, 4).toString('ascii') !== 'wOF2') {
      throw new Error(`${name}: not a woff2 file`);
    }
    writeFileSync(join(DIR, name), buf);
    console.log(`fonts: fetched ${name} (${buf.length} bytes)`);
  }
}
