package com.example.ailauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        val scores = ScoreEngine.score(events, currentHour = LocalTime.now().hour)

        return installedApps
            .mapIndexed { index, pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)

                val score = scores[pkg] ?: (-index.toFloat())
                AppEntry(packageName = pkg, label = label, score = score)
            }
            .sortedByDescending { it.score }
    }

    fun getLaunchIntent(packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)
}
