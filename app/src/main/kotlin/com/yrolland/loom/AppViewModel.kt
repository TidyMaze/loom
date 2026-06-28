package com.yrolland.loom

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

    fun refresh(ctx: LaunchContext.Capture? = null) {
        viewModelScope.launch(Dispatchers.IO) { fetchAndPost(ctx) }
    }

    fun resetApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.resetApp(packageName)
            fetchAndPost(null)
        }
    }

    fun setHidden(packageName: String, hidden: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setHidden(packageName, hidden)
            fetchAndPost(null)
        }
    }

    fun clearUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearUsage()
            fetchAndPost(null)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
            fetchAndPost(null)
        }
    }

    private fun fetchAndPost(ctx: LaunchContext.Capture?) {
        val all = repository.getRankedApps(ctx)
        val maxScore = all.maxOfOrNull { it.score.coerceAtLeast(0f) }?.takeIf { it > 0f } ?: 1f
        _apps.postValue(all.map { e -> e.copy(rank = (e.score / maxScore).coerceIn(0f, 1f)) })
    }

    fun recordLaunchAndGetIntent(packageName: String, ctx: LaunchContext.Capture? = null) = run {
        viewModelScope.launch(Dispatchers.IO) { repository.recordLaunch(packageName, ctx) }
        repository.getLaunchIntent(packageName)
    }

    fun updateLastLaunchDwell() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLastLaunchDwell()
        }
    }
}
