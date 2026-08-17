package dev.cue.app.capture

import android.net.Uri
import dev.cue.app.ocr.MlKitRecognizer
import dev.cue.capture.ChatLayout
import dev.cue.capture.ConversationStitcher
import dev.cue.capture.ProfileParser
import dev.cue.capture.RecognizedScreen
import dev.cue.data.repo.CueRepository
import dev.cue.data.settings.SettingsRepository
import dev.cue.draft.StageClassifier
import dev.cue.model.CapturedContext
import dev.cue.model.MatchProfile
import dev.cue.model.Platform
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What happens between "share to Cue" and "there are drafts on screen".
 *
 * Thin on purpose: OCR, then four calls into tested code, then a write. The
 * decisions live in `:core:capture` and `:core:draft`, so the part that only runs
 * on a device is the part with no judgement in it.
 */
@Singleton
class CaptureIngest @Inject constructor(
    private val recognizer: MlKitRecognizer,
    private val repository: CueRepository,
    private val settings: SettingsRepository,
) {

    sealed interface Result {
        data class Captured(
            val conversationId: String,
            val messageCount: Int,
            /** §13: ambiguous attribution asks rather than assuming. */
            val needsAttributionConfirmation: Boolean,
        ) : Result

        data class ProfileOnly(val conversationId: String, val profile: MatchProfile) : Result

        /** §13: "OCR returns no text → ask for a clearer screenshot; never guess." */
        data class NothingRecognised(val reason: String) : Result

        data class Excluded(val conversationId: String) : Result
    }

    suspend fun ingest(
        uris: List<Uri>,
        platform: Platform,
        capturedAt: Long,
        conversationId: String? = null,
    ): Result {
        val screens = uris.mapIndexedNotNull { index, uri ->
            runCatching {
                recognizer.recognize(uri, id = "shot-$index", capturedAt = capturedAt + index)
            }.getOrNull()
        }
        if (screens.isEmpty() || screens.all { it.blocks.isEmpty() }) {
            return Result.NothingRecognised("No text was recognised in that screenshot")
        }

        val layout = ChatLayout(
            myMessagesAlignedRight = settings.myMessagesAlignedRight(platform).first(),
        )

        // A profile screenshot has prompts and no bubbles; a chat has bubbles.
        // Deciding by what was found rather than by asking the user is worth it:
        // the alternative is a modal in front of every capture, which is the
        // friction §1 says the app exists to remove.
        val profile = ProfileParser.parse(screens, capturedAt)
        val stitched = ConversationStitcher.stitch(screens, resolveId(screens, conversationId), layout)

        if (stitched.messages.isEmpty()) {
            if (profile.isEmpty) {
                return Result.NothingRecognised("That does not look like a chat or a profile")
            }
            val id = resolveId(screens, conversationId)
            val stored = repository.conversation(id)
            if (stored?.excluded == true) return Result.Excluded(id)
            return Result.ProfileOnly(id, profile)
        }

        val id = resolveId(screens, conversationId)
        val existing = repository.conversation(id)
        if (existing?.excluded == true) return Result.Excluded(id)

        val context = CapturedContext(
            conversationId = id,
            platform = platform,
            // A chat screenshot rarely carries her profile. Keep whatever a
            // previous profile capture stored rather than overwriting it with the
            // empty parse of a chat screen.
            profile = profile.takeUnless { it.isEmpty } ?: existing?.profile ?: MatchProfile(),
            messages = stitched.messages,
            capturedAt = capturedAt,
            nowMillis = capturedAt,
        )

        val stage = StageClassifier.classify(context, existing?.lastTheirMessageAt)
        val written = repository.saveCapture(
            context = context,
            pseudonym = stitched.headerName ?: "Match ${id.takeLast(4)}",
            stage = stage,
        )
        if (!written) return Result.Excluded(id)

        // §8: a fresh capture is the only way to learn whether she replied.
        repository.resolveReplies(id, capturedAt)

        return Result.Captured(
            conversationId = id,
            messageCount = stitched.messages.size,
            needsAttributionConfirmation = stitched.needsConfirmation,
        )
    }

    /**
     * A stable id for a conversation across captures, derived from her name.
     *
     * Imperfect and deliberately so: two matches with the same first name share
     * an id and their captures merge. The alternative — asking "which
     * conversation is this?" on every share — costs a tap every time to prevent
     * something that happens rarely, and the merge is visible and fixable while a
     * modal on every capture is neither.
     */
    private fun resolveId(screens: List<RecognizedScreen>, provided: String?): String {
        if (provided != null) return provided
        val name = screens.firstNotNullOfOrNull { dev.cue.capture.ScreenChrome.headerName(it) }
        return name?.lowercase()?.replace(Regex("""[^a-z0-9]"""), "")?.ifEmpty { null }
            ?: "unknown-${screens.first().capturedAt}"
    }
}
