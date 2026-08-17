package dev.cue.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cue.inference.EngineHolder

// TopAppBar is still @ExperimentalMaterial3Api, so its use has to be opted into
// explicitly. Scoped to the one composable that needs it rather than set as a
// module-wide compiler flag: a blanket -opt-in would silence the next
// experimental API to arrive, which is the warning worth reading.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenOnboarding: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { WhatLeavesYourDevice() }

            item {
                Section("Your voice") {
                    Text(
                        "${state.corpusSize} of your messages" +
                            if (state.profile?.isCalibrated == true) ", calibrated." else ", need 50.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.profile?.let { profile ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            listOf(
                                "median ${profile.medianWords.toInt()} words",
                                "${(profile.capitalizationRate * 100).toInt()}% capitalised",
                                "${(profile.terminalPunctuationRate * 100).toInt()}% punctuated",
                                "${profile.emojiRate} emoji per message",
                                "${profile.commaRate.toInt()} commas per 100 words",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (profile.characteristicTokens.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "your tells: ${profile.characteristicTokens.take(6).joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = onOpenOnboarding) { Text("Add screenshots") }
                        TextButton(onClick = viewModel::exportProfile) { Text("Export for browser") }
                    }
                    state.exportedProfileJson?.let { json ->
                        Text(
                            "${json.length} characters of JSON — numbers about how you write, " +
                                "nothing she said.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row {
                            TextButton(
                                onClick = { clipboard.setText(AnnotatedString(json)) },
                            ) { Text("Copy JSON") }
                            TextButton(onClick = viewModel::clearExport) { Text("Done") }
                        }
                    }
                }
            }

            item {
                Section("Model") {
                    Text(
                        when (val engine = state.engine) {
                            is EngineHolder.State.Ready ->
                                "${engine.tier.modelId}, loaded and on-device."
                            is EngineHolder.State.TemplateOnly ->
                                "${engine.reason}. Cue is using the template opener path, which " +
                                    "needs no model at all."
                            EngineHolder.State.NotLoaded -> "Still loading."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Drop a .task or .litertlm file here to use a model:\n${state.modelDirectory}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.stats.isNotEmpty()) {
                item {
                    Section("What works, for you") {
                        // §8: after enough drafts you know whether playful openers
                        // or specific callbacks actually get replies. Until then
                        // the numbers are shown and not acted on.
                        state.stats.forEach { stat ->
                            Text(
                                "${stat.strategy.label}: ${stat.sent}/${stat.shown} used, " +
                                    "${(stat.replyRate * 100).toInt()}% replied" +
                                    if (stat.trustworthy) "" else " (too few to trust)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * §10. "Ship a **'What leaves your device'** screen that says: *nothing*. With
 * on-device inference this is literally true, which is the version worth having."
 */
@Composable
private fun WhatLeavesYourDevice() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text("What leaves your device", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text("Nothing.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Text(
                "Cue has no INTERNET permission. Not a setting — the app cannot open a " +
                    "network connection at all. Text recognition and every draft run on this " +
                    "phone. The database is encrypted and excluded from backups and device " +
                    "transfers. There is no account, no analytics, and no crash reporting.\n\n" +
                    "Screenshots are read and discarded in the same breath; the images are " +
                    "never stored. Her photos are never looked at beyond text visible in them.\n\n" +
                    "She did not agree to any of this, which is the reason it works this way.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
