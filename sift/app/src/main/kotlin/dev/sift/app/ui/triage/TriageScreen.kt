package dev.sift.app.ui.triage

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.sift.app.MainActivity
import dev.sift.model.ContentClass
import dev.sift.model.Verdict
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The swipe deck (§8).
 *
 * Right = keep, left = toss, up = skip. Long-press gives 1:1 zoom, because
 * sharpness uncertainty is the most common reason to hesitate and it should be
 * resolvable in one gesture rather than a trip to another screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageScreen(
    onOpenReview: () -> Unit,
    onOpenGrid: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPending: () -> Unit,
    viewModel: TriageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onTrashResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(state.trashRequest) {
        state.trashRequest?.let {
            trashLauncher.launch(IntentSenderRequest.Builder(it.intent.intentSender).build())
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // §8 — volume-key bindings. Registered while this screen is on top only.
    val activity = context as? MainActivity
    DisposableEffect(activity) {
        val listener: (Boolean) -> Boolean = { keep ->
            viewModel.decide(if (keep) Verdict.KEEP else Verdict.TOSS)
            true
        }
        activity?.addVolumeKeyListener(listener)
        onDispose { activity?.removeVolumeKeyListener(listener) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.progressLabel) },
                actions = {
                    if (state.canUndo) {
                        IconButton(onClick = viewModel::undo) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo last decision")
                        }
                    }
                    // Review used to be reachable only from the empty state,
                    // which meant graded photos could pile up unreviewed with no
                    // visible way in — and §9 only works if you actually look at
                    // them before the originals go.
                    if (state.pendingReview > 0) {
                        BadgedBox(
                            badge = { Badge { Text("${state.pendingReview}") } },
                        ) {
                            IconButton(onClick = onOpenReview) {
                                Icon(
                                    Icons.Default.RateReview,
                                    contentDescription = "${state.pendingReview} photos to review",
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isEmpty) {
                EmptyState(
                    libraryTotal = state.total,
                    reviewed = state.reviewed,
                    pendingToss = state.pendingToss,
                    onCommit = onOpenPending,
                    onReview = onOpenReview,
                    onRescan = viewModel::rescanLibrary,
                )
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // The next card sits underneath, so a swipe reveals rather
                    // than flashing an empty frame.
                    state.deck.getOrNull(1)?.let { next ->
                        PhotoCard(uri = next.uri, modifier = Modifier.fillMaxSize())
                    }
                    state.current?.let { current ->
                        SwipeableCard(
                            uri = current.uri,
                            onKeep = { viewModel.decide(Verdict.KEEP) },
                            onToss = { viewModel.decide(Verdict.TOSS) },
                            onSkip = { viewModel.decide(Verdict.SKIP) },
                        )
                    }
                }

                if (state.cluster.size > 1) {
                    ClusterFilmstrip(
                        members = state.cluster,
                        suggestedKeeperId = state.suggestedKeeperId,
                        onPromote = viewModel::promoteInCluster,
                    )
                }

                if (state.current?.contentClass == ContentClass.NON_PHOTOGRAPHIC) {
                    OutlinedButton(
                        onClick = viewModel::tossAllNonPhotographic,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Bin all screenshots and documents")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = { viewModel.decide(Verdict.TOSS) }) {
                        Icon(Icons.Default.Close, contentDescription = "Toss")
                    }
                    // Goes to the bin rather than straight to the trash dialog:
                    // the last chance to pull one photo back out is worth more
                    // than one saved tap.
                    OutlinedButton(
                        onClick = { if (state.pendingToss > 0) onOpenPending() else viewModel.commit() },
                    ) {
                        Text(if (state.pendingToss > 0) "Bin ${state.pendingToss}" else "Commit")
                    }
                    IconButton(onClick = { viewModel.decide(Verdict.KEEP) }) {
                        Icon(Icons.Default.Check, contentDescription = "Keep")
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableCard(
    uri: String,
    onKeep: () -> Unit,
    onToss: () -> Unit,
    onSkip: () -> Unit,
) {
    var offsetX by remember(uri) { mutableFloatStateOf(0f) }
    var offsetY by remember(uri) { mutableFloatStateOf(0f) }
    var zoomed by remember(uri) { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
                rotationZ = (offsetX / 40f).coerceIn(-12f, 12f)
            }
            .pointerInput(uri) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            offsetX > threshold -> onKeep()
                            offsetX < -threshold -> onToss()
                            offsetY < -threshold -> onSkip()
                        }
                        offsetX = 0f
                        offsetY = 0f
                    },
                ) { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .pointerInput(uri) {
                // §8 — long-press for 1:1 zoom. One gesture, no navigation.
                detectTapGestures(
                    onLongPress = { zoomed = true },
                    onPress = {
                        tryAwaitRelease()
                        zoomed = false
                    },
                )
            },
    ) {
        PhotoCard(
            uri = uri,
            modifier = Modifier.fillMaxSize(),
            contentScale = if (zoomed) ContentScale.None else ContentScale.Fit,
        )

        if (abs(offsetX) > 24f) {
            val keeping = offsetX > 0
            Text(
                text = if (keeping) "KEEP" else "TOSS",
                style = MaterialTheme.typography.headlineMedium,
                color = if (keeping) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier
                    .align(if (keeping) Alignment.TopStart else Alignment.TopEnd)
                    .padding(24.dp)
                    .graphicsLayer { alpha = (abs(offsetX) / threshold).coerceIn(0f, 1f) },
            )
        }
    }
}

@Composable
private fun PhotoCard(
    uri: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Card(shape = RoundedCornerShape(12.dp), modifier = modifier) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/**
 * §8 — a cluster shows its suggested keeper large with the rest as a filmstrip;
 * tapping promotes.
 */
@Composable
private fun ClusterFilmstrip(
    members: List<dev.sift.data.db.MediaAsset>,
    suggestedKeeperId: Long?,
    onPromote: (Long) -> Unit,
) {
    Column {
        Text(
            "${members.size} in this burst",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(72.dp),
        ) {
            items(members, key = { it.id }) { member ->
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .pointerInput(member.id) {
                            detectTapGestures { onPromote(member.id) }
                        },
                ) {
                    AsyncImage(
                        model = member.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().aspectRatio(1f),
                    )
                    if (member.id == suggestedKeeperId) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Suggested keeper",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * §8 — the empty state offers the next thing to do rather than a blank screen.
 *
 * It also has to tell the truth about *why* it is empty. "Deck clear" in front of
 * someone who has just installed the app and whose library never scanned is
 * actively misleading: it reads as "nothing to do" when the real state is
 * "nothing was found", and it leaves no next action. The three cases are
 * genuinely different and each gets its own wording and its own button.
 */
@Composable
private fun EmptyState(
    libraryTotal: Int,
    reviewed: Int,
    pendingToss: Int,
    onCommit: () -> Unit,
    onReview: () -> Unit,
    onRescan: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (libraryTotal == 0) {
            // Nothing in the database at all: the scan has not finished, or it
            // failed, or the permission was granted to a subset that excludes
            // everything.
            Text("No photos yet", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Sift has not found anything in your library. The first scan runs in " +
                    "the background and can take a minute or two on a large roll.\n\n" +
                    "If it stays empty, check that Sift has access to all your photos " +
                    "rather than a selected few, then rescan.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRescan) { Text("Rescan library") }
        } else {
            Text("Deck clear", style = MaterialTheme.typography.headlineSmall)
            Text(
                "You have been through all $libraryTotal photos" +
                    if (reviewed > 0) " ($reviewed reviewed)." else ".",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pendingToss > 0) {
                Button(onClick = onCommit) { Text("Commit $pendingToss deletions") }
            }
            OutlinedButton(onClick = onRescan) { Text("Rescan library") }
        }
        OutlinedButton(onClick = onReview) { Text("Review graded photos") }
    }
}
