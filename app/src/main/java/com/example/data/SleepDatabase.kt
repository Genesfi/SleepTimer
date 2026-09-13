package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sleep_sessions")
data class SleepSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTimeMs: Long,
    val durationMinutes: Int,
    val endedSuccessfully: Boolean,
    val internetOffAttempted: Boolean,
    val appsKilledCount: Int,
    val lastMediaTitle: String? = null,
    val lastMediaArtist: String? = null
)

@Entity(tableName = "sleep_app_usage")
data class SleepAppUsage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val packageName: String,
    val appName: String,
    val usageDurationMs: Long
)

@Entity(tableName = "sleep_media_playback")
data class SleepMediaPlayback(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val title: String,
    val artist: String?,
    val timestamp: Long
)

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_sessions ORDER BY startTimeMs DESC")
    fun getAllSessionsFlow(): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_sessions ORDER BY startTimeMs DESC")
    suspend fun getAllSessions(): List<SleepSession>

    @Query("SELECT * FROM sleep_sessions ORDER BY startTimeMs DESC LIMIT :limit OFFSET :offset")
    suspend fun getSessionsPaged(limit: Int, offset: Int): List<SleepSession>

    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun getTotalSessionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SleepSession): Long

    @Query("SELECT * FROM sleep_app_usage WHERE sessionId = :sessionId ORDER BY usageDurationMs DESC")
    suspend fun getAppUsageForSession(sessionId: Int): List<SleepAppUsage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsages(usages: List<SleepAppUsage>)

    @Query("SELECT * FROM sleep_media_playback WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMediaPlaybackForSession(sessionId: Int): List<SleepMediaPlayback>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaPlaybacks(playbacks: List<SleepMediaPlayback>)

    @Query("DELETE FROM sleep_sessions")
    suspend fun clearAllData()
    
    @Query("DELETE FROM sleep_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Int)

    @Query("DELETE FROM sleep_app_usage")
    suspend fun clearAllAppUsage()

    @Query("DELETE FROM sleep_app_usage WHERE sessionId = :sessionId")
    suspend fun deleteAppUsageBySessionId(sessionId: Int)

    @Query("DELETE FROM sleep_media_playback")
    suspend fun clearAllMediaPlayback()

    @Query("DELETE FROM sleep_media_playback WHERE sessionId = :sessionId")
    suspend fun deleteMediaPlaybackBySessionId(sessionId: Int)
}

@Database(entities = [SleepSession::class, SleepAppUsage::class, SleepMediaPlayback::class], version = 3, exportSchema = false)
abstract class SleepDatabase : RoomDatabase() {
    abstract fun sleepDao(): SleepDao
}
