package com.example.ailauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class AppRepository(private val context: Context) {

    private val usageStore = UsageStore(context)

    fun recordLaunch(packageName: String) {
        usageStore.record(packageName)
    }

    fun getRankedApps(): List<AppEntry> {
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installedApps = pm.queryIntentActivities(launchIntent, PackageManager.GET_META_DATA)
            .map { it.activityInfo.packageName }.distinct()

        val events = usageStore.load()
        val scores = ScoreEngine.score(events)
        val stats = computeStats(events)

        return installedApps
            .mapIndexed { index, pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                val count = stats.countByPkg[pkg] ?: 0
                AppEntry(
                    packageName = pkg,
                    label = label,
                    score = scores[pkg] ?: (-index.toFloat()),
                    launchCount = count,
                    lastLaunchedMillis = stats.lastByPkg[pkg],
                    todayCount = stats.todayByPkg[pkg] ?: 0,
                    dailyAvg = count / stats.spanDays
                )
            }
            .sortedWith(compareByDescending<AppEntry> { it.score }.thenByDescending { it.lastLaunchedMillis }.thenBy { it.label })
    }

    private data class UsageStats(
        val countByPkg: Map<String, Int>,
        val lastByPkg: Map<String, Long>,
        val todayByPkg: Map<String, Int>,
        val spanDays: Float
    )

    private fun computeStats(events: List<UsageEvent>): UsageStats {
        val countByPkg = events.groupingBy { it.packageName }.eachCount()
        val lastByPkg = events.groupBy { it.packageName }.mapValues { (_, e) -> e.maxOf { it.timestampMillis } }
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayByPkg = events.filter { it.timestampMillis >= todayStart }
            .groupingBy { it.packageName }.eachCount()
        val spanDays = if (events.isEmpty()) 1f else {
            val oldest = Instant.ofEpochMilli(events.minOf { it.timestampMillis })
                .atZone(ZoneId.systemDefault()).toLocalDate()
            ChronoUnit.DAYS.between(oldest, LocalDate.now()).coerceAtLeast(1).toFloat()
        }
        return UsageStats(countByPkg, lastByPkg, todayByPkg, spanDays)
    }

    fun resetApp(packageName: String) = usageStore.deleteApp(packageName)

    fun getLaunchIntent(packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)
}
