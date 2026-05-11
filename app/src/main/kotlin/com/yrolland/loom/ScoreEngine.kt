package com.yrolland.loom

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

object ScoreEngine {

    // Tuned via time-series cross-validation on collected usage data.
    private const val HOUR_SIGMA = 0.75f
    private const val DECAY_HALF_LIFE_DAYS = 7.0f
    private const val RECENCY_HOURS = 4.0f
    private const val SESSION_MS = 15 * 60 * 1000L     // ≤15min between launches = same session
    private const val TRANSITION_SMOOTH = 0.5f          // Laplace smoothing for sparse transitions

    // Linear blend weights (applied to per-feature max-normalised scores)
    private const val W_CONTEXT = 1.5f      // hour×day match
    private const val W_RECENCY = 2.0f      // recently opened
    private const val W_FREQUENCY = 0.2f    // overall launch frequency
    private const val W_TRANSITION = 2.0f   // Markov: P(app | last app in session)

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

        // Build session-transition table from chronologically-ordered events
        val sorted = events.sortedBy { it.timestampMillis }
        val transitionCounts = HashMap<String, HashMap<String, Int>>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            if (curr.timestampMillis - prev.timestampMillis <= SESSION_MS) {
                val row = transitionCounts.getOrPut(prev.packageName) { HashMap() }
                row[curr.packageName] = (row[curr.packageName] ?: 0) + 1
            }
        }

        // Are we currently in a session? If yes, use last app's transition row.
        val lastEvent = sorted.last()
        val inSession = (nowMillis - lastEvent.timestampMillis) <= SESSION_MS
        val transitionRow: Map<String, Int> =
            if (inSession) transitionCounts[lastEvent.packageName] ?: emptyMap() else emptyMap()
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
                ((transitionRow[pkg] ?: 0) + TRANSITION_SMOOTH) / transitionDenom
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
