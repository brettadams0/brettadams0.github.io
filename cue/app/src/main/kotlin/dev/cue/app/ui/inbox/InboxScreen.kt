package dev.cue.app.ui.inbox

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cue.model.Conversation
import dev.cue.model.ConversationStage

/**
 * §9. The ball-in-your-court list, which the spec calls "the cheapest
 * high-value screen in the app", used as the app's home.
 *
 * It is the home screen because it answers the question you actually opened the
 * app with. A chronological list of every match answers "what happened", which
 * you already know.
 */
@Composable
fun InboxScreen(
    onOpenConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cue") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.calibrated) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text("Teach Cue your voice", fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${state.corpusSize} of 50 messages. Below fifty, drafts use a " +
                                    "generic casual baseline instead of yours.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = onOpenOnboarding) { Text("Add screenshots") }
                        }
                    }
                }
            }

            if (state.yourTurn.isNotEmpty()) {
                item { SectionHeader("Your turn", "${state.yourTurn.size} waiting on you") }
                items(state.yourTurn, key = { "turn-${it.id}" }) { conversation ->
                    ConversationRow(conversation, onOpenConversation)
                }
            }

            if (state.others.isNotEmpty()) {
                item { SectionHeader("Everything else", null) }
                items(state.others, key = { "other-${it.id}" }) { conversation ->
                    ConversationRow(conversation, onOpenConversation)
                }
            }

            if (state.yourTurn.isEmpty() && state.others.isEmpty()) {
                item {
                    Text(
                        "Nothing captured yet. Screenshot a conversation, share it, and pick Cue.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onOpen: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(conversation.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(conversation.matchPseudonym, fontWeight = FontWeight.Medium)
                Text(
                    conversation.stage.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (conversation.stage == ConversationStage.READY_TO_ASK) {
                Text(
                    "ASK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun ConversationStage.description(): String = when (this) {
    ConversationStage.OPENER -> "No messages yet"
    ConversationStage.EARLY_RAPPORT -> "Early"
    ConversationStage.ESTABLISHED -> "Going somewhere"
    ConversationStage.READY_TO_ASK -> "Ready to ask"
    ConversationStage.STALLING -> "Cooling off"
    ConversationStage.DEAD -> "Over a week quiet"
}
