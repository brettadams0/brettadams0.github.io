// Generated into the portfolio folder only. Registration requires an explicit save.
const release = "a74858b763f0daa84797c99d326a5eeb1c361dcb506dabb72193a0db2fb22d36";
const integrity = {"assets/Retention-CJdPQsn4.js":"sha256-MFVj5qpA6czg4NawnmNVdFfGijLP6OZoz2Jr2G3PK/k=","assets/Retention-CgNNPefQ.css":"sha256-7iczAYTw2ZL+C7MrFd9Bp3KlcgXFXK8hQMNXvCsYuQg=","assets/Revenue-CDM1ixv1.js":"sha256-woFlQi6sevxTARJzit1xwtYdvoarP7oBLZtSk4B5Mhw=","assets/Revenue-DL2jme-J.css":"sha256-E7Hsg2KUVpfiaxbmd5bfShe+ZSCxM6X8wCcdWDs5vIU=","assets/Severity-wlsY_wWw.js":"sha256-FqLQ1YyFpBAkuhcx7jP8yYxSJIFUpVU9mJcXvF1rh2s=","assets/charts-C04-HEW5.js":"sha256-8aH/qETqpxeMt9czluPS6hfkXLbDxZv8Cu70x92VtGw=","assets/ibm-plex-mono-latin-400-BJoXLJYV.woff2":"sha256-w29QnAqPn4XynLRLyHAdip4LFMSZ53qIT3ierXCTp6w=","assets/ibm-plex-sans-latin-var-BD64o3ke.woff2":"sha256-BW5OJFn1egAzyMnIRP8Z1uQqyGAgJ4A9Q0WCO8yTmBg=","assets/index-BuXryZhB.js":"sha256-iwPSbhLH92vI5dIGTFRh55cLC2dRzHrCGQFQHa0Fssk=","assets/index-QHnHY5W7.css":"sha256-R4W7iSBFiIV585C7/rbuy/3kjWSbW34vgyt56pZ2FZk=","assets/plot-DXuoGxG6.js":"sha256-HGC3UZha93GU1j0YjMG1A4brZIX6fmxsdoHpkU/9wE0=","assets/release-B6gFP0UI.js":"sha256-UOSOB9f8joEpXT1Ja5gql1CxlKkLemnZY4CXgNuqfmk=","assets/renderer-0DptQx3h.js":"sha256-d54DS6XY/jnkNuhhZnadHPmeTmbN4TKZ+XzYD1ATmEw=","index.html":"sha256-eaNxOy7X8LYRWBO+CVOxFZjFbb+9yD7KkjTJZZvWG28=","models/transit/3210a26fcc5b0a2171195cee5d6b8b7a07cd6921579de7e91a18fed514357e24.json":"sha256-MhCib8xbCiFxGVzuXWuLegfNaSFXnefpGhj+1RQ1fiQ=","snapshots/retention/retention-7fc6ddf4a393864b.json.gz":"sha256-f8bd9KOThkslY9EF85WUhsVmcR7kclx0gBbZK4JDA88=","snapshots/revenue/assessments-f0c524a49402dcca.json.gz":"sha256-8MUkpJQC3Mr5RZFcB01oceZCXcUWz7KYfzNoxVqsJuE=","snapshots/v2/manifest.json":"sha256-XtMtv7BGU90ldWtbdyVoTI7LZWSyaw2cy6T5QwWFG0o=","snapshots/v2/retention-30-f6f0bb3d12384494.json":"sha256-9vC7PRI4RJTjNml48jfgcmnLzzDeaIMABQIB5H48ejk=","snapshots/v2/retention-7-c78119f42e36ad4b.json":"sha256-x4EZ9C42rUtgmxpaNV8v0IW1yI8EX/AcM4/MHYBIrpM=","snapshots/v2/retention-90-193cbf08c58cee1a.json":"sha256-GTy/CMWM7hpCC75YcXGr/TLYLL3YnNJUlMjBcb4dN5c=","snapshots/v2/revenue-30-0985b646baca0b9f.json":"sha256-CYW2RrrKC5/1YNw1zbplgwuMU+6Q59N2D3ShjXg3pjE=","snapshots/v2/revenue-7-d854b8dc35c53a9c.json":"sha256-2FS43DXFOpwjIAMoZFbDpfk73syJaYi1vrHwdpmtI5w=","snapshots/v2/revenue-90-1db0de2da03e6794.json":"sha256-HbDeLaA+Z5TjZ665kMJIkY4jTf5FUOp+vM5FMAA1ZJM=","snapshots/v2/transit-30-fb5a0688bd6a061b.json":"sha256-+1oGiL1qBhtqV1KQUlZ78px6o8UZQkTysjya31zNxdE=","snapshots/v2/transit-7-013a235b7aa7ec2b.json":"sha256-ATojW3qn7CsSYrP7IKVqWBdY7yD3GPuvzFQwwXtICbk=","snapshots/v2/transit-90-1811af4445ab89e9.json":"sha256-GBGvREWriem4SLxb8IrNAwQjIl9V9M03hJKSMWFLAXo="};
const scope = self.registration.scope;
const cacheName = "signal-lab-offline-" + release;
const urls = new Set(Object.keys(integrity).map((path) => new URL(path, scope).href));
self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(cacheName);
      await cache.addAll(
        Object.entries(integrity).map(([path, digest]) =>
          new Request(new URL(path, scope), { cache: "reload", integrity: digest }),
        ),
      );
      // Let open clients finish against their original index, manifest and chunks.
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
    [scope, scope + "index.html"].includes(event.request.url.split(/[?#]/)[0]);
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
