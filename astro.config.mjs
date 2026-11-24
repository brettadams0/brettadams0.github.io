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
  // Content-Security-Policy as a <meta> tag (Pages can't send headers). Astro hashes every inline script
  // and style; the only third party is Google Analytics, loaded after idle on brettadams.ca.
  security: {
    csp: {
      directives: [
        "default-src 'self'",
        "img-src 'self' data: https://*.google-analytics.com https://*.googletagmanager.com",
        "connect-src 'self' https://*.google-analytics.com https://*.analytics.google.com https://*.googletagmanager.com",
        "font-src 'self'",
        "object-src 'none'",
        "base-uri 'self'",
        "form-action 'self'",
      ],
      scriptDirective: { resources: ["'self'", 'https://www.googletagmanager.com'] },
    },
  },
});
