package com.yrolland.loom

import android.util.Log
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

object ScoreEngine {

    /** When true, log top-15 apps with per-feature breakdown to logcat (tag=ScoreEngine).
     *  View with: adb logcat -s ScoreEngine */
    private const val DEBUG_LOG = false

    // v14 — Optuna TPE retune on 4617 events; MRR 0.2283→0.3493 (+53%) vs v13+ctx params.
    // Key changes: shorter burst gap, longer session window, stronger w_ctx/w_trans/w_r168,
    //              weaker w_rec/w_trans2, all ctx features positive, tighter notif_scale.
    private const val HOUR_SIGMA = 2.24f
    private const val DECAY_HALF_LIFE_DAYS = 13.65f
    private const val RECENCY_HOURS = 0.51f
    private const val TRANSITION_DECAY_DAYS = 5.60f
    private const val SESSION_MS = 60_000L
    private const val TRANSITION_SMOOTH = 8.98f
    private const val BURST_GAP_MS = 27_500L
    private const val CTX_MIN_EVENTS = 15

    private const val W_CONTEXT = 4.51f
    private const val W_RECENCY = 3.04f
    private const val W_TRANSITION = 2.99f
    private const val W_TRANSITION_2 = 0.36f

    private const val W_REC_8H = 0.56f
    private const val W_REC_24H = 2.16f
    private const val W_REC_168H = 2.67f

    private const val SELF_PENALTY = 16.96f
    private const val SELF_PENALTY_HL_MIN = 51.82f

    private const val W_AUDIO = 0.04f
    private const val W_DEVICE = 1.91f
    private const val W_CHARGING = 1.42f
    private const val W_SR = 1.59f
    private const val SR_HALF_LIFE_SECS = 903.04f
    private const val PHASE1_SMOOTH = 4.50f

    // Phase-3 context features (notif / calendar / battery / dwell) — v14 retune on 4617 events
    private const val W_NOTIF = 0.79f
    private const val W_CAL = 3.32f
    private const val W_BAT = 5.31f
    private const val W_DWELL = 1.25f
    private const val W_CAT_TRANS = 1.20f
    private const val BAT_SCALE = 60.48f    // exp(-|Δbat%| / BAT_SCALE)
    private const val CAL_SCALE = 1741.40f  // exp(-|Δsecs| / CAL_SCALE)
    private const val DWELL_SCALE = 120.0f  // exp(-|ΔdwellSecs| / DWELL_SCALE)
    private const val CTX3_MIN_EVENTS = 11
    private const val CTX3_SMOOTH = 0.375f

    private const val MS_PER_DAY = 86_400_000f
    private val LN2 = ln(2.0)

    fun score(
        events: List<UsageEvent>,
        currentCtx: LaunchContext.Capture? = null,
        currentHour: Int = java.time.LocalTime.now().hour,
        currentDayOfWeek: Int = java.time.LocalDate.now().dayOfWeek.value,
        nowMillis: Long = System.currentTimeMillis(),
        currentNotifCounts: Map<String, Int> = emptyMap(),
        appCategories: Map<String, Int> = emptyMap()
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
                val diff = abs(prev.hour - currentHour)
                val hourDist = if (diff > 12) 24 - diff else diff
                val hourMatch = exp(-(hourDist * hourDist) / (2f * HOUR_SIGMA * HOUR_SIGMA))
                val dayMatch = when {
                    prev.dayOfWeek == 0 -> 1.0f
                    prev.dayOfWeek == currentDayOfWeek -> 1.0f
                    isWeekend(prev.dayOfWeek) == isWeekend(currentDayOfWeek) -> 0.6f
                    else -> 0.2f
                }
                val daysAgo = (nowMillis - prev.timestampMillis) / MS_PER_DAY
                val decay = exp(-(daysAgo / DECAY_HALF_LIFE_DAYS) * LN2).toFloat()
                val w = decay * hourMatch * dayMatch

                transitionWeights.getOrPut(prev.packageName) { HashMap() }
                    .merge(curr.packageName, w) { a, b -> a + b }

                if (i >= 2) {
                    val prevPrev = sorted[i - 2]
                    if (prev.timestampMillis - prevPrev.timestampMillis <= SESSION_MS) {
                        transition2Weights.getOrPut(prevPrev.packageName to prev.packageName) { HashMap() }
                            .merge(curr.packageName, w) { a, b -> a + b }
                    }
                }
            }
        }

        val lastEvent = sorted.last()
        val inSession = (nowMillis - lastEvent.timestampMillis) <= SESSION_MS

        val transScores = HashMap<String, Float>(byPkg.size)
        if (inSession) {
            val penultimate = if (sorted.size >= 2) sorted[sorted.size - 2] else null
            val prev2pkg = if (penultimate != null && lastEvent.timestampMillis - penultimate.timestampMillis <= SESSION_MS)
                penultimate.packageName
            else null

            var row: Map<String, Float>? = if (prev2pkg != null) {
                transition2Weights[prev2pkg to lastEvent.packageName]
            } else null

            if (row == null || row.isEmpty()) {
                row = transitionWeights[lastEvent.packageName]
            }

            if (row != null && row.isNotEmpty()) {
                val sumWeights = row.values.sum()
                val denom = sumWeights + TRANSITION_SMOOTH * nApps
                for (pkg in byPkg.keys) {
                    transScores[pkg] = ((row[pkg] ?: 0f) + TRANSITION_SMOOTH) / denom
                }
            }
        }

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
        val calRaw = HashMap<String, Float>(byPkg.size)
        val batRaw = HashMap<String, Float>(byPkg.size)
        val dwellRaw = HashMap<String, Float>(byPkg.size)
        val ctx3CountByPkg = HashMap<String, Int>(byPkg.size)

        val curBat = currentCtx?.batteryPct ?: 50
        val curCal = currentCtx?.secsToNextEvent
        val curDwell = currentCtx?.prevAppDwellSecs

        for ((pkg, appEvents) in byPkg) {
            var totalDecay = 0.0
            var hourSum = 0.0
            var daySum = 0.0
            var lastMs = 0L
            var audMatch = 0.0; var audTotal = 0.0
            var devMatch = 0.0; var devTotal = 0.0
            var chgMatch = 0.0; var chgTotal = 0.0
            var srMatch = 0.0
            var calMatch = 0.0; var calTotal = 0.0
            var batMatch = 0.0; var batTotal = 0.0
            var dwellMatch = 0.0; var dwellTotal = 0.0
            var ctx3Count = 0
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
                    val evBat = event.batteryPct ?: 50
                    batMatch += exp(-abs(evBat - curBat) / BAT_SCALE) * decay
                    batTotal += decay
                    val evCal = event.secsToNextEvent
                    if (evCal != null && curCal != null) {
                        calMatch += exp(-abs(evCal - curCal) / CAL_SCALE) * decay
                        calTotal += decay
                    }
                    val evDwell = event.prevAppDwellSecs
                    if (evDwell != null && curDwell != null) {
                        dwellMatch += exp(-abs(evDwell - curDwell) / DWELL_SCALE) * decay
                        dwellTotal += decay
                    }
                }
            }
            ctxRaw[pkg] = if (totalDecay > 0.0) (hourSum * daySum / totalDecay).toFloat() else 0f
            val hoursSinceLast = (nowMillis - lastMs) / 3_600_000f
            recRaw[pkg] = exp(-hoursSinceLast / RECENCY_HOURS)
            rec8Raw[pkg] = exp(-hoursSinceLast / 8f)
            rec24Raw[pkg] = exp(-hoursSinceLast / 24f)
            rec168Raw[pkg] = exp(-hoursSinceLast / 168f)
            transRaw[pkg] = transScores[pkg] ?: 0f
            trans2Raw[pkg] = 0f
 
            audRaw[pkg] = ((audMatch + PHASE1_SMOOTH) / (audTotal + 2 * PHASE1_SMOOTH)).toFloat()
            devRaw[pkg] = ((devMatch + PHASE1_SMOOTH) / (devTotal + 2 * PHASE1_SMOOTH)).toFloat()
            chgRaw[pkg] = ((chgMatch + PHASE1_SMOOTH) / (chgTotal + 2 * PHASE1_SMOOTH)).toFloat()
            srRaw[pkg] = srMatch.toFloat()
 
            ctx3CountByPkg[pkg] = ctx3Count
            calRaw[pkg] = if (calTotal > 0) ((calMatch + CTX3_SMOOTH) / (calTotal + 2 * CTX3_SMOOTH)).toFloat() else 0.5f
            batRaw[pkg] = ((batMatch + CTX3_SMOOTH) / (batTotal + 2 * CTX3_SMOOTH)).toFloat()
            dwellRaw[pkg] = if (dwellTotal > 0) ((dwellMatch + CTX3_SMOOTH) / (dwellTotal + 2 * CTX3_SMOOTH)).toFloat() else 0.5f
        }

        // Phase-1 gating with category fallback
        if (effCtx != null) {
            val ctxCountByPkg = HashMap<String, Int>(byPkg.size)
            for ((pkg, evs) in byPkg) {
                ctxCountByPkg[pkg] = evs.count { it.audioActive != null }
            }
            val qualifying = byPkg.keys.filter { (ctxCountByPkg[it] ?: 0) >= CTX_MIN_EVENTS }
            if (qualifying.isNotEmpty()) {
                val globalAud = qualifying.map { audRaw[it] ?: 0.5f }.average().toFloat()
                val globalDev = qualifying.map { devRaw[it] ?: 0.5f }.average().toFloat()
                val globalChg = qualifying.map { chgRaw[it] ?: 0.5f }.average().toFloat()
                val globalSr = qualifying.map { srRaw[it] ?: 0f }.average().toFloat()

                val catQualifying = qualifying.groupBy { appCategories[it] ?: -1 }
                val catAud = catQualifying.mapValues { (_, pkgs) -> pkgs.map { audRaw[it] ?: 0.5f }.average().toFloat() }
                val catDev = catQualifying.mapValues { (_, pkgs) -> pkgs.map { devRaw[it] ?: 0.5f }.average().toFloat() }
                val catChg = catQualifying.mapValues { (_, pkgs) -> pkgs.map { chgRaw[it] ?: 0.5f }.average().toFloat() }
                val catSr = catQualifying.mapValues { (_, pkgs) -> pkgs.map { srRaw[it] ?: 0f }.average().toFloat() }

                for (pkg in byPkg.keys) {
                    if ((ctxCountByPkg[pkg] ?: 0) < CTX_MIN_EVENTS) {
                        val cat = appCategories[pkg] ?: -1
                        audRaw[pkg] = catAud[cat] ?: globalAud
                        devRaw[pkg] = catDev[cat] ?: globalDev
                        chgRaw[pkg] = catChg[cat] ?: globalChg
                        srRaw[pkg] = catSr[cat] ?: globalSr
                    }
                }
            }
        }

        // Phase-3 gating with category fallback
        val ctx3Qualifying = byPkg.keys.filter { (ctx3CountByPkg[it] ?: 0) >= CTX3_MIN_EVENTS }
        if (ctx3Qualifying.isNotEmpty()) {
            val globalCa = ctx3Qualifying.map { calRaw[it] ?: 0.5f }.average().toFloat()
            val globalBa = ctx3Qualifying.map { batRaw[it] ?: 0.5f }.average().toFloat()
            val globalDw = ctx3Qualifying.map { dwellRaw[it] ?: 0.5f }.average().toFloat()

            val catQualifying = ctx3Qualifying.groupBy { appCategories[it] ?: -1 }
            val catCa = catQualifying.mapValues { (_, pkgs) -> pkgs.map { calRaw[it] ?: 0.5f }.average().toFloat() }
            val catBa = catQualifying.mapValues { (_, pkgs) -> pkgs.map { batRaw[it] ?: 0.5f }.average().toFloat() }
            val catDw = catQualifying.mapValues { (_, pkgs) -> pkgs.map { dwellRaw[it] ?: 0.5f }.average().toFloat() }

            for (pkg in byPkg.keys) {
                if ((ctx3CountByPkg[pkg] ?: 0) < CTX3_MIN_EVENTS) {
                    val cat = appCategories[pkg] ?: -1
                    calRaw[pkg] = catCa[cat] ?: globalCa
                    batRaw[pkg] = catBa[cat] ?: globalBa
                    dwellRaw[pkg] = catDw[cat] ?: globalDw
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
        val mCa = calRaw.values.max().coerceAtLeast(1e-9f)
        val mBa = batRaw.values.max().coerceAtLeast(1e-9f)
        val mDw = dwellRaw.values.max().coerceAtLeast(1e-9f)

        val useCtxFeats = effCtx != null
        val useCtx3Feats = currentCtx?.batteryPct != null && ctx3Qualifying.isNotEmpty()
        val selfFactor = if (inSession && SELF_PENALTY > 0)
            (1f - SELF_PENALTY * exp(-(gapMin / SELF_PENALTY_HL_MIN) * LN2).toFloat()).coerceAtLeast(0.40f)
        else 1f

        val lastCategory = if (inSession) appCategories[lastEvent.packageName] ?: -1 else -1

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
            val curNotif = currentNotifCounts[pkg] ?: 0
            val pNo = if (curNotif > 0) W_NOTIF * ln(1f + curNotif) else 0f
            val pCal = if (useCtx3Feats && curCal != null) W_CAL * (calRaw[pkg] ?: 0.5f) / mCa else 0f
            val pBat = if (useCtx3Feats) W_BAT * (batRaw[pkg] ?: 0.5f) / mBa else 0f
            val pDwell = if (useCtx3Feats && curDwell != null) W_DWELL * (dwellRaw[pkg] ?: 0.5f) / mDw else 0f
            val pkgCategory = appCategories[pkg] ?: -1
            val pCatTrans = if (inSession && lastCategory != -1 && lastCategory == pkgCategory) W_CAT_TRANS else 0f

            val base = pCtx + pRec + pR8 + pR24 + pR168 + pT + pT2 + pA + pD + pCh + pSr + pNo + pCal + pBat + pDwell + pCatTrans
            val s = if (pkg == lastEvent.packageName) base * selfFactor else base
            breakdowns?.put(pkg, floatArrayOf(pCtx, pRec, pR8, pR24, pR168, pT, pT2, pA, pD, pCh, pSr, pNo, pCal, pBat, pCatTrans, if (pkg == lastEvent.packageName) base * (selfFactor - 1f) else 0f))
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
        Log.d("ScoreEngine", "labels: ctx rec rec8 rec24 rec168 trans trans2 aud dev chg sr notif cal bat SELF")
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
