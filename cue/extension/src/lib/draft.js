// §6 and §7 for the browser, ported from :core:draft.
//
// Stage rules, BM25 retrieval, the prompt shape, the distinctness check and both
// hard gates. Same thresholds as the Kotlin, and the same asymmetry: an off-voice
// draft ships with a badge, an ungrounded one does not ship.

import { normalize, wordCount } from './text.js';
import { compile, maxDraftWords, verifyStyle } from './voice.js';

// -- stopwords and stemming (Stopwords.kt) ---------------------------------

export const STOPWORDS = new Set([
  'a', 'about', 'above', 'after', 'again', 'all', 'am', 'an', 'and', 'any',
  'are', "aren't", 'as', 'at', 'be', 'because', 'been', 'before', 'being',
  'below', 'between', 'both', 'but', 'by', 'can', "can't", 'cannot',
  'could', "couldn't", 'did', "didn't", 'do', 'does', "doesn't", 'doing',
  "don't", 'down', 'during', 'each', 'few', 'for', 'from', 'further',
  'had', "hadn't", 'has', "hasn't", 'have', "haven't", 'having', 'he',
  'her', 'here', 'hers', 'herself', 'him', 'himself', 'his', 'how', 'i',
  "i'd", "i'll", "i'm", "i've", 'if', 'in', 'into', 'is', "isn't", 'it',
  "it's", 'its', 'itself', 'just', "let's", 'me', 'more', 'most', 'my',
  'myself', 'no', 'nor', 'not', 'of', 'off', 'on', 'once', 'only', 'or',
  'other', 'ought', 'our', 'ours', 'ourselves', 'out', 'over', 'own',
  'same', 'she', 'should', "shouldn't", 'so', 'some', 'such', 'than',
  'that', "that's", 'the', 'their', 'theirs', 'them', 'themselves',
  'then', 'there', 'these', 'they', 'this', 'those', 'through', 'to',
  'too', 'under', 'until', 'up', 'very', 'was', "wasn't", 'we', 'were',
  'what', 'when', 'where', 'which', 'while', 'who', 'whom', 'why',
  'with', "won't", 'would', "wouldn't", 'you', 'your', 'yours',
  'yourself', 'yourselves', 'yeah', 'yep', 'nah', 'ok', 'okay', 'im',
  'u', 'ur', 'got', 'get', 'go', 'going', 'gonna', 'wanna', 'like',
  'really', 'actually', 'still', 'even', 'also', 'much', 'many', 'one',
  'two', 'way', 'thing', 'things', 'something', 'anything', 'nothing',
  'someone', 'anyone', 'everyone', 'know', 'think', 'want', 'need',
  'make', 'made', 'take', 'took', 'come', 'came', 'see', 'saw', 'say',
  'said', 'tell', 'told', 'look', 'looks', 'feel', 'feels', 'sounds',
  'sound', 'good', 'bad', 'nice', 'great', 'sure', 'maybe', 'well',
  'now', 'today', 'tomorrow', 'tonight', 'yesterday', 'time',
  'day', 'week', 'weekend', 'month', 'year', 'always', 'never', 'ever',
  'back', 'around', 'though', 'yet', 'since',
  'lot', 'lots', 'bit', 'kind', 'sort', 'stuff', 'loads', 'plenty',
  'couple', 'bunch', 'whole', 'half', 'part', 'rest', 'end',
]);

export function stem(word) {
  if (word.length <= 3) return word;
  if (word.endsWith('ies') && word.length > 4) return `${word.slice(0, -3)}y`;
  if (word.endsWith('sses')) return word.slice(0, -2);
  if (word.endsWith('s') && !word.endsWith('ss') && !word.endsWith('us')) return word.slice(0, -1);
  if (word.endsWith('ing') && word.length > 5) return undouble(word.slice(0, -3));
  if (word.endsWith('ed') && word.length > 4) return undouble(word.slice(0, -2));
  return word;
}

// "swimming" stems to "swimm" without this, and §7.2 then rejects a draft about
// swimming against a profile that says she cannot swim.
function undouble(root) {
  if (root.length < 3) return root;
  const last = root[root.length - 1];
  const previous = root[root.length - 2];
  const isVowel = 'aeiou'.includes(last);
  if (last === previous && !isVowel && !'ls'.includes(last)) return root.slice(0, -1);
  return root;
}

const WORD_PATTERN = /[\p{L}\p{N}]+(?:['’]\p{L}+)*/gu;

export function contentTerms(text) {
  return Array.from(text.matchAll(WORD_PATTERN), (match) => normalize(match[0]))
    .filter((word) => !STOPWORDS.has(word))
    .filter((word) => /\p{L}/u.test(word))
    .map(stem);
}

// -- §4.3 BM25 --------------------------------------------------------------

export class Bm25Index {
  constructor(corpus, { k1 = 1.2, b = 0.75 } = {}) {
    this.k1 = k1;
    this.b = b;
    this.documents = corpus.map((message) => {
      const text = [message.precedingTheirMessage, message.text].filter(Boolean).join(' ');
      const terms = contentTerms(text);
      const frequencies = new Map();
      for (const term of terms) frequencies.set(term, (frequencies.get(term) || 0) + 1);
      return { message, frequencies, length: terms.length };
    });
    this.averageLength =
      this.documents.length === 0
        ? 0
        : this.documents.reduce((sum, doc) => sum + doc.length, 0) / this.documents.length;

    this.postings = new Map();
    this.documents.forEach((doc, index) => {
      for (const term of doc.frequencies.keys()) {
        if (!this.postings.has(term)) this.postings.set(term, []);
        this.postings.get(term).push(index);
      }
    });
  }

  search(query, limit = 5, stage = null) {
    if (this.documents.length === 0) return [];
    const terms = Array.from(new Set(contentTerms(query)));
    if (terms.length === 0) return [];

    const scores = new Map();
    for (const term of terms) {
      const matching = this.postings.get(term);
      if (!matching) continue;
      const idf = Math.log(
        1 + (this.documents.length - matching.length + 0.5) / (matching.length + 0.5),
      );
      for (const index of matching) {
        const doc = this.documents[index];
        const tf = doc.frequencies.get(term);
        const norm =
          tf + this.k1 * (1 - this.b + (this.b * doc.length) / Math.max(1, this.averageLength));
        scores.set(index, (scores.get(index) || 0) + (idf * tf * (this.k1 + 1)) / norm);
      }
    }

    return Array.from(scores.entries())
      .map(([index, score]) => {
        const { message } = this.documents[index];
        const weight = 1 + 0.5 * ((message.weight || 1) - 1);
        const stageBonus = stage && message.stage === stage ? 1.25 : 1;
        return { message, score: score * weight * stageBonus };
      })
      .sort((a, b) => b.score - a.score || a.message.id.localeCompare(b.message.id))
      .slice(0, limit);
  }
}

// -- §6.3 stage classification ----------------------------------------------

const MILLIS_PER_DAY = 86400000;

const LOGISTICS_TERMS = [
  'meet up', 'meet', 'grab a drink', 'grab drinks', 'drinks', 'coffee',
  'dinner', 'lunch', 'brunch', 'date', 'hang out', 'hangout',
  'free this week', 'free next week', 'your week look', 'are you around',
  'this weekend', 'next weekend', 'friday', 'saturday', 'sunday',
  'what time', 'see you', 'come with me', 'go together',
];

function median(values) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
}

export function stageSignals(context, now = Date.now()) {
  const hers = context.messages.filter((message) => message.sender === 'THEM');
  const counts = hers.map((message) => wordCount(message.text));
  const recent = counts.slice(-4);
  const earlier = counts.slice(0, -4).slice(-4);
  const lastTheirs = context.lastTheirMessageAt || null;

  return {
    messageCount: context.messages.length,
    daysSinceTheirLast: lastTheirs === null ? null : (now - lastTheirs) / MILLIS_PER_DAY,
    herRecentMedianWords: median(recent),
    herEarlierMedianWords: earlier.length < 4 ? 0 : median(earlier),
    herQuestionRate:
      hers.length === 0 ? 0 : hers.filter((message) => message.text.includes('?')).length / hers.length,
    logisticsMentioned: context.messages.some((message) => {
      const text = message.text.toLowerCase();
      return LOGISTICS_TERMS.some((term) =>
        new RegExp(`(?<!\\p{L})${term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?!\\p{L})`, 'u').test(text),
      );
    }),
  };
}

/**
 * §6.3's rules, with the same deliberate reordering as the Kotlin: silence is
 * checked before message count, so a four-message thread abandoned five days ago
 * is STALLING rather than EARLY_RAPPORT.
 */
export function classifyStage(signals) {
  if (signals.messageCount === 0) return 'OPENER';
  if (signals.daysSinceTheirLast !== null && signals.daysSinceTheirLast > 7) return 'DEAD';
  if (signals.daysSinceTheirLast !== null && signals.daysSinceTheirLast > 3) return 'STALLING';
  if (signals.messageCount < 6) return 'EARLY_RAPPORT';
  if (
    signals.herEarlierMedianWords > 0 &&
    signals.herRecentMedianWords < signals.herEarlierMedianWords * 0.6
  ) {
    return 'STALLING';
  }
  if (signals.messageCount >= 8 && signals.herQuestionRate > 0.3 && !signals.logisticsMentioned) {
    return 'READY_TO_ASK';
  }
  return 'ESTABLISHED';
}

// -- §6.1 strategies ---------------------------------------------------------

export const STRATEGIES = {
  OPENER: [
    { id: 'OPENER_CALLBACK', label: 'Specific callback', instruction: 'Respond to exactly one thing she wrote in her profile. Name it directly.' },
    { id: 'OPENER_CHALLENGE', label: 'Playful challenge', instruction: 'Disagree lightly with something in her profile, or set her a small challenge about it.' },
    { id: 'OPENER_BRIDGE', label: 'Shared interest', instruction: 'Connect one thing in her profile to something you plainly have in common, then ask about hers.' },
  ],
  EARLY_RAPPORT: [
    { id: 'RAPPORT_BUILD', label: 'Build on her thread', instruction: 'Continue the topic she just raised and add something of your own to it.' },
    { id: 'RAPPORT_REDIRECT', label: 'New topic', instruction: 'Acknowledge her last message briefly, then move to a different topic from her profile.' },
    { id: 'RAPPORT_ESCALATE', label: 'Light escalation', instruction: 'Reply with a little more warmth or teasing than the thread currently has. No plans, no meeting.' },
  ],
  ESTABLISHED: [
    { id: 'ESTABLISHED_DEEPEN', label: 'Go deeper', instruction: 'Ask about the reason behind what she just said, not the fact of it.' },
    { id: 'ESTABLISHED_LOGISTICS', label: 'Toward logistics', instruction: 'Move the conversation toward something you could actually do together, without asking yet.' },
    { id: 'ESTABLISHED_CALLBACK', label: 'Earlier callback', instruction: 'Return to something she said earlier in the conversation and pick it back up.' },
  ],
  READY_TO_ASK: [
    { id: 'ASK_DIRECT', label: 'Direct ask', instruction: 'Propose one specific plan — an activity and a rough day. Make it easy to say yes to.' },
    { id: 'ASK_SOFT', label: 'Soft ask', instruction: 'Float an idea for meeting without pinning a time. Leave her room to shape it.' },
    { id: 'ASK_AVAILABILITY', label: 'Availability probe', instruction: "Ask what her week looks like, in a way that clearly points at meeting up." },
  ],
  STALLING: [
    { id: 'REVIVAL_LOW_STAKES', label: 'Low-stakes revival', instruction: 'Send something that costs her nothing to answer. Do not mention the silence.' },
    { id: 'REVIVAL_DIRECT', label: 'Direct re-engage', instruction: 'Acknowledge the gap lightly and give her one clear thing to respond to.' },
    { id: 'REVIVAL_CLOSE', label: 'Let it go', instruction: 'Close warmly and without complaint. No guilt, no question she has to answer.' },
  ],
  DEAD: [
    { id: 'REVIVAL_CLOSE', label: 'Let it go', instruction: 'Close warmly and without complaint. No guilt, no question she has to answer.' },
  ],
};

// -- §6.2 prompt -------------------------------------------------------------

export const MAX_CONTEXT_TOKENS = 1500;
const MAX_EXAMPLES = 5;
const MIN_EXAMPLES = 2;
const MAX_HISTORY = 6;
const MIN_HISTORY = 2;

function renderProfile(profile) {
  const lines = [];
  const identity = [profile.displayName, profile.age].filter(Boolean).join(', ');
  if (identity) lines.push(identity);
  for (const prompt of profile.prompts || []) lines.push(`${prompt.prompt} -> ${prompt.answer}`);
  if (profile.bio) lines.push(`bio: ${profile.bio}`);
  for (const [key, value] of Object.entries(profile.attributes || {})) lines.push(`${key}: ${value}`);
  for (const caption of profile.photoCaptions || []) lines.push(`in a photo: ${caption}`);
  if (lines.length === 0) lines.push('(nothing captured)');
  return lines.join('\n');
}

export function estimateTokens(text) {
  return Math.ceil(text.length / 4);
}

/** Constraints last, because small models weight recent tokens heavily (§6.2). */
export function buildPrompt({ context, strategy, examples, profile, extraConstraints = [] }) {
  let exampleCount = Math.min(examples.length, MAX_EXAMPLES);
  let historyCount = MAX_HISTORY;

  for (;;) {
    const history = context.messages.slice(-historyCount);
    const lines = [];
    lines.push('HER PROFILE', renderProfile(context.profile), '');
    if (history.length > 0) {
      lines.push('CONVERSATION SO FAR');
      for (const message of history) {
        lines.push(`${message.sender === 'ME' ? 'ME' : 'HER'}: ${message.text.trim()}`);
      }
      lines.push('');
    }
    const used = examples.slice(0, exampleCount);
    if (used.length > 0) {
      lines.push('HOW I WRITE (real messages I sent, copy this register exactly)');
      for (const example of used) {
        if (example.precedingTheirMessage) {
          lines.push(`  when she said: ${example.precedingTheirMessage.trim()}`);
        }
        lines.push(`  I wrote: ${example.text.trim()}`);
      }
      lines.push('');
    }
    lines.push('WRITE THE NEXT MESSAGE FROM ME.', strategy.instruction, '');
    lines.push('RULES');
    lines.push(`- At most ${maxDraftWords(profile)} words.`);
    lines.push('- One idea. Not two.');
    lines.push('- Only mention things written above. Invent nothing about her.');
    lines.push('- Output the message only. No greeting, no explanation, no quotes.');
    for (const constraint of extraConstraints) lines.push(`- ${constraint}`);

    const text = lines.join('\n').trim();
    const tokens = estimateTokens(text);
    const canTrim = exampleCount > MIN_EXAMPLES || historyCount > MIN_HISTORY;
    if (tokens <= MAX_CONTEXT_TOKENS || !canTrim) {
      return { text, estimatedTokens: tokens, examplesUsed: exampleCount, historyUsed: history.length };
    }
    if (historyCount > MIN_HISTORY) historyCount -= 1;
    else exampleCount -= 1;
  }
}

// -- §6.4 distinctness --------------------------------------------------------

export const DISTINCTNESS_THRESHOLD = 0.6;

export function similarity(a, b) {
  const left = new Set(contentTerms(a));
  const right = new Set(contentTerms(b));
  if (left.size === 0 && right.size === 0) return 1;
  if (left.size === 0 || right.size === 0) return 0;
  let shared = 0;
  for (const term of left) if (right.has(term)) shared += 1;
  return shared / (left.size + right.size - shared);
}

export function tooSimilar(drafts, threshold = DISTINCTNESS_THRESHOLD) {
  const flagged = [];
  for (let i = 0; i < drafts.length; i += 1) {
    for (let j = 0; j < i; j += 1) {
      if (flagged.includes(j)) continue;
      if (similarity(drafts[i], drafts[j]) > threshold) {
        flagged.push(i);
        break;
      }
    }
  }
  return flagged;
}

// -- §7.2 grounding -----------------------------------------------------------

// Common English, generated from :core:voice's Lexicons.GENERIC_ENGLISH — every
// entry at or above GroundingGate.COMMON_WORD_THRESHOLD (1e-4). Using a word this
// common asserts nothing, so §7.2 does not ask where it came from. Regenerate
// rather than edit: the Kotlin table is the source.
const GENERIC_COMMON = new Set([
  'a', 'about', 'after', 'all', 'also', 'am', 'an', 'and', 'any', 'anything', 'appear', 'are',
  'as', 'ask', 'at', 'back', 'be', 'because', 'been', 'believe', 'big', 'bring', 'build',
  'but', 'buy', 'by', 'can', 'change', 'come', 'consider', 'continue', 'could', 'create',
  'cut', 'day', 'did', 'die', 'do', 'does', 'drink', 'drive', 'eat', 'even', 'everything',
  'fall', 'feel', 'find', 'first', 'follow', 'for', 'from', 'get', 'give', 'go', 'good',
  'great', 'grow', 'had', 'happen', 'has', 'have', 'he', 'hear', 'help', 'her', 'here', 'him',
  'his', 'hold', 'how', 'i', 'if', 'in', 'include', 'into', 'is', 'it', 'its', 'just', 'keep',
  'kill', 'know', 'last', 'lead', 'learn', 'leave', 'let', 'like', 'little', 'live', 'look',
  'lose', 'love', 'make', 'many', 'maybe', 'me', 'mean', 'meet', 'most', 'move', 'much', 'my',
  'need', 'new', 'next', 'nice', 'night', 'no', 'not', 'nothing', 'now', 'of', 'offer',
  'okay', 'on', 'one', 'only', 'open', 'or', 'other', 'our', 'out', 'over', 'pay', 'people',
  'play', 'put', 'reach', 'read', 'really', 'remain', 'remember', 'run', 'say', 'see', 'seem',
  'send', 'serve', 'set', 'she', 'show', 'sit', 'sleep', 'so', 'some', 'someone', 'something',
  'sorry', 'speak', 'spend', 'stand', 'start', 'stay', 'stop', 'sure', 'take', 'talk', 'tell',
  'than', 'thanks', 'that', 'the', 'their', 'them', 'then', 'there', 'these', 'they', 'think',
  'this', 'time', 'to', 'today', 'tomorrow', 'try', 'turn', 'two', 'understand', 'up', 'us',
  'use', 'very', 'wait', 'walk', 'want', 'was', 'watch', 'way', 'we', 'wear', 'week',
  'weekend', 'well', 'were', 'what', 'when', 'where', 'which', 'who', 'why', 'will', 'win',
  'with', 'work', 'would', 'write', 'yeah', 'year', 'you', 'your',
]);

const GENERIC_ABSTRACT = new Set([
  'answer', 'question', 'reason', 'idea', 'point', 'story', 'thing',
  'side', 'chance', 'guess', 'opinion', 'choice', 'option', 'problem',
  'difference', 'example', 'moment', 'minute', 'hour', 'second', 'order',
  'matter', 'case', 'fact', 'sense', 'name', 'number', 'word', 'words',
  'line', 'list', 'note', 'detail', 'details', 'version', 'attempt',
  'correct', 'wrong', 'right', 'true', 'false', 'better', 'best', 'worse',
  'worst', 'different', 'easy', 'hard', 'simple', 'weird', 'strange',
  'funny', 'serious', 'quiet', 'loud', 'fast', 'slow', 'early', 'late',
  'close', 'high', 'low', 'long', 'short', 'small', 'large', 'huge',
  'real', 'actual', 'usual', 'normal', 'fine', 'cool', 'fair', 'solid',
  'decent', 'terrible', 'awful', 'unreal', 'wild', 'mad', 'insane',
  'honest', 'genuine', 'proper', 'exact', 'complete',
  'agree', 'disagree', 'explain', 'explaining', 'mean', 'means',
  'sound', 'sounds', 'seem', 'seems',
]);

const PROPOSAL_TERMS = new Set([
  'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday',
  'sunday', 'weekend', 'weekday', 'morning', 'afternoon', 'evening',
  'tonight', 'tomorrow', 'later', 'sometime', 'soon',
  'coffee', 'drink', 'drinks', 'dinner', 'lunch', 'brunch', 'walk',
  'food', 'beer', 'wine', 'bar', 'park', 'place', 'spot', 'plan',
  'plans', 'meet', 'hang', 'grab', 'free', 'busy', 'around',
]);

const MODIFIER_SUFFIXES = ['ly', 'able', 'ible', 'ous', 'ive', 'ful', 'less', 'est'];
const IDIOM_MIN_COUNT = 3;

function contextVocabulary(context) {
  const sources = [];
  const profile = context.profile || {};
  if (profile.bio) sources.push(profile.bio);
  if (profile.displayName) sources.push(profile.displayName);
  for (const prompt of profile.prompts || []) sources.push(prompt.prompt, prompt.answer);
  for (const [key, value] of Object.entries(profile.attributes || {})) sources.push(key, value);
  for (const caption of profile.photoCaptions || []) sources.push(caption);
  for (const message of context.messages) sources.push(message.text);

  const vocabulary = new Set();
  for (const source of sources) {
    for (const match of source.matchAll(WORD_PATTERN)) {
      const word = normalize(match[0]);
      vocabulary.add(word);
      vocabulary.add(stem(word));
    }
  }
  return vocabulary;
}

function isSentenceInitial(text, index) {
  let i = index - 1;
  while (i >= 0 && /[\s"']/.test(text[i])) i -= 1;
  if (i < 0) return true;
  return '.!?…'.includes(text[i]);
}

/**
 * §7.2, zero tolerance. Returns the specifics in `draft` that are not reachable
 * from `context`; an empty array means ship.
 */
export function checkGrounding(draft, context, profile) {
  const vocabulary = contextVocabulary(context);
  const yours = profile.vocabulary || {};
  const ungrounded = [];

  for (const match of draft.matchAll(WORD_PATTERN)) {
    const raw = match[0];
    const word = normalize(raw);

    if (/^\d+$/.test(word)) continue;
    if (word.length < 3) continue;
    if (PROPOSAL_TERMS.has(word)) continue;

    const properNoun =
      raw[0] === raw[0].toUpperCase() &&
      /\p{L}/u.test(raw[0]) &&
      !isSentenceInitial(draft, match.index) &&
      raw !== 'I';

    if (!properNoun) {
      if (STOPWORDS.has(word)) continue;
      if (GENERIC_COMMON.has(word)) continue;
      if (GENERIC_ABSTRACT.has(word)) continue;
      if (MODIFIER_SUFFIXES.some((suffix) => word.length > suffix.length + 2 && word.endsWith(suffix))) {
        continue;
      }
      // Membership alone would let one use of "dog" license every future draft.
      if ((yours[word] || 0) >= IDIOM_MIN_COUNT) continue;
    }

    if (!vocabulary.has(stem(word)) && !vocabulary.has(word)) ungrounded.push(raw);
  }

  return Array.from(new Set(ungrounded));
}

// -- §7.3 escalation ----------------------------------------------------------

const MEETING_PROPOSALS = [
  'grab a drink', 'grab drinks', 'grab a coffee', 'grab coffee',
  'get a drink', 'get drinks', 'get coffee', 'go for a drink',
  'go for a walk', 'go for coffee', 'meet up', 'meet you', "let's meet",
  'we should meet', 'we should go', 'you should come', 'come over',
  'my place', 'your place', 'dinner sometime', 'take you out',
  'are you free', 'what are you doing this weekend', 'want to go out',
  "let's go out", 'on a date', 'grab food', 'grab dinner',
];

const SEXUAL_TERMS = [
  'sexy', 'naked', 'nudes', 'hook up', 'hookup', 'in bed',
  'netflix and chill', 'turn me on', 'turn you on', 'kiss you',
  'make out', 'sleep with', 'spend the night', 'take you home',
  'come home with me', 'in my bed',
];

function containsPhrase(haystack, phrase) {
  const escaped = phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(?<!\\p{L})${escaped}(?!\\p{L})`, 'u').test(haystack);
}

export function checkEscalation(draft, stage, context) {
  const text = draft.toLowerCase();

  if (stage === 'OPENER' || stage === 'EARLY_RAPPORT') {
    const proposal = MEETING_PROPOSALS.find((phrase) => containsPhrase(text, phrase));
    if (proposal) {
      return `Proposes meeting ("${proposal}") at ${stage}, before the conversation is established`;
    }
  }

  const sexual = SEXUAL_TERMS.find((phrase) => containsPhrase(text, phrase));
  if (!sexual) return null;

  // Her messages only. Your own escalation is not permission.
  const sheDid = context.messages
    .filter((message) => message.sender === 'THEM')
    .some((message) => SEXUAL_TERMS.some((phrase) => containsPhrase(message.text.toLowerCase(), phrase)));

  return sheDid ? null : `References "${sexual}" before she has`;
}

// -- the pipeline -------------------------------------------------------------

const MAX_ATTEMPTS = 3;
const MAX_GROUNDING_FAILURES = 2;

/**
 * §6–§7 for one conversation. `generate` is `async (prompt, { seed, temperature })
 * => string`, or null for the no-model path (§13's WebGPU fallback).
 */
export async function draftAll({ context, profile, corpus, generate }) {
  const signals = stageSignals(context, context.nowMillis || Date.now());
  const stage = classifyStage(signals);
  const strategies = STRATEGIES[stage] || STRATEGIES.ESTABLISHED;
  const index = new Bm25Index(corpus);

  const query = [
    context.messages.filter((message) => message.sender === 'THEM').slice(-1)[0]?.text,
    (context.profile.prompts || [])[0]?.answer,
    context.profile.bio,
  ]
    .filter(Boolean)
    .join(' ');
  const examples = index.search(query, MAX_EXAMPLES, stage).map((result) => result.message);

  const drafts = [];
  const suppressed = [];

  if (generate) {
    for (const strategy of strategies) {
      const outcome = await attemptVariant({ context, stage, strategy, examples, profile, generate });
      if (outcome.draft) drafts.push(outcome.draft);
      else suppressed.push(outcome.suppressed);
    }

    // §6.4: one retry per collapsed variant, at a higher temperature.
    const flagged = tooSimilar(drafts.map((draft) => draft.text));
    for (const position of flagged) {
      const others = drafts.filter((_, i) => i !== position).map((draft) => draft.text);
      const retry = await attemptVariant({
        context,
        stage,
        strategy: drafts[position].strategy,
        examples,
        profile,
        generate,
        extraConstraints: [
          `Take a different angle from these, and do not reuse their words: ${others.join(' | ')}`,
        ],
        seedOffset: 977,
      });
      if (retry.draft) drafts[position] = retry.draft;
    }
  }

  return {
    stage,
    drafts,
    suppressed,
    readyToAsk: stage === 'READY_TO_ASK',
    calibrating: (profile.sampleCount || 0) < 50,
  };
}

async function attemptVariant({
  context,
  stage,
  strategy,
  examples,
  profile,
  generate,
  extraConstraints = [],
  seedOffset = 0,
}) {
  const constraints = [...extraConstraints];
  let groundingFailures = 0;
  let bestOffVoice = null;

  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt += 1) {
    const prompt = buildPrompt({ context, strategy, examples, profile, extraConstraints: constraints });
    let raw;
    try {
      raw = await generate(prompt.text, {
        seed: hashSeed(strategy.id) + attempt + seedOffset,
        temperature: 0.8 + 0.15 * attempt,
        maxTokens: Math.trunc(maxDraftWords(profile) * 1.6) + 8,
      });
    } catch (error) {
      return {
        suppressed: { strategy, reason: String(error && error.message) || 'Model unavailable' },
      };
    }

    const compiled = compile(raw, profile);
    if (compiled.needsRegeneration) {
      constraints.push('Write a complete sentence. Do not open with filler.');
      continue;
    }

    const ungrounded = checkGrounding(compiled.text, context, profile);
    const escalation = checkEscalation(compiled.text, stage, context);
    const style = verifyStyle(compiled.text, profile);

    const draft = {
      id: `${context.conversationId}:${strategy.id}`,
      strategy,
      rawModelOutput: raw,
      text: compiled.text,
      transforms: compiled.transforms,
      gates: { style, ungrounded, escalation, attempts: attempt + 1 },
      offVoice: style.length > 0,
    };

    if (ungrounded.length > 0) {
      groundingFailures += 1;
      if (groundingFailures >= MAX_GROUNDING_FAILURES) {
        return {
          suppressed: {
            strategy,
            reason: `Invented ${ungrounded.join(', ')} twice; nothing shipped`,
          },
        };
      }
      constraints.push(`Do not mention: ${ungrounded.join(', ')}.`);
      continue;
    }
    if (escalation) {
      constraints.push('Do not suggest meeting up or anything physical.');
      continue;
    }
    if (style.length === 0) return { draft };
    if (!bestOffVoice || style.length < bestOffVoice.gates.style.length) bestOffVoice = draft;
  }

  if (bestOffVoice) return { draft: bestOffVoice };
  return {
    suppressed: { strategy, reason: `No candidate passed the gates in ${MAX_ATTEMPTS} attempts` },
  };
}

function hashSeed(id) {
  let hash = 0;
  for (const char of id) hash = (hash * 31 + char.charCodeAt(0)) % 100000;
  return hash;
}
