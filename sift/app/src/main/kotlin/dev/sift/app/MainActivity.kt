package dev.sift.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import dev.sift.app.ui.pending.PendingScreen
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

    private fun isVolumeKey(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isVolumeKey(keyCode)) return super.onKeyDown(keyCode, event)

        // Auto-repeat would fire a verdict every ~50ms while the key is held,
        // tossing a run of photos from one accidental long press. Only the
        // initial press counts — but the repeats are still swallowed below so
        // holding a key does not leak through to the volume stream.
        if (event != null && event.repeatCount > 0) return true

        val keep = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        for (listener in volumeKeyListeners) {
            if (listener(keep)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Consuming only `onKeyDown` is not enough to suppress the volume UI.
     *
     * The framework adjusts the stream on key-down but shows the volume panel on
     * key-**up**, so a deck bound to the volume keys would flash a slider over
     * the photo on every single decision. Swallow the up event whenever a
     * listener is active, and only then — with no deck on screen the keys must
     * still work as volume keys.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumeKey(keyCode) && volumeKeyListeners.isNotEmpty()) return true
        return super.onKeyUp(keyCode, event)
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
                    // Re-check rather than reading the result map: the map is
                    // keyed by the permission actually requested, which differs
                    // by OS version, and POST_NOTIFICATIONS being declined must
                    // not block the app.
                    granted = results[readImagesPermission()] == true || hasMediaPermissions()
                    if (granted) IngestWorker.enqueue(this)
                }

                if (granted) {
                    SiftApp()
                } else {
                    PermissionGate { launcher.launch(requiredPermissions()) }
                }
            }
        }

        if (hasMediaPermissions()) IngestWorker.enqueue(this)
    }

    /**
     * The read permission for images, which is not the same string on every
     * supported version.
     *
     * `READ_MEDIA_IMAGES` arrived in API 33. `minSdk` here is 30, so on Android
     * 11 and 12 that constant names a permission the platform has never heard
     * of: `checkSelfPermission` returns DENIED forever and the request is a
     * no-op, leaving the app permanently stuck on its own permission screen with
     * no way forward. Below 33 the correct permission is `READ_EXTERNAL_STORAGE`.
     */
    private fun readImagesPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            @Suppress("DEPRECATION")
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasMediaPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, readImagesPermission()) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Everything worth asking for, filtered to what this OS version has.
     *
     * `POST_NOTIFICATIONS` is also API 33+; requesting it on older versions is
     * harmless but pointless. Neither it nor `ACCESS_MEDIA_LOCATION` gates the
     * app — declining notifications costs you the grading progress bar, and
     * declining media location costs GPS in exports (trap #12), but neither
     * should stop you triaging.
     */
    private fun requiredPermissions(): Array<String> = buildList {
        add(readImagesPermission())
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
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
                onOpenPending = { navController.navigate(SiftNav.PENDING) },
            )
        }
        composable(SiftNav.PENDING) {
            PendingScreen(
                onBack = { navController.popBackStack() },
                onCommit = { navController.popBackStack() },
            )
        }
        composable(SiftNav.REVIEW) { ReviewScreen(onBack = { navController.popBackStack() }) }
        composable(SiftNav.GRID) { GridScreen(onBack = { navController.popBackStack() }) }
        composable(SiftNav.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
