package com.yrolland.loom

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream

/**
 * Walk-forward replay of the real ScoreEngine.score over a usage log, scored like scripts/bench.py evaluate():
 * every package of the whole log is ranked by score (missing = 0.0, ties by package name), then hit@k and MRR.
 * Skipped unless LOOM_REPLAY_LOG points to a usage log JSON (the log is private, it never lives in the repo).
 *
 * LOOM_REPLAY_START (default 0) and LOOM_REPLAY_END (default: end of log) bound the walked targets, which
 * always start at MIN_HISTORY or later. Every target sees events[:i] of the full log, whatever the bounds.
 * LOOM_REPLAY_WINDOW_A_START (default 12490) and LOOM_REPLAY_SNAPSHOT_N (default 10472, the size of the
 * log the shipped constants were tuned on) add two summaries next to the summary over all walked targets.
 *
 * Gradle does not track environment variables: re-run with `--rerun` after changing them.
 */
class ScoreEngineReplayTest {

    @Test
    fun `replay usage log through ScoreEngine`() {
        val logPath = System.getenv("LOOM_REPLAY_LOG").orEmpty()
        assumeTrue("LOOM_REPLAY_LOG is not set, replay skipped", logPath.isNotBlank())

        val events = parseUsageLog(File(logPath).readText()).sortedBy { it.timestampMillis }
        val packages = events.map { it.packageName }.toSortedSet().toList()
        val first = maxOf(MIN_HISTORY, envInt("LOOM_REPLAY_START", 0))
        val end = minOf(envInt("LOOM_REPLAY_END", events.size), events.size)
        val windowAStart = envInt("LOOM_REPLAY_WINDOW_A_START", 12_490)
        val snapshotN = envInt("LOOM_REPLAY_SNAPSHOT_N", 10_472)
        require(first < end) { "no target to walk: first=$first end=$end events=${events.size}" }
        require(snapshotN in 1..events.size) { "LOOM_REPLAY_SNAPSHOT_N=$snapshotN outside 1..${events.size}" }

        val snapshotEnd = events[snapshotN - 1].timestampMillis
        val afterSnapshotStart = events.indexOfFirst { it.timestampMillis > snapshotEnd }

        println("REPLAY log=${File(logPath).name} events=${events.size} packages=${packages.size} walk=$first..$end")
        val startedAt = System.nanoTime()
        val ranks = rankTargets(events, packages, first, end)
        val seconds = (System.nanoTime() - startedAt) / 1e9

        println(summary("all-walked", first, ranks, first))
        println(summary("from-index", windowAStart, ranks, first))
        val boundary = "after-snapshot(n=$snapshotN,last=${Instant.ofEpochMilli(snapshotEnd)})"
        println(summary(boundary, if (afterSnapshotStart < 0) end else afterSnapshotStart, ranks, first))
        println(String.format(Locale.ROOT, "REPLAY wall=%.1fs targets=%d", seconds, ranks.size))
    }

    @Test
    fun `rank breaks score ties by package name and counts missing packages as zero`() {
        val packages = listOf("com.a", "com.b", "com.c", "com.d")
        val scores = mapOf("com.b" to 1f, "com.c" to 1f, "com.a" to 0.5f)

        assertEquals(1, rankOf("com.b", scores, packages))
        assertEquals(2, rankOf("com.c", scores, packages))
        assertEquals(3, rankOf("com.a", scores, packages))
        assertEquals(4, rankOf("com.d", scores, packages))
        assertEquals(4, rankOf("com.d", emptyMap(), listOf("com.a", "com.b", "com.c", "com.d")))
    }

    @Test
    fun `parses a flat usage log and rebuilds launch context only when audio state was stored`() {
        val json = """[
            {"packageName":"com.a","timestampMillis":1000,"hour":7,"dayOfWeek":2},
            {"packageName":"com.b","timestampMillis":2000,"hour":8,"dayOfWeek":3,"secsSinceResume":12,
             "audioActive":true,"audioDevice":"bt","charging":false,"notificationCount":4,
             "lastNotifPkg":null,"batteryPct":81,"prevAppDwellSecs":30}
        ]"""
        val events = parseUsageLog(json)

        assertEquals(listOf("com.a", "com.b"), events.map { it.packageName })
        assertEquals(2000L, events[1].timestampMillis)
        assertNull(events[0].audioActive)
        assertNull(launchContextOf(events[0]))

        val ctx = launchContextOf(events[1])
        assertNotNull(ctx)
        assertEquals(LaunchContext.Capture(12, true, "bt", false, 4, batteryPct = 81, prevAppDwellSecs = 30), ctx)
    }

    }

private const val MIN_HISTORY = 50
private val HIT_KS = intArrayOf(1, 3, 5, 10)

/**
 * Rank of the launched package among every package of the log, 1-based.
 * Same order as bench.py: score descending, missing = 0.0, equal scores by package name ascending.
 */
private fun rankOf(target: String, scores: Map<String, Float>, packages: List<String>): Int {
    val targetScore = scores[target] ?: 0f
    var ahead = 0
    for (pkg in packages) {
        if (pkg == target) continue
        val score = scores[pkg] ?: 0f
        if (score > targetScore || (score == targetScore && pkg < target)) ahead++
    }
    return ahead + 1
}

/**
 * Rank of each target i in [first, end), index i - first. Called like the app calls ScoreEngine.score
 * (AppRepository.getRankedApps), with the clock and the launch context taken from the target itself.
 * Per-package notification counts and app categories are empty: they are not in the log, and the app
 * reads them live from the notification listener and PackageManager.
 */
private fun rankTargets(events: List<UsageEvent>, packages: List<String>, first: Int, end: Int): IntArray {
    val ranks = IntArray(end - first)
    val done = AtomicInteger()
    IntStream.range(first, end).parallel().forEach { i ->
        val target = events[i]
        val scores = ScoreEngine.score(
            events.subList(0, i),
            currentCtx = launchContextOf(target),
            currentHour = target.hour,
            currentDayOfWeek = if (target.dayOfWeek == 0) 1 else target.dayOfWeek,
            nowMillis = target.timestampMillis,
            currentNotifCounts = emptyMap(),
            appCategories = emptyMap()
        )
        ranks[i - first] = rankOf(target.packageName, scores, packages)
        val count = done.incrementAndGet()
        if (count % 2_000 == 0) println("REPLAY progress $count/${ranks.size}")
    }
    return ranks
}

/** hit@k and MRR over the walked targets whose index is at least [from]; [ranks] starts at index [first]. */
private fun summary(name: String, from: Int, ranks: IntArray, first: Int): String {
    val begin = maxOf(from, first) - first
    val window = if (begin >= ranks.size) IntArray(0) else ranks.copyOfRange(begin, ranks.size)
    val label = "REPLAY window=$name from=${maxOf(from, first)} n=${window.size}"
    if (window.isEmpty()) return label
    val hits = HIT_KS.joinToString(" ") { k ->
        String.format(Locale.ROOT, "hit@%d=%.2f%%", k, 100.0 * window.count { it <= k } / window.size)
    }
    return String.format(Locale.ROOT, "%s %s mrr=%.4f", label, hits, window.sumOf { 1.0 / it } / window.size)
}

private fun envInt(name: String, default: Int): Int =
    System.getenv(name)?.let { it.toIntOrNull() ?: error("$name must be an integer, got '$it'") } ?: default

/** Same fields and defaults as UsageStore.readEvent; JSON null and a missing key both mean "not stored". */
private fun parseUsageLog(json: String): List<UsageEvent> =
    JsonParser.parseString(json).asJsonArray.map { element ->
        val o = element.asJsonObject
        UsageEvent(
            packageName = o.string("packageName") ?: "",
            timestampMillis = o.long("timestampMillis") ?: 0L,
            hour = o.int("hour") ?: 0,
            dayOfWeek = o.int("dayOfWeek") ?: 0,
            secsSinceResume = o.int("secsSinceResume"),
            audioActive = o.bool("audioActive"),
            audioDevice = o.string("audioDevice"),
            charging = o.bool("charging"),
            notificationCount = o.int("notificationCount"),
            lastNotifPkg = o.string("lastNotifPkg"),
            secsSinceLastNotif = o.int("secsSinceLastNotif"),
            wifiSsidHash = o.string("wifiSsidHash"),
            batteryPct = o.int("batteryPct"),
            activityType = o.string("activityType"),
            activityConfidence = o.int("activityConfidence"),
            secsToNextEvent = o.int("secsToNextEvent"),
            btDeviceHash = o.string("btDeviceHash"),
            prevAppDwellSecs = o.int("prevAppDwellSecs")
        )
    }

private fun JsonObject.present(name: String) = get(name)?.takeIf { !it.isJsonNull }
private fun JsonObject.string(name: String) = present(name)?.asString
private fun JsonObject.int(name: String) = present(name)?.asInt
private fun JsonObject.long(name: String) = present(name)?.asLong
private fun JsonObject.bool(name: String) = present(name)?.asBoolean

/**
 * The launch context the app would have captured for this launch, from the fields stored on the event.
 * Null when the event has no audio state (old events): the app then has no capture and ScoreEngine
 * falls back to the latest history event that has one.
 */
private fun launchContextOf(event: UsageEvent): LaunchContext.Capture? {
    val audioActive = event.audioActive ?: return null
    return LaunchContext.Capture(
        secsSinceResume = event.secsSinceResume ?: 0,
        audioActive = audioActive,
        audioDevice = event.audioDevice ?: "speaker",
        charging = event.charging ?: false,
        notificationCount = event.notificationCount ?: 0,
        lastNotifPkg = event.lastNotifPkg,
        secsSinceLastNotif = event.secsSinceLastNotif,
        wifiSsidHash = event.wifiSsidHash,
        batteryPct = event.batteryPct,
        activityType = event.activityType,
        activityConfidence = event.activityConfidence,
        secsToNextEvent = event.secsToNextEvent,
        btDeviceHash = event.btDeviceHash,
        prevAppDwellSecs = event.prevAppDwellSecs
    )
}
