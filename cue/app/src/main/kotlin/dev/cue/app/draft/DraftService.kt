package dev.cue.app.draft

import dev.cue.data.repo.CueRepository
import dev.cue.draft.DraftPipeline
import dev.cue.draft.DraftSet
import dev.cue.draft.GenerationBudget
import dev.cue.draft.OutcomeLoop
import dev.cue.inference.EngineHolder
import dev.cue.model.CapturedContext
import dev.cue.model.Draft
import dev.cue.model.DraftAction
import dev.cue.model.DraftOutcome
import dev.cue.model.MatchProfile
import dev.cue.model.VoiceProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles a [DraftPipeline] for one request and records what came of it.
 *
 * The pipeline is built per call rather than injected because two of its three
 * inputs change underneath it: the corpus grows every time you send an edited
 * draft (§8), and the voice profile is recomputed when it does. A long-lived
 * pipeline would hold a BM25 index built before your last correction, which is
 * precisely the correction §8 says is the most valuable signal in the app.
 */
@Singleton
class DraftService @Inject constructor(
    private val repository: CueRepository,
    private val engines: EngineHolder,
    private val budget: GenerationBudget,
) {

    data class Outcome(
        val set: DraftSet,
        /** §12: past twenty an hour, warn — do not block. */
        val overThermalBudget: Boolean,
        val generationsRemaining: Int,
    )

    suspend fun draft(context: CapturedContext, now: Long): Outcome {
        val voice = repository.currentVoiceProfile() ?: VoiceProfile.BASELINE
        val corpus = repository.corpus()

        val pipeline = DraftPipeline(
            voice = voice,
            corpus = corpus,
            engine = engines.asEngineOrNull(),
            clock = { System.currentTimeMillis() },
        )

        val stored = repository.conversation(context.conversationId)
        val set = pipeline.draft(context, lastTheirMessageAt = stored?.lastTheirMessageAt)
        repository.saveDrafts(set.drafts)

        val overBudget = budget.exhausted(now)
        set.drafts.count { it.inferenceMs > 0 }.let { generated ->
            repeat(generated) { budget.record(now) }
        }

        return Outcome(
            set = set,
            overThermalBudget = overBudget,
            generationsRemaining = budget.remaining(now),
        )
    }

    /**
     * §8. Records what you did with a draft, and folds an edit back into the
     * corpus at double weight.
     *
     * The recomputation of the voice profile happens here, synchronously with the
     * send, because §8's payoff depends on it: "your corrections become future
     * few-shot examples". A nightly job would work too and would mean the next
     * draft — the one you write ten seconds later, in the same conversation —
     * still does not know what you just fixed.
     */
    suspend fun record(
        draft: Draft,
        action: DraftAction,
        finalText: String?,
        precedingTheirMessage: String?,
        now: Long,
    ) {
        val editDistance = finalText?.let { OutcomeLoop.editDistance(draft.text, it) }
        val outcome = DraftOutcome(
            draftId = draft.id,
            variantStrategy = draft.strategy,
            action = action,
            editDistance = editDistance,
            finalText = finalText,
        )
        repository.recordOutcome(outcome, draft.conversationId, now)

        val entry = OutcomeLoop.toCorpusEntry(
            outcome = outcome,
            draft = draft,
            precedingTheirMessage = precedingTheirMessage,
            stage = repository.conversation(draft.conversationId)?.stage,
            sentAt = now,
        ) ?: return

        repository.saveCorpus(listOf(entry), draft.conversationId)
        recomputeVoiceProfile(now)
    }

    suspend fun recomputeVoiceProfile(now: Long) {
        val corpus = repository.corpus()
        if (corpus.isEmpty()) return
        repository.saveVoiceProfile(dev.cue.voice.VoiceProfiler.profile(corpus), now)
    }

    /** §5.4: a profile capture updates her side without touching the thread. */
    suspend fun updateProfile(conversationId: String, profile: MatchProfile, now: Long) {
        val stored = repository.conversation(conversationId) ?: return
        val messages = repository.messages(conversationId)
        repository.saveCapture(
            context = CapturedContext(
                conversationId = conversationId,
                platform = stored.platform,
                profile = profile,
                messages = messages,
                capturedAt = now,
                nowMillis = now,
            ),
            pseudonym = stored.matchPseudonym,
            stage = stored.stage,
        )
    }
}
