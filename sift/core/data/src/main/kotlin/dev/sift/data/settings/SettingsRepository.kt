package dev.sift.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sift.model.ExportPreset
import dev.sift.model.GradeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sift_settings")

/**
 * Settings (§11).
 *
 * The portrait L\*/a\*/b\* targets are editable on purpose: §11 notes that
 * hardcoding them means editing Kotlin to change a number, and §9.5 exists
 * specifically so a run of `TOO_WARM` rejections tells you which number to move.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<GradeSettings> = context.dataStore.data.map { prefs ->
        GradeSettings(
            routing = prefs[ROUTING]?.let { runCatching { GradeSettings.RoutingMode.valueOf(it) }.getOrNull() }
                ?: GradeSettings.RoutingMode.AUTO,
            autoGradeOnCommit = prefs[AUTO_GRADE] ?: true,
            upscale = prefs[UPSCALE]?.let { runCatching { GradeSettings.UpscaleMode.valueOf(it) }.getOrNull() }
                ?: GradeSettings.UpscaleMode.GATED,
            portraitTargetL = prefs[TARGET_L] ?: GradeSettings.DEFAULT_PORTRAIT_TARGET_L,
            portraitTargetA = prefs[TARGET_A] ?: GradeSettings.DEFAULT_PORTRAIT_TARGET_A,
            portraitTargetB = prefs[TARGET_B] ?: GradeSettings.DEFAULT_PORTRAIT_TARGET_B,
            detailBlendFraction = prefs[DETAIL_BLEND] ?: GradeSettings.DEFAULT_DETAIL_BLEND,
            enabledPresets = prefs[PRESETS]
                ?.mapNotNull { name -> runCatching { ExportPreset.valueOf(name) }.getOrNull() }
                ?.toSet()
                ?.ifEmpty { setOf(ExportPreset.MASTER) }
                ?: setOf(ExportPreset.MASTER),
            dumpDebugJson = prefs[DEBUG_DUMP] ?: false,
        )
    }

    suspend fun setRouting(mode: GradeSettings.RoutingMode) = put { it[ROUTING] = mode.name }

    suspend fun setAutoGrade(enabled: Boolean) = put { it[AUTO_GRADE] = enabled }

    suspend fun setUpscale(mode: GradeSettings.UpscaleMode) = put { it[UPSCALE] = mode.name }

    suspend fun setPortraitTarget(l: Float, a: Float, b: Float) = put {
        it[TARGET_L] = l
        it[TARGET_A] = a
        it[TARGET_B] = b
    }

    suspend fun setDetailBlend(fraction: Float) = put {
        it[DETAIL_BLEND] = fraction.coerceIn(0f, 0.5f)
    }

    suspend fun setPresets(presets: Set<ExportPreset>) = put {
        it[PRESETS] = presets.ifEmpty { setOf(ExportPreset.MASTER) }.map(ExportPreset::name).toSet()
    }

    suspend fun setDebugDump(enabled: Boolean) = put { it[DEBUG_DUMP] = enabled }

    /** §11 — reset to validated defaults. */
    suspend fun resetToValidatedDefaults() {
        context.dataStore.edit { it.clear() }
    }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val ROUTING = stringPreferencesKey("routing")
        val AUTO_GRADE = booleanPreferencesKey("auto_grade_on_commit")
        val UPSCALE = stringPreferencesKey("upscale")
        val TARGET_L = floatPreferencesKey("portrait_target_l")
        val TARGET_A = floatPreferencesKey("portrait_target_a")
        val TARGET_B = floatPreferencesKey("portrait_target_b")
        val DETAIL_BLEND = floatPreferencesKey("detail_blend")
        val PRESETS = stringSetPreferencesKey("enabled_presets")
        val DEBUG_DUMP = booleanPreferencesKey("debug_dump")
    }
}
