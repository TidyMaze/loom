package com.yrolland.loom

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class UsageStore(context: Context) {

    private val file = File(context.filesDir, "usage_log.json")
    private val atomicFile = AtomicFile(file)
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<UsageEvent>>() {}.type

    fun record(packageName: String, ctx: LaunchContext.Capture? = null, nowMs: Long = System.currentTimeMillis()): Long {
        synchronized(lock) {
            val events = load().toMutableList()
            events.add(UsageEvent(packageName, nowMs, ctx))
            val trimmed = if (events.size > MAX_EVENTS) events.takeLast(MAX_EVENTS) else events
            saveEvents(trimmed)
            return nowMs
        }
    }

    /** Replace the entire stored list (used by sync for one-shot cleanup like launcher purge). */
    fun replaceAll(events: List<UsageEvent>) {
        synchronized(lock) {
            val capped = if (events.size > MAX_EVENTS) events.takeLast(MAX_EVENTS) else events
            saveEvents(capped)
        }
    }

    /** Append a batch of pre-built events; merges chronologically and applies MAX_EVENTS cap. */
    fun appendBulk(newEvents: List<UsageEvent>) {
        if (newEvents.isEmpty()) return
        synchronized(lock) {
            val merged = (load() + newEvents).sortedBy { it.timestampMillis }
            val trimmed = if (merged.size > MAX_EVENTS) merged.takeLast(MAX_EVENTS) else merged
            saveEvents(trimmed)
        }
    }

    private fun saveEvents(events: List<UsageEvent>) {
        val jsonStr = gson.toJson(events)
        var fos: FileOutputStream? = null
        try {
            fos = atomicFile.startWrite()
            fos.write(jsonStr.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(fos)
        } catch (e: IOException) {
            fos?.let { atomicFile.failWrite(it) }
            android.util.Log.e("UsageStore", "Failed to write usage events atomically", e)
        }
    }

    companion object {
        const val MAX_EVENTS = 20000
        private val lock = Any()
    }

    fun deleteApp(packageName: String) {
        synchronized(lock) {
            saveEvents(load().filter { it.packageName != packageName })
        }
    }

    fun clear() {
        synchronized(lock) {
            atomicFile.delete()
        }
    }

    fun load(): List<UsageEvent> {
        synchronized(lock) {
            if (!file.exists() && !File(file.parent, file.name + ".bak").exists()) return emptyList()
            return runCatching {
                val bytes = atomicFile.readFully()
                gson.fromJson<List<UsageEvent>>(String(bytes, Charsets.UTF_8), listType) ?: emptyList()
            }.getOrElse { e ->
                android.util.Log.e("UsageStore", "Failed to load usage events, file might be corrupted", e)
                emptyList()
            }
        }
    }
}
