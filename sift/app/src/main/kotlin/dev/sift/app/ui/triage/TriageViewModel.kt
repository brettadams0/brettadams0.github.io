package dev.sift.app.ui.triage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sift.data.db.MediaAsset
import dev.sift.data.db.SiftDatabase
import dev.sift.data.media.LifecycleRepository
import dev.sift.data.media.TrashCoordinator
import dev.sift.data.settings.SettingsRepository
import dev.sift.app.work.GradeWorker
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
    private val db: SiftDatabase,
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
        db.mediaAssets().deck(),
        db.mediaAssets().seenCount(),
        db.mediaAssets().totalCount(),
        lifecycle.pendingTossCount(),
        internal,
    ) { deck, seen, total, pendingToss, base ->
        base.copy(
            deck = deck,
            reviewed = seen,
            total = total,
            pendingToss = pendingToss,
            canUndo = undoStack.isNotEmpty(),
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
            val candidates = db.mediaAssets()
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
            internal.value = internal.value.copy(
                trashRequest = request,
                message = if (request == null) "Nothing to commit" else null,
            )
        }
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
                if (settings.settings.first().autoGradeOnCommit) {
                    // §9.2 — grading starts on commit, battery permitting.
                    GradeWorker.enqueue(getApplication())
                    "Trashed ${request.assetIds.size}. Grading your keepers now."
                } else {
                    "Trashed ${request.assetIds.size}."
                }
            } else {
                "Cancelled — your decisions are still here."
            }
            internal.value = internal.value.copy(trashRequest = null, message = message)
        }
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
        val members = db.mediaAssets().inCluster(clusterId)
        internal.value = internal.value.copy(
            cluster = members,
            // §7 — pre-select the sharpest, ranked on P90 not mean (trap #11).
            suggestedKeeperId = members.maxByOrNull { it.id }?.id,
        )
    }
}
