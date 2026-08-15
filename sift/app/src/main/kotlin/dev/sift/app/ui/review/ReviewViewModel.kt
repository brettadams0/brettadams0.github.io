package dev.sift.app.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sift.app.work.GradeWorker
import dev.sift.data.db.EditJob
import dev.sift.data.db.MediaAsset
import dev.sift.data.db.SiftDatabase
import dev.sift.data.media.ApprovalGuard
import dev.sift.data.media.LifecycleRepository
import dev.sift.data.media.TrashCoordinator
import dev.sift.model.DerivedParams
import dev.sift.model.GateReport
import dev.sift.model.GradeProfile
import dev.sift.model.GradeSettings
import dev.sift.model.RegradeAction
import dev.sift.model.RejectionReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Review and approval (§9.4).
 *
 * This screen is what makes the rest of the app safe to trust. Until it exists,
 * auto-grading is writing files nobody has looked at, and §15 says to keep
 * original-trashing disabled entirely until it lands.
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    application: Application,
    private val db: SiftDatabase,
    private val lifecycle: LifecycleRepository,
    private val trash: TrashCoordinator,
    private val json: Json,
) : AndroidViewModel(application) {

    data class Item(
        val job: EditJob,
        val asset: MediaAsset,
        val derived: DerivedParams?,
        val gates: GateReport?,
    ) {
        /** §9.3 invariant 2 — never offer original-trashing on a fallback. */
        val canTrashOriginal: Boolean get() = ApprovalGuard.canOfferOriginalTrashing(job)
        val disabledReason: String? get() = ApprovalGuard.disabledReason(job)

        /**
         * §9.4 — the verdict strip: profile used, iterations to converge, final
         * skin b*, which gates passed, whether it fell back.
         *
         * "Surface fallbacks in the UI — a silent fallback teaches you nothing"
         * (§6.12).
         */
        fun verdictLines(): List<String> = buildList {
            add("Profile: ${job.profile.name.lowercase()}${if (job.profileWasManual) " (manual)" else ""}")
            derived?.portrait?.let { p ->
                add("Converged in ${p.iterations} pass${if (p.iterations == 1) "" else "es"}")
                add("Skin b* ${"%.1f".format(p.finalSkinB)} (target ${"%.1f".format(p.targetB)})")
            }
            derived?.upscale?.takeIf { it.effectiveFactor > 1f }?.let {
                add("Upscaled ${"%.1f".format(it.effectiveFactor)}x via ${it.method}")
            }
            gates?.let { report ->
                if (report.fellBackToOriginal) {
                    add("FELL BACK: ${report.fallbackReason ?: "a quality gate failed"}")
                } else {
                    val passed = report.results.count { it.passed }
                    add("$passed of ${report.results.size} gates passed")
                    report.failed.forEach { add("Failed: ${it.gate.displayName}") }
                }
            }
            add("${job.processingMs} ms")
        }
    }

    data class UiState(
        val items: List<Item> = emptyList(),
        val index: Int = 0,
        val comparing: Boolean = false,
        val flaggedIds: Set<String> = emptySet(),
        val message: String? = null,
        val pendingReasonForJobId: String? = null,
        val trashRequest: TrashCoordinator.Request? = null,
        val storageReadout: String = "",
        val rejectionHistogram: List<Pair<RejectionReason, Int>> = emptyList(),
        val rejectionTotal: Int = 0,
    ) {
        val current: Item? get() = items.getOrNull(index)
        val isEmpty: Boolean get() = items.isEmpty()
        val progressLabel: String get() = if (items.isEmpty()) "" else "${index + 1} of ${items.size}"

        /**
         * §9.5 — after 50 rejections the distribution is worth acting on: a run
         * of TOO_WARM means the b* target is high for this lighting and one
         * number moves instead of a guess.
         */
        val tuningHintReady: Boolean get() = rejectionTotal >= 50
    }

    private val internal = MutableStateFlow(UiState())

    val state: StateFlow<UiState> = combine(
        lifecycle.pendingReview(),
        db.editJobs().rejectionHistogram(),
        db.editJobs().rejectionTotal(),
        internal,
    ) { jobs, histogram, total, base ->
        val items = jobs.mapNotNull { job ->
            db.mediaAssets().byId(job.sourceAssetId)?.let { asset ->
                Item(
                    job = job,
                    asset = asset,
                    derived = runCatching {
                        json.decodeFromString<DerivedParams>(job.derivedParamsJson)
                    }.getOrNull(),
                    gates = runCatching {
                        json.decodeFromString<GateReport>(job.gateResultsJson)
                    }.getOrNull(),
                )
            }
        }
        base.copy(
            items = items,
            index = base.index.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            rejectionHistogram = histogram.map { it.reason to it.count },
            rejectionTotal = total,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /**
     * §9.4 — press-and-hold to compare. Hold shows the original, release returns
     * to the graded version.
     *
     * Deliberately not a split-screen slider: a global colour shift is nearly
     * impossible to judge when half the frame is the other version, because the
     * eye normalises across the seam.
     */
    fun setComparing(comparing: Boolean) {
        internal.value = internal.value.copy(comparing = comparing)
    }

    fun approveCurrent() {
        val item = state.value.current ?: return
        viewModelScope.launch {
            lifecycle.approve(item.job)
            advance()
        }
    }

    /** Rejection always asks why — it is the only tuning signal there is (§9.5). */
    fun beginReject() {
        val item = state.value.current ?: return
        internal.value = internal.value.copy(pendingReasonForJobId = item.job.id)
    }

    fun confirmReject(reason: RejectionReason?) {
        val jobId = internal.value.pendingReasonForJobId ?: return
        val item = state.value.items.firstOrNull { it.job.id == jobId } ?: return
        viewModelScope.launch {
            lifecycle.reject(item.job, reason)
            internal.value = internal.value.copy(pendingReasonForJobId = null)
            advance()
        }
    }

    fun cancelReject() {
        internal.value = internal.value.copy(pendingReasonForJobId = null)
    }

    /** §9.5 — the three inline recovery actions. */
    fun regrade(action: RegradeAction) {
        val item = state.value.current ?: return
        viewModelScope.launch {
            when (action) {
                RegradeAction.OTHER_PROFILE -> {
                    val other = if (item.job.profile == GradeProfile.PORTRAIT) {
                        GradeProfile.SCENE
                    } else {
                        GradeProfile.PORTRAIT
                    }
                    lifecycle.reject(item.job, null)
                    lifecycle.requeueForGrade(item.asset.id, "regrade as ${other.name.lowercase()}")
                    db.editJobs().upsert(item.job.copy(profile = other, profileWasManual = true))
                    GradeWorker.enqueue(getApplication(), force = true)
                }
                RegradeAction.REDUCED_STRENGTH -> {
                    lifecycle.reject(item.job, null)
                    lifecycle.requeueForGrade(item.asset.id, "regrade at reduced strength")
                    GradeWorker.enqueue(getApplication(), force = true)
                }
                RegradeAction.KEEP_ORIGINAL -> {
                    lifecycle.reject(item.job, RejectionReason.PREFER_ORIGINAL)
                    lifecycle.markDoNotGrade(item.asset.id)
                }
            }
            advance()
        }
    }

    /** §9.4 — approve all, with individually flagged items held back. */
    fun toggleFlag() {
        val item = state.value.current ?: return
        val flagged = internal.value.flaggedIds
        internal.value = internal.value.copy(
            flaggedIds = if (item.job.id in flagged) flagged - item.job.id else flagged + item.job.id,
        )
    }

    fun approveAllUnflagged() {
        viewModelScope.launch {
            val flagged = internal.value.flaggedIds
            val approved = state.value.items.filter { it.job.id !in flagged }
            for (item in approved) lifecycle.approve(item.job)
            internal.value = internal.value.copy(
                message = "Approved ${approved.size}. ${flagged.size} held back for a second look.",
            )
        }
    }

    /**
     * Build **deletion batch 2**: originals of approved keepers.
     *
     * Never merged with batch 1 (trap #16). Every candidate is re-verified
     * against all five §9.3 invariants inside the repository, including a fresh
     * decode of the output — write-time success is not read-time success.
     */
    fun trashApprovedOriginals() {
        viewModelScope.launch {
            val batch = lifecycle.buildApprovedOriginalsRequest()
            val refusalNote = if (batch.refusals.isEmpty()) {
                ""
            } else {
                " ${batch.refusals.size} held back and re-queued."
            }
            internal.value = internal.value.copy(
                trashRequest = batch.request,
                storageReadout = batch.request
                    ?.let { "Approving frees ${trash.formatBytes(it.bytesFreed)}." }
                    .orEmpty(),
                message = if (batch.request == null) {
                    "Nothing eligible to trash.$refusalNote"
                } else {
                    null
                },
            )
        }
    }

    fun onTrashResult(granted: Boolean) {
        val request = internal.value.trashRequest ?: return
        viewModelScope.launch {
            lifecycle.onApprovedOriginalsResult(request, granted)
            internal.value = internal.value.copy(
                trashRequest = null,
                message = if (granted) {
                    "Trashed ${request.assetIds.size} originals."
                } else {
                    "Cancelled — nothing was trashed."
                },
            )
        }
    }

    fun next() = advance()

    fun previous() {
        internal.value = internal.value.copy(index = (internal.value.index - 1).coerceAtLeast(0))
    }

    fun consumeMessage() {
        internal.value = internal.value.copy(message = null)
    }

    /**
     * §9.5 — what the rejection distribution suggests, once there is enough of
     * it to mean anything.
     */
    fun tuningSuggestion(current: GradeSettings): String? {
        val state = state.value
        if (!state.tuningHintReady) return null
        val (reason, count) = state.rejectionHistogram.firstOrNull() ?: return null
        if (count < state.rejectionTotal / 3) return null

        return when (reason) {
            RejectionReason.TOO_WARM ->
                "Most rejections are 'too warm'. The b* target of " +
                    "${"%.1f".format(current.portraitTargetB)} may be high for your lighting."
            RejectionReason.TOO_COOL ->
                "Most rejections are 'too cool'. Consider raising the b* target above " +
                    "${"%.1f".format(current.portraitTargetB)}."
            RejectionReason.TOO_CONTRASTY -> "Most rejections are 'too contrasty'."
            RejectionReason.TOO_FLAT -> "Most rejections are 'too flat'."
            RejectionReason.SKIN_WRONG ->
                "Most rejections are 'skin wrong'. The a*/b* targets are worth revisiting together."
            RejectionReason.LOST_DETAIL ->
                "Most rejections are 'lost detail'. Try turning upscale off, or check denoise strength."
            RejectionReason.PREFER_ORIGINAL -> "Most rejections prefer the original as shot."
        }
    }

    private fun advance() {
        val next = internal.value.index + 1
        internal.value = internal.value.copy(
            index = next.coerceAtMost((state.value.items.size - 1).coerceAtLeast(0)),
        )
    }
}
