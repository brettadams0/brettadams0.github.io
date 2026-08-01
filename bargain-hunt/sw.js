/* The Bargain Hunt — service worker.
 *
 * Two jobs: cache the shell so the last brief is readable offline, and run
 * the daily hunt in the background when the browser lets us.
 */
importScripts("core.js");

const CACHE = "bargain-hunt-v1";
const SHELL = [
  "./",
  "index.html",
  "styles.css",
  "core.js",
  "app.js",
  "manifest.json",
  "icon-192.png",
  "icon-512.png",
  "icon-maskable-512.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((c) => c.addAll(SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;

  const url = new URL(req.url);
  // Never cache API traffic; it carries the key header and is always live.
  if (url.hostname === "api.anthropic.com") return;

  // Same-origin shell: cache first, so a cold offline start still renders.
  if (url.origin === location.origin) {
    event.respondWith(
      caches.match(req).then(
        (hit) =>
          hit ||
          fetch(req)
            .then((res) => {
              if (res.ok) {
                const copy = res.clone();
                caches.open(CACHE).then((c) => c.put(req, copy));
              }
              return res;
            })
            .catch(() => caches.match("index.html"))
      )
    );
    return;
  }

  // Google Fonts: stale-while-revalidate so type survives offline.
  if (url.hostname.endsWith("googleapis.com") || url.hostname.endsWith("gstatic.com")) {
    event.respondWith(
      caches.match(req).then((hit) => {
        const net = fetch(req)
          .then((res) => {
            if (res.ok) {
              const copy = res.clone();
              caches.open(CACHE).then((c) => c.put(req, copy));
            }
            return res;
          })
          .catch(() => hit);
        return hit || net;
      })
    );
  }
});

/* ------------------------------------------------------ background refresh */

async function notify(result) {
  const { brief, shown, settings, watchlistHit } = result;

  if (watchlistHit && settings.notifyOnWatchlistDrop) {
    const pct = typeof watchlistHit.dropPct === "number"
      ? `${Math.abs(watchlistHit.dropPct).toFixed(1)}%`
      : "sharply";
    await self.registration.showNotification(
      `${watchlistHit.ticker} is down ${pct}`,
      { body: brief.note || "", tag: "daily-brief", icon: "icon-192.png", badge: "icon-192.png" }
    );
    return;
  }

  if (settings.notifyOnNewBrief) {
    const n = (shown || brief.candidates).length;
    if (!n) return; // nothing survived his filters — don't buzz him for zero
    await self.registration.showNotification(
      `${n} candidate${n === 1 ? "" : "s"} today`,
      { body: brief.note || "", tag: "daily-brief", icon: "icon-192.png", badge: "icon-192.png" }
    );
  }
}

async function runDailyHunt() {
  try {
    const result = await self.BH.refreshBrief();
    if (!result.ok) return;

    if (Notification.permission === "granted") await notify(result);

    for (const client of await self.clients.matchAll({ type: "window" })) {
      client.postMessage({ type: "brief-updated" });
    }
  } catch {
    // Leave the previous brief in place. A failed refresh must never clear
    // a good cached brief (spec §13).
  }
}

self.addEventListener("periodicsync", (event) => {
  if (event.tag === "daily-hunt") event.waitUntil(runDailyHunt());
});

// Manual trigger, so a foreground "run now" can also go through the worker.
self.addEventListener("sync", (event) => {
  if (event.tag === "daily-hunt") event.waitUntil(runDailyHunt());
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((list) => {
      for (const c of list) {
        if (c.url.includes("/bargain-hunt/") && "focus" in c) return c.focus();
      }
      return self.clients.openWindow("./");
    })
  );
});
