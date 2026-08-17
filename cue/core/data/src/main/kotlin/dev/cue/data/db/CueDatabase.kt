package dev.cue.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.cue.model.ConversationStage
import dev.cue.model.DraftAction
import dev.cue.model.ModelTier
import dev.cue.model.Platform
import dev.cue.model.Sender
import dev.cue.model.Strategy

/**
 * Enums stored by name, never by ordinal.
 *
 * §6.1's [Strategy] list will gain entries, and inserting one in the middle
 * would silently reinterpret every stored row if ordinals were the key —
 * turning last month's playful openers into logistics nudges in the stats
 * §8 draws conclusions from.
 */
class CueConverters {
    @TypeConverter fun toPlatform(value: String) = Platform.valueOf(value)
    @TypeConverter fun fromPlatform(value: Platform) = value.name

    @TypeConverter fun toSender(value: String) = Sender.valueOf(value)
    @TypeConverter fun fromSender(value: Sender) = value.name

    @TypeConverter fun toStage(value: String?) = value?.let { ConversationStage.valueOf(it) }
    @TypeConverter fun fromStage(value: ConversationStage?) = value?.name

    @TypeConverter fun toStrategy(value: String) = Strategy.valueOf(value)
    @TypeConverter fun fromStrategy(value: Strategy) = value.name

    @TypeConverter fun toTier(value: String) = ModelTier.valueOf(value)
    @TypeConverter fun fromTier(value: ModelTier) = value.name

    @TypeConverter fun toAction(value: String) = DraftAction.valueOf(value)
    @TypeConverter fun fromAction(value: DraftAction) = value.name
}

/**
 * §10. The local database, encrypted, with no counterpart anywhere else.
 *
 * There is no sync, no backup, and no server, so this file is the only copy of
 * a stranger's private messages that exists. That is the argument for SQLCipher
 * over plain Room: the threat is not a network attacker, it is a device backup,
 * a lost phone, or another app on a rooted handset.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        CorpusEntryEntity::class,
        DraftEntity::class,
        OutcomeEntity::class,
        VoiceProfileEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(CueConverters::class)
abstract class CueDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun corpus(): CorpusDao
    abstract fun drafts(): DraftDao
    abstract fun outcomes(): OutcomeDao
    abstract fun voiceProfile(): VoiceProfileDao

    companion object {
        const val NAME = "cue.db"
    }
}
