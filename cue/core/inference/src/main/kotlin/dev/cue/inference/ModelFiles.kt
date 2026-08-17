package dev.cue.inference

import android.app.ActivityManager
import android.content.Context
import dev.cue.model.ModelTier
import java.io.File

/**
 * §3.3. Where the model file lives and which one it should be.
 *
 * The app does not download it. §2.3 ships without the `INTERNET` permission at
 * all, so acquiring the file is a separate act: either a one-time downloader the
 * user installs and then removes, or `adb push` / a file manager drop into the
 * app's own storage. That is inconvenient exactly once, and it is what makes
 * "nothing leaves your device" a property of the binary rather than a promise in
 * a settings screen.
 */
class ModelFiles(private val context: Context) {

    /** Where a dropped model is looked for, in order. */
    fun candidates(tier: ModelTier): List<File> = listOf(
        File(modelDirectory(), "${tier.modelId}.task"),
        File(modelDirectory(), "${tier.modelId}.litertlm"),
    )

    fun modelDirectory(): File = File(context.filesDir, "models").apply { mkdirs() }

    fun installed(tier: ModelTier): File? = candidates(tier).firstOrNull { it.isFile && it.length() > 0 }

    /** Every tier a model file is actually present for, best first. */
    fun availableTiers(): List<ModelTier> =
        listOf(ModelTier.E4B, ModelTier.E2B, ModelTier.ONE_B).filter { installed(it) != null }

    /**
     * §3.3's table, applied to *measured* free memory — trap 8.
     *
     * `availMem` rather than `totalMem`: a 8 GB phone with 2 GB free cannot hold
     * E4B, and choosing by total memory is how you get an OOM on a device the
     * spec's table said was fine.
     */
    fun tierForThisDevice(): ModelTier {
        val info = ActivityManager.MemoryInfo()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.getMemoryInfo(info)
        val freeMb = (info.availMem / BYTES_PER_MB).toInt()
        return ModelTier.forFreeRamMb(freeMb)
    }

    /**
     * The tier to actually load: the best one that is both installed and within
     * the device's means.
     *
     * Returns [ModelTier.TEMPLATE_ONLY] when nothing is installed, which is a
     * supported state (§13) rather than an error — §6.5's template path works
     * with no model at all.
     */
    fun resolveTier(demotedTo: ModelTier?): ModelTier {
        val ceiling = demotedTo ?: tierForThisDevice()
        val installed = availableTiers()
        return installed.firstOrNull { it.approxDiskMb <= ceiling.approxDiskMb }
            ?: ModelTier.TEMPLATE_ONLY
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
