package com.yrolland.loom

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class AppRepository(private val context: Context) {

    private val usageStore = UsageStore(context)
    private val pinStore = PinStore(context)
    private val hiddenStore = HiddenStore(context)

    fun recordLaunch(packageName: String) {
        usageStore.record(packageName)
    }

    fun setPinned(pkg: String, pinned: Boolean) = pinStore.setPinned(pkg, pinned)
    fun setHidden(pkg: String, hidden: Boolean) = hiddenStore.setHidden(pkg, hidden)
    fun pinnedPackages(): Set<String> = pinStore.all()
    fun hiddenPackages(): Set<String> = hiddenStore.all()
    fun clearUsage() = usageStore.clear()
    fun clearAll() { usageStore.clear(); pinStore.clear(); hiddenStore.clear() }

    fun getRankedApps(): List<AppEntry> {
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hidden = hiddenStore.all()
        val pinned = pinStore.all()
        val installedApps = pm.queryIntentActivities(launchIntent, PackageManager.GET_META_DATA)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it !in hidden }

        val events = usageStore.load()
        val rawScores = ScoreEngine.score(events)
        val totalScore = rawScores.values.filter { it > 0f }.sum().coerceAtLeast(1f)
        val stats = computeStats(events)

        return installedApps
            .mapIndexed { index, pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                val count = stats.countByPkg[pkg] ?: 0
                val raw = rawScores[pkg]
                AppEntry(
                    packageName = pkg,
                    label = label,
                    score = if (raw != null && raw > 0f) raw / totalScore else (-index.toFloat()),
                    launchCount = count,
                    lastLaunchedMillis = stats.lastByPkg[pkg],
                    todayCount = stats.todayByPkg[pkg] ?: 0,
                    dailyAvg = count / stats.spanDays,
                    isPinned = pkg in pinned
                )
            }
            .sortedWith(
                compareByDescending<AppEntry> { it.isPinned }
                    .thenByDescending { it.score }
                    .thenByDescending { it.lastLaunchedMillis }
                    .thenBy { it.label }
            )
    }

    /** Apps installed but currently hidden — for the Settings unhide list. */
    fun getHiddenApps(): List<AppEntry> {
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hidden = hiddenStore.all()
        return pm.queryIntentActivities(launchIntent, PackageManager.GET_META_DATA)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it in hidden }
            .map { pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                AppEntry(pkg, label, 0f, 0, null)
            }
            .sortedBy { it.label.lowercase() }
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
        val todayStart = System.currentTimeMillis() - 86_400_000L
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
