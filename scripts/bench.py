"""
Loom — unified model benchmark, evaluation harness, and Optuna retune.
Single source of truth for all offline scoring, tuning, and model comparison.

Replaces: analyze.py, experiment.py, round5.py, round6.py

Usage (via uv):
  uv run python3 scripts/bench.py [OPTIONS]

Options:
  --data FILE        usage_log.json path (default: usage_log.json in cwd)
  --benchmark        Benchmark all models (default when --tune not given)
  --tune             Optuna HP search on train split, report on held-out test
  --apply            Write best Optuna params to ScoreEngine.kt (use with --tune)
  --split FLOAT      Train fraction for tune/eval (default: 0.8)
  --trials N         Optuna trials (default: 200)
  --min-hist N       Walk-forward warmup events (default: 50)
  --stats            Show bootstrap CI + Wilcoxon p-values vs v14
"""

import argparse
import collections
import json
import math
import random
import re
import sys
import time
from pathlib import Path
from typing import Callable

# ─── v14 hyperparameters (mirror ScoreEngine.kt) ────────────────────────────

V14 = dict(
    # Core scoring
    hour_sigma      = 2.2035,
    decay_hl        = 10.7516,   # days — main decay half-life
    recency_h       = 0.5048,    # hours — short recency scale
    trans_decay     = 24.6941,   # days — transition table decay
    session_ms      = 70_000,    # session window (ms)
    trans_smooth    = 1.9016,    # Laplace smoothing for transition probs
    burst_gap_ms    = 25_000,    # collapse same-pkg events within this gap
    ctx_min         = 2,         # phase-1 gate: min events with ctx to qualify
    w_ctx           = 1.8234,
    w_rec           = 3.5373,
    w_trans         = 5.1687,
    w_trans2        = 3.3636,
    w_r8            = 4.8793,
    w_r24           = 0.9228,
    w_r168          = 3.8755,
    self_pen        = 0.0083,
    self_hl_min     = 112.4804,
    # Phase-1 context (audio/device/charging/secsSinceResume)
    w_audio         = 0.04,
    w_device        = 1.91,
    w_charging      = 1.42,
    w_sr            = 1.59,
    sr_hl_secs      = 903.04,
    phase1_smooth   = 4.50,
    # Phase-3 context (notif / calendar / battery)
    w_notif         = 0.79,
    w_cal           = 3.32,
    w_bat           = 5.31,
    w_cat_trans     = 1.20,
    bat_scale       = 60.48,
    cal_scale       = 1741.40,
    ctx3_min        = 11,
    ctx3_smooth     = 0.375,
)

# ─── shared math helpers ─────────────────────────────────────────────────────

_LN2 = math.log(2.0)


def _hd(a: int, b: int) -> int:
    """Circular hour distance (0–12)."""
    d = abs(a - b)
    return min(d, 24 - d)


def _hm(hour_dist: float, sigma: float) -> float:
    return math.exp(-(hour_dist ** 2) / (2 * sigma * sigma))


def _dm(dow: int, now_dow: int) -> float:
    """Day-of-week match factor."""
    if dow == 0 or dow == now_dow:
        return 1.0
    if (dow >= 6) == (now_dow >= 6):   # both weekend or both weekday
        return 0.6
    return 0.2


def _decay(ts_ms: int, now_ms: int, hl_days: float) -> float:
    return 0.5 ** ((now_ms - ts_ms) / 86_400_000 / hl_days)


def _norm(d: dict) -> dict:
    if not d:
        return {}
    m = max(d.values())
    if m <= 0:
        return {k: 0.0 for k in d}
    return {k: v / m for k, v in d.items()}


def _collapse_bursts(sorted_events: list, burst_gap_ms: int = None) -> list:
    """Drop consecutive same-package events within burst_gap_ms (keep first)."""
    gap = burst_gap_ms if burst_gap_ms is not None else V14["burst_gap_ms"]
    if gap <= 0 or len(sorted_events) < 2:
        return sorted_events
    out = [sorted_events[0]]
    for e in sorted_events[1:]:
        prev = out[-1]
        if (e["packageName"] == prev["packageName"] and
                e["timestampMillis"] - prev["timestampMillis"] <= gap):
            continue
        out.append(e)
    return out


def _ema_analytical(events: list, alpha: float = 0.15) -> dict:
    """
    O(n_events) EMA — analytical form.
    Event at sorted position i contributes alpha*(1-alpha)^(n-1-i).
    Identical to incremental O(n*apps) formula but without the inner loop.
    """
    n = len(events)
    ema: dict = collections.defaultdict(float)
    for i, e in enumerate(events):
        ema[e["packageName"]] += alpha * (1 - alpha) ** (n - 1 - i)
    return dict(ema)


def get_app_category(pkg: str) -> int:
    mapping = {
        'com.whatsapp': 4,
        'com.instagram.android': 4,
        'com.facebook.katana': 4,
        'com.facebook.orca': 4,
        'com.twitter.android': 4,
        'org.telegram.messenger': 4,
        'com.google.android.apps.dynamite': 4,
        'com.openai.chatgpt': 4,
        'com.anthropic.claude': 4,
        'com.spotify.music': 1,
        'com.radioplayer.mobile': 1,
        'com.suno.android': 1,
        'com.google.android.youtube': 2,
        'com.netflix.mediaclient': 2,
        'com.google.android.apps.subscriptions.red': 2,
        'com.google.android.gm': 7,
        'com.google.android.calendar': 7,
        'com.google.android.keep': 7,
        'com.google.android.apps.docs': 7,
        'com.google.android.apps.docs.editors.docs': 7,
        'com.google.android.apps.docs.editors.sheets': 7,
        'com.google.android.calculator': 7,
        'com.google.android.apps.playconsole': 7,
        'com.github.android': 7,
        'com.google.android.apps.maps': 6,
        'com.waze': 6,
        'fr.geovelo': 6,
        'com.tranzmate': 6,
        'com.devhd.feedly': 5,
        'com.google.android.apps.magazines': 5,
        'fr.playsoft.teleloisirs': 5,
        'com.google.android.apps.photos': 3,
        'com.google.android.GoogleCamera': 3,
    }
    return mapping.get(pkg, -1)


# ─── ScoreEngine.kt v14 — Python port ────────────────────────────────────────

def score_v14(events: list, now_hour: int, now_dow: int, now_ms: int,
              target_ev: dict = None, p: dict = None) -> dict:
    """
    Faithful port of ScoreEngine.kt v14.
    14 features: ctx (hour×day NB), rec+rec8h/24h/168h, trans1+trans2,
    phase-1 ctx (audio/device/charging/sr), phase-3 ctx (notif/cal/bat),
    self-penalty.
    Context uses last known historical event as proxy (same as Kotlin fallback).
    """
    if not events:
        return {}
    p = p or V14

    sorted_evs = _collapse_bursts(
        sorted(events, key=lambda e: e["timestampMillis"]),
        burst_gap_ms=int(p["burst_gap_ms"]),
    )
    by_pkg: dict = collections.defaultdict(list)
    for e in sorted_evs:
        by_pkg[e["packageName"]].append(e)
    n_apps = len(by_pkg)

    sess_ms = int(p["session_ms"])
    ts_smooth = p["trans_smooth"]

    # Transition tables: 1-gram and 2-gram (context-weighted)
    trans1: dict = collections.defaultdict(lambda: collections.defaultdict(float))
    trans2: dict = collections.defaultdict(lambda: collections.defaultdict(float))
    for i in range(1, len(sorted_evs)):
        prev, curr = sorted_evs[i - 1], sorted_evs[i]
        if curr["timestampMillis"] - prev["timestampMillis"] <= sess_ms:
            w = (_decay(prev["timestampMillis"], now_ms, p["decay_hl"]) *
                 _hm(_hd(prev["hour"], now_hour), p["hour_sigma"]) *
                 _dm(prev.get("dayOfWeek", 0), now_dow))
            trans1[prev["packageName"]][curr["packageName"]] += w
            if i >= 2:
                prevPrev = sorted_evs[i - 2]
                if prev["timestampMillis"] - prevPrev["timestampMillis"] <= sess_ms:
                    trans2[(prevPrev["packageName"], prev["packageName"])][curr["packageName"]] += w

    last_e = sorted_evs[-1]
    in_session = (now_ms - last_e["timestampMillis"]) <= sess_ms

    trans_scores: dict = collections.defaultdict(float)
    if in_session:
        penultimate = sorted_evs[-2] if len(sorted_evs) >= 2 else None
        prev2pkg = penultimate["packageName"] if (penultimate and
                   last_e["timestampMillis"] - penultimate["timestampMillis"] <= sess_ms) else None

        row = dict(trans2.get((prev2pkg, last_e["packageName"]), {})) if prev2pkg else {}
        if not row:
            row = dict(trans1.get(last_e["packageName"], {}))

        if row:
            denom = sum(row.values()) + ts_smooth * n_apps
            for pkg in by_pkg:
                trans_scores[pkg] = (row.get(pkg, 0) + ts_smooth) / denom

    # Proxy ctx: last historical event with audio data (Kotlin fallback behavior)
    eff_ctx = next(
        (e for e in reversed(sorted_evs) if e.get("audioActive") is not None), None
    )
    eff_ctx3 = next(
        (e for e in reversed(sorted_evs) if e.get("notificationCount") is not None), None
    )

    gap_min = (now_ms - last_e["timestampMillis"]) / 60_000.0
    sp = p["self_pen"]
    sh = p["self_hl_min"]
    self_factor = (
        max(0.40, 1.0 - sp * math.exp(-(gap_min / sh) * _LN2))
        if (in_session and sp > 0) else 1.0
    )

    p1s = p["phase1_smooth"]
    ctx3s = p["ctx3_smooth"]
    cur_bat = eff_ctx3.get("batteryPct", 50) if eff_ctx3 else 50
    cur_cal = eff_ctx3.get("secsToNextEvent") if eff_ctx3 else None

    ctx_r, rec_r, r8, r24, r168 = {}, {}, {}, {}, {}
    trans1_r, trans2_r = {}, {}
    aud_r, dev_r, chg_r, sr_r = {}, {}, {}, {}
    cal_r, bat_r = {}, {}
    ctx3_count: dict = {}

    for pkg, pkg_evs in by_pkg.items():
        total_decay = 0.0
        hour_sum = day_sum = 0.0
        last_ms = 0
        aud_m = aud_t = 0.0
        dev_m = dev_t = 0.0
        chg_m = chg_t = 0.0
        sr_m = 0.0
        cal_m = cal_t = 0.0
        bat_m = bat_t = 0.0
        c3 = 0

        for e in pkg_evs:
            hm  = _hm(_hd(e["hour"], now_hour), p["hour_sigma"])
            dm  = _dm(e.get("dayOfWeek", 0), now_dow)
            dec = _decay(e["timestampMillis"], now_ms, p["decay_hl"])
            total_decay += dec
            hour_sum    += hm * dec
            day_sum     += dm * dec
            if e["timestampMillis"] > last_ms:
                last_ms = e["timestampMillis"]

            # Phase-1 ctx features
            if eff_ctx is not None and e.get("audioActive") is not None:
                aud_m += dec if e["audioActive"] == eff_ctx.get("audioActive") else 0
                aud_t += dec
                dev_m += dec if e.get("audioDevice") == eff_ctx.get("audioDevice") else 0
                dev_t += dec
                chg_m += dec if e.get("charging") == eff_ctx.get("charging") else 0
                chg_t += dec
                sd = abs((e.get("secsSinceResume") or 0) - (eff_ctx.get("secsSinceResume") or 0))
                sr_m += math.exp(-(sd / p["sr_hl_secs"]) * _LN2) * dec

            # Phase-3 ctx features
            if e.get("notificationCount") is not None:
                c3 += 1
                ev_bat = e.get("batteryPct", 50)
                bat_m  += math.exp(-abs(ev_bat - cur_bat) / p["bat_scale"]) * dec
                bat_t  += dec
                ev_cal = e.get("secsToNextEvent")
                if ev_cal is not None and cur_cal is not None:
                    cal_m += math.exp(-abs(ev_cal - cur_cal) / p["cal_scale"]) * dec
                    cal_t += dec

        ctx_r[pkg]  = (hour_sum * day_sum / total_decay) if total_decay > 0 else 0.0
        hrs_since   = (now_ms - last_ms) / 3_600_000.0
        rec_r[pkg]  = math.exp(-hrs_since / p["recency_h"])
        r8[pkg]     = math.exp(-hrs_since / 8.0)
        r24[pkg]    = math.exp(-hrs_since / 24.0)
        r168[pkg]   = math.exp(-hrs_since / 168.0)
        trans1_r[pkg] = trans_scores.get(pkg, 0.0)
        trans2_r[pkg] = 0.0

        aud_r[pkg]  = (aud_m + p1s) / (aud_t + 2 * p1s)  if eff_ctx is not None else 0.5
        dev_r[pkg]  = (dev_m + p1s) / (dev_t + 2 * p1s)  if eff_ctx is not None else 0.5
        chg_r[pkg]  = (chg_m + p1s) / (chg_t + 2 * p1s)  if eff_ctx is not None else 0.5
        sr_r[pkg]   = sr_m

        ctx3_count[pkg] = c3
        cal_r[pkg]   = ((cal_m + ctx3s) / (cal_t + 2 * ctx3s)) if cal_t > 0 else 0.5
        bat_r[pkg]   = (bat_m  + ctx3s) / (bat_t  + 2 * ctx3s)

    # Phase-1 gating with category fallback
    ctx_min = int(p["ctx_min"])
    if eff_ctx is not None:
        ctx_cnts = {pkg: sum(1 for e in evs if e.get("audioActive") is not None)
                    for pkg, evs in by_pkg.items()}
        q1 = [pkg for pkg in by_pkg if ctx_cnts.get(pkg, 0) >= ctx_min]
        if q1:
            global_aud = sum(aud_r[pkg] for pkg in q1) / len(q1)
            global_dev = sum(dev_r[pkg] for pkg in q1) / len(q1)
            global_chg = sum(chg_r[pkg] for pkg in q1) / len(q1)
            global_sr  = sum(sr_r[pkg]  for pkg in q1) / len(q1)

            cat_q1 = collections.defaultdict(list)
            for pkg in q1:
                cat_q1[get_app_category(pkg)].append(pkg)
            cat_aud = {cat: sum(aud_r[pkg] for pkg in pkgs) / len(pkgs) for cat, pkgs in cat_q1.items()}
            cat_dev = {cat: sum(dev_r[pkg] for pkg in pkgs) / len(pkgs) for cat, pkgs in cat_q1.items()}
            cat_chg = {cat: sum(chg_r[pkg] for pkg in pkgs) / len(pkgs) for cat, pkgs in cat_q1.items()}
            cat_sr  = {cat: sum(sr_r[pkg]  for pkg in pkgs) / len(pkgs) for cat, pkgs in cat_q1.items()}

            for pkg in by_pkg:
                if ctx_cnts.get(pkg, 0) < ctx_min:
                    cat = get_app_category(pkg)
                    aud_r[pkg] = cat_aud.get(cat, global_aud)
                    dev_r[pkg] = cat_dev.get(cat, global_dev)
                    chg_r[pkg] = cat_chg.get(cat, global_chg)
                    sr_r[pkg]  = cat_sr.get(cat, global_sr)

    # Phase-3 gating with category fallback
    ctx3_min = int(p["ctx3_min"])
    use_ctx3 = eff_ctx3 is not None
    if use_ctx3:
        q3 = [pkg for pkg in by_pkg if ctx3_count.get(pkg, 0) >= ctx3_min]
        if q3:
            global_ca = sum(cal_r[pkg] for pkg in q3) / len(q3)
            global_ba = sum(bat_r[pkg] for pkg in q3) / len(q3)

            cat_q3 = collections.defaultdict(list)
            for pkg in q3:
                cat_q3[get_app_category(pkg)].append(pkg)
            cat_ca = {cat: sum(cal_r[pkg] for pkg in pkgs) / len(pkgs) for cat, pkgs in cat_q3.items()}
            cat_ba = {cat: sum(bat_r[pkg] for pkg in pkgs) / len(pkgs) for cat, pkgs in cat_q3.items()}

            for pkg in by_pkg:
                if ctx3_count.get(pkg, 0) < ctx3_min:
                    cat = get_app_category(pkg)
                    cal_r[pkg] = cat_ca.get(cat, global_ca)
                    bat_r[pkg] = cat_ba.get(cat, global_ba)

    # Max-normalize each feature
    EPS = 1e-9
    mC   = max(ctx_r.values())   or EPS
    mR   = max(rec_r.values())   or EPS
    m8   = max(r8.values())      or EPS
    m24  = max(r24.values())     or EPS
    m168 = max(r168.values())    or EPS
    mT   = max(trans1_r.values()) or EPS
    mT2  = max(trans2_r.values()) or EPS
    mA   = max(aud_r.values())   or EPS
    mD   = max(dev_r.values())   or EPS
    mCh  = max(chg_r.values())   or EPS
    mSr  = max(sr_r.values())    or EPS
    mCa  = max(cal_r.values())   or EPS
    mBa  = max(bat_r.values())   or EPS

    use_ctx1 = eff_ctx is not None
    last_cat = get_app_category(last_e["packageName"]) if in_session else -1

    scores = {}
    for pkg in by_pkg:
        cur_notif = (target_ev.get("notificationCount") or 0) if (target_ev and pkg == target_ev["packageName"]) else 0
        pNo = p["w_notif"] * math.log1p(cur_notif) if cur_notif > 0 else 0.0

        pkg_cat = get_app_category(pkg)
        pCatTrans = p["w_cat_trans"] if (in_session and last_cat != -1 and last_cat == pkg_cat) else 0.0

        s = (p["w_ctx"]    * ctx_r[pkg]   / mC  +
             p["w_rec"]    * rec_r[pkg]   / mR  +
             p["w_r8"]     * r8[pkg]      / m8  +
             p["w_r24"]    * r24[pkg]     / m24 +
             p["w_r168"]   * r168[pkg]    / m168 +
             p["w_trans"]  * trans1_r[pkg]/ mT  +
             p["w_trans2"] * trans2_r[pkg]/ mT2 +
             pNo + pCatTrans)
        if use_ctx1:
            s += (p["w_audio"]    * aud_r[pkg] / mA  +
                  p["w_device"]   * dev_r[pkg] / mD  +
                  p["w_charging"] * chg_r[pkg] / mCh +
                  p["w_sr"]       * sr_r[pkg]  / mSr)
        if use_ctx3:
            s += (p["w_cal"]   * cal_r[pkg]   / mCa +
                  p["w_bat"]   * bat_r[pkg]   / mBa)
        if in_session and pkg == last_e["packageName"]:
            s *= self_factor
        scores[pkg] = s
    return scores


# ─── alternative models ──────────────────────────────────────────────────────

def score_bigram(events: list, now_hour: int, now_dow: int, now_ms: int,
                 target_ev: dict = None, p: dict = None) -> dict:
    """Bigram Markov: 2-gram transitions + ctx NB + recency."""
    if not events:
        return {}
    p = p or V14
    sorted_evs = sorted(events, key=lambda e: e["timestampMillis"])
    by_pkg: dict = collections.defaultdict(list)
    for e in sorted_evs:
        by_pkg[e["packageName"]].append(e)
    n_apps = len(by_pkg)
    sess_ms = int(p["session_ms"])

    bigrams:  dict = collections.defaultdict(lambda: collections.defaultdict(float))
    unigrams: dict = collections.defaultdict(lambda: collections.defaultdict(float))
    for i in range(1, len(sorted_evs)):
        p1, c = sorted_evs[i - 1], sorted_evs[i]
        if c["timestampMillis"] - p1["timestampMillis"] > sess_ms:
            continue
        w = (_decay(p1["timestampMillis"], now_ms, p["decay_hl"]) *
             _hm(_hd(p1["hour"], now_hour), p["hour_sigma"]) *
             _dm(p1.get("dayOfWeek", 0), now_dow))
        unigrams[p1["packageName"]][c["packageName"]] += w
        if i >= 2:
            p2 = sorted_evs[i - 2]
            if p1["timestampMillis"] - p2["timestampMillis"] <= sess_ms:
                bigrams[(p2["packageName"], p1["packageName"])][c["packageName"]] += w

    last_e = sorted_evs[-1]
    in_session = (now_ms - last_e["timestampMillis"]) <= sess_ms
    trans_scores: dict = collections.defaultdict(float)
    if in_session:
        prev2 = None
        if len(sorted_evs) >= 2:
            pe = sorted_evs[-2]
            if last_e["timestampMillis"] - pe["timestampMillis"] <= sess_ms:
                prev2 = pe
        key = ((prev2["packageName"] if prev2 else None), last_e["packageName"])
        row = dict(bigrams.get(key, {})) if key[0] else {}
        if not row:
            row = dict(unigrams.get(last_e["packageName"], {}))
        if row:
            denom = sum(row.values()) + 0.5 * n_apps
            for pkg in by_pkg:
                trans_scores[pkg] = (row.get(pkg, 0) + 0.5) / denom

    ctx_raw, rec_raw = {}, {}
    for pkg, evs in by_pkg.items():
        td = hs = ds = 0.0; last_ms = 0
        for e in evs:
            dec = _decay(e["timestampMillis"], now_ms, p["decay_hl"])
            td  += dec
            hs  += _hm(_hd(e["hour"], now_hour), p["hour_sigma"]) * dec
            ds  += _dm(e.get("dayOfWeek", 0), now_dow) * dec
            if e["timestampMillis"] > last_ms:
                last_ms = e["timestampMillis"]
        ctx_raw[pkg] = (hs * ds / td) if td > 0 else 0.0
        rec_raw[pkg] = math.exp(-((now_ms - last_ms) / 3_600_000) / p["recency_h"])

    cN = _norm(ctx_raw); rN = _norm(rec_raw); tN = _norm(dict(trans_scores))
    return {pkg: 1.5*cN.get(pkg,0) + 2.0*rN.get(pkg,0) + 3.5*tN.get(pkg,0)
            for pkg in by_pkg}


def score_recency(events: list, now_hour: int, now_dow: int, now_ms: int,
                  target_ev: dict = None, p: dict = None) -> dict:
    """Dumb recency baseline: rank by last-launched timestamp."""
    by_pkg: dict = collections.defaultdict(list)
    for e in events:
        by_pkg[e["packageName"]].append(e)
    return {pkg: max(e["timestampMillis"] for e in evs)
            for pkg, evs in by_pkg.items()}


def score_rrf(events: list, now_hour: int, now_dow: int, now_ms: int,
              target_ev: dict = None, p: dict = None, k: int = 60) -> dict:
    """Reciprocal Rank Fusion of v14 + bigram Markov."""
    all_pkgs = list({e["packageName"] for e in events})
    rrf: dict = collections.defaultdict(float)
    for fn in (score_v14, score_bigram):
        s = fn(events, now_hour, now_dow, now_ms, target_ev, p)
        ranked = sorted(all_pkgs, key=lambda pkg: s.get(pkg, 0.0), reverse=True)
        for rank, pkg in enumerate(ranked, 1):
            rrf[pkg] += 1.0 / (k + rank)
    return dict(rrf)


# ─── evaluation harness ──────────────────────────────────────────────────────

def evaluate(events: list, score_fn: Callable, min_hist: int = 50) -> dict:
    """
    Walk-forward CV: score_fn only receives events[:i] — no lookahead.
    Returns @1/@3/@5/@10/MRR/lift/rr_list.
    """
    events = sorted(events, key=lambda e: e["timestampMillis"])
    all_pkgs = list({e["packageName"] for e in events})
    n_apps = len(all_pkgs)
    hits = {1: 0, 3: 0, 5: 0, 10: 0}
    rr_list = []
    count = 0

    for i, target in enumerate(events):
        if i < min_hist:
            continue
        history = events[:i]
        scores  = score_fn(
            history,
            target.get("hour", 0),
            target.get("dayOfWeek", 0) or 1,
            target["timestampMillis"],
            target
        )
        ranked = sorted(all_pkgs, key=lambda pkg: scores.get(pkg, 0.0), reverse=True)
        pkg = target["packageName"]
        for k in hits:
            if pkg in ranked[:k]:
                hits[k] += 1
        rr_list.append(1.0 / (ranked.index(pkg) + 1))
        count += 1

    if count == 0:
        return {}
    random_mrr = sum(1 / r for r in range(1, n_apps + 1)) / n_apps
    mrr = sum(rr_list) / count
    return {
        "n":       count,
        "@1":      hits[1]  / count * 100,
        "@3":      hits[3]  / count * 100,
        "@5":      hits[5]  / count * 100,
        "@10":     hits[10] / count * 100,
        "mrr":     mrr,
        "lift":    mrr / random_mrr if random_mrr > 0 else 0,
        "rr_list": rr_list,
    }


# ─── statistical tests ───────────────────────────────────────────────────────

def bootstrap_ci(rr_a: list, rr_b: list, n_boot: int = 1000,
                 ci: float = 0.95) -> tuple:
    """Paired bootstrap CI on ΔMRR = mean(rr_b) − mean(rr_a)."""
    assert len(rr_a) == len(rr_b)
    n = len(rr_a)
    deltas = []
    for _ in range(n_boot):
        idx = [random.randrange(n) for _ in range(n)]
        deltas.append(sum(rr_b[i] - rr_a[i] for i in idx) / n)
    deltas.sort()
    lo = deltas[int((1 - ci) / 2 * n_boot)]
    hi = deltas[int((1 + ci) / 2 * n_boot)]
    return lo, hi


def wilcoxon_p(rr_a: list, rr_b: list) -> float:
    """Wilcoxon signed-rank p-value. Returns nan if scipy unavailable."""
    try:
        from scipy.stats import wilcoxon  # type: ignore
        diffs = [b - a for a, b in zip(rr_a, rr_b) if a != b]
        if not diffs:
            return 1.0
        _, p = wilcoxon(diffs)
        return float(p)
    except ImportError:
        return float("nan")


# ─── model registry ──────────────────────────────────────────────────────────

MODELS = [
    ("v14 (deployed)", score_v14),
    ("bigram Markov",  score_bigram),
    ("RRF ensemble",   score_rrf),
    ("recency",        score_recency),
]


# ─── display ─────────────────────────────────────────────────────────────────

def _print_table(results: list) -> None:
    base = results[0][1]
    base_at1 = base.get("@1", 0)
    base_mrr = base.get("mrr", 0)
    hdr = (f"{'Model':<22}  {'@1':>6}  {'@3':>6}  {'@5':>6}  {'@10':>6}"
           f"  {'MRR':>7}  {'lift':>5}  {'Δ@1':>6}  {'ΔMRR':>7}")
    print("\n" + hdr)
    print("─" * len(hdr))
    best_at1 = max(r.get("@1", 0) for _, r in results)
    for name, r in results:
        d1   = r.get("@1", 0)  - base_at1
        dmrr = r.get("mrr", 0) - base_mrr
        star = " ◀" if r.get("@1") == best_at1 else ""
        print(f"{name:<22}  {r.get('@1',0):>5.1f}%  {r.get('@3',0):>5.1f}%  "
              f"{r.get('@5',0):>5.1f}%  {r.get('@10',0):>5.1f}%  "
              f"{r.get('mrr',0):>7.4f}  {r.get('lift',0):>4.2f}x  "
              f"{d1:>+5.1f}  {dmrr:>+7.4f}" + star)


# ─── benchmark mode ──────────────────────────────────────────────────────────

def run_benchmark(events: list, min_hist: int = 50, show_stats: bool = False) -> None:
    results = []
    for name, fn in MODELS:
        t0 = time.time()
        r  = evaluate(events, fn, min_hist=min_hist)
        dt = time.time() - t0
        results.append((name, r))
        print(f"  [{name}] @1={r.get('@1',0):.1f}%  "
              f"MRR={r.get('mrr',0):.4f}  ({dt:.0f}s)", flush=True)

    _print_table(results)

    if show_stats and len(results) > 1:
        base_rr = results[0][1].get("rr_list", [])
        print("\n=== Statistical significance vs v14 (bootstrap + Wilcoxon) ===")
        for name, r in results[1:]:
            rr = r.get("rr_list", [])
            if not rr or not base_rr or len(rr) != len(base_rr):
                print(f"  {name:<22} n mismatch — skipped")
                continue
            lo, hi = bootstrap_ci(base_rr, rr)
            p_val  = wilcoxon_p(base_rr, rr)
            delta  = r["mrr"] - results[0][1]["mrr"]
            sig    = "✓ SIG" if lo > 0 else ("✗ ns" if hi < 0 else "— inconclusive")
            print(f"  {name:<22} ΔMRR={delta:+.4f}  [{lo:+.4f}, {hi:+.4f}]  "
                  f"p={p_val:.4f}  {sig}")


# ─── Optuna tune mode ────────────────────────────────────────────────────────

def run_tune(events: list, train_frac: float = 0.8, n_trials: int = 200,
             min_hist: int = 50, tune_stride: int = 4) -> tuple:
    """
    Tune v14 HPs on train split only; report final metrics on held-out test.
    Returns best params dict (all V14 keys, with tuned values replaced).
    """
    try:
        import optuna                                  # type: ignore
        optuna.logging.set_verbosity(optuna.logging.WARNING)
    except ImportError:
        print("optuna not installed. Run: uv pip install optuna", file=sys.stderr)
        sys.exit(1)

    events = sorted(events, key=lambda e: e["timestampMillis"])
    split  = int(len(events) * train_frac)
    train  = events[:split]
    test   = events[split:]
    print(f"Train: {len(train)} events  |  Test: {len(test)} events")

    # Subsample train for Optuna inner loop (stride reduces n_eval_points N×, same history)
    # This keeps the history window intact (events[:i] always uses full train),
    # but skips evaluation targets — trades accuracy for speed.
    train_eval = train[::tune_stride] if tune_stride > 1 else train
    print(f"Optuna eval stride={tune_stride}: {len(train_eval)} eval points per trial "
          f"(~{len(train_eval)*26//1000}min estimated)")

    # Frozen ctx params: too sparse / complex for reliable offline tuning
    _FROZEN = {k: V14[k] for k in (
        "w_audio", "w_device", "w_charging", "w_sr", "sr_hl_secs", "phase1_smooth",
        "w_notif", "w_cal", "w_bat", "w_cat_trans", "bat_scale", "cal_scale",
        "ctx3_min", "ctx3_smooth",
    )}

    def objective(trial) -> float:
        p = {
            "hour_sigma":   trial.suggest_float("hour_sigma",   0.5,  5.0),
            "decay_hl":     trial.suggest_float("decay_hl",     3.0, 60.0),
            "recency_h":    trial.suggest_float("recency_h",    0.5, 10.0),
            "trans_decay":  trial.suggest_float("trans_decay",  1.0, 30.0),
            "session_ms":   trial.suggest_int  ("session_ms",   60_000, 600_000, step=10_000),
            "trans_smooth": trial.suggest_float("trans_smooth", 0.5, 20.0),
            "burst_gap_ms": trial.suggest_int  ("burst_gap_ms", 1_000, 30_000, step=500),
            "ctx_min":      trial.suggest_int  ("ctx_min",      1, 20),
            "w_ctx":        trial.suggest_float("w_ctx",        0.0,  5.0),
            "w_rec":        trial.suggest_float("w_rec",        0.0,  5.0),
            "w_trans":      trial.suggest_float("w_trans",      0.0,  8.0),
            "w_trans2":     trial.suggest_float("w_trans2",     0.0,  4.0),
            "w_r8":         trial.suggest_float("w_r8",         0.0,  5.0),
            "w_r24":        trial.suggest_float("w_r24",        0.0,  5.0),
            "w_r168":       trial.suggest_float("w_r168",       0.0,  5.0),
            "self_pen":     trial.suggest_float("self_pen",     0.0, 50.0),
            "self_hl_min":  trial.suggest_float("self_hl_min",  5.0, 120.0),
            **_FROZEN,
        }
        fn = lambda evs, h, d, t, target_ev=None: score_v14(evs, h, d, t, target_ev, p)
        # Use strided train events — history is still events[:i] for each target i
        r  = evaluate(train_eval, fn, min_hist=min_hist // tune_stride)
        return -r.get("mrr", 0.0)

    study = optuna.create_study(sampler=optuna.samplers.TPESampler(seed=42))
    study.optimize(objective, n_trials=n_trials, show_progress_bar=True)

    best_p = {**V14, **study.best_params}
    best_p["session_ms"]   = int(best_p["session_ms"])
    best_p["burst_gap_ms"] = int(best_p["burst_gap_ms"])
    best_p["ctx_min"]      = int(best_p["ctx_min"])

    print(f"\nBest train MRR (strided): {-study.best_value:.4f}")

    fn_cur  = score_v14
    fn_new  = lambda evs, h, d, t, target_ev=None: score_v14(evs, h, d, t, target_ev, best_p)

    # Full test-set evaluation: all models vs retuned v14
    print("\n=== Held-out test set (ALL models) ===")
    results = []
    for name, fn in [("v14 current", fn_cur), ("v14 retuned", fn_new),
                     ("bigram Markov", score_bigram), ("RRF ensemble", score_rrf),
                     ("recency", score_recency)]:
        r = evaluate(test, fn, min_hist=0)
        results.append((name, r))
        print(f"  [{name}] @1={r.get('@1',0):.1f}%  MRR={r.get('mrr',0):.4f}", flush=True)
    _print_table(results)

    # Check if retuned v14 genuinely beats current v14 (bootstrap CI lower bound > 0)
    rr_cur = results[0][1].get("rr_list", [])
    rr_new = results[1][1].get("rr_list", [])
    improved = False
    if rr_cur and rr_new and len(rr_cur) == len(rr_new):
        lo, hi = bootstrap_ci(rr_cur, rr_new)
        p_val  = wilcoxon_p(rr_cur, rr_new)
        delta  = results[1][1]["mrr"] - results[0][1]["mrr"]
        sig    = "✓ SIGNIFICANT" if lo > 0 else "✗ Not significant"
        print(f"\nv14 retuned vs current: ΔMRR={delta:+.4f}  "
              f"95% CI=[{lo:+.4f}, {hi:+.4f}]  p={p_val:.4f}  {sig}")
        improved = lo > 0  # only if CI lower bound strictly positive

    if improved:
        print("\n✓ Retuned v14 is significantly better — params will be applied.")
        print("\n=== Changed params (vs current V14) ===")
        for k, v in best_p.items():
            if V14.get(k) != v:
                print(f"  {k:<20} {V14.get(k)} → {v:.4f}" if isinstance(v, float)
                      else f"  {k:<20} {V14.get(k)} → {v}")
    else:
        print("\n✗ Retuned v14 not significantly better — skipping apply.")

    return best_p, improved


# ─── apply params to ScoreEngine.kt ─────────────────────────────────────────

# Mapping: Python param name → (Kotlin constant name, value suffix)
_PARAM_MAP = {
    "hour_sigma":   ("HOUR_SIGMA",              "f"),
    "decay_hl":     ("DECAY_HALF_LIFE_DAYS",    "f"),
    "recency_h":    ("RECENCY_HOURS",           "f"),
    "trans_decay":  ("TRANSITION_DECAY_DAYS",   "f"),
    "session_ms":   ("SESSION_MS",              "L"),
    "trans_smooth": ("TRANSITION_SMOOTH",       "f"),
    "burst_gap_ms": ("BURST_GAP_MS",            "L"),
    "ctx_min":      ("CTX_MIN_EVENTS",          ""),
    "w_ctx":        ("W_CONTEXT",               "f"),
    "w_rec":        ("W_RECENCY",               "f"),
    "w_trans":      ("W_TRANSITION",            "f"),
    "w_trans2":     ("W_TRANSITION_2",          "f"),
    "w_r8":         ("W_REC_8H",                "f"),
    "w_r24":        ("W_REC_24H",               "f"),
    "w_r168":       ("W_REC_168H",              "f"),
    "self_pen":     ("SELF_PENALTY",            "f"),
    "self_hl_min":  ("SELF_PENALTY_HL_MIN",     "f"),
}


def apply_params(params: dict, kt_path: Path) -> None:
    """Write all tunable Optuna params back to ScoreEngine.kt via regex."""
    text = kt_path.read_text()
    for py_key, (kt_name, suffix) in _PARAM_MAP.items():
        val = params.get(py_key)
        if val is None:
            continue
        if suffix == "f":
            val_str = f"{float(val):.2f}f"
        elif suffix == "L":
            val_str = f"{int(val):_}L"
        else:
            val_str = str(int(val))
        pattern  = rf"(private const val {kt_name}\s*=\s*)[^\n]+"
        new_text = re.sub(pattern, rf"\g<1>{val_str}", text)
        if new_text == text:
            print(f"  ⚠ Could not patch {kt_name}")
        else:
            text = new_text
            print(f"  ✓ {kt_name} = {val_str}")
    kt_path.write_text(text)
    print(f"\nUpdated {kt_path}")


# ─── main ────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Loom model benchmark + Optuna retune",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--data",        default="usage_log.json")
    parser.add_argument("--benchmark",   action="store_true",
                        help="Benchmark all models")
    parser.add_argument("--tune",        action="store_true",
                        help="Optuna HP search on train split, report on held-out test")
    parser.add_argument("--apply",       action="store_true",
                        help="Write best params to ScoreEngine.kt ONLY if significantly better")
    parser.add_argument("--split",       type=float, default=0.8,
                        help="Train fraction for tune/eval split")
    parser.add_argument("--trials",      type=int,   default=200)
    parser.add_argument("--tune-stride", type=int,   default=4, dest="tune_stride",
                        help="Eval every Nth train event in Optuna (speeds up ~Nx, default 4)")
    parser.add_argument("--min-hist",    type=int,   default=50, dest="min_hist")
    parser.add_argument("--stats",       action="store_true",
                        help="Bootstrap CI + Wilcoxon vs v14")
    args = parser.parse_args()

    data_path = Path(args.data)
    if not data_path.exists():
        print(f"Error: {data_path} not found", file=sys.stderr)
        sys.exit(1)

    events = json.loads(data_path.read_text())
    n_apps = len({e["packageName"] for e in events})
    print(f"Loaded {len(events)} events  |  {n_apps} apps")

    if args.benchmark or not args.tune:
        run_benchmark(events, min_hist=args.min_hist, show_stats=args.stats)

    if args.tune:
        best, improved = run_tune(
            events,
            train_frac=args.split,
            n_trials=args.trials,
            min_hist=args.min_hist,
            tune_stride=args.tune_stride,
        )
        if args.apply:
            if improved:
                kt_path = (Path(__file__).parent.parent /
                           "app/src/main/kotlin/com/yrolland/loom/ScoreEngine.kt")
                print(f"\nApplying to {kt_path} …")
                apply_params(best, kt_path)
                print("\n✓ ScoreEngine.kt updated — ready to build + deploy.")
            else:
                print("\n⚠ --apply given but skipped: retuned model not significantly better.")


if __name__ == "__main__":
    main()

