package com.yrolland.loom

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens for active notifications system-wide. Exposes per-package count via NotificationCounts.
 *
 * Requires the user to grant "Notification access" in Settings (special permission, no runtime
 * request available). Deeplink: Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS.
 */
class LoomNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Hydrate counts from currently active notifications.
        runCatching { activeNotifications?.let { NotificationCounts.replaceAll(it) } }
        Log.d(TAG, "listener connected, ${NotificationCounts.totalCount()} active notifications")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        NotificationCounts.increment(sbn.packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        NotificationCounts.decrement(sbn.packageName)
    }

    companion object { private const val TAG = "LoomNotif" }
}

/** Tracks active notification counts per package. Singleton. Thread-safe. */
object NotificationCounts {
    private val counts = ConcurrentHashMap<String, Int>()

    fun increment(pkg: String) { counts.merge(pkg, 1, Int::plus) }

    fun decrement(pkg: String) {
        counts.compute(pkg) { _, c -> if (c == null || c <= 1) null else c - 1 }
    }

    fun replaceAll(active: Array<StatusBarNotification>) {
        counts.clear()
        for (sbn in active) {
            val pkg = sbn.packageName ?: continue
            counts.merge(pkg, 1, Int::plus)
        }
    }

    fun get(pkg: String): Int = counts[pkg] ?: 0
    fun snapshot(): Map<String, Int> = HashMap(counts)
    fun totalCount(): Int = counts.values.sum()

    fun hasPermission(context: Context): Boolean {
        val cn = ComponentName(context, LoomNotificationListener::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabled?.contains(cn.flattenToString()) == true
    }

    fun openPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, LoomNotificationListener::class.java).flattenToString()
                )
        } else {
            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
