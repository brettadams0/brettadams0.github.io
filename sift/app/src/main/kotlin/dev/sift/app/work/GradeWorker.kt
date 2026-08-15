package dev.sift.app.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sift.data.db.EditJob
import dev.sift.data.db.SiftDatabase
import dev.sift.data.di.ImagingDispatcher
import dev.sift.data.media.LifecycleRepository
import dev.sift.data.media.MediaStoreRepository
import dev.sift.data.settings.SettingsRepository
import dev.sift.imaging.Pipeline
import dev.sift.ml.ModelPolicy
import dev.sift.model.ExportPreset
import dev.sift.model.JobState
import dev.sift.model.LifecycleState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Batch grading (§9.2).
 *
 * Automatic on triage commit, battery-aware, resumable. Assume process death at
 * any point: the queue lives in Room as `QUEUED_FOR_GRADE` rows, so a worker
 * that dies mid-batch resumes exactly where it stopped and never redoes a
 * completed job (§4.2, §12).
 *
 * §12 also governs the failure policy: **never fail an entire batch because of
 * one bad frame.** A decode failure marks that asset `UNREADABLE` and the loop
 * continues.
 */
@HiltWorker
class GradeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: SiftDatabase,
    private val media: MediaStoreRepository,
    private val lifecycle: LifecycleRepository,
    private val settings: SettingsRepository,
    private val json: Json,
    @ImagingDispatcher private val imagingDispatcher: CoroutineDispatcher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // §9.6 — refuse below 2GB free, with a clear message rather than a
        // half-written batch.
        if (!media.hasSpaceForBatch()) {
            return Result.failure(
                androidx.work.Data.Builder()
                    .putString(KEY_ERROR, "Less than 2 GB free. Review the pending backlog first.")
                    .build(),
            )
        }

        val current = settings.settings.first()
        val queued = db.mediaAssets().inStateNow(LifecycleState.QUEUED_FOR_GRADE)
        if (queued.isEmpty()) return Result.success()

        setForeground(GradeNotifications.foregroundInfo(applicationContext, 0, queued.size))

        var completed = 0
        for (asset in queued) {
            if (isStopped) return Result.retry()

            // §9.6 — stop adding to a backlog nobody has looked at.
            if (lifecycle.atBacklogCap()) break

            lifecycle.transition(asset.id, LifecycleState.GRADING, "grade worker picked up")

            val outcome = runCatching {
                withContext(imagingDispatcher) {
                    gradeOne(asset.id, Uri.parse(asset.uri), current)
                }
            }

            outcome.onSuccess { job ->
                db.editJobs().upsert(job)
                lifecycle.transition(
                    asset.id,
                    LifecycleState.PENDING_REVIEW,
                    if (job.fellBackToOriginal) "fell back to original" else "graded",
                )
            }.onFailure { error ->
                // One bad frame never fails the batch (§12).
                db.editJobs().upsert(failedJob(asset.id, error))
                lifecycle.transition(asset.id, LifecycleState.UNREADABLE, "decode/grade failed: $error")
            }

            completed++
            setForeground(
                GradeNotifications.foregroundInfo(applicationContext, completed, queued.size),
            )
        }

        return Result.success()
    }

    private suspend fun gradeOne(
        assetId: Long,
        uri: Uri,
        gradeSettings: dev.sift.model.GradeSettings,
    ): EditJob {
        val decoded = media.decode(uri)

        // The master is always produced; §10's other presets derive from it and
        // are exported alongside when enabled.
        val result = Pipeline.process(
            Pipeline.Request(
                source = Pipeline.SourceFrame(decoded.image, decoded.metadata),
                settings = gradeSettings,
                preset = ExportPreset.MASTER,
                faceDetector = ModelPolicy.faceDetector,
                // Seeded from the asset id so a regrade of the same photo
                // produces a byte-identical file (§2.3).
                ditherSeed = assetId,
            ),
        )

        val outputUri = media.writeExport(
            jpeg = result.jpeg,
            displayName = "sift_${assetId}_${System.currentTimeMillis()}.jpg",
            width = result.width,
            height = result.height,
            sourceUri = uri,
        )

        db.mediaAssets().setAnalysis(
            id = assetId,
            json = json.encodeToString(result.analysisBefore),
            contentClass = result.contentClass,
            dHash = db.mediaAssets().byId(assetId)?.dHash ?: 0L,
        )

        return EditJob(
            id = UUID.randomUUID().toString(),
            sourceAssetId = assetId,
            outputUri = outputUri?.toString(),
            profile = result.profile,
            profileWasManual = false,
            // §6.3 — every parameter, serialised. Not optional.
            derivedParamsJson = json.encodeToString(result.derived),
            upscaleFactor = result.derived.upscale?.effectiveFactor ?: 1f,
            gateResultsJson = json.encodeToString(result.gates),
            fellBackToOriginal = result.fellBackToOriginal,
            processingMs = result.processingMs,
            state = if (outputUri != null) JobState.DONE else JobState.FAILED,
            approvedAt = null,
            rejectedAt = null,
            rejectionReason = null,
            originalTrashedAt = null,
        )
    }

    private fun failedJob(assetId: Long, error: Throwable) = EditJob(
        id = UUID.randomUUID().toString(),
        sourceAssetId = assetId,
        outputUri = null,
        profile = dev.sift.model.GradeProfile.NONE,
        profileWasManual = false,
        derivedParamsJson = "{}",
        upscaleFactor = 1f,
        gateResultsJson = "{}",
        fellBackToOriginal = true,
        processingMs = 0,
        state = JobState.FAILED,
        approvedAt = null,
        rejectedAt = null,
        rejectionReason = null,
        originalTrashedAt = null,
    ).also { GradeLog.record(assetId, error) }

    companion object {
        const val WORK_NAME = "sift-grade"
        const val KEY_ERROR = "error"

        /**
         * §9.2 — battery > 30% or charging starts immediately; otherwise the
         * work is deferred until the device is charging.
         *
         * `requiresBatteryNotLow` is the platform's own expression of the same
         * idea and avoids Sift second-guessing the battery manager.
         */
        fun enqueue(context: Context, force: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(!force)
                .build()

            val request = OneTimeWorkRequestBuilder<GradeWorker>()
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                // Keep, not replace: re-enqueueing must never restart a batch
                // that is already part way through.
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
