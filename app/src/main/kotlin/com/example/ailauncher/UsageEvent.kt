package com.example.ailauncher

import java.time.Instant
import java.time.ZoneId

data class UsageEvent(
    val packageName: String,
    val timestampMillis: Long,
    val hour: Int,
    val dayOfWeek: Int
) {
    companion object {
        operator fun invoke(packageName: String, timestampMillis: Long): UsageEvent {
            val zdt = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
            return UsageEvent(packageName, timestampMillis, zdt.hour, zdt.dayOfWeek.value)
        }
    }
}
