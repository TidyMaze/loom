package com.yrolland.loom

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * Pulls system-wide ACTIVITY_RESUMED events from UsageStatsManager and merges them
 * into the local UsageStore. Captures apps opened OUTSIDE the launcher (recents,
 * notifications, deep links, widgets, system back, etc).
 *
 * Requires PACKAGE_USAGE_STATS — special permission. User must enable via
 * Settings → Apps → Special access → Usage access.
 */
object UsageStatsSync {

    private const val PREFS = "loom_sync_prefs"
    private const val KEY_LAST_SYNC = "last_sync_ms"
    private const val DEDUP_WINDOW_MS = 3_000L
    private const val FIRST_RUN_BACKFILL_MS = 90L * 24 * 3_600_000 // 90 days

    fun hasPermission(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Sync new MOVE_TO_FOREGROUND events from system since last sync.
     * Skips own package, system UI, dedupes against recent existing events.
     * Returns count of events newly recorded.
     */
    fun sync(context: Context, store: UsageStore): Int {
        if (!hasPermission(context)) return 0

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
        val start = if (lastSync == 0L) now - FIRST_RUN_BACKFILL_MS else lastSync

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(start, now)

        // Load existing store to dedupe.
        val existing = store.load()
        // Index existing events by (pkg, second-bucket) for fast dedup.
        val recentByPkg = HashMap<String, ArrayList<Long>>()
        for (e in existing) {
            if (now - e.timestampMillis <= 24 * 3_600_000) {
                recentByPkg.getOrPut(e.packageName) { ArrayList() }.add(e.timestampMillis)
            }
        }

        val ownPkg = context.packageName
        val skipPrefixes = listOf(
            "com.android.systemui",
            "com.google.android.inputmethod",
            "com.google.android.permissioncontroller",
            "android"
        )

        val toAdd = ArrayList<UsageEvent>()
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val pkg = ev.packageName ?: continue
            if (pkg == ownPkg) continue
            if (skipPrefixes.any { pkg == it || pkg.startsWith("$it.") }) continue
            // Dedupe against recent events within DEDUP_WINDOW_MS
            val nearby = recentByPkg[pkg]
            if (nearby != null && nearby.any { kotlin.math.abs(it - ev.timeStamp) <= DEDUP_WINDOW_MS }) continue
            toAdd.add(UsageEvent(pkg, ev.timeStamp))
            // Update local dedup index so consecutive duplicates in this batch are also filtered
            recentByPkg.getOrPut(pkg) { ArrayList() }.add(ev.timeStamp)
        }

        if (toAdd.isNotEmpty()) {
            store.appendBulk(toAdd)
        }
        prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
        Log.d("UsageStatsSync", "synced ${toAdd.size} new events (window: ${(now - start) / 1000}s)")
        return toAdd.size
    }
}
