package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.SleepAppUsage
import com.example.data.SleepRepository
import com.example.data.SleepSession
import com.example.service.BedtimeReminderWorker
import com.example.service.SleepTimerService
import com.example.service.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class LauncherAppInfo(
    val packageName: String,
    val appName: String
)

class SleepViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = SleepRepository.getInstance(context)

    // Timer configuration states
    private val _selectedDurationMinutes = MutableStateFlow(30)
    val selectedDurationMinutes: StateFlow<Int> = _selectedDurationMinutes.asStateFlow()

    private val _killSwitchEnabled = MutableStateFlow(true)
    val killSwitchEnabled: StateFlow<Boolean> = _killSwitchEnabled.asStateFlow()

    private val _selectedAppsToKill = MutableStateFlow<Set<String>>(emptySet())
    val selectedAppsToKill: StateFlow<Set<String>> = _selectedAppsToKill.asStateFlow()

    private val _bedtimeReminderEnabled = MutableStateFlow(false)
    val bedtimeReminderEnabled: StateFlow<Boolean> = _bedtimeReminderEnabled.asStateFlow()

    // Apps list querying
    private val _userInstalledApps = MutableStateFlow<List<LauncherAppInfo>>(emptyList())
    val userInstalledApps: StateFlow<List<LauncherAppInfo>> = _userInstalledApps.asStateFlow()

    // Usage lists
    private val _isUsagePermissionGranted = MutableStateFlow(false)
    @Suppress("StateFlowValueCalledInComposition")
    val isUsagePermissionGranted: StateFlow<Boolean> = _isUsagePermissionGranted.asStateFlow()

    private val _isNotificationListenerGranted = MutableStateFlow(false)
    val isNotificationListenerGranted: StateFlow<Boolean> = _isNotificationListenerGranted.asStateFlow()

    private val _weeklyUsageStatsFlow = MutableStateFlow<List<AppUsageDetail>>(emptyList())
    val weeklyUsageStatsFlow: StateFlow<List<AppUsageDetail>> = _weeklyUsageStatsFlow.asStateFlow()

    // Historical listings from Room (Numbered Paging)
    val pageSize = 5
    private val _pagedHistorySessions = MutableStateFlow<List<SleepSession>>(emptyList())
    val pagedHistorySessions: StateFlow<List<SleepSession>> = _pagedHistorySessions.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _totalSessionCount = MutableStateFlow(0)
    val totalSessionCount: StateFlow<Int> = _totalSessionCount.asStateFlow()

    private val _isLoadingPage = MutableStateFlow(false)
    val isLoadingPage: StateFlow<Boolean> = _isLoadingPage.asStateFlow()

    // Keep historySessions for backward-compatibility if needed
    val historySessions: StateFlow<List<SleepSession>> = _pagedHistorySessions.asStateFlow()

    private val _activeSessionDetailList = MutableStateFlow<List<SleepAppUsage>>(emptyList())
    val activeSessionDetailList: StateFlow<List<SleepAppUsage>> = _activeSessionDetailList.asStateFlow()

    private val _activeSessionPlaybackList = MutableStateFlow<List<com.example.data.SleepMediaPlayback>>(emptyList())
    val activeSessionPlaybackList: StateFlow<List<com.example.data.SleepMediaPlayback>> = _activeSessionPlaybackList.asStateFlow()

    private val _selectedSessionIdForDetail = MutableStateFlow<Int?>(null)
    val selectedSessionIdForDetail: StateFlow<Int?> = _selectedSessionIdForDetail.asStateFlow()

    // Observe service status
    val remainingSeconds: StateFlow<Int> = SleepTimerService.remainingSeconds
    val timerState: StateFlow<TimerState> = SleepTimerService.serviceState
    val totalDurationSeconds: StateFlow<Int> = SleepTimerService.totalDurationSeconds

    init {
        checkPermissions()
        loadInstalledLauncherApps()
        loadWeeklyStats()
        loadSettings()
        refreshHistoryPagination()
    }

    private fun loadSettings() {
        val sharedPrefs = context.getSharedPreferences("sleep_timer_prefs", Context.MODE_PRIVATE)
        _selectedDurationMinutes.value = sharedPrefs.getInt("last_duration_minutes", 30)
        _bedtimeReminderEnabled.value = sharedPrefs.getBoolean("bedtime_reminder_enabled", false)
        if (_bedtimeReminderEnabled.value) {
            scheduleBedtimeReminder()
        }
    }

    fun toggleBedtimeReminder(enabled: Boolean) {
        _bedtimeReminderEnabled.value = enabled
        val sharedPrefs = context.getSharedPreferences("sleep_timer_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("bedtime_reminder_enabled", enabled).apply()
        
        if (enabled) {
            scheduleBedtimeReminder()
        } else {
            cancelBedtimeReminder()
        }
    }

    private fun scheduleBedtimeReminder() {
        val constraints = Constraints.Builder()
            .build()

        val workRequest = PeriodicWorkRequestBuilder<BedtimeReminderWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "bedtime_reminder_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun cancelBedtimeReminder() {
        WorkManager.getInstance(context).cancelUniqueWork("bedtime_reminder_work")
    }

    fun setDuration(minutes: Int) {
        val coerced = minutes.coerceIn(1, 480)
        _selectedDurationMinutes.value = coerced
        val sharedPrefs = context.getSharedPreferences("sleep_timer_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("last_duration_minutes", coerced).apply()
    }

    fun toggleKillSwitch(enabled: Boolean) {
        _killSwitchEnabled.value = enabled
    }

    fun toggleAppSelection(packageName: String) {
        val current = _selectedAppsToKill.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _selectedAppsToKill.value = current
    }

    fun startSleepTimer() {
        val intent = Intent(context, SleepTimerService::class.java).apply {
            action = SleepTimerService.ACTION_START
            putExtra(SleepTimerService.EXTRA_DURATION_MINUTES, _selectedDurationMinutes.value)
            putExtra(SleepTimerService.EXTRA_KILL_SWITCH, _killSwitchEnabled.value)
            putStringArrayListExtra(
                SleepTimerService.EXTRA_APPS_TO_KILL,
                ArrayList(_selectedAppsToKill.value.toList())
            )
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopSleepTimer() {
        val intent = Intent(context, SleepTimerService::class.java).apply {
            action = SleepTimerService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun resetTimerState() {
        val intent = Intent(context, SleepTimerService::class.java).apply {
            action = SleepTimerService.ACTION_RESET
        }
        context.startService(intent)
    }

    fun checkPermissions() {
        _isUsagePermissionGranted.value = UsageStatsHelper.isUsagePermissionGranted(context)
        _isNotificationListenerGranted.value = isNotificationListenerEnabled()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val pkgName = context.packageName
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkgName) == true
    }

    fun refreshStats() {
        checkPermissions()
        loadWeeklyStats()
        refreshHistoryPagination()
    }

    fun refreshHistoryPagination() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingPage.value = true
            val totalCount = repository.getTotalSessionCount()
            _totalSessionCount.value = totalCount
            val pages = if (totalCount == 0) 1 else kotlin.math.ceil(totalCount.toDouble() / pageSize).toInt()
            _totalPages.value = pages

            val validPage = _currentPage.value.coerceIn(1, pages)
            _currentPage.value = validPage

            val offset = (validPage - 1) * pageSize
            val sessions = repository.getSessionsPaged(limit = pageSize, offset = offset)
            _pagedHistorySessions.value = sessions
            _isLoadingPage.value = false
        }
    }

    fun goToPage(page: Int) {
        val targetPage = page.coerceIn(1, kotlin.math.max(1, _totalPages.value))
        _currentPage.value = targetPage
        clearSessionSelection()

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingPage.value = true
            val offset = (targetPage - 1) * pageSize
            val sessions = repository.getSessionsPaged(limit = pageSize, offset = offset)
            _pagedHistorySessions.value = sessions
            _isLoadingPage.value = false
        }
    }

    private fun loadWeeklyStats() {
        viewModelScope.launch(Dispatchers.IO) {
            if (UsageStatsHelper.isUsagePermissionGranted(context)) {
                val stats = UsageStatsHelper.getWeeklyUsage(context)
                _weeklyUsageStatsFlow.value = stats
            }
        }
    }

    fun loadSessionUsageDetails(sessionId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedSessionIdForDetail.value = sessionId
            val details = repository.getAppUsageForSession(sessionId)
            val playbacks = repository.getMediaPlaybackForSession(sessionId)
            _activeSessionDetailList.value = details
            _activeSessionPlaybackList.value = playbacks
        }
    }

    fun clearSessionSelection() {
        _selectedSessionIdForDetail.value = null
        _activeSessionDetailList.value = emptyList()
        _activeSessionPlaybackList.value = emptyList()
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            _activeSessionDetailList.value = emptyList()
            _selectedSessionIdForDetail.value = null
            _pagedHistorySessions.value = emptyList()
            _totalSessionCount.value = 0
            _totalPages.value = 1
            _currentPage.value = 1
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSession(sessionId)
            if (_selectedSessionIdForDetail.value == sessionId) {
                _selectedSessionIdForDetail.value = null
                _activeSessionDetailList.value = emptyList()
                _activeSessionPlaybackList.value = emptyList()
            }
            refreshHistoryPagination()
        }
    }

    private fun loadInstalledLauncherApps() {
        viewModelScope.launch(Dispatchers.Default) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, 0)
                }
            } catch (e: Exception) {
                emptyList()
            }

            val apps = resolveInfos.mapNotNull { resolve ->
                val pkgName = resolve.activityInfo.packageName
                if (pkgName == context.packageName) null
                else {
                    LauncherAppInfo(
                        packageName = pkgName,
                        appName = resolve.loadLabel(pm).toString()
                    )
                }
            }.distinctBy { it.packageName }.sortedBy { it.appName }

            withContext(Dispatchers.Main) {
                _userInstalledApps.value = apps
                // Automatically pre-select classic media apps if they exist (e.g. youtube, spotify, netflix, youtube music)
                val targetSuffixes = listOf("youtube", "spotify", "music", "netflix", "chrome", "tiktok", "vlc", "mxtech")
                val preselected = apps.filter { app ->
                    val nameLower = app.packageName.lowercase()
                    targetSuffixes.any { s -> nameLower.contains(s) }
                }.map { it.packageName }.toSet()
                if (preselected.isNotEmpty() && _selectedAppsToKill.value.isEmpty()) {
                    _selectedAppsToKill.value = preselected
                }
            }
        }
    }
}
