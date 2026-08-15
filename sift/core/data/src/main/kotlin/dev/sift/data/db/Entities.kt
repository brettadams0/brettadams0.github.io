package dev.sift.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.sift.model.ContentClass
import dev.sift.model.GradeProfile
import dev.sift.model.JobState
import dev.sift.model.LifecycleState
import dev.sift.model.RejectionReason
import dev.sift.model.Verdict

/**
 * The data model of §5.
 *
 * Room entities live here rather than in `:core:model` because §4.1 defines that
 * module as "shared immutable types, **no dependencies**" — pulling
 * `androidx.room` into it would put an Android dependency underneath
 * `:core:imaging` and break the module that has to stay unit-testable off-device.
 * The field lists are exactly as §5 specifies them.
 */
@Entity(
    tableName = "media_assets",
    indices = [
        Index("lifecycleState"),
        Index("clusterId"),
        Index("dateTaken"),
        Index("dHash"),
    ],
)
data class MediaAsset(
    /** `MediaStore._ID`. */
    @PrimaryKey val id: Long,
    val uri: String,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
    /** 64-bit perceptual hash (§7). */
    val dHash: Long,
    val clusterId: String?,
    /** Serialised [dev.sift.model.FrameAnalysis] (§6.3). */
    val analysisJson: String?,
    val contentClass: ContentClass?,
    /** §9.1 — the single source of truth. */
    val lifecycleState: LifecycleState,
    /** Null means never triaged. */
    val seenAt: Long?,
)

@Entity(tableName = "triage_decisions")
data class TriageDecision(
    @PrimaryKey val assetId: Long,
    val verdict: Verdict,
    val decidedAt: Long,
    /**
     * False until the batched trash request actually succeeds.
     *
     * §8: a cancelled dialog must leave decisions intact in Room, not silently
     * discard them. This flag is what makes that recoverable — decisions survive
     * as uncommitted and the next commit picks them up.
     */
    val committed: Boolean,
)

@Entity(
    tableName = "edit_jobs",
    indices = [Index("sourceAssetId"), Index("state")],
)
data class EditJob(
    @PrimaryKey val id: String,
    val sourceAssetId: Long,
    val outputUri: String?,
    val profile: GradeProfile,
    val profileWasManual: Boolean,
    /** Every parameter used (§6.3). Not optional. */
    val derivedParamsJson: String,
    /** 1.0 = none. */
    val upscaleFactor: Float,
    /** §6.12 gate results. Not optional. */
    val gateResultsJson: String,
    /**
     * §9.3 invariant 2, and trap #14.
     *
     * When true the "graded" export is just a re-encode of the source, so
     * trashing the original would leave a generation-loss JPEG as the only
     * master. Assets in this state must never be offered for original-trashing.
     */
    val fellBackToOriginal: Boolean,
    val processingMs: Long,
    val state: JobState,

    // Review & approval — §9
    val approvedAt: Long?,
    val rejectedAt: Long?,
    /** §9.5 — the only tuning signal there is. */
    val rejectionReason: RejectionReason?,
    val originalTrashedAt: Long?,
)

/**
 * Append-only log of lifecycle transitions.
 *
 * §9.1 requires every transition to be written before any filesystem operation
 * so process death mid-transition is recoverable. This table is what makes
 * "recoverable" mean something specific: on relaunch the app can see that an
 * asset reached `APPROVED` but never reached `ORIGINAL_TRASHED` and resume,
 * without ever double-trashing (§14.10).
 */
@Entity(tableName = "lifecycle_events", indices = [Index("assetId")])
data class LifecycleEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val from: LifecycleState,
    val to: LifecycleState,
    val at: Long,
    val note: String? = null,
)
