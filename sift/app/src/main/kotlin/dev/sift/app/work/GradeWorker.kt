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
import dev.sift.data.db.MediaAssetDao
import dev.sift.data.db.EditJobDao
import dev.sift.data.db.EditJob
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
import kotlinx.serialization.encodeToString
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
    private val mediaAssets: MediaAssetDao,
    private val editJobs: EditJobDao,
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
        val queued = mediaAssets.inStateNow(LifecycleState.QUEUED_FOR_GRADE)
        if (queued.isEmpty()) return Result.success()

        showProgress(0, queued.size)

        var completed = 0
        for (asset in queued) {
            if (isStopped) return Result.retry()

            // §9.6 — stop adding to a backlog nobody has looked at.
            if (lifecycle.atBacklogCap()) break

            lifecycle.transition(asset.id, LifecycleState.GRADING, "grade worker picked up")

            val outcome = runCatching {
                withContext(imagingDispatcher) {
                    try {
                        gradeOne(asset.id, Uri.parse(asset.uri), settingsFor(asset, current), decodeLongEdge = null)
                    } catch (oom: OutOfMemoryError) {
                        // §12: "OOM during processing — catch, release Mats,
                        // retry once at half resolution, then fail the job."
                        // A 12MP frame is ~144MB as unbounded float and the
                        // pipeline holds several buffers at once, so this is a
                        // routine outcome on a loaded device, not a corrupt file.
                        GradeLog.record(asset.id, oom)
                        System.gc()
                        gradeOne(
                            asset.id,
                            Uri.parse(asset.uri),
                            settingsFor(asset, current),
                            decodeLongEdge = HALF_RESOLUTION_LONG_EDGE,
                        )
                    }
                }
            }

            outcome.onSuccess { job ->
                editJobs.upsert(job)
                mediaAssets.clearRegradeOverride(asset.id)
                if (job.fellBackToOriginal) {
                    // Terminal, not pending: there is no decision for the user to
                    // make. Parking these in the review queue meant scrolling
                    // through photos that were never changed, rejecting each one.
                    lifecycle.transition(
                        asset.id,
                        LifecycleState.REJECTED,
                        "quality gate failed; original kept unchanged",
                    )
                } else {
                    lifecycle.transition(asset.id, LifecycleState.PENDING_REVIEW, "graded")
                }
            }.onFailure { error ->
                // One bad frame never fails the batch (§12).
                editJobs.upsert(failedJob(asset.id, error))
                lifecycle.transition(asset.id, LifecycleState.UNREADABLE, "decode/grade failed: $error")
            }

            completed++
            showProgress(completed, queued.size)
        }

        return Result.success()
    }

    /**
     * Fold any one-shot regrade override into the settings for this asset (§9.5).
     *
     * "Regrade at reduced strength" and "regrade with the other profile" are
     * per-asset decisions, but grading reads global settings — so without this
     * both actions re-derived exactly the same grade they had just been asked to
     * change.
     */
    private fun settingsFor(
        asset: dev.sift.data.db.MediaAsset,
        base: dev.sift.model.GradeSettings,
    ): dev.sift.model.GradeSettings {
        var result = base
        asset.pendingStrengthScale?.let { result = result.copy(strengthScale = it) }
        asset.pendingProfile?.let {
            result = result.copy(
                routing = when (it) {
                    dev.sift.model.GradeProfile.PORTRAIT ->
                        dev.sift.model.GradeSettings.RoutingMode.FORCE_PORTRAIT
                    dev.sift.model.GradeProfile.SCENE ->
                        dev.sift.model.GradeSettings.RoutingMode.FORCE_SCENE
                    dev.sift.model.GradeProfile.NONE ->
                        dev.sift.model.GradeSettings.RoutingMode.OFF
                },
            )
        }
        return result
    }

    /**
     * Show batch progress, tolerating a refusal.
     *
     * `setForeground` throws on Android 12+ when the system declines to let a
     * background app start a foreground service. That is a notification
     * problem, not a grading problem — losing the progress bar must not abandon
     * a batch of photos halfway through.
     */
    private suspend fun showProgress(completed: Int, total: Int) {
        runCatching {
            setForeground(GradeNotifications.foregroundInfo(applicationContext, completed, total))
        }
    }

    private suspend fun gradeOne(
        assetId: Long,
        uri: Uri,
        gradeSettings: dev.sift.model.GradeSettings,
        decodeLongEdge: Int?,
    ): EditJob {
        val decoded = media.decode(uri, maxLongEdge = decodeLongEdge)

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

        // A fallback means a quality gate rejected the graded result and the
        // pipeline shipped the source unchanged (§6.12). The "export" would be a
        // byte-for-byte re-encode of a photo you already have: nothing to review,
        // nothing to approve, and §9.3 forbids ever trashing its original. Writing
        // it anyway just fills Pictures/Sift — and the gallery — with duplicates.
        if (result.fellBackToOriginal) {
            return EditJob(
                id = UUID.randomUUID().toString(),
                sourceAssetId = assetId,
                outputUri = null,
                profile = result.profile,
                profileWasManual = false,
                derivedParamsJson = json.encodeToString(result.derived),
                upscaleFactor = 1f,
                gateResultsJson = json.encodeToString(result.gates),
                fellBackToOriginal = true,
                processingMs = result.processingMs,
                state = JobState.DONE,
                approvedAt = null,
                rejectedAt = System.currentTimeMillis(),
                rejectionReason = null,
                originalTrashedAt = null,
            )
        }

        val outputUri = media.writeExport(
            jpeg = result.jpeg,
            displayName = "sift_${assetId}_${System.currentTimeMillis()}.jpg",
            width = result.width,
            height = result.height,
            sourceUri = uri,
            // The graded frame is the same photograph, so it belongs on the same
            // day in the gallery as the original — not on the day it was graded.
            dateTakenMillis = mediaAssets.byId(assetId)?.dateTaken,
        )

        mediaAssets.setAnalysis(
            id = assetId,
            json = json.encodeToString(result.analysisBefore),
            contentClass = result.contentClass,
            dHash = mediaAssets.byId(assetId)?.dHash ?: 0L,
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
         * Retry ceiling after an OOM (§12). Roughly a quarter of the pixels of a
         * 12MP frame, which is the difference between a ~144MB float buffer and
         * a ~36MB one.
         */
        const val HALF_RESOLUTION_LONG_EDGE = 2048

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
