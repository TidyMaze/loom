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
        NotificationCounts.increment(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        NotificationCounts.decrement(sbn)
    }

    companion object { private const val TAG = "LoomNotif" }
}

/** Tracks active notification counts per package. Singleton. Thread-safe.
 *  Uses notification keys (unique per notification) so updates don't inflate counts.
 *  Also tracks last notification source for "Slack pinged → open Slack" pattern. */
object NotificationCounts {
    // key = sbn.key (unique per notification slot), value = packageName
    private val keys = ConcurrentHashMap<String, String>()
    @Volatile private var lastPkg: String? = null
    @Volatile private var lastMs: Long = 0L

    fun increment(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val isNew = keys.put(sbn.key, pkg) == null
        if (isNew) {
            lastPkg = pkg
            lastMs = System.currentTimeMillis()
        }
    }

    fun decrement(sbn: StatusBarNotification) {
        keys.remove(sbn.key)
    }

    fun replaceAll(active: Array<StatusBarNotification>) {
        keys.clear()
        for (sbn in active) {
            val pkg = sbn.packageName ?: continue
            keys[sbn.key] = pkg
        }
    }

    fun get(pkg: String): Int = keys.values.count { it == pkg }
    fun getCount(pkg: String): Int = get(pkg)
    fun snapshot(): Map<String, Int> = keys.values.groupingBy { it }.eachCount()
    fun totalCount(): Int = keys.size

    /** Returns (lastNotifPkg, lastNotifMs). Null pkg if no notification seen yet. */
    fun lastNotification(): Pair<String?, Long> = lastPkg to lastMs

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
