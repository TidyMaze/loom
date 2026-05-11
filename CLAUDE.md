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
1. `UsageStore` — persists launch events as JSON (`usage_log.json`, capped at 1000 events)
2. `ScoreEngine` — pure stateless scorer; Naive Bayes with time-of-day Gaussian + day-of-week + exponential decay (7-day half-life)
3. `AppRepository` — merges PackageManager installed apps with scored events; computes stats (launchCount, todayCount, dailyAvg)
4. `AppViewModel` — normalizes raw scores to `rank` (0–1 relative to max), exposes `LiveData<List<AppEntry>>`
5. `MainActivity` — observes apps, drives `AppAdapter`; handles search, swipe-to-reset, long-press score reveal

**Key behaviors:**
- Home screen shows only apps with `launchCount > 0`; all apps appear in search
- `AppEntry.score` is the raw normalized score (negative for unscored apps, used for alphabetical fallback sort)
- `AppEntry.rank` is set by ViewModel as score relative to list max — used by adapter for visual prominence (font weight, size, alpha, progress bar width)
- Swipe left on an app → `resetApp` deletes all its events from store
- `Accent` colors are static white/gold; `accentForNow()` is a stub kept for future time-based theming

**No database** — all persistence is a single flat JSON file via Gson.
