package com.example.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.SleepAppUsage
import com.example.data.SleepRepository
import com.example.data.SleepSession
import com.example.ui.UsageStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TimerState {
    IDLE, RUNNING, FINISHED
}

class SleepTimerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var timerJob: Job? = null

    private lateinit var repository: SleepRepository
    private var wakeLock: PowerManager.WakeLock? = null

    private var durationMinutes = 0
    private var killSwitchEnabled = false
    private var appsToKill = ArrayList<String>()
    
    private var startTimeMs = 0L
    private var originalVolume = -1

    companion object {
        const val CHANNEL_ID = "sleep_timer_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESET = "ACTION_RESET"
        
        const val EXTRA_DURATION_MINUTES = "EXTRA_DURATION_MINUTES"
        const val EXTRA_KILL_SWITCH = "EXTRA_KILL_SWITCH"
        const val EXTRA_INTERNET_OFF = "EXTRA_INTERNET_OFF"
        const val EXTRA_APPS_TO_KILL = "EXTRA_APPS_TO_KILL"

        private val _remainingSeconds = MutableStateFlow(0)
        val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

        private val _serviceState = MutableStateFlow(TimerState.IDLE)
        val serviceState: StateFlow<TimerState> = _serviceState.asStateFlow()

        private val _totalDurationSeconds = MutableStateFlow(1)
        val totalDurationSeconds: StateFlow<Int> = _totalDurationSeconds.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        repository = SleepRepository.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START) {
            val minutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 30)
            val killSwitch = intent.getBooleanExtra(EXTRA_KILL_SWITCH, false)
            val apps = intent.getStringArrayListExtra(EXTRA_APPS_TO_KILL) ?: ArrayList()

            startTimer(minutes, killSwitch, apps)
        } else if (action == ACTION_STOP) {
            stopTimer(userCancelled = true)
        } else if (action == ACTION_RESET) {
            _serviceState.value = TimerState.IDLE
        }
        return START_STICKY
    }

    private fun startTimer(
        minutes: Int,
        killSwitch: Boolean,
        apps: ArrayList<String>
    ) {
        durationMinutes = minutes
        killSwitchEnabled = killSwitch
        appsToKill = apps
        startTimeMs = System.currentTimeMillis()

        val seconds = minutes * 60
        _totalDurationSeconds.value = seconds
        _remainingSeconds.value = seconds
        _serviceState.value = TimerState.RUNNING

        acquireWakeLock()
        MediaNotificationListener.startNewTrackingSession()
        startForeground(NOTIFICATION_ID, buildNotification(seconds))

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var timeLeft = seconds
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
                _remainingSeconds.value = timeLeft
                updateNotification(timeLeft)
            }
            executeSleepActions()
        }
    }

    private fun stopTimer(userCancelled: Boolean) {
        timerJob?.cancel()
        timerJob = null
        
        val elapsedMinutes = if (startTimeMs > 0) {
            ((System.currentTimeMillis() - startTimeMs) / 60000).toInt()
        } else {
            0
        }

        if (userCancelled && _serviceState.value == TimerState.RUNNING) {
            _serviceState.value = TimerState.IDLE
            serviceScope.launch(Dispatchers.IO) {
                repository.insertSession(
                    SleepSession(
                        startTimeMs = startTimeMs,
                        durationMinutes = elapsedMinutes,
                        endedSuccessfully = false,
                        internetOffAttempted = false,
                        appsKilledCount = 0
                    )
                )
            }
        }

        _remainingSeconds.value = 0
        releaseWakeLock()
        stopForeground(true)
        stopSelf()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepTimer::WakeLock").apply {
            acquire(durationMinutes * 60 * 1000L + 10000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun updateNotification(secondsLeft: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(secondsLeft))
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    private fun buildNotification(secondsLeft: Int): Notification {
        val title = "Sleep Timer Aktif"
        val text = "Sisa waktu: ${formatTime(secondsLeft)}"

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SleepTimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // For Xiaomi/POCO: Use CATEGORY_ALARM and HIGH priority to force lockscreen visibility
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, android.R.drawable.ic_lock_idle_alarm))
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "BATALKAN", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(if (startTimeMs > 0) startTimeMs else System.currentTimeMillis())
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTicker(title)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setLocalOnly(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Timer Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi status sisa waktu sleep timer"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private suspend fun executeSleepActions() {
        _serviceState.value = TimerState.FINISHED
        val stopTimeMs = System.currentTimeMillis()
        
        // Capture ALL tracks that were playing during the session
        val mediaPlaybacks = MediaNotificationListener.getCapturedMediaList()
        val latestMedia = MediaNotificationListener.getLatestMediaInfo()

        // 1. Audio Fade-out before hard pause
        fadeOutAudio()

        // 2. Hijack Audio Focus to Pause Players (YouTube, Spotify, etc.)
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Dispatch Media Key Pause Events (Standard hardware simulations)
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Kill Background Processes for targeted apps (if opt-in)
        var killedCount = 0
        if (killSwitchEnabled && appsToKill.isNotEmpty()) {
            try {
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                for (packageName in appsToKill) {
                    activityManager.killBackgroundProcesses(packageName)
                    killedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 5. Restore volume if we changed it during fade out
        restoreVolume()

        // 6. Gather actual background usage stats for this sleep interval & store to Local DB
        try {
            val sleepSessionId = repository.insertSession(
                SleepSession(
                    startTimeMs = startTimeMs,
                    durationMinutes = durationMinutes,
                    endedSuccessfully = true,
                    internetOffAttempted = false,
                    appsKilledCount = killedCount,
                    lastMediaTitle = latestMedia.first,
                    lastMediaArtist = latestMedia.second
                )
            ).toInt()

            // Store the full playback list
            if (mediaPlaybacks.isNotEmpty()) {
                repository.insertMediaPlaybacks(mediaPlaybacks.map { 
                    com.example.data.SleepMediaPlayback(
                        sessionId = sleepSessionId,
                        title = it.title,
                        artist = it.artist,
                        timestamp = it.timestamp
                    )
                })
            }

            val usageStats = UsageStatsHelper.getSleepIntervalUsage(
                context = applicationContext,
                startTimeMs = startTimeMs,
                endTimeMs = stopTimeMs
            )

            if (usageStats.isNotEmpty()) {
                val usagesToStore = usageStats.map { detail ->
                    SleepAppUsage(
                        sessionId = sleepSessionId,
                        packageName = detail.packageName,
                        appName = detail.appName,
                        usageDurationMs = detail.usageDurationMs
                    )
                }
                repository.insertAppUsages(usagesToStore)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Turn off service
        _remainingSeconds.value = 0
        releaseWakeLock()
        stopForeground(true)
        stopSelf()
    }

    private suspend fun fadeOutAudio() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            var currentVol = originalVolume
            
            // Gradually reduce volume over 10 steps (approx 5-10 seconds total)
            while (currentVol > 0) {
                currentVol--
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVol, 0)
                delay(800) // 800ms between volume steps
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreVolume() {
        if (originalVolume != -1) {
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            originalVolume = -1
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
