package dev.sift.app.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sift.data.db.MediaAssetDao
import dev.sift.data.di.ImagingDispatcher
import dev.sift.data.media.MediaStoreRepository
import dev.sift.imaging.BurstClustering
import dev.sift.imaging.ColorSpaces
import dev.sift.imaging.FrameAnalyzer
import dev.sift.imaging.PerceptualHash
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Library ingest, hashing and clustering (§7).
 *
 * §7 is explicit that full-library processing on first launch is a multi-minute
 * stall, so this runs as a **low-priority background job** rather than blocking
 * the deck. The grid works from MediaStore rows immediately; hashes and analysis
 * fill in behind it.
 */
@HiltWorker
class IngestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaAssets: MediaAssetDao,
    private val media: MediaStoreRepository,
    private val json: Json,
    @ImagingDispatcher private val imagingDispatcher: CoroutineDispatcher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 1. Page in the library. Newest first, 200-row pages, projection only.
        var offset = 0
        while (true) {
            if (isStopped) return Result.retry()
            val page = media.page(MediaStoreRepository.PAGE_SIZE, offset)
            if (page.isEmpty()) break
            mediaAssets.insertAll(page)
            offset += page.size
        }

        // 2. Hash and analyse whatever has not been done yet, in bounded chunks
        //    so the job is interruptible and resumable.
        while (true) {
            if (isStopped) return Result.retry()
            val batch = mediaAssets.needingAnalysis(ANALYSIS_CHUNK)
            if (batch.isEmpty()) break

            for (asset in batch) {
                runCatching {
                    withContext(imagingDispatcher) {
                        val decoded = media.decode(
                            Uri.parse(asset.uri),
                            maxLongEdge = MediaStoreRepository.SCAN_LONG_EDGE,
                        )
                        val linear = ColorSpaces.toLinear(decoded.image)
                        val analysis = FrameAnalyzer.analyze(linear, decoded.metadata)
                        val hash = PerceptualHash.dHash(linear)

                        mediaAssets.setAnalysis(
                            id = asset.id,
                            json = json.encodeToString(analysis),
                            contentClass = analysis.route(),
                            dHash = hash,
                        )
                    }
                }.onFailure {
                    // §12 — a frame that will not decode is skipped, not fatal.
                    GradeLog.record(asset.id, it)
                    mediaAssets.setAnalysis(asset.id, "{}", null, 0L)
                }
            }
        }

        // 3. Cluster (§7). Recomputed wholesale because it is cheap once the
        //    hashes exist and because a single new capture can legitimately join
        //    an existing burst.
        recluster()
        return Result.success()
    }

    private suspend fun recluster() {
        var offset = 0
        val candidates = mutableListOf<BurstClustering.Candidate>()
        while (true) {
            val page = mediaAssets.page(CLUSTER_CHUNK, offset)
            if (page.isEmpty()) break
            for (asset in page) {
                if (asset.dHash == 0L) continue
                val sharpness = asset.analysisJson
                    ?.let { runCatching { json.decodeFromString<dev.sift.model.FrameAnalysis>(it) }.getOrNull() }
                    ?.laplacianVarianceP90
                    ?: 0f
                candidates += BurstClustering.Candidate(
                    id = asset.id,
                    dateTaken = asset.dateTaken,
                    dHash = asset.dHash,
                    sharpnessP90 = sharpness,
                )
            }
            offset += page.size
        }

        for (cluster in BurstClustering.cluster(candidates)) {
            // Singles carry no cluster id, so the deck can tell a burst from a
            // lone frame without counting members.
            val id = if (cluster.isBurst) cluster.id else null
            for (member in cluster.members) {
                mediaAssets.setCluster(member.id, id)
            }
        }
    }

    companion object {
        const val WORK_NAME = "sift-ingest"
        private const val ANALYSIS_CHUNK = 25
        private const val CLUSTER_CHUNK = 500

        /**
         * @param force replaces work already in flight. Used by the manual
         *   "Rescan library" action: with [ExistingWorkPolicy.KEEP] a scan that
         *   is wedged or waiting can never be restarted by the user, which
         *   leaves an empty deck with no way out.
         */
        fun enqueue(context: Context, force: Boolean = false) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IngestWorker>().addTag(WORK_NAME).build(),
            )
        }
    }
}
