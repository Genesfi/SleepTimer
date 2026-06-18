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

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_sessions ORDER BY startTimeMs DESC")
    fun getAllSessionsFlow(): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_sessions ORDER BY startTimeMs DESC")
    suspend fun getAllSessions(): List<SleepSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SleepSession): Long

    @Query("SELECT * FROM sleep_app_usage WHERE sessionId = :sessionId ORDER BY usageDurationMs DESC")
    suspend fun getAppUsageForSession(sessionId: Int): List<SleepAppUsage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsages(usages: List<SleepAppUsage>)

    @Query("DELETE FROM sleep_sessions")
    suspend fun clearAllData()
    
    @Query("DELETE FROM sleep_app_usage")
    suspend fun clearAllAppUsage()
}

@Database(entities = [SleepSession::class, SleepAppUsage::class], version = 2, exportSchema = false)
abstract class SleepDatabase : RoomDatabase() {
    abstract fun sleepDao(): SleepDao
}
