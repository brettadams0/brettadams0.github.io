// A classic entry point can report a failed module download instead of leaving dead controls.
import('./app.js?v=3').catch(() => {
  document.getElementById('loading-title').textContent = 'The demo could not load';
  document.getElementById('loading-detail').textContent = 'Check your connection and try again.';
  document.getElementById('run-state').textContent = 'Offline';
  document.getElementById('goal').textContent = 'The interface could not load. Retry to reload the page.';
  document.getElementById('lab').setAttribute('aria-busy', 'false');
  const retry = document.getElementById('retry');
  retry.hidden = false;
  retry.addEventListener('click', () => location.reload());
});
