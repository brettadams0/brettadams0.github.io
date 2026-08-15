package dev.sift.data.media

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import dev.sift.data.db.EditJob
import dev.sift.model.JobState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The five safety invariants of §9.3.
 *
 * **An original may be trashed only when all five hold.** This object is the
 * single place that decision is made, so there is exactly one code path to audit
 * and exactly one to test (§14.10 asserts an original with
 * `fellBackToOriginal == true` can never be trashed *through any code path*).
 *
 * Two of the five deserve their own note, because they are the ones that bite:
 *
 * - **Invariant 2** — when a quality gate fails, the pipeline ships the original
 *   (§6.12), so the "graded" export is just a re-encode of the source. Trashing
 *   the original then leaves a generation-loss JPEG as the only master. That is
 *   trap #14, described in the spec as the one genuinely unrecoverable bug in
 *   the app. The control is disabled in the UI *and* refused here.
 * - **Invariant 3** — write-time success is not read-time success. Storage can
 *   fill, a write can be interrupted, MediaStore can leave an `IS_PENDING` row
 *   behind. The output is decoded again immediately before the trash request is
 *   issued for its source. It costs about 200ms and prevents the one
 *   unrecoverable failure in the app.
 */
object ApprovalGuard {

    sealed interface Verdict {
        data object Allowed : Verdict
        data class Refused(val reason: String, val requeueGrade: Boolean = false) : Verdict
    }

    /**
     * @param userApproved invariant 5 — the user explicitly approved *this*
     *   asset. Passed in rather than read from the job so that a bulk
     *   "approve all" cannot accidentally satisfy it for an item held back.
     */
    suspend fun evaluate(
        resolver: ContentResolver,
        job: EditJob,
        expectedWidth: Int,
        expectedHeight: Int,
        userApproved: Boolean,
    ): Verdict = withContext(Dispatchers.IO) {
        // 1. The job finished.
        if (job.state != JobState.DONE) {
            return@withContext Verdict.Refused("grade job is ${job.state}, not DONE")
        }

        // 2. The grade did not fall back. Trap #14.
        if (job.fellBackToOriginal) {
            return@withContext Verdict.Refused(
                "a quality gate failed and the original was shipped unchanged — " +
                    "the export is a re-encode, not a graded master",
            )
        }

        // 5. The user approved this specific asset.
        if (!userApproved) {
            return@withContext Verdict.Refused("not explicitly approved by the user")
        }

        val uriString = job.outputUri
            ?: return@withContext Verdict.Refused("no output URI recorded", requeueGrade = true)
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return@withContext Verdict.Refused("output URI is unparseable", requeueGrade = true)

        // 3. The output resolves AND decodes. Re-verified now, not at write time.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            bounds.outWidth > 0 && bounds.outHeight > 0
        }.getOrDefault(false)

        if (!decoded) {
            return@withContext Verdict.Refused(
                "output did not decode — it may have been deleted or truncated since it was written",
                requeueGrade = true,
            )
        }

        // 4. Non-zero size and the dimensions the preset promised.
        val length = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (length <= 0L) {
            return@withContext Verdict.Refused("output file is empty", requeueGrade = true)
        }
        if (bounds.outWidth != expectedWidth || bounds.outHeight != expectedHeight) {
            return@withContext Verdict.Refused(
                "output is ${bounds.outWidth}x${bounds.outHeight}, " +
                    "expected ${expectedWidth}x$expectedHeight",
                requeueGrade = true,
            )
        }

        Verdict.Allowed
    }

    /**
     * Whether the approve-and-trash control should be offered at all.
     *
     * §9.3: "Assets with `fellBackToOriginal == true` must never be offered for
     * original-trashing — disable the control and state the reason in the UI."
     * A silent fallback teaches you nothing (§6.12).
     */
    fun canOfferOriginalTrashing(job: EditJob): Boolean =
        job.state == JobState.DONE && !job.fellBackToOriginal && job.outputUri != null

    fun disabledReason(job: EditJob): String? = when {
        job.state != JobState.DONE -> "Grade has not finished"
        job.fellBackToOriginal -> "A quality gate failed, so the original was shipped unchanged. " +
            "Trashing it would leave a re-encode as your only master."
        job.outputUri == null -> "No export was written"
        else -> null
    }
}
