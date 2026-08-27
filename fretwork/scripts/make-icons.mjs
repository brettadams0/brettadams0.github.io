// Generates the PWA launcher icons as PNGs with no image dependencies —
// a fretboard fragment on the chassis colour, drawn by pixel arithmetic.
// Run with `npm run icons` after changing any colour in §9.
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';

const CHASSIS = [0x1b, 0x21, 0x1f];
const EDGE = [0x3a, 0x44, 0x41];
const DIM = [0x8e, 0x98, 0x91];
const SILK = [0xea, 0xe4, 0xd2];
const LAMP = [0xf0, 0xa8, 0x68];

const CRC = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return (buf) => {
    let c = -1;
    for (const b of buf) c = t[(c ^ b) & 0xff] ^ (c >>> 8);
    return (c ^ -1) >>> 0;
  };
})();

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(CRC(body));
  return Buffer.concat([len, body, crc]);
}

function png(size, pixel) {
  // One filter byte (0 = None) then RGB per row.
  const raw = Buffer.alloc(size * (1 + size * 3));
  let o = 0;
  for (let y = 0; y < size; y++) {
    raw[o++] = 0;
    for (let x = 0; x < size; x++) {
      const [r, g, b] = pixel(x, y);
      raw[o++] = r;
      raw[o++] = g;
      raw[o++] = b;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // colour type: truecolour
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// `inset` is the fraction of the canvas kept clear on every side. Maskable
// icons get 0.1 so the art stays inside the launcher's safe circle.
function fretboard(size, inset) {
  const pad = size * inset;
  const span = size - pad * 2;
  return (x, y) => {
    const u = (x - pad) / span; // 0..1 across the neck
    const v = (y - pad) / span; // 0..1 along the neck
    if (u < 0 || u > 1 || v < 0 || v > 1) return CHASSIS;

    // Two frets, bright nickel.
    for (const fy of [0.3, 0.72]) {
      if (Math.abs(v - fy) < 0.035) return DIM;
    }

    // A single lamp-lit position marker between them.
    const dx = (u - 0.5) * span;
    const dy = (v - 0.51) * span;
    if (dx * dx + dy * dy < (span * 0.085) ** 2) return LAMP;

    // Six strings, thickest at the low E on the left.
    for (let s = 0; s < 6; s++) {
      const sx = 0.11 + s * 0.156;
      const w = 0.031 - s * 0.0035;
      if (Math.abs(u - sx) < w) return s < 3 ? SILK : DIM;
    }

    return EDGE;
  };
}

mkdirSync('public/icons', { recursive: true });
const out = [
  ['public/icons/icon-192.png', png(192, fretboard(192, 0.06))],
  ['public/icons/icon-512.png', png(512, fretboard(512, 0.06))],
  ['public/icons/icon-maskable-512.png', png(512, fretboard(512, 0.18))],
];
for (const [path, buf] of out) {
  writeFileSync(path, buf);
  console.log(`${path}  ${buf.length} bytes`);
}
