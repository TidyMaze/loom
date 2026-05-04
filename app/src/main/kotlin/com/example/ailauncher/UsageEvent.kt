package com.example.ailauncher

data class UsageEvent(
    val packageName: String,
    val timestampMillis: Long,
    val hour: Int = java.time.LocalTime.now().hour,
    val dayOfWeek: Int = java.time.LocalDate.now().dayOfWeek.value // 1=Mon … 7=Sun; 0 = legacy
)
