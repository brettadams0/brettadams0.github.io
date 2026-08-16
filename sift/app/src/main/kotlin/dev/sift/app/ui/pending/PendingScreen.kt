package dev.sift.app.ui.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sift.data.db.MediaAsset
import dev.sift.data.media.LifecycleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The bin, before it is emptied.
 *
 * Undo-the-last-decision is the wrong shape for the mistake people actually
 * make while triaging: you swipe the wrong way, notice four photos later, and
 * by then reversing it means reversing everything after it too. This screen
 * shows everything currently queued for deletion and lets one specific photo be
 * pulled back out, leaving every other decision alone.
 *
 * It is reachable from the Commit button, so the last thing between a decision
 * and the trash dialog is a chance to look at what is about to go.
 */
@HiltViewModel
class PendingViewModel @Inject constructor(
    private val lifecycle: LifecycleRepository,
) : ViewModel() {

    val pending: StateFlow<List<MediaAsset>> = lifecycle.pendingDeletions()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rescue(assetId: Long) {
        viewModelScope.launch { lifecycle.undoDecision(assetId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingScreen(
    onBack: () -> Unit,
    onCommit: () -> Unit,
    viewModel: PendingViewModel = hiltViewModel(),
) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${pending.size} to delete") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (pending.isNotEmpty()) {
                        IconButton(onClick = onCommit) { Text("Commit") }
                    }
                },
            )
        },
    ) { padding ->
        if (pending.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Nothing queued", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Photos you swipe left land here until you commit. Nothing is " +
                        "deleted before that.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Tap the arrow on any photo to take it back out of the bin. " +
                    "Nothing here is deleted until you commit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(pending, key = { it.id }) { asset ->
                    Box(modifier = Modifier.padding(2.dp)) {
                        AsyncImage(
                            model = asset.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        ) {
                            FilledIconButton(
                                onClick = { viewModel.rescue(asset.id) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Default.Undo,
                                    contentDescription = "Keep this one after all",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
