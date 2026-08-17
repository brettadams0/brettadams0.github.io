package dev.cue.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun byId(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE excluded = 0 ORDER BY lastCapturedAt DESC")
    fun active(): Flow<List<ConversationEntity>>

    /**
     * §9's ball-in-your-court list: she replied last, oldest first.
     *
     * "The cheapest high-value screen in the app" — and cheapest is literal, it
     * is one indexed query with no inference and no computation behind it.
     */
    @Query(
        """
        SELECT * FROM conversations
        WHERE excluded = 0
          AND lastTheirMessageAt IS NOT NULL
          AND (lastMyMessageAt IS NULL OR lastTheirMessageAt > lastMyMessageAt)
        ORDER BY lastTheirMessageAt ASC
        """,
    )
    fun ballInYourCourt(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET excluded = :excluded WHERE id = :id")
    suspend fun setExcluded(id: String, excluded: Boolean)

    /**
     * §10's exclude toggle has to take effect retroactively, not just from now
     * on. A toggle that leaves her messages in the database is a setting, not a
     * guarantee.
     */
    @Transaction
    suspend fun excludeAndPurge(id: String) {
        setExcluded(id, true)
        deleteMessages(id)
        deleteDrafts(id)
        deleteCorpusFrom(id)
    }

    @Query("DELETE FROM messages WHERE conversationId = :id")
    suspend fun deleteMessages(id: String)

    @Query("DELETE FROM drafts WHERE conversationId = :id")
    suspend fun deleteDrafts(id: String)

    @Query("DELETE FROM corpus WHERE sourceConversationId = :id")
    suspend fun deleteCorpusFrom(id: String)
}

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY sequence ASC")
    suspend fun forConversation(id: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :id")
    suspend fun count(id: String): Int

    /**
     * Replaces a conversation's messages wholesale on re-capture.
     *
     * The alternative — merging by sequence — sounds cheaper and is wrong: a
     * later capture may start mid-scroll, so its sequence 0 is not the same
     * message as the stored sequence 0. The stitcher already resolves overlap
     * across screenshots (§5.1); the database's job is to hold its answer, not
     * to attempt a second reconciliation with different information.
     */
    @Transaction
    suspend fun replaceAll(conversationId: String, messages: List<MessageEntity>) {
        deleteFor(conversationId)
        insertAll(messages)
    }

    @Query("DELETE FROM messages WHERE conversationId = :id")
    suspend fun deleteFor(id: String)
}

@Dao
interface CorpusDao {

    @Upsert
    suspend fun upsert(entries: List<CorpusEntryEntity>)

    @Upsert
    suspend fun upsert(entry: CorpusEntryEntity)

    @Query("SELECT * FROM corpus ORDER BY sentAt ASC")
    suspend fun all(): List<CorpusEntryEntity>

    @Query("SELECT * FROM corpus ORDER BY sentAt ASC")
    fun observe(): Flow<List<CorpusEntryEntity>>

    @Query("SELECT COUNT(*) FROM corpus")
    fun size(): Flow<Int>

    @Query("DELETE FROM corpus")
    suspend fun clear()
}

@Dao
interface DraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(drafts: List<DraftEntity>)

    @Query("SELECT * FROM drafts WHERE conversationId = :id ORDER BY createdAt DESC")
    fun forConversation(id: String): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun byId(id: String): DraftEntity?
}

@Dao
interface OutcomeDao {

    @Upsert
    suspend fun upsert(outcome: OutcomeEntity)

    @Query("SELECT * FROM outcomes")
    suspend fun all(): List<OutcomeEntity>

    @Query("SELECT * FROM outcomes")
    fun observe(): Flow<List<OutcomeEntity>>

    /**
     * §8: `gotReply` is resolved on the next capture of that conversation, so
     * the rows waiting on an answer have to be findable.
     */
    @Query(
        """
        SELECT * FROM outcomes
        WHERE gotReply IS NULL AND action != 'DISCARDED' AND conversationId = :conversationId
        ORDER BY recordedAt ASC
        """,
    )
    suspend fun awaitingReply(conversationId: String): List<OutcomeEntity>

    @Query("UPDATE outcomes SET gotReply = :gotReply, replyLatencyMs = :latencyMs WHERE draftId = :draftId")
    suspend fun resolveReply(draftId: String, gotReply: Boolean, latencyMs: Long?)
}

@Dao
interface VoiceProfileDao {

    @Upsert
    suspend fun upsert(profile: VoiceProfileEntity)

    @Query("SELECT * FROM voice_profile WHERE id = ${VoiceProfileEntity.SINGLETON}")
    fun observe(): Flow<VoiceProfileEntity?>

    @Query("SELECT * FROM voice_profile WHERE id = ${VoiceProfileEntity.SINGLETON}")
    suspend fun current(): VoiceProfileEntity?
}
