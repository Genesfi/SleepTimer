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

    suspend fun insertSession(session: SleepSession): Long {
        return dao.insertSession(session)
    }

    suspend fun getAppUsageForSession(sessionId: Int): List<SleepAppUsage> {
        return dao.getAppUsageForSession(sessionId)
    }

    suspend fun insertAppUsages(usages: List<SleepAppUsage>) {
        dao.insertAppUsages(usages)
    }

    suspend fun clearHistory() {
        dao.clearAllData()
        dao.clearAllAppUsage()
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
