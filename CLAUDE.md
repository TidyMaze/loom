# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore.properties)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.yrolland.loom.ScoreEngineTest"

# Lint
./gradlew lint
```

## Architecture

Pure MVVM, no DI framework, no Compose — classic View system with RecyclerView.

**Data flow:**
1. `UsageStore` — persists launch events as JSON (`usage_log.json` in filesDir, written with `AtomicFile` + `JsonWriter`, capped at 20,000 events, oldest dropped, `UsageStore.MAX_EVENTS`)
2. `ScoreEngine` — pure stateless scorer (v16): hand-weighted linear blend of features (hour Gaussian, recency, Markov transitions, context), two regimes picked by the gap since the last event (in-session <= 70 s, cold-start otherwise); constants are `private const val`s at the top of the file, tuned offline with `scripts/bench.py`
3. `AppRepository` — merges PackageManager installed apps with scored events; computes stats (launchCount, todayCount, dailyAvg)
4. `AppViewModel` — normalizes raw scores to `rank` (0–1 relative to max), exposes `LiveData<List<AppEntry>>`
5. `MainActivity` — observes apps, drives `AppAdapter`; handles search (always-visible pill at the bottom) and the long-press item sheet (hide / app info / reset stats)

**Key behaviors:**
- Home screen shows launched apps (`launchCount > 0`) first, then fills up with unused apps until `SearchFallback.HOME_MIN_TILES` (12) so a new user isn't left with one icon; all apps appear in search
- `AppEntry.score` is the raw normalized score (negative for unscored apps, used for alphabetical fallback sort)
- `AppEntry.rank` is set by ViewModel as score relative to list max — used by adapter for visual prominence: three tile types (hero / row / tile, see `TileLayout`) and a 2dp rank bar
- Long-press an app → item sheet; its "Reset stats" row calls `resetApp`, which deletes all that app's events from the store (there is no swipe-to-reset and no score reveal)
- Colours are theme tokens (`loom_*` in `res/values/` and `res/values-night/`, theme `Theme.Loom`, light and dark); `Accent.kt` only holds small greeting/time helpers

**No database** — the usage log is a single flat JSON file (`JsonReader`/`JsonWriter`); small settings live in SharedPreferences. The log is private to the app: pull it with `scripts/pull_log.sh` (debuggable build, `adb run-as`).
