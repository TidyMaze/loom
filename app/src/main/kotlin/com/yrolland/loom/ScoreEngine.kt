package com.yrolland.loom

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

object ScoreEngine {

    private const val HOUR_SIGMA = 1.5f
    private const val DECAY_HALF_LIFE_DAYS = 7f
    private const val MS_PER_DAY = 86_400_000f
    private val LN2 = ln(2.0)

    fun score(
        events: List<UsageEvent>,
        currentHour: Int = java.time.LocalTime.now().hour,
        currentDayOfWeek: Int = java.time.LocalDate.now().dayOfWeek.value,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, Float> {
        return events
            .groupBy { it.packageName }
            .mapValues { (_, appEvents) ->
                // Naive Bayes: P(hour|app) × P(dayType|app) × P(app)
                //   = Σ(hourMatch×decay) × Σ(dayMatch×decay) / Σ(decay)
                // Hour and day conditionals estimated independently — more robust
                // with sparse data than multiplying both per event (v1 heuristic).
                var totalDecay = 0.0
                var hourSum = 0.0
                var daySum = 0.0
                for (event in appEvents) {
                    val diff = abs(event.hour - currentHour)
                    val hourDist = if (diff > 12) 24 - diff else diff
                    val hourMatch = exp(-(hourDist * hourDist) / (2f * HOUR_SIGMA * HOUR_SIGMA))
                    val dayMatch = when {
                        event.dayOfWeek == 0 -> 1.0
                        event.dayOfWeek == currentDayOfWeek -> 1.0
                        isWeekend(event.dayOfWeek) == isWeekend(currentDayOfWeek) -> 0.6
                        else -> 0.2
                    }
                    val daysAgo = (nowMillis - event.timestampMillis) / MS_PER_DAY
                    val decay = exp(-(daysAgo / DECAY_HALF_LIFE_DAYS) * LN2).toDouble()
                    totalDecay += decay
                    hourSum += hourMatch * decay
                    daySum += dayMatch * decay
                }
                if (totalDecay > 0.0) (hourSum * daySum / totalDecay).toFloat()
                else 0f
            }
    }

    private fun isWeekend(dayOfWeek: Int) = dayOfWeek >= 6
}
