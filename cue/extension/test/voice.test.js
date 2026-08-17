// The port's guard rail.
//
// `src/lib/voice.js` and `src/lib/draft.js` are hand ports of :core:voice and
// :core:draft, so they can drift from the Kotlin. These are the same assertions
// as `VoiceCompilerTest.kt` and `GatesTest.kt`, against the same inputs — when
// the Kotlin changes, this is where the divergence shows up.
//
// Run with: node --test extension/test/

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { compile, verifyStyle } from '../src/lib/voice.js';
import {
  checkEscalation,
  checkGrounding,
  classifyStage,
  similarity,
  stageSignals,
  stem,
  tooSimilar,
  Bm25Index,
  buildPrompt,
  STRATEGIES,
} from '../src/lib/draft.js';
import { emojiCount, endsWithTerminalPunctuation } from '../src/lib/text.js';
import { BASELINE } from '../src/lib/profile.js';

const lowercase = {
  ...BASELINE,
  sampleCount: 200,
  medianWords: 8,
  p90Words: 16,
  capitalizationRate: 0.05,
  lowercaseIRate: 0.95,
  terminalPunctuationRate: 0.04,
  ellipsisRate: 0.02,
  commaRate: 0.4,
  exclamationRate: 0.1,
  emojiRate: 0.1,
  topEmoji: ['😂'],
  contractionRate: 0.95,
  vocabulary: { sounds: 40, good: 30, ferry: 12 },
};

const proper = {
  ...BASELINE,
  sampleCount: 200,
  medianWords: 14,
  p90Words: 28,
  capitalizationRate: 0.98,
  lowercaseIRate: 0,
  terminalPunctuationRate: 0.95,
  commaRate: 4,
  exclamationRate: 1.5,
  emojiRate: 0,
  contractionRate: 0.2,
};

describe('text primitives', () => {
  it('counts multi-codepoint emoji as one', () => {
    assert.equal(emojiCount('👍🏽'), 1);
    assert.equal(emojiCount('👨‍👩‍👧'), 1);
    assert.equal(emojiCount('🇨🇦'), 1);
    assert.equal(emojiCount('❤️'), 1);
    assert.equal(emojiCount('👍🏽 👨‍👩‍👧 🇨🇦 ❤️'), 4);
  });

  it('sees terminal punctuation behind a trailing emoji', () => {
    assert.equal(endsWithTerminalPunctuation("that's the plan. 😂"), true);
    assert.equal(endsWithTerminalPunctuation("that's the plan 😂"), false);
  });

  it('collapses doubled consonants when stemming', () => {
    assert.equal(stem('swimming'), 'swim');
    assert.equal(stem('running'), 'run');
    assert.equal(stem('bones'), 'bone');
  });
});

describe('§4.4 voice compiler', () => {
  it('downcases the lead for a lowercase writer', () => {
    assert.equal(compile('Sounds good to me', lowercase).text, 'sounds good to me');
  });

  it('leaves capitals alone for a writer who uses them', () => {
    assert.equal(compile('Sounds good to me.', proper).text, 'Sounds good to me.');
  });

  it('downcases a later sentence only when it cannot be a name', () => {
    assert.equal(compile('yeah. That works', lowercase).text, 'yeah. that works');
    assert.equal(compile('yeah. Toronto works', lowercase).text, 'yeah. Toronto works');
  });

  it('strips a trailing period but never a question mark', () => {
    assert.equal(compile('that works.', lowercase).text, 'that works');
    assert.equal(compile('does that work?', lowercase).text, 'does that work?');
  });

  it('lowercases standalone I without touching other capitals', () => {
    assert.equal(
      compile('I think I saw Toronto from the ferry', lowercase).text,
      'i think i saw Toronto from the ferry',
    );
  });

  it('trims emoji beyond the profile rate', () => {
    assert.equal(emojiCount(compile('sounds good 🙌 really 🎉', lowercase).text), 0);
  });

  it('substitutes a kept emoji for one of yours', () => {
    const emojiUser = { ...lowercase, emojiRate: 1.2, topEmoji: ['😂', '🙃'] };
    const result = compile('that is wild 🎉', emojiUser);
    assert.equal(emojiCount(result.text), 1);
    assert.ok(result.text.includes('😂'));
  });

  it('drops serial commas first', () => {
    const result = compile('we could do coffee, a walk, or the market', lowercase);
    assert.ok(!result.text.includes(', or'), result.text);
  });

  it('does not strip a comma user bare', () => {
    const result = compile('We could do coffee, a walk, or the market.', proper);
    assert.equal((result.text.match(/,/g) || []).length, 1, result.text);
  });

  it('contracts for a contractor and not for anyone else', () => {
    assert.ok(compile('I do not think that is right', lowercase).text.includes("don't"));
    assert.ok(compile('I do not think that is right.', proper).text.includes('do not'));
  });

  it('deletes the cheerful register', () => {
    const result = compile("That sounds amazing, I'd love to. Can't wait!", lowercase);
    for (const phrase of ['sounds amazing', 'love to', "can't wait"]) {
      assert.ok(!result.text.toLowerCase().includes(phrase), `left '${phrase}' in: ${result.text}`);
    }
  });

  it('keeps lol when your corpus contains it', () => {
    const lolUser = { ...lowercase, vocabulary: { ...lowercase.vocabulary, lol: 90 } };
    assert.ok(compile('lol that tracks', lolUser).text.includes('lol'));
    assert.ok(!compile('lol that tracks', lowercase).text.includes('lol'));
  });

  it('replaces em dashes and semicolons rather than deleting them', () => {
    const result = compile('I went there — it was fine; the walk back was better.', proper);
    assert.ok(!result.text.includes('—'), result.text);
    assert.ok(!result.text.includes(';'), result.text);
    assert.ok(result.text.includes('it was fine'), result.text);
    assert.ok(result.text.includes('the walk back'), result.text);
  });

  it('truncates at a clause boundary under the ceiling', () => {
    const long =
      'that place is genuinely great, the coffee is unreasonable and ' +
      'the walk back is the best part of the whole thing honestly';
    const result = compile(long, lowercase);
    assert.ok(result.text.split(/\s+/).length <= 19, result.text);
    assert.ok(!result.text.trim().endsWith('and'), result.text);
  });

  it('strips the wrapper a model puts around the message', () => {
    assert.equal(
      compile('Sure! Here\'s a reply: "That place is great."', lowercase).text,
      'that place is great',
    );
    assert.equal(
      compile('Draft A: That works for me.\n\nThis keeps the tone light.', lowercase).text,
      'that works for me',
    );
  });

  it('flags a draft that was mostly filler', () => {
    assert.equal(compile('Absolutely, totally!', lowercase).needsRegeneration, true);
  });

  it('output passes its own verifier', () => {
    const outputs = [
      "That sounds amazing! I'd love to hear more about it 😊",
      'Here\'s my reply: "Absolutely — I totally get that, honestly."',
      'I definitely think the ferry is the best part; can\'t wait to try it!!!',
      'Sure! You should definitely go, it\'s great, and I would love to join.',
      'I am not sure that is true, but I would like to know more about it.',
    ];
    for (const raw of outputs) {
      const compiled = compile(raw, lowercase);
      if (compiled.needsRegeneration) continue;
      assert.deepEqual(
        verifyStyle(compiled.text, lowercase),
        [],
        `compiled '${compiled.text}' still deviates`,
      );
    }
  });

  it('is deterministic', () => {
    const raw = 'Honestly? That sounds amazing — I\'d definitely go!!';
    assert.equal(compile(raw, lowercase).text, compile(raw, lowercase).text);
  });
});

const context = {
  conversationId: 'c1',
  profile: {
    displayName: 'Maya',
    age: 27,
    bio: null,
    prompts: [
      {
        prompt: 'Two truths and a lie',
        answer: 'i have broken three bones, i cannot swim, i met a prime minister',
      },
    ],
    attributes: {},
    photoCaptions: ['kayak'],
  },
  messages: [
    { id: '0', sender: 'THEM', text: 'i cannot swim, it is a whole thing', sequence: 0 },
    { id: '1', sender: 'ME', text: 'ok that needs explaining', sequence: 1 },
  ],
  lastTheirMessageAt: Date.now(),
};

describe('§7.2 grounding', () => {
  it('rejects every invented detail', () => {
    const invented = [
      "how's your dog",
      'does your dog like the kayak',
      'the climbing thing is impressive',
      'how long have you played the cello',
      'your trip to lisbon sounds unreal',
      "so you're a nurse",
      "how's the marathon training going",
      'you mentioned your brother',
      'is the bakery still your favourite',
      'tell me about berlin',
    ];
    const survivors = invented.filter(
      (draft) => checkGrounding(draft, context, lowercase).length === 0,
    );
    assert.deepEqual(survivors, [], `these got through: ${survivors}`);
  });

  it('accepts what she actually said', () => {
    for (const draft of [
      'wait you cannot swim',
      'the swimming thing needs explaining',
      'three bones is a lot of bones',
      'a prime minister though',
      'explain the kayak',
    ]) {
      assert.deepEqual(checkGrounding(draft, context, lowercase), [], draft);
    }
  });

  it('treats a plan as a proposal, not a claim', () => {
    for (const draft of ['drinks thursday?', 'are you free saturday for a walk']) {
      assert.deepEqual(checkGrounding(draft, context, lowercase), [], draft);
    }
  });

  it('checks a proper noun even when it is your own idiom', () => {
    const yours = { ...lowercase, vocabulary: { ...lowercase.vocabulary, ossington: 40 } };
    assert.deepEqual(checkGrounding('the Ossington place then', context, yours), ['Ossington']);
  });
});

describe('§7.3 escalation', () => {
  it('rejects a meeting proposal before established', () => {
    assert.ok(checkEscalation('we should grab a drink this week', 'EARLY_RAPPORT', context));
    assert.equal(checkEscalation('we should grab a drink this week', 'ESTABLISHED', context), null);
  });

  it('does not treat mentioning a place as proposing to go there', () => {
    assert.equal(checkEscalation('that coffee place is genuinely good', 'OPENER', context), null);
  });

  it('rejects sexual content until she raises it', () => {
    assert.ok(checkEscalation('you look sexy in that photo', 'ESTABLISHED', context));
    const sheDid = {
      ...context,
      messages: [{ id: '0', sender: 'THEM', text: 'ok that was a sexy answer', sequence: 0 }],
    };
    assert.equal(checkEscalation('that was a sexy answer back', 'ESTABLISHED', sheDid), null);
  });

  it('does not let your own escalation open the door', () => {
    const onlyMe = {
      ...context,
      messages: [{ id: '0', sender: 'ME', text: 'that was a sexy answer', sequence: 0 }],
    };
    assert.ok(checkEscalation('sexy answer again', 'ESTABLISHED', onlyMe));
  });
});

describe('§6.3 stage classification', () => {
  const day = 86400000;

  function conversation(count, { herQuestions = false, lastTheirMessageAt = Date.now() } = {}) {
    const messages = [];
    for (let index = 0; index < count; index += 1) {
      const sender = index % 2 === 0 ? 'THEM' : 'ME';
      messages.push({
        id: String(index),
        sender,
        sequence: index,
        text:
          sender === 'THEM' && herQuestions
            ? 'and what did you make of the whole thing then?'
            : sender === 'THEM'
              ? 'that is a long and considered reply about the topic'
              : 'ok that tracks completely',
      });
    }
    return { ...context, messages, lastTheirMessageAt };
  }

  it('no messages is an opener', () => {
    assert.equal(classifyStage(stageSignals({ ...context, messages: [], lastTheirMessageAt: null })), 'OPENER');
  });

  it('under six messages is early rapport', () => {
    assert.equal(classifyStage(stageSignals(conversation(4))), 'EARLY_RAPPORT');
  });

  it('silence outranks a short message count', () => {
    const stale = conversation(4, { lastTheirMessageAt: Date.now() - 5 * day });
    assert.equal(classifyStage(stageSignals(stale)), 'STALLING');
  });

  it('over a week is dead', () => {
    const dead = conversation(8, { lastTheirMessageAt: Date.now() - 9 * day });
    assert.equal(classifyStage(stageSignals(dead)), 'DEAD');
  });

  it('her questions with no logistics is ready to ask', () => {
    assert.equal(classifyStage(stageSignals(conversation(10, { herQuestions: true }))), 'READY_TO_ASK');
  });

  it('logistics already raised is not ready to ask', () => {
    const withPlan = conversation(10, { herQuestions: true });
    withPlan.messages.push({ id: 'x', sender: 'ME', text: 'drinks thursday then', sequence: 10 });
    assert.equal(classifyStage(stageSignals(withPlan)), 'ESTABLISHED');
  });
});

describe('§6.4 distinctness', () => {
  it('detects near-identical variants', () => {
    assert.ok(
      similarity('the ramen place needs explaining', 'the ramen place really needs explaining') > 0.6,
    );
  });

  it('treats genuinely different moves as distinct', () => {
    assert.ok(similarity('three bones is a lot of bones', 'are you free thursday') < 0.6);
  });

  it('flags the later duplicate', () => {
    assert.deepEqual(
      tooSimilar([
        'the ramen place needs explaining',
        'are you free thursday',
        'the ramen place really needs explaining',
      ]),
      [2],
    );
  });
});

describe('§4.3 retrieval and §6.2 prompt', () => {
  const corpus = [
    {
      id: 'a',
      text: "i'll bring the good speakers",
      precedingTheirMessage: 'are you coming to the barbecue on sunday',
      weight: 1,
    },
    { id: 'b', text: 'that tracks', precedingTheirMessage: 'how was your week', weight: 1 },
  ];

  it('a probe matching only her side finds your reply', () => {
    const index = new Bm25Index(corpus);
    assert.equal(index.search('the barbecue on sunday', 2)[0].message.id, 'a');
  });

  it('puts constraints last', () => {
    const prompt = buildPrompt({
      context,
      strategy: STRATEGIES.ESTABLISHED[0],
      examples: corpus,
      profile: lowercase,
    });
    assert.ok(prompt.text.indexOf('HER PROFILE') < prompt.text.indexOf('HOW I WRITE'));
    assert.ok(prompt.text.indexOf('HOW I WRITE') < prompt.text.indexOf('RULES'));
    assert.ok(prompt.text.trimEnd().endsWith('no quotes.'), prompt.text.slice(-80));
  });

  it('never describes the voice it demonstrates', () => {
    const prompt = buildPrompt({
      context,
      strategy: STRATEGIES.ESTABLISHED[0],
      examples: corpus,
      profile: lowercase,
    });
    for (const word of ['lowercase', 'capitalis', 'capitaliz', 'punctuation', 'emoji']) {
      assert.ok(!prompt.text.toLowerCase().includes(word), `prompt describes '${word}'`);
    }
  });
});
