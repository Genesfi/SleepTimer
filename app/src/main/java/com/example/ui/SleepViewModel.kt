package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SleepAppUsage
import com.example.data.SleepRepository
import com.example.data.SleepSession
import com.example.service.SleepTimerService
import com.example.service.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Apps list querying
    private val _userInstalledApps = MutableStateFlow<List<LauncherAppInfo>>(emptyList())
    val userInstalledApps: StateFlow<List<LauncherAppInfo>> = _userInstalledApps.asStateFlow()

    // Usage lists
    private val _isUsagePermissionGranted = MutableStateFlow(false)
    @Suppress("StateFlowValueCalledInComposition")
    val isUsagePermissionGranted: StateFlow<Boolean> = _isUsagePermissionGranted.asStateFlow()

    private val _weeklyUsageStatsFlow = MutableStateFlow<List<AppUsageDetail>>(emptyList())
    val weeklyUsageStatsFlow: StateFlow<List<AppUsageDetail>> = _weeklyUsageStatsFlow.asStateFlow()

    // Historical listings from Room
    val historySessions: StateFlow<List<SleepSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeSessionDetailList = MutableStateFlow<List<SleepAppUsage>>(emptyList())
    val activeSessionDetailList: StateFlow<List<SleepAppUsage>> = _activeSessionDetailList.asStateFlow()

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
    }

    fun setDuration(minutes: Int) {
        _selectedDurationMinutes.value = minutes.coerceIn(1, 480)
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
    }

    fun refreshStats() {
        checkPermissions()
        loadWeeklyStats()
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
            _activeSessionDetailList.value = details
        }
    }

    fun clearSessionSelection() {
        _selectedSessionIdForDetail.value = null
        _activeSessionDetailList.value = emptyList()
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            _activeSessionDetailList.value = emptyList()
            _selectedSessionIdForDetail.value = null
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
