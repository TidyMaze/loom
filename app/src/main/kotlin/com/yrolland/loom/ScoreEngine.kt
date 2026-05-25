package com.yrolland.loom

import android.util.Log
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

object ScoreEngine {

    /** When true, log top-15 apps with per-feature breakdown to logcat (tag=ScoreEngine).
     *  View with: adb logcat -s ScoreEngine */
    private const val DEBUG_LOG = false

    // v13 + ctx — Optuna re-tune on 3108 events; adds notif/calendar/battery context features.
    // vs v13: MRR +0.0113 CI=[+0.0077,+0.0152] p=0.0000
    // vs v12: MRR=0.5965 (+0.0218) @1=47.49% (+3.84pp) Wilcoxon p=0.0000
    // Key changes: wider hour_sigma, longer decay, near-zero w_ctx, stronger w_trans2, self_pen re-tuned.
    private const val HOUR_SIGMA = 3.67f
    private const val DECAY_HALF_LIFE_DAYS = 11.77f
    private const val RECENCY_HOURS = 0.44f
    private const val TRANSITION_DECAY_DAYS = 3.78f
    private const val SESSION_MS = 120 * 1000L
    private const val TRANSITION_SMOOTH = 1.82f
    private const val BURST_GAP_MS = 11_000L
    private const val CTX_MIN_EVENTS = 8

    private const val W_CONTEXT = 0.007f
    private const val W_RECENCY = 2.84f
    private const val W_TRANSITION = 1.56f
    private const val W_TRANSITION_2 = 2.73f

    private const val W_REC_8H = 0.025f
    private const val W_REC_24H = 3.94f
    private const val W_REC_168H = 2.06f

    private const val SELF_PENALTY = 19.93f
    private const val SELF_PENALTY_HL_MIN = 16.94f

    private const val W_AUDIO = -0.22f
    private const val W_DEVICE = -0.27f
    private const val W_CHARGING = 0.23f
    private const val W_SR = -1.36f
    private const val SR_HALF_LIFE_SECS = 466.28f
    private const val PHASE1_SMOOTH = 1.68f

    // Phase-3 context features (notif / calendar / battery) — tuned on 3108 events
    private const val W_NOTIF = 5.52f
    private const val W_CAL = 2.12f
    private const val W_BAT = 2.92f
    private const val NOTIF_SCALE = 200f   // exp(-|Δnotif| / NOTIF_SCALE)
    private const val BAT_SCALE = 20f      // exp(-|Δbat%| / BAT_SCALE)
    private const val CAL_SCALE = 1800f    // exp(-|Δsecs| / CAL_SCALE) — 30-min half-life
    private const val CTX3_MIN_EVENTS = 6
    private const val CTX3_SMOOTH = 0.054f

    private const val MS_PER_DAY = 86_400_000f
    private val LN2 = ln(2.0)

    fun score(
        events: List<UsageEvent>,
        currentCtx: LaunchContext.Capture? = null,
        currentHour: Int = java.time.LocalTime.now().hour,
        currentDayOfWeek: Int = java.time.LocalDate.now().dayOfWeek.value,
        nowMillis: Long = System.currentTimeMillis(),
        currentNotifCounts: Map<String, Int> = emptyMap()
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
                charging = it.charging ?: false,
                notificationCount = 0
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
        val notifRaw = HashMap<String, Float>(byPkg.size)
        val calRaw = HashMap<String, Float>(byPkg.size)
        val batRaw = HashMap<String, Float>(byPkg.size)
        val ctx3CountByPkg = HashMap<String, Int>(byPkg.size)

        val curBat = currentCtx?.batteryPct ?: 50
        val curCal = currentCtx?.secsToNextEvent

        for ((pkg, appEvents) in byPkg) {
            var totalDecay = 0.0
            var hourSum = 0.0
            var daySum = 0.0
            var lastMs = 0L
            var audMatch = 0.0; var audTotal = 0.0
            var devMatch = 0.0; var devTotal = 0.0
            var chgMatch = 0.0; var chgTotal = 0.0
            var srMatch = 0.0
            var notifMatch = 0.0; var notifTotal = 0.0
            var calMatch = 0.0; var calTotal = 0.0
            var batMatch = 0.0; var batTotal = 0.0
            var ctx3Count = 0
            val curNotif = currentNotifCounts[pkg] ?: 0
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

                if (event.notificationCount != null) {
                    ctx3Count++
                    notifMatch += exp(-abs(event.notificationCount - curNotif) / NOTIF_SCALE) * decay
                    notifTotal += decay
                    val evBat = event.batteryPct ?: 50
                    batMatch += exp(-abs(evBat - curBat) / BAT_SCALE) * decay
                    batTotal += decay
                    val evCal = event.secsToNextEvent
                    if (evCal != null && curCal != null) {
                        calMatch += exp(-abs(evCal - curCal) / CAL_SCALE) * decay
                        calTotal += decay
                    }
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

            ctx3CountByPkg[pkg] = ctx3Count
            notifRaw[pkg] = ((notifMatch + CTX3_SMOOTH) / (notifTotal + 2 * CTX3_SMOOTH)).toFloat()
            calRaw[pkg] = if (calTotal > 0) ((calMatch + CTX3_SMOOTH) / (calTotal + 2 * CTX3_SMOOTH)).toFloat() else 0.5f
            batRaw[pkg] = ((batMatch + CTX3_SMOOTH) / (batTotal + 2 * CTX3_SMOOTH)).toFloat()
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

        // Phase-3 gating: apps with < CTX3_MIN_EVENTS ctx-tagged events get the mean
        val ctx3Qualifying = byPkg.keys.filter { (ctx3CountByPkg[it] ?: 0) >= CTX3_MIN_EVENTS }
        if (ctx3Qualifying.isNotEmpty()) {
            val mNo = ctx3Qualifying.map { notifRaw[it] ?: 0.5f }.average().toFloat()
            val mCa = ctx3Qualifying.map { calRaw[it] ?: 0.5f }.average().toFloat()
            val mBa = ctx3Qualifying.map { batRaw[it] ?: 0.5f }.average().toFloat()
            for (pkg in byPkg.keys) {
                if ((ctx3CountByPkg[pkg] ?: 0) < CTX3_MIN_EVENTS) {
                    notifRaw[pkg] = mNo; calRaw[pkg] = mCa; batRaw[pkg] = mBa
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
        val mNo = notifRaw.values.max().coerceAtLeast(1e-9f)
        val mCa = calRaw.values.max().coerceAtLeast(1e-9f)
        val mBa = batRaw.values.max().coerceAtLeast(1e-9f)

        val useCtxFeats = effCtx != null
        val useCtx3Feats = currentCtx?.batteryPct != null && ctx3Qualifying.isNotEmpty()
        val selfFactor = if (inSession && SELF_PENALTY > 0)
            (1f - SELF_PENALTY * exp(-(gapMin / SELF_PENALTY_HL_MIN) * LN2).toFloat()).coerceAtLeast(0.40f)
        else 1f

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
            val pNo = if (useCtx3Feats) W_NOTIF * (notifRaw[pkg] ?: 0.5f) / mNo else 0f
            val pCal = if (useCtx3Feats && curCal != null) W_CAL * (calRaw[pkg] ?: 0.5f) / mCa else 0f
            val pBat = if (useCtx3Feats) W_BAT * (batRaw[pkg] ?: 0.5f) / mBa else 0f
            val base = pCtx + pRec + pR8 + pR24 + pR168 + pT + pT2 + pA + pD + pCh + pSr + pNo + pCal + pBat
            val s = if (pkg == lastEvent.packageName) base * selfFactor else base
            breakdowns?.put(pkg, floatArrayOf(pCtx, pRec, pR8, pR24, pR168, pT, pT2, pA, pD, pCh, pSr, if (pkg == lastEvent.packageName) base * (selfFactor - 1f) else 0f))
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
