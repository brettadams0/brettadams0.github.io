package dev.cue.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.cue.model.ConversationStage
import dev.cue.model.DraftAction
import dev.cue.model.ModelTier
import dev.cue.model.Platform
import dev.cue.model.Sender
import dev.cue.model.Strategy

/**
 * §11's data model, as stored.
 *
 * Separate types from the `:core:model` domain classes rather than annotating
 * those. The domain types are pure Kotlin and serialisable — the Chrome
 * extension reads one of them as JSON (§3.4) — and making them Room entities
 * would drag an Android dependency into the module every pure test uses.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val platform: Platform,
    /**
     * §10's posture. Her display name if the header was in frame, otherwise a
     * generated label. Never a real name Cue inferred from anywhere else.
     */
    val matchPseudonym: String,
    val profileJson: String?,
    val stage: ConversationStage,
    val lastCapturedAt: Long,
    val lastTheirMessageAt: Long?,
    val lastMyMessageAt: Long?,
    /** §10: halts all capture and processing for this match. */
    val excluded: Boolean = false,
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "sequence"], unique = true)],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val sender: Sender,
    val text: String,
    /** Usually null. Trap 12: OCR'd relative times are not evidence. */
    val sentAt: Long?,
    val sequence: Int,
    val attributionConfidence: Float,
)

/**
 * Your corpus: the messages the voice profile and retrieval are built from.
 *
 * Deliberately a separate table from [MessageEntity] rather than a view over
 * `sender = 'ME'`. Two reasons. §4.2 excludes anything below 0.8 attribution
 * confidence, and §8 adds entries that were never part of a captured
 * conversation at all — the text you sent after editing a draft, at double
 * weight. A view could express the first and not the second.
 */
@Entity(tableName = "corpus")
data class CorpusEntryEntity(
    @PrimaryKey val id: String,
    val text: String,
    val precedingTheirMessage: String?,
    val stage: ConversationStage?,
    val sentAt: Long?,
    val weight: Int,
    /** Null for onboarding imports, set for anything the outcome loop added. */
    val sourceConversationId: String?,
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val strategy: Strategy,
    /** §11: pre-compiler, so "model or compiler?" is answerable. */
    val rawModelOutput: String,
    val text: String,
    val transformsApplied: String,
    val gateResultsJson: String,
    val modelTier: ModelTier,
    val inferenceMs: Long,
    val createdAt: Long,
)

@Entity(tableName = "outcomes")
data class OutcomeEntity(
    @PrimaryKey val draftId: String,
    val conversationId: String,
    val variantStrategy: Strategy,
    val action: DraftAction,
    val editDistance: Int?,
    val finalText: String?,
    /** Resolved on the next capture of that conversation, not at send time. */
    val gotReply: Boolean?,
    val replyLatencyMs: Long?,
    val recordedAt: Long,
)

/**
 * The measured profile, stored as one row.
 *
 * Kept as JSON rather than columns because §4.1 will grow fields, and a
 * migration per feature is a poor trade for a single-row table that is rewritten
 * whole every time the corpus changes.
 */
@Entity(tableName = "voice_profile")
data class VoiceProfileEntity(
    @PrimaryKey val id: Int = SINGLETON,
    val profileJson: String,
    val computedAt: Long,
) {
    companion object {
        const val SINGLETON = 1
    }
}
