package com.yrolland.loom

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager

object LaunchContext {

    data class Capture(
        val secsSinceResume: Int,
        val audioActive: Boolean,
        val audioDevice: String,    // "speaker" | "wired" | "bt"
        val charging: Boolean,
        val notificationCount: Int  // active notifications for the target app at launch time
    )

    fun capture(context: Context, launcherResumeMs: Long, targetPackage: String = "", nowMs: Long = System.currentTimeMillis()): Capture {
        val app = context.applicationContext
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return Capture(
            secsSinceResume = ((nowMs - launcherResumeMs) / 1000).toInt().coerceIn(0, 3600),
            audioActive = am.isMusicActive,
            audioDevice = audioDevice(am),
            charging = bm.isCharging,
            notificationCount = NotificationCounts.getCount(targetPackage)
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
}
