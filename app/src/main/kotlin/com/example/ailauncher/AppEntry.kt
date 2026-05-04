package com.example.ailauncher

data class AppEntry(
    val packageName: String,
    val label: String,
    val score: Float,
    val launchCount: Int,
    val lastLaunchedMillis: Long?
)
