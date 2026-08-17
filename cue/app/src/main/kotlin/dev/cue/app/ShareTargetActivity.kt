package dev.cue.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.cue.app.capture.CaptureIngest
import dev.cue.model.Platform
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * §5.1. The share sheet, which is the entire capture surface in v1.
 *
 * > Three taps, no special permissions, identical across both apps, immune to
 * > redesigns. Ugly and unbreakable. **Ship this before anything smarter** — it
 * > proves whether you use the app at all, which is the real risk.
 *
 * The activity renders nothing. It ingests, hands off to [MainActivity], and
 * finishes, so the visible behaviour of sharing a screenshot is that the drafts
 * screen opens.
 */
@AndroidEntryPoint
class ShareTargetActivity : ComponentActivity() {

    @Inject lateinit var ingest: CaptureIngest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = incomingImages()
        if (uris.isEmpty()) {
            toast("No screenshot came through")
            finish()
            return
        }

        lifecycleScope.launch {
            val result = ingest.ingest(
                uris = uris,
                // Which app the screenshot came from is not in the intent, and
                // guessing from pixels would be worse than asking. Both apps use
                // the same layout convention, so HINGE is the default and the
                // per-platform alignment memory (§13) is what actually matters.
                platform = Platform.HINGE,
                capturedAt = System.currentTimeMillis(),
            )
            when (result) {
                is CaptureIngest.Result.Captured -> open(result.conversationId)

                is CaptureIngest.Result.ProfileOnly -> open(result.conversationId)

                is CaptureIngest.Result.NothingRecognised -> {
                    // §13: ask for a clearer screenshot; never guess.
                    toast("${result.reason}. Try a clearer screenshot.")
                }

                is CaptureIngest.Result.Excluded ->
                    toast("Cue is excluded from that conversation")
            }
            finish()
        }
    }

    private fun incomingImages(): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))

        Intent.ACTION_SEND_MULTIPLE ->
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()

        else -> emptyList()
    }

    private fun open(conversationId: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
