"""Unit tests for bench.py — run with: uv run --with pytest pytest scripts/test_bench.py -v"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import bench


# ─── burst collapse ──────────────────────────────────────────────────────────

def test_burst_collapse_drops_rapid_same_pkg():
    gap = bench.V14["burst_gap_ms"]
    evs = [
        {"packageName": "A", "timestampMillis": 0},
        {"packageName": "A", "timestampMillis": gap - 1},
        {"packageName": "A", "timestampMillis": gap + 1},
    ]
    out = bench._collapse_bursts(sorted(evs, key=lambda e: e["timestampMillis"]))
    assert len(out) == 2
    assert out[0]["timestampMillis"] == 0
    assert out[1]["timestampMillis"] == gap + 1


def test_burst_collapse_keeps_different_pkgs():
    evs = [
        {"packageName": "A", "timestampMillis": 0},
        {"packageName": "B", "timestampMillis": 1},
    ]
    assert len(bench._collapse_bursts(evs)) == 2


def test_burst_collapse_noop_when_gap_zero():
    evs = [
        {"packageName": "A", "timestampMillis": 0},
        {"packageName": "A", "timestampMillis": 1},
    ]
    assert len(bench._collapse_bursts(evs, burst_gap_ms=0)) == 2


# ─── _norm ───────────────────────────────────────────────────────────────────

def test_norm_empty():
    assert bench._norm({}) == {}


def test_norm_scales_max_to_one():
    n = bench._norm({"a": 2.0, "b": 4.0, "c": 1.0})
    assert n["b"] == 1.0
    assert abs(n["a"] - 0.5) < 1e-9


def test_norm_all_zero():
    n = bench._norm({"a": 0.0, "b": 0.0})
    assert n["a"] == 0.0


# ─── EMA analytical ──────────────────────────────────────────────────────────

def test_ema_analytical_matches_incremental():
    alpha = 0.15
    events = [
        {"packageName": "A", "timestampMillis": 1000},
        {"packageName": "B", "timestampMillis": 2000},
        {"packageName": "A", "timestampMillis": 3000},
        {"packageName": "C", "timestampMillis": 4000},
    ]
    all_pkgs = list({e["packageName"] for e in events})
    ema_ref = {p: 0.0 for p in all_pkgs}
    for e in events:
        for p in all_pkgs:
            ema_ref[p] *= (1 - alpha)
        ema_ref[e["packageName"]] += alpha

    ema_fast = bench._ema_analytical(events, alpha)
    for p in all_pkgs:
        assert abs(ema_ref[p] - ema_fast.get(p, 0.0)) < 1e-9, (
            f"{p}: ref={ema_ref[p]:.9f} fast={ema_fast.get(p,0):.9f}"
        )


# ─── evaluate ────────────────────────────────────────────────────────────────

def test_evaluate_no_lookahead():
    seen_sizes = []
    def spy_scorer(events, hour, dow, now_ms):
        seen_sizes.append(len(events))
        return {e["packageName"]: 1.0 for e in events}

    events = [
        {"packageName": f"app{i%5}", "timestampMillis": i*1_000,
         "hour": 10, "dayOfWeek": 1}
        for i in range(25)
    ]
    bench.evaluate(events, spy_scorer, min_hist=5)
    for j, size in enumerate(seen_sizes):
        assert size == j + 5, f"step {j}: saw {size}, expected {j+5}"


def test_evaluate_returns_expected_keys():
    events = [
        {"packageName": "A" if i%3==0 else "B",
         "timestampMillis": i*60_000, "hour": 10, "dayOfWeek": 1}
        for i in range(70)
    ]
    r = bench.evaluate(events, lambda evs,h,d,t: {e["packageName"]: 1.0 for e in evs}, min_hist=10)
    assert {"n","@1","@3","@5","@10","mrr","lift","rr_list"} <= set(r.keys())
    assert r["n"] > 0
    assert len(r["rr_list"]) == r["n"]


# ─── v14 scorer ──────────────────────────────────────────────────────────────

def test_v14_returns_all_apps():
    events = [
        {"packageName": "A", "timestampMillis": 0,       "hour": 10, "dayOfWeek": 1},
        {"packageName": "B", "timestampMillis": 60_000,  "hour": 10, "dayOfWeek": 1},
        {"packageName": "A", "timestampMillis": 120_000, "hour": 10, "dayOfWeek": 1},
    ]
    scores = bench.score_v14(events, now_hour=10, now_dow=1, now_ms=180_000)
    assert "A" in scores and "B" in scores


def test_v14_penalizes_last_app():
    sess = bench.V14["session_ms"]
    events = [
        {"packageName": "A", "timestampMillis": 0,      "hour": 10, "dayOfWeek": 1},
        {"packageName": "B", "timestampMillis": 60_000, "hour": 10, "dayOfWeek": 1},
    ]
    now_ms = 60_000 + sess // 2
    s_pen  = bench.score_v14(events, 10, 1, now_ms)
    s_nopen= bench.score_v14(events, 10, 1, now_ms, p={**bench.V14, "self_pen": 0.0})
    assert s_pen["B"] < s_nopen["B"]


def test_v14_recent_beats_old():
    now_ms = 1_000_000
    events = [
        {"packageName": "OLD", "timestampMillis": now_ms - 7*86_400_000, "hour": 10, "dayOfWeek": 1},
        {"packageName": "NEW", "timestampMillis": now_ms - 3_600_000,    "hour": 10, "dayOfWeek": 1},
    ]
    scores = bench.score_v14(events, 10, 1, now_ms, p={**bench.V14, "self_pen": 0.0})
    assert scores["NEW"] > scores["OLD"]
