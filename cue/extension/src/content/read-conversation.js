// §5.3. Reads the Tinder web conversation out of the DOM and reports it.
//
// > Content script reads the DOM directly — real text, real ordering, real
// > sender attribution, no OCR guesswork. By far the cleanest capture path in
// > the system.
//
// This file is the only code in the extension with access to the page, so it is
// the only place a write could originate. It does not write. There is no
// `execCommand`, no synthetic `KeyboardEvent`, no `dispatchEvent`, no
// `innerHTML`, no `.click()` — and the root project's `verifyContentScriptReadOnly`
// task fails the build if any of those appear here (§2.1). The extension holds no
// permission to inject into inputs either; the side panel renders drafts and you
// paste them yourself.

/**
 * Tinder's markup is generated and its class names change without notice.
 *
 * So attribution does not depend on any single selector. The message list is
 * found by a small ladder of candidates, and each row's sender comes from the
 * `msg--received` / `msg--sent` distinction Tinder has kept stable for years,
 * falling back to the same left/right geometry the Android app uses (§4.2) when
 * the class names have moved again.
 */
const MESSAGE_LIST_SELECTORS = [
  '[class*="msgList"]',
  '[aria-label*="Conversation"]',
  '.messageList',
  'main [role="log"]',
];

const MESSAGE_ROW_SELECTORS = [
  '[class*="msg--"]',
  '[class*="message"]',
  'li',
];

const SENT_MARKERS = ['msg--sent', 'msg--outgoing', 'sent'];
const RECEIVED_MARKERS = ['msg--received', 'msg--incoming', 'received'];

function findMessageList() {
  for (const selector of MESSAGE_LIST_SELECTORS) {
    const node = document.querySelector(selector);
    if (node) return node;
  }
  return null;
}

function findRows(container) {
  for (const selector of MESSAGE_ROW_SELECTORS) {
    const rows = Array.from(container.querySelectorAll(selector));
    if (rows.length > 0) return rows;
  }
  return [];
}

function classList(node) {
  // `className` is a string for HTML elements and an SVGAnimatedString for SVG;
  // reading `classList` avoids caring which.
  return Array.from(node.classList || []).join(' ').toLowerCase();
}

/**
 * ME or THEM for one row.
 *
 * Returns a confidence alongside the answer for the same reason §4.2 does: a
 * guess that knows it is a guess can be shown to the user instead of poisoning
 * the corpus. `null` means the row is not a message at all.
 */
function attribute(row, containerWidth) {
  const marks = classList(row);
  if (SENT_MARKERS.some((mark) => marks.includes(mark))) {
    return { sender: 'ME', confidence: 1 };
  }
  if (RECEIVED_MARKERS.some((mark) => marks.includes(mark))) {
    return { sender: 'THEM', confidence: 1 };
  }

  // Geometry, exactly as §4.2 does it on Android: within 15% of one edge and
  // not the other is a decision.
  const box = row.getBoundingClientRect();
  if (!containerWidth || box.width === 0) return null;
  const margin = containerWidth * 0.15;
  const rightGap = containerWidth - box.right;
  const leftGap = box.left;

  if (rightGap <= margin && leftGap > margin) return { sender: 'ME', confidence: 0.9 };
  if (leftGap <= margin && rightGap > margin) return { sender: 'THEM', confidence: 0.9 };
  return { sender: rightGap < leftGap ? 'ME' : 'THEM', confidence: 0.5 };
}

const CHROME_TEXT = new Set([
  'send', 'sent', 'delivered', 'read', 'seen', 'gif', 'type a message',
  'send a message', 'unmatch', 'report', 'you matched', 'say something',
]);

const TIMESTAMP = /^(\d{1,2}:\d{2}\s?(am|pm)?|today|yesterday|now|\d+\s?[mhdw](\sago)?)$/i;

function messageText(row) {
  const text = (row.innerText || row.textContent || '').trim();
  if (!text) return null;
  const lower = text.toLowerCase();
  if (CHROME_TEXT.has(lower)) return null;
  if (TIMESTAMP.test(text)) return null;
  return text.replace(/\s+/g, ' ');
}

/** Her name from the conversation header, for the pseudonym and nothing else. */
function readMatchName() {
  const candidates = [
    document.querySelector('[class*="chatTitle"]'),
    document.querySelector('h1'),
    document.querySelector('[class*="messageListHeader"] h1'),
  ];
  for (const node of candidates) {
    const text = node && (node.innerText || node.textContent || '').trim();
    if (text && text.length < 40) return text;
  }
  return null;
}

/**
 * §5.4. Her profile, when the pane is open beside the conversation.
 *
 * Tinder does not use Hinge's fixed prompt set, so there is nothing to match
 * against — what comes back is a bio and whatever attribute chips are visible.
 * Deliberately conservative: unlabelled chips go into `photoCaptions` rather
 * than being invented keys, for the same reason the Android parser refuses to
 * guess (§5.4).
 */
function readProfile() {
  const bioNode = document.querySelector('[class*="bio"], [class*="Bio"]');
  const bio = bioNode && (bioNode.innerText || '').trim();
  const chips = Array.from(document.querySelectorAll('[class*="Bdrs"] [class*="Typs"]'))
    .map((node) => (node.innerText || '').trim())
    .filter((text) => text.length > 0 && text.length < 40)
    .slice(0, 12);

  return {
    displayName: readMatchName(),
    age: null,
    bio: bio || null,
    prompts: [],
    attributes: {},
    photoCaptions: chips,
    capturedAt: Date.now(),
  };
}

function readConversation() {
  const container = findMessageList();
  if (!container) {
    return { ok: false, reason: 'No conversation is open' };
  }
  const containerWidth = container.getBoundingClientRect().width;
  const rows = findRows(container);

  const messages = [];
  for (const row of rows) {
    const text = messageText(row);
    if (!text) continue;
    const attribution = attribute(row, containerWidth);
    if (!attribution) continue;
    // A row that contains another message row would duplicate its text.
    if (row.querySelector(MESSAGE_ROW_SELECTORS[0])) continue;

    messages.push({
      id: `dom:${messages.length}`,
      conversationId: '',
      sender: attribution.sender,
      text,
      sentAt: null,
      sequence: messages.length,
      attributionConfidence: attribution.confidence,
    });
  }

  if (messages.length === 0) {
    return { ok: false, reason: 'The conversation is open but empty' };
  }

  const name = readMatchName();
  return {
    ok: true,
    conversationId: name ? `tinder:${name.toLowerCase().replace(/[^a-z0-9]/g, '')}` : `tinder:${Date.now()}`,
    platform: 'TINDER',
    matchName: name,
    profile: readProfile(),
    messages,
    capturedAt: Date.now(),
    lowConfidenceCount: messages.filter((message) => message.attributionConfidence < 0.8).length,
  };
}

chrome.runtime.onMessage.addListener((request, _sender, respond) => {
  if (request && request.type === 'cue:read-conversation') {
    try {
      respond(readConversation());
    } catch (error) {
      respond({ ok: false, reason: String(error && error.message) });
    }
    return true;
  }
  return false;
});
