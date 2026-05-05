package com.example.ailauncher

import java.time.Instant
import java.time.ZoneId

data class UsageEvent(
    val packageName: String,
    val timestampMillis: Long,
    val hour: Int = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()).hour,
    val dayOfWeek: Int = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()).dayOfWeek.value // 1=Mon … 7=Sun; 0 = legacy
)
