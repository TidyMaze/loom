"""Unit tests for bench.py. Run with:
uv run --with pytest --with scipy --with optuna python3 -m pytest scripts/test_bench.py
Never `pytest scripts/`: test_dual_regime.py runs an Optuna experiment at import."""
import collections
import random
import sys
from pathlib import Path
from typing import Any, Callable

import pytest

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
    def spy_scorer(events, hour, dow, now_ms, target_ev=None):
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
    r = bench.evaluate(events, lambda evs,h,d,t,target=None: {e["packageName"]: 1.0 for e in evs}, min_hist=10)
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


# ─── harness fixtures ────────────────────────────────────────────────────────

Event = dict[str, Any]


def _synthetic_log(n: int = 200) -> list[Event]:
    """Both regimes present (20 s / 30 s gaps in-session, 600 s cold), all 12 apps seen early."""
    rng = random.Random(0)
    ts = 0
    out: list[Event] = []
    for i in range(n):
        ts += rng.choice([20_000, 30_000, 600_000])
        pkg = f"app{i}" if i < 12 else f"app{rng.randrange(12)}"
        out.append({"packageName": pkg, "timestampMillis": ts, "hour": (ts // 3_600_000) % 24,
                    "dayOfWeek": 1 + (ts // 86_400_000) % 7})
    return out


def _old_evaluate(events: list[Event], score_fn: Callable[..., dict[str, float]],
                  min_hist: int = 50) -> dict[str, Any]:
    """Verbatim copy of evaluate() before the harness fix, the reference for default behaviour."""
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
        scores = score_fn(history, target.get("hour", 0), target.get("dayOfWeek", 0) or 1,
                          target["timestampMillis"], target)
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
    return {"n": count, "@1": hits[1] / count * 100, "@3": hits[3] / count * 100,
            "@5": hits[5] / count * 100, "@10": hits[10] / count * 100, "mrr": mrr,
            "lift": mrr / random_mrr if random_mrr > 0 else 0, "rr_list": rr_list}


def _history_sizes(events: list[Event], **kwargs: Any) -> list[int]:
    seen: list[int] = []

    def spy(history: list[Event], hour: int, dow: int, now_ms: int,
            target_ev: Event | None = None) -> dict[str, float]:
        seen.append(len(history))
        return {}

    bench.evaluate(events, spy, **kwargs)
    return seen


# ─── evaluate: window, stride, select keep the full history ─────────────────

def test_evaluate_stride_does_not_thin_history() -> None:
    events = _synthetic_log(40)
    assert _history_sizes(events, min_hist=5, stride=3) == list(range(5, 40, 3))


def test_evaluate_window_scores_targets_with_full_history() -> None:
    events = _synthetic_log(60)
    assert _history_sizes(events, min_hist=5, start=30, end=36) == [30, 31, 32, 33, 34, 35]


def test_evaluate_start_never_goes_below_min_hist() -> None:
    events = _synthetic_log(40)
    assert _history_sizes(events, min_hist=10, start=3, end=13) == [10, 11, 12]


def test_evaluate_select_restricts_targets() -> None:
    events = _synthetic_log(80)
    targets: list[str] = []

    def spy(history: list[Event], hour: int, dow: int, now_ms: int,
            target_ev: Event | None = None) -> dict[str, float]:
        assert target_ev is not None
        targets.append(target_ev["packageName"])
        return {}

    def only_app3(history: list[Event], target: Event, i: int) -> bool:
        assert len(history) == i and target is events[i]
        return bool(target["packageName"] == "app3")

    r = bench.evaluate(events, spy, min_hist=10, select=only_app3)
    expected = sum(1 for e in events[10:] if e["packageName"] == "app3")
    assert expected > 0
    assert r["n"] == expected == len(r["rr_list"])
    assert set(targets) == {"app3"}


def test_evaluate_defaults_match_old_implementation() -> None:
    events = _synthetic_log(160)
    for fn in (bench.score_v16, bench.score_recency):
        old = _old_evaluate(events, fn, min_hist=50)
        new = bench.evaluate(events, fn, min_hist=50)
        for key in ("n", "@1", "@3", "@5", "@10", "mrr", "rr_list"):
            assert new[key] == old[key], f"{fn.__name__} {key}"


def test_evaluate_breaks_score_ties_in_sorted_package_order() -> None:
    pkgs = ["zz", "mm", "aa", "qq", "bb", "kk", "cc", "pp"]
    events: list[Event] = [{"packageName": pkgs[i % 8], "timestampMillis": i * 1_000, "hour": 10, "dayOfWeek": 1}
              for i in range(40)]
    r = bench.evaluate(events, lambda *a, **k: {}, min_hist=8)
    assert r["rr_list"] == [1 / (sorted(pkgs).index(e["packageName"]) + 1) for e in events[8:]]


# ─── regime helper (mirrors ScoreEngine.kt inSession: gap <= SESSION_MS) ────

def test_is_in_session_boundary_is_inclusive() -> None:
    history = [{"packageName": "A", "timestampMillis": 1_000}]
    assert bench.is_in_session(history, {"timestampMillis": 1_000 + 70_000})
    assert not bench.is_in_session(history, {"timestampMillis": 1_000 + 70_001})


def test_in_session_constant_is_what_scoreengine_ships() -> None:
    kt = (Path(__file__).parent.parent / "app/src/main/kotlin/com/yrolland/loom/ScoreEngine.kt").read_text()
    assert f"private const val SESSION_MS = {bench.IN_SESSION_MS:_}L" in kt


# ─── shipped precision ───────────────────────────────────────────────────────

def test_round_params_matches_shipped_precision() -> None:
    out = bench.round_params({"w_ctx": 0.9984, "hour_sigma": 2.5283, "session_ms": 70_000.4,
                              "burst_gap_ms": 25_000, "ctx3_min": 11.0})
    assert out == {"w_ctx": 1.0, "hour_sigma": 2.53, "session_ms": 70_000,
                   "burst_gap_ms": 25_000, "ctx3_min": 11}
    assert all(type(out[k]) is int for k in ("session_ms", "burst_gap_ms", "ctx3_min"))


def test_offline_params_are_rounded_and_notif_free() -> None:
    p = bench.offline_params(bench.V16_IN)
    assert p["w_notif"] == 0.0 and p["hour_sigma"] == 2.53 and p["w_trans"] == 8.28


def test_kotlin_names_exist_in_scoreengine() -> None:
    kt = (Path(__file__).parent.parent / "app/src/main/kotlin/com/yrolland/loom/ScoreEngine.kt").read_text()
    for regime in ("in", "cold"):
        block = bench.kotlin_block(regime, bench.offline_params(bench.BASE[regime]))
        assert block.count("\n") + 1 == len(bench.SPACES[regime])
        for line in block.splitlines():
            name = line.split()[3]
            assert f"private const val {name} =" in kt, name
    assert bench.kotlin_block("in", bench.offline_params(bench.V16_IN)).splitlines()[0] == \
        "private const val W_IN_TRANSITION = 8.28f"


# ─── acceptance rule (plan step 4) ───────────────────────────────────────────

def _result(rr: list[float], at1: float = 30.0, at5: float = 60.0) -> "bench.EvalResult":
    return {"rr_list": rr, "mrr": sum(rr) / len(rr), "@1": at1, "@5": at5}


def _accept(base_rr: list[float], cand_rr: list[float], flags: list[bool],
            d_at1: float = 0.0, d_at5: float = 0.0) -> "bench.Acceptance":
    return bench.acceptance(_result(base_rr), _result(cand_rr, 30.0 + d_at1, 60.0 + d_at5), flags)


def _clear_win() -> tuple[list[float], list[float], list[bool]]:
    base = [0.5] * 300
    cand = [0.5 + 0.01 + 0.005 * (i % 3) for i in range(300)]
    return base, cand, [i % 5 != 0 for i in range(300)]


def test_acceptance_accepts_a_clear_win() -> None:
    pytest.importorskip("scipy")
    a = _accept(*_clear_win())
    assert a.accepted and all(a.checks.values()), a
    assert a.ci_lo > 0 and a.p_value < 0.05


def test_acceptance_rejects_when_ci_lower_bound_is_not_positive() -> None:
    base, _, flags = _clear_win()
    cand = [0.5 + (0.1 if i % 2 else -0.1) for i in range(300)]
    a = _accept(base, cand, flags)
    assert not a.checks["ci_lo>0"] and not a.accepted


def test_acceptance_rejects_hit1_loss() -> None:
    pytest.importorskip("scipy")
    a = _accept(*_clear_win(), d_at1=-0.1)
    assert not a.checks["hit1>=0"] and not a.accepted


def test_acceptance_allows_small_hit5_loss_but_not_beyond_0_3pp() -> None:
    pytest.importorskip("scipy")
    assert _accept(*_clear_win(), d_at5=-0.3).checks["hit5>=-0.3pp"]
    a = _accept(*_clear_win(), d_at5=-0.5)
    assert not a.checks["hit5>=-0.3pp"] and not a.accepted


def test_acceptance_rejects_when_wilcoxon_p_is_not_below_0_05() -> None:
    pytest.importorskip("scipy")
    a = _accept([0.5] * 5, [0.6 + 0.02 * i for i in range(5)], [True] * 5)   # exact n=5 minimum p is 0.0625
    assert a.ci_lo > 0 and not a.checks["wilcoxon<0.05"] and not a.accepted


def test_acceptance_rejects_a_regression_in_one_regime() -> None:
    pytest.importorskip("scipy")
    flags = [i % 5 != 0 for i in range(300)]
    base = [0.5] * 300
    cand = [0.55 + 0.001 * (i % 3) if f else 0.5 - 0.006 for i, f in enumerate(flags)]
    a = _accept(base, cand, flags)
    assert a.ci_lo > 0 and a.d_mrr_cold < -0.005
    assert not a.checks["regime dMRR>=-0.005"] and not a.accepted


# ─── --apply is disabled ─────────────────────────────────────────────────────

def test_apply_params_is_disabled_and_leaves_the_file_alone(tmp_path: Path) -> None:
    kt = tmp_path / "ScoreEngine.kt"
    kt.write_text("private const val HOUR_SIGMA = 2.53f\n")
    with pytest.raises(SystemExit) as exc:
        bench.apply_params({"hour_sigma": 1.0}, kt)
    assert "disabled" in str(exc.value.code)
    assert kt.read_text() == "private const val HOUR_SIGMA = 2.53f\n"


def test_main_apply_exits_nonzero_before_loading_data(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(sys, "argv", ["bench.py", "--apply", "--data", "/nonexistent.json"])
    with pytest.raises(SystemExit) as exc:
        bench.main()
    assert "disabled" in str(exc.value.code)


def test_main_tune_requires_a_study_dir(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(sys, "argv", ["bench.py", "--tune"])
    with pytest.raises(SystemExit) as exc:
        bench.main()
    assert exc.value.code == 2


# ─── run_tune: seeded trial 0, resume, stale study ──────────────────────────

def test_run_tune_seeds_trial_zero_with_v16_and_resumes(tmp_path: Path) -> None:
    optuna = pytest.importorskip("optuna")
    events = _synthetic_log(200)
    split = int(0.8 * len(events))
    kw: dict[str, Any] = dict(min_hist=50, tune_stride=2, study_dir=tmp_path, regime="in")
    storage = f"sqlite:///{tmp_path / 'tune_in.db'}"

    bench.run_tune(events, n_trials=2, **kw)
    study = optuna.load_study(study_name="loom_in", storage=storage)
    assert [t.number for t in study.trials] == [0, 1]

    p = bench.offline_params(bench.V16_IN)
    plain = bench.evaluate(
        events, lambda h, hr, d, t, target=None: bench.score_v14(h, hr, d, t, target, p),
        min_hist=50, end=split, stride=2, select=lambda h, t, i: bench.is_in_session(h, t))
    assert study.trials[0].value == plain["mrr"]

    bench.run_tune(events, n_trials=2, **kw)
    assert len(optuna.load_study(study_name="loom_in", storage=storage).trials) == 2
    bench.run_tune(events, n_trials=3, **kw)
    assert [t.number for t in optuna.load_study(study_name="loom_in", storage=storage).trials] == [0, 1, 2]

    with pytest.raises(SystemExit) as exc:
        bench.run_tune(events[:-5], n_trials=3, **kw)
    assert "stale" in str(exc.value.code)


def test_run_tune_objective_scores_shipped_precision_without_notif(
        tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    pytest.importorskip("optuna")
    seen: list[dict[str, float]] = []
    real = bench.score_v14

    def spy(events: list[Event], hour: int, dow: int, now_ms: int,
            target_ev: Event | None = None, p: dict[str, float] | None = None) -> dict[str, float]:
        assert p is not None and target_ev is not None
        seen.append(p)
        return real(events, hour, dow, now_ms, target_ev, p)

    monkeypatch.setattr(bench, "score_v14", spy)
    bench.run_tune(_synthetic_log(200), n_trials=2, min_hist=50, tune_stride=2,
                   study_dir=tmp_path, regime="cold")
    assert seen
    assert all(p == bench.round_params(p) and p["w_notif"] == 0.0 for p in seen)
    assert {p["hour_sigma"] for p in seen} == {2.53}   # frozen, never searched


def test_run_tune_both_scores_test_window_once_and_keeps_v16_when_nothing_won(
        tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    pytest.importorskip("optuna")
    pytest.importorskip("scipy")
    events = _synthetic_log(200)
    best = bench.run_tune(events, n_trials=1, min_hist=50, tune_stride=2,
                          study_dir=tmp_path, regime="both")
    out = capsys.readouterr().out
    assert set(best) == {"in", "cold"}
    assert f"test targets n={len(events) - int(0.8 * len(events))}" in out
    assert "VERDICT: REJECT" in out
    counts = collections.Counter(line.split()[0] for line in out.splitlines() if line.startswith(("train:", "test:")))
    assert counts == {"train:": 1, "test:": 1}
