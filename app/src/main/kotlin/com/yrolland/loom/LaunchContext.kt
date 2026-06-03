package com.yrolland.loom

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.security.MessageDigest

object LaunchContext {

    data class Capture(
        val secsSinceResume: Int,
        val audioActive: Boolean,
        val audioDevice: String,    // "speaker" | "wired" | "bt"
        val charging: Boolean,
        val notificationCount: Int,
        val lastNotifPkg: String? = null,
        val secsSinceLastNotif: Int? = null,
        val wifiSsidHash: String? = null,
        val batteryPct: Int? = null,
        // Phase 3 additions (2026-05-18)
        val activityType: String? = null,        // still | walking | running | on_bicycle | in_vehicle | on_foot | tilting
        val activityConfidence: Int? = null,     // 0-100
        val secsToNextEvent: Int? = null,        // seconds until next calendar event (capped 24h)
        val btDeviceHash: String? = null,        // 8-hex hash of connected BT audio device name
        val prevAppDwellSecs: Int? = null        // foreground duration of previous app in seconds
    )

    fun capture(context: Context, launcherResumeMs: Long, targetPackage: String = "", nowMs: Long = System.currentTimeMillis()): Capture {
        val app = context.applicationContext
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val (lastPkg, lastMs) = NotificationCounts.lastNotification()
        val secsSinceLast = if (lastPkg != null && lastMs > 0) {
            ((nowMs - lastMs) / 1000).toInt().coerceIn(0, 86400)
        } else null
        return Capture(
            secsSinceResume = ((nowMs - launcherResumeMs) / 1000).toInt().coerceIn(0, 3600),
            audioActive = am.isMusicActive,
            audioDevice = audioDevice(am),
            charging = bm.isCharging,
            notificationCount = NotificationCounts.getCount(targetPackage),
            lastNotifPkg = lastPkg,
            secsSinceLastNotif = secsSinceLast,
            wifiSsidHash = wifiSsidHash(app),
            batteryPct = batteryPct(bm),
            activityType = ActivityState.lastActivity.takeIf { (ActivityState.secsSinceUpdate(nowMs) ?: Int.MAX_VALUE) < 1200 },
            activityConfidence = ActivityState.lastConfidence.takeIf { it >= 0 && (ActivityState.secsSinceUpdate(nowMs) ?: Int.MAX_VALUE) < 1200 },
            secsToNextEvent = secsToNextCalendarEvent(app, nowMs),
            btDeviceHash = bluetoothDeviceHash(app),
            prevAppDwellSecs = null  // set by AppRepository — needs UsageStats query
        )
    }

    private fun audioDevice(am: AudioManager): String {
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (d in devices) {
            when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> return "bt"
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET -> return "wired"
            }
        }
        return "speaker"
    }

    private fun batteryPct(bm: BatteryManager): Int? = runCatching {
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    }.getOrNull()

    private fun wifiSsidHash(context: Context): String? = runCatching {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return@runCatching null
        @Suppress("DEPRECATION")
        val info = wm.connectionInfo ?: return@runCatching "none"
        val ssid = info.ssid?.trim('"') ?: return@runCatching "none"
        if (ssid.isEmpty() || ssid == "<unknown ssid>") return@runCatching "none"
        hash8(ssid)
    }.getOrNull()

    private fun bluetoothDeviceHash(context: Context): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return@runCatching null
        val btm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return@runCatching null
        // A2DP = BT audio sink (headphones, speakers); only returns actually-connected devices
        val device = btm.getConnectedDevices(BluetoothProfile.A2DP).firstOrNull() ?: return@runCatching "none"
        hash8(device.name ?: return@runCatching "none")
    }.getOrNull()

    private fun secsToNextCalendarEvent(context: Context, nowMs: Long): Int? = runCatching {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return@runCatching null
        val endMs = nowMs + 24L * 3600_000  // look ahead 24h
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, nowMs)
        ContentUris.appendId(builder, endMs)
        val cursor = context.contentResolver.query(
            builder.build(),
            arrayOf(CalendarContract.Instances.BEGIN),
            null, null,
            "${CalendarContract.Instances.BEGIN} ASC"
        ) ?: return@runCatching null
        cursor.use {
            if (!it.moveToFirst()) return@runCatching null
            val begin = it.getLong(0)
            ((begin - nowMs) / 1000).toInt().coerceIn(0, 86400)
        }
    }.getOrNull()

    private fun hash8(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.take(4).joinToString("") { "%02x".format(it) }
    }
}
