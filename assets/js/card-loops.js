/*
 * Card loops: a 4 s silent clip recorded from each live demo, over its card's
 * still screenshot. Nothing loads until a clip is needed: the <source>s carry
 * data-src, so the browser picks and fetches one only when the clip starts.
 *   data-loop="inview"  plays while half the card's shot is on screen;
 *   data-loop="hover"   plays while a fine pointer hovers or the card has focus.
 * Under prefers-reduced-motion nothing loads or plays; the screenshot stays.
 */
(function () {
  'use strict';

  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)');
  if (!reduce || reduce.matches) return;
  var fine = window.matchMedia('(pointer: fine)').matches;

  function arm(video) {
    if (video.getAttribute('data-armed')) return;
    video.setAttribute('data-armed', '1');
    var sources = video.querySelectorAll('source[data-src]');
    for (var i = 0; i < sources.length; i++) sources[i].src = sources[i].getAttribute('data-src');
    video.addEventListener('playing', function () { video.classList.add('is-playing'); });
    video.load();
  }

  function play(video) {
    if (reduce.matches || document.hidden) return;
    arm(video);
    var p = video.play();
    if (p && p.catch) p.catch(function () {});
  }

  function stop(video) {
    video.pause();
    video.classList.remove('is-playing');
  }

  var videos = document.querySelectorAll('video.shot-loop[data-loop]');
  var inview = [];
  var observer = 'IntersectionObserver' in window ? new IntersectionObserver(function (entries) {
    entries.forEach(function (e) { e.isIntersecting ? play(e.target) : stop(e.target); });
  }, { threshold: 0.5 }) : null;

  for (var i = 0; i < videos.length; i++) {
    (function (video) {
      if (video.getAttribute('data-loop') === 'inview') {
        if (observer) { observer.observe(video); inview.push(video); }
        return;
      }
      if (!fine) return; // hover clips need a hovering pointer; keyboard focus still plays them below
      var card = video.closest('[data-project]') || video.parentNode;
      card.addEventListener('mouseenter', function () { play(video); });
      card.addEventListener('mouseleave', function () { stop(video); });
      card.addEventListener('focusin', function () { play(video); });
      card.addEventListener('focusout', function () { stop(video); });
    })(videos[i]);
  }

  document.addEventListener('visibilitychange', function () {
    if (document.hidden) for (var j = 0; j < videos.length; j++) stop(videos[j]);
  });
  reduce.addEventListener && reduce.addEventListener('change', function () {
    if (reduce.matches) for (var j = 0; j < videos.length; j++) stop(videos[j]);
  });
})();
