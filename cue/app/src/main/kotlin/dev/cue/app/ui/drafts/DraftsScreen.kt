package dev.cue.app.ui.drafts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cue.draft.SuppressedVariant
import dev.cue.model.ConversationStage
import dev.cue.model.Draft

/**
 * The screen the whole app exists for: three strategically distinct drafts, a
 * copy button, and nothing that touches the other app.
 *
 * §2.1 is visible in what is absent. There is no send button, no "open in Hinge",
 * no autofill. The clipboard is the end of the line — you paste it yourself, in
 * the other app, having read it.
 */
@Composable
fun DraftsScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: DraftsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(conversationId) { viewModel.load(conversationId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.conversation?.matchPseudonym ?: "Drafts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.regenerate(conversationId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Draft again")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                // §12 budgets twelve seconds. Saying what it is doing is the
                // difference between "composing" and "frozen".
                Text("Reading the conversation and writing three drafts", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { message ->
                item { Banner(message, MaterialTheme.colorScheme.error) }
            }

            // §6.3: READY_TO_ASK is "the most valuable output in the app".
            // Surfaced as a banner, not a subtle variant label.
            if (state.set?.readyToAsk == true) {
                item {
                    Banner(
                        "She is asking questions and nobody has suggested meeting. " +
                            "This is the moment.",
                        MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (state.set?.stage == ConversationStage.DEAD) {
                item {
                    Banner(
                        "No reply in over a week. A warm close beats a fourth revival.",
                        MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            // §4.2: below fifty messages, a persistent calibrating banner.
            if (state.set?.calibrating == true) {
                item {
                    Banner(
                        "Still calibrating your voice — fewer than 50 of your messages so far. " +
                            "Drafts will read generic until then.",
                        MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            if (state.overThermalBudget) {
                item {
                    Banner(
                        "Twenty drafts this hour. The phone is getting warm — " +
                            "the template option costs nothing.",
                        MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            state.health?.signals?.takeIf { it.isNotEmpty() }?.let { signals ->
                item { Banner(signals.joinToString(". "), MaterialTheme.colorScheme.surfaceVariant) }
            }

            items(state.drafts, key = { it.id }) { draft ->
                DraftCard(
                    draft = draft,
                    onCopied = { edited -> viewModel.onCopied(draft, edited) },
                    onDiscarded = { viewModel.onDiscarded(draft) },
                )
            }

            // §7.2 ships nothing for a variant that invented something twice. An
            // empty slot with no explanation looks like a crash, so it is named.
            items(state.suppressed) { variant -> SuppressedCard(variant) }

            if (state.drafts.isEmpty() && state.suppressed.isEmpty()) {
                item {
                    Text(
                        "Nothing to draft yet. Share a screenshot of the conversation.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: Draft,
    onCopied: (String?) -> Unit,
    onDiscarded: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var edited by remember(draft.id) { mutableStateOf(draft.text) }
    var editing by remember(draft.id) { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // §6.1: each variant is labelled with its intent, because three
                // rewordings of one message is a worthless choice.
                Text(
                    draft.strategy.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                if (draft.gates.offVoice) {
                    // §7.1: shipped after two retries, with a visible badge.
                    Text(
                        "OFF-VOICE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (editing) {
                OutlinedTextField(
                    value = edited,
                    onValueChange = { edited = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(draft.text, style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(edited))
                        onCopied(edited.takeIf { it != draft.text })
                    },
                ) { Text("Copy") }

                TextButton(onClick = { editing = !editing }) {
                    Text(if (editing) "Done" else "Edit")
                }

                TextButton(onClick = onDiscarded) { Text("Not this one") }
            }
        }
    }
}

@Composable
private fun SuppressedCard(variant: SuppressedVariant) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                variant.strategy.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Held back: ${variant.reason.lowercase()}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Banner(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
