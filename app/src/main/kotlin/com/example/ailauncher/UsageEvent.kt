package com.example.ailauncher

data class UsageEvent(
    val packageName: String,
    val timestampMillis: Long,
    val hour: Int = java.time.LocalTime.now().hour
)
