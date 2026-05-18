package com.yrolland.loom

import java.time.Instant
import java.time.ZoneId

data class UsageEvent(
    val packageName: String,
    val timestampMillis: Long,
    val hour: Int,
    val dayOfWeek: Int,
    // Phase 1 context features — nullable for backward compatibility with old events
    val secsSinceResume: Int? = null,
    val audioActive: Boolean? = null,
    val audioDevice: String? = null,
    val charging: Boolean? = null,
    val notificationCount: Int? = null,
    // Phase 2 (v13 prep) — collected starting 2026-05-18, will become features once enough data
    val lastNotifPkg: String? = null,
    val secsSinceLastNotif: Int? = null,
    val wifiSsidHash: String? = null,
    val batteryPct: Int? = null,
    // Phase 3 additions (2026-05-18)
    val activityType: String? = null,
    val activityConfidence: Int? = null,
    val secsToNextEvent: Int? = null,
    val btDeviceHash: String? = null,
    val prevAppDwellSecs: Int? = null
) {
    companion object {
        operator fun invoke(
            packageName: String,
            timestampMillis: Long,
            ctx: LaunchContext.Capture? = null
        ): UsageEvent {
            val zdt = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
            return UsageEvent(
                packageName, timestampMillis, zdt.hour, zdt.dayOfWeek.value,
                ctx?.secsSinceResume, ctx?.audioActive, ctx?.audioDevice, ctx?.charging,
                ctx?.notificationCount,
                ctx?.lastNotifPkg, ctx?.secsSinceLastNotif,
                ctx?.wifiSsidHash, ctx?.batteryPct,
                ctx?.activityType, ctx?.activityConfidence,
                ctx?.secsToNextEvent, ctx?.btDeviceHash, ctx?.prevAppDwellSecs
            )
        }
    }
}
