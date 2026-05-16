# ML model attempt — findings

**Branch:** `ml-model-attempt`
**Date:** 2026-05-16
**Outcome:** Negative result. ML cannot honestly beat the current ScoreEngine v3 with available data.
**Recommendation:** Discard branch. Pursue new feature types (notifications, calendar) instead.

## Goal

Try a real ML model to beat the current 24.13% @1 / 0.4039 MRR ScoreEngine v3. Constraint: any winning model must be **easily deployable to Kotlin** (i.e. linear coefficients or tiny tree).

## Setup

- **Dataset**: 1000 events from real phone usage (9 days), 397 with phase-1 ctx.
- **Split**: time-ordered, train on first 70% (events 50–700), test on last 30% (700–1000) — 294 valid predictions in test.
- **Eval metrics**: @1, @3, @5, @10 hit-rate + MRR.
- **Framing**: pointwise learning-to-rank. Each prediction point → one row per candidate app (~30 candidates). Label = 1 if this is the next app, else 0. ~650 prediction groups × ~30 rows = 19.5K rows, 3.4% positive rate.

## Rounds

### Round 1–3 — raw ML on ScoreEngine-style features

15 features (ctx, 4-scale recency, 1-gram + 2-gram trans, audio/dev/charge/sr, log_count, is_last, is_in_session).

| model | @1 | MRR |
|---|---|---|
| LogReg (balanced) | 17.69% | 0.3855 |
| LogReg (plain) | 18.71% | 0.3917 |
| HistGBM | 24.49% | 0.4182 |
| LightGBM ranker | 17.69% | 0.3592 |
| HistGBM + one-hot apps | 21.43% | 0.3993 |
| **ScoreEngine v3 (biased, ceiling)** | **27.21%** | **0.4472** |
| **ScoreEngine v3 (fair retune on train)** | **25.17%** | **0.4260** |

Even the biased ScoreEngine ceiling beats every standalone ML model.

### Round 4 — richer features (23) + regularization sweep + TabPFN

Added `hours_since_app`, `session_pos`, `launches_last24h`, `launches_last7d`, `is_morning/evening/weekend`, `freq_share`.

| model | @1 | MRR |
|---|---|---|
| LogReg(C=0.5, L1) | 20.75% | 0.4039 |
| HistGBM regularized | 19.39% | 0.3876 |
| kNN(k=200) | 18.71% | 0.3799 |
| GaussianNB | (similar tier) | |
| **TabPFN** | **N/A** | requires API token |

Best ML caught up to ScoreEngine v2 MRR (~0.40) but still well below v3.

### Round 5 — stacking on fair-tuned ScoreEngine

Add ScoreEngine score as feature #0, train LR on top.

| approach | @1 | MRR |
|---|---|---|
| Fair-tuned SE alone | 21.77% | 0.4104 |
| **LR(C=5, L1) stacked on fair SE** | **22.79%** | **0.4155** ✓ +1.24% MRR |
| Bagging 5 LRs | 22.45% | 0.4172 |

**First real win!** But base was fair-tuned (weaker), so we still need to beat the biased deployed SE.

### Round 6 — stacking on deployed (biased) SE + blends

| blend α (test-tuned) | @1 | MRR | comment |
|---|---|---|---|
| α=0.0 (pure LR) | 23.13% | 0.4186 | worse than SE |
| α=0.5 (50/50) | **28.23%** | **0.4521** ★ apparent win on test |
| α=1.0 (pure SE) | 27.21% | 0.4472 | baseline |

α=0.5 won by +1.10% MRR — looked like a real win.

### Round 7 — proper CV validation of round 6

**Tuned α via group-CV on train only**, then applied to test.

| where α validated | best α | test MRR |
|---|---|---|
| chose by test (cherry-pick) | 0.5 | 0.4521 (apparent win) |
| chose by train CV (honest) | 1.0 | 0.4472 (same as baseline) |

CV correctly rejected the test-overfit α=0.5. The "win" was random noise.

### Round 8 — minimal deployable: greedy feature selection on top of SE

Start with `[SE]`, greedy-add 1 feature at a time, only keep if CV-MRR improves by ≥0.001 (0.1% MRR).

Result: **0 features survived**. SE alone (`[SE]`) is the CV-best set. No combination of the 23 features improves over `SE` alone.

```
SE alone:                      CV-MRR = 0.3836
SE + best single feature:      CV-MRR = 0.3836  (no change)
```

LR-on-[SE] is degenerate (monotonic transform → same ranking → same scores).

## Why ML loses here

1. **Same features**: The 23 ML features are recomputed from the same underlying signals ScoreEngine already exploits. No fresh information.
2. **Hyperparameter density**: ScoreEngine has 20 Optuna-tuned constants (half-lives, σ, smoothing). They shape the features themselves — LR/GBM can't recover that with linear/tree coefficients on top.
3. **Per-query max-normalization**: ScoreEngine normalizes each feature across the current candidate set. This is a strong inductive bias ML can't replicate from raw features.
4. **Sample size**: 650 prediction groups is tiny. Tree models overfit; linear underfits.
5. **Class imbalance**: 3.4% positives. Even with balanced weights, signal is sparse.

## What was tried

- ✓ Logistic Regression (L1, L2, varied C) — best ML standalone
- ✓ HistGradientBoosting (sklearn) — close to LR
- ✓ LightGBM classifier + LambdaRank ranker — both worse
- ✓ Naive Bayes, kNN — worse
- ✓ One-hot app identity — didn't help
- ✓ Stacking on ScoreEngine score — wins only when test-set is used to tune α (overfit)
- ✓ Greedy forward selection on top of SE — no feature survives CV
- ✗ TabPFN — requires API token, not attempted
- ✗ Neural nets — would not be Kotlin-deployable

## What would actually help (next steps)

None require continuing ML on the current feature set.

1. **Notification badges** as feature. ScoreEngine has no access to "you have 3 unread WhatsApp messages." Requires `BIND_NOTIFICATION_LISTENER_SERVICE`. Likely +1-3pp @1.
2. **More data**. 5000-event cap unlocked in v3. Wait ~weeks, retry ML.
3. **Cross-user generalization test**. Get a 2nd user's log to confirm ScoreEngine isn't overfit to one user's patterns.
4. **App2vec embeddings**. Learn dense app representations from session co-occurrences. Could be exported as ~32-dim vectors per app, deployable.
5. **TabPFN with token** for offline benchmark — wouldn't deploy but tells us if better is possible.

## Conclusion

ScoreEngine v3 is at or very near the local optimum for the current data scale (1000 events, ~650 prediction points). Adding ML on top yields no honest gain. Effort better spent on:
- Adding new feature TYPES (notifications, calendar)
- Collecting more data
- Re-tuning ScoreEngine when new signals are available

**Branch verdict**: discard. Nothing to deploy.

---
*Generated from `/tmp/ml_train*.py`, `/tmp/ml_stack*.py`, `/tmp/ml_lean.py`. Branch: `ml-model-attempt`.*
