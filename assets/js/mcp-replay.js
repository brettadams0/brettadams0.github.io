/*
 * /mcp/ replay: an AI assistant calling three of the Google Workspace server's
 * real tools (names, arguments and annotations from its mcp-surface.json).
 * The calls are scripted and every value is example data; no account is touched.
 * Lines appear one at a time, then the replay loops. It pauses when off screen
 * or hidden; under prefers-reduced-motion the whole transcript shows at once.
 */
(function () {
  'use strict';

  var box = document.getElementById('replay');
  if (!box) return;
  var screen = box.querySelector('.replay-screen');
  var toggle = box.querySelector('[data-replay="toggle"]');
  var again = box.querySelector('[data-replay="again"]');
  var lines = [].slice.call(box.querySelectorAll('.replay-script li')).map(function (li) {
    return { kind: li.getAttribute('data-kind'), text: li.textContent };
  });
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // Every line is laid out from the start, and lines not yet shown are only hidden,
  // so the box never changes height (no layout shift) as the replay runs.
  for (var i = 0; i < lines.length; i++) {
    var span = document.createElement('span');
    span.className = 'rl rl-' + lines[i].kind;
    span.textContent = lines[i].text + '\n';
    screen.appendChild(span);
  }
  var spans = screen.children;
  function draw(n) {
    for (var i = 0; i < spans.length; i++) spans[i].classList.toggle('rl-hidden', i >= n);
  }

  if (reduce) { draw(lines.length); toggle.hidden = true; again.hidden = true; return; }
  draw(0);

  var shown = 0, timer = 0, playing = true, visible = false;
  var DELAY = { call: 1100, meta: 500, result: 900, note: 1400 };

  function step() {
    timer = 0;
    if (!playing || !visible || document.hidden) return;
    if (shown >= lines.length) { shown = 0; draw(0); timer = setTimeout(step, 600); return; }
    shown++;
    draw(shown);
    timer = setTimeout(step, shown >= lines.length ? 3500 : DELAY[lines[shown] ? lines[shown].kind : 'note'] || 900);
  }

  function resume() { if (!timer && playing && visible && !document.hidden) timer = setTimeout(step, 400); }
  function halt() { clearTimeout(timer); timer = 0; }

  function setPlaying(on) {
    playing = on;
    toggle.textContent = on ? 'Pause' : 'Play';
    toggle.setAttribute('aria-pressed', String(!on));
    on ? resume() : halt();
  }

  toggle.addEventListener('click', function () { setPlaying(!playing); });
  again.addEventListener('click', function () { halt(); shown = 0; draw(0); setPlaying(true); });
  document.addEventListener('visibilitychange', function () { document.hidden ? halt() : resume(); });
  if ('IntersectionObserver' in window) {
    new IntersectionObserver(function (es) { visible = es[0].isIntersecting; visible ? resume() : halt(); }, { threshold: 0.3 }).observe(box);
  } else { visible = true; resume(); }
})();
