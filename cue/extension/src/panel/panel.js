// The side panel: read the page, draft three replies, offer a copy button.
//
// §2.1 is visible in the absence of a send path. The panel can read the tab and
// write to its own DOM. It has no permission to inject into the page, and the
// clipboard is where a draft's journey ends.

import { draftAll } from '../lib/draft.js';
import { decodeProfile, isCalibrated, loadCorpus, loadProfile, saveProfile } from '../lib/profile.js';
import { createEngine } from '../llm/webllm.js';

const statusNode = document.querySelector('#status');
const bannersNode = document.querySelector('#banners');
const draftsNode = document.querySelector('#drafts');
const profileSummaryNode = document.querySelector('#profile-summary');

let engine = null;
let engineReason = null;

async function currentTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  return tab;
}

function setStatus(text) {
  statusNode.textContent = text;
}

function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}

function banner(text, quiet = false) {
  const node = document.createElement('div');
  node.className = quiet ? 'banner quiet' : 'banner';
  node.textContent = text;
  bannersNode.appendChild(node);
}

function draftCard(draft) {
  const card = document.createElement('div');
  card.className = 'draft';

  const header = document.createElement('header');
  const label = document.createElement('span');
  label.className = 'label';
  label.textContent = draft.strategy.label;
  header.appendChild(label);

  if (draft.offVoice) {
    const badge = document.createElement('span');
    badge.className = 'badge';
    badge.textContent = 'off-voice';
    header.appendChild(badge);
  }
  card.appendChild(header);

  const editor = document.createElement('textarea');
  editor.rows = Math.max(2, Math.ceil(draft.text.length / 44));
  editor.value = draft.text;
  card.appendChild(editor);

  const actions = document.createElement('div');
  actions.className = 'actions';

  const copy = document.createElement('button');
  copy.type = 'button';
  copy.textContent = 'Copy';
  copy.addEventListener('click', async () => {
    await navigator.clipboard.writeText(editor.value);
    copy.textContent = 'Copied — paste it yourself';
    setTimeout(() => {
      copy.textContent = 'Copy';
    }, 2000);
  });
  actions.appendChild(copy);

  card.appendChild(actions);
  return card;
}

function suppressedCard(variant) {
  const node = document.createElement('div');
  node.className = 'suppressed';
  node.textContent = `${variant.strategy.label} — held back: ${variant.reason.toLowerCase()}`;
  return node;
}

async function ensureEngine() {
  if (engine) return engine;
  engine = await createEngine((message) => setStatus(message));
  engineReason = createEngine.lastReason;
  return engine;
}

async function readConversation() {
  const tab = await currentTab();
  if (!tab?.id || !tab.url?.startsWith('https://tinder.com/')) {
    return { ok: false, reason: 'Open a Tinder conversation in this tab' };
  }
  try {
    return await chrome.tabs.sendMessage(tab.id, { type: 'cue:read-conversation' });
  } catch {
    return { ok: false, reason: 'Reload the Tinder tab so Cue can read it' };
  }
}

async function run() {
  clear(bannersNode);
  clear(draftsNode);
  setStatus('Reading the conversation…');

  const profile = await loadProfile();
  const corpus = await loadCorpus();
  renderProfileSummary(profile, corpus);

  const page = await readConversation();
  if (!page.ok) {
    setStatus(page.reason);
    return;
  }

  setStatus('Loading the model…');
  const loaded = await ensureEngine();
  if (!loaded) {
    // §13: no WebGPU, or no vendored library — say which, and carry on.
    banner(`${engineReason} Cue can still show what it read, but not draft.`, true);
  }

  const hers = page.messages.filter((message) => message.sender === 'THEM');
  const context = {
    conversationId: page.conversationId,
    platform: 'TINDER',
    profile: page.profile,
    messages: page.messages,
    capturedAt: page.capturedAt,
    nowMillis: Date.now(),
    // §5.3 gives real ordering but not real timestamps: Tinder's DOM shows
    // relative times. Treating "now" as her last message keeps the stage rules
    // from declaring a live conversation dead.
    lastTheirMessageAt: hers.length > 0 ? Date.now() : null,
  };

  setStatus(`${page.messages.length} messages read, on this machine.`);

  const result = await draftAll({
    context,
    profile,
    corpus,
    generate: loaded ? loaded.generate : null,
  });

  if (result.readyToAsk) {
    banner('She is asking questions and nobody has suggested meeting. This is the moment.');
  }
  if (result.stage === 'DEAD') {
    banner('Nothing recent from her. A warm close beats a fourth revival.');
  }
  if (result.calibrating) {
    banner(
      'No calibrated voice profile yet — export one from the Android app. ' +
        'Until then drafts use a generic casual baseline.',
      true,
    );
  }
  if (page.lowConfidenceCount > 0) {
    banner(
      `${page.lowConfidenceCount} messages could not be attributed to a side with confidence. ` +
        'Check the drafts read as replies to the right person.',
      true,
    );
  }

  for (const draft of result.drafts) draftsNode.appendChild(draftCard(draft));
  for (const variant of result.suppressed) draftsNode.appendChild(suppressedCard(variant));

  if (result.drafts.length === 0 && result.suppressed.length === 0) {
    setStatus(`${page.messages.length} messages read. Nothing to draft — ${result.stage}.`);
  }
}

function renderProfileSummary(profile, corpus) {
  if (!profile.sampleCount) {
    profileSummaryNode.textContent =
      'No profile imported. Drafts will use a generic casual baseline.';
    return;
  }
  profileSummaryNode.textContent =
    `${profile.sampleCount} messages, ${isCalibrated(profile) ? 'calibrated' : 'still calibrating'}. ` +
    `${corpus.length} examples for retrieval.` +
    (corpus.length === 0
      ? ' Without examples the browser drafts noticeably worse than the phone.'
      : '');
}

document.querySelector('#refresh').addEventListener('click', () => {
  run().catch((error) => setStatus(String(error && error.message)));
});

document.querySelector('#import-profile').addEventListener('click', async () => {
  const textarea = document.querySelector('#profile-json');
  try {
    const profile = decodeProfile(textarea.value);
    await saveProfile(profile);
    textarea.value = '';
    renderProfileSummary(profile, await loadCorpus());
    setStatus('Voice profile imported.');
  } catch (error) {
    setStatus(String(error.message));
  }
});

run().catch((error) => setStatus(String(error && error.message)));
