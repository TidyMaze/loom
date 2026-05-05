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

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _apps.postValue(if (MOCK) mockApps() else repository.getRankedApps())
        }
    }

    private fun mockApps(): List<AppEntry> {
        val now = System.currentTimeMillis()
        fun ago(ms: Long) = now - ms
        return listOf(
            AppEntry("com.google.android.apps.messaging", "Messages",    score = 1.00f, launchCount = 42, lastLaunchedMillis = ago(30_000),        todayCount = 6,  dailyAvg = 3f),
            AppEntry("com.google.android.youtube",        "YouTube",     score = 0.91f, launchCount = 80, lastLaunchedMillis = ago(4 * 60_000),     todayCount = 0,  dailyAvg = 5f),
            AppEntry("com.android.chrome",                "Chrome",      score = 0.81f, launchCount = 38, lastLaunchedMillis = ago(8 * 60_000),     todayCount = 4,  dailyAvg = 4f),
            AppEntry("com.google.android.gm",             "Gmail",       score = 0.63f, launchCount = 29, lastLaunchedMillis = ago(22 * 60_000),    todayCount = 2,  dailyAvg = 4f),
            AppEntry("com.google.android.apps.maps",      "Maps",        score = 0.28f, launchCount = 14, lastLaunchedMillis = ago(3 * 3600_000L),  todayCount = 0,  dailyAvg = 2f),
            AppEntry("com.google.android.calendar",       "Calendar",    score = 0.16f, launchCount = 9,  lastLaunchedMillis = ago(24 * 3600_000L), todayCount = 0,  dailyAvg = 1f),
            AppEntry("com.android.settings",              "Settings",    score = 0.09f, launchCount = 5,  lastLaunchedMillis = ago(3 * 86400_000L), todayCount = 0,  dailyAvg = 1f),
            AppEntry("com.google.android.apps.photos",    "Photos",      score = 0.04f, launchCount = 3,  lastLaunchedMillis = ago(7 * 86400_000L), todayCount = 0,  dailyAvg = 1f),
            AppEntry("com.google.android.contacts",       "Contacts",    score = 0.00f, launchCount = 0,  lastLaunchedMillis = null,                todayCount = 0,  dailyAvg = 0f),
            AppEntry("com.google.android.deskclock",      "Clock",       score = 0.00f, launchCount = 0,  lastLaunchedMillis = null,                todayCount = 0,  dailyAvg = 0f),
        )
    }

    companion object {
        private const val MOCK = true
    }

    fun recordLaunchAndGetIntent(packageName: String) = run {
        viewModelScope.launch(Dispatchers.IO) { repository.recordLaunch(packageName) }
        repository.getLaunchIntent(packageName)
    }
}
