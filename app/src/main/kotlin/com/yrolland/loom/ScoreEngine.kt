package com.yrolland.loom

import android.util.Log
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

object ScoreEngine {

    /** When true, log top-15 apps with per-feature breakdown to logcat (tag=ScoreEngine).
     *  View with: adb logcat -s ScoreEngine */
    private const val DEBUG_LOG = false

    // v9 — phase-1 gating added (per academic-rigor-auditor recommendation #2).
    // For each app, count ctx-tagged events; if below CTX_MIN_EVENTS, the four
    // phase-1 conditionals (audMatch/devMatch/chgMatch/srMatch) are set to the
    // mean across qualifying apps instead of being computed from too-few samples
    // (was overfitting per agent: PHASE1_SMOOTH alone shrinks toward 0.5 too softly).
    // @1=44.28% MRR=0.5688 vs v8 (@1=43.46% MRR=0.5678): +0.82pp @1, +0.19% MRR.
    // vs pure recency baseline (@1=17.84% MRR=0.4221): +26.44pp @1.
    private const val HOUR_SIGMA = 1.76f
    private const val DECAY_HALF_LIFE_DAYS = 15.0f
    private const val RECENCY_HOURS = 0.45f
    private const val TRANSITION_DECAY_DAYS = 18.0f
    private const val SESSION_MS = 119 * 1000L
    private const val TRANSITION_SMOOTH = 0.55f
    private const val BURST_GAP_MS = 30_000L
    private const val CTX_MIN_EVENTS = 5

    private const val W_CONTEXT = 0.00f
    private const val W_RECENCY = 4.78f
    private const val W_TRANSITION = 3.82f
    private const val W_TRANSITION_2 = 5.57f

    private const val W_REC_8H = 0.40f
    private const val W_REC_24H = 1.00f
    private const val W_REC_168H = 0.40f

    private const val SELF_PENALTY = 2.88f
    private const val SELF_PENALTY_HL_MIN = 17.0f

    private const val W_AUDIO = 1.89f
    private const val W_DEVICE = 1.69f
    private const val W_CHARGING = 0.44f
    private const val W_SR = 3.47f
    private const val SR_HALF_LIFE_SECS = 374.0f
    private const val PHASE1_SMOOTH = 3.50f

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

        val sorted = collapseBursts(events.sortedBy { it.timestampMillis })
        val byPkg = sorted.groupBy { it.packageName }
        val nApps = byPkg.size

        val transitionWeights = HashMap<String, HashMap<String, Float>>()
        val transition2Weights = HashMap<Pair<String, String>, HashMap<String, Float>>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            if (curr.timestampMillis - prev.timestampMillis <= SESSION_MS) {
                val daysAgo = (nowMillis - prev.timestampMillis) / MS_PER_DAY
                val w = exp(-(daysAgo / TRANSITION_DECAY_DAYS) * LN2).toFloat()
                transitionWeights.getOrPut(prev.packageName) { HashMap() }
                    .merge(curr.packageName, w) { a, b -> a + b }
            }
            if (i >= 2) {
                val prevPrev = sorted[i - 2]
                if (prev.timestampMillis - prevPrev.timestampMillis <= SESSION_MS &&
                    curr.timestampMillis - prev.timestampMillis <= SESSION_MS) {
                    val daysAgo = (nowMillis - prevPrev.timestampMillis) / MS_PER_DAY
                    val w = exp(-(daysAgo / TRANSITION_DECAY_DAYS) * LN2).toFloat()
                    transition2Weights.getOrPut(prevPrev.packageName to prev.packageName) { HashMap() }
                        .merge(curr.packageName, w) { a, b -> a + b }
                }
            }
        }

        val lastEvent = sorted.last()
        val inSession = (nowMillis - lastEvent.timestampMillis) <= SESSION_MS
        val transitionRow: Map<String, Float> =
            if (inSession) transitionWeights[lastEvent.packageName] ?: emptyMap() else emptyMap()
        val transitionDenom: Float =
            if (transitionRow.isNotEmpty()) transitionRow.values.sum() + TRANSITION_SMOOTH * nApps else 0f

        val transition2Row: Map<String, Float> = if (inSession && sorted.size >= 2) {
            val penultimate = sorted[sorted.size - 2]
            if (lastEvent.timestampMillis - penultimate.timestampMillis <= SESSION_MS)
                transition2Weights[penultimate.packageName to lastEvent.packageName] ?: emptyMap()
            else emptyMap()
        } else emptyMap()
        val transition2Denom: Float =
            if (transition2Row.isNotEmpty()) transition2Row.values.sum() + TRANSITION_SMOOTH * nApps else 0f

        val effCtx = currentCtx ?: sorted.asReversed().firstOrNull { it.audioActive != null }?.let {
            LaunchContext.Capture(
                secsSinceResume = it.secsSinceResume ?: 0,
                audioActive = it.audioActive ?: false,
                audioDevice = it.audioDevice ?: "speaker",
                charging = it.charging ?: false
            )
        }

        val gapMin = (nowMillis - lastEvent.timestampMillis) / 60_000f

        val ctxRaw = HashMap<String, Float>(byPkg.size)
        val recRaw = HashMap<String, Float>(byPkg.size)
        val rec8Raw = HashMap<String, Float>(byPkg.size)
        val rec24Raw = HashMap<String, Float>(byPkg.size)
        val rec168Raw = HashMap<String, Float>(byPkg.size)
        val transRaw = HashMap<String, Float>(byPkg.size)
        val trans2Raw = HashMap<String, Float>(byPkg.size)
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
            val hoursSinceLast = (nowMillis - lastMs) / 3_600_000f
            recRaw[pkg] = exp(-hoursSinceLast / RECENCY_HOURS)
            rec8Raw[pkg] = exp(-hoursSinceLast / 8f)
            rec24Raw[pkg] = exp(-hoursSinceLast / 24f)
            rec168Raw[pkg] = exp(-hoursSinceLast / 168f)
            transRaw[pkg] = if (transitionDenom > 0f)
                ((transitionRow[pkg] ?: 0f) + TRANSITION_SMOOTH) / transitionDenom else 0f
            trans2Raw[pkg] = if (transition2Denom > 0f)
                ((transition2Row[pkg] ?: 0f) + TRANSITION_SMOOTH) / transition2Denom else 0f

            audRaw[pkg] = ((audMatch + PHASE1_SMOOTH) / (audTotal + 2 * PHASE1_SMOOTH)).toFloat()
            devRaw[pkg] = ((devMatch + PHASE1_SMOOTH) / (devTotal + 2 * PHASE1_SMOOTH)).toFloat()
            chgRaw[pkg] = ((chgMatch + PHASE1_SMOOTH) / (chgTotal + 2 * PHASE1_SMOOTH)).toFloat()
            srRaw[pkg] = srMatch.toFloat()
        }

        // Phase-1 gating: apps with too few ctx-tagged events get the mean of
        // qualifying apps' phase-1 features instead of their own noisy estimate.
        if (effCtx != null) {
            val ctxCountByPkg = HashMap<String, Int>(byPkg.size)
            for ((pkg, evs) in byPkg) {
                ctxCountByPkg[pkg] = evs.count { it.audioActive != null }
            }
            val qualifying = byPkg.keys.filter { (ctxCountByPkg[it] ?: 0) >= CTX_MIN_EVENTS }
            if (qualifying.isNotEmpty()) {
                val mAud = qualifying.map { audRaw[it] ?: 0f }.average().toFloat()
                val mDev = qualifying.map { devRaw[it] ?: 0f }.average().toFloat()
                val mChg = qualifying.map { chgRaw[it] ?: 0f }.average().toFloat()
                val mSr = qualifying.map { srRaw[it] ?: 0f }.average().toFloat()
                for (pkg in byPkg.keys) {
                    if ((ctxCountByPkg[pkg] ?: 0) < CTX_MIN_EVENTS) {
                        audRaw[pkg] = mAud
                        devRaw[pkg] = mDev
                        chgRaw[pkg] = mChg
                        srRaw[pkg] = mSr
                    }
                }
            }
        }

        val mC = ctxRaw.values.max().coerceAtLeast(1e-9f)
        val mR = recRaw.values.max().coerceAtLeast(1e-9f)
        val m8 = rec8Raw.values.max().coerceAtLeast(1e-9f)
        val m24 = rec24Raw.values.max().coerceAtLeast(1e-9f)
        val m168 = rec168Raw.values.max().coerceAtLeast(1e-9f)
        val mT = transRaw.values.max().coerceAtLeast(1e-9f)
        val mT2 = trans2Raw.values.max().coerceAtLeast(1e-9f)
        val mA = audRaw.values.max().coerceAtLeast(1e-9f)
        val mD = devRaw.values.max().coerceAtLeast(1e-9f)
        val mCh = chgRaw.values.max().coerceAtLeast(1e-9f)
        val mSr = srRaw.values.max().coerceAtLeast(1e-9f)

        val useCtxFeats = effCtx != null
        val penaltyDecay = if (inSession && SELF_PENALTY > 0)
            exp(-(gapMin / SELF_PENALTY_HL_MIN) * LN2).toFloat() else 0f

        val breakdowns: HashMap<String, FloatArray>? = if (DEBUG_LOG) HashMap(byPkg.size) else null
        val scores = byPkg.keys.associateWith { pkg ->
            val pCtx = W_CONTEXT * (ctxRaw[pkg] ?: 0f) / mC
            val pRec = W_RECENCY * (recRaw[pkg] ?: 0f) / mR
            val pR8 = W_REC_8H * (rec8Raw[pkg] ?: 0f) / m8
            val pR24 = W_REC_24H * (rec24Raw[pkg] ?: 0f) / m24
            val pR168 = W_REC_168H * (rec168Raw[pkg] ?: 0f) / m168
            val pT = W_TRANSITION * (transRaw[pkg] ?: 0f) / mT
            val pT2 = W_TRANSITION_2 * (trans2Raw[pkg] ?: 0f) / mT2
            val pA = if (useCtxFeats) W_AUDIO * (audRaw[pkg] ?: 0f) / mA else 0f
            val pD = if (useCtxFeats) W_DEVICE * (devRaw[pkg] ?: 0f) / mD else 0f
            val pCh = if (useCtxFeats) W_CHARGING * (chgRaw[pkg] ?: 0f) / mCh else 0f
            val pSr = if (useCtxFeats) W_SR * (srRaw[pkg] ?: 0f) / mSr else 0f
            val pen = if (inSession && pkg == lastEvent.packageName) -SELF_PENALTY * penaltyDecay else 0f
            val s = pCtx + pRec + pR8 + pR24 + pR168 + pT + pT2 + pA + pD + pCh + pSr + pen
            breakdowns?.put(pkg, floatArrayOf(pCtx, pRec, pR8, pR24, pR168, pT, pT2, pA, pD, pCh, pSr, pen))
            s
        }

        if (DEBUG_LOG && breakdowns != null) logBreakdown(scores, breakdowns, effCtx, lastEvent, inSession)
        return scores
    }

    private fun logBreakdown(
        scores: Map<String, Float>,
        breakdowns: Map<String, FloatArray>,
        ctx: LaunchContext.Capture?,
        lastEvent: UsageEvent,
        inSession: Boolean
    ) {
        Log.d("ScoreEngine", "----- score run @${System.currentTimeMillis()} -----")
        Log.d("ScoreEngine", "ctx=${ctx?.let { "audio=${it.audioActive} dev=${it.audioDevice} chg=${it.charging} sr=${it.secsSinceResume}" } ?: "none"}")
        Log.d("ScoreEngine", "last=${lastEvent.packageName} inSession=$inSession")
        Log.d("ScoreEngine", "labels: ctx rec rec8 rec24 rec168 trans trans2 aud dev chg sr SELF")
        scores.entries.sortedByDescending { it.value }.take(15).forEachIndexed { i, (pkg, s) ->
            val b = breakdowns[pkg]!!
            val parts = b.joinToString(" ") { "%+.2f".format(it) }
            Log.d("ScoreEngine", "%2d %-45s %+.2f  [%s]".format(i + 1, pkg, s, parts))
        }
    }

    /** Drop consecutive same-package events within BURST_GAP_MS (keep the first).
     *  Cleans noise from rapid re-launches (e.g. Chrome refreshes) without losing the session start.
     *  No-op when BURST_GAP_MS <= 0. */
    private fun collapseBursts(sorted: List<UsageEvent>): List<UsageEvent> {
        if (BURST_GAP_MS <= 0L || sorted.size < 2) return sorted
        val out = ArrayList<UsageEvent>(sorted.size)
        out.add(sorted[0])
        for (i in 1 until sorted.size) {
            val e = sorted[i]
            val prev = out.last()
            if (e.packageName == prev.packageName &&
                e.timestampMillis - prev.timestampMillis <= BURST_GAP_MS) continue
            out.add(e)
        }
        return out
    }

    private fun isWeekend(dayOfWeek: Int) = dayOfWeek >= 6
}
