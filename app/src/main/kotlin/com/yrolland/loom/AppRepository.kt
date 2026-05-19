package com.yrolland.loom

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class AppRepository(private val context: Context) {

    private val usageStore = UsageStore(context)
    private val hiddenStore = HiddenStore(context)

    fun recordLaunch(packageName: String, ctx: LaunchContext.Capture? = null) {
        val enriched = ctx?.copy(prevAppDwellSecs = computePrevAppDwellSecs())
        usageStore.record(packageName, enriched ?: ctx)
    }

    /** Foreground duration of the most recently used non-Loom app in seconds.
     *  Queries UsageStatsManager for events in the last 30 min, computes RESUMED→PAUSED. */
    /** Foreground duration of the most recently used non-Loom app in seconds.
     *  Queries UsageStatsManager for events in the last 30 min, computes RESUMED→PAUSED.
     *  Returns null if UsageStats hasn't yet processed recent events (typical latency: 30s-2min). */
    private fun computePrevAppDwellSecs(): Int? = runCatching {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@runCatching null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 30 * 60_000L, now) ?: return@runCatching null
        val ev = UsageEvents.Event()
        val resumeTimes = HashMap<String, Long>()
        var lastDwellMs = 0L
        val ownPkg = context.packageName
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName ?: continue
            if (pkg == ownPkg) continue
            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumeTimes[pkg] = ev.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val res = resumeTimes.remove(pkg) ?: continue
                    lastDwellMs = ev.timeStamp - res
                }
            }
        }
        if (lastDwellMs > 0) (lastDwellMs / 1000).toInt().coerceIn(0, 86400) else null
    }.getOrNull()

    fun setHidden(pkg: String, hidden: Boolean) = hiddenStore.setHidden(pkg, hidden)
    fun hiddenPackages(): Set<String> = hiddenStore.all()
    fun clearUsage() = usageStore.clear()
    fun clearAll() { usageStore.clear(); hiddenStore.clear() }

    fun getRankedApps(currentCtx: LaunchContext.Capture? = null): List<AppEntry> {
        UsageStatsSync.sync(context, usageStore)
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hidden = hiddenStore.all()
        val installedApps = pm.queryIntentActivities(launchIntent, PackageManager.GET_META_DATA)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it !in hidden }

        val events = usageStore.load()
        val rawScores = ScoreEngine.score(events, currentCtx)
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
                    score = if (raw != null) raw / totalScore else (-index.toFloat()),
                    launchCount = count,
                    lastLaunchedMillis = stats.lastByPkg[pkg],
                    todayCount = stats.todayByPkg[pkg] ?: 0,
                    dailyAvg = count / stats.spanDays
                )
            }
            .sortedWith(
                compareByDescending<AppEntry> { it.score }
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
