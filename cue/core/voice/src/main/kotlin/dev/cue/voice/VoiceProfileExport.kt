package dev.cue.voice

import dev.cue.model.VoiceProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * §3.4. The voice profile as a file.
 *
 * No backend means no sync, so the Android app is the source of truth and the
 * Chrome extension imports this JSON by hand. The profile changes slowly —
 * manual is fine — but the format still needs a version, because the extension
 * will be on a different release than the app more often than not.
 *
 * This is a file the user moves between two devices they own. It never travels
 * over a network Cue opened (§2.3), and there is no network for it to travel
 * over.
 */
@Serializable
data class VoiceProfileExport(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val profile: VoiceProfile,
) {
    companion object {
        const val FORMAT = "cue.voice-profile"
        const val VERSION = 1

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(profile: VoiceProfile, exportedAt: Long): String =
            json.encodeToString<VoiceProfileExport>(
                VoiceProfileExport(exportedAt = exportedAt, profile = profile),
            )

        /**
         * Returns the profile, or throws with a message a human can act on.
         *
         * Import failures here are the user pasting the wrong file, not a bug,
         * so the errors name the file rather than the parser.
         */
        fun decode(text: String): VoiceProfile {
            val export = try {
                json.decodeFromString<VoiceProfileExport>(text)
            } catch (e: Exception) {
                throw IllegalArgumentException("That is not a Cue voice profile export.", e)
            }
            require(export.format == FORMAT) {
                "That file says it is '${export.format}', not a Cue voice profile."
            }
            require(export.version <= VERSION) {
                "That profile was exported by a newer version of Cue (format ${export.version}, " +
                    "this build reads $VERSION). Update the extension."
            }
            return export.profile
        }
    }
}
