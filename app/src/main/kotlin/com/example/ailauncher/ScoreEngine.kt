package com.example.ailauncher

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

object ScoreEngine {

    // Gaussian σ in hours: smooth bell curve instead of a binary match/miss step.
    // σ=3 means half-peak at ~2.5h away; no hard cutoff, so score transitions are gradual.
    private const val HOUR_SIGMA = 3f
    private const val DECAY_HALF_LIFE_DAYS = 7f
    private const val MS_PER_DAY = 86_400_000f

    fun score(
        events: List<UsageEvent>,
        currentHour: Int = java.time.LocalTime.now().hour,
        currentDayOfWeek: Int = java.time.LocalDate.now().dayOfWeek.value,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, Float> {
        return events
            .groupBy { it.packageName }
            .mapValues { (_, appEvents) ->
                appEvents.sumOf { event ->
                    val diff = abs(event.hour - currentHour)
                    val hourDist = if (diff > 12) 24 - diff else diff
                    val hourMatch = exp(-(hourDist * hourDist) / (2f * HOUR_SIGMA * HOUR_SIGMA))
                    val dayMatch = when {
                        event.dayOfWeek == 0 -> 1.0f
                        event.dayOfWeek == currentDayOfWeek -> 1.0f
                        isWeekend(event.dayOfWeek) == isWeekend(currentDayOfWeek) -> 0.6f
                        else -> 0.2f
                    }
                    val daysAgo = (nowMillis - event.timestampMillis) / MS_PER_DAY
                    val decay = 0.5f.pow(daysAgo / DECAY_HALF_LIFE_DAYS)
                    (hourMatch * dayMatch * decay).toDouble()
                }.toFloat()
            }
    }

    private fun isWeekend(dayOfWeek: Int) = dayOfWeek >= 6
}
