package com.example.ailauncher

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

object ScoreEngine {

    private const val HOUR_SIGMA = 1.5f
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
                // score = Σ(hourMatch × w) / Σ(w)^α   where w = dayMatch × decay, α = 0.3
                // α=0 → raw sum (frequency dominated); α=1 → pure conditional probability.
                // α=0.3 measured as best trade-off: helps specialized apps without hurting
                // the overall ranking quality (MRR −0.011 vs α=0 while TeleLoisirs rank −4).
                var totalWeight = 0.0
                var hourWeightedSum = 0.0
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
                    val decay = 0.5f.pow(daysAgo / DECAY_HALF_LIFE_DAYS).toDouble()
                    val w = dayMatch * decay
                    totalWeight += w
                    hourWeightedSum += hourMatch * w
                }
                if (totalWeight > 0.0) (hourWeightedSum / totalWeight.pow(0.3)).toFloat()
                else 0f
            }
    }

    private fun isWeekend(dayOfWeek: Int) = dayOfWeek >= 6
}
