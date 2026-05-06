package com.example.ailauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
        val resolveInfos = pm.queryIntentActivities(launchIntent, PackageManager.GET_META_DATA)

        val installedApps = resolveInfos.map { it.activityInfo.packageName }.distinct()

        val events = usageStore.load()
        val scores = ScoreEngine.score(
            events,
            currentHour = LocalTime.now().hour,
            currentDayOfWeek = LocalDate.now().dayOfWeek.value
        )
        val countByPkg = events.groupingBy { it.packageName }.eachCount()
        val lastByPkg = events.groupBy { it.packageName }.mapValues { (_, e) -> e.maxOf { it.timestampMillis } }

        val todayStartMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayByPkg = events.filter { it.timestampMillis >= todayStartMillis }
            .groupingBy { it.packageName }.eachCount()
        val spanDays = if (events.isEmpty()) 1f else {
            val oldest = Instant.ofEpochMilli(events.minOf { it.timestampMillis })
                .atZone(ZoneId.systemDefault()).toLocalDate()
            ChronoUnit.DAYS.between(oldest, LocalDate.now()).coerceAtLeast(1).toFloat()
        }

        return installedApps
            .mapIndexed { index, pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)

                val score = scores[pkg] ?: (-index.toFloat())
                AppEntry(
                    packageName = pkg,
                    label = label,
                    score = score,
                    launchCount = countByPkg[pkg] ?: 0,
                    lastLaunchedMillis = lastByPkg[pkg],
                    todayCount = todayByPkg[pkg] ?: 0,
                    dailyAvg = (countByPkg[pkg] ?: 0) / spanDays
                )
            }
            .sortedWith(compareByDescending<AppEntry> { it.score }.thenByDescending { it.lastLaunchedMillis }.thenBy { it.label })
    }

    fun resetApp(packageName: String) = usageStore.deleteApp(packageName)

    fun getLaunchIntent(packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)
}
