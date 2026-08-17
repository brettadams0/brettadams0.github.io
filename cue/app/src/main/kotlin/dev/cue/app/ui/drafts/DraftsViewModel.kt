package dev.cue.app.ui.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.cue.app.draft.DraftService
import dev.cue.data.repo.CueRepository
import dev.cue.draft.ConversationHealth
import dev.cue.draft.DraftSet
import dev.cue.draft.HealthReport
import dev.cue.draft.SuppressedVariant
import dev.cue.model.CapturedContext
import dev.cue.model.Conversation
import dev.cue.model.Draft
import dev.cue.model.DraftAction
import dev.cue.model.MatchProfile
import dev.cue.model.Sender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DraftsUiState(
    val loading: Boolean = true,
    val conversation: Conversation? = null,
    val set: DraftSet? = null,
    val health: HealthReport? = null,
    val overThermalBudget: Boolean = false,
    val error: String? = null,
) {
    val drafts: List<Draft> get() = set?.drafts.orEmpty()
    val suppressed: List<SuppressedVariant> get() = set?.suppressed.orEmpty()
}

@HiltViewModel
class DraftsViewModel @Inject constructor(
    private val repository: CueRepository,
    private val drafting: DraftService,
) : ViewModel() {

    private val _state = MutableStateFlow(DraftsUiState())
    val state: StateFlow<DraftsUiState> = _state.asStateFlow()

    private var lastTheirMessage: String? = null

    fun load(conversationId: String) {
        viewModelScope.launch {
            _state.value = DraftsUiState(loading = true)
            val conversation = repository.conversation(conversationId)
            if (conversation == null) {
                _state.value = DraftsUiState(loading = false, error = "That conversation is gone")
                return@launch
            }
            val messages = repository.messages(conversationId)
            lastTheirMessage = messages.lastOrNull { it.sender == Sender.THEM }?.text

            val now = System.currentTimeMillis()
            val context = CapturedContext(
                conversationId = conversationId,
                platform = conversation.platform,
                profile = conversation.profile ?: MatchProfile(),
                messages = messages,
                capturedAt = conversation.lastCapturedAt,
                nowMillis = now,
            )

            val outcome = drafting.draft(context, now)
            _state.value = DraftsUiState(
                loading = false,
                conversation = conversation,
                set = outcome.set,
                health = ConversationHealth.assess(context, now),
                overThermalBudget = outcome.overThermalBudget,
            )
        }
    }

    fun regenerate(conversationId: String) = load(conversationId)

    /**
     * §8. Called when you copy a draft.
     *
     * Copying is recorded as [DraftAction.SENT_CLEAN] and an edit as
     * [DraftAction.SENT_EDITED], which is an approximation the app cannot improve
     * on: §2.1 means Cue never watches the send, so the last thing it knows is
     * what left the clipboard. Recording the copy is honest about that; asking
     * "did you send it?" afterwards would be a modal in exchange for a slightly
     * better statistic.
     */
    fun onCopied(draft: Draft, editedText: String?) {
        viewModelScope.launch {
            val edited = editedText?.takeIf { it.isNotBlank() && it != draft.text }
            drafting.record(
                draft = draft,
                action = if (edited == null) DraftAction.SENT_CLEAN else DraftAction.SENT_EDITED,
                finalText = edited,
                precedingTheirMessage = lastTheirMessage,
                now = System.currentTimeMillis(),
            )
        }
    }

    fun onDiscarded(draft: Draft) {
        viewModelScope.launch {
            drafting.record(
                draft = draft,
                action = DraftAction.DISCARDED,
                finalText = null,
                precedingTheirMessage = lastTheirMessage,
                now = System.currentTimeMillis(),
            )
        }
    }

    fun exclude(conversationId: String) {
        viewModelScope.launch { repository.exclude(conversationId) }
    }
}
