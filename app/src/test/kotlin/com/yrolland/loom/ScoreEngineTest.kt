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
    fun `scores sum across multiple events for same app`() {
        val events = listOf(
            event("com.app", timestampMillis = FIXED_NOW),
            event("com.app", timestampMillis = FIXED_NOW),
            event("com.app", timestampMillis = FIXED_NOW)
        )
        val singleEvents = listOf(event("com.app", timestampMillis = FIXED_NOW))

        val multiScore = ScoreEngine.score(events, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)["com.app"]!!
        val singleScore = ScoreEngine.score(singleEvents, currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)["com.app"]!!

        assertEquals("Triple usage should result in triple score", 3 * singleScore, multiScore, 0.001f)
    }

    @Test
    fun `score decreases as event becomes older within the same day`() {
        val recentEvent = event("com.app", timestampMillis = FIXED_NOW)
        val olderEvent = event("com.app", timestampMillis = oneHourAgo)

        val recentScore = ScoreEngine.score(listOf(recentEvent), currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)["com.app"]!!
        val olderScore = ScoreEngine.score(listOf(olderEvent), currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW)["com.app"]!!


        assertTrue("Score for older event ($olderScore) should be less than recent ($recentScore)", 
            olderScore < recentScore)
    }

    @Test
    fun `hour matching handles midnight wrap-around`() {
        // 23:00 and 00:00 are 1 hour apart
        val event23 = event("com.app", timestampMillis = 1714863600000L) // May 4, 23:00 UTC
        
        // currentHour is 0 (midnight), currentDay is 6 (Saturday) to match event
        val score = ScoreEngine.score(listOf(event23), currentHour = 0, currentDayOfWeek = 6, nowMillis = 1714867200000L)["com.app"]!!
        
        // Should get HOUR_MATCH_WEIGHT (1.0) and dayMatch (1.0)
        assertTrue("Event at 23:00 should match current hour 00:00", score >= 0.9f)
    }

    @Test
    fun `thirty day old events score much lower than today`() {
        val todayScore = ScoreEngine.score(
            listOf(event("com.app", timestampMillis = FIXED_NOW)),
            currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW
        )["com.app"]!!

        val oldScore = ScoreEngine.score(
            listOf(event("com.app", timestampMillis = thirtyDaysAgo)),
            currentHour = 18, currentDayOfWeek = 7, nowMillis = FIXED_NOW
        )["com.app"]!!

        assertTrue("Old score ($oldScore) should be < 20% of today ($todayScore)", oldScore < todayScore * 0.2f)
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
