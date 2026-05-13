package com.yrolland.loom

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

object ScoreEngine {

    // Tuned via Optuna (TPE, 500 trials, 150 random warmup) on 787 events (9 days).
    // @1=21.16% MRR=0.3866 vs deployed (@1=20.73% MRR=0.3819): +0.43pp @1, +1.2% MRR.
    private const val HOUR_SIGMA = 0.71f
    private const val DECAY_HALF_LIFE_DAYS = 19.7f
    private const val RECENCY_HOURS = 1.51f
    private const val TRANSITION_DECAY_DAYS = 20.9f
    private const val SESSION_MS = 437 * 1000L
    private const val TRANSITION_SMOOTH = 0.33f

    private const val W_CONTEXT = 3.26f
    private const val W_RECENCY = 4.11f
    private const val W_FREQUENCY = 0.0f
    private const val W_TRANSITION = 3.90f

    private const val MS_PER_DAY = 86_400_000f
    private val LN2 = ln(2.0)

    fun score(
        events: List<UsageEvent>,
        currentHour: Int = java.time.LocalTime.now().hour,
        currentDayOfWeek: Int = java.time.LocalDate.now().dayOfWeek.value,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, Float> {
        if (events.isEmpty()) return emptyMap()

        val byPkg = events.groupBy { it.packageName }
        val totalLaunches = events.size.toFloat()

        // Build session-transition table (decay-weighted: recent transitions count more)
        val sorted = events.sortedBy { it.timestampMillis }
        val transitionWeights = HashMap<String, HashMap<String, Float>>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            if (curr.timestampMillis - prev.timestampMillis <= SESSION_MS) {
                val daysAgo = (nowMillis - prev.timestampMillis) / MS_PER_DAY
                val w = exp(-(daysAgo / TRANSITION_DECAY_DAYS) * LN2).toFloat()
                val row = transitionWeights.getOrPut(prev.packageName) { HashMap() }
                row[curr.packageName] = (row[curr.packageName] ?: 0f) + w
            }
        }

        val lastEvent = sorted.last()
        val inSession = (nowMillis - lastEvent.timestampMillis) <= SESSION_MS
        val transitionRow: Map<String, Float> =
            if (inSession) transitionWeights[lastEvent.packageName] ?: emptyMap() else emptyMap()
        val transitionDenom: Float =
            if (transitionRow.isNotEmpty()) transitionRow.values.sum() + TRANSITION_SMOOTH * byPkg.size
            else 0f

        // Compute raw per-feature scores
        val ctxRaw = HashMap<String, Float>(byPkg.size)
        val recRaw = HashMap<String, Float>(byPkg.size)
        val freqRaw = HashMap<String, Float>(byPkg.size)
        val transRaw = HashMap<String, Float>(byPkg.size)

        for ((pkg, appEvents) in byPkg) {
            var totalDecay = 0.0
            var hourSum = 0.0
            var daySum = 0.0
            var lastMs = 0L
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
                if (event.timestampMillis > lastMs) lastMs = event.timestampMillis
            }
            ctxRaw[pkg] = if (totalDecay > 0.0) (hourSum * daySum / totalDecay).toFloat() else 0f
            recRaw[pkg] = exp(-((nowMillis - lastMs) / 3_600_000f) / RECENCY_HOURS).toFloat()
            freqRaw[pkg] = appEvents.size / totalLaunches
            transRaw[pkg] = if (transitionDenom > 0f) {
                ((transitionRow[pkg] ?: 0f) + TRANSITION_SMOOTH) / transitionDenom
            } else 0f
        }

        // Max-normalise each feature, then weighted linear blend
        val mC = ctxRaw.values.max().coerceAtLeast(1e-9f)
        val mR = recRaw.values.max().coerceAtLeast(1e-9f)
        val mF = freqRaw.values.max().coerceAtLeast(1e-9f)
        val mT = (transRaw.values.max()).coerceAtLeast(1e-9f)

        return byPkg.keys.associateWith { pkg ->
            W_CONTEXT * (ctxRaw[pkg] ?: 0f) / mC +
            W_RECENCY * (recRaw[pkg] ?: 0f) / mR +
            W_FREQUENCY * (freqRaw[pkg] ?: 0f) / mF +
            W_TRANSITION * (transRaw[pkg] ?: 0f) / mT
        }
    }

    private fun isWeekend(dayOfWeek: Int) = dayOfWeek >= 6
}
