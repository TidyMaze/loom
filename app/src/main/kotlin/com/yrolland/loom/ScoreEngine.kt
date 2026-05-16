package com.yrolland.loom

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

object ScoreEngine {

    // Tuned via Optuna (200 random + 400 TPE) on 1000 events (9 days, 397 with phase-1 ctx).
    // @1=22.50% MRR=0.3977 vs prev (@1=19.78% MRR=0.3826): +2.72pp @1, +3.92% MRR.
    private const val HOUR_SIGMA = 0.74f
    private const val DECAY_HALF_LIFE_DAYS = 21.7f
    private const val RECENCY_HOURS = 1.02f
    private const val TRANSITION_DECAY_DAYS = 13.8f
    private const val SESSION_MS = 444 * 1000L
    private const val TRANSITION_SMOOTH = 0.43f

    private const val W_CONTEXT = 1.54f
    private const val W_RECENCY = 3.71f
    private const val W_TRANSITION = 2.42f

    // Phase-1 ctx features (decay-weighted conditional P(ctx|pkg), add-k smoothed).
    private const val W_AUDIO = 1.07f
    private const val W_DEVICE = 1.99f
    private const val W_CHARGING = 0.46f
    private const val W_SR = 1.11f
    private const val SR_HALF_LIFE_SECS = 509f
    private const val PHASE1_SMOOTH = 0.63f

    private const val MS_PER_DAY = 86_400_000f
    private val LN2 = ln(2.0)

    fun score(
        events: List<UsageEvent>,
        currentCtx: LaunchContext.Capture? = null,
        currentHour: Int = java.time.LocalTime.now().hour,
        currentDayOfWeek: Int = java.time.LocalDate.now().dayOfWeek.value,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, Float> {
        if (events.isEmpty()) return emptyMap()

        val byPkg = events.groupBy { it.packageName }

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

        // Effective current ctx: passed value, else fall back to most recent event with ctx.
        val effCtx = currentCtx ?: sorted.asReversed().firstOrNull { it.audioActive != null }?.let {
            LaunchContext.Capture(
                secsSinceResume = it.secsSinceResume ?: 0,
                audioActive = it.audioActive ?: false,
                audioDevice = it.audioDevice ?: "speaker",
                charging = it.charging ?: false
            )
        }

        val ctxRaw = HashMap<String, Float>(byPkg.size)
        val recRaw = HashMap<String, Float>(byPkg.size)
        val transRaw = HashMap<String, Float>(byPkg.size)
        val audRaw = HashMap<String, Float>(byPkg.size)
        val devRaw = HashMap<String, Float>(byPkg.size)
        val chgRaw = HashMap<String, Float>(byPkg.size)
        val srRaw = HashMap<String, Float>(byPkg.size)

        for ((pkg, appEvents) in byPkg) {
            var totalDecay = 0.0
            var hourSum = 0.0
            var daySum = 0.0
            var lastMs = 0L
            var audMatch = 0.0; var audTotal = 0.0
            var devMatch = 0.0; var devTotal = 0.0
            var chgMatch = 0.0; var chgTotal = 0.0
            var srMatch = 0.0
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

                if (effCtx != null && event.audioActive != null) {
                    if (event.audioActive == effCtx.audioActive) audMatch += decay
                    audTotal += decay
                    if (event.audioDevice == effCtx.audioDevice) devMatch += decay
                    devTotal += decay
                    if (event.charging == effCtx.charging) chgMatch += decay
                    chgTotal += decay
                    val sd = abs((event.secsSinceResume ?: 0) - effCtx.secsSinceResume).toFloat()
                    srMatch += exp(-(sd / SR_HALF_LIFE_SECS) * LN2) * decay
                }
            }
            ctxRaw[pkg] = if (totalDecay > 0.0) (hourSum * daySum / totalDecay).toFloat() else 0f
            recRaw[pkg] = exp(-((nowMillis - lastMs) / 3_600_000f) / RECENCY_HOURS).toFloat()
            transRaw[pkg] = if (transitionDenom > 0f) {
                ((transitionRow[pkg] ?: 0f) + TRANSITION_SMOOTH) / transitionDenom
            } else 0f

            audRaw[pkg] = ((audMatch + PHASE1_SMOOTH) / (audTotal + 2 * PHASE1_SMOOTH)).toFloat()
            devRaw[pkg] = ((devMatch + PHASE1_SMOOTH) / (devTotal + 2 * PHASE1_SMOOTH)).toFloat()
            chgRaw[pkg] = ((chgMatch + PHASE1_SMOOTH) / (chgTotal + 2 * PHASE1_SMOOTH)).toFloat()
            srRaw[pkg] = srMatch.toFloat()
        }

        val mC = ctxRaw.values.max().coerceAtLeast(1e-9f)
        val mR = recRaw.values.max().coerceAtLeast(1e-9f)
        val mT = transRaw.values.max().coerceAtLeast(1e-9f)
        val mA = audRaw.values.max().coerceAtLeast(1e-9f)
        val mD = devRaw.values.max().coerceAtLeast(1e-9f)
        val mCh = chgRaw.values.max().coerceAtLeast(1e-9f)
        val mSr = srRaw.values.max().coerceAtLeast(1e-9f)

        val useCtxFeats = effCtx != null
        return byPkg.keys.associateWith { pkg ->
            W_CONTEXT * (ctxRaw[pkg] ?: 0f) / mC +
            W_RECENCY * (recRaw[pkg] ?: 0f) / mR +
            W_TRANSITION * (transRaw[pkg] ?: 0f) / mT +
            if (useCtxFeats) {
                W_AUDIO * (audRaw[pkg] ?: 0f) / mA +
                W_DEVICE * (devRaw[pkg] ?: 0f) / mD +
                W_CHARGING * (chgRaw[pkg] ?: 0f) / mCh +
                W_SR * (srRaw[pkg] ?: 0f) / mSr
            } else 0f
        }
    }

    private fun isWeekend(dayOfWeek: Int) = dayOfWeek >= 6
}
