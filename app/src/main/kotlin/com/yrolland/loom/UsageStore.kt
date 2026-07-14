package com.yrolland.loom

import android.content.Context
import android.util.AtomicFile
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class UsageStore(context: Context) {

    private val file = File(context.filesDir, "usage_log.json")
    private val atomicFile = AtomicFile(file)

    fun record(packageName: String, ctx: LaunchContext.Capture? = null, nowMs: Long = System.currentTimeMillis()): Long {
        synchronized(lock) {
            val events = load() ?: return nowMs
            val mutableEvents = events.toMutableList()
            mutableEvents.add(UsageEvent(packageName, nowMs, ctx))
            val trimmed = if (mutableEvents.size > MAX_EVENTS) mutableEvents.takeLast(MAX_EVENTS) else mutableEvents
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
            val current = load() ?: return
            val merged = (current + newEvents).sortedBy { it.timestampMillis }
            val trimmed = if (merged.size > MAX_EVENTS) merged.takeLast(MAX_EVENTS) else merged
            saveEvents(trimmed)
        }
    }

    private fun saveEvents(events: List<UsageEvent>) {
        var fos: FileOutputStream? = null
        try {
            fos = atomicFile.startWrite()
            JsonWriter(OutputStreamWriter(fos, Charsets.UTF_8)).use { w ->
                w.beginArray()
                for (e in events) {
                    writeEvent(w, e)
                }
                w.endArray()
            }
            atomicFile.finishWrite(fos)
        } catch (e: Exception) {
            fos?.let { atomicFile.failWrite(it) }
            android.util.Log.e("UsageStore", "Failed to write usage events atomically", e)
        }
    }

    private fun writeEvent(w: JsonWriter, e: UsageEvent) {
        w.beginObject()
        w.name("packageName").value(e.packageName)
        w.name("timestampMillis").value(e.timestampMillis)
        w.name("hour").value(e.hour)
        w.name("dayOfWeek").value(e.dayOfWeek)
        e.secsSinceResume?.let { w.name("secsSinceResume").value(it.toLong()) }
        e.audioActive?.let { w.name("audioActive").value(it) }
        e.audioDevice?.let { w.name("audioDevice").value(it) }
        e.charging?.let { w.name("charging").value(it) }
        e.notificationCount?.let { w.name("notificationCount").value(it.toLong()) }
        e.lastNotifPkg?.let { w.name("lastNotifPkg").value(it) }
        e.secsSinceLastNotif?.let { w.name("secsSinceLastNotif").value(it.toLong()) }
        e.wifiSsidHash?.let { w.name("wifiSsidHash").value(it) }
        e.batteryPct?.let { w.name("batteryPct").value(it.toLong()) }
        e.activityType?.let { w.name("activityType").value(it) }
        e.activityConfidence?.let { w.name("activityConfidence").value(it.toLong()) }
        e.secsToNextEvent?.let { w.name("secsToNextEvent").value(it.toLong()) }
        e.btDeviceHash?.let { w.name("btDeviceHash").value(it) }
        e.prevAppDwellSecs?.let { w.name("prevAppDwellSecs").value(it.toLong()) }
        w.endObject()
    }

    companion object {
        const val MAX_EVENTS = 20000
        private val lock = Any()
    }

    fun deleteApp(packageName: String) {
        synchronized(lock) {
            val current = load() ?: return
            saveEvents(current.filter { it.packageName != packageName })
        }
    }

    fun clear() {
        synchronized(lock) {
            atomicFile.delete()
        }
    }

    fun load(): List<UsageEvent>? {
        synchronized(lock) {
            if (!file.exists() && !File(file.parent, file.name + ".bak").exists()) return emptyList()
            return try {
                val events = ArrayList<UsageEvent>()
                JsonReader(InputStreamReader(atomicFile.openRead(), Charsets.UTF_8)).use { r ->
                    r.beginArray()
                    while (r.hasNext()) {
                        events.add(readEvent(r))
                    }
                    r.endArray()
                }
                events
            } catch (e: Throwable) {
                android.util.Log.e("UsageStore", "Failed to load usage events", e)
                null
            }
        }
    }

    private fun readEvent(r: JsonReader): UsageEvent {
        var packageName = ""
        var timestampMillis = 0L
        var hour = 0
        var dayOfWeek = 0
        var secsSinceResume: Int? = null
        var audioActive: Boolean? = null
        var audioDevice: String? = null
        var charging: Boolean? = null
        var notificationCount: Int? = null
        var lastNotifPkg: String? = null
        var secsSinceLastNotif: Int? = null
        var wifiSsidHash: String? = null
        var batteryPct: Int? = null
        var activityType: String? = null
        var activityConfidence: Int? = null
        var secsToNextEvent: Int? = null
        var btDeviceHash: String? = null
        var prevAppDwellSecs: Int? = null

        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName()
            if (r.peek() == JsonToken.NULL) {
                r.nextNull()
                continue
            }
            when (name) {
                "packageName" -> packageName = r.nextString().intern()
                "timestampMillis" -> timestampMillis = r.nextLong()
                "hour" -> hour = r.nextInt()
                "dayOfWeek" -> dayOfWeek = r.nextInt()
                "secsSinceResume" -> secsSinceResume = r.nextInt()
                "audioActive" -> audioActive = r.nextBoolean()
                "audioDevice" -> audioDevice = r.nextString()
                "charging" -> charging = r.nextBoolean()
                "notificationCount" -> notificationCount = r.nextInt()
                "lastNotifPkg" -> lastNotifPkg = r.nextString().intern()
                "secsSinceLastNotif" -> secsSinceLastNotif = r.nextInt()
                "wifiSsidHash" -> wifiSsidHash = r.nextString()
                "batteryPct" -> batteryPct = r.nextInt()
                "activityType" -> activityType = r.nextString()
                "activityConfidence" -> activityConfidence = r.nextInt()
                "secsToNextEvent" -> secsToNextEvent = r.nextInt()
                "btDeviceHash" -> btDeviceHash = r.nextString()
                "prevAppDwellSecs" -> prevAppDwellSecs = r.nextInt()
                else -> r.skipValue()
            }
        }
        r.endObject()
        return UsageEvent(
            packageName, timestampMillis, hour, dayOfWeek,
            secsSinceResume, audioActive, audioDevice, charging,
            notificationCount, lastNotifPkg, secsSinceLastNotif,
            wifiSsidHash, batteryPct, activityType, activityConfidence,
            secsToNextEvent, btDeviceHash, prevAppDwellSecs
        )
    }
}
