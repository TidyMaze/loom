"""
Loom — 10-algorithm experiment suite.
Time-series CV (no lookahead). Eval: @1/@3/@5/@10 + MRR.

All algorithms are Kotlin-portable (no external ML libs, O(events*apps) max).

Usage:
  uv run python3 scripts/experiment.py [--data /tmp/usage_log.json] [--tune] [--verbose]
"""

import argparse
import collections
import json
import math
import subprocess
import sys
import time
import itertools
from pathlib import Path


# ---------------------------------------------------------------------------
# Constants  (mirror ScoreEngine.kt deployed values)
# ---------------------------------------------------------------------------

HOUR_SIGMA      = 0.75
DECAY_HALF_LIFE = 7.0
RECENCY_HOURS   = 4.0
SESSION_MS      = 15 * 60 * 1000
DAY_MS          = 24 * 3600_000
WEEK_MS         = 7 * DAY_MS


# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------

def _hd(a, b):
    d = abs(a - b); return min(d, 24 - d)

def _hm(hour_dist, sigma=HOUR_SIGMA):
    return math.exp(-(hour_dist ** 2) / (2 * sigma * sigma))

def _dm(dow, now_dow):
    if dow == 0 or dow == now_dow:  return 1.0
    if (dow >= 6) == (now_dow >= 6): return 0.6
    return 0.2

def _decay(ts_ms, now_ms, hl=DECAY_HALF_LIFE):
    return 0.5 ** ((now_ms - ts_ms) / 86_400_000 / hl)

def _norm(d):
    if not d: return {}
    m = max(d.values())
    return {p: v / m for p, v in d.items()} if m > 0 else {p: 0.0 for p in d}

def _by_pkg(events):
    d = collections.defaultdict(list)
    for e in events: d[e["packageName"]].append(e)
    return d

def _sorted(events):
    return sorted(events, key=lambda e: e["timestampMillis"])


# ---------------------------------------------------------------------------
# 1. v3 — Deployed baseline
# ---------------------------------------------------------------------------

def score_v3(events, now_hour, now_dow, now_ms,
             W_CTX=1.5, W_REC=2.0, W_FREQ=0.2, W_TRANS=2.0, sess_ms=SESSION_MS):
    bp    = _by_pkg(events)
    total = sum(len(v) for v in bp.values()) or 1
    se    = _sorted(events)

    trans = collections.defaultdict(lambda: collections.defaultdict(int))
    for i in range(1, len(se)):
        p, c = se[i-1], se[i]
        if c["timestampMillis"] - p["timestampMillis"] <= sess_ms:
            trans[p["packageName"]][c["packageName"]] += 1

    last_e = se[-1] if se else None
    in_sess = last_e and (now_ms - last_e["timestampMillis"]) <= sess_ms
    row, denom = {}, 0.0
    if in_sess and last_e["packageName"] in trans:
        row   = dict(trans[last_e["packageName"]])
        denom = sum(row.values()) + 0.5 * len(bp)

    ctx_r, rec_r, fq_r, tr_r = {}, {}, {}, {}
    for pkg, evs in bp.items():
        td = hs = ds = last_ms = 0
        for e in evs:
            dec = _decay(e["timestampMillis"], now_ms)
            td += dec; hs += _hm(_hd(e["hour"], now_hour)) * dec
            ds += _dm(e.get("dayOfWeek", 0), now_dow) * dec
            if e["timestampMillis"] > last_ms: last_ms = e["timestampMillis"]
        ctx_r[pkg] = (hs * ds / td) if td > 0 else 0.0
        rec_r[pkg] = math.exp(-((now_ms - last_ms) / 3_600_000) / RECENCY_HOURS)
        fq_r[pkg]  = len(evs) / total
        tr_r[pkg]  = ((row.get(pkg, 0) + 0.5) / denom) if denom > 0 else 0.0

    cN, rN, fN, tN = _norm(ctx_r), _norm(rec_r), _norm(fq_r), _norm(tr_r)
    return {p: W_CTX*cN[p] + W_REC*rN[p] + W_FREQ*fN[p] + W_TRANS*tN[p] for p in bp}


# ---------------------------------------------------------------------------
# 2. Tuned weights — grid-search optimal blend
# ---------------------------------------------------------------------------

_V4_WEIGHTS = {"W_CTX": 1.0, "W_REC": 3.0, "W_FREQ": 0.1, "W_TRANS": 3.0}

def score_v4(events, now_hour, now_dow, now_ms):
    return score_v3(events, now_hour, now_dow, now_ms, **_V4_WEIGHTS)

def tune_v4(events, verbose=False):
    se = _sorted(events)
    all_pkgs = list({e["packageName"] for e in se})
    MIN_HIST = 30
    grid = list(itertools.product(
        [0.5, 1.0, 1.5, 2.0, 3.0],
        [1.0, 2.0, 3.0, 4.0, 5.0],
        [0.0, 0.1, 0.2, 0.5],
        [0.5, 1.5, 2.5, 3.5, 4.5],
    ))
    if verbose:
        print(f"  Tuning v4: {len(grid)} combos × {len(se)-MIN_HIST} eval points…", flush=True)

    best, best_p = -1, None
    for wc, wr, wf, wt in grid:
        h1 = h3 = n = 0
        for i in range(MIN_HIST, len(se)):
            t = se[i]
            s = score_v3(se[:i], t["hour"], t.get("dayOfWeek",0) or 1, t["timestampMillis"],
                         W_CTX=wc, W_REC=wr, W_FREQ=wf, W_TRANS=wt)
            r = sorted(all_pkgs, key=lambda p: s.get(p,0.0), reverse=True)
            h1 += r[0] == t["packageName"]
            h3 += t["packageName"] in r[:3]
            n  += 1
        score_c = h1/n + 0.3*h3/n
        if score_c > best:
            best, best_p = score_c, (wc, wr, wf, wt)

    wc, wr, wf, wt = best_p
    if verbose:
        print(f"  Best: W_CTX={wc} W_REC={wr} W_FREQ={wf} W_TRANS={wt}")
    global _V4_WEIGHTS
    _V4_WEIGHTS = {"W_CTX": wc, "W_REC": wr, "W_FREQ": wf, "W_TRANS": wt}
    return best_p


# ---------------------------------------------------------------------------
# 3. Periodicity — yesterday + last-week same-slot signals
# ---------------------------------------------------------------------------

def score_v5(events, now_hour, now_dow, now_ms):
    base = score_v3(events, now_hour, now_dow, now_ms)
    bp   = _by_pkg(events)
    WIN  = 3 * 3600_000  # ±3h

    yd_r, wk_r = {}, {}
    for pkg, evs in bp.items():
        yd = wk = 0.0
        for e in evs:
            hm  = _hm(_hd(e["hour"], now_hour), sigma=1.0)
            age = now_ms - e["timestampMillis"]
            if DAY_MS - WIN <= age <= DAY_MS + WIN:
                yd += hm
            if WEEK_MS - WIN <= age <= WEEK_MS + WIN:
                wk += hm * _dm(e.get("dayOfWeek", 0), now_dow)
        yd_r[pkg] = yd
        wk_r[pkg] = wk

    bN  = _norm(base)
    ydN = _norm(yd_r)
    wkN = _norm(wk_r)
    all_p = set(base) | set(yd_r)
    return {p: bN.get(p,0.0) + 1.5*ydN.get(p,0.0) + 2.0*wkN.get(p,0.0) for p in all_p}


# ---------------------------------------------------------------------------
# 4. Bigram Markov — P(next | prev, context) with decay
# ---------------------------------------------------------------------------

def score_v6(events, now_hour, now_dow, now_ms, sess_ms=30*60*1000):
    bp = _by_pkg(events)
    se = _sorted(events)

    bigrams  = collections.defaultdict(lambda: collections.defaultdict(float))
    unigrams = collections.defaultdict(lambda: collections.defaultdict(float))

    for i in range(1, len(se)):
        p1, c = se[i-1], se[i]
        if c["timestampMillis"] - p1["timestampMillis"] > sess_ms: continue
        w = _decay(p1["timestampMillis"], now_ms) * _hm(_hd(p1["hour"], now_hour)) * _dm(p1.get("dayOfWeek",0), now_dow)
        unigrams[p1["packageName"]][c["packageName"]] += w
        if i >= 2:
            p2 = se[i-2]
            if p1["timestampMillis"] - p2["timestampMillis"] <= sess_ms:
                bigrams[(p2["packageName"], p1["packageName"])][c["packageName"]] += w

    last_e = se[-1] if se else None
    in_sess = last_e and (now_ms - last_e["timestampMillis"]) <= sess_ms

    trans_scores = collections.defaultdict(float)
    if in_sess and last_e:
        prev2 = se[-2] if len(se) >= 2 and (last_e["timestampMillis"] - se[-2]["timestampMillis"]) <= sess_ms else None
        key   = ((prev2["packageName"] if prev2 else None), last_e["packageName"])
        row   = bigrams.get(key, {}) if key[0] else {}
        if not row:
            row = unigrams.get(last_e["packageName"], {})
        if row:
            denom = sum(row.values()) + 0.5 * len(bp)
            for pkg in bp:
                trans_scores[pkg] = (row.get(pkg, 0) + 0.5) / denom

    ctx_r, rec_r = {}, {}
    for pkg, evs in bp.items():
        td = hs = ds = 0.0; last_ms = 0
        for e in evs:
            dec = _decay(e["timestampMillis"], now_ms)
            td += dec; hs += _hm(_hd(e["hour"], now_hour)) * dec
            ds += _dm(e.get("dayOfWeek",0), now_dow) * dec
            if e["timestampMillis"] > last_ms: last_ms = e["timestampMillis"]
        ctx_r[pkg] = (hs * ds / td) if td > 0 else 0.0
        rec_r[pkg] = math.exp(-((now_ms - last_ms) / 3_600_000) / RECENCY_HOURS)

    cN = _norm(ctx_r); rN = _norm(rec_r); tN = _norm(trans_scores)
    return {p: 1.5*cN.get(p,0.0) + 2.0*rN.get(p,0.0) + 3.5*tN.get(p,0.0) for p in bp}


# ---------------------------------------------------------------------------
# 5. Context kNN — vote by K nearest past moments
# ---------------------------------------------------------------------------

def score_v7(events, now_hour, now_dow, now_ms, K=15):
    if not events: return {}
    scored = []
    for e in events:
        sim = _hm(_hd(e["hour"], now_hour), sigma=1.5) * _dm(e.get("dayOfWeek",0), now_dow) * _decay(e["timestampMillis"], now_ms)
        scored.append((sim, e["packageName"]))
    scored.sort(reverse=True)
    votes = collections.defaultdict(float)
    for sim, pkg in scored[:K]:
        votes[pkg] += sim
    return dict(votes)


# ---------------------------------------------------------------------------
# 6. Time-slot habits — 8 day-parts × per-slot distribution
# ---------------------------------------------------------------------------

SLOTS = [
    (0,  5,  "night"),
    (5,  8,  "dawn"),
    (8,  11, "morning"),
    (11, 14, "midday"),
    (14, 17, "afternoon"),
    (17, 20, "evening"),
    (20, 22, "night2"),
    (22, 24, "late"),
]

def _slot(hour):
    for lo, hi, name in SLOTS:
        if lo <= hour < hi: return name
    return "late"

def score_v8_slots(events, now_hour, now_dow, now_ms):
    now_slot = _slot(now_hour)
    bp = _by_pkg(events)

    slot_r, rec_r = {}, {}
    for pkg, evs in bp.items():
        slot_w = last_ms = 0.0
        for e in evs:
            if _slot(e["hour"]) == now_slot:
                dm  = _dm(e.get("dayOfWeek",0), now_dow)
                dec = _decay(e["timestampMillis"], now_ms)
                slot_w += dm * dec
            if e["timestampMillis"] > last_ms: last_ms = e["timestampMillis"]
        slot_r[pkg] = slot_w
        rec_r[pkg]  = math.exp(-((now_ms - last_ms) / 3_600_000) / RECENCY_HOURS)

    sN = _norm(slot_r); rN = _norm(rec_r)
    return {p: 3.0*sN.get(p,0.0) + 1.5*rN.get(p,0.0) for p in bp}


# ---------------------------------------------------------------------------
# 7. Session opener vs continuation — split model
# ---------------------------------------------------------------------------

GAP_OPENER_MS = 30 * 60 * 1000  # 30 min gap → new session

def score_v9_session(events, now_hour, now_dow, now_ms):
    se = _sorted(events)
    bp = _by_pkg(events)

    last_e = se[-1] if se else None
    is_opener = (not last_e) or (now_ms - last_e["timestampMillis"]) > GAP_OPENER_MS

    if is_opener:
        # opener: weight by context + periodicity (habit-driven)
        ctx_r, yd_r = {}, {}
        WIN = 3 * 3600_000
        for pkg, evs in bp.items():
            td = hs = ds = 0.0; yd = 0.0
            for e in evs:
                dec = _decay(e["timestampMillis"], now_ms)
                td += dec; hs += _hm(_hd(e["hour"], now_hour)) * dec
                ds += _dm(e.get("dayOfWeek",0), now_dow) * dec
                age = now_ms - e["timestampMillis"]
                if DAY_MS - WIN <= age <= DAY_MS + WIN:
                    yd += _hm(_hd(e["hour"], now_hour), sigma=1.0)
            ctx_r[pkg] = (hs * ds / td) if td > 0 else 0.0
            yd_r[pkg]  = yd
        cN = _norm(ctx_r); yN = _norm(yd_r)
        return {p: 2.0*cN.get(p,0.0) + 2.5*yN.get(p,0.0) for p in bp}
    else:
        # continuation: weight by transitions (session-driven)
        trans = collections.defaultdict(lambda: collections.defaultdict(int))
        for i in range(1, len(se)):
            p, c = se[i-1], se[i]
            if c["timestampMillis"] - p["timestampMillis"] <= GAP_OPENER_MS:
                trans[p["packageName"]][c["packageName"]] += 1
        row   = dict(trans.get(last_e["packageName"], {}))
        denom = sum(row.values()) + 0.5 * len(bp) if row else 0
        tr_r  = {}
        rec_r = {}
        for pkg, evs in bp.items():
            last_ms = max(e["timestampMillis"] for e in evs)
            tr_r[pkg]  = ((row.get(pkg, 0) + 0.5) / denom) if denom > 0 else 0.0
            rec_r[pkg] = math.exp(-((now_ms - last_ms) / 3_600_000) / RECENCY_HOURS)
        tN = _norm(tr_r); rN = _norm(rec_r)
        return {p: 4.0*tN.get(p,0.0) + 2.0*rN.get(p,0.0) for p in bp}


# ---------------------------------------------------------------------------
# 8. EMA frequency — exponential moving average of usage rate
# ---------------------------------------------------------------------------

EMA_ALPHA = 0.15   # higher = more reactive to recent changes

def score_v10_ema(events, now_hour, now_dow, now_ms):
    """EMA-based frequency: recency-aware count that decays recent silence."""
    se = _sorted(events)
    bp = _by_pkg(events)

    # EMA: process events in order, update each app's EMA score
    ema = collections.defaultdict(float)
    all_pkgs = list(bp.keys())
    for e in se:
        # decay all apps, then bump the launched one
        for p in all_pkgs:
            ema[p] *= (1 - EMA_ALPHA)
        ema[e["packageName"]] += EMA_ALPHA

    ctx_r, rec_r = {}, {}
    for pkg, evs in bp.items():
        td = hs = ds = 0.0; last_ms = 0
        for e in evs:
            dec = _decay(e["timestampMillis"], now_ms)
            td += dec; hs += _hm(_hd(e["hour"], now_hour)) * dec
            ds += _dm(e.get("dayOfWeek",0), now_dow) * dec
            if e["timestampMillis"] > last_ms: last_ms = e["timestampMillis"]
        ctx_r[pkg] = (hs * ds / td) if td > 0 else 0.0
        rec_r[pkg] = math.exp(-((now_ms - last_ms) / 3_600_000) / RECENCY_HOURS)

    cN = _norm(ctx_r); rN = _norm(rec_r); eN = _norm(dict(ema))
    return {p: 1.5*cN.get(p,0.0) + 2.0*rN.get(p,0.0) + 2.0*eN.get(p,0.0) for p in bp}


# ---------------------------------------------------------------------------
# 9. Multi-resolution context — narrow σ + wide σ blend
# ---------------------------------------------------------------------------

def score_v11_multires(events, now_hour, now_dow, now_ms):
    """Blend narrow (σ=0.5) + broad (σ=2.0) context windows."""
    bp = _by_pkg(events)

    narrow_r, broad_r, rec_r = {}, {}, {}
    for pkg, evs in bp.items():
        td_n = hs_n = ds_n = 0.0
        td_b = hs_b = ds_b = 0.0
        last_ms = 0
        for e in evs:
            dec = _decay(e["timestampMillis"], now_ms)
            hd  = _hd(e["hour"], now_hour)
            dm  = _dm(e.get("dayOfWeek",0), now_dow)
            # narrow
            hm_n = _hm(hd, sigma=0.5)
            td_n += dec; hs_n += hm_n * dec; ds_n += dm * dec
            # broad
            hm_b = _hm(hd, sigma=2.0)
            td_b += dec; hs_b += hm_b * dec; ds_b += dm * dec
            if e["timestampMillis"] > last_ms: last_ms = e["timestampMillis"]
        narrow_r[pkg] = (hs_n * ds_n / td_n) if td_n > 0 else 0.0
        broad_r[pkg]  = (hs_b * ds_b / td_b) if td_b > 0 else 0.0
        rec_r[pkg]    = math.exp(-((now_ms - last_ms) / 3_600_000) / RECENCY_HOURS)

    nN = _norm(narrow_r); bN = _norm(broad_r); rN = _norm(rec_r)
    return {p: 2.0*nN.get(p,0.0) + 1.0*bN.get(p,0.0) + 2.0*rN.get(p,0.0) for p in bp}


# ---------------------------------------------------------------------------
# 10. RRF ensemble — combine best individual models
# ---------------------------------------------------------------------------

def score_v12_rrf(events, now_hour, now_dow, now_ms):
    """Reciprocal Rank Fusion of v3, v5 (periodicity), v6 (bigram), v9 (session-aware)."""
    fns = [score_v3, score_v5, score_v6, score_v9_session]
    all_pkgs = list({e["packageName"] for e in events})
    rrf = collections.defaultdict(float)
    k = 60
    for fn in fns:
        s      = fn(events, now_hour, now_dow, now_ms)
        ranked = sorted(all_pkgs, key=lambda p: s.get(p, 0.0), reverse=True)
        for rank, pkg in enumerate(ranked, 1):
            rrf[pkg] += 1.0 / (k + rank)
    return dict(rrf)


# ---------------------------------------------------------------------------
# Evaluation  (time-series CV, no lookahead)
# ---------------------------------------------------------------------------

MODELS = [
    ("1  v3 deployed",        score_v3),
    ("2  tuned weights",      score_v4),
    ("3  periodicity",        score_v5),
    ("4  bigram Markov",      score_v6),
    ("5  context kNN",        score_v7),
    ("6  time-slot habits",   score_v8_slots),
    ("7  session split",      score_v9_session),
    ("8  EMA frequency",      score_v10_ema),
    ("9  multi-res context",  score_v11_multires),
    ("10 RRF ensemble",       score_v12_rrf),
]


def evaluate(events, score_fn, min_hist=10):
    events    = sorted(events, key=lambda e: e["timestampMillis"])
    all_pkgs  = list({e["packageName"] for e in events})
    n_apps    = len(all_pkgs)
    hits      = {1: 0, 3: 0, 5: 0, 10: 0}
    rr_sum    = 0.0
    count     = 0

    for i, t in enumerate(events):
        if i < min_hist: continue
        s      = score_fn(events[:i], t["hour"], t.get("dayOfWeek", 0) or 1, t["timestampMillis"])
        ranked = sorted(all_pkgs, key=lambda p: s.get(p, 0.0), reverse=True)
        for k in hits:
            if t["packageName"] in ranked[:k]: hits[k] += 1
        rr_sum += 1.0 / (ranked.index(t["packageName"]) + 1)
        count  += 1

    if count == 0: return {}
    random_mrr = sum(1 / r for r in range(1, n_apps + 1)) / n_apps
    mrr = rr_sum / count
    return {
        "n":    count,
        "@1":   hits[1]  / count * 100,
        "@3":   hits[3]  / count * 100,
        "@5":   hits[5]  / count * 100,
        "@10":  hits[10] / count * 100,
        "mrr":  mrr,
        "lift": mrr / random_mrr,
    }


def print_results(results, baseline_key="1  v3 deployed"):
    base = next((r for n, r in results if baseline_key in n), results[0][1])
    hdr  = f"{'#  Model':<24}  {'@1':>6}  {'@3':>6}  {'@5':>6}  {'@10':>6}  {'MRR':>6}  {'lift':>5}  {'Δ@1':>5}  {'Δ@5':>5}"
    print("\n" + hdr)
    print("-" * len(hdr))

    best_at1 = max(r["@1"] for _, r in results)
    for name, r in results:
        d1  = r["@1"] - base["@1"]
        d5  = r["@5"] - base["@5"]
        top = " ◀" if r["@1"] == best_at1 else ""
        print(
            f"{name:<24}  {r['@1']:>5.1f}%  {r['@3']:>5.1f}%  {r['@5']:>5.1f}%  "
            f"{r['@10']:>5.1f}%  {r['mrr']:>6.4f}  {r['lift']:>4.2f}x  "
            f"{d1:>+5.1f}  {d5:>+5.1f}" + top
        )


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data",    default="/tmp/usage_log.json")
    parser.add_argument("--tune",    action="store_true")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    events = json.loads(Path(args.data).read_text())
    n_apps = len({e["packageName"] for e in events})
    print(f"{len(events)} events  |  {n_apps} apps")

    if args.tune:
        print("\nTuning v4 blend weights (grid search)…")
        tune_v4(events, verbose=True)
    print(f"  v4 weights: {_V4_WEIGHTS}")

    results = []
    for name, fn in MODELS:
        t0 = time.time()
        r  = evaluate(events, fn)
        dt = time.time() - t0
        if args.verbose:
            print(f"  {name}: {dt:.1f}s")
        results.append((name, r))
        print(f"  [{name}] @1={r['@1']:.1f}% @5={r['@5']:.1f}% MRR={r['mrr']:.4f}", flush=True)

    print_results(results)


if __name__ == "__main__":
    main()
