package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

class SleepRepository private constructor(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        SleepDatabase::class.java,
        "sleep_timer_database"
    )
    .fallbackToDestructiveMigration()
    .build()

    private val dao = db.sleepDao()

    val allSessions: Flow<List<SleepSession>> = dao.getAllSessionsFlow()

    suspend fun getAllSessionsList(): List<SleepSession> {
        return dao.getAllSessions()
    }

    suspend fun getSessionsPaged(limit: Int, offset: Int): List<SleepSession> {
        return dao.getSessionsPaged(limit, offset)
    }

    suspend fun getTotalSessionCount(): Int {
        return dao.getTotalSessionCount()
    }

    suspend fun insertSession(session: SleepSession): Long {
        return dao.insertSession(session)
    }

    suspend fun getAppUsageForSession(sessionId: Int): List<SleepAppUsage> {
        return dao.getAppUsageForSession(sessionId)
    }

    suspend fun getMediaPlaybackForSession(sessionId: Int): List<SleepMediaPlayback> {
        return dao.getMediaPlaybackForSession(sessionId)
    }

    suspend fun insertAppUsages(usages: List<SleepAppUsage>) {
        dao.insertAppUsages(usages)
    }

    suspend fun insertMediaPlaybacks(playbacks: List<SleepMediaPlayback>) {
        dao.insertMediaPlaybacks(playbacks)
    }

    suspend fun clearHistory() {
        dao.clearAllData()
        dao.clearAllAppUsage()
        dao.clearAllMediaPlayback()
    }

    suspend fun deleteSession(sessionId: Int) {
        dao.deleteSessionById(sessionId)
        dao.deleteAppUsageBySessionId(sessionId)
        dao.deleteMediaPlaybackBySessionId(sessionId)
    }

    companion object {
        @Volatile
        private var INSTANCE: SleepRepository? = null

        fun getInstance(context: Context): SleepRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = SleepRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
