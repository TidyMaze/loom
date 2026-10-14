<p align="center">
  <img src="docs/app_icon_512.png" width="96" height="96" alt="Loom logo" />
</p>

# <p align="center">Loom</p>

<p align="center">
  <em>A minimal Android launcher that quietly learns how you use your phone.</em>
</p>

<p align="center">
  <a href="https://github.com/TidyMaze/loom/releases"><img src="https://img.shields.io/badge/Release-v1.3-black?style=for-the-badge&logo=android&logoColor=white" alt="Release v1.3" /></a>
  <a href="https://android.com"><img src="https://img.shields.io/badge/API-36%20Vanilla%20Ice%20Cream-black?style=for-the-badge&logo=android&logoColor=white" alt="Target API 36" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-1.9.23-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 1.9" /></a>
  <a href="#zero-tracking--100-private"><img src="https://img.shields.io/badge/Privacy-100%25%20On--Device-10B981?style=for-the-badge&logo=shield&logoColor=white" alt="100% On Device" /></a>
  <a href="https://github.com/TidyMaze/loom/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge" alt="Apache 2.0 License" /></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Telemetry-0%25-black?style=flat-square" alt="0% Telemetry" />
  <img src="https://img.shields.io/badge/Ads-Zero-black?style=flat-square" alt="Zero Ads" />
  <img src="https://img.shields.io/badge/Battery%20Drain-OLED%20Friendly-000000?style=flat-square" alt="OLED Friendly" />
  <img src="https://img.shields.io/badge/Clutter-None-black?style=flat-square" alt="No Clutter" />
  <img src="https://img.shields.io/badge/AI-On--Device%20ML-6366F1?style=flat-square" alt="On-Device ML" />
  <img src="https://img.shields.io/badge/Vibes-Minimalist%20AF-purple?style=flat-square" alt="Minimalist AF" />
</p>

No folders. No widgets. No clutter. Just a clean list of apps, ranked by what you are most likely to need right now based on time of day, day of week, and your past habits.

---

## Screenshots

<p align="center">
  <img src="docs/screenshots/00_onboarding.png" width="30%" alt="Onboarding tutorial" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="docs/screenshots/01_home.png" width="30%" alt="Home screen" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="docs/screenshots/02_search.png" width="30%" alt="Instant search" />
</p>

---

## Features

- **Smart ranking**: Apps surface automatically when you actually use them.
- **Context awareness**: Learns routines by time of day, day of the week, and launch transitions.
- **Instant search**: Swipe down anywhere to filter apps by name with fuzzy matching.
- **Clean aesthetic**: Pure black UI, easy on eyes and battery efficient on OLED screens.
- **Swipe to reset**: Clear an app's history with a swipe.
- **Zero tracking & 100% private**: No internet connection required. Everything stays strictly on your device.
- **Permission-refusal fallback**: Fully usable with zero runtime permissions granted.

---

## Architecture & Scoring Engine

Loom uses an on-device score engine combining multiple temporal and behavioral signals:

1. **Recency decay**: Exponential time decay based on last usage timestamp.
2. **Time-of-day affinity**: Gaussian kernel centered on current hour.
3. **Day-of-week affinity**: Differentiates weekday vs weekend habits.
4. **Transition bigram**: Probabilistic likelihood of opening App B right after App A.
5. **Context signals**: Optional on-device location, activity, and calendar signals.

---

## Build & Development

### Requirements
- Android SDK 36
- Java 17 / Kotlin 1.9
- Gradle 8.3+

### Common Commands

```bash
# Run unit tests
./gradlew test

# Build debug APK
./gradlew assembleDebug

# Build signed release bundle (.aab)
./gradlew bundleRelease
```

---

## Privacy Policy

Loom stores nothing but your own launch history locally on your device. No analytics, no ads, no remote telemetry.

Read the full [Privacy Policy](docs/privacy.md).

---

## License

Open source under the [Apache 2.0 License](LICENSE).
