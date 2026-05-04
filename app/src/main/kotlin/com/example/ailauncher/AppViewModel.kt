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
            _apps.postValue(repository.getRankedApps())
        }
    }

    fun recordLaunchAndGetIntent(packageName: String) = run {
        viewModelScope.launch(Dispatchers.IO) { repository.recordLaunch(packageName) }
        repository.getLaunchIntent(packageName)
    }
}
