# Loom — ScoreEngine experiment log

Summary of every tuning iteration, what changed, and measured results. Walk-forward CV (warmup=50) on the actual on-phone event log of that day. Bigger logs over time as UsageStatsManager fills the store.

## Convention

- @1 / @3 / @5 / @10 = recall at top-K (% of predictions where true app is in top K)
- MRR = mean reciprocal rank
- Comparison rows always evaluate models on the **same dataset** unless noted
- Pure-recency baseline = "sort by `lastLaunchedMillis` descending"

## Versions

| v | tuned on | tuning method | @1 | @3 | @5 | @10 | MRR | Δ vs prev |
|---|---|---|---|---|---|---|---|---|
| v0 | 723 events | hand-set 3 features | 19.78% | 42.88% | 58.67% | 81.24% | 0.3826 | — original deploy |
| v1 | 1000 ev | + phase-1 ctx (audio/dev/charge/sr); Optuna | 22.50% | 45.87% | 60.65% | 81.63% | 0.3977 | +2.72pp @1 |
| v2 | 1000 ev | + 2-gram trans + self-penalty | 22.93% | 45.11% | 60.43% | 81.74% | 0.3997 | +0.43pp |
| v3 | 1000 ev | + burst-collapse + 4-scale recency | 24.13% | 44.35% | 59.46% | 81.52% | 0.4039 | +1.20pp |
| v4 | 4313 ev | UsageStats sync → 4× data, retune | 43.70% | 75.60% | 83.99% | 91.61% | 0.6134 | data boost +data |
| v5 | 9142 ev | 30-day backfill, retune | 67.88% | 86.90% | 91.61% | 95.06% | 0.7830 | data boost |
| v6 | 6685 ev (filter) | filter Pixel Launcher events from sync | 71.68% | 86.04% | 88.92% | 92.95% | 0.7976 | +5.43pp from filter |
| v7 | 6706 ev | retune (no gain over v6) — discarded | — | — | — | — | — | discard |
| **— data audit —** | | | | | | | | discovered 45% duplicate events |
| post-clean | 2454 ev | de-dup ACTIVITY_RESUMED (120s same-pkg), retune | 17.62% (recency) | — | — | — | 0.4214 | dirty headline gone |
| **v8** | **2454 clean ev** | **Optuna on clean data** | **43.68%** | **64.68%** | **71.88%** | **82.24%** | **0.5688** | **+26.06pp over recency** |
| v9 | 2500 clean ev | + phase-1 gating (ctxMinEvents=5) | 44.33% | 63.31% | 71.27% | 82.16% | 0.5689 | +0.87pp (eval-on-tune-set inflated; see audit below) |
| **v10** | **2544 clean ev** | **held-out tuned (Optuna on 80% train, eval on 20% test)** | **40.67%** | — | — | — | **0.5444** | **honest number, +0.98pp vs v9 on same test set** |
| **v11** | **2544 clean ev (held-out)** | **Plackett-Luce ML training (PyTorch, cross-entropy on softmax)** | **42.15%** | — | — | — | **0.5712** | **first statistically significant improvement** (ΔMRR CI [+0.004, +0.046], Wilcoxon p=0.0004; Δ@1 within noise) |
| **v12** | **2640 clean ev (held-out)** | **Joint Optuna HP search + PL retraining (80 trials, both HPs and weights re-optimized)** | **42.74%** | — | — | — | **0.5722** | ΔMRR=+0.0213 vs v11 [bootstrap CI: +0.0049, +0.0383] → **SIGNIFICANT** |

## v11 validation deep-dive (2026-05-18)

Bootstrap-validated against v10 on the held-out test set (n=465 predictions):

| metric | v10 train | v10 test | v11 train | v11 test | Δ test | bootstrap 95% CI | significant? |
|---|---|---|---|---|---|---|---|
| @1 | 45.24% | 40.43% | 42.32% | 42.15% | +1.72pp | [−1.72, +5.16] | **NO** (within ±4.5pp binomial noise) |
| MRR | 0.5809 | 0.5457 | 0.5694 | 0.5712 | +0.0255 | [+0.004, +0.046] | **YES** |
| Wilcoxon | — | — | — | — | — | p=0.0004 | **HIGHLY YES** |
| **train-test gap** | — | +4.81pp | — | +0.17pp | — | — | — |

**Key insight from validation**: v11's win is NOT from better training fit — it actually scores LOWER than v10 on train (42.3% vs 45.2%). The win comes from better GENERALIZATION (train-test gap shrunk from +4.81pp to +0.17pp). PL's smooth log-loss + weight decay 1e-4 = much less overfitting than Optuna on jagged MRR.

**Honest claims from v11**:
- ✓ MRR significantly improved (continuous metric has tighter CI than binary @1)
- ✓ Generalization dramatically better (train-test gap 28× smaller)
- ✓ Model still beats pure-recency baseline by +22pp @1 on test (sanity check passed)
- ✗ @1 absolute gain (+1.72pp) is within binomial noise at n=465 — DON'T claim it
- ✗ PL not universally better than Optuna — depends on data size + objective shape

**Noise floor reminder**: at n=465 test predictions, binomial 95% CI half-width on @1 is ±4.5pp. Any future @1 improvement claim ≤ 9pp needs bootstrap + Wilcoxon to be trustworthy.

**Negative weights discovered by PL (Optuna couldn't):**
- W_REC_8H: +2.63 → **−2.34**
- W_AUDIO: +4.27 → −0.20
- W_SR: 0.00 → **−0.72**
- SELF_PENALTY: 2.60 → 8.89 (much stronger)

These weren't bugs — they're consistent across PL training runs. Some features actually anti-predict when others are present (collinearity / redundancy). Constrained-positive Optuna couldn't represent this.

## ⚠️ Honesty audit (2026-05-18)

All v0–v9 numbers above were measured on the SAME data the model was tuned on. Held-out validation (Optuna on first 80%, report on untouched last 20%) reveals overfitting:

| version | reported @1 | TRAIN @1 | TEST @1 (honest) | overfit gap |
|---|---|---|---|---|
| v9 | 44.23% | 45.39% | **39.69%** | **−4.54pp** |
| v10 (held-out tuned) | — | 44.28% | **40.67%** | −3.61pp |

The +0.87pp v8→v9 claim was within tuning noise (binomial 95% CI on 509 predictions ≈ ±2.1pp). Future "+1pp" claims need bootstrap CIs or Wilcoxon signed-rank tests to be trustworthy.

**What's real**: the model still beats pure recency by ~23pp @1 on held-out test (40.67% vs 17.84%). Order-of-magnitude gain is robust. Micro-iterations after v8 are not.

## Failed / discarded experiments

| Idea | Tested how | Result | Verdict |
|---|---|---|---|
| Logistic Regression / GBM / LightGBM ranker / TabPFN | Same features as ScoreEngine; train/test split | Best ML 24.49% @1 vs ScoreEngine 25.17% @1 on fair holdout | No win, see [ml_findings.md](ml_findings.md) |
| Constrained recency (force `wR_total ≤ 2`) — make model learn beyond recency | Optuna with constraint | 68.52% @1 vs v6 71.38% | Discarded (was on dirty data; redundant after clean) |
| Cold-start specialist tune (only gap > 5min predictions) | Optuna on gap-filtered subset | +1.32pp on cold-start but −5.53pp overall | Discarded |
| Hour histogram per app (24-bin Laplace-smoothed P(hour\|app)) | Add feature, full retune | @1=43.34% vs 43.64% | No gain |
| Per-feature softmax with learnable temperature (replaces max-norm) | Add 11 τ params, full retune | @1=43.95% vs 43.46% | Tied, not worth the complexity |
| Macro-MRR tune (equal weight per app, not per event) | Retune with macro objective | macro +1.34pp @1, micro −2.58pp | Trade-off, not deployed |
| Hierarchical PL (per-app delta weights, λ sweep) | PL with L2 on Δ, λ∈{10,1,0.1,0.01,0.001} | Best λ=10: @1=39.83% vs 40.04% global-only; Δ@1=−0.21pp ns | ns — too few samples per app |
| Mixture-of-Experts (separate weights: in_session vs cold_start) | Hard binary gate on in_sess flag | @1=39.63% vs 40.25%; Δ@1=−0.62pp [−3.11, +1.87] ns | ns — more params, same data = overfit |
| Fourier hour features (sin/cos at 1× and 2× daily freq, 4 extra dims) | PL with 15 features | @1=40.04% vs 40.25%; Δ@1=−0.21pp [−2.07, +1.45] ns | ns — Gaussian already captures hour well enough |
| PL retrain on fresh data (2640 ev, frozen HPs) | Same PL, just newer data | Δ@1=+0.41pp [−1.66, +2.49] ns, ΔMRR=+0.0037 ns | ns — marginal data gain insufficient |

## Bootstrap CI rule (added 2026-05-18)

Going forward, any "improvement" claim must show:
- Bootstrap 95% CI on Δ@1 NOT crossing 0
- Wilcoxon signed-rank p < 0.05 on paired per-prediction reciprocal ranks
- v10 vs v9 example: Δ@1=−0.68pp [CI: −1.64, +0.24], p=0.23 → NOT a real difference

See `/tmp/bootstrap_ci.py`. 1000 resamples, paired comparison on per-prediction RR.

## Active experiments (this session)

| Idea | Status | Notes |
|---|---|---|
| Phase-1 gating (zero out audioMatch/devMatch for apps with <N ctx events) | tuning | Quick @ fixed v8 weights: +1.03pp @1 |
| Notification listener — count active notifications, use as ranking feature | Kotlin built, no data yet | Need user to grant `BIND_NOTIFICATION_LISTENER_SERVICE` in Settings, then accumulate data before tuning |

## Sync hardening (this session)

| Fix | Why | Effect |
|---|---|---|
| `DEDUP_WINDOW_MS = 3s → 120s` | UsageStatsManager fires ACTIVITY_RESUMED per Activity transition (in-app navigation), not just app launches | Future events deduped at insert; 84% in-session noise gone |
| One-time cleanup pass in `sync()` | Existing store had 45% duplicate / in-app-nav events | 4260 events purged on first run (6714 → 2454) |
| Filter Pixel Launcher (`com.google.android.apps.nexuslauncher`) etc. | These are "user pressed home", not app launches; 29% of events | 2794 launcher events purged earlier |
| `MAX_EVENTS = 1000 → 5000 → 20000` | Backfill from `UsageStatsManager` 7d → 30d → 90d window | Cap no longer trims real data |

## Cumulative impact (v0 → v8)

Different datasets, not directly comparable as a single line. Most-honest comparison: gain over pure recency baseline on the same data:

- **v0-era**: model beat recency by ~17.62pp → ~24.13pp @1 (within range of the simpler heuristic on small data)
- **v8 on clean data**: model beats recency by **+26.06pp @1**, **+34.5% MRR**

The model genuinely learns patterns. Earlier headlines of "70%+ @1" were inflated by duplicate-event noise — the actual achievement is the +25pp gap above the trivial baseline on clean data.

## Tooling

- `lib4.py` / `fast_eval.py`: walk-forward eval in Python (numpy-vectorized for ~500× speedup over the naive O(N²) loop)
- `fast_tune.py`: parallel Optuna (4 workers × 500 trials, stride=3 sampling)
- `fast_eval_softmax.py`, `fast_eval_gated.py`, `fast_eval_v2.py`: experimental variants
- All scripts read `usage_log.json` pulled via `adb -s … shell run-as com.yrolland.loom cat files/usage_log.json`

---
*Updated continuously as experiments run. Append entries; don't rewrite history.*
