package dev.cue.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.cue.data.repo.CueRepository
import dev.cue.inference.EngineHolder
import dev.cue.inference.ModelFiles
import dev.cue.model.StrategyStats
import dev.cue.model.VoiceProfile
import dev.cue.voice.VoiceProfileExport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val corpusSize: Int = 0,
    val profile: VoiceProfile? = null,
    val engine: EngineHolder.State = EngineHolder.State.NotLoaded,
    val modelDirectory: String = "",
    val stats: List<StrategyStats> = emptyList(),
    /** §3.4's export, held in memory until the user copies or shares it. */
    val exportedProfileJson: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: CueRepository,
    private val engines: EngineHolder,
    private val files: ModelFiles,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = SettingsUiState(
                corpusSize = repository.corpus().size,
                profile = repository.currentVoiceProfile(),
                engine = engines.state.value,
                modelDirectory = files.modelDirectory().absolutePath,
                stats = repository.strategyStats().sortedByDescending { it.replyRate },
            )
        }
    }

    /**
     * §3.4. Exports the voice profile for the Chrome extension to import.
     *
     * This is the only data Cue ever hands out, it goes to a browser on a machine
     * the same person owns, and it contains numbers about how *you* write —
     * nothing she said. There is no network in the app to send it over (§2.3), so
     * the transport is the clipboard or a file the user moves themselves.
     */
    fun exportProfile() {
        viewModelScope.launch {
            val profile = repository.currentVoiceProfile() ?: return@launch
            _state.value = _state.value.copy(
                exportedProfileJson = VoiceProfileExport.encode(profile, System.currentTimeMillis()),
            )
        }
    }

    fun clearExport() {
        _state.value = _state.value.copy(exportedProfileJson = null)
    }
}
