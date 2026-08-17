package dev.cue.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * §4.2's confirmation gate, as a screen.
 *
 * Both columns are shown because the failure this prevents is invisible in one
 * column: a list of messages that all look plausibly like yours *is* what her
 * messages look like too. Seeing them side by side is the only cheap way to
 * notice a reversal, and noticing it later means a poisoned profile and drafts
 * that sound like the person you are talking to.
 */
// TopAppBar is still @ExperimentalMaterial3Api, so its use has to be opted into
// explicitly. Scoped to the one composable that needs it rather than set as a
// module-wide compiler flag: a blanket -opt-in would silence the next
// experimental API to arrive, which is the warning worth reading.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris -> viewModel.read(uris) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Teach Cue your voice") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Screenshot 15 to 20 of your own past conversations and pick them here. " +
                        "Cue reads only the text, on this device, and throws the images away " +
                        "immediately. Fifty of your messages is the point where the profile " +
                        "starts describing you instead of an average.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item {
                Button(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !state.working,
                ) { Text("Pick screenshots") }
            }

            if (state.working) {
                item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        Spacer(Modifier.fillMaxWidth(0.05f))
                        Text("Reading…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.message?.let { message ->
                item { Text(message, style = MaterialTheme.typography.bodyMedium) }
            }

            if (state.extractedMine.isNotEmpty() || state.extractedTheirs.isNotEmpty()) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text("Which column is you?", fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Cue read ${state.screenshotsRead} screenshots and used the side " +
                                    "of the screen each message sat on. If these are backwards, " +
                                    "swap them — a profile built from her messages would make " +
                                    "every future draft sound like her.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = viewModel::swapSides) {
                                Text("Swap: I am on the ${if (state.alignedRight) "right" else "left"}")
                            }
                        }
                    }
                }

                item {
                    ColumnCard(
                        title = "You (${state.extractedMine.size} messages)",
                        lines = state.extractedMine,
                        container = MaterialTheme.colorScheme.surface,
                    )
                }
                item {
                    ColumnCard(
                        title = "Her (${state.extractedTheirs.size} messages, not stored)",
                        lines = state.extractedTheirs,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.confirm(onDone) }, enabled = !state.working) {
                            Text("That's me — save")
                        }
                        TextButton(onClick = viewModel::discard) { Text("Discard") }
                    }
                }
            }

            item {
                Text(
                    "${state.corpusSize} messages stored" +
                        if (state.calibrated) " — calibrated." else " — need 50.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ColumnCard(
    title: String,
    lines: List<String>,
    container: androidx.compose.ui.graphics.Color,
) {
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            if (lines.isEmpty()) {
                Text("(nothing)", style = MaterialTheme.typography.bodyMedium)
            }
            lines.take(40).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
            }
            if (lines.size > 40) {
                Text(
                    "…and ${lines.size - 40} more",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
