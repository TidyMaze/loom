# v13 Tuning Session — 2026-05-25

## Data snapshot
- **3108 events** (up from 2749 last session, +359)
- Date range: 2026-05-07 → 2026-05-25 (18 days)
- New context feature coverage:

| Feature | Coverage |
|---|---|
| notificationCount | 14% (461/3108) |
| batteryPct | 14% (458/3108) |
| secsToNextEvent | 14% (455/3108) |
| btDeviceHash | 11% (346/3108) |
| wifiSsidHash | 9% (290/3108) |
| activityType | 6% (193/3108) |
| prevAppDwellSecs | 0% |

---

## Experiment 1 — Base HP retune (300 Optuna trials)

**Goal:** Re-run full HP optimization on fresh data (v13 was tuned on 2749 events).

**Result: NOT SIGNIFICANT**

| | MRR | @1 |
|---|---|---|
| v13 baseline | 0.5708 | 44.13% |
| tuned | 0.5713 | 44.09% |
| Δ | +0.0003 | -0.04pp |

CI=[-0.0046, +0.0050] p=0.1926

**Best params found (not shipped):**
```
hour_sigma=3.03, decay_hl=19.03, recency_h=0.47, trans_decay=4.05,
trans_smooth=2.62, burst_gap=22000, w_ctx=0.005, w_rec=5.06,
w_r8=1.07, w_r24=1.78, w_r168=2.89, w_trans=3.23, w_trans2=4.56,
self_pen=26.4, self_hl=38.9
```

**Interpretation:** v13 base weights are already well-tuned for this data volume.
Notable shift in best params: higher w_rec (2.84→5.06), higher w_trans2 (2.73→4.56),
longer decay (11.77→19.03). May reflect the extra 359 events but gain is below noise floor.
Revisit when data doubles (~5000+ events).

---

## Experiment 2 — New context features (150 Optuna trials)

**Goal:** Test if wifi/BT/activity/notif/calendar/battery signals (Phase 3 collection) now
add predictive value at 6-14% coverage.

**Result: SIGNIFICANT**

| | MRR | @1 |
|---|---|---|
| v13 baseline | 0.5708 | 44.13% |
| + ctx features | 0.5815 | ~45.5% |
| Δ | **+0.0107** | **+1.4pp** |

p=0.0000

**Best weights:**
```
w_notif=3.771   (notification count match)
w_bat=3.978     (battery % proximity)
w_cal=1.961     (secs to next calendar event proximity)
w_wifi=0.079    (wifi SSID hash match — near zero)
w_bt=-0.851     (BT device hash match — negative, suspicious)
w_act=-0.046    (activity type match — near zero)
ctx_smooth=0.175
ctx_min=3       (min events with ctx to qualify, very low)
```

**Interpretation:**
- **notif + battery + calendar** carry real signal at 14% coverage
- **BT negative weight** is suspicious — only 11% coverage, likely overfitting
- **wifi and activity near zero** — too sparse (9%, 6%) to be reliable yet
- **ctx_min=3 + ctx_smooth=0.175** = aggressive: low smoothing, low qualification
  threshold. Risk of per-app overfitting on 3 events.
- The +0.0107 gain is meaningful but should be validated over more data

**⚠️ Caution flags before shipping:**
1. BT negative weight makes no semantic sense — fitting noise
2. ctx_smooth=0.175 is very low for 14% coverage
3. Only 3 events needed to qualify — most apps will qualify with sparse data

**Recommendation:** Ship notif + battery + calendar only (positive, semantically
sensible weights). Zero out wifi, BT, activity until coverage hits 25%+.

---

## Summary table

| Experiment | Δ MRR | p | Ship? |
|---|---|---|---|
| Base HP retune | +0.0003 | 0.19 | No (NS) |
| Ctx features (all) | +0.0107 | 0.0000 | Partial — notif+bat+cal only |

---

## Current model state

**v13** (deployed): MRR=0.5965 @1=47.49%
*(Note: eval MRR varies slightly run-to-run due to walk-forward boundary — 0.5708 vs 0.5965
reflects different eval starting points/data slices, not regression.)*

**Next milestones:**
- prevAppDwellSecs still 0% — investigate why (UsageStats latency or permission issue?)
- ctx features → ship notif+bat+cal weights once validated
- Base retune → revisit at ~5000 events
- Full v14 retune (base + ctx) when coverage hits 25%+ on all signals (~4 more weeks)
