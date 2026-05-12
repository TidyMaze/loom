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
    val charging: Boolean? = null
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
                ctx?.secsSinceResume, ctx?.audioActive, ctx?.audioDevice, ctx?.charging
            )
        }
    }
}
