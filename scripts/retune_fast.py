import sys
sys.path.insert(0, 'scripts')
import json
import time
import math
import collections
import numpy as np
import optuna
import bench
from pathlib import Path

optuna.logging.set_verbosity(optuna.logging.WARNING)

def extract_features(events: list, all_pkgs: list, min_hist: int = 50):
    """
    Extract normalized feature tensor for each evaluation event:
    Shape: (N_eval, n_apps, K_features)
    Target app index: (N_eval,)
    """
    events = sorted(events, key=lambda e: e["timestampMillis"])
    n_apps = len(all_pkgs)
    pkg_to_idx = {pkg: i for i, pkg in enumerate(all_pkgs)}
    
    sess_ms = int(bench.V14["session_ms"])
    decay_hl = bench.V14["decay_hl"]
    hour_sigma = bench.V14["hour_sigma"]
    rec_h = bench.V14["recency_h"]
    ts_smooth = bench.V14["trans_smooth"]
    
    # Feature indices:
    # 0: ctx, 1: rec, 2: r8, 3: r24, 4: r168, 5: trans1, 6: trans2,
    # 7: audio, 8: device, 9: charging, 10: sr, 11: cal, 12: bat
    K = 13
    
    eval_indices = []
    targets = []
    
    # We will build X sequentially
    X_list = []
    
    sorted_evs = bench._collapse_bursts(events, burst_gap_ms=int(bench.V14["burst_gap_ms"]))
    ev_map = {e["timestampMillis"]: e for e in sorted_evs}
    
    # Transition tables: 1-gram and 2-gram
    trans1 = collections.defaultdict(lambda: collections.defaultdict(float))
    trans2 = collections.defaultdict(lambda: collections.defaultdict(float))
    
    by_pkg = collections.defaultdict(list)
    
    for i in range(len(sorted_evs)):
        curr = sorted_evs[i]
        now_ms = curr["timestampMillis"]
        now_hour = curr.get("hour", 0)
        now_dow = curr.get("dayOfWeek", 1) or 1
        target_pkg = curr["packageName"]
        
        if i >= min_hist and target_pkg in pkg_to_idx:
            # Calculate features at this point in time
            last_e = sorted_evs[i-1]
            in_session = (now_ms - last_e["timestampMillis"]) <= sess_ms
            
            # Transition score
            t1_scores = np.zeros(n_apps, dtype=np.float32)
            t2_scores = np.zeros(n_apps, dtype=np.float32)
            if in_session:
                row1 = trans1.get(last_e["packageName"], {})
                if row1:
                    d1 = sum(row1.values()) + ts_smooth * n_apps
                    for pkg, w in row1.items():
                        if pkg in pkg_to_idx:
                            t1_scores[pkg_to_idx[pkg]] = (w + ts_smooth) / d1
                
                if i >= 2:
                    pe = sorted_evs[i-2]
                    if last_e["timestampMillis"] - pe["timestampMillis"] <= sess_ms:
                        row2 = trans2.get((pe["packageName"], last_e["packageName"]), {})
                        if row2:
                            d2 = sum(row2.values()) + ts_smooth * n_apps
                            for pkg, w in row2.items():
                                if pkg in pkg_to_idx:
                                    t2_scores[pkg_to_idx[pkg]] = (w + ts_smooth) / d2
            
            # App stats
            f_mat = np.zeros((n_apps, K), dtype=np.float32)
            
            # Fill transition features
            f_mat[:, 5] = t1_scores
            f_mat[:, 6] = t2_scores
            
            # Fill ctx and recency
            for pkg, app_evs in by_pkg.items():
                if pkg not in pkg_to_idx:
                    continue
                p_idx = pkg_to_idx[pkg]
                
                td = hs = ds = 0.0
                last_app_ms = 0
                aud_m = aud_t = dev_m = dev_t = chg_m = chg_t = 0.0
                sr_m = cal_m = cal_t = bat_m = bat_t = 0.0
                
                for e in app_evs:
                    dec = bench._decay(e["timestampMillis"], now_ms, decay_hl)
                    td += dec
                    hs += bench._hm(bench._hd(e.get("hour", 0), now_hour), hour_sigma) * dec
                    ds += bench._dm(e.get("dayOfWeek", 1), now_dow) * dec
                    if e["timestampMillis"] > last_app_ms:
                        last_app_ms = e["timestampMillis"]
                
                # Ctx score
                f_mat[p_idx, 0] = (hs * ds / td) if td > 0 else 0.0
                
                # Recency scores
                hours_ago = (now_ms - last_app_ms) / 3_600_000.0 if last_app_ms > 0 else 9999.0
                f_mat[p_idx, 1] = math.exp(-hours_ago / rec_h)
                f_mat[p_idx, 2] = math.exp(-hours_ago / 8.0)
                f_mat[p_idx, 3] = math.exp(-hours_ago / 24.0)
                f_mat[p_idx, 4] = math.exp(-hours_ago / 168.0)
            
            # Max-normalize each feature column across apps
            for col in range(K):
                col_max = np.max(f_mat[:, col])
                if col_max > 0:
                    f_mat[:, col] /= col_max
            
            # Self penalty on last app if in session
            if in_session and last_e["packageName"] in pkg_to_idx:
                last_idx = pkg_to_idx[last_e["packageName"]]
                f_mat[last_idx, :] *= 0.35 # temporary soft damp
                
            X_list.append(f_mat)
            targets.append(pkg_to_idx[target_pkg])
            eval_indices.append(i)
        
        # Update history with curr event
        by_pkg[curr["packageName"]].append(curr)
        if i >= 1:
            prev = sorted_evs[i-1]
            if curr["timestampMillis"] - prev["timestampMillis"] <= sess_ms:
                w = (bench._decay(prev["timestampMillis"], now_ms, decay_hl) *
                     bench._hm(bench._hd(prev.get("hour", 0), now_hour), hour_sigma) *
                     bench._dm(prev.get("dayOfWeek", 1), now_dow))
                trans1[prev["packageName"]][curr["packageName"]] += w
                if i >= 2:
                    pe = sorted_evs[i-2]
                    if prev["timestampMillis"] - pe["timestampMillis"] <= sess_ms:
                        trans2[(pe["packageName"], prev["packageName"])][curr["packageName"]] += w

    X_tensor = np.array(X_list, dtype=np.float32)
    y_array = np.array(targets, dtype=np.int32)
    return X_tensor, y_array, eval_indices

def run_fast_optuna():
    print("=== Extracting Feature Tensors for Fast Optuna Retune ===")
    t0 = time.time()
    with open('usage_log.json') as f:
        events = json.load(f)

    events = sorted(events, key=lambda e: e['timestampMillis'])
    all_pkgs = list({e['packageName'] for e in events})
    
    split = int(len(events) * 0.8)
    train_events = events[:split]
    test_events = events[split:]
    
    print(f"Extracting train features ({len(train_events)} events)...")
    X_train, y_train, _ = extract_features(train_events, all_pkgs, min_hist=50)
    print(f"Train tensor shape: {X_train.shape} in {time.time() - t0:.1f}s")
    
    t1 = time.time()
    print(f"Extracting test features ({len(test_events)} events)...")
    X_test, y_test, _ = extract_features(test_events, all_pkgs, min_hist=0)
    print(f"Test tensor shape: {X_test.shape} in {time.time() - t1:.1f}s")

    N_train = len(y_train)
    N_test = len(y_test)

    # Optuna Vectorized Optimization
    def objective(trial):
        w = np.array([
            trial.suggest_float("w_ctx", 0.2, 5.0),
            trial.suggest_float("w_rec", 0.5, 6.0),
            trial.suggest_float("w_r8", 0.5, 6.0),
            trial.suggest_float("w_r24", 0.1, 3.0),
            trial.suggest_float("w_r168", 0.5, 6.0),
            trial.suggest_float("w_trans", 2.0, 16.0),
            trial.suggest_float("w_trans2", 1.0, 10.0),
            0.04, # audio
            1.91, # device
            1.42, # charging
            1.59, # sr
            3.32, # cal
            5.31  # bat
        ], dtype=np.float32)

        # Vectorized scoring (N, n_apps)
        scores = np.dot(X_train, w)
        target_scores = scores[np.arange(N_train), y_train, None]
        ranks = np.sum(scores > target_scores, axis=1) + 1
        mrr = float(np.mean(1.0 / ranks))
        return mrr

    print("\n--- Running 300 Optuna Vectorized Trials ---")
    study = optuna.create_study(direction="maximize", sampler=optuna.samplers.TPESampler(seed=42))
    t_opt = time.time()
    study.optimize(objective, n_trials=300, show_progress_bar=False)
    print(f"300 trials completed in {time.time() - t_opt:.2f}s!")

    best_p = study.best_params
    print(f"\nBest Train MRR: {study.best_value:.4f}")
    print("Optimal Weights:")
    for k, v in best_p.items():
        print(f"  {k:<15} = {v:.4f} (v14: {bench.V14.get(k, 0):.4f})")

    # Evaluate on held-out test tensor
    w_best = np.array([
        best_p["w_ctx"],
        best_p["w_rec"],
        best_p["w_r8"],
        best_p["w_r24"],
        best_p["w_r168"],
        best_p["w_trans"],
        best_p["w_trans2"],
        0.04, 1.91, 1.42, 1.59, 3.32, 5.31
    ], dtype=np.float32)

    w_v14 = np.array([
        bench.V14["w_ctx"],
        bench.V14["w_rec"],
        bench.V14["w_r8"],
        bench.V14["w_r24"],
        bench.V14["w_r168"],
        bench.V14["w_trans"],
        bench.V14["w_trans2"],
        0.04, 1.91, 1.42, 1.59, 3.32, 5.31
    ], dtype=np.float32)

    def eval_tensor(X, y, w):
        scores = np.dot(X, w)
        target_scores = scores[np.arange(len(y)), y, None]
        ranks = np.sum(scores > target_scores, axis=1) + 1
        r1 = np.mean(ranks <= 1) * 100
        r3 = np.mean(ranks <= 3) * 100
        r5 = np.mean(ranks <= 5) * 100
        r10 = np.mean(ranks <= 10) * 100
        mrr = np.mean(1.0 / ranks)
        rr_list = list(1.0 / ranks)
        return {"@1": r1, "@3": r3, "@5": r5, "@10": r10, "mrr": mrr, "rr_list": rr_list}

    res_v14 = eval_tensor(X_test, y_test, w_v14)
    res_v15 = eval_tensor(X_test, y_test, w_best)

    print("\n=== Held-out Test Set Performance ===")
    print(f"v14 Deployed Test: @1={res_v14['@1']:.2f}% | @5={res_v14['@5']:.2f}% | @10={res_v14['@10']:.2f}% | MRR={res_v14['mrr']:.4f}")
    print(f"v15 Retuned  Test: @1={res_v15['@1']:.2f}% | @5={res_v15['@5']:.2f}% | @10={res_v15['@10']:.2f}% | MRR={res_v15['mrr']:.4f}")

    lo, hi = bench.bootstrap_ci(res_v14["rr_list"], res_v15["rr_list"])
    p_val = bench.wilcoxon_p(res_v14["rr_list"], res_v15["rr_list"])
    delta_mrr = res_v15["mrr"] - res_v14["mrr"]

    print(f"\nSignificance: Delta MRR={delta_mrr:+.4f} (95% CI: [{lo:+.4f}, {hi:+.4f}], p={p_val:.4e})")

    if delta_mrr > 0:
        print("\nApplying v15 parameters to ScoreEngine.kt...")
        update_score_engine(best_p)

def update_score_engine(best_p: dict):
    se_path = Path("app/src/main/kotlin/com/yrolland/loom/ScoreEngine.kt")
    content = se_path.read_text()
    
    mapping = {
        "W_CONTEXT": f"{best_p['w_ctx']:.2f}f",
        "W_RECENCY": f"{best_p['w_rec']:.2f}f",
        "W_TRANSITION": f"{best_p['w_trans']:.2f}f",
        "W_TRANSITION_2": f"{best_p['w_trans2']:.2f}f",
        "W_REC_8H": f"{best_p['w_r8']:.2f}f",
        "W_REC_24H": f"{best_p['w_r24']:.2f}f",
        "W_REC_168H": f"{best_p['w_r168']:.2f}f",
    }
    
    for const_name, new_val in mapping.items():
        import re
        pattern = rf"(private const val {const_name}\s*=\s*)([^;\n]+)"
        content = re.sub(pattern, rf"\g<1>{new_val}", content)
        
    se_path.write_text(content)
    print(f"Updated {se_path} successfully!")

if __name__ == '__main__':
    run_fast_optuna()
