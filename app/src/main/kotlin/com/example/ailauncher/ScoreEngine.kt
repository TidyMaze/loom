package com.example.ailauncher

import kotlin.math.abs
import kotlin.math.pow

object ScoreEngine {

    private const val HOUR_MATCH_WEIGHT = 1.0f
    private const val HOUR_MISS_WEIGHT = 0.15f
    private const val HOUR_TOLERANCE = 1
    private const val DECAY_HALF_LIFE_DAYS = 7f
    private const val MS_PER_DAY = 86_400_000f

    fun score(
        events: List<UsageEvent>,
        currentHour: Int = java.time.LocalTime.now().hour,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, Float> {
        return events
            .groupBy { it.packageName }
            .mapValues { (_, appEvents) ->
                appEvents.sumOf { event ->
                    val hourMatch = if (abs(event.hour - currentHour) <= HOUR_TOLERANCE)
                        HOUR_MATCH_WEIGHT else HOUR_MISS_WEIGHT
                    val daysAgo = (nowMillis - event.timestampMillis) / MS_PER_DAY
                    val decay = 0.5f.pow(daysAgo / DECAY_HALF_LIFE_DAYS)
                    (hourMatch * decay).toDouble()
                }.toFloat()
            }
    }
}
