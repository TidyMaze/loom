package com.example.ailauncher

data class AppEntry(
    val packageName: String,
    val label: String,
    val score: Float,
    val launchCount: Int,
    val lastLaunchedMillis: Long?,
    val todayCount: Int,
    val dailyAvg: Float,
    val rank: Float = 1f  // 1.0 = top, 0.0 = last, set by ViewModel
)
