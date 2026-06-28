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

        // Sentinel "last event" is a separate app so neither test app is hit by self-penalty.
        val events = listOf(
            event("com.maps", timestampMillis = hour9Timestamp),
            event("com.music", timestampMillis = hour4Timestamp),
            event("com.last", timestampMillis = hour9Timestamp + 1000)
        )
        val scores = ScoreEngine.score(events, currentHour = 9, currentDayOfWeek = 7, nowMillis = hour9Timestamp + 1000)

        assertTrue("Maps (current hour) should score higher than Music", scores["com.maps"]!! > scores["com.music"]!!)
    }

    @Test
    fun `recent events score higher than old events`() {
        // Sentinel "last event" so recent/old aren't self-penalized.
        val events = listOf(
            event("com.recent", timestampMillis = FIXED_NOW - TimeUnit.MINUTES.toMillis(1)),
            event("com.old", timestampMillis = thirtyDaysAgo),
            event("com.last", timestampMillis = FIXED_NOW)
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
            event("com.recent", timestampMillis = FIXED_NOW - TimeUnit.MINUTES.toMillis(1)),
            event("com.older", timestampMillis = oneHourAgo),
            event("com.last", timestampMillis = FIXED_NOW)
        )
        val scores = ScoreEngine.score(events, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)

        assertTrue("Recent (1min ago) should outrank older (1h ago)", scores["com.recent"]!! > scores["com.older"]!!)
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
            event("com.today", timestampMillis = FIXED_NOW - TimeUnit.MINUTES.toMillis(1)),
            event("com.old", timestampMillis = thirtyDaysAgo),
            event("com.last", timestampMillis = FIXED_NOW)
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
            events += event("com.c", timestampMillis = t - TimeUnit.HOURS.toMillis(3))
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
    fun `bigram transitions with backoff fallback`() {
        val t1 = FIXED_NOW - TimeUnit.HOURS.toMillis(2)
        val t2 = t1 + TimeUnit.SECONDS.toMillis(30) // 30s separation prevents interleaving
        val sessionGap = TimeUnit.SECONDS.toMillis(10)

        val events = listOf(
            // Sequence 1: a -> b -> c
            event("com.a", t1),
            event("com.b", t1 + sessionGap),
            event("com.c", t1 + sessionGap * 2),

            // Sequence 2: d -> b -> e
            event("com.d", t2),
            event("com.b", t2 + sessionGap),
            event("com.e", t2 + sessionGap * 2)
        )

        // Case 1: last is a -> b. Bigram matches a -> b -> c.
        // Therefore com.c should outrank com.e
        val events1 = events + listOf(
            event("com.a", FIXED_NOW - sessionGap),
            event("com.b", FIXED_NOW)
        )
        val scores1 = ScoreEngine.score(events1, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW + 1000)
        assertTrue("c should outrank e when history is a -> b", scores1["com.c"]!! > scores1["com.e"]!!)

        // Case 2: last is d -> b. Bigram matches d -> b -> e.
        // Therefore com.e should outrank com.c
        val events2 = events + listOf(
            event("com.d", FIXED_NOW - sessionGap),
            event("com.b", FIXED_NOW)
        )
        val scores2 = ScoreEngine.score(events2, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW + 1000)
        assertTrue("e should outrank c when history is d -> b", scores2["com.e"]!! > scores2["com.c"]!!)

        // Case 3: last is x -> b. Bigram x -> b -> ? does not exist.
        // Falls back to unigram: b -> c and b -> e. Both should score high.
        val events3 = events + listOf(
            event("com.x", FIXED_NOW - sessionGap),
            event("com.b", FIXED_NOW)
        )
        val scores3 = ScoreEngine.score(events3, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW + 1000)
        assertTrue("c should score reasonably high due to unigram fallback", scores3["com.c"]!! > 0f)
        assertTrue("e should score reasonably high due to unigram fallback", scores3["com.e"]!! > 0f)
    }

    @Test
    fun `direct notification boost increases score`() {
        val events = listOf(
            event("com.notifapp", timestampMillis = FIXED_NOW - TimeUnit.HOURS.toMillis(1)),
            event("com.plainapp", timestampMillis = FIXED_NOW - TimeUnit.HOURS.toMillis(1)),
            event("com.last", timestampMillis = FIXED_NOW)
        )
        val notifCounts = mapOf("com.notifapp" to 1)

        val scores = ScoreEngine.score(
            events,
            currentHour = 18,
            currentDayOfWeek = 7,
            nowMillis = FIXED_NOW,
            currentNotifCounts = notifCounts
        )
        assertTrue(
            "notifapp with active notification should outrank plainapp",
            scores["com.notifapp"]!! > scores["com.plainapp"]!!
        )
    }

    @Test
    fun `same category transition boost increases score`() {
        val events = listOf(
            event("com.appA", timestampMillis = FIXED_NOW - TimeUnit.HOURS.toMillis(2)),
            event("com.appB", timestampMillis = FIXED_NOW - TimeUnit.HOURS.toMillis(2)),
            event("com.last", timestampMillis = FIXED_NOW - TimeUnit.SECONDS.toMillis(10))
        )
        val categories = mapOf(
            "com.appA" to 1,
            "com.last" to 1,
            "com.appB" to 2
        )
        val scores = ScoreEngine.score(
            events,
            currentHour = 18,
            currentDayOfWeek = 7,
            nowMillis = FIXED_NOW,
            appCategories = categories
        )
        assertTrue(
            "appA (same category as last) should outrank appB (different category)",
            scores["com.appA"]!! > scores["com.appB"]!!
        )
    }
}

