package dev.sift.app.ui.review

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.sift.model.RegradeAction
import dev.sift.model.RejectionReason

/**
 * Review and approval UI (§9.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(onBack: () -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        state.message?.let { message ->
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = if (state.reasonOfferedForJobId != null) "Why?" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.offerReason()
            viewModel.consumeMessage()
        }
    }

    state.pendingReasonForJobId?.let {
        RejectionReasonDialog(
            onPick = viewModel::confirmReject,
            onDismiss = viewModel::cancelReject,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Review ${state.progressLabel}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFlag) {
                        Icon(Icons.Default.Flag, contentDescription = "Hold back for a second look")
                    }
                },
            )
        },
    ) { padding ->
        val item = state.current
        if (item == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Nothing waiting for review", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = viewModel::trashApprovedOriginals,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Trash originals of approved photos")
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ComparableFrame(
                gradedUri = item.job.outputUri ?: item.asset.uri,
                originalUri = item.asset.uri,
                comparing = state.comparing,
                onComparingChange = viewModel::setComparing,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            Text(
                if (state.comparing) "Original" else "Graded — hold the image to compare",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VerdictStrip(item)

            if (!item.canTrashOriginal) {
                Text(
                    item.disabledReason.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = viewModel::rejectCurrent, modifier = Modifier.weight(1f)) {
                    Text("Reject")
                }
                Button(onClick = viewModel::approveCurrent, modifier = Modifier.weight(1f)) {
                    Text("Approve")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { viewModel.regrade(RegradeAction.OTHER_PROFILE) }) {
                    Text("Other profile")
                }
                TextButton(onClick = { viewModel.regrade(RegradeAction.REDUCED_STRENGTH) }) {
                    Text("Half strength")
                }
                TextButton(onClick = { viewModel.regrade(RegradeAction.KEEP_ORIGINAL) }) {
                    Text("Keep original")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = viewModel::approveAllUnflagged, modifier = Modifier.weight(1f)) {
                    Text("Approve all")
                }
                OutlinedButton(onClick = viewModel::trashApprovedOriginals, modifier = Modifier.weight(1f)) {
                    Text("Trash originals")
                }
            }

            if (state.storageReadout.isNotEmpty()) {
                Text(state.storageReadout, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * §9.4 — press-and-hold to compare, pinch to 1:1, and **compare persists at
 * zoom**. Upscale artifacts and oversharpening are only visible there, so a
 * comparison that resets the zoom is a comparison you cannot use.
 */
@Composable
private fun ComparableFrame(
    gradedUri: String,
    originalUri: String,
    comparing: Boolean,
    onComparingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember(gradedUri) { mutableFloatStateOf(1f) }
    var offsetX by remember(gradedUri) { mutableFloatStateOf(0f) }
    var offsetY by remember(gradedUri) { mutableFloatStateOf(0f) }

    Card(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gradedUri) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(gradedUri) {
                    detectTapGestures(
                        onPress = {
                            onComparingChange(true)
                            tryAwaitRelease()
                            onComparingChange(false)
                        },
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2f
                            offsetX = 0f
                            offsetY = 0f
                        },
                    )
                },
        ) {
            // Both images stay loaded and only their opacity swaps. Swapping the
            // model instead made every press-and-hold a fresh decode, so the
            // comparison flickered and arrived late — and a colour comparison you
            // cannot make instantly is one you cannot make at all (§9.4).
            val frame = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }

            AsyncImage(
                model = gradedUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = frame.graphicsLayer { alpha = if (comparing) 0f else 1f },
            )
            AsyncImage(
                model = originalUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = frame.graphicsLayer { alpha = if (comparing) 1f else 0f },
            )
        }
    }
}

@Composable
private fun VerdictStrip(item: ReviewViewModel.Item) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                item.changeSummary(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            for (line in item.verdictLines()) {
                Text(
                    line,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (line.startsWith("FELL BACK") || line.startsWith("Failed")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun RejectionReasonDialog(
    onPick: (RejectionReason?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What was wrong? (optional)") },
        text = {
            Column {
                Text(
                    "Only if you have an opinion. This is the one signal Sift gets " +
                        "about whether its targets suit your photographs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (reason in RejectionReason.entries) {
                    TextButton(onClick = { onPick(reason) }) {
                        Text(reason.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(null) }) { Text("Skip") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
