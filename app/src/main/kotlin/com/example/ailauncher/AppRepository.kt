package com.example.ailauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.LocalDate
import java.time.LocalTime

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
                    lastLaunchedMillis = lastByPkg[pkg]
                )
            }
            .sortedWith(compareByDescending<AppEntry> { it.score }.thenBy { it.label })
    }

    fun getLaunchIntent(packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)
}
