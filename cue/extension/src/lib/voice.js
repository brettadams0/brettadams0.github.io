// §4.4's voice compiler, ported from :core:voice.
//
// Same order, same rules, same thresholds — see `VoiceCompiler.kt` for why each
// one exists. The comments here cover only what differs in JavaScript.

import {
  clauseBoundaries,
  countChar,
  emojiList,
  emojiRanges,
  firstLetterIndex,
  normalize,
  wordCount,
  words,
} from './text.js';

export const CONTRACTIONS = new Map([
  ['cannot', "can't"],
  ['can not', "can't"],
  ['do not', "don't"],
  ['does not', "doesn't"],
  ['did not', "didn't"],
  ['is not', "isn't"],
  ['are not', "aren't"],
  ['was not', "wasn't"],
  ['were not', "weren't"],
  ['have not', "haven't"],
  ['has not', "hasn't"],
  ['had not', "hadn't"],
  ['will not', "won't"],
  ['would not', "wouldn't"],
  ['should not', "shouldn't"],
  ['could not', "couldn't"],
  ['i am', "i'm"],
  ['you are', "you're"],
  ['we are', "we're"],
  ['they are', "they're"],
  ['it is', "it's"],
  ['that is', "that's"],
  ['there is', "there's"],
  ['what is', "what's"],
  ['how is', "how's"],
  ['i will', "i'll"],
  ['you will', "you'll"],
  ['we will', "we'll"],
  ['i have', "i've"],
  ['you have', "you've"],
  ['we have', "we've"],
  ['i would', "i'd"],
  ['you would', "you'd"],
  ['let us', "let's"],
]);

const FORBIDDEN_PHRASES = [
  "i'd love to",
  'i would love to',
  'that sounds amazing',
  'sounds amazing',
  "can't wait",
  'cant wait',
  'for sure',
  'let me know',
  'feel free to',
  'no worries',
  "that's awesome",
  "i'm so glad",
];

// Deleted only when absent from your corpus — §4.4's "lol (if absent from your
// corpus)", generalised. If you genuinely write "totally", removing it makes the
// draft sound less like you.
const FORBIDDEN_INTERJECTIONS = [
  'haha', 'hahaha', 'lol', 'lmao', 'totally', 'absolutely',
  'definitely', 'honestly', 'truly', 'literally', 'amazing',
];

const SCAFFOLDING = [
  /^\s*(?:sure|okay|ok|got it|absolutely)\s*[,!.]\s*/i,
  /^\s*here(?:'s| is)[^:\n]{0,40}:\s*/i,
  /^\s*(?:draft|option|reply|response|message|variant)\s*[a-c1-3]?\s*[:\-–—]\s*/i,
  /^\s*\*\*[^*\n]{1,40}\*\*\s*:?\s*/,
];

const COMMENTARY = /^\s*(?:this|note|i (?:chose|went|kept|used|avoided)|the (?:draft|reply|strategy|tone))\b/i;

const SAFE_LEAD_WORDS = new Set([
  'i', 'you', 'we', 'they', 'he', 'she', 'it', 'that', 'this', 'there',
  'a', 'an', 'the', 'my', 'your', 'our', 'their', 'his', 'her', 'its',
  'and', 'but', 'so', 'or', 'if', 'when', 'while', 'because', 'though',
  'what', 'who', 'where', 'why', 'how', 'which',
  'do', 'does', 'did', 'is', 'are', 'was', 'were', 'am', 'be', 'been',
  'have', 'has', 'had', 'will', 'would', 'should', 'could', 'can', 'may',
  'no', 'not', 'yes', 'yeah', 'yep', 'nah', 'ok', 'okay', 'well', 'just',
  'still', 'also', 'maybe', 'honestly', 'actually', 'kinda', 'sorta',
  'gonna', 'wanna', 'let', 'sounds', 'looks', 'feels', 'seems', 'same',
  'one', 'some', 'any', 'every', 'all', 'both', 'either', 'neither',
  'here', 'now', 'then', 'once', 'after', 'before', 'with', 'without',
  'for', 'from', 'to', 'in', 'on', 'at', 'by', 'as', 'about', 'into',
]);

const DANGLING_TAIL_WORDS = new Set([
  'and', 'but', 'so', 'or', 'the', 'a', 'an', 'to', 'of', 'in', 'on',
  'at', 'for', 'with', 'about', 'that', 'if', 'because', 'than',
  'my', 'your', 'is', 'was', 'are', 'were', 'i', 'you', 'it',
]);

const MINIMUM_VIABLE_WORDS = 2;

/** The thresholds §4.4 and §7.1 must agree on — VoicePolicy.kt. */
export const policy = {
  lowercasesLead: (profile) => profile.capitalizationRate < 0.3,
  stripsTerminalPeriod: (profile) => profile.terminalPunctuationRate < 0.3,
  lowercasesI: (profile) => profile.lowercaseIRate > 0.7,
  contracts: (profile) => profile.contractionRate > 0.8,
  stripsEllipsis: (profile) => profile.ellipsisRate < 0.1,
  allowedEmoji: (profile) => Math.round(profile.emojiRate),
  allowedExclamations: (profile, count) => allowance(profile.exclamationRate, count, 1),
  allowedCommas: (profile, count) => allowance(profile.commaRate, count, 1.5),
};

function allowance(ratePer100Words, count, floorThreshold) {
  const expected = (ratePer100Words * count) / 100;
  if (expected >= 0.5) return Math.round(expected);
  return ratePer100Words >= floorThreshold ? 1 : 0;
}

export function maxDraftWords(profile) {
  return Math.max(4, Math.trunc(profile.p90Words + 3));
}

function escapeLiteral(word) {
  return Array.from(word)
    .map((char) => {
      if (char === "'" || char === '’') return "['’]";
      return /[\p{L}\p{N}]/u.test(char) ? char : `\\${char}`;
    })
    .join('');
}

function phrasePattern(phrase) {
  const body = phrase.split(' ').map(escapeLiteral).join('\\s+');
  return new RegExp(`(?<!\\p{L})${body}(?!\\p{L})`, 'giu');
}

function vocabularyCount(profile, word) {
  const vocabulary = profile.vocabulary || {};
  return vocabulary[word] || 0;
}

export function forbiddenFor(profile) {
  const phrases = FORBIDDEN_PHRASES.map((phrase) => ({
    pattern: phrasePattern(phrase),
    replacement: '',
    name: phrase,
  }));

  const abbreviations = profile.abbreviations || {};
  const interjections = FORBIDDEN_INTERJECTIONS.filter(
    (word) => vocabularyCount(profile, word) === 0 && !(word in abbreviations),
  ).map((word) => ({ pattern: phrasePattern(word), replacement: '', name: word }));

  const emDash = profile.commaRate >= 1 ? ', ' : ' ';
  const semicolon = profile.commaRate >= 1 ? ', ' : profile.terminalPunctuationRate >= 0.3 ? '. ' : ' ';

  return [
    ...phrases,
    ...interjections,
    { pattern: /\s*[—–]\s*/g, replacement: emDash, name: 'em dash' },
    { pattern: /\s*;\s*/g, replacement: semicolon, name: 'semicolon' },
  ];
}

function stripSurroundingQuotes(text) {
  const trimmed = text.trim();
  if (trimmed.length <= 1) return trimmed;
  const opens = '"\'“‘';
  const closes = '"\'”’';
  if (opens.includes(trimmed[0]) && closes.includes(trimmed.slice(-1))) {
    return trimmed.slice(1, -1).trim();
  }
  return trimmed;
}

export function stripScaffolding(raw) {
  let text = raw.trim();
  for (let pass = 0; pass < 3; pass += 1) {
    const before = text;
    text = stripSurroundingQuotes(text);
    for (const pattern of SCAFFOLDING) text = text.replace(pattern, '');
    text = stripSurroundingQuotes(text).trim();
    if (text === before) break;
  }

  const paragraphs = text.split(/\n\s*\n/).map((part) => part.trim()).filter(Boolean);
  if (paragraphs.length > 1) {
    const kept = [];
    for (const paragraph of paragraphs) {
      if (COMMENTARY.test(paragraph)) break;
      kept.push(paragraph);
    }
    text = (kept.length ? kept : [paragraphs[0]]).join('\n');
  }
  return text.trim();
}

function tidyPunctuation(text) {
  return text
    .replace(/ +/g, ' ')
    .replace(/ +([,.!?…])/g, '$1')
    .replace(/(,\s*){2,}/g, ', ')
    .replace(/^[\s,;:.!]+/, '')
    .replace(/[ ,;]+$/, '')
    .trim();
}

function applyContractions(text) {
  let output = text;
  const entries = Array.from(CONTRACTIONS.entries()).sort((a, b) => b[0].length - a[0].length);
  for (const [long, short] of entries) {
    const pattern = new RegExp(
      `(?<!\\p{L})${long.split(' ').join('\\s+')}(?!\\p{L})`,
      'giu',
    );
    output = output.replace(pattern, (match) =>
      /^[A-Z]/.test(match) ? short.charAt(0).toUpperCase() + short.slice(1) : short,
    );
  }
  return output;
}

function applyEmojiPolicy(text, profile, transforms) {
  const allowed = policy.allowedEmoji(profile);
  let output = text;

  const ranges = emojiRanges(output);
  if (ranges.length > allowed) {
    const doomed = ranges.slice(allowed).reverse();
    for (const [start, end] of doomed) {
      output = output.slice(0, start) + output.slice(end);
    }
    transforms.push('EMOJI_TRIM');
  }

  const top = profile.topEmoji || [];
  if (top.length > 0) {
    const preferred = top[0];
    let substituted = false;
    const remaining = emojiRanges(output).reverse();
    for (const [start, end] of remaining) {
      const emoji = output.slice(start, end);
      if (!top.includes(emoji)) {
        output = output.slice(0, start) + preferred + output.slice(end);
        substituted = true;
      }
    }
    if (substituted) transforms.push('EMOJI_SUBSTITUTE');
  }
  return output;
}

function applyExclamations(text, profile, transforms) {
  let output = text.replace(/!{2,}/g, '!');
  const allowed = policy.allowedExclamations(profile, wordCount(output));
  const present = countChar(output, '!');
  if (present <= allowed) return output;

  const replacement = policy.stripsTerminalPeriod(profile) ? '' : '.';
  let seen = 0;
  output = Array.from(output)
    .map((char) => {
      if (char !== '!') return char;
      seen += 1;
      return seen <= allowed ? char : replacement;
    })
    .join('');
  transforms.push('EXCLAMATION_TRIM');
  return output;
}

function applyEllipsis(text, profile, transforms) {
  if (!(text.includes('…') || /\.{2,}/.test(text))) return text;
  transforms.push('ELLIPSIS_NORMALIZED');
  if (policy.stripsEllipsis(profile)) {
    return text.replace(/\s*(?:\.{2,}|…)\s*/g, ' ');
  }
  return text.replace(/…/g, '...').replace(/\.{4,}/g, '...');
}

function applyCommas(text, profile, transforms) {
  const count = wordCount(text);
  if (count === 0) return text;
  const present = countChar(text, ',');
  if (present === 0) return text;

  const allowed = policy.allowedCommas(profile, count);
  if (present <= allowed) return text;

  let output = text.replace(/,(\s+(?:and|or)\s)/g, '$1');
  while (countChar(output, ',') > allowed) {
    const last = output.lastIndexOf(',');
    if (last < 0) break;
    output = output.slice(0, last) + output.slice(last + 1);
  }
  if (output !== text) transforms.push('COMMA_THINNING');
  return output;
}

function hardTruncate(text, ceiling) {
  const matches = Array.from(text.matchAll(/[\p{L}\p{N}]+(?:['’]\p{L}+)*/gu));
  if (matches.length <= ceiling) return text;
  let output = text.slice(0, matches[ceiling - 1].index + matches[ceiling - 1][0].length);
  for (;;) {
    const remaining = words(output);
    const last = remaining.length ? normalize(remaining[remaining.length - 1]) : null;
    if (last && DANGLING_TAIL_WORDS.has(last) && remaining.length > MINIMUM_VIABLE_WORDS) {
      output = output.slice(0, output.lastIndexOf(remaining[remaining.length - 1])).trimEnd();
    } else {
      break;
    }
  }
  return output;
}

function applyLength(text, profile, transforms) {
  const ceiling = maxDraftWords(profile);
  if (wordCount(text) <= ceiling) return text;

  const candidates = clauseBoundaries(text).filter((offset) => {
    const count = wordCount(text.slice(0, offset));
    return count >= MINIMUM_VIABLE_WORDS && count <= ceiling;
  });
  const boundary = candidates.length ? Math.max(...candidates) : null;
  const truncated = boundary === null ? hardTruncate(text, ceiling) : text.slice(0, boundary);
  transforms.push('TRUNCATED_TO_LENGTH');
  return truncated.replace(/[\s,;-]+$/, '');
}

function applyCapitalization(text, profile, transforms) {
  if (!policy.lowercasesLead(profile)) return text;
  let output = text;
  let changed = false;

  const lead = firstLetterIndex(output);
  if (lead >= 0 && output[lead] !== output[lead].toLowerCase()) {
    output = output.slice(0, lead) + output[lead].toLowerCase() + output.slice(lead + 1);
    changed = true;
  }

  // Later sentences only when the opening word cannot be a name — see
  // Lexicons.SAFE_LEAD_WORDS for the argument.
  output = output.replace(/([.!?…]["')]?\s+)(\p{Lu}\p{L}*)/gu, (whole, prefix, word) => {
    if (SAFE_LEAD_WORDS.has(normalize(word))) {
      changed = true;
      return prefix + word.charAt(0).toLowerCase() + word.slice(1);
    }
    return whole;
  });

  if (changed) transforms.push('LOWERCASE_LEAD');
  return output;
}

function applyLowercaseI(text, profile, transforms) {
  if (!policy.lowercasesI(profile)) return text;
  const output = text.replace(/(?<!\p{L})I(?=(?:['’]\p{L}+)?(?!\p{L}))/gu, 'i');
  if (output !== text) transforms.push('LOWERCASE_I');
  return output;
}

/**
 * Compiles raw model output into your voice.
 *
 * Returns `{ text, transforms, removedPhrases, needsRegeneration }`, matching
 * `CompiledDraft` in Kotlin.
 */
export function compile(raw, profile) {
  const transforms = [];
  const removedPhrases = [];

  let text = stripScaffolding(raw);

  for (const phrase of forbiddenFor(profile)) {
    if (phrase.pattern.test(text)) {
      phrase.pattern.lastIndex = 0;
      removedPhrases.push(phrase.name);
      text = text.replace(phrase.pattern, phrase.replacement);
      transforms.push('FORBIDDEN_TOKEN_DELETED');
    }
    phrase.pattern.lastIndex = 0;
  }
  text = tidyPunctuation(text);

  if (policy.contracts(profile)) {
    const contracted = applyContractions(text);
    if (contracted !== text) {
      transforms.push('CONTRACT');
      text = contracted;
    }
  }

  text = applyEmojiPolicy(text, profile, transforms);
  text = applyExclamations(text, profile, transforms);
  text = applyEllipsis(text, profile, transforms);
  text = applyCommas(text, profile, transforms);
  text = applyLength(text, profile, transforms);

  if (policy.stripsTerminalPeriod(profile) && text.trimEnd().endsWith('.')) {
    text = text.trimEnd().replace(/\.+$/, '');
    transforms.push('STRIP_TERMINAL_PERIOD');
  }

  text = applyCapitalization(text, profile, transforms);
  text = applyLowercaseI(text, profile, transforms);
  text = tidyPunctuation(text);

  return {
    text,
    transforms: Array.from(new Set(transforms)),
    removedPhrases,
    needsRegeneration: wordCount(text) < MINIMUM_VIABLE_WORDS,
  };
}

/** §7.1. Re-measures the compiled draft against the profile. */
export function verifyStyle(text, profile) {
  const deviations = [];
  const count = wordCount(text);

  if (count > maxDraftWords(profile)) {
    deviations.push({ feature: 'length', profileValue: maxDraftWords(profile), draftValue: count });
  }
  if (policy.lowercasesLead(profile)) {
    const lead = firstLetterIndex(text);
    if (lead >= 0 && text[lead] !== text[lead].toLowerCase()) {
      deviations.push({ feature: 'capitalization', profileValue: profile.capitalizationRate, draftValue: 1 });
    }
  }
  if (policy.stripsTerminalPeriod(profile) && text.trimEnd().endsWith('.')) {
    deviations.push({
      feature: 'terminalPunctuation',
      profileValue: profile.terminalPunctuationRate,
      draftValue: 1,
    });
  }
  if (policy.lowercasesI(profile) && /(?<!\p{L})I(?=(?:['’]\p{L}+)?(?!\p{L}))/u.test(text)) {
    deviations.push({ feature: 'lowercaseI', profileValue: profile.lowercaseIRate, draftValue: 0 });
  }

  const emoji = emojiList(text).length;
  const allowedEmoji = policy.allowedEmoji(profile);
  if (emoji > allowedEmoji) {
    deviations.push({ feature: 'emojiRate', profileValue: allowedEmoji, draftValue: emoji });
  }

  if (count > 0) {
    const commas = countChar(text, ',');
    const allowedCommas = policy.allowedCommas(profile, count);
    if (commas > allowedCommas) {
      deviations.push({ feature: 'commaRate', profileValue: allowedCommas, draftValue: commas });
    }
    const exclamations = countChar(text, '!');
    const allowedExclamations = policy.allowedExclamations(profile, count);
    if (exclamations > allowedExclamations) {
      deviations.push({
        feature: 'exclamationRate',
        profileValue: allowedExclamations,
        draftValue: exclamations,
      });
    }
  }

  for (const phrase of forbiddenFor(profile)) {
    phrase.pattern.lastIndex = 0;
    if (phrase.pattern.test(text)) {
      deviations.push({ feature: `forbidden:${phrase.name}`, profileValue: 0, draftValue: 1 });
    }
    phrase.pattern.lastIndex = 0;
  }

  return deviations;
}
