package dev.cue.draft

import dev.cue.model.CapturedContext
import dev.cue.model.ConversationStage
import dev.cue.model.MatchProfile
import dev.cue.model.Message
import dev.cue.model.ModelTier
import dev.cue.model.Platform
import dev.cue.model.PromptAnswer
import dev.cue.model.SentMessage
import dev.cue.model.Sender
import dev.cue.model.VoiceProfile
import dev.cue.voice.VoiceProfiler

/**
 * One labelled retrieval situation: what she said, what you replied, and a later
 * message that means the same thing.
 *
 * §14.9 asks for 20 hand-labelled situations where BM25 must surface the right
 * past message in the top five. [probe] is the paraphrase — the query the app
 * would actually build from a new conversation — and [mine] is the answer.
 */
data class Situation(
    val her: String,
    val mine: String,
    val probe: String,
)

object DraftFixtures {

    /** §14.9's twenty. Distinct topics, paraphrased probes, no shared nouns. */
    val SITUATIONS: List<Situation> = listOf(
        Situation(
            "have you been to that new ramen place on ossington",
            "not yet, the queue is apparently legendary",
            "the new ramen place on ossington, worth queueing for?",
        ),
        Situation(
            "i finally went to the climbing gym",
            "i fell off the same wall twice last time",
            "how was the climbing gym in the end",
        ),
        Situation(
            "the bookshop on college is my favourite place in the city",
            "i lost an entire hour in that bookshop basement",
            "which bookshop on college do you mean",
        ),
        Situation(
            "my sister's wedding is next month",
            "did you survive your sister's wedding speeches",
            "how did your sister's wedding go",
        ),
        Situation(
            "i started marathon training in january",
            "my knees filed a complaint about the marathon plan",
            "how is the marathon training going",
        ),
        Situation(
            "my cat is called pierogi",
            "pierogi is an elite name for a cat",
            "tell me more about the cat called pierogi",
        ),
        Situation(
            "i go to the film festival every year",
            "the midnight screenings are the whole point of that festival",
            "are you doing the film festival again",
        ),
        Situation(
            "i signed up for a pottery class",
            "i made one bowl and three casualties in pottery",
            "how is the pottery class going",
        ),
        Situation(
            "we hiked in banff last summer",
            "banff ruined every other hike for me",
            "was the hiking in banff as good as they say",
        ),
        Situation(
            "my sourdough starter is thriving",
            "my sourdough starter died in a heatwave",
            "is the sourdough starter still alive",
        ),
        Situation(
            "my thesis deadline is in six weeks",
            "when does the thesis actually stop being your problem",
            "how is the thesis deadline looking",
        ),
        Situation(
            "karaoke is non negotiable for me",
            "i have exactly one karaoke song and it is terrible",
            "would you actually do karaoke",
        ),
        Situation(
            "i cycle to work every day",
            "the bridge headwind on that cycle is character building",
            "do you still cycle to work in winter",
        ),
        Situation(
            "there is a jazz bar i love near dundas",
            "the jazz bar with the terrible chairs, near dundas",
            "which jazz bar near dundas",
        ),
        Situation(
            "i adopted a rescue dog in march",
            "does the rescue dog have opinions about the ferry",
            "how is the rescue dog settling in",
        ),
        Situation(
            "the night market is on this weekend",
            "the dumplings at the night market are unreasonable",
            "is the night market still running",
        ),
        Situation(
            "we did an escape room for my birthday",
            "we escaped that room with four seconds left",
            "how did the escape room go",
        ),
        Situation(
            "i went to iceland in february",
            "iceland in winter is a commitment",
            "was iceland worth it in february",
        ),
        Situation(
            "there is a board game cafe on queen",
            "i am unbeaten at that board game cafe",
            "the board game cafe on queen, any good?",
        ),
        Situation(
            "i got a tattoo last week",
            "how long did that tattoo actually take",
            "is the tattoo new",
        ),
    )

    /** Openers you have sent before, for §6.5's pattern extraction. */
    private val PAST_OPENERS = listOf(
        "ok how did you end up with a kayak",
        "genuine question about the accordion",
        "i need the full story behind the tractor",
        "the pierogi thing needs explaining",
    )

    /** Filler, so the corpus clears §4.2's fifty-message calibration line. */
    private val FILLER = listOf(
        "that tracks", "no shot", "ok fair", "i'd have said the same",
        "how was the rest of your week", "sounds about right", "that's a bold claim",
        "i'm around later if you are", "same honestly", "you're not wrong",
        "i've been meaning to do that", "ok but where's the best coffee near you",
        "genuinely what got you into it", "the walk back is the good part",
    )

    fun corpus(): List<SentMessage> {
        val situations = SITUATIONS.mapIndexed { index, situation ->
            SentMessage(
                id = "s$index",
                text = situation.mine,
                precedingTheirMessage = situation.her,
                stage = ConversationStage.ESTABLISHED,
                sentAt = 1_700_000_000_000L + index * 600_000L,
            )
        }
        val openers = PAST_OPENERS.mapIndexed { index, text ->
            SentMessage(
                id = "o$index",
                text = text,
                precedingTheirMessage = null,
                stage = ConversationStage.OPENER,
                sentAt = 1_700_100_000_000L + index * 600_000L,
            )
        }
        val filler = FILLER.mapIndexed { index, text ->
            SentMessage(
                id = "f$index",
                text = text,
                precedingTheirMessage = "how's your week going",
                stage = ConversationStage.EARLY_RAPPORT,
                sentAt = 1_700_200_000_000L + index * 600_000L,
            )
        } + (0 until 20).map { index ->
            SentMessage(
                id = "g$index",
                text = FILLER[index % FILLER.size],
                precedingTheirMessage = "what are you up to",
                stage = ConversationStage.EARLY_RAPPORT,
                sentAt = 1_700_300_000_000L + index * 600_000L,
            )
        }
        return situations + openers + filler
    }

    /** A calibrated, lowercase, unpunctuated voice measured off [corpus]. */
    fun voice(): VoiceProfile = VoiceProfiler.profile(corpus())

    fun herProfile(): MatchProfile = MatchProfile(
        displayName = "Maya",
        age = 27,
        prompts = listOf(
            PromptAnswer("Two truths and a lie", "i have broken three bones, i cannot swim, i met a prime minister"),
            PromptAnswer("My simple pleasures", "the first coffee, before anyone else is awake"),
        ),
        bio = null,
        attributes = mapOf("height" to "5'7\""),
        photoCaptions = listOf("kayak"),
        capturedAt = NOW,
    )

    fun context(
        messages: List<Pair<Sender, String>> = emptyList(),
        profile: MatchProfile = herProfile(),
        now: Long = NOW,
        conversationId: String = "c1",
    ): CapturedContext = CapturedContext(
        conversationId = conversationId,
        platform = Platform.HINGE,
        profile = profile,
        messages = messages.mapIndexed { index, (sender, text) ->
            Message(
                id = "$conversationId:$index",
                conversationId = conversationId,
                sender = sender,
                text = text,
                sentAt = now - (messages.size - index) * 3_600_000L,
                sequence = index,
            )
        },
        capturedAt = now,
        nowMillis = now,
    )

    const val NOW = 1_800_000_000_000L
}

/** A stand-in for the model: hands back scripted text and records what it was asked. */
class ScriptedEngine(
    override val tier: ModelTier = ModelTier.E2B,
    responses: List<String> = emptyList(),
    private val fallback: String = "so nowhere near water at all then",
) : InferenceEngine {

    private val queue = ArrayDeque(responses)
    val prompts = mutableListOf<String>()
    val seeds = mutableListOf<Int>()
    val temperatures = mutableListOf<Float>()

    override suspend fun generate(request: GenerationRequest): String {
        prompts += request.prompt
        seeds += request.seed
        temperatures += request.temperature
        return if (queue.isEmpty()) fallback else queue.removeFirst()
    }
}
