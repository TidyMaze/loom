package com.example.ailauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ScoreEngineTest {

    private val now = System.currentTimeMillis()
    private val oneDayAgo = now - TimeUnit.DAYS.toMillis(1)
    private val thirtyDaysAgo = now - TimeUnit.DAYS.toMillis(30)

    private fun event(pkg: String, hour: Int, timestampMillis: Long = now) =
        UsageEvent(packageName = pkg, timestampMillis = timestampMillis, hour = hour)

    @Test
    fun `app launched at current hour scores higher than app launched at different hour`() {
        val events = listOf(
            event("com.maps", hour = 9),
            event("com.music", hour = 14)
        )
        val scores = ScoreEngine.score(events, currentHour = 9, nowMillis = now)

        assertTrue(scores["com.maps"]!! > scores["com.music"]!!)
    }

    @Test
    fun `recent events score higher than old events for same hour`() {
        val events = listOf(
            event("com.recent", hour = 9, timestampMillis = now),
            event("com.old", hour = 9, timestampMillis = thirtyDaysAgo)
        )
        val scores = ScoreEngine.score(events, currentHour = 9, nowMillis = now)

        assertTrue(scores["com.recent"]!! > scores["com.old"]!!)
    }

    @Test
    fun `scores sum across multiple events for same app`() {
        val events = listOf(
            event("com.app", hour = 9),
            event("com.app", hour = 9),
            event("com.app", hour = 9)
        )
        val singleEvents = listOf(event("com.app", hour = 9))

        val multiScore = ScoreEngine.score(events, currentHour = 9, nowMillis = now)["com.app"]!!
        val singleScore = ScoreEngine.score(singleEvents, currentHour = 9, nowMillis = now)["com.app"]!!

        assertTrue(multiScore > singleScore)
        assertEquals(3 * singleScore, multiScore, 0.001f)
    }

    @Test
    fun `thirty day old events score much lower than today`() {
        val todayScore = ScoreEngine.score(
            listOf(event("com.app", hour = 9, timestampMillis = now)),
            currentHour = 9, nowMillis = now
        )["com.app"]!!

        val oldScore = ScoreEngine.score(
            listOf(event("com.app", hour = 9, timestampMillis = thirtyDaysAgo)),
            currentHour = 9, nowMillis = now
        )["com.app"]!!

        assertTrue("Old score ($oldScore) should be < 20% of today ($todayScore)", oldScore < todayScore * 0.2f)
    }

    @Test
    fun `returns empty map for empty events`() {
        val scores = ScoreEngine.score(emptyList(), currentHour = 9, nowMillis = now)
        assertTrue(scores.isEmpty())
    }

    @Test
    fun `apps not in events are not in scores`() {
        val events = listOf(event("com.known", hour = 9))
        val scores = ScoreEngine.score(events, currentHour = 9, nowMillis = now)

        assertTrue("com.known" in scores)
        assertTrue("com.unknown" !in scores)
    }
}
