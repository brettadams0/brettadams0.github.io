// The text primitives, ported from :core:voice's Text.kt.
//
// This file is a port and not a shared library, which is a real cost: two
// implementations of §4.4 can drift. The alternative was a build step to compile
// Kotlin/JS, and §3.2 keeps the whole dependency list at "free, offline, and
// unlimited" — adding a toolchain to save 300 lines of JavaScript is the wrong
// trade for a project whose §15 milestone 7 is "parity with mobile".
//
// What keeps them honest is `test/voice.test.js`, which asserts the same
// behaviours as `VoiceCompilerTest.kt` against the same inputs. When the Kotlin
// changes, that file is where the drift shows up.

const WORD_PATTERN = /[\p{L}\p{N}]+(?:['’]\p{L}+)*/gu;

export const TERMINAL_PUNCTUATION = ['.', '!', '?', '…'];

export function words(text) {
  return Array.from(text.matchAll(WORD_PATTERN), (match) => match[0]);
}

export function wordCount(text) {
  return words(text).length;
}

export function normalize(word) {
  return word.toLowerCase().replace(/’/g, "'");
}

export function normalizedWords(text) {
  return words(text).map(normalize);
}

export function countChar(text, character) {
  let count = 0;
  for (const char of text) if (char === character) count += 1;
  return count;
}

export function firstLetterIndex(text) {
  const match = /\p{L}/u.exec(text);
  return match ? match.index : -1;
}

// Emoji, by code point. JavaScript strings are UTF-16 like Kotlin's, so the same
// trap applies: iterating by index splits every astral emoji in half.
const EMOJI_RANGES = [
  [0x1f000, 0x1f02f],
  [0x1f0a0, 0x1f0ff],
  [0x1f300, 0x1faff],
  [0x2600, 0x27bf],
  [0x2b00, 0x2bff],
  [0x2190, 0x21ff],
  [0x2900, 0x297f],
  [0x3030, 0x3030],
  [0x303d, 0x303d],
  [0x3297, 0x3299],
];
const REGIONAL = [0x1f1e6, 0x1f1ff];
const ZWJ = 0x200d;
const VARIATION = [0xfe0f, 0xfe0e, 0x20e3];
const SKIN_TONES = [0x1f3fb, 0x1f3ff];

export function isEmojiCodePoint(code) {
  if (code >= REGIONAL[0] && code <= REGIONAL[1]) return true;
  return EMOJI_RANGES.some(([low, high]) => code >= low && code <= high);
}

/** Whole emoji graphemes, with ZWJ sequences and modifiers folded in. */
export function emojiRanges(text) {
  const found = [];
  let index = 0;
  while (index < text.length) {
    const code = text.codePointAt(index);
    const width = code > 0xffff ? 2 : 1;
    if (!isEmojiCodePoint(code)) {
      index += width;
      continue;
    }
    let end = index + width;
    if (code >= REGIONAL[0] && code <= REGIONAL[1] && end < text.length) {
      const next = text.codePointAt(end);
      if (next >= REGIONAL[0] && next <= REGIONAL[1]) end += next > 0xffff ? 2 : 1;
    }
    while (end < text.length) {
      const next = text.codePointAt(end);
      const nextWidth = next > 0xffff ? 2 : 1;
      if (VARIATION.includes(next) || (next >= SKIN_TONES[0] && next <= SKIN_TONES[1])) {
        end += nextWidth;
      } else if (next === ZWJ) {
        const after = end + nextWidth;
        if (after >= text.length) break;
        const joined = text.codePointAt(after);
        end = after + (joined > 0xffff ? 2 : 1);
      } else {
        break;
      }
    }
    found.push([index, end]);
    index = end;
  }
  return found;
}

export function emojiList(text) {
  return emojiRanges(text).map(([start, end]) => text.slice(start, end));
}

export function emojiCount(text) {
  return emojiRanges(text).length;
}

// `Array.from` on a string yields whole code points, which is the whole trick:
// indexing by `.length` walks into the middle of an emoji and concludes that
// "that's the plan. 😂" does not end in punctuation.
function trimTrailingNonPunctuation(text) {
  const points = Array.from(text);
  let end = points.length;
  while (end > 0) {
    const point = points[end - 1].codePointAt(0);
    const skippable =
      /\s/u.test(points[end - 1]) ||
      isEmojiCodePoint(point) ||
      VARIATION.includes(point) ||
      point === ZWJ ||
      (point >= SKIN_TONES[0] && point <= SKIN_TONES[1]) ||
      points[end - 1] === '"' ||
      points[end - 1] === "'";
    if (!skippable) break;
    end -= 1;
  }
  return points.slice(0, end).join('');
}

export function endsWithTerminalPunctuation(text) {
  const trimmed = trimTrailingNonPunctuation(text);
  return TERMINAL_PUNCTUATION.includes(trimmed.slice(-1));
}

export function endsWithQuestionMark(text) {
  return trimTrailingNonPunctuation(text).endsWith('?');
}

const CONJUNCTIONS = [' and ', ' but ', ' so ', ' because ', ' though ', ' while '];

/** Offsets a message can be cut at without reading as interrupted. */
export function clauseBoundaries(text) {
  const boundaries = new Set();
  let index = 0;
  while (index < text.length) {
    const char = text[index];
    if (TERMINAL_PUNCTUATION.includes(char) || char === ',' || char === ';') {
      let end = index + 1;
      while (
        end < text.length &&
        (TERMINAL_PUNCTUATION.includes(text[end]) || text[end] === ',' || text[end] === ';')
      ) {
        end += 1;
      }
      boundaries.add(end);
      index = end;
    } else {
      index += 1;
    }
  }
  for (const conjunction of CONJUNCTIONS) {
    let from = 0;
    for (;;) {
      const at = text.toLowerCase().indexOf(conjunction, from);
      if (at < 0) break;
      boundaries.add(at);
      from = at + 1;
    }
  }
  return Array.from(boundaries)
    .filter((offset) => offset >= 1 && offset < text.length)
    .sort((a, b) => a - b);
}

export function tidy(text) {
  return text
    .replace(/[ \t]+/g, ' ')
    .replace(/ ?\n ?/g, '\n')
    .trim();
}
