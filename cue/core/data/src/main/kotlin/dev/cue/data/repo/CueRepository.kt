package dev.cue.data.repo

import dev.cue.data.db.ConversationEntity
import dev.cue.data.db.CorpusEntryEntity
import dev.cue.data.db.CueDatabase
import dev.cue.data.db.DraftEntity
import dev.cue.data.db.MessageEntity
import dev.cue.data.db.OutcomeEntity
import dev.cue.data.db.VoiceProfileEntity
import dev.cue.model.CapturedContext
import dev.cue.model.Conversation
import dev.cue.model.ConversationStage
import dev.cue.model.Draft
import dev.cue.model.DraftOutcome
import dev.cue.model.GateReport
import dev.cue.model.MatchProfile
import dev.cue.model.Message
import dev.cue.model.Platform
import dev.cue.model.SentMessage
import dev.cue.model.Sender
import dev.cue.model.StrategyStats
import dev.cue.model.VoiceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * The one door between the tested core and the database.
 *
 * Mapping happens here rather than in the ViewModels so that the domain types
 * §4–§9 operate on never acquire a storage shape, and so that "what does the app
 * know about her" has exactly one answer to read.
 */
class CueRepository(
    private val database: CueDatabase,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // -- conversations ----------------------------------------------------

    fun activeConversations(): Flow<List<Conversation>> =
        database.conversations().active().map { rows -> rows.map { it.toDomain() } }

    /** §9's cheapest high-value screen. */
    fun ballInYourCourt(): Flow<List<Conversation>> =
        database.conversations().ballInYourCourt().map { rows -> rows.map { it.toDomain() } }

    suspend fun conversation(id: String): Conversation? =
        database.conversations().byId(id)?.toDomain()

    /**
     * Writes a capture, and refuses to write one for an excluded match.
     *
     * §10's exclude toggle is checked here rather than at the UI layer because
     * this is the only place that could break it: a share-sheet intent can arrive
     * for any conversation at any time, including one the user has already asked
     * the app to stop seeing.
     */
    suspend fun saveCapture(
        context: CapturedContext,
        pseudonym: String,
        stage: ConversationStage,
    ): Boolean {
        val existing = database.conversations().byId(context.conversationId)
        if (existing?.excluded == true) return false

        database.conversations().upsert(
            ConversationEntity(
                id = context.conversationId,
                platform = context.platform,
                matchPseudonym = existing?.matchPseudonym ?: pseudonym,
                profileJson = json.encodeToString(MatchProfile.serializer(), context.profile),
                stage = stage,
                lastCapturedAt = context.capturedAt,
                lastTheirMessageAt = context.theirMessages.lastOrNull()?.sentAt
                    ?: existing?.lastTheirMessageAt,
                lastMyMessageAt = context.myMessages.lastOrNull()?.sentAt
                    ?: existing?.lastMyMessageAt,
                excluded = false,
            ),
        )
        database.messages().replaceAll(
            context.conversationId,
            context.messages.map { it.toEntity() },
        )

        // §4.2: only well-attributed messages of yours enter the corpus. A
        // misattributed one poisons the profile silently; an excluded one costs
        // nothing.
        database.corpus().upsert(
            context.messages
                .filter { it.trustedForVoiceProfile }
                .map { message ->
                    CorpusEntryEntity(
                        id = "capture:${message.id}",
                        text = message.text,
                        precedingTheirMessage = precedingTheirMessage(context.messages, message),
                        stage = stage,
                        sentAt = message.sentAt,
                        weight = 1,
                        sourceConversationId = context.conversationId,
                    )
                },
        )
        return true
    }

    suspend fun exclude(conversationId: String) =
        database.conversations().excludeAndPurge(conversationId)

    suspend fun messages(conversationId: String): List<Message> =
        database.messages().forConversation(conversationId).map { it.toDomain() }

    // -- corpus and voice profile -------------------------------------------

    suspend fun corpus(): List<SentMessage> = database.corpus().all().map { it.toDomain() }

    fun corpusSize(): Flow<Int> = database.corpus().size()

    suspend fun saveCorpus(entries: List<SentMessage>, sourceConversationId: String?) =
        database.corpus().upsert(
            entries.map { entry ->
                CorpusEntryEntity(
                    id = entry.id,
                    text = entry.text,
                    precedingTheirMessage = entry.precedingTheirMessage,
                    stage = entry.stage,
                    sentAt = entry.sentAt,
                    weight = entry.weight,
                    sourceConversationId = sourceConversationId,
                )
            },
        )

    fun voiceProfile(): Flow<VoiceProfile?> =
        database.voiceProfile().observe().map { row ->
            row?.let { json.decodeFromString(VoiceProfile.serializer(), it.profileJson) }
        }

    suspend fun currentVoiceProfile(): VoiceProfile? =
        database.voiceProfile().current()
            ?.let { json.decodeFromString(VoiceProfile.serializer(), it.profileJson) }

    suspend fun saveVoiceProfile(profile: VoiceProfile, computedAt: Long) =
        database.voiceProfile().upsert(
            VoiceProfileEntity(
                profileJson = json.encodeToString(VoiceProfile.serializer(), profile),
                computedAt = computedAt,
            ),
        )

    // -- drafts and outcomes -------------------------------------------------

    suspend fun saveDrafts(drafts: List<Draft>) =
        database.drafts().insertAll(drafts.map { it.toEntity() })

    fun drafts(conversationId: String): Flow<List<Draft>> =
        database.drafts().forConversation(conversationId).map { rows -> rows.map { it.toDomain() } }

    suspend fun draft(id: String): Draft? = database.drafts().byId(id)?.toDomain()

    suspend fun recordOutcome(outcome: DraftOutcome, conversationId: String, at: Long) =
        database.outcomes().upsert(
            OutcomeEntity(
                draftId = outcome.draftId,
                conversationId = conversationId,
                variantStrategy = outcome.variantStrategy,
                action = outcome.action,
                editDistance = outcome.editDistance,
                finalText = outcome.finalText,
                gotReply = outcome.gotReply,
                replyLatencyMs = outcome.replyLatencyMs,
                recordedAt = at,
            ),
        )

    suspend fun outcomes(): List<DraftOutcome> = database.outcomes().all().map { it.toDomain() }

    fun observeOutcomes(): Flow<List<DraftOutcome>> =
        database.outcomes().observe().map { rows -> rows.map { it.toDomain() } }

    /**
     * §8: resolve `gotReply` against a fresh capture.
     *
     * "Did she reply" is not knowable at send time and is not worth asking the
     * user, so it is inferred the next time that conversation is captured: any
     * message from her after the send is a reply.
     */
    suspend fun resolveReplies(conversationId: String, capturedAt: Long) {
        val pending = database.outcomes().awaitingReply(conversationId)
        if (pending.isEmpty()) return
        val hers = database.messages().forConversation(conversationId)
            .filter { it.sender == Sender.THEM }
        pending.forEach { outcome ->
            val reply = hers.lastOrNull { message ->
                message.sentAt != null && message.sentAt > outcome.recordedAt
            }
            if (reply != null) {
                database.outcomes().resolveReply(
                    draftId = outcome.draftId,
                    gotReply = true,
                    latencyMs = reply.sentAt?.minus(outcome.recordedAt),
                )
            } else if (capturedAt - outcome.recordedAt > NO_REPLY_AFTER_MILLIS) {
                // Silence is only evidence once it has had time to be silence.
                database.outcomes().resolveReply(outcome.draftId, gotReply = false, latencyMs = null)
            }
        }
    }

    suspend fun strategyStats(): List<StrategyStats> =
        dev.cue.data.repo.StatsSupport.stats(outcomes())

    // -- mapping ------------------------------------------------------------

    private fun precedingTheirMessage(messages: List<Message>, message: Message): String? =
        messages.lastOrNull { it.sequence < message.sequence && it.sender == Sender.THEM }?.text

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        platform = platform,
        matchPseudonym = matchPseudonym,
        profile = profileJson?.let { json.decodeFromString(MatchProfile.serializer(), it) },
        stage = stage,
        lastCapturedAt = lastCapturedAt,
        lastTheirMessageAt = lastTheirMessageAt,
        lastMyMessageAt = lastMyMessageAt,
        excluded = excluded,
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        sender = sender,
        text = text,
        sentAt = sentAt,
        sequence = sequence,
        attributionConfidence = attributionConfidence,
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        conversationId = conversationId,
        sender = sender,
        text = text,
        sentAt = sentAt,
        sequence = sequence,
        attributionConfidence = attributionConfidence,
    )

    private fun CorpusEntryEntity.toDomain() = SentMessage(
        id = id,
        text = text,
        precedingTheirMessage = precedingTheirMessage,
        stage = stage,
        sentAt = sentAt,
        weight = weight,
    )

    private fun Draft.toEntity() = DraftEntity(
        id = id,
        conversationId = conversationId,
        strategy = strategy,
        rawModelOutput = rawModelOutput,
        text = text,
        transformsApplied = transformsApplied.joinToString(",") { it.name },
        gateResultsJson = json.encodeToString(GateReport.serializer(), gates),
        modelTier = modelTier,
        inferenceMs = inferenceMs,
        createdAt = createdAt,
    )

    private fun DraftEntity.toDomain() = Draft(
        id = id,
        conversationId = conversationId,
        strategy = strategy,
        rawModelOutput = rawModelOutput,
        text = text,
        transformsApplied = transformsApplied.split(",")
            .filter { it.isNotBlank() }
            .map { dev.cue.model.VoiceTransform.valueOf(it) },
        gates = json.decodeFromString(GateReport.serializer(), gateResultsJson),
        modelTier = modelTier,
        inferenceMs = inferenceMs,
        createdAt = createdAt,
    )

    private fun OutcomeEntity.toDomain() = DraftOutcome(
        draftId = draftId,
        variantStrategy = variantStrategy,
        action = action,
        editDistance = editDistance,
        finalText = finalText,
        gotReply = gotReply,
        replyLatencyMs = replyLatencyMs,
    )

    private companion object {
        /** §9's stale cutoff, reused: past a week, no reply is the answer. */
        const val NO_REPLY_AFTER_MILLIS = 7 * 86_400_000L
    }
}

/**
 * §8's stats, computed here rather than pulled from `:core:draft`.
 *
 * `:core:data` deliberately does not depend on the drafting module — the arrow
 * runs the other way at every other layer, and one convenience import would make
 * the storage layer part of the pipeline's dependency graph.
 */
internal object StatsSupport {
    fun stats(outcomes: List<DraftOutcome>): List<StrategyStats> =
        outcomes.groupBy { it.variantStrategy }
            .map { (strategy, group) ->
                StrategyStats(
                    strategy = strategy,
                    shown = group.size,
                    sent = group.count { it.action != dev.cue.model.DraftAction.DISCARDED },
                    replied = group.count { it.gotReply == true },
                )
            }
}
