---
name: loom-retune
description: Retune the Loom ScoreEngine on the phone's usage log. Archive the log, tune both regimes with Optuna, score a held-out test once, apply constants by hand, prove it in Kotlin, install and roll back.
---

# loom-retune

The weights live only in `ScoreEngine.kt` (`private const val` block at the top). `scripts/bench.py` mirrors them (`V17_IN` / `V17_COLD`, checked against the Kotlin file by a test in `scripts/test_bench.py`). Change constants only after an `ACCEPT` verdict (step 3).

## Workflow

1. **Archive the log.** The app keeps at most 20,000 events and drops the oldest (`UsageStore.MAX_EVENTS`).
   ```bash
   scripts/pull_log.sh
   ```
   Writes `~/work/analyses/loom/usage_log/pulls/*.json` (immutable) and rebuilds `master.json` from all pulls. Never commit these (app usage plus hashed wifi/bt ids). Pull at least every 2 weeks.

2. **Held-out rule.** The test window is the last 20% of `master.json` by time and is scored once. A window that already gave a verdict is spent: for the next retune, train on everything up to the last event of the previous master and test on the newer events (about 2,000 needed).

3. **Tune both regimes in parallel** (about 40 to 50 s per trial each at 15.6k events; 100 trials take 70 to 85 min). Study dir outside the repo, resumable: re-run the same commands after a crash.
   ```bash
   M=~/work/analyses/loom/usage_log/master.json; D=~/work/analyses/loom/usage_log/tune_<tag>; mkdir -p $D
   for R in in cold; do PYTHONHASHSEED=0 nohup uv run --with scipy --with optuna python3 scripts/bench.py --data $M --tune --trials 100 --study-dir $D --regime $R > $D/tune_$R.log 2>&1 & done
   ```
   When both logs print `best train mrr=`, score the test once:
   ```bash
   PYTHONHASHSEED=0 uv run --with scipy --with optuna python3 scripts/bench.py --data $M --tune --trials 100 --study-dir $D --regime both > $D/final.log
   ```
   A changed `master.json` needs a new `$D` (stale-study guard). `ACCEPT` needs all five checks: paired-bootstrap CI lower bound of dMRR > 0, dhit@1 >= 0, dhit@5 >= -0.3 pp, Wilcoxon p < 0.05, each regime's dMRR >= -0.005. Otherwise keep the shipped model.

4. **Apply by hand.** Paste the Kotlin block printed by `final.log` into `ScoreEngine.kt` (`--apply` is disabled: it targeted v14 names). Update the `V<N>_IN` / `V<N>_COLD` mirror in `bench.py`, the version comment, `versionName` in `app/build.gradle.kts`.

5. **Prove it in Kotlin (what ships).** Before and after the edit:
   ```bash
   LOOM_REPLAY_LOG=$M LOOM_REPLAY_START=<test start index> ./gradlew --console=plain testDebugUnitTest --tests '*ScoreEngineReplayTest*' --rerun -i 2>&1 | grep REPLAY
   ```
   The Kotlin and Python held-out gains must agree. `--rerun` is required: Gradle does not track env vars.

6. **Tests.** `./gradlew testDebugUnitTest`, and `uv run --with pytest --with scipy --with optuna python3 -m pytest scripts/test_bench.py` (never `pytest scripts/`: it would run an Optuna experiment).

7. **Deploy** (debug build, signed with `keystore.properties`, must match the installed signer).
   - Signer check first: pull the running APK (`adb -s <serial> pull "$(adb -s <serial> shell pm path com.yrolland.loom | sed 's/package://')" <archive>/apk/loom-v<prev>.apk`), then `apksigner verify --print-certs` on it and on `app/build/outputs/apk/debug/app-debug.apk`. Digests must be identical, otherwise stop: an install would need an uninstall, which deletes the log.
   - `scripts/pull_log.sh`, then `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`.
   - Prove it: `dumpsys package com.yrolland.loom | grep -E 'versionName|lastUpdateTime'`; on-phone APK sha256 equals the built one; event count in the log did not drop; `am start -n com.yrolland.loom/.MainActivity`; no crash in `logcat -d -s AndroidRuntime:E`.
   - Never `adb uninstall` or `pm clear` (`allowBackup=false`, the log lives only in the app).

8. **Rollback.** `adb -s <serial> install -r <archive>/apk/loom-v<prev>.apk` keeps the data. Or `git revert` and rebuild.

## Build environment (this Mac)
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export JAVA_OPTS="-Djavax.net.ssl.trustStore=$HOME/.local/share/aikido-cacerts.jks -Djavax.net.ssl.trustStorePassword=changeit"
```
`local.properties` (sdk.dir), `keystore.properties` and `~/.gradle/gradle.properties` (truststore) are set and gitignored or outside the repo. The signing key is `~/.android/loom-release.jks`. adb comes from the brew cask `android-platform-tools`, so `./gradlew installDebug` cannot find it: use `adb install -r`.

## Failed Attempts & Gotchas

### 1. Optuna Selection Bias (Stride Gotcha)
* **Problem**: `--tune-stride 4` alters the target evaluation frequency, so parameters overfit the stride and lose 4.2pp on the test set.
* **Fix**: stride 1, or 2 at most (the default is 2).

### 2. Analytical EMA Calculation
* **Problem**: the iterative EMA frequency model is O(n_apps * n_events * n_events) and times out on large logs.
* **Fix**: per package, sum `alpha * (1 - alpha) ** steps_after` over its events, O(n_events).

### 3. Transition Unit Test Confounding
* **Problem**: long gaps between sequence starts let recency and hour matching overpower transition scores.
* **Fix**: start sequences about 30 s apart at the same hour of day.

### 4. Thinned history in the old tuner
* **Problem**: `evaluate()` took its history from the list it was given. The old tuner passed `train[::stride]` (thinned history) and the test set alone (empty history at its start).
* **Fix**: `evaluate(start, end, stride, select)` always uses `events[:i]` of the full sorted log.

### 5. Notification boost leaks the label offline
* **Problem**: the old bench gave the boost only to the true target, using the target's own notification count.
* **Fix**: both arms run with `w_notif=0` offline. The Kotlin replay passes empty notification counts.

### 6. Do not re-score a spent test window
* **Problem**: trying a second candidate or feature on a window that already gave a verdict inflates the result.
* **Fix**: wait for fresh events (step 2) or use a nested split.
