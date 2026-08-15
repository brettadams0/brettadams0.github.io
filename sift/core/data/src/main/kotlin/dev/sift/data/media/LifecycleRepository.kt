package dev.sift.data.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sift.data.db.EditJob
import dev.sift.data.db.LifecycleEvent
import dev.sift.data.db.SiftDatabase
import dev.sift.model.LifecycleState
import dev.sift.model.RejectionReason
import dev.sift.model.Verdict
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The asset lifecycle of §9.1, and the only place [LifecycleState] changes.
 *
 * ```
 * UNTRIAGED
 *    ├─ toss ──→ TRASHED_AT_TRIAGE                 (deletion batch 1, immediate)
 *    └─ keep ──→ QUEUED_FOR_GRADE
 *                   ↓ auto, §9.2
 *                GRADING ──→ PENDING_REVIEW
 *                               ├─ approve ─→ APPROVED ─→ ORIGINAL_TRASHED (batch 2)
 *                               ├─ reject  ─→ REJECTED  (graded discarded, original kept)
 *                               └─ regrade ─→ QUEUED_FOR_GRADE
 * ```
 *
 * **Every transition is written to Room before any filesystem operation**, so
 * process death mid-transition is recoverable rather than ambiguous (§9.1). The
 * append-only event log is what makes recovery decidable: an asset that reached
 * `APPROVED` but never reached `ORIGINAL_TRASHED` is resumable, and one that
 * reached `ORIGINAL_TRASHED` is never trashed twice (§14.10).
 */
@Singleton
class LifecycleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SiftDatabase,
    private val trash: TrashCoordinator,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** Legal transitions. Anything absent here is a bug, not a state. */
    private val allowed: Map<LifecycleState, Set<LifecycleState>> = mapOf(
        LifecycleState.UNTRIAGED to setOf(
            LifecycleState.TRASHED_AT_TRIAGE,
            LifecycleState.QUEUED_FOR_GRADE,
            LifecycleState.DO_NOT_GRADE,
            LifecycleState.UNREADABLE,
        ),
        LifecycleState.QUEUED_FOR_GRADE to setOf(
            LifecycleState.GRADING,
            LifecycleState.DO_NOT_GRADE,
            LifecycleState.UNREADABLE,
        ),
        LifecycleState.GRADING to setOf(
            LifecycleState.PENDING_REVIEW,
            LifecycleState.QUEUED_FOR_GRADE,
            LifecycleState.UNREADABLE,
        ),
        LifecycleState.PENDING_REVIEW to setOf(
            LifecycleState.APPROVED,
            LifecycleState.REJECTED,
            LifecycleState.QUEUED_FOR_GRADE,
            LifecycleState.DO_NOT_GRADE,
        ),
        LifecycleState.APPROVED to setOf(LifecycleState.ORIGINAL_TRASHED),
        LifecycleState.REJECTED to setOf(
            LifecycleState.QUEUED_FOR_GRADE,
            LifecycleState.DO_NOT_GRADE,
        ),
        // Terminal.
        LifecycleState.ORIGINAL_TRASHED to emptySet(),
        LifecycleState.TRASHED_AT_TRIAGE to emptySet(),
        LifecycleState.DO_NOT_GRADE to setOf(LifecycleState.QUEUED_FOR_GRADE),
        LifecycleState.UNREADABLE to emptySet(),
    )

    fun canTransition(from: LifecycleState, to: LifecycleState): Boolean =
        to in (allowed[from] ?: emptySet())

    /**
     * Move an asset, recording the transition first.
     *
     * Returns false for an illegal transition rather than throwing: a race
     * between the grading worker and the review screen should be a no-op, not a
     * crash mid-batch (§12).
     */
    suspend fun transition(assetId: Long, to: LifecycleState, note: String? = null): Boolean {
        val asset = db.mediaAssets().byId(assetId) ?: return false
        if (asset.lifecycleState == to) return true
        if (!canTransition(asset.lifecycleState, to)) return false

        db.lifecycleEvents().insert(
            LifecycleEvent(
                assetId = assetId,
                from = asset.lifecycleState,
                to = to,
                at = System.currentTimeMillis(),
                note = note,
            ),
        )
        db.mediaAssets().setState(assetId, to)
        return true
    }

    // ---- Triage (§8) -------------------------------------------------------

    /** Decisions write to Room only. Nothing is trashed until commit. */
    suspend fun recordDecision(assetId: Long, verdict: Verdict) {
        db.triageDecisions().upsert(
            dev.sift.data.db.TriageDecision(
                assetId = assetId,
                verdict = verdict,
                decidedAt = System.currentTimeMillis(),
                committed = false,
            ),
        )
        db.mediaAssets().markSeen(assetId, System.currentTimeMillis())
    }

    /** §8 — undo, last ten decisions. A swipe deck without undo is hostile. */
    suspend fun undoLast(): Long? {
        val recent = db.triageDecisions().mostRecent(UNDO_DEPTH)
        val last = recent.firstOrNull { !it.committed } ?: return null
        db.triageDecisions().delete(last.assetId)
        db.mediaAssets().markSeen(last.assetId, 0L)
        return last.assetId
    }

    fun pendingTossCount(): Flow<Int> = db.triageDecisions().pendingCount(Verdict.TOSS)

    /**
     * Build **deletion batch 1**: the triage rejects.
     *
     * Never merged with batch 2 (§8, trap #16).
     */
    suspend fun buildTriageTrashRequest(): TrashCoordinator.Request? {
        val decisions = db.triageDecisions().uncommittedWith(Verdict.TOSS)
        if (decisions.isEmpty()) return null

        val ids = decisions.map { it.assetId }
        val assets = db.mediaAssets().byIds(ids)
        val bytes = assets.sumOf { it.sizeBytes }

        return trash.buildRequest(
            batch = TrashCoordinator.Batch.TRIAGE_REJECTS,
            assetIds = assets.map { it.id },
            uris = assets.map { Uri.parse(it.uri) },
            bytesFreed = bytes,
        )
    }

    /**
     * Apply the result of batch 1.
     *
     * §8 and §14.8: a cancelled dialog must leave every decision intact and
     * uncommitted, so the user can retry without re-triaging.
     */
    suspend fun onTriageTrashResult(request: TrashCoordinator.Request, granted: Boolean) {
        if (!granted) return

        for (id in request.assetIds) {
            transition(id, LifecycleState.TRASHED_AT_TRIAGE, "deletion batch 1")
        }
        db.triageDecisions().markCommitted(request.assetIds)

        // Keepers move on to grading in the same commit.
        val keeps = db.triageDecisions().uncommittedWith(Verdict.KEEP)
        for (decision in keeps) {
            transition(decision.assetId, LifecycleState.QUEUED_FOR_GRADE, "kept at triage")
        }
        db.triageDecisions().markCommitted(keeps.map { it.assetId })
    }

    // ---- Review and approval (§9) -----------------------------------------

    fun pendingReview(): Flow<List<EditJob>> = db.editJobs().pendingReview()

    suspend fun pendingReviewCount(): Int = db.editJobs().pendingReviewCount()

    suspend fun atBacklogCap(): Boolean =
        pendingReviewCount() >= MediaStoreRepository.PENDING_REVIEW_CAP

    /**
     * Approve a graded result.
     *
     * Approval alone does **not** trash anything. It moves the asset to
     * `APPROVED`, which is the only state from which batch 2 can be built, and
     * every candidate is re-checked against [ApprovalGuard] at that point — not
     * here, and not at write time (§9.3 invariant 3).
     */
    suspend fun approve(job: EditJob): Boolean {
        db.editJobs().markApproved(job.id, System.currentTimeMillis())
        return transition(job.sourceAssetId, LifecycleState.APPROVED, "graded result approved")
    }

    /** §9.5 — rejection is the only tuning signal there is. Always capture why. */
    suspend fun reject(job: EditJob, reason: RejectionReason?): Boolean {
        db.editJobs().markRejected(job.id, System.currentTimeMillis(), reason)
        // The graded export is discarded; the original is kept, untouched.
        job.outputUri?.let { runCatching { resolver.delete(Uri.parse(it), null, null) } }
        return transition(job.sourceAssetId, LifecycleState.REJECTED, "rejected: $reason")
    }

    suspend fun requeueForGrade(assetId: Long, note: String): Boolean =
        transition(assetId, LifecycleState.QUEUED_FOR_GRADE, note)

    suspend fun markDoNotGrade(assetId: Long): Boolean =
        transition(assetId, LifecycleState.DO_NOT_GRADE, "user marked right as shot")

    /**
     * Build **deletion batch 2**: the originals of approved keepers.
     *
     * Every candidate passes all five §9.3 invariants or it is not in the batch.
     * Anything refused with `requeueGrade` is put back in the grading queue and
     * surfaced, rather than silently dropped (§12).
     */
    suspend fun buildApprovedOriginalsRequest(): ApprovedOriginalsBatch {
        val approved = db.mediaAssets().inStateNow(LifecycleState.APPROVED)
        val eligibleIds = mutableListOf<Long>()
        val eligibleUris = mutableListOf<Uri>()
        val refusals = mutableListOf<Refusal>()
        var bytes = 0L

        for (asset in approved) {
            val job = db.editJobs().latestForAsset(asset.id)
            if (job == null) {
                refusals += Refusal(asset.id, "no grade job on record")
                continue
            }

            val verdict = ApprovalGuard.evaluate(
                resolver = resolver,
                job = job,
                expectedWidth = asset.width,
                expectedHeight = asset.height,
                userApproved = job.approvedAt != null,
            )

            when (verdict) {
                is ApprovalGuard.Verdict.Allowed -> {
                    eligibleIds += asset.id
                    eligibleUris += Uri.parse(asset.uri)
                    bytes += asset.sizeBytes
                }
                is ApprovalGuard.Verdict.Refused -> {
                    refusals += Refusal(asset.id, verdict.reason)
                    if (verdict.requeueGrade) {
                        requeueForGrade(asset.id, "output failed verification: ${verdict.reason}")
                    }
                }
            }
        }

        val request = trash.buildRequest(
            batch = TrashCoordinator.Batch.APPROVED_ORIGINALS,
            assetIds = eligibleIds,
            uris = eligibleUris,
            bytesFreed = bytes,
        )
        return ApprovedOriginalsBatch(request, refusals)
    }

    suspend fun onApprovedOriginalsResult(request: TrashCoordinator.Request, granted: Boolean) {
        if (!granted) return
        val now = System.currentTimeMillis()
        for (id in request.assetIds) {
            // Recorded before the state change so a crash between the two leaves
            // the asset resumable rather than double-trashed.
            db.editJobs().latestForAsset(id)?.let { db.editJobs().markOriginalTrashed(it.id, now) }
            transition(id, LifecycleState.ORIGINAL_TRASHED, "deletion batch 2")
        }
    }

    /**
     * §14.10 — resume after process death.
     *
     * An asset that reached `APPROVED` but never `ORIGINAL_TRASHED` is simply
     * still approved; it will be picked up by the next batch build, which
     * re-runs all five invariants. Nothing needs undoing, and crucially nothing
     * is trashed twice, because `ORIGINAL_TRASHED` is terminal and
     * [canTransition] refuses to leave it.
     */
    suspend fun strandedApprovals(): List<Long> =
        db.mediaAssets().inStateNow(LifecycleState.APPROVED).map { it.id }

    data class Refusal(val assetId: Long, val reason: String)

    data class ApprovedOriginalsBatch(
        val request: TrashCoordinator.Request?,
        val refusals: List<Refusal>,
    )

    companion object {
        const val UNDO_DEPTH = 10
    }
}
