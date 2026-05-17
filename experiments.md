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

## Failed / discarded experiments

| Idea | Tested how | Result | Verdict |
|---|---|---|---|
| Logistic Regression / GBM / LightGBM ranker / TabPFN | Same features as ScoreEngine; train/test split | Best ML 24.49% @1 vs ScoreEngine 25.17% @1 on fair holdout | No win, see [ml_findings.md](ml_findings.md) |
| Constrained recency (force `wR_total ≤ 2`) — make model learn beyond recency | Optuna with constraint | 68.52% @1 vs v6 71.38% | Discarded (was on dirty data; redundant after clean) |
| Cold-start specialist tune (only gap > 5min predictions) | Optuna on gap-filtered subset | +1.32pp on cold-start but −5.53pp overall | Discarded |
| Hour histogram per app (24-bin Laplace-smoothed P(hour\|app)) | Add feature, full retune | @1=43.34% vs 43.64% | No gain |
| Per-feature softmax with learnable temperature (replaces max-norm) | Add 11 τ params, full retune | @1=43.95% vs 43.46% | Tied, not worth the complexity |
| Macro-MRR tune (equal weight per app, not per event) | Retune with macro objective | macro +1.34pp @1, micro −2.58pp | Trade-off, not deployed |

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
