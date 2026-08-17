package dev.cue.app.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.cue.app.draft.DraftService
import dev.cue.app.ocr.MlKitRecognizer
import dev.cue.capture.ChatLayout
import dev.cue.capture.ConversationStitcher
import dev.cue.data.repo.CueRepository
import dev.cue.data.settings.SettingsRepository
import dev.cue.model.Platform
import dev.cue.model.SentMessage
import dev.cue.model.Sender
import dev.cue.model.VoiceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val working: Boolean = false,
    /** What the app believes you wrote. Shown for confirmation before anything is stored. */
    val extractedMine: List<String> = emptyList(),
    /** What it believes she wrote, shown beside it so a reversal is obvious. */
    val extractedTheirs: List<String> = emptyList(),
    val screenshotsRead: Int = 0,
    val alignedRight: Boolean = true,
    val corpusSize: Int = 0,
    val message: String? = null,
) {
    val calibrated: Boolean get() = corpusSize >= VoiceProfile.MIN_SAMPLES
}

/**
 * §4.2. Bootstrapping the voice profile from screenshots of your own past
 * conversations.
 *
 * The confirmation step is not politeness. §4.2:
 *
 * > **Highest-stakes logic in the app.** Reverse it and you build a voice profile
 * > from *her* messages, then generate drafts that sound like the person you're
 * > talking to — subtly wrong, hard to diagnose, and it poisons everything
 * > downstream. Display the extracted messages during onboarding and require
 * > explicit confirmation before writing the profile.
 *
 * So both sides are shown, side by side, labelled, with a swap control — and
 * nothing is written until you say which column is you.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val recognizer: MlKitRecognizer,
    private val repository: CueRepository,
    private val settings: SettingsRepository,
    private val drafting: DraftService,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var pendingMine: List<SentMessage> = emptyList()

    /**
     * The recognised screens are kept so that swapping sides can re-extract
     * rather than transpose two lists of strings.
     *
     * Transposing looked equivalent and was not: `precedingTheirMessage` is what
     * makes §4.3 retrieve on situation instead of vocabulary, and it cannot be
     * recovered from the display text once the two columns have been swapped. The
     * corpus would have been built with every pairing silently null.
     *
     * They hold text and rectangles, never images (§10).
     */
    private var lastScreens: List<dev.cue.capture.RecognizedScreen> = emptyList()

    fun read(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true, message = null)
            val now = System.currentTimeMillis()

            val screens = uris.mapIndexedNotNull { index, uri ->
                runCatching { recognizer.recognize(uri, "onboard-$index", now + index) }.getOrNull()
            }
            if (screens.isEmpty()) {
                _state.value = _state.value.copy(
                    working = false,
                    message = "No text was recognised. Try screenshots without the keyboard open.",
                )
                return@launch
            }

            lastScreens = screens
            extract(screens, _state.value.alignedRight)
        }
    }

    /** §13: remember which side is you, per platform. */
    fun swapSides() {
        val flipped = !_state.value.alignedRight
        viewModelScope.launch {
            settings.setMyMessagesAlignedRight(Platform.HINGE, flipped)
            settings.setMyMessagesAlignedRight(Platform.TINDER, flipped)
            if (lastScreens.isNotEmpty()) extract(lastScreens, flipped)
        }
    }

    /**
     * Writes the confirmed messages into the corpus and recomputes the profile.
     *
     * Each screenshot's messages are paired with the message before them from
     * her, so §4.3 can retrieve on *situation* rather than vocabulary. Onboarding
     * is the only chance to capture that pairing — a bare list of your messages
     * would make retrieval a bag of words forever.
     */
    fun confirm(onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(working = true)
            repository.saveCorpus(pendingMine, sourceConversationId = null)
            drafting.recomputeVoiceProfile(System.currentTimeMillis())
            val size = repository.corpus().size
            settings.setOnboardingComplete(size >= VoiceProfile.MIN_SAMPLES)
            _state.value = _state.value.copy(
                working = false,
                corpusSize = size,
                extractedMine = emptyList(),
                extractedTheirs = emptyList(),
                message = if (size >= VoiceProfile.MIN_SAMPLES) {
                    "$size messages. Your voice profile is calibrated."
                } else {
                    "$size of 50. Add a few more conversations."
                },
            )
            onDone()
        }
    }

    fun discard() {
        pendingMine = emptyList()
        lastScreens = emptyList()
        _state.value = _state.value.copy(
            extractedMine = emptyList(),
            extractedTheirs = emptyList(),
            message = "Discarded. Nothing was stored.",
        )
    }

    private suspend fun extract(
        screens: List<dev.cue.capture.RecognizedScreen>,
        alignedRight: Boolean,
    ) {
        val layout = ChatLayout(myMessagesAlignedRight = alignedRight)

        val mine = mutableListOf<SentMessage>()
        val theirs = mutableListOf<String>()

        screens.forEachIndexed { index, screen ->
            // Stitched per screenshot rather than across all of them: these are
            // twenty *different* conversations, and overlap dedup across
            // unrelated threads would delete real messages that happen to match.
            val stitched = ConversationStitcher.stitch(listOf(screen), "onboard-$index", layout)
            stitched.messages.forEach { message ->
                if (message.sender == Sender.THEM) {
                    theirs += message.text
                    return@forEach
                }
                // §4.2: below 0.8 confidence, excluded from the profile.
                if (!message.trustedForVoiceProfile) return@forEach
                mine += SentMessage(
                    id = "onboard-$index:${message.sequence}",
                    text = message.text,
                    precedingTheirMessage = stitched.messages
                        .lastOrNull { it.sequence < message.sequence && it.sender == Sender.THEM }
                        ?.text,
                    stage = null,
                    sentAt = null,
                )
            }
        }

        pendingMine = mine
        _state.value = _state.value.copy(
            working = false,
            extractedMine = mine.map { it.text },
            extractedTheirs = theirs,
            screenshotsRead = screens.size,
            alignedRight = alignedRight,
            corpusSize = repository.corpus().size,
            message = null,
        )
    }
}
