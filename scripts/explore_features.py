import sys
sys.path.insert(0, 'scripts')
import json
import time
import math
import collections
import numpy as np
from sklearn.decomposition import TruncatedSVD
import bench

def explore_architectures():
    print("=== Exploration of Next-Gen Loom Architectures ===")
    with open('usage_log.json') as f:
        events = json.load(f)

    events = sorted(events, key=lambda e: e['timestampMillis'])
    all_pkgs = list({e['packageName'] for e in events})
    n_apps = len(all_pkgs)
    pkg_to_idx = {pkg: i for i, pkg in enumerate(all_pkgs)}

    split = int(len(events) * 0.8)
    train_events = events[:split]
    test_events = events[split:]
    print(f"Total: {len(events)} events | Train: {len(train_events)} | Test: {len(test_events)}")

    # 1. Compute App2Vec Cosine Similarities on Train
    cooc_mat = np.zeros((n_apps, n_apps), dtype=np.float32)
    for i in range(1, len(train_events)):
        p1 = train_events[i-1]['packageName']
        p2 = train_events[i]['packageName']
        dt = train_events[i]['timestampMillis'] - train_events[i-1]['timestampMillis']
        if dt <= 120_000 and p1 in pkg_to_idx and p2 in pkg_to_idx and p1 != p2:
            i1, i2 = pkg_to_idx[p1], pkg_to_idx[p2]
            cooc_mat[i1, i2] += 1
            cooc_mat[i2, i1] += 1

    svd = TruncatedSVD(n_components=min(12, n_apps - 1), random_state=42)
    emb = svd.fit_transform(cooc_mat)
    norms = np.linalg.norm(emb, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    emb_norm = emb / norms
    app_sim_mat = np.dot(emb_norm, emb_norm.T) # (n_apps, n_apps)
    print("App2Vec matrix computed.")

    # 2. Define Candidate Model Architectures
    # Baseline v15
    print("\n--- Baseline v15 on Test Set ---")
    r_v15 = bench.evaluate(test_events, bench.score_v15, min_hist=0)
    print(f"v15 Test: @1={r_v15['@1']:.2f}% | @5={r_v15['@5']:.2f}% | @10={r_v15['@10']:.2f}% | MRR={r_v15['mrr']:.4f}")

    # Candidate A: App2Vec Cosine Backoff for Transitions
    def score_app2vec_backoff(events: list, now_hour: int, now_dow: int, now_ms: int,
                              target_ev: dict = None, p: dict = None) -> dict:
        p = p or bench.V15
        s = bench.score_v14(events, now_hour, now_dow, now_ms, target_ev, p)
        if not events:
            return s
        last_e = events[-1]
        dt = now_ms - last_e['timestampMillis']
        if dt <= int(p['session_ms']) and last_e['packageName'] in pkg_to_idx:
            prev_idx = pkg_to_idx[last_e['packageName']]
            sim_scores = app_sim_mat[prev_idx]
            for pkg, idx in pkg_to_idx.items():
                if pkg in s:
                    s[pkg] += 1.25 * float(sim_scores[idx])
        return s

    print("\n--- Testing Candidate A: App2Vec Transition Backoff ---")
    r_candA = bench.evaluate(test_events, score_app2vec_backoff, min_hist=0)
    print(f"Cand A Test: @1={r_candA['@1']:.2f}% | @5={r_candA['@5']:.2f}% | @10={r_candA['@10']:.2f}% | MRR={r_candA['mrr']:.4f}")

    # Candidate B: Multi-Horizon Exponential Recency (5min, 30min, 2h, 8h, 24h, 168h)
    def score_multi_horizon(events: list, now_hour: int, now_dow: int, now_ms: int,
                            target_ev: dict = None, p: dict = None) -> dict:
        p = p or bench.V15
        s = bench.score_v14(events, now_hour, now_dow, now_ms, target_ev, p)
        if not events:
            return s
        
        by_pkg = collections.defaultdict(list)
        for e in events:
            by_pkg[e['packageName']].append(e)
            
        for pkg, evs in by_pkg.items():
            last_ms = max(e['timestampMillis'] for e in evs)
            h_ago = (now_ms - last_ms) / 3_600_000.0
            # 5-minute micro recency
            r_micro = math.exp(-h_ago / 0.0833) # 5 min = 1/12 hour
            # 2-hour mid recency
            r_mid = math.exp(-h_ago / 2.0)
            if pkg in s:
                s[pkg] += 2.2 * r_micro + 1.1 * r_mid
        return s

    print("\n--- Testing Candidate B: Multi-Horizon Recency ---")
    r_candB = bench.evaluate(test_events, score_multi_horizon, min_hist=0)
    print(f"Cand B Test: @1={r_candB['@1']:.2f}% | @5={r_candB['@5']:.2f}% | @10={r_candB['@10']:.2f}% | MRR={r_candB['mrr']:.4f}")

    # Candidate C: Combined v16 Architecture (Multi-Horizon + App2Vec Backoff + Dynamic Smoothing)
    def score_v16_prototype(events: list, now_hour: int, now_dow: int, now_ms: int,
                            target_ev: dict = None, p: dict = None) -> dict:
        p = p or bench.V15
        s = bench.score_v14(events, now_hour, now_dow, now_ms, target_ev, p)
        if not events:
            return s
            
        last_e = events[-1]
        dt = now_ms - last_e['timestampMillis']
        in_sess = dt <= int(p['session_ms'])
        
        by_pkg = collections.defaultdict(list)
        for e in events:
            by_pkg[e['packageName']].append(e)
            
        prev_idx = pkg_to_idx.get(last_e['packageName'])
        
        for pkg, evs in by_pkg.items():
            last_ms = max(e['timestampMillis'] for e in evs)
            h_ago = (now_ms - last_ms) / 3_600_000.0
            r_micro = math.exp(-h_ago / 0.0833)
            r_mid = math.exp(-h_ago / 2.0)
            
            boost = 2.0 * r_micro + 0.8 * r_mid
            if in_sess and prev_idx is not None and pkg in pkg_to_idx:
                p_idx = pkg_to_idx[pkg]
                boost += 1.1 * float(app_sim_mat[prev_idx, p_idx])
                
            if pkg in s:
                s[pkg] += boost
        return s

    print("\n--- Testing Candidate C: v16 Full Prototype ---")
    r_candC = bench.evaluate(test_events, score_v16_prototype, min_hist=0)
    print(f"Cand C Test: @1={r_candC['@1']:.2f}% | @5={r_candC['@5']:.2f}% | @10={r_candC['@10']:.2f}% | MRR={r_candC['mrr']:.4f}")

    lo, hi = bench.bootstrap_ci(r_v15['rr_list'], r_candC['rr_list'])
    p_val = bench.wilcoxon_p(r_v15['rr_list'], r_candC['rr_list'])
    print(f"Cand C vs v15: Delta MRR={r_candC['mrr'] - r_v15['mrr']:+.4f} (95% CI: [{lo:+.4f}, {hi:+.4f}], p={p_val:.4e})")

if __name__ == '__main__':
    explore_architectures()
