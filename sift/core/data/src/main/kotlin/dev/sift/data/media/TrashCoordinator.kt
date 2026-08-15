package dev.sift.data.media

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two deletion batches (§8, §9).
 *
 * §8 is explicit and trap #16 repeats it: **triage rejects and approved
 * originals must never be merged into one `createTrashRequest`.** They carry
 * different intent, different risk and different confirmation copy —
 * "throw away 34 photos you rejected" is a routine action, "destroy the
 * originals of 34 photos you approved graded versions of" is not, and a user
 * confirming one has not consented to the other.
 *
 * The type system enforces the separation: there is no call that takes a mixed
 * list, and [Batch] names which one is being requested so the UI can pick the
 * right copy.
 *
 * Trash, never delete — 30-day recovery.
 */
@Singleton
class TrashCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    enum class Batch(val title: String, val body: String) {
        /** Batch 1: rejected at triage, trashed on commit. */
        TRIAGE_REJECTS(
            title = "Move rejected photos to Trash?",
            body = "These are the photos you swiped away. They stay recoverable for 30 days.",
        ),

        /**
         * Batch 2: originals of approved keepers. Only ever reached through
         * [ApprovalGuard].
         */
        APPROVED_ORIGINALS(
            title = "Move originals to Trash?",
            body = "You approved the graded versions of these. The originals stay recoverable " +
                "for 30 days, and the graded exports are untouched.",
        ),
    }

    data class Request(
        val batch: Batch,
        val intent: PendingIntent,
        val assetIds: List<Long>,
        val bytesFreed: Long,
    )

    /**
     * Build one trash request for one batch.
     *
     * Returns null when there is nothing to do, which is not an error — an empty
     * commit should not throw a dialog at the user.
     *
     * The caller launches [Request.intent] and **must** treat a cancelled result
     * as "nothing happened": decisions stay uncommitted in Room, lifecycle states
     * stay where they were (§8, §14.8).
     */
    fun buildRequest(
        batch: Batch,
        assetIds: List<Long>,
        uris: List<Uri>,
        bytesFreed: Long,
    ): Request? {
        require(assetIds.size == uris.size) { "asset ids and uris must correspond" }
        if (uris.isEmpty()) return null

        val intent = MediaStore.createTrashRequest(resolver, uris, true)
        return Request(batch, intent, assetIds, bytesFreed)
    }

    /**
     * Human-readable storage readout for the approve action (§9.4:
     * "Approving frees 1.4 GB").
     */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
