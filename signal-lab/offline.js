// Generated into the portfolio folder only. Registration requires an explicit save.
const release = "1ec21eb69e362284";
const files = ["models/transit/3210a26fcc5b0a2171195cee5d6b8b7a07cd6921579de7e91a18fed514357e24.json","index.html","snapshots/v2/manifest.json","assets/index-BaBwlXbT.js","assets/renderer-0DptQx3h.js","assets/charts-C04-HEW5.js","assets/Severity-D4qw9mwD.js","assets/plot-BB4nqe9T.js","assets/release-B6gFP0UI.js","assets/ibm-plex-sans-latin-var-BD64o3ke.woff2","assets/ibm-plex-mono-latin-400-BJoXLJYV.woff2","assets/index-cAXMPZDX.css","snapshots/v2/transit-7-013a235b7aa7ec2b.json","snapshots/v2/transit-30-fb5a0688bd6a061b.json","snapshots/v2/transit-90-1811af4445ab89e9.json","snapshots/v2/revenue-7-d854b8dc35c53a9c.json","snapshots/v2/revenue-30-0985b646baca0b9f.json","snapshots/v2/revenue-90-1db0de2da03e6794.json","snapshots/v2/retention-7-c78119f42e36ad4b.json","snapshots/v2/retention-30-f6f0bb3d12384494.json","snapshots/v2/retention-90-193cbf08c58cee1a.json"];
const scope = self.registration.scope;
const cacheName = "signal-lab-offline-" + release;
const urls = new Set(files.map((path) => new URL(path, scope).href));
self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(cacheName);
      await cache.addAll(
        [...urls].map((url) => new Request(url, { cache: "reload" })),
      );
      await self.skipWaiting();
    })(),
  );
});
self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      for (const name of await caches.keys()) {
        if (name.startsWith("signal-lab-offline-") && name !== cacheName)
          await caches.delete(name);
      }
      await self.clients.claim();
    })(),
  );
});
self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  const navigation =
    event.request.mode === "navigate" &&
    [scope, scope + "index.html"].includes(event.request.url.split("?")[0]);
  if (!navigation && !urls.has(event.request.url)) return;
  event.respondWith(
    (async () => {
      const cache = await caches.open(cacheName);
      // Serve the saved release as a unit, including its matching manifest and code.
      return (
        (await cache.match(
          navigation ? scope + "index.html" : event.request.url,
        )) ?? fetch(event.request)
      );
    })(),
  );
});
