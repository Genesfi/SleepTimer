package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Device rebooted. Rescheduling Bedtime Reminder...")
            
            val sharedPrefs = context.getSharedPreferences("sleep_timer_prefs", Context.MODE_PRIVATE)
            val isEnabled = sharedPrefs.getBoolean("bedtime_reminder_enabled", false)
            
            if (isEnabled) {
                val workRequest = PeriodicWorkRequestBuilder<BedtimeReminderWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().build())
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "bedtime_reminder_work",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
            }
        }
    }
}
