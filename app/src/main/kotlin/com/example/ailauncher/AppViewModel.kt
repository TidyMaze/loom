package com.example.ailauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AppRepository(app)

    private val _apps = MutableLiveData<List<AppEntry>>()
    val apps: LiveData<List<AppEntry>> = _apps

    fun refresh() { viewModelScope.launch(Dispatchers.IO) { fetchAndPost() } }

    fun resetApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.resetApp(packageName)
            fetchAndPost()
        }
    }

    private fun fetchAndPost() {
        val all = if (MOCK) mockApps() else repository.getRankedApps()
        val maxScore = all.maxOfOrNull { it.score.coerceAtLeast(0f) }?.takeIf { it > 0f } ?: 1f
        _apps.postValue(all.map { e -> e.copy(rank = (e.score / maxScore).coerceIn(0f, 1f)) })
    }

    private fun mockApps(): List<AppEntry> {
        val now = System.currentTimeMillis()
        fun ago(ms: Long) = now - ms
        return listOf(
            AppEntry("com.google.android.apps.messaging", "Messages", score = 1.00f, launchCount = 55, lastLaunchedMillis = ago(45_000),        todayCount = 9,  dailyAvg = 3f),
            AppEntry("com.google.android.youtube",        "YouTube",  score = 0.88f, launchCount = 80, lastLaunchedMillis = ago(18 * 3600_000L),  todayCount = 0,  dailyAvg = 5f),
            AppEntry("com.android.chrome",                "Chrome",   score = 0.72f, launchCount = 38, lastLaunchedMillis = ago(12 * 60_000),     todayCount = 3,  dailyAvg = 4f),
            AppEntry("com.google.android.gm",             "Gmail",    score = 0.54f, launchCount = 29, lastLaunchedMillis = ago(28 * 60_000),     todayCount = 6,  dailyAvg = 2f),
            AppEntry("com.google.android.apps.maps",      "Maps",     score = 0.38f, launchCount = 20, lastLaunchedMillis = ago(90 * 60_000),     todayCount = 1,  dailyAvg = 1.5f),
            AppEntry("com.google.android.calendar",       "Calendar", score = 0.22f, launchCount = 12, lastLaunchedMillis = ago(28 * 3600_000L),  todayCount = 0,  dailyAvg = 2f),
            AppEntry("com.android.settings",              "Settings", score = 0.12f, launchCount = 7,  lastLaunchedMillis = ago(4 * 3600_000L),   todayCount = 1,  dailyAvg = 1.5f),
            AppEntry("com.google.android.apps.photos",    "Photos",   score = 0.05f, launchCount = 3,  lastLaunchedMillis = ago(6 * 86400_000L),  todayCount = 0,  dailyAvg = 1f),
            AppEntry("com.google.android.contacts",       "Contacts", score = 0.00f, launchCount = 0,  lastLaunchedMillis = null),
            AppEntry("com.google.android.deskclock",      "Clock",    score = 0.00f, launchCount = 0,  lastLaunchedMillis = null),
        )
    }

    companion object {
        private const val MOCK = false
    }

    fun recordLaunchAndGetIntent(packageName: String) = run {
        viewModelScope.launch(Dispatchers.IO) { repository.recordLaunch(packageName) }
        repository.getLaunchIntent(packageName)
    }
}
