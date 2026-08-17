package dev.cue.inference.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.cue.data.settings.SettingsRepository
import dev.cue.inference.EngineHolder
import dev.cue.inference.ModelFiles
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    fun modelFiles(@ApplicationContext context: Context): ModelFiles = ModelFiles(context)

    @Provides
    @Singleton
    fun engineHolder(
        @ApplicationContext context: Context,
        files: ModelFiles,
        settings: SettingsRepository,
    ): EngineHolder = EngineHolder(context, files, settings)
}
