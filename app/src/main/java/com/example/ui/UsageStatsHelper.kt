package com.example.ui

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import java.util.Calendar

data class AppUsageDetail(
    val packageName: String,
    val appName: String,
    val usageDurationMs: Long
)

object UsageStatsHelper {

    fun isUsagePermissionGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getAppName(context: Context, packageName: String): String {
        val pm = context.packageManager
        return try {
            val appInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // Extrapolate name from package if name is not accessible
            packageName.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: packageName
        }
    }

    fun getWeeklyUsage(context: Context): List<AppUsageDetail> {
        if (!isUsagePermissionGranted(context)) return emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startTime = calendar.timeInMillis

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_WEEKLY,
            startTime,
            endTime
        ) ?: return emptyList()

        // Group by package and sum foreground time
        return stats.groupBy { it.packageName }
            .mapValues { (_, value) -> value.sumOf { it.totalTimeInForeground } }
            .filter { it.value > 0 && !isSystemOrSelfPackage(it.key, context) }
            .map { (packageName, timeMs) ->
                AppUsageDetail(
                    packageName = packageName,
                    appName = getAppName(context, packageName),
                    usageDurationMs = timeMs
                )
            }
            .sortedByDescending { it.usageDurationMs }
            .take(15) // Top 15 apps
    }

    fun getSleepIntervalUsage(
        context: Context,
        startTimeMs: Long,
        endTimeMs: Long
    ): List<AppUsageDetail> {
        if (!isUsagePermissionGranted(context)) return emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTimeMs,
            endTimeMs
        ) ?: return emptyList()

        return stats.groupBy { it.packageName }
            .mapValues { (_, value) -> value.sumOf { it.totalTimeInForeground } }
            .filter { it.value > 0 && !isSystemOrSelfPackage(it.key, context) }
            .map { (packageName, timeMs) ->
                AppUsageDetail(
                    packageName = packageName,
                    appName = getAppName(context, packageName),
                    usageDurationMs = timeMs
                )
            }
            .sortedByDescending { it.usageDurationMs }
            .take(8)
    }

    private fun isSystemOrSelfPackage(packageName: String, context: Context): Boolean {
        if (packageName == context.packageName) return true
        val systemPackages = listOf(
            "com.android.launcher3", "com.google.android.apps.nexuslauncher",
            "com.android.systemui", "com.google.android.googlequicksearchbox",
            "android", "com.google.android.inputmethod.latin"
        )
        return systemPackages.contains(packageName)
    }
}
