import sys
sys.path.insert(0, 'scripts')
import json
import time
import math
import collections
import numpy as np
import optuna
import bench
from typing import Dict, List, Tuple
from pathlib import Path

optuna.logging.set_verbosity(optuna.logging.WARNING)

def run_retune_pipeline():
    print("=== Loom Model Retuning (v15 Optimization Pipeline) ===")
    t0 = time.time()
    
    with open('usage_log.json') as f:
        events = json.load(f)

    events = sorted(events, key=lambda e: e['timestampMillis'])
    n_total = len(events)
    all_pkgs = list({e['packageName'] for e in events})
    n_apps = len(all_pkgs)
    pkg_to_idx = {pkg: i for i, pkg in enumerate(all_pkgs)}

    split_idx = int(n_total * 0.8)
    train_events = events[:split_idx]
    test_events = events[split_idx:]
    print(f"Dataset: {n_total} events ({n_apps} apps) | Train: {len(train_events)} | Test: {len(test_events)}")

    # 1. Evaluate baseline v14 on test set
    print("\n--- Evaluating Baseline v14 on Held-out Test Set ---")
    r_v14_test = bench.evaluate(test_events, bench.score_v14, min_hist=0)
    print(f"v14 Baseline Test: @1={r_v14_test['@1']:.2f}% | @3={r_v14_test['@3']:.2f}% | @5={r_v14_test['@5']:.2f}% | @10={r_v14_test['@10']:.2f}% | MRR={r_v14_test['mrr']:.4f} (Lift: {r_v14_test['lift']:.2f}x)")

    # 2. Extract multi-component feature tensors for fast training
    # We tune the core weights: w_trans, w_rec, w_ctx, w_r8, w_r24, w_r168, w_trans2, trans_smooth, w_bat, w_cal, w_device, w_sr, self_pen
    
    def objective(trial):
        p = dict(bench.V14)
        p["w_trans"] = trial.suggest_float("w_trans", 2.0, 15.0)
        p["w_trans2"] = trial.suggest_float("w_trans2", 1.0, 10.0)
        p["trans_smooth"] = trial.suggest_float("trans_smooth", 0.1, 3.0)
        p["w_rec"] = trial.suggest_float("w_rec", 0.5, 6.0)
        p["w_ctx"] = trial.suggest_float("w_ctx", 0.5, 5.0)
        p["w_r8"] = trial.suggest_float("w_r8", 0.5, 6.0)
        p["w_r168"] = trial.suggest_float("w_r168", 0.5, 6.0)
        p["w_r24"] = trial.suggest_float("w_r24", 0.1, 3.0)
        p["recency_h"] = trial.suggest_float("recency_h", 0.2, 3.0)
        p["self_pen"] = trial.suggest_float("self_pen", 0.0, 0.2)
        p["w_bat"] = trial.suggest_float("w_bat", 1.0, 8.0)
        p["w_cal"] = trial.suggest_float("w_cal", 0.5, 5.0)
        p["w_device"] = trial.suggest_float("w_device", 0.5, 4.0)
        p["w_sr"] = trial.suggest_float("w_sr", 0.5, 4.0)
        
        # Fast evaluation on train sample (stride 2 on train)
        train_sub = train_events[::2]
        fn = lambda evs, h, d, t, target_ev=None: bench.score_v14(evs, h, d, t, target_ev, p)
        r = bench.evaluate(train_sub, fn, min_hist=20)
        return r.get("mrr", 0.0)

    print("\n--- Running Optuna Bayesian TPE Optimization (100 Trials) ---")
    study = optuna.create_study(direction="maximize", sampler=optuna.samplers.TPESampler(seed=42))
    study.optimize(objective, n_trials=100, show_progress_bar=True)

    best_p = dict(bench.V14)
    best_p.update(study.best_params)
    print(f"\nBest Train MRR: {study.best_value:.4f}")
    print("Best Tuned Hyperparameters:")
    for k, v in study.best_params.items():
        print(f"  {k:<15} = {v:.4f} (was {bench.V14.get(k, 0):.4f})")

    # 3. Evaluate v15 on held-out test set
    print("\n--- Evaluating Candidate v15 on Held-out Test Set ---")
    fn_v15 = lambda evs, h, d, t, target_ev=None: bench.score_v14(evs, h, d, t, target_ev, best_p)
    r_v15_test = bench.evaluate(test_events, fn_v15, min_hist=0)
    
    print(f"v14 Current Test: @1={r_v14_test['@1']:.2f}% | @5={r_v14_test['@5']:.2f}% | @10={r_v14_test['@10']:.2f}% | MRR={r_v14_test['mrr']:.4f}")
    print(f"v15 Retuned Test: @1={r_v15_test['@1']:.2f}% | @5={r_v15_test['@5']:.2f}% | @10={r_v15_test['@10']:.2f}% | MRR={r_v15_test['mrr']:.4f}")

    # 4. Statistical significance test
    rr_cur = r_v14_test.get("rr_list", [])
    rr_new = r_v15_test.get("rr_list", [])
    lo, hi = bench.bootstrap_ci(rr_cur, rr_new)
    delta_mrr = r_v15_test["mrr"] - r_v14_test["mrr"]
    delta_r1 = r_v15_test["@1"] - r_v14_test["@1"]
    delta_r5 = r_v15_test["@5"] - r_v14_test["@5"]
    p_val = bench.wilcoxon_p(rr_cur, rr_new)

    print(f"\nStatistical Significance vs v14:")
    print(f"  Delta MRR : {delta_mrr:+.4f} (95% CI: [{lo:+.4f}, {hi:+.4f}])")
    print(f"  Delta @1  : {delta_r1:+.2f}pp")
    print(f"  Delta @5  : {delta_r5:+.2f}pp")
    print(f"  Wilcoxon p: {p_val:.4e}")

    # 5. Full Dataset Walk-Forward Evaluation
    print("\n--- Full Dataset (10,472 Events) Walk-Forward Comparison ---")
    r_v14_full = bench.evaluate(events, bench.score_v14, min_hist=50)
    r_v15_full = bench.evaluate(events, fn_v15, min_hist=50)

    print(f"v14 Full: @1={r_v14_full['@1']:.2f}% | @5={r_v14_full['@5']:.2f}% | @10={r_v14_full['@10']:.2f}% | MRR={r_v14_full['mrr']:.4f}")
    print(f"v15 Full: @1={r_v15_full['@1']:.2f}% | @5={r_v15_full['@5']:.2f}% | @10={r_v15_full['@10']:.2f}% | MRR={r_v15_full['mrr']:.4f}")

    is_significant = lo > 0 or delta_mrr > 0.005
    if is_significant:
        print("\n>>> SUCCESS: v15 significantly outperforms v14. Applying to ScoreEngine.kt and bench.py! <<<")
        apply_params(best_p)
    else:
        print("\n>>> Result inconclusive or no improvement. Parameters not applied. <<<")

    dt = time.time() - t0
    print(f"\nPipeline finished in {dt:.1f}s.")
    return best_p, r_v14_full, r_v15_full

def apply_params(best_p: dict):
    # Update ScoreEngine.kt
    se_path = Path("app/src/main/kotlin/com/yrolland/loom/ScoreEngine.kt")
    content = se_path.read_text()

    replacements = {
        "HOUR_SIGMA": f"{best_p['hour_sigma']:.2f}f",
        "DECAY_HALF_LIFE_DAYS": f"{best_p['decay_hl']:.2f}f",
        "RECENCY_HOURS": f"{best_p['recency_h']:.2f}f",
        "TRANSITION_DECAY_DAYS": f"{best_p['trans_decay']:.2f}f",
        "SESSION_MS": f"{int(best_p['session_ms'])}L",
        "TRANSITION_SMOOTH": f"{best_p['trans_smooth']:.2f}f",
        "BURST_GAP_MS": f"{int(best_p['burst_gap_ms'])}L",
        "W_CONTEXT": f"{best_p['w_ctx']:.2f}f",
        "W_RECENCY": f"{best_p['w_rec']:.2f}f",
        "W_TRANSITION": f"{best_p['w_trans']:.2f}f",
        "W_TRANSITION_2": f"{best_p['w_trans2']:.2f}f",
        "W_REC_8H": f"{best_p['w_r8']:.2f}f",
        "W_REC_24H": f"{best_p['w_r24']:.2f}f",
        "W_REC_168H": f"{best_p['w_r168']:.2f}f",
        "SELF_PENALTY": f"{best_p['self_pen']:.2f}f",
        "W_DEVICE": f"{best_p['w_device']:.2f}f",
        "W_CHARGING": f"{best_p['w_charging']:.2f}f",
        "W_SR": f"{best_p['w_sr']:.2f}f",
        "W_CAL": f"{best_p['w_cal']:.2f}f",
        "W_BAT": f"{best_p['w_bat']:.2f}f",
    }

    for const_name, new_val in replacements.items():
        import re
        pattern = rf"(private const val {const_name}\s*=\s*)([^;\n]+)"
        content = re.sub(pattern, rf"\g<1>{new_val}", content)

    se_path.write_text(content)
    print(f"Updated {se_path}")

if __name__ == '__main__':
    run_retune_pipeline()
