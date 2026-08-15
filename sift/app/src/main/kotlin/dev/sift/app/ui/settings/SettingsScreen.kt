package dev.sift.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sift.app.work.GradeLog
import dev.sift.data.db.SiftDatabase
import dev.sift.data.settings.SettingsRepository
import dev.sift.model.ExportPreset
import dev.sift.model.GradeSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    db: SiftDatabase,
) : ViewModel() {

    data class UiState(
        val settings: GradeSettings = GradeSettings.VALIDATED_DEFAULTS,
        val rejections: List<Pair<dev.sift.model.RejectionReason, Int>> = emptyList(),
        val rejectionTotal: Int = 0,
    )

    val state: StateFlow<UiState> = combine(
        repository.settings,
        db.editJobs().rejectionHistogram(),
        db.editJobs().rejectionTotal(),
    ) { settings, histogram, total ->
        UiState(settings, histogram.map { it.reason to it.count }, total)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setRouting(mode: GradeSettings.RoutingMode) = launch { repository.setRouting(mode) }

    fun setAutoGrade(enabled: Boolean) = launch { repository.setAutoGrade(enabled) }

    fun setUpscale(mode: GradeSettings.UpscaleMode) = launch { repository.setUpscale(mode) }

    fun setTarget(l: Float, a: Float, b: Float) = launch { repository.setPortraitTarget(l, a, b) }

    fun setDetailBlend(value: Float) = launch { repository.setDetailBlend(value) }

    fun togglePreset(preset: ExportPreset) = launch {
        val current = state.value.settings.enabledPresets
        repository.setPresets(if (preset in current) current - preset else current + preset)
    }

    fun setDebugDump(enabled: Boolean) = launch { repository.setDebugDump(enabled) }

    fun reset() = launch { repository.resetToValidatedDefaults() }

    fun failureLog(): String = GradeLog.read()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** Settings (§11). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("Grade profile") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (mode in GradeSettings.RoutingMode.entries) {
                        FilterChip(
                            selected = settings.routing == mode,
                            onClick = { viewModel.setRouting(mode) },
                            label = { Text(mode.name.lowercase().replace('_', ' ')) },
                        )
                    }
                }
            }

            Section("Auto-grade on commit") {
                Switch(
                    checked = settings.autoGradeOnCommit,
                    onCheckedChange = viewModel::setAutoGrade,
                )
                Hint("Off means grading only runs when you ask for it.")
            }

            Section("Upscale") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (mode in GradeSettings.UpscaleMode.entries) {
                        FilterChip(
                            selected = settings.upscale == mode,
                            onClick = { viewModel.setUpscale(mode) },
                            label = { Text(mode.name.lowercase()) },
                        )
                    }
                }
                Hint(
                    "Gated means Sift only upscales when the source genuinely lacks " +
                        "resolution, and never past what its measured sharpness supports.",
                )
            }

            /**
             * §11 — the portrait targets are editable because you will want to
             * tune them for different lighting, and hardcoding them means
             * editing Kotlin to change a number.
             */
            Section("Portrait skin target (CIELAB)") {
                LabeledSlider("L*", settings.portraitTargetL, 40f..90f) {
                    viewModel.setTarget(it, settings.portraitTargetA, settings.portraitTargetB)
                }
                LabeledSlider("a*", settings.portraitTargetA, 0f..30f) {
                    viewModel.setTarget(settings.portraitTargetL, it, settings.portraitTargetB)
                }
                LabeledSlider("b*", settings.portraitTargetB, 5f..30f) {
                    viewModel.setTarget(settings.portraitTargetL, settings.portraitTargetA, it)
                }
                Hint(
                    "The guard rail rejects any graded frame whose skin b* lands outside " +
                        "${GradeSettings.SKIN_B_GUARD_MIN.toInt()}–${GradeSettings.SKIN_B_GUARD_MAX.toInt()}. " +
                        "Below about 10 a subject reads ill and grey.",
                )
            }

            Section("Upscale detail blend") {
                LabeledSlider("Blend", settings.detailBlendFraction, 0f..0.4f) {
                    viewModel.setDetailBlend(it)
                }
                Hint("How much natural micro-texture is added back after a learned upscale.")
            }

            Section("Export presets") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (preset in ExportPreset.entries) {
                        FilterChip(
                            selected = preset in settings.enabledPresets,
                            onClick = { viewModel.togglePreset(preset) },
                            label = { Text(preset.displayName) },
                        )
                    }
                }
            }

            /** §9.5 — the rejection distribution, once there is enough of it. */
            if (state.rejectionTotal > 0) {
                Section("Why you rejected grades (${state.rejectionTotal})") {
                    for ((reason, count) in state.rejections) {
                        Text(
                            "${reason.name.replace('_', ' ').lowercase()}: $count",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.rejectionTotal >= 50) {
                        Hint(
                            "Enough rejections to act on. A run of one reason usually means " +
                                "one target above is wrong for your lighting, not that the " +
                                "pipeline is broken.",
                        )
                    } else {
                        Hint("${50 - state.rejectionTotal} more before this is worth acting on.")
                    }
                }
            }

            Section("Debug") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = settings.dumpDebugJson, onCheckedChange = viewModel::setDebugDump)
                    Text("Dump analysis and derived parameters alongside output")
                }
                val log = viewModel.failureLog()
                if (log.isNotBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            log.takeLast(2000),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }

            OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                Text("Reset to validated defaults")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Text("$label ${"%.1f".format(value)}", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
