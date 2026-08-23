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
    private const val DEDUP_WINDOW_MS = 120_000L  // 2min: collapses in-app Activity transitions
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

    fun clearSyncState(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
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
        val storedLastSync = prefs.getLong(KEY_LAST_SYNC, 0L)

        // Load existing store to dedupe.
        val existing = store.load() ?: return 0

        // Self-healing: If existing store is empty but lastSync was set (e.g. data reset occurred),
        // reset sync window to 90 days to recover system history.
        val effectiveLastSync = if (existing.isEmpty() && storedLastSync > 0L) 0L else storedLastSync
        val start = if (effectiveLastSync == 0L) now - FIRST_RUN_BACKFILL_MS else effectiveLastSync

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(start, now)
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
        // Other launchers / system home — these aren't real "app launches", they're
        // the user returning to home. Filtering them removes ~29% noise events and
        // unblocks scoring of real apps that were being displaced.
        val launcherPkgs = setOf(
            "com.google.android.apps.nexuslauncher",  // Pixel
            "com.android.launcher",
            "com.android.launcher3",
            "com.sec.android.app.launcher",            // Samsung
            "com.miui.home",                            // Xiaomi
            "com.huawei.android.launcher",              // Huawei
            "com.oneplus.launcher",                     // OnePlus
        )

        // One-time clean: filter launcher entries using stream-aware collapsing
        val collapsed = collapseEvents(existing, ownPkg, launcherPkgs)
        val purgedCount = existing.size - collapsed.size
        if (purgedCount > 0) {
            store.replaceAll(collapsed)
            Log.d("UsageStatsSync", "cleaned $purgedCount stale events (launcher + duplicates)")
        }

        // Pull raw events and process with stream-aware collapse
        val rawBatch = ArrayList<UsageEvent>()
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val pkg = ev.packageName ?: continue
            if (skipPrefixes.any { pkg == it || pkg.startsWith("$it.") }) continue
            rawBatch.add(UsageEvent(pkg, ev.timeStamp))
        }

        val lastStoredPkg = collapsed.lastOrNull()?.packageName
        val lastStoredMs = collapsed.lastOrNull()?.timestampMillis ?: 0L

        val batchToCollapse = ArrayList<UsageEvent>()
        if (lastStoredPkg != null) {
            batchToCollapse.add(UsageEvent(lastStoredPkg, lastStoredMs))
        }
        batchToCollapse.addAll(rawBatch)

        val collapsedBatch = collapseEvents(batchToCollapse, ownPkg, launcherPkgs)
        val toAdd = if (lastStoredPkg != null && collapsedBatch.isNotEmpty()) {
            collapsedBatch.drop(1)
        } else {
            collapsedBatch
        }

        if (toAdd.isNotEmpty()) {
            store.appendBulk(toAdd)
        }
        prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
        Log.d("UsageStatsSync", "synced ${toAdd.size} new events (window: ${(now - start) / 1000}s)")
        return toAdd.size
    }

    /**
     * Collapses in-app activity transitions while preserving legitimate app launches
     * even if opened in quick succession after returning to launcher/home.
     */
    fun collapseEvents(
        events: List<UsageEvent>,
        ownPackage: String? = null,
        launcherPackages: Set<String> = setOf(
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oneplus.launcher"
        )
    ): List<UsageEvent> {
        val sorted = events.sortedBy { it.timestampMillis }
        val result = ArrayList<UsageEvent>(sorted.size)
        var lastPkg: String? = null
        var lastMs: Long = 0L

        for (e in sorted) {
            val pkg = e.packageName
            val isLauncher = (ownPackage != null && pkg == ownPackage) || pkg in launcherPackages
            if (isLauncher) {
                // Return to home/launcher breaks any in-app activity transition stream
                lastPkg = null
                continue
            }

            if (pkg == lastPkg && e.timestampMillis - lastMs < DEDUP_WINDOW_MS) {
                // Rapid consecutive activity transition within same app without returning home
                continue
            }

            result.add(e)
            lastPkg = pkg
            lastMs = e.timestampMillis
        }
        return result
    }
}
