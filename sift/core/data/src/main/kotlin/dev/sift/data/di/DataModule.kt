package dev.sift.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.sift.data.db.SiftDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * §4.3 — the imaging dispatcher, with parallelism capped at 2.
 *
 * A 12MP frame held as unbounded float RGB is about 144MB, and three concurrent
 * grade jobs will OOM on most devices. The cap is not a throughput tuning knob;
 * it is the difference between a batch of 200 photos finishing and the app dying
 * two thirds of the way through.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ImagingDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SiftDatabase =
        Room.databaseBuilder(context, SiftDatabase::class.java, SiftDatabase.NAME)
            // No destructive fallback: this database is the only record of which
            // originals have been trashed (§9.1).
            .addMigrations(*SiftDatabase.MIGRATIONS)
            .build()

    @Provides fun mediaAssetDao(db: SiftDatabase) = db.mediaAssets()

    @Provides fun triageDecisionDao(db: SiftDatabase) = db.triageDecisions()

    @Provides fun editJobDao(db: SiftDatabase) = db.editJobs()

    @Provides fun lifecycleEventDao(db: SiftDatabase) = db.lifecycleEvents()

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    @ImagingDispatcher
    fun imagingDispatcher(): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)
}
