package dev.sift.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.sift.model.GradeProfile
import dev.sift.model.JobState
import dev.sift.model.LifecycleState
import dev.sift.model.RejectionReason
import dev.sift.model.Verdict
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaAssetDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(assets: List<MediaAsset>)

    @Update
    suspend fun update(asset: MediaAsset)

    @Query("SELECT * FROM media_assets WHERE id = :id")
    suspend fun byId(id: Long): MediaAsset?

    @Query("SELECT * FROM media_assets WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<MediaAsset>

    @Query("SELECT * FROM media_assets ORDER BY dateTaken DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<MediaAsset>

    /** The triage deck: never seen, newest first. */
    @Query(
        """
        SELECT * FROM media_assets
        WHERE lifecycleState = :untriaged AND seenAt IS NULL
        ORDER BY dateTaken DESC
        LIMIT :limit
        """,
    )
    fun deck(
        limit: Int = 200,
        untriaged: LifecycleState = LifecycleState.UNTRIAGED,
    ): Flow<List<MediaAsset>>

    @Query("SELECT COUNT(*) FROM media_assets WHERE lifecycleState = :state")
    fun countIn(state: LifecycleState): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_assets WHERE seenAt IS NOT NULL")
    fun seenCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_assets")
    fun totalCount(): Flow<Int>

    @Query("SELECT * FROM media_assets WHERE lifecycleState = :state ORDER BY dateTaken DESC")
    fun inState(state: LifecycleState): Flow<List<MediaAsset>>

    @Query("SELECT * FROM media_assets WHERE lifecycleState = :state ORDER BY dateTaken DESC")
    suspend fun inStateNow(state: LifecycleState): List<MediaAsset>

    @Query("SELECT * FROM media_assets WHERE clusterId = :clusterId ORDER BY dateTaken ASC")
    suspend fun inCluster(clusterId: String): List<MediaAsset>

    @Query("SELECT * FROM media_assets WHERE analysisJson IS NULL LIMIT :limit")
    suspend fun needingAnalysis(limit: Int): List<MediaAsset>

    @Query("UPDATE media_assets SET lifecycleState = :state WHERE id = :id")
    suspend fun setState(id: Long, state: LifecycleState)

    @Query("UPDATE media_assets SET seenAt = :at WHERE id = :id")
    suspend fun markSeen(id: Long, at: Long)

    /**
     * Put an asset back in the deck.
     *
     * Must set `seenAt` to NULL, not to 0: the deck query filters on
     * `seenAt IS NULL`, so a zero timestamp still reads as "already triaged"
     * and the photo would vanish from the deck permanently even though its
     * decision had been reversed.
     */
    @Query("UPDATE media_assets SET seenAt = NULL WHERE id = :id")
    suspend fun clearSeen(id: Long)

    @Query("UPDATE media_assets SET clusterId = :clusterId WHERE id = :id")
    suspend fun setCluster(id: Long, clusterId: String?)

    /** Arm a one-shot override for the next grade of this asset (§9.5). */
    @Query(
        "UPDATE media_assets SET pendingProfile = :profile, pendingStrengthScale = :strength " +
            "WHERE id = :id",
    )
    suspend fun setRegradeOverride(id: Long, profile: GradeProfile?, strength: Float?)

    /** Clear it once the grade has consumed it, so a later grade is unaffected. */
    @Query("UPDATE media_assets SET pendingProfile = NULL, pendingStrengthScale = NULL WHERE id = :id")
    suspend fun clearRegradeOverride(id: Long)

    @Query("UPDATE media_assets SET analysisJson = :json, contentClass = :contentClass, dHash = :dHash WHERE id = :id")
    suspend fun setAnalysis(
        id: Long,
        json: String,
        contentClass: dev.sift.model.ContentClass?,
        dHash: Long,
    )

    @Query("SELECT SUM(sizeBytes) FROM media_assets WHERE id IN (:ids)")
    suspend fun totalBytes(ids: List<Long>): Long?
}

@Dao
interface TriageDecisionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(decision: TriageDecision)

    @Query("DELETE FROM triage_decisions WHERE assetId = :assetId")
    suspend fun delete(assetId: Long)

    @Query("SELECT * FROM triage_decisions WHERE assetId = :assetId")
    suspend fun byAsset(assetId: Long): TriageDecision?

    @Query("SELECT * FROM triage_decisions WHERE committed = 0 ORDER BY decidedAt ASC")
    suspend fun uncommitted(): List<TriageDecision>

    @Query("SELECT * FROM triage_decisions WHERE committed = 0 AND verdict = :verdict")
    suspend fun uncommittedWith(verdict: Verdict): List<TriageDecision>

    @Query("SELECT COUNT(*) FROM triage_decisions WHERE committed = 0 AND verdict = :verdict")
    fun pendingCount(verdict: Verdict): Flow<Int>

    @Query("UPDATE triage_decisions SET committed = 1 WHERE assetId IN (:ids)")
    suspend fun markCommitted(ids: List<Long>)

    /** Undo support — §8 requires the last ten decisions to be reversible. */
    @Query("SELECT * FROM triage_decisions ORDER BY decidedAt DESC LIMIT :limit")
    suspend fun mostRecent(limit: Int): List<TriageDecision>

    /**
     * Uncommitted decisions, newest first, as a live count.
     *
     * Undo availability is derived from this rather than from an in-memory
     * stack: a ViewModel-scoped stack silently empties on process death or a
     * screen rotation, so the undo control would disappear while the decisions
     * it would reverse are still sitting in the database.
     */
    @Query("SELECT COUNT(*) FROM triage_decisions WHERE committed = 0")
    fun uncommittedCount(): Flow<Int>

    /** The photos currently queued for deletion, so one can be pulled back out. */
    @Query(
        """
        SELECT a.* FROM media_assets a
        INNER JOIN triage_decisions d ON d.assetId = a.id
        WHERE d.committed = 0 AND d.verdict = :verdict
        ORDER BY d.decidedAt DESC
        """,
    )
    fun pendingWithVerdict(verdict: Verdict): Flow<List<MediaAsset>>
}

@Dao
interface EditJobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: EditJob)

    @Query("SELECT * FROM edit_jobs WHERE id = :id")
    suspend fun byId(id: String): EditJob?

    @Query("SELECT * FROM edit_jobs WHERE sourceAssetId = :assetId ORDER BY processingMs DESC LIMIT 1")
    suspend fun latestForAsset(assetId: Long): EditJob?

    @Query("SELECT * FROM edit_jobs WHERE state = :state")
    suspend fun inState(state: JobState): List<EditJob>

    /**
     * Jobs genuinely awaiting a decision.
     *
     * `fellBackToOriginal = 0` is not a nicety. A fallback produced no new
     * image — the gate rejected the grade and the original was kept — so there
     * is nothing to compare, nothing to approve, and §9.3 forbids trashing its
     * original in any case. Including them turned Review into a queue of
     * unchanged photos that had to be rejected one by one.
     */
    @Query(
        """
        SELECT * FROM edit_jobs
        WHERE state = :done AND approvedAt IS NULL AND rejectedAt IS NULL
          AND fellBackToOriginal = 0
        ORDER BY processingMs DESC
        """,
    )
    fun pendingReview(done: JobState = JobState.DONE): Flow<List<EditJob>>

    /** §6.12: "surface fallbacks in the UI — a silent fallback teaches you nothing." */
    @Query("SELECT COUNT(*) FROM edit_jobs WHERE fellBackToOriginal = 1")
    fun fallbackCount(): Flow<Int>

    /**
     * Fallback jobs that still have a file on disk.
     *
     * Versions before 0.1.4 wrote an export even when a gate failed, which
     * produced a byte-identical re-encode of a photo the library already had.
     * Those files are still sitting in Pictures/Sift; this is how they get found
     * so the user can clear them out.
     */
    @Query("SELECT * FROM edit_jobs WHERE fellBackToOriginal = 1 AND outputUri IS NOT NULL")
    suspend fun fallbacksWithOutput(): List<EditJob>

    @Query("UPDATE edit_jobs SET outputUri = NULL WHERE id = :id")
    suspend fun clearOutputUri(id: String)

    @Query(
        """
        SELECT COUNT(*) FROM edit_jobs
        WHERE state = :done AND approvedAt IS NULL AND rejectedAt IS NULL
        """,
    )
    suspend fun pendingReviewCount(done: JobState = JobState.DONE): Int

    @Query("UPDATE edit_jobs SET approvedAt = :at WHERE id = :id")
    suspend fun markApproved(id: String, at: Long)

    @Query("UPDATE edit_jobs SET rejectedAt = :at, rejectionReason = :reason WHERE id = :id")
    suspend fun markRejected(id: String, at: Long, reason: RejectionReason?)

    @Query("UPDATE edit_jobs SET originalTrashedAt = :at WHERE id = :id")
    suspend fun markOriginalTrashed(id: String, at: Long)

    @Query("UPDATE edit_jobs SET state = :state WHERE id = :id")
    suspend fun setState(id: String, state: JobState)

    /** §9.5 — the rejection distribution that tells you a target is wrong. */
    @Query(
        """
        SELECT rejectionReason AS reason, COUNT(*) AS count FROM edit_jobs
        WHERE rejectionReason IS NOT NULL
        GROUP BY rejectionReason ORDER BY count DESC
        """,
    )
    fun rejectionHistogram(): Flow<List<RejectionCount>>

    @Query("SELECT COUNT(*) FROM edit_jobs WHERE rejectedAt IS NOT NULL")
    fun rejectionTotal(): Flow<Int>
}

data class RejectionCount(val reason: RejectionReason, val count: Int)

@Dao
interface LifecycleEventDao {

    @Insert
    suspend fun insert(event: LifecycleEvent)

    @Query("SELECT * FROM lifecycle_events WHERE assetId = :assetId ORDER BY at ASC")
    suspend fun forAsset(assetId: Long): List<LifecycleEvent>

    @Transaction
    @Query(
        """
        SELECT * FROM lifecycle_events
        WHERE `to` = :state
        AND assetId NOT IN (SELECT assetId FROM lifecycle_events WHERE `to` = :terminal)
        """,
    )
    suspend fun strandedAt(state: LifecycleState, terminal: LifecycleState): List<LifecycleEvent>
}
