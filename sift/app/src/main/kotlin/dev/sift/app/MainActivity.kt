package dev.sift.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.sift.app.ui.SiftNav
import dev.sift.app.ui.grid.GridScreen
import dev.sift.app.ui.review.ReviewScreen
import dev.sift.app.ui.settings.SettingsScreen
import dev.sift.app.ui.theme.SiftTheme
import dev.sift.app.ui.triage.TriageScreen
import dev.sift.app.work.IngestWorker
import java.util.concurrent.CopyOnWriteArrayList

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * §8 — volume-key bindings: vol-down toss, vol-up keep.
     *
     * Faster than swiping and it lets the thumb rest, which is what makes 200
     * photos in one sitting tolerable. Registered as listeners rather than
     * handled here so whichever screen is on top decides whether the keys mean
     * anything; the review screen deliberately does not bind them, because
     * approving a grade is not a decision to make without looking.
     */
    private val volumeKeyListeners = CopyOnWriteArrayList<(Boolean) -> Boolean>()

    fun addVolumeKeyListener(listener: (Boolean) -> Boolean) {
        volumeKeyListeners += listener
    }

    fun removeVolumeKeyListener(listener: (Boolean) -> Boolean) {
        volumeKeyListeners -= listener
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val keep = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return super.onKeyDown(keyCode, event)
        }
        for (listener in volumeKeyListeners) {
            if (listener(keep)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SiftTheme {
                var granted by remember { mutableStateOf(hasMediaPermissions()) }

                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { results ->
                    granted = results[Manifest.permission.READ_MEDIA_IMAGES] == true
                    if (granted) IngestWorker.enqueue(this)
                }

                if (granted) {
                    SiftApp()
                } else {
                    PermissionGate { launcher.launch(REQUIRED_PERMISSIONS) }
                }
            }
        }

        if (hasMediaPermissions()) IngestWorker.enqueue(this)
    }

    private fun hasMediaPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            // Without this MediaStore silently strips GPS from every frame
            // (trap #12) — the loss is invisible until you go looking months later.
            Manifest.permission.ACCESS_MEDIA_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sift", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Sift reads your camera roll to let you triage it, and writes graded " +
                    "exports to Pictures/Sift. It has no network permission at all — " +
                    "nothing leaves the device.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Location access is for keeping GPS data in your exported photos. " +
                    "Without it Android quietly strips it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRequest) { Text("Grant access") }
        }
    }
}

@Composable
private fun SiftApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SiftNav.TRIAGE) {
        composable(SiftNav.TRIAGE) {
            TriageScreen(
                onOpenReview = { navController.navigate(SiftNav.REVIEW) },
                onOpenGrid = { navController.navigate(SiftNav.GRID) },
                onOpenSettings = { navController.navigate(SiftNav.SETTINGS) },
            )
        }
        composable(SiftNav.REVIEW) { ReviewScreen(onBack = { navController.popBackStack() }) }
        composable(SiftNav.GRID) { GridScreen(onBack = { navController.popBackStack() }) }
        composable(SiftNav.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
