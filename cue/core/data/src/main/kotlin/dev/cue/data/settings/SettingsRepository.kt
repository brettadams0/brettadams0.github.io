package dev.cue.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.cue.model.ModelTier
import dev.cue.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cue.settings")

/**
 * The handful of things the app has to remember that are not conversations.
 *
 * [tier] is here rather than derived at each launch because §13's OOM response
 * is "drop one model tier **permanently**". Recomputing it from free RAM every
 * time would undo the demotion on the next boot and reproduce the crash.
 */
class SettingsRepository(private val context: Context) {

    val tier: Flow<ModelTier?> = context.dataStore.data.map { preferences ->
        preferences[TIER]?.let { runCatching { ModelTier.valueOf(it) }.getOrNull() }
    }

    suspend fun setTier(tier: ModelTier) {
        context.dataStore.edit { it[TIER] = tier.name }
    }

    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    /**
     * §13: "Attribution ambiguous → show both interpretations, ask which is you,
     * **remember per platform**."
     */
    fun myMessagesAlignedRight(platform: Platform): Flow<Boolean> =
        context.dataStore.data.map { it[alignmentKey(platform)] ?: true }

    suspend fun setMyMessagesAlignedRight(platform: Platform, alignedRight: Boolean) {
        context.dataStore.edit { it[alignmentKey(platform)] = alignedRight }
    }

    private fun alignmentKey(platform: Platform) =
        booleanPreferencesKey("alignment.${platform.name.lowercase()}")

    private companion object {
        val TIER = stringPreferencesKey("model.tier")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding.complete")
    }
}
