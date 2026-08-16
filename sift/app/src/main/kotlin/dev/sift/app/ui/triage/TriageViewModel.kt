package dev.sift.app.ui.triage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sift.data.db.EditJobDao
import dev.sift.data.db.MediaAssetDao
import dev.sift.data.db.TriageDecisionDao
import dev.sift.data.db.MediaAsset
import dev.sift.data.media.LifecycleRepository
import dev.sift.data.media.TrashCoordinator
import dev.sift.data.settings.SettingsRepository
import dev.sift.app.work.GradeWorker
import dev.sift.app.work.IngestWorker
import dev.sift.model.ContentClass
import dev.sift.model.LifecycleState
import dev.sift.model.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Triage deck state (§8).
 *
 * Two things this deliberately does not do:
 *
 * - **It never trashes anything.** Every swipe writes a decision to Room and
 *   nothing else. Deletion happens once, on commit, as a single batched request.
 * - **It never discards a decision.** A cancelled trash dialog leaves the
 *   decisions uncommitted and the deck position intact (§8, §14.8), so the user
 *   can retry rather than re-triage.
 */
@HiltViewModel
class TriageViewModel @Inject constructor(
    application: Application,
    private val mediaAssets: MediaAssetDao,
    private val triageDecisions: TriageDecisionDao,
    private val editJobs: EditJobDao,
    private val lifecycle: LifecycleRepository,
    private val settings: SettingsRepository,
) : AndroidViewModel(application) {

    data class UiState(
        val deck: List<MediaAsset> = emptyList(),
        val cluster: List<MediaAsset> = emptyList(),
        val suggestedKeeperId: Long? = null,
        val reviewed: Int = 0,
        val total: Int = 0,
        val pendingToss: Int = 0,
        val pendingReview: Int = 0,
        val canUndo: Boolean = false,
        val message: String? = null,
        val trashRequest: TrashCoordinator.Request? = null,
    ) {
        val current: MediaAsset? get() = deck.firstOrNull()

        /** §8 — "142 of 380". */
        val progressLabel: String get() = "$reviewed of $total"

        val isEmpty: Boolean get() = deck.isEmpty()
    }

    private val undoStack = ArrayDeque<Long>()
    private val internal = MutableStateFlow(UiState())

    val state: StateFlow<UiState> = combine(
        mediaAssets.deck(),
        mediaAssets.seenCount(),
        mediaAssets.totalCount(),
        lifecycle.pendingTossCount(),
        combine(lifecycle.undoableCount(), editJobs.pendingReview(), internal) { undoable, review, base ->
            Triple(undoable, review.size, base)
        },
    ) { deck, seen, total, pendingToss, (undoable, pendingReview, base) ->
        base.copy(
            deck = deck,
            reviewed = seen,
            total = total,
            pendingToss = pendingToss,
            pendingReview = pendingReview,
            // Derived from the database, not from an in-memory stack: the stack
            // empties on rotation or process death while the decisions it would
            // reverse are still sitting there.
            canUndo = undoable > 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun decide(verdict: Verdict) {
        val asset = state.value.current ?: return
        viewModelScope.launch {
            lifecycle.recordDecision(asset.id, verdict)
            undoStack.addLast(asset.id)
            while (undoStack.size > LifecycleRepository.UNDO_DEPTH) undoStack.removeFirst()
            loadClusterFor(state.value.deck.getOrNull(1))
        }
    }

    /** §8 — a swipe deck without undo is hostile. */
    fun undo() {
        viewModelScope.launch {
            val restored = lifecycle.undoLast()
            if (restored != null) {
                undoStack.removeLastOrNull()
                internal.value = internal.value.copy(message = null)
            }
        }
    }

    /** Tapping a filmstrip frame promotes it to the cluster's keeper. */
    fun promoteInCluster(assetId: Long) {
        internal.value = internal.value.copy(suggestedKeeperId = assetId)
    }

    /**
     * §8 — bulk-reject an auto-clustered group of screenshots and documents in
     * one swipe. Non-photographic content is a large fraction of any real camera
     * roll and swiping it one frame at a time is the tax this removes.
     */
    fun tossAllNonPhotographic() {
        viewModelScope.launch {
            val candidates = mediaAssets
                .inStateNow(LifecycleState.UNTRIAGED)
                .filter { it.contentClass == ContentClass.NON_PHOTOGRAPHIC }
            for (asset in candidates) lifecycle.recordDecision(asset.id, Verdict.TOSS)
            internal.value = internal.value.copy(
                message = "${candidates.size} screenshots and documents marked for the bin",
            )
        }
    }

    /** Build deletion batch 1. The caller launches the returned IntentSender. */
    fun commit() {
        viewModelScope.launch {
            val request = lifecycle.buildTriageTrashRequest()
            if (request != null) {
                // Keepers are promoted once the trash dialog resolves, so a
                // cancelled dialog leaves the whole commit untouched.
                internal.value = internal.value.copy(trashRequest = request, message = null)
                return@launch
            }

            // Nothing to delete, but there may still be keepers waiting. This
            // path exists because a session with no rejects used to commit
            // nothing at all.
            val queued = startGrading()
            internal.value = internal.value.copy(
                message = when {
                    queued > 0 -> "Grading $queued photos."
                    else -> "Nothing to commit"
                },
            )
        }
    }

    /** Promote keepers and kick the grader if settings allow. */
    private suspend fun startGrading(): Int {
        val queued = lifecycle.commitKeepers()
        if (queued > 0 && settings.settings.first().autoGradeOnCommit) {
            GradeWorker.enqueue(getApplication())
        }
        return queued
    }

    /**
     * @param granted false when the user cancelled. Decisions stay uncommitted
     *   in Room; nothing is lost (§14.8).
     */
    fun onTrashResult(granted: Boolean) {
        val request = internal.value.trashRequest ?: return
        viewModelScope.launch {
            lifecycle.onTriageTrashResult(request, granted)
            undoStack.clear()

            val message = if (granted) {
                // §9.2 — grading starts on commit, battery permitting.
                val queued = startGrading()
                if (queued > 0) {
                    "Trashed ${request.assetIds.size}. Grading $queued keepers now."
                } else {
                    "Trashed ${request.assetIds.size}."
                }
            } else {
                "Cancelled — your decisions are still here."
            }
            internal.value = internal.value.copy(trashRequest = null, message = message)
        }
    }

    /**
     * Kick the library scan again.
     *
     * The deck is fed from the database, not from MediaStore directly, so if
     * ingest never ran or died the deck is empty and stays empty. Without a way
     * to retry, the only recovery is reinstalling.
     */
    fun rescanLibrary() {
        IngestWorker.enqueue(getApplication(), force = true)
        internal.value = internal.value.copy(message = "Rescanning your library\u2026")
    }

    fun consumeMessage() {
        internal.value = internal.value.copy(message = null)
    }

    private suspend fun loadClusterFor(asset: MediaAsset?) {
        val clusterId = asset?.clusterId
        if (clusterId == null) {
            internal.value = internal.value.copy(cluster = emptyList(), suggestedKeeperId = null)
            return
        }
        val members = mediaAssets.inCluster(clusterId)
        internal.value = internal.value.copy(
            cluster = members,
            // §7 — pre-select the sharpest, ranked on P90 not mean (trap #11).
            suggestedKeeperId = members.maxByOrNull { it.id }?.id,
        )
    }
}
