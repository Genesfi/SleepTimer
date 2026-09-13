package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BedtimeReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "BedtimeReminderWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker triggered check...")
        BedtimeReminderHelper.maybeShowReminder(applicationContext)
        return Result.success()
    }
}
