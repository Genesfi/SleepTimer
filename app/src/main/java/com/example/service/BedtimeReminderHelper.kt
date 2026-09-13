package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.ui.BedtimeReminderActivity
import java.util.Calendar

object BedtimeReminderHelper {
    private const val TAG = "BedtimeReminderHelper"

    fun maybeShowReminder(context: Context) {
        val sharedPrefs = context.getSharedPreferences("sleep_timer_prefs", Context.MODE_PRIVATE)
        val isEnabled = sharedPrefs.getBoolean("bedtime_reminder_enabled", false)
        
        if (!isEnabled) {
            Log.d(TAG, "Reminder disabled in settings.")
            return
        }

        // Check if timer is already running
        if (SleepTimerService.serviceState.value == TimerState.RUNNING) {
            Log.d(TAG, "Timer is already running, skipping reminder.")
            return
        }

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        // Bedtime range (9 PM to 2 AM)
        val isCorrectTime = hour >= 21 || hour < 2 
        
        if (!isCorrectTime) {
            Log.d(TAG, "Not bedtime (current hour: $hour).")
            return
        }

        // Check if already notified today
        val today = calendar.get(Calendar.DAY_OF_YEAR)
        val lastNotifiedDay = sharedPrefs.getInt("last_notified_day", -1)
        if (today == lastNotifiedDay) {
            Log.d(TAG, "Already notified today.")
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val isMusicActive = audioManager.isMusicActive
        val isNotificationMediaActive = MediaNotificationListener.isAnyMediaActive()
        
        // Also check if any other audio is playing (not just music stream)
        val isAnyAudioPlaying = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.activePlaybackConfigurations.isNotEmpty()
        } else {
            false
        }
        
        val isAudioPlaying = isMusicActive || isNotificationMediaActive || isAnyAudioPlaying
        Log.d(TAG, "Audio playing check: MusicActive=$isMusicActive, NotifMediaActive=$isNotificationMediaActive, ActiveConfigs=$isAnyAudioPlaying")

        if (isAudioPlaying) {
            showReminderNotification(context)
            sharedPrefs.edit().putInt("last_notified_day", today).apply()
            Log.d(TAG, "Bedtime reminder notification triggered.")
        }
    }

    private fun showReminderNotification(context: Context) {
        val channelId = "bedtime_reminder_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Bedtime Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mengingatkan Anda untuk memasang sleep timer saat memutar musik di malam hari."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Baca durasi timer terakhir dari SharedPreferences
        val sharedPrefs = context.getSharedPreferences("sleep_timer_prefs", Context.MODE_PRIVATE)
        val lastDuration = sharedPrefs.getInt("last_duration_minutes", 30)

        // Intent untuk langsung memulai Sleep Timer dari notifikasi
        val startTimerIntent = Intent(context, SleepTimerService::class.java).apply {
            action = SleepTimerService.ACTION_START
            putExtra(SleepTimerService.EXTRA_DURATION_MINUTES, lastDuration)
            putExtra(SleepTimerService.EXTRA_KILL_SWITCH, true)
        }
        val startTimerPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context, 201, startTimerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context, 201, startTimerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 203, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Mau pasang Sleep Timer?")
            .setContentText("Lagi dengerin sesuatu ya? Pasang timer $lastDuration mnt biar tidur makin nyenyak!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "MULAI ($lastDuration MNT)", startTimerPendingIntent)
            .addAction(android.R.drawable.ic_menu_agenda, "BUKA APLIKASI", openAppPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(202, notification)
    }
}
