package dev.cue.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.cue.data.crypto.DatabaseKey
import dev.cue.data.db.CueDatabase
import dev.cue.data.repo.CueRepository
import dev.cue.data.settings.SettingsRepository
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CueDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = DatabaseKey(context).passphrase()
        return Room.databaseBuilder(context, CueDatabase::class.java, CueDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            // No fallbackToDestructiveMigration. The corpus is not
            // reconstructible — §4.2's onboarding is twenty screenshots of work,
            // and §8's corrections accumulate over months. A schema change ships
            // with a migration or it does not ship.
            .build()
    }

    @Provides
    @Singleton
    fun repository(database: CueDatabase): CueRepository = CueRepository(database)

    @Provides
    @Singleton
    fun settings(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)
}
