"""
Loom — unified model benchmark, evaluation harness, and Optuna retune.
Single source of truth for all offline scoring, tuning, and model comparison.

Replaces: analyze.py, experiment.py, round5.py, round6.py

Usage (via uv):
  uv run python3 scripts/bench.py [OPTIONS]

Options:
  --data FILE        usage_log.json path (default: usage_log.json in cwd)
  --benchmark        Benchmark all models (default when --tune not given)
  --tune             One Optuna study per regime (in-session, cold) on the train split,
                     then v17 vs candidate on the held-out test, scored once
  --study-dir DIR    Optuna sqlite dir, required with --tune, resume-safe (keep it outside the repo)
  --regime R         in | cold | both: tune one regime (train only) or both plus the test (default: both)
  --apply            Disabled (exits non-zero): the tuner prints a Kotlin block instead
  --split FLOAT      Train fraction for tune/eval (default: 0.8)
  --trials N         Optuna trials per study, total including resumed ones (default: 200)
  --tune-stride N    Score every Nth train target per trial (default: 2)
  --min-hist N       Walk-forward warmup events (default: 50)
  --stats            Show bootstrap CI + Wilcoxon p-values vs v14
"""

import argparse
import collections
import functools
import json
import math
import random
import sys
import time
from pathlib import Path
from types import ModuleType
from typing import TYPE_CHECKING, Any, Callable, Iterator, NamedTuple, NoReturn, TypedDict

if TYPE_CHECKING:
    from optuna import Study, Trial
    from optuna.trial import FrozenTrial

# ─── v16 hyperparameters (previous dual-regime model, kept for --benchmark) ───

V16_IN = dict(
    hour_sigma      = 2.5283,
    decay_hl        = 10.7516,
    recency_h       = 0.5048,
    trans_decay     = 24.6941,
    session_ms      = 70_000,
    trans_smooth    = 1.1458,
    burst_gap_ms    = 25_000,
    ctx_min         = 2,
    w_ctx           = 0.9984,
    w_rec           = 1.0723,
    w_trans         = 8.2760,
    w_trans2        = 9.7385,
    w_r8            = 1.8151,
    w_r24           = 0.5000,
    w_r168          = 1.3568,
    self_pen        = 0.0000,
    self_hl_min     = 112.4804,
    w_audio         = 0.04,
    w_device        = 0.50,
    w_charging      = 1.42,
    w_sr            = 0.50,
    sr_hl_secs      = 903.04,
    phase1_smooth   = 4.50,
    w_notif         = 0.79,
    w_cal           = 0.50,
    w_bat           = 1.00,
    w_cat_trans     = 1.20,
    bat_scale       = 60.48,
    cal_scale       = 1741.40,
    ctx3_min        = 11,
    ctx3_smooth     = 0.375,
)

V16_COLD = dict(V16_IN)
V16_COLD.update(dict(
    w_ctx           = 3.2691,
    w_rec           = 5.3538,
    w_trans         = 0.0000,
    w_trans2        = 0.0000,
    w_r8            = 2.5227,
    w_r24           = 2.6751,
    w_r168          = 9.5687,
    w_bat           = 3.2513,
    w_cal           = 1.4203,
    w_device        = 0.9407,
    w_sr            = 0.7066,
))

V16 = V16_IN

# ─── v17 hyperparameters (mirror ScoreEngine.kt v17 Dual-Regime) ──────────────
# V16 with the constants retuned by --tune, at the 2 decimals ScoreEngine.kt ships.

V17_IN = {**V16_IN, **dict(
    trans_smooth    = 1.31,
    w_ctx           = 0.30,
    w_rec           = 4.17,
    w_trans         = 9.68,
    w_trans2        = 14.11,
    w_r8            = 0.67,
    w_r168          = 1.57,
    self_pen        = 0.38,
)}

V17_COLD = {**V17_IN, **dict(
    w_ctx           = 0.54,
    w_rec           = 5.92,
    w_trans         = 0.0000,
    w_trans2        = 0.0000,
    w_r8            = 2.93,
    w_r24           = 4.21,
    w_r168          = 8.38,
    w_bat           = 1.24,
    w_cal           = 4.19,
    w_device        = 1.08,
    w_sr            = 0.60,
)}

V15 = dict(
    hour_sigma      = 2.2035,
    decay_hl        = 10.7516,
    recency_h       = 0.5048,
    trans_decay     = 24.6941,
    session_ms      = 70_000,
    trans_smooth    = 1.9016,
    burst_gap_ms    = 25_000,
    ctx_min         = 2,
    w_ctx           = 0.2924,
    w_rec           = 5.7759,
    w_trans         = 3.1978,
    w_trans2        = 4.9098,
    w_r8            = 0.8073,
    w_r24           = 0.7369,
    w_r168          = 2.0725,
    self_pen        = 0.0083,
    self_hl_min     = 112.4804,
    w_audio         = 0.04,
    w_device        = 1.91,
    w_charging      = 1.42,
    w_sr            = 1.59,
    sr_hl_secs      = 903.04,
    phase1_smooth   = 4.50,
    w_notif         = 0.79,
    w_cal           = 3.32,
    w_bat           = 5.31,
    w_cat_trans     = 1.20,
    bat_scale       = 60.48,
    cal_scale       = 1741.40,
    ctx3_min        = 11,
    ctx3_smooth     = 0.375,
)

V14 = dict(V15)
V14.update(dict(
    w_ctx=1.8234, w_rec=3.5373, w_trans=5.1687, w_trans2=3.3636,
    w_r8=4.8793, w_r24=0.9228, w_r168=3.8755
))

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

Event = dict[str, Any]   # one usage_log.json row, the deserialization boundary
Selector = Callable[[list[Event], Event, int], bool]   # (history, target, i) -> keep this target?
EvalResult = TypedDict("EvalResult", {
    "n": int, "@1": float, "@3": float, "@5": float, "@10": float,
    "mrr": float, "lift": float, "rr_list": list[float],
}, total=False)   # empty when no target was scored

# ScoreEngine.kt SESSION_MS: a target is in-session iff its gap to the previous event is <= this.
IN_SESSION_MS = int(V17_IN["session_ms"])


def is_in_session(history: list[Event], target: Event) -> bool:
    """Regime of a target, same comparison as ScoreEngine.kt inSession (inclusive)."""
    return bool(target["timestampMillis"] - history[-1]["timestampMillis"] <= IN_SESSION_MS)


def iter_targets(events: list[Event], min_hist: int = 50, start: int = 0,
                 end: int | None = None, stride: int = 1,
                 select: Selector | None = None) -> Iterator[tuple[int, list[Event], Event]]:
    """Walk-forward targets of a time-sorted log, as (i, history, target).

    The history is always events[:i] of the full log: start/end/stride/select only choose
    which targets are scored, never what a target gets to see.
    """
    stop = len(events) if end is None else min(end, len(events))
    for i in range(max(min_hist, start), stop, stride):
        history = events[:i]
        if select is None or select(history, events[i], i):
            yield i, history, events[i]


def evaluate(events: list[Event], score_fn: Callable[..., dict[str, float]], min_hist: int = 50,
             start: int = 0, end: int | None = None, stride: int = 1,
             select: Selector | None = None) -> EvalResult:
    """
    Walk-forward CV: score_fn only receives events[:i], no lookahead, never thinned.
    Targets are range(max(min_hist, start), end, stride), optionally filtered by select.
    Returns @1/@3/@5/@10/MRR/lift/rr_list.
    """
    events = sorted(events, key=lambda e: e["timestampMillis"])
    all_pkgs = sorted({e["packageName"] for e in events})   # sorted: score ties must not depend on hash order
    n_apps = len(all_pkgs)
    hits = {1: 0, 3: 0, 5: 0, 10: 0}
    rr_list = []
    count = 0

    for _, history, target in iter_targets(events, min_hist, start, end, stride, select):
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


def score_v16(events: list, now_hour: int, now_dow: int, now_ms: int,
              target_ev: dict = None, p_in: dict = None, p_cold: dict = None) -> dict:
    """ScoreEngine.kt v16 Dual-Regime (In-Session vs Cold-Start)."""
    if not events:
        return {}
    p_in = p_in or V16_IN
    p_cold = p_cold or V16_COLD
    last_e = events[-1]
    in_session = (now_ms - last_e["timestampMillis"]) <= int(p_in["session_ms"])
    p = p_in if in_session else p_cold
    return score_v14(events, now_hour, now_dow, now_ms, target_ev, p)


# ScoreEngine.kt v17 Dual-Regime: the v16 blend with the v17 constants.
score_v17 = functools.partial(score_v16, p_in=V17_IN, p_cold=V17_COLD)


def score_v15(events: list, now_hour: int, now_dow: int, now_ms: int,
              target_ev: dict = None, p: dict = None) -> dict:
    """ScoreEngine.kt v15 - previous retune."""
    return score_v14(events, now_hour, now_dow, now_ms, target_ev, p or V15)


# ─── model registry ──────────────────────────────────────────────────────────

MODELS = [
    ("v17 (deployed dual-regime)", score_v17),
    ("v16 (previous dual-regime)", score_v16),
    ("v15 (previous single-regime)", score_v15),
    ("v14 (baseline)", score_v14),
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

# Search spaces per regime (bounds from scripts/test_dual_regime.py). Frozen, never searched:
# session_ms, burst_gap_ms, hour_sigma, decay_hl, recency_h and everything not listed here.
SPACES: dict[str, dict[str, tuple[float, float]]] = {
    "in": {
        "w_trans": (2.0, 20.0), "w_trans2": (2.0, 15.0), "trans_smooth": (0.05, 1.5),
        "w_rec": (0.5, 8.0), "w_r8": (0.1, 2.0), "w_r168": (0.1, 2.0),
        "w_ctx": (0.0, 1.0), "self_pen": (0.0, 0.4),
    },
    "cold": {
        "w_ctx": (0.5, 6.0), "w_r8": (0.5, 8.0), "w_r24": (0.5, 5.0), "w_r168": (1.0, 10.0),
        "w_rec": (0.5, 6.0), "w_bat": (1.0, 10.0), "w_cal": (0.5, 6.0),
        "w_device": (0.5, 5.0), "w_sr": (0.5, 5.0),
    },
}
BASE: dict[str, dict[str, float]] = {"in": V17_IN, "cold": V17_COLD}

# Python param -> ScoreEngine.kt constant, per regime (self_pen is one shared constant).
KOTLIN_NAMES: dict[str, dict[str, str]] = {
    "in": {
        "w_trans": "W_IN_TRANSITION", "w_trans2": "W_IN_TRANSITION_2",
        "trans_smooth": "W_IN_TRANS_SMOOTH", "w_rec": "W_IN_RECENCY", "w_r8": "W_IN_REC_8H",
        "w_r168": "W_IN_REC_168H", "w_ctx": "W_IN_CONTEXT", "self_pen": "SELF_PENALTY",
    },
    "cold": {
        "w_ctx": "W_COLD_CONTEXT", "w_r8": "W_COLD_REC_8H", "w_r24": "W_COLD_REC_24H",
        "w_r168": "W_COLD_REC_168H", "w_rec": "W_COLD_RECENCY", "w_bat": "W_COLD_BAT",
        "w_cal": "W_COLD_CAL", "w_device": "W_COLD_DEVICE", "w_sr": "W_COLD_SR",
    },
}

_INT_PARAMS = {"ctx_min", "ctx3_min"}


def round_params(p: dict[str, float]) -> dict[str, float]:
    """Shipped precision: Kotlin constants carry 2 decimals, *_ms and event counts are integers."""
    return {k: int(round(v)) if k.endswith("_ms") or k in _INT_PARAMS else round(v, 2)
            for k, v in p.items()}


def offline_params(p: dict[str, float]) -> dict[str, float]:
    """Params as shipped, minus the notification boost: it reads the target's own notificationCount."""
    return {**round_params(p), "w_notif": 0.0}


def kotlin_block(regime: str, params: dict[str, float]) -> str:
    """The tuned constants of one regime as `private const val` lines for ScoreEngine.kt."""
    names = KOTLIN_NAMES[regime]
    return "\n".join(f"private const val {names[k]} = {params[k]:.2f}f" for k in SPACES[regime])


class Acceptance(NamedTuple):
    d_mrr: float
    ci_lo: float
    ci_hi: float
    d_at1: float
    d_at5: float
    p_value: float
    d_mrr_in: float
    d_mrr_cold: float
    checks: dict[str, bool]

    @property
    def accepted(self) -> bool:
        return all(self.checks.values())


def _mean_delta(rr_base: list[float], rr_cand: list[float], in_session: list[bool], want: bool) -> float:
    idx = [i for i, flag in enumerate(in_session) if flag == want]
    return sum(rr_cand[i] - rr_base[i] for i in idx) / len(idx) if idx else 0.0


def acceptance(base: EvalResult, cand: EvalResult, in_session: list[bool],
               n_boot: int = 2000, seed: int = 0) -> Acceptance:
    """Ship rule on the test targets: every check must hold (candidate = cand, shipped model = base)."""
    rr_base, rr_cand = base["rr_list"], cand["rr_list"]
    random.seed(seed)
    lo, hi = bootstrap_ci(rr_base, rr_cand, n_boot=n_boot)
    p_value = wilcoxon_p(rr_base, rr_cand)
    d_at1, d_at5 = cand["@1"] - base["@1"], cand["@5"] - base["@5"]
    d_in = _mean_delta(rr_base, rr_cand, in_session, True)
    d_cold = _mean_delta(rr_base, rr_cand, in_session, False)
    eps = 1e-9
    checks = {
        "ci_lo>0": lo > 0,
        "hit1>=0": d_at1 >= -eps,
        "hit5>=-0.3pp": d_at5 >= -0.3 - eps,
        "wilcoxon<0.05": p_value < 0.05,
        "regime dMRR>=-0.005": d_in >= -0.005 and d_cold >= -0.005,
    }
    return Acceptance(cand["mrr"] - base["mrr"], lo, hi, d_at1, d_at5, p_value, d_in, d_cold, checks)


def _import_optuna() -> ModuleType:
    try:
        import optuna
    except ImportError:
        sys.exit("optuna not installed. Run: uv run --with optuna --with scipy python3 scripts/bench.py ...")
    optuna.logging.set_verbosity(optuna.logging.WARNING)
    return optuna


def _regime_selector(regime: str) -> Selector:
    want = regime == "in"
    return lambda history, target, i: is_in_session(history, target) == want


def _regime_counts(events: list[Event], min_hist: int, start: int, end: int | None,
                   stride: int) -> tuple[int, int]:
    """(in-session, cold) target counts of a window."""
    flags = [is_in_session(h, t) for _, h, t in iter_targets(events, min_hist, start, end, stride)]
    return sum(flags), len(flags) - sum(flags)


def _tune_regime(regime: str, events: list[Event], split: int, n_trials: int, min_hist: int,
                 tune_stride: int, study_dir: Path) -> dict[str, float]:
    """One resumable Optuna study on the regime's train targets [min_hist, split).

    Trial 0 is the shipped model (BASE) itself. The score is the MRR of the shipped-precision params on the
    regime's targets, history always the full log before each target. Returns the best
    params at shipped precision.
    """
    optuna = _import_optuna()
    space, base, select = SPACES[regime], BASE[regime], _regime_selector(regime)
    db = study_dir / f"tune_{regime}.db"
    study_dir.mkdir(parents=True, exist_ok=True)
    study = optuna.create_study(
        study_name=f"loom_{regime}", storage=f"sqlite:///{db}", load_if_exists=True,
        direction="maximize", sampler=optuna.samplers.TPESampler(seed=0))

    fingerprint = (f"n={len(events)} last_ts={events[-1]['timestampMillis']} split={split} "
                   f"min_hist={min_hist} stride={tune_stride}")
    stored = study.user_attrs.get("fingerprint")
    if stored is None:
        study.set_user_attr("fingerprint", fingerprint)
    elif stored != fingerprint:
        sys.exit(f"stale study {db}: built on [{stored}], now [{fingerprint}]. Use a new --study-dir.")
    if not study.trials:
        study.enqueue_trial({k: float(base[k]) for k in space})

    def objective(trial: "Trial") -> float:
        suggested = {k: trial.suggest_float(k, lo, hi) for k, (lo, hi) in space.items()}
        p = offline_params({**base, **suggested})
        fn = lambda h, hr, d, t, target=None: score_v14(h, hr, d, t, target, p)
        r = evaluate(events, fn, min_hist, start=min_hist, end=split, stride=tune_stride, select=select)
        return r.get("mrr", 0.0)

    def log_trial(study: "Study", trial: "FrozenTrial") -> None:
        assert trial.value is not None and trial.datetime_start and trial.datetime_complete
        secs = (trial.datetime_complete - trial.datetime_start).total_seconds()
        print(f"[{regime}] trial {trial.number:>3}  mrr={trial.value:.4f}  "
              f"best={study.best_value:.4f}  {secs:.0f}s", flush=True)

    done = sum(t.state == optuna.trial.TrialState.COMPLETE for t in study.trials)
    print(f"[{regime}] {done}/{n_trials} trials already in {db}", flush=True)
    study.optimize(objective, n_trials=max(0, n_trials - done), callbacks=[log_trial])
    if not any(t.state == optuna.trial.TrialState.COMPLETE for t in study.trials):
        sys.exit(f"[{regime}] no completed trial in {db}")
    print(f"[{regime}] best train mrr={study.best_value:.4f} (trial {study.best_trial.number})", flush=True)
    return offline_params({**base, **study.best_params})


def _report_test(events: list[Event], split: int, min_hist: int,
                 best: dict[str, dict[str, float]]) -> None:
    """Score v17 and the candidate once on the test targets [split, N), full history, and gate."""
    def arm(p_in: dict[str, float], p_cold: dict[str, float]) -> Callable[..., dict[str, float]]:
        return lambda h, hr, d, t, target=None: score_v16(h, hr, d, t, target, p_in, p_cold)

    base_p = {r: offline_params(BASE[r]) for r in SPACES}
    r_base = evaluate(events, arm(base_p["in"], base_p["cold"]), min_hist, start=split)
    r_cand = evaluate(events, arm(best["in"], best["cold"]), min_hist, start=split)
    flags = [is_in_session(h, t) for _, h, t in iter_targets(events, min_hist, split)]
    acc = acceptance(r_base, r_cand, flags)

    print("\n=== Held-out test (both arms: shipped precision, w_notif=0) ===")
    _print_table([("v17 (as shipped)", r_base), ("candidate", r_cand)])
    print(f"\ntest targets n={r_cand['n']}")
    print(f"ΔMRR={acc.d_mrr:+.4f}  95% CI=[{acc.ci_lo:+.4f}, {acc.ci_hi:+.4f}]  Wilcoxon p={acc.p_value:.4f}"
          f"  Δ@1={acc.d_at1:+.2f}pp  Δ@5={acc.d_at5:+.2f}pp")
    print(f"regime ΔMRR: in-session {acc.d_mrr_in:+.4f} ({sum(flags)} targets)  "
          f"cold {acc.d_mrr_cold:+.4f} ({len(flags) - sum(flags)} targets)")
    for check, ok in acc.checks.items():
        print(f"  {'pass' if ok else 'FAIL'}  {check}")
    print("\nTuned params (v17 -> candidate):")
    for regime, space in SPACES.items():
        for k in space:
            print(f"  [{regime}] {k:<13} {base_p[regime][k]} -> {best[regime][k]}")
    if not acc.accepted:
        failed = ", ".join(c for c, ok in acc.checks.items() if not ok)
        print(f"\nVERDICT: REJECT, keep v17 (failed: {failed})")
        return
    print("\nVERDICT: ACCEPT (all checks passed). Kotlin block, paste by hand into ScoreEngine.kt:")
    for regime, space in SPACES.items():
        if any(best[regime][k] != base_p[regime][k] for k in space):
            print(f"\n// candidate, {regime} regime\n{kotlin_block(regime, best[regime])}")


def run_tune(events: list[Event], train_frac: float = 0.8, n_trials: int = 200,
             min_hist: int = 50, tune_stride: int = 2, study_dir: Path | None = None,
             regime: str = "both") -> dict[str, dict[str, float]]:
    """
    Tune v17 per regime on train targets [min_hist, split); with regime="both" also score
    v17 vs the candidate on test targets [split, N) once. Every target sees the full log
    before it. Returns the best params per regime tuned, at shipped precision.
    """
    if study_dir is None:
        sys.exit("run_tune needs a study_dir (Optuna sqlite, outside the repo)")
    random.seed(0)
    events = sorted(events, key=lambda e: e["timestampMillis"])
    split = int(len(events) * train_frac)
    regimes = tuple(SPACES) if regime == "both" else (regime,)

    tr_in, tr_cold = _regime_counts(events, min_hist, min_hist, split, 1)
    ps_in, ps_cold = _regime_counts(events, min_hist, min_hist, split, tune_stride)
    te_in, te_cold = _regime_counts(events, min_hist, split, None, 1)
    print(f"train: in-session={tr_in} cold={tr_cold}  (per trial at stride {tune_stride}: "
          f"in-session={ps_in} cold={ps_cold})")
    print(f"test: in-session={te_in} cold={te_cold}  (events {split}..{len(events)})", flush=True)
    per_trial = {"in": ps_in, "cold": ps_cold}
    empty = [r for r in regimes if per_trial[r] == 0]
    if empty:
        sys.exit(f"no train targets for regime {empty}: the study would be uniform")
    if regime == "both" and 0 in (te_in, te_cold):
        sys.exit("a regime has no test targets: the gate cannot be evaluated")

    best = {r: _tune_regime(r, events, split, n_trials, min_hist, tune_stride, study_dir)
            for r in regimes}
    if regime == "both":
        _report_test(events, split, min_hist, best)
    else:
        print("\ntrain only. Run --regime both on the same --study-dir to score the test once.")
    return best


# ─── apply params to ScoreEngine.kt: disabled ───────────────────────────────

# Mapping: Python param name → (Kotlin constant name, value suffix). Dead: it names v14's
# single-regime constants (W_CONTEXT, W_TRANSITION...), the dual-regime file has W_IN_* / W_COLD_*.
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


KT_PATH = Path(__file__).parent.parent / "app/src/main/kotlin/com/yrolland/loom/ScoreEngine.kt"


def apply_params(params: dict[str, float], kt_path: Path) -> NoReturn:
    sys.exit("disabled: _PARAM_MAP targets v14")


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
                        help="One Optuna study per regime on the train split, then v17 vs candidate on the test")
    parser.add_argument("--study-dir",   type=Path, default=None, dest="study_dir",
                        help="Optuna sqlite dir, required with --tune; resume-safe, keep it outside the repo")
    parser.add_argument("--regime",      choices=("in", "cold", "both"), default="both",
                        help="Tune one regime (train only) or both and score the test once")
    parser.add_argument("--apply",       action="store_true",
                        help="Disabled: exits non-zero. The tuner prints a Kotlin block instead")
    parser.add_argument("--split",       type=float, default=0.8,
                        help="Train fraction for tune/eval split")
    parser.add_argument("--trials",      type=int,   default=200,
                        help="Optuna trials per study, including trials already in --study-dir")
    parser.add_argument("--tune-stride", type=int,   default=2, dest="tune_stride",
                        help="Score every Nth train target per trial (speeds up ~Nx)")
    parser.add_argument("--min-hist",    type=int,   default=50, dest="min_hist")
    parser.add_argument("--stats",       action="store_true",
                        help="Bootstrap CI + Wilcoxon vs v14")
    args = parser.parse_args()

    if args.apply:
        apply_params({}, KT_PATH)   # exits: fail before any hours of tuning
    if args.tune and args.study_dir is None:
        parser.error("--tune needs --study-dir (Optuna sqlite dir, outside the repo)")
    random.seed(0)

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
        run_tune(
            events,
            train_frac=args.split,
            n_trials=args.trials,
            min_hist=args.min_hist,
            tune_stride=args.tune_stride,
            study_dir=args.study_dir,
            regime=args.regime,
        )


if __name__ == "__main__":
    main()

