/*
 * Section, click and scroll events for GA4. Loaded with `defer` from
 * _includes/analytics.html, so the blog gets it too -- the blog has no
 * `section[id]`, and the section events simply do not arm there.
 *
 * The landing page is one URL holding six sections, so per-URL reporting
 * cannot say whether anyone reached #education or ever opened a project card.
 * These events answer that. gtag is assumed absent throughout: a real share of
 * this audience blocks googletagmanager.com, so every send is guarded.
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

  // "At least half visible" means two things at once: half the section is on
  // screen, or -- for a section taller than the viewport, whose ratio can never
  // reach 0.5 -- it fills half the screen. Both come from the observer entry;
  // nothing here reads opacity or measures layout, because AOS leaves these
  // sections at opacity:0 until they animate in.
  function visible(e) {
    if (!e.isIntersecting) return false;
    if (e.intersectionRatio >= 0.5) return true;
    var root = e.rootBounds ? e.rootBounds.height : window.innerHeight;
    return root > 0 && e.intersectionRect.height >= root / 2;
  }

  // A continuous second at half visibility, so scrolling past at speed does not
  // register as having looked at anything.
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

  // Browsers fire visibilitychange, pagehide, or both, in either order --
  // whichever arrives first wins, and the rest are no-ops.
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

  /* project_click, resume_download, contact_submit */

  var projects = document.getElementById('projects');
  if (projects) {
    projects.addEventListener('click', function (event) {
      var link = closest(event.target, 'a');
      var row = link && closest(link, '.project-row');
      if (!row) return;
      var h3 = row.querySelector('.project-right h3');
      send('project_click', {
        project_name: h3 ? h3.textContent.replace(/\s+/g, ' ').trim() : '',
        link_url: link.href
      });
    });
  }

  // Enhanced measurement also records the résumé as file_download; the explicit
  // event is for reliability, and for a name that cannot be renamed under us.
  document.addEventListener('click', function (event) {
    if (closest(event.target, 'a[href$="Resume_BrettAdams.pdf"]')) send('resume_download');
  });

  var form = document.getElementById('contact-form');
  // Deliberately parameterless: the form carries a name, an email address and a
  // message body, and none of that may reach GA.
  if (form) form.addEventListener('submit', function () { send('contact_submit'); });

  /* scroll_depth -- GA's built-in scroll event only fires at 90%, too coarse
     to tell a skim from a read. */

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
