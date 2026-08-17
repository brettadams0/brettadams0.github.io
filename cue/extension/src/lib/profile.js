// §3.4. Importing the voice profile the Android app exported.
//
// > No backend means no sync: export the voice profile as JSON from Android,
// > import into the extension. It changes slowly; manual is fine.
//
// The baseline is not neutral, for the reason `VoiceProfile.BASELINE` gives in
// Kotlin: all-zeroes would strip every capital from someone who writes in full
// sentences, and all-ones would let the model's native register through
// untouched.

export const FORMAT = 'cue.voice-profile';
export const VERSION = 1;
export const MIN_SAMPLES = 50;

export const BASELINE = {
  sampleCount: 0,
  medianWords: 9,
  p90Words: 22,
  capitalizationRate: 0.35,
  lowercaseIRate: 0.4,
  terminalPunctuationRate: 0.3,
  ellipsisRate: 0.05,
  commaRate: 2.5,
  exclamationRate: 1.0,
  emojiRate: 0.15,
  topEmoji: [],
  abbreviations: {},
  contractionRate: 0.9,
  profanityRate: 0.02,
  questionRate: 0.5,
  burstRate: 0.2,
  characteristicTokens: [],
  vocabulary: {},
};

/** Throws with a message a human can act on, not a parser error. */
export function decodeProfile(text) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error('That is not a Cue voice profile export.');
  }
  if (parsed.format !== FORMAT) {
    throw new Error(`That file says it is '${parsed.format}', not a Cue voice profile.`);
  }
  if (parsed.version > VERSION) {
    throw new Error(
      `That profile was exported by a newer version of Cue (format ${parsed.version}, ` +
        `this build reads ${VERSION}). Update the extension.`,
    );
  }
  return { ...BASELINE, ...parsed.profile };
}

export function isCalibrated(profile) {
  return (profile.sampleCount || 0) >= MIN_SAMPLES;
}

const STORAGE_KEY = 'cue.voiceProfile';
const CORPUS_KEY = 'cue.corpus';

export async function loadProfile() {
  const stored = await chrome.storage.local.get([STORAGE_KEY]);
  return stored[STORAGE_KEY] || BASELINE;
}

export async function saveProfile(profile) {
  await chrome.storage.local.set({ [STORAGE_KEY]: profile });
}

/**
 * The corpus §4.3 retrieves from.
 *
 * Imported alongside the profile when the export carries one. Without it the
 * extension has the numbers but no examples, and §4.3 is explicit that the
 * examples are the part that does the work — so a profile-only import leaves the
 * browser noticeably worse than the phone, and the panel says so.
 */
export async function loadCorpus() {
  const stored = await chrome.storage.local.get([CORPUS_KEY]);
  return stored[CORPUS_KEY] || [];
}

export async function saveCorpus(corpus) {
  await chrome.storage.local.set({ [CORPUS_KEY]: corpus });
}
