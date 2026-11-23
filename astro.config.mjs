// @ts-check
import { defineConfig } from 'astro/config';

export default defineConfig({
  site: 'https://brettadams.ca',
  trailingSlash: 'ignore',
  build: {
    format: 'directory',
    // A few KB of CSS per page: inlining saves a render-blocking request.
    inlineStylesheets: 'always',
  },
  compressHTML: true,
  devToolbar: { enabled: false },
});
