package com.example.ailauncher

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class UsageStore(context: Context) {

    private val file = File(context.filesDir, "usage_log.json")
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<UsageEvent>>() {}.type

    fun record(packageName: String) {
        val events = load().toMutableList()
        events.add(UsageEvent(packageName = packageName, timestampMillis = System.currentTimeMillis()))
        val trimmed = if (events.size > MAX_EVENTS) events.takeLast(MAX_EVENTS) else events
        file.writeText(gson.toJson(trimmed))
    }

    companion object {
        const val MAX_EVENTS = 1000
    }

    fun load(): List<UsageEvent> {
        if (!file.exists()) return emptyList()
        return runCatching {
            gson.fromJson<List<UsageEvent>>(file.readText(), listType) ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
