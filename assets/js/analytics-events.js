/*
 * Section, click and scroll events for GA4, loaded with `defer` from
 * _includes/analytics.html.
 *
 * The homepage is one URL holding several sections, so per-URL reporting
 * cannot say whether anyone reached #skills or opened a project. These
 * events answer that. gtag may be blocked, so every send is guarded.
 */
(function () {
  'use strict';

  if (navigator.doNotTrack === '1' || window.doNotTrack === '1') return;

  function send(name, params) {
    if (typeof window.gtag !== 'function') return;
    try {
      window.gtag('event', name, params);
    } catch (e) {}
  }

  function closest(node, selector) {
    var el = node && node.nodeType === 1 ? node : node && node.parentNode;
    return el && el.closest ? el.closest(selector) : null;
  }

  /* section_view, section_dwell */

  var states = {};
  var flushed = false;

  // "At least half visible": half the section is on screen or, for a section
  // taller than the viewport, it fills half the screen.
  function visible(e) {
    if (!e.isIntersecting) return false;
    if (e.intersectionRatio >= 0.5) return true;
    var root = e.rootBounds ? e.rootBounds.height : window.innerHeight;
    return root > 0 && e.intersectionRect.height >= root / 2;
  }

  // A continuous second at half visibility, so scrolling past does not count.
  function armView(s) {
    s.timer = setTimeout(function () {
      s.timer = null;
      s.viewed = true;
      send('section_view', { section_id: s.id });
    }, 1000);
  }

  if (window.IntersectionObserver) {
    var thresholds = [];
    for (var t = 0; t <= 20; t++) thresholds.push(t / 20);

    var observer = new IntersectionObserver(function (entries) {
      var now = Date.now();
      for (var i = 0; i < entries.length; i++) {
        var e = entries[i];
        var s = states[e.target.id];
        if (visible(e)) {
          if (!s.since) s.since = now;
          if (!s.viewed && !s.timer) armView(s);
        } else {
          if (s.since) { s.total += now - s.since; s.since = 0; }
          if (s.timer) { clearTimeout(s.timer); s.timer = null; }
        }
      }
    }, { threshold: thresholds });

    var nodes = document.querySelectorAll('section[id]');
    for (var n = 0; n < nodes.length; n++) {
      states[nodes[n].id] = { id: nodes[n].id, total: 0, since: 0, timer: null, viewed: false };
      observer.observe(nodes[n]);
    }
  }

  // Browsers fire visibilitychange, pagehide or both; the first one wins.
  function flushDwell() {
    if (flushed) return;
    flushed = true;
    var now = Date.now();
    for (var id in states) {
      var s = states[id];
      if (s.since) { s.total += now - s.since; s.since = 0; }
      var seconds = Math.round(s.total / 1000);
      if (seconds >= 2) send('section_dwell', { section_id: id, seconds: seconds });
    }
  }

  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'hidden') flushDwell();
  });
  window.addEventListener('pagehide', flushDwell);

  /* project_click, resume_download, contact_click */

  document.addEventListener('click', function (event) {
    var link = closest(event.target, 'a');
    if (!link) return;

    var project = closest(link, '[data-project]');
    if (project) {
      send('project_click', {
        project_name: project.getAttribute('data-project'),
        link_url: link.href
      });
    }

    // Enhanced measurement also records this as file_download; this name is ours.
    if (/Resume_BrettAdams\.pdf$/.test(link.getAttribute('href') || '')) send('resume_download');

    var method = link.getAttribute('data-contact');
    if (method) send('contact_click', { method: method });
  });

  /* scroll_depth: GA's built-in scroll event only fires at 90%. */

  var marks = [25, 50, 75, 100];
  var next = 0;
  var queued = false;

  function measure() {
    queued = false;
    var doc = document.documentElement;
    if (doc.scrollHeight <= 0) return;
    var reached = (window.pageYOffset + window.innerHeight) / doc.scrollHeight * 100;
    while (next < marks.length && reached >= marks[next]) {
      send('scroll_depth', { percent: marks[next] });
      next++;
    }
  }

  window.addEventListener('scroll', function () {
    if (queued) return;
    queued = true;
    window.requestAnimationFrame(measure);
  }, { passive: true });

  measure(); // A page short enough to need no scrolling is still read to the end.
})();
