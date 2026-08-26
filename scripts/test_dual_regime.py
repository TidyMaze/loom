import sys
sys.path.insert(0, 'scripts')
import json
import time
import math
import collections
import numpy as np
import optuna
import bench

optuna.logging.set_verbosity(optuna.logging.WARNING)

def test_dual_regime_model():
    print("=== Testing Dual-Regime (In-Session vs Cold-Start) Model ===")
    with open('usage_log.json') as f:
        events = json.load(f)

    events = sorted(events, key=lambda e: e['timestampMillis'])
    split = int(len(events) * 0.8)
    train_events = events[:split]
    test_events = events[split:]

    all_pkgs = list({e['packageName'] for e in events})

    # Evaluate baseline v15
    print("\n--- Baseline v15 on Held-out Test Set ---")
    r_v15_test = bench.evaluate(test_events, bench.score_v15, min_hist=0)
    print(f"v15 Baseline Test: @1={r_v15_test['@1']:.2f}% | @5={r_v15_test['@5']:.2f}% | @10={r_v15_test['@10']:.2f}% | MRR={r_v15_test['mrr']:.4f}")

    # Dual-Regime Scoring Function
    def score_dual_regime(events: list, now_hour: int, now_dow: int, now_ms: int,
                          target_ev: dict = None, p_in: dict = None, p_cold: dict = None) -> dict:
        if not events:
            return {}
        
        last_e = events[-1]
        sess_ms = 70_000
        in_session = (now_ms - last_e["timestampMillis"]) <= sess_ms
        
        p = p_in if in_session else p_cold
        return bench.score_v14(events, now_hour, now_dow, now_ms, target_ev, p)

    # Let's optimize p_in and p_cold separately with Optuna on train split!
    print("\n--- Optimizing In-Session Regime Parameters ---")
    # Filter in-session train events
    train_in_sess = []
    train_cold = []
    for i in range(1, len(train_events)):
        dt = train_events[i]["timestampMillis"] - train_events[i-1]["timestampMillis"]
        if dt <= 70_000:
            train_in_sess.append(train_events[i])
        else:
            train_cold.append(train_events[i])

    print(f"Train breakdown: {len(train_in_sess)} in-session ({len(train_in_sess)/len(train_events)*100:.1f}%), {len(train_cold)} cold-start ({len(train_cold)/len(train_events)*100:.1f}%)")

    # Fast tuning of in-session weights
    def obj_in(trial):
        p = dict(bench.V15)
        p["w_trans"] = trial.suggest_float("w_trans", 2.0, 20.0)
        p["w_trans2"] = trial.suggest_float("w_trans2", 2.0, 15.0)
        p["trans_smooth"] = trial.suggest_float("trans_smooth", 0.05, 1.5)
        p["w_rec"] = trial.suggest_float("w_rec", 0.5, 8.0)
        p["w_r8"] = trial.suggest_float("w_r8", 0.1, 2.0)
        p["w_r168"] = trial.suggest_float("w_r168", 0.1, 2.0)
        p["w_ctx"] = trial.suggest_float("w_ctx", 0.0, 1.0)
        p["self_pen"] = trial.suggest_float("self_pen", 0.0, 0.4)
        
        fn = lambda evs, h, d, t, target_ev=None: bench.score_v14(evs, h, d, t, target_ev, p)
        r = bench.evaluate(train_in_sess[::2], fn, min_hist=10)
        return r.get("mrr", 0.0)

    study_in = optuna.create_study(direction="maximize", sampler=optuna.samplers.TPESampler(seed=42))
    study_in.optimize(obj_in, n_trials=80, show_progress_bar=False)
    p_in_best = dict(bench.V15)
    p_in_best.update(study_in.best_params)
    print(f"Best In-Session MRR (Train): {study_in.best_value:.4f}")
    for k, v in study_in.best_params.items():
        print(f"  {k:<15} = {v:.4f}")

    # Fast tuning of cold-start weights
    print("\n--- Optimizing Cold-Start Regime Parameters ---")
    def obj_cold(trial):
        p = dict(bench.V15)
        p["w_ctx"] = trial.suggest_float("w_ctx", 0.5, 6.0)
        p["w_r8"] = trial.suggest_float("w_r8", 0.5, 8.0)
        p["w_r24"] = trial.suggest_float("w_r24", 0.5, 5.0)
        p["w_r168"] = trial.suggest_float("w_r168", 1.0, 10.0)
        p["w_rec"] = trial.suggest_float("w_rec", 0.5, 6.0)
        p["w_bat"] = trial.suggest_float("w_bat", 1.0, 10.0)
        p["w_cal"] = trial.suggest_float("w_cal", 0.5, 6.0)
        p["w_device"] = trial.suggest_float("w_device", 0.5, 5.0)
        p["w_sr"] = trial.suggest_float("w_sr", 0.5, 5.0)
        p["hour_sigma"] = trial.suggest_float("hour_sigma", 1.0, 3.5)
        
        fn = lambda evs, h, d, t, target_ev=None: bench.score_v14(evs, h, d, t, target_ev, p)
        r = bench.evaluate(train_cold[::2], fn, min_hist=10)
        return r.get("mrr", 0.0)

    study_cold = optuna.create_study(direction="maximize", sampler=optuna.samplers.TPESampler(seed=42))
    study_cold.optimize(obj_cold, n_trials=80, show_progress_bar=False)
    p_cold_best = dict(bench.V15)
    p_cold_best.update(study_cold.best_params)
    print(f"Best Cold-Start MRR (Train): {study_cold.best_value:.4f}")
    for k, v in study_cold.best_params.items():
        print(f"  {k:<15} = {v:.4f}")

    # Final Combined Evaluation on Test Split
    print("\n=== Evaluating Dual-Regime Model on Held-out Test Set ===")
    fn_dual = lambda evs, h, d, t, target_ev=None: score_dual_regime(evs, h, d, t, target_ev, p_in_best, p_cold_best)
    r_dual_test = bench.evaluate(test_events, fn_dual, min_hist=0)

    print(f"v15 Baseline Test : @1={r_v15_test['@1']:.2f}% | @5={r_v15_test['@5']:.2f}% | @10={r_v15_test['@10']:.2f}% | MRR={r_v15_test['mrr']:.4f}")
    print(f"Dual-Regime Test  : @1={r_dual_test['@1']:.2f}% | @5={r_dual_test['@5']:.2f}% | @10={r_dual_test['@10']:.2f}% | MRR={r_dual_test['mrr']:.4f}")

    lo, hi = bench.bootstrap_ci(r_v15_test['rr_list'], r_dual_test['rr_list'])
    p_val = bench.wilcoxon_p(r_v15_test['rr_list'], r_dual_test['rr_list'])
    delta_mrr = r_dual_test['mrr'] - r_v15_test['mrr']
    print(f"\nSignificance vs v15: Delta MRR={delta_mrr:+.4f} (95% CI: [{lo:+.4f}, {hi:+.4f}], p={p_val:.4e})")

if __name__ == '__main__':
    test_dual_regime_model()
