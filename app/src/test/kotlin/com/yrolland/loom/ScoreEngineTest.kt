package com.yrolland.loom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class ScoreEngineTest {

    // Use a fixed constant for "now" to ensure deterministic tests
    private val FIXED_NOW = 1714932000000L // Sunday, May 5, 2024 18:00:00 UTC
    private val thirtyDaysAgo = FIXED_NOW - TimeUnit.DAYS.toMillis(30)
    private val oneHourAgo = FIXED_NOW - TimeUnit.HOURS.toMillis(1)

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private fun event(pkg: String, timestampMillis: Long = FIXED_NOW) =
        UsageEvent(packageName = pkg, timestampMillis = timestampMillis)

    @Test
    fun `app launched at current hour scores higher than app launched at different hour`() {
        val hour9Timestamp = 1714899600000L // May 5, 2024 09:00:00 UTC
        val hour4Timestamp = 1714881600000L // May 5, 2024 04:00:00 UTC
        
        val events = listOf(
            event("com.maps", timestampMillis = hour9Timestamp),
            event("com.music", timestampMillis = hour4Timestamp)
        )
        // Use hour9Timestamp as "now"
        val scores = ScoreEngine.score(events, currentHour = 9, currentDayOfWeek = 7, nowMillis = hour9Timestamp)

        assertTrue("Maps (current hour) should score higher than Music", scores["com.maps"]!! > scores["com.music"]!!)
    }

    @Test
    fun `recent events score higher than old events`() {
        val events = listOf(
            event("com.recent", timestampMillis = FIXED_NOW),
            event("com.old", timestampMillis = thirtyDaysAgo)
        )
        val scores = ScoreEngine.score(events, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)

        assertTrue("Recent event should score higher than 30 days old", scores["com.recent"]!! > scores["com.old"]!!)
    }

    @Test
    fun `app with more launches outranks app with single launch`() {
        // Last event is a third app so neither heavy/light is hit by the in-session self-penalty.
        val events = listOf(
            event("com.heavy", timestampMillis = FIXED_NOW - TimeUnit.HOURS.toMillis(2)),
            event("com.heavy", timestampMillis = FIXED_NOW - TimeUnit.HOURS.toMillis(3)),
            event("com.heavy", timestampMillis = FIXED_NOW - TimeUnit.HOURS.toMillis(4)),
            event("com.light", timestampMillis = FIXED_NOW - TimeUnit.HOURS.toMillis(5)),
            event("com.last", timestampMillis = FIXED_NOW)
        )
        val scores = ScoreEngine.score(events, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)

        assertTrue("Heavy app should outrank light app", scores["com.heavy"]!! > scores["com.light"]!!)
    }

    @Test
    fun `recent app outranks older app at same hour`() {
        val events = listOf(
            event("com.recent", timestampMillis = FIXED_NOW),
            event("com.older", timestampMillis = oneHourAgo)
        )
        val scores = ScoreEngine.score(events, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)

        assertTrue("Recent (now) should outrank older (1h ago)", scores["com.recent"]!! > scores["com.older"]!!)
    }

    @Test
    fun `hour matching handles midnight wrap-around`() {
        // Two-app baseline so normalization is meaningful
        val match23 = event("com.match", timestampMillis = 1714863600000L) // May 4, 23:00 UTC
        val mismatchNoon = event("com.mismatch", timestampMillis = 1714824000000L) // May 4, 12:00 UTC
        // currentHour=0 (midnight) → 23h should match (diff=1), noon shouldn't (diff=12)
        val scores = ScoreEngine.score(listOf(match23, mismatchNoon),
            currentHour = 0, currentDayOfWeek = 6, nowMillis = 1714867200000L)

        assertTrue("23:00 event should outrank 12:00 event when now is 00:00",
            scores["com.match"]!! > scores["com.mismatch"]!!)
    }

    @Test
    fun `thirty day old app ranks below recent app`() {
        val events = listOf(
            event("com.today", timestampMillis = FIXED_NOW),
            event("com.old", timestampMillis = thirtyDaysAgo)
        )
        val scores = ScoreEngine.score(events, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)

        assertTrue("30-day-old app should rank below today's app", scores["com.today"]!! > scores["com.old"]!!)
    }

    @Test
    fun `transition feature boosts likely next app within session`() {
        // History: A → B sequence repeated, then a recent A. B should rank above C (never-followed app).
        // Gap is 30s — fits within SESSION_MS (currently 113s) so transition is recorded.
        val sessionGap = TimeUnit.SECONDS.toMillis(30)
        val events = mutableListOf<UsageEvent>()
        var t = FIXED_NOW - TimeUnit.DAYS.toMillis(2)
        repeat(5) {
            events += event("com.a", timestampMillis = t)
            events += event("com.b", timestampMillis = t + sessionGap)
            events += event("com.c", timestampMillis = t + TimeUnit.HOURS.toMillis(3))
            t += TimeUnit.DAYS.toMillis(1) / 5
        }
        // Recent A launch — "now" is within SESSION_MS of this event
        events += event("com.a", timestampMillis = FIXED_NOW - TimeUnit.SECONDS.toMillis(30))

        val scores = ScoreEngine.score(events, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)
        assertTrue("B (followed A 5x) should outrank C", scores["com.b"]!! > scores["com.c"]!!)
    }

    @Test
    fun `returns empty map for empty events`() {
        val scores = ScoreEngine.score(emptyList(), currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)
        assertTrue(scores.isEmpty())
    }

    @Test
    fun `apps not in events are not in scores`() {
        val events = listOf(event("com.known", timestampMillis = FIXED_NOW))
        val scores = ScoreEngine.score(events, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)

        assertTrue("com.known" in scores)
        assertTrue("com.unknown" !in scores)
    }
}
