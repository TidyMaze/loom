---
name: loom-retune
description: Workflow to benchmark, tune (via Optuna), test, and deploy the Loom ScoreEngine.
---

# loom-retune Skill

Automate the end-to-end pipeline to pull usage logs, run benchmarking/tuning, verify with unit tests, compile, and deploy.

## Goal
Optimize launcher scoring weights based on current usage logs and install the updated build on the connected phone.

## Workflow

1. **Sync Device Logs**:
   Pull latest `usage_log.json` from the device:
   ```bash
   adb shell run-as com.yrolland.loom cat files/usage_log.json > usage_log.json
   ```

2. **Benchmark / Tune**:
   Run the unified Python benchmark script to evaluate existing models and tune hyperparameters:
   - **Benchmark**:
     ```bash
     uv run python3 scripts/bench.py --data usage_log.json --benchmark --stats
     ```
   - **Hyperparameter Optimization**:
     ```bash
     uv run --with optuna python3 scripts/bench.py --data usage_log.json --tune --apply --trials 100 --tune-stride 2
     ```
     *(Note: `--apply` will only overwrite ScoreEngine.kt if the new parameters show statistically significant improvement on the held-out test partition).*

3. **Verify via Tests**:
   Before deploying, run the Kotlin unit test suite to ensure the ScoreEngine and transition models behave correctly:
   ```bash
   ./gradlew testDebugUnitTest
   ```

4. **Deploy**:
   Perform a clean build and install the debug APK onto the connected device:
   ```bash
   ./gradlew clean installDebug
   ```

5. **Launch Activity**:
   Start the launcher activity:
   ```bash
   adb shell am start -n com.yrolland.loom/.MainActivity
   ```

## Failed Attempts & Gotchas

### 1. Optuna Selection Bias (Stride Gotcha)
* **Problem**: Setting `--tune-stride 4` to speed up the tuning loop alters the target evaluation frequency, leading to parameters that overfit the stride and perform worse (-4.2pp) on the test set.
* **Fix**: Use `--tune-stride 1` (or at most `2` for a 2x speedup) to ensure generalizability.

### 2. Analytical EMA Calculation
* **Problem**: Standard iterative EMA frequency models run in $O(n_{\text{apps}} \cdot n_{\text{events}} \cdot n_{\text{events}})$, which times out on large event logs.
* **Fix**: Use the analytical calculation: for each package, sum `alpha * (1 - alpha) ** steps_after` over its events. This runs in $O(n_{\text{events}})$.

### 3. Transition Unit Test Confounding
* **Problem**: Long gaps between sequence starts in test events allow recency and hour-matching to overpower transition scores, causing unit test assertions to fail.
* **Fix**: Set sequence start timestamps to be extremely close (e.g., 30s apart) at the same hour of day to eliminate confounding recency and context bias.
