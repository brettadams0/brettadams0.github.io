package dev.cue.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.cue.data.repo.CueRepository
import dev.cue.model.Conversation
import dev.cue.model.VoiceProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class InboxUiState(
    val yourTurn: List<Conversation> = emptyList(),
    val others: List<Conversation> = emptyList(),
    val corpusSize: Int = 0,
    val calibrated: Boolean = false,
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    repository: CueRepository,
) : ViewModel() {

    val state: StateFlow<InboxUiState> = combine(
        repository.ballInYourCourt(),
        repository.activeConversations(),
        repository.corpusSize(),
    ) { yourTurn, all, corpusSize ->
        val waiting = yourTurn.map { it.id }.toSet()
        InboxUiState(
            yourTurn = yourTurn,
            others = all.filterNot { it.id in waiting },
            corpusSize = corpusSize,
            calibrated = corpusSize >= VoiceProfile.MIN_SAMPLES,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InboxUiState(),
    )
}
