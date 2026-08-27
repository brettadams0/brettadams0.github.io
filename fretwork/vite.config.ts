import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { VitePWA } from 'vite-plugin-pwa';

// Fretwork is installed to the Android home screen and launched standalone, so
// the manifest is the part of this config that matters most. display:standalone
// plus a 192px and a 512px icon are Chrome's minimum bar for the install
// prompt; the separate maskable icon keeps the launcher from cropping the
// artwork into a circle badly.
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'fonts/*.woff2'],
      manifest: {
        name: 'Fretwork',
        short_name: 'Fretwork',
        description: 'Learn electric guitar. Offline, free, no account.',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#1B211F',
        theme_color: '#1B211F',
        categories: ['education', 'music'],
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          {
            src: 'icons/icon-maskable-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        // The whole app is meant to run with the radio off, so everything the
        // build emits is precached rather than fetched on demand. The worklet
        // and font globs are here so Phase 1's audio code and the panel type
        // survive going offline.
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        cleanupOutdatedCaches: true,
        navigateFallback: 'index.html',
      },
      devOptions: { enabled: false },
    }),
  ],
});
