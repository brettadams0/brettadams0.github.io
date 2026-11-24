import type { APIRoute } from 'astro';
import { SITE_URL } from '../data/site';

// Hand-listed so the live demos (static files in public/) are included alongside the Astro pages.
const pages = [
  { path: '/', priority: '1.0' },
  { path: '/projects/signal-lab/', priority: '0.8' },
  { path: '/projects/signal-lab/investigate/', priority: '0.5' },
  { path: '/mcp/', priority: '0.8' },
  { path: '/projects/neuronet-arena/', priority: '0.8' },
  { path: '/signal-lab/', priority: '0.7' },
  { path: '/mcp-surface/', priority: '0.7' },
  { path: '/neuronet-arena/', priority: '0.7' },
  { path: '/border-waits/', priority: '0.7' },
  { path: '/mazefinder/', priority: '0.7' },
];

export const GET: APIRoute = () => {
  const urls = pages
    .map((p) => `  <url><loc>${SITE_URL}${p.path}</loc><priority>${p.priority}</priority></url>`)
    .join('\n');
  return new Response(
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`,
    { headers: { 'Content-Type': 'application/xml; charset=utf-8' } },
  );
};
