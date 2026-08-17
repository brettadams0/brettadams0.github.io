package dev.cue.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.cue.app.ui.CueApp
import dev.cue.app.ui.theme.CueTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startConversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        setContent {
            CueTheme {
                CueApp(startConversationId = startConversationId)
            }
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "dev.cue.app.CONVERSATION_ID"
    }
}
