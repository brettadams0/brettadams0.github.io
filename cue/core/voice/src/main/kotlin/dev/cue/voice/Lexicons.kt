package dev.cue.voice

/**
 * The word lists §4 needs. Kept in one file so they can be reviewed as data
 * rather than discovered inline, and so the Chrome extension's port (§3.4) has
 * a single source to mirror.
 */
object Lexicons {
    /**
     * Expanded form to contracted form (§4.4's contraction transform).
     *
     * Ordered longest-first at use time so "can not" is not half-matched by a
     * shorter key. Written lowercase; the transform restores the original case
     * of the first character.
     */
    val CONTRACTIONS: Map<String, String> = linkedMapOf(
        "cannot" to "can't",
        "can not" to "can't",
        "do not" to "don't",
        "does not" to "doesn't",
        "did not" to "didn't",
        "is not" to "isn't",
        "are not" to "aren't",
        "was not" to "wasn't",
        "were not" to "weren't",
        "have not" to "haven't",
        "has not" to "hasn't",
        "had not" to "hadn't",
        "will not" to "won't",
        "would not" to "wouldn't",
        "should not" to "shouldn't",
        "could not" to "couldn't",
        "i am" to "i'm",
        "you are" to "you're",
        "we are" to "we're",
        "they are" to "they're",
        "it is" to "it's",
        "that is" to "that's",
        "there is" to "there's",
        "what is" to "what's",
        "how is" to "how's",
        "i will" to "i'll",
        "you will" to "you'll",
        "we will" to "we'll",
        "i have" to "i've",
        "you have" to "you've",
        "we have" to "we've",
        "i would" to "i'd",
        "you would" to "you'd",
        "let us" to "let's",
    )

    /**
     * Abbreviations to look for when measuring register (§4.1).
     *
     * Counted, not judged. A corpus with 200 "u" and no "you" means something
     * the compiler can act on; a corpus with three of them means nothing.
     */
    val ABBREVIATIONS: List<String> = listOf(
        "u", "ur", "rn", "tbh", "ngl", "idk", "btw", "fr", "imo", "af",
        "omg", "wyd", "hbu", "nvm", "tho", "kinda", "gonna", "wanna",
        "prob", "def", "rly", "rlly", "ppl", "ig", "lmao", "lol", "lmk",
    )

    /**
     * The expansion each abbreviation competes with, for §4.4's dominance test.
     * Only pairs where the substitution is safe in any grammatical position.
     */
    val ABBREVIATION_EXPANSIONS: Map<String, List<String>> = mapOf(
        "u" to listOf("you"),
        "ur" to listOf("your", "you're"),
        "rn" to listOf("right now"),
        "tbh" to listOf("to be honest"),
        "idk" to listOf("i don't know"),
        "btw" to listOf("by the way"),
        "tho" to listOf("though"),
        "gonna" to listOf("going to"),
        "wanna" to listOf("want to"),
        "kinda" to listOf("kind of"),
        "lmk" to listOf("let me know"),
    )

    val PROFANITY: Set<String> = setOf(
        "fuck", "fucking", "fucked", "shit", "shitty", "damn", "hell",
        "ass", "asshole", "crap", "bitch", "dick", "piss", "pissed", "bullshit",
    )

    /**
     * Words that can start a sentence without being a proper noun.
     *
     * §4.4 downcases the leading character unconditionally, which is right for
     * a lowercase writer — they really do type "toronto sounds fun". Applying
     * the same rule to *every* sentence start is not safe, because "Toronto"
     * mid-message is a name and downcasing it reads as a bug rather than as a
     * style. So subsequent sentences are only downcased when they begin with a
     * word on this list. It is short, it is boring, and it covers the
     * overwhelming majority of casual sentence openings.
     */
    val SAFE_LEAD_WORDS: Set<String> = setOf(
        "i", "you", "we", "they", "he", "she", "it", "that", "this", "there",
        "a", "an", "the", "my", "your", "our", "their", "his", "her", "its",
        "and", "but", "so", "or", "if", "when", "while", "because", "though",
        "what", "who", "where", "why", "how", "which",
        "do", "does", "did", "is", "are", "was", "were", "am", "be", "been",
        "have", "has", "had", "will", "would", "should", "could", "can", "may",
        "no", "not", "yes", "yeah", "yep", "nah", "ok", "okay", "well", "just",
        "still", "also", "maybe", "honestly", "actually", "kinda", "sorta",
        "gonna", "wanna", "let", "sounds", "looks", "feels", "seems", "same",
        "one", "some", "any", "every", "all", "both", "either", "neither",
        "here", "now", "then", "once", "after", "before", "with", "without",
        "for", "from", "to", "in", "on", "at", "by", "as", "about", "into",
    )

    /**
     * Words that cannot name a fact about a person.
     *
     * §7.2 checks "content nouns" against the captured context, and the honest
     * problem with that is a word like "answer": it is a content noun, it is not
     * in a 200-entry frequency table, and it asserts precisely nothing. Left
     * unlisted, "before anyone else is awake is the correct answer" fails
     * grounding on *"correct"* and *"answer"*, and a good draft disappears.
     *
     * The membership test for this list is deliberately narrow: could a stranger
     * reading the word learn something about her? "Dog" would fail that test and
     * is not here. "Reason" passes and is.
     */
    val GENERIC_ABSTRACT: Set<String> = setOf(
        "answer", "question", "reason", "idea", "point", "story", "thing",
        "side", "chance", "guess", "opinion", "choice", "option", "problem",
        "difference", "example", "moment", "minute", "hour", "second", "order",
        "matter", "case", "fact", "sense", "name", "number", "word", "words",
        "line", "list", "note", "detail", "details", "version", "attempt",
        "correct", "wrong", "right", "true", "false", "better", "best", "worse",
        "worst", "different", "easy", "hard", "simple", "weird", "strange",
        "funny", "serious", "quiet", "loud", "fast", "slow", "early", "late",
        "close", "high", "low", "long", "short", "small", "large", "huge",
        "real", "actual", "usual", "normal", "fine", "cool", "fair", "solid",
        "decent", "terrible", "awful", "unreal", "wild", "mad", "insane",
        "correctly", "honest", "genuine", "proper", "exact", "complete",
        "agree", "disagree", "explain", "explaining", "mean", "means",
        "sound", "sounds", "seem", "seems", "guessing", "wondering",
    )

    /**
     * Approximate frequencies of common English words, as a fraction of tokens.
     *
     * Used only as the denominator in §4.1's characteristic-token score, so
     * relative magnitude is what matters and being off by a factor of two on
     * "because" changes nothing. Anything absent gets [RARE_WORD_FREQUENCY],
     * which is what makes an unusual word score highly.
     */
    const val RARE_WORD_FREQUENCY = 0.000_02

    val GENERIC_ENGLISH: Map<String, Double> = mapOf(
        "the" to 0.0610, "be" to 0.0270, "to" to 0.0260, "of" to 0.0250,
        "and" to 0.0240, "a" to 0.0210, "in" to 0.0180, "that" to 0.0110,
        "have" to 0.0100, "i" to 0.0100, "it" to 0.0095, "for" to 0.0090,
        "not" to 0.0085, "on" to 0.0078, "with" to 0.0075, "he" to 0.0070,
        "as" to 0.0068, "you" to 0.0067, "do" to 0.0063, "at" to 0.0060,
        "this" to 0.0055, "but" to 0.0053, "his" to 0.0050, "by" to 0.0048,
        "from" to 0.0046, "they" to 0.0044, "we" to 0.0042, "say" to 0.0040,
        "her" to 0.0038, "she" to 0.0037, "or" to 0.0036, "an" to 0.0035,
        "will" to 0.0034, "my" to 0.0033, "one" to 0.0032, "all" to 0.0031,
        "would" to 0.0030, "there" to 0.0029, "their" to 0.0028, "what" to 0.0027,
        "so" to 0.0026, "up" to 0.0025, "out" to 0.0024, "if" to 0.0023,
        "about" to 0.0022, "who" to 0.0021, "get" to 0.0020, "which" to 0.0020,
        "go" to 0.0019, "me" to 0.0019, "when" to 0.0018, "make" to 0.0018,
        "can" to 0.0017, "like" to 0.0017, "time" to 0.0016, "no" to 0.0016,
        "just" to 0.0015, "him" to 0.0015, "know" to 0.0014, "take" to 0.0014,
        "people" to 0.0013, "into" to 0.0013, "year" to 0.0012, "your" to 0.0012,
        "good" to 0.0012, "some" to 0.0011, "could" to 0.0011, "them" to 0.0011,
        "see" to 0.0010, "other" to 0.0010, "than" to 0.0010, "then" to 0.0010,
        "now" to 0.0009, "look" to 0.0009, "only" to 0.0009, "come" to 0.0009,
        "its" to 0.0008, "over" to 0.0008, "think" to 0.0008, "also" to 0.0008,
        "back" to 0.0008, "after" to 0.0007, "use" to 0.0007, "two" to 0.0007,
        "how" to 0.0007, "our" to 0.0007, "work" to 0.0007, "first" to 0.0006,
        "well" to 0.0006, "way" to 0.0006, "even" to 0.0006, "new" to 0.0006,
        "want" to 0.0006, "because" to 0.0006, "any" to 0.0005, "these" to 0.0005,
        "give" to 0.0005, "day" to 0.0005, "most" to 0.0005, "us" to 0.0005,
        "is" to 0.0090, "was" to 0.0060, "are" to 0.0045, "were" to 0.0022,
        "been" to 0.0018, "has" to 0.0025, "had" to 0.0030, "am" to 0.0012,
        "did" to 0.0012, "does" to 0.0008, "very" to 0.0007, "really" to 0.0006,
        "yeah" to 0.0006, "okay" to 0.0004, "thanks" to 0.0003, "sorry" to 0.0003,
        "much" to 0.0007, "many" to 0.0005, "little" to 0.0004, "big" to 0.0004,
        "nice" to 0.0003, "great" to 0.0004, "sure" to 0.0004, "maybe" to 0.0004,
        "night" to 0.0003, "week" to 0.0003, "weekend" to 0.0002, "today" to 0.0003,
        "tomorrow" to 0.0002, "next" to 0.0004, "last" to 0.0005, "here" to 0.0006,
        "where" to 0.0005, "why" to 0.0004, "something" to 0.0004, "anything" to 0.0002,
        "nothing" to 0.0003, "everything" to 0.0002, "someone" to 0.0002,
        "feel" to 0.0004, "need" to 0.0004, "try" to 0.0003, "keep" to 0.0003,
        "find" to 0.0004, "tell" to 0.0004, "ask" to 0.0003, "seem" to 0.0003,
        "leave" to 0.0003, "put" to 0.0003, "mean" to 0.0003, "let" to 0.0003,
        "start" to 0.0003, "help" to 0.0003, "talk" to 0.0002, "turn" to 0.0002,
        "show" to 0.0003, "hear" to 0.0002, "play" to 0.0002, "run" to 0.0002,
        "move" to 0.0002, "live" to 0.0002, "believe" to 0.0002, "hold" to 0.0002,
        "bring" to 0.0002, "happen" to 0.0002, "write" to 0.0002, "sit" to 0.0002,
        "stand" to 0.0002, "lose" to 0.0002, "pay" to 0.0002, "meet" to 0.0002,
        "include" to 0.0001, "continue" to 0.0001, "set" to 0.0002, "learn" to 0.0002,
        "change" to 0.0002, "lead" to 0.0001, "understand" to 0.0002, "watch" to 0.0002,
        "follow" to 0.0001, "stop" to 0.0002, "create" to 0.0001, "speak" to 0.0001,
        "read" to 0.0002, "spend" to 0.0001, "grow" to 0.0001, "open" to 0.0002,
        "walk" to 0.0002, "win" to 0.0001, "offer" to 0.0001, "remember" to 0.0002,
        "love" to 0.0004, "consider" to 0.0001, "appear" to 0.0001, "buy" to 0.0002,
        "wait" to 0.0002, "serve" to 0.0001, "die" to 0.0001, "send" to 0.0001,
        "build" to 0.0001, "stay" to 0.0002, "fall" to 0.0001, "cut" to 0.0001,
        "reach" to 0.0001, "kill" to 0.0001, "remain" to 0.0001, "eat" to 0.0002,
        "drink" to 0.0001, "sleep" to 0.0001, "wear" to 0.0001, "drive" to 0.0001,
    )
}
