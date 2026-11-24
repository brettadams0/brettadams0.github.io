(() => {
  const receipt = document.getElementById('receipt');
  const requested = new URLSearchParams(location.search).get('alert');
  if (requested !== null && requested !== receipt.dataset.alert) {
    receipt.hidden = true;
    document.getElementById('missing-alert').hidden = false;
    return;
  }
  const button = document.getElementById('check-snapshot');
  const status = document.getElementById('check-result');
  button.hidden = false;
  button.addEventListener('click', async () => {
    button.disabled = true;
    status.textContent = 'Checking the current public release…';
    try {
      const options = { cache: 'no-store', signal: AbortSignal.timeout(15000) };
      const manifest = await fetch(document.getElementById('manifest').href, options);
      if (!manifest.ok) throw new Error('fetch');
      const published = (await manifest.json()).financial?.revenue;
      const snapshotUrl = document.getElementById('snapshot').href;
      if (published?.revision !== receipt.dataset.revision || published.sha256 !== receipt.dataset.sha256 ||
          new URL(published.file, new URL('../../', manifest.url)).href !== snapshotUrl) {
        status.textContent = 'The current manifest no longer identifies this snapshot. The figures above describe the saved release only.';
        return;
      }
      const response = await fetch(snapshotUrl, options);
      if (!response.ok) throw new Error('fetch');
      const bytes = await response.arrayBuffer();
      const hash = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes));
      const digest = Array.from(hash, byte => byte.toString(16).padStart(2, '0')).join('');
      status.textContent = digest === receipt.dataset.sha256
        ? 'Verified. The downloaded bytes match the manifest and this investigation.'
        : 'Checksum mismatch. The downloaded bytes cannot verify this investigation.';
    } catch {
      status.textContent = 'Verification unavailable. Check your connection and retry, or run the Python verifier on previously saved files.';
    } finally {
      button.disabled = false;
    }
  });
})();
