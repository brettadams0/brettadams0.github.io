package dev.cue.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.cue.app.ocr.MlKitRecognizer
import dev.cue.draft.GenerationBudget
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun recognizer(@ApplicationContext context: Context): MlKitRecognizer = MlKitRecognizer(context)

    /**
     * §12's thermal budget is process-wide, not per screen.
     *
     * Twenty generations an hour is a property of the phone getting hot, so a
     * budget scoped to a ViewModel would reset on every rotation and enforce
     * nothing.
     */
    @Provides
    @Singleton
    fun generationBudget(): GenerationBudget = GenerationBudget()
}
