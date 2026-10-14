<p align="center">
  <img src="docs/app_icon_512.png" width="96" height="96" alt="Loom logo" />
</p>

# <p align="center">Loom</p>

<p align="center">
  <em>A minimal Android launcher that quietly learns how you use your phone.</em>
</p>

<p align="center">
  <a href="https://github.com/TidyMaze/loom/releases/latest/download/app-release.apk"><img src="https://img.shields.io/badge/Download_APK-v1.3-2ea44f?style=flat-square&logo=android&logoColor=white" alt="Download APK" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-black?style=flat-square" alt="Android 8.0+" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-black?style=flat-square" alt="License Apache 2.0" /></a>
</p>

<p align="center">
  <sub>Early Alpha &nbsp;•&nbsp; Vibe Coded &nbsp;•&nbsp; Active Development</sub>
</p>

No folders. No widgets. No clutter. Just a clean list of apps, ranked by what you are most likely to need right now based on time of day, day of week, and your past habits.

> [!NOTE]
> **Early Alpha**: Loom is at the very beginning of its journey and vibe-coded with care. Everything works on-device, but expect rapid evolution. Feedback and bug reports are warmly welcome!

<div align="center">

| 🎯 1 in 3 | ⚡ 83% | 🔒 100% |
| :---: | :---: | :---: |
| **Right app, first try**<br><sub>Already #1 on screen</sub> | **Zero search needed**<br><sub>Directly in view</sub> | **Private & offline**<br><sub>Zero tracking, zero ads</sub> |

<sub><em>Tested on real daily phone usage across 10,000+ app launches over 3 months.</em></sub>

</div>

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
- **Zero tracking & 100% private**: No internet connection required. Everything stays strictly on your device. Anonymous usage stats to improve the model may come later, opt-in only.
- **Permission-refusal fallback**: Fully usable with zero runtime permissions granted.

---

## Getting Started & Installation

### 1. Download & Install APK
1. Download [`app-release.apk`](https://github.com/TidyMaze/loom/releases/latest/download/app-release.apk) from the latest release.
2. Open the file on your device and confirm installation. If prompted, enable **Install unknown apps** for your browser or file manager.

### 2. Set as Default Launcher
1. Open Android **Settings** > **Apps** > **Default apps** > **Home app**.
2. Select **Loom**.
3. Or press your device's **Home** button/gesture and select **Loom** > **Always**.

---

## Gestures & Navigation

| Action | Gesture | Description |
|---|---|---|
| **Launch App** | Tap | Opens the application immediately. |
| **Instant Search** | Swipe down | Opens fuzzy search filter for quick typing. |
| **App Options** | Long press | View app details, system info, or reset learned score. |
| **Fast Jump** | Alphabet scroll | Drag along the alphabet bar to jump directly to a letter. |

---

## Compatibility

- **Minimum Version**: Android 8.0 Oreo (API 26)
- **Target Version**: Android 16 (API 36)
- **Supported Architectures**: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`
- **Display**: Optimized for 60Hz-120Hz displays and OLED battery saving.

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

## Contributing

Contributions, feature requests, and bug reports are welcome!

- **Report a Bug**: Open an issue on [GitHub Issues](https://github.com/TidyMaze/loom/issues).
---

## FAQ & Troubleshooting

### Why are there no colorful app icons?
Loom is designed intentionally around digital wellbeing and focus. Colorful icons trigger dopamine loops and subconscious distraction. A clean typography-first list helps you launch what you intended without rabbit-holing.

### How does the ranking engine learn without internet access?
Everything runs on-device using a lightweight Kotlin scoring engine. When you open an app, Loom logs the timestamp and previous app in an encrypted local database. The math uses exponential decay and Gaussian time-of-day kernels. Zero data ever leaves your phone.

### Can I reset the learned history?
- **For a single app**: Long press the app item and select **Reset Rank** (or swipe to clear).
- **For all apps**: Go to Android **Settings** > **Apps** > **Loom** > **Storage** > **Clear Data**.

### How do I switch back to my previous launcher or uninstall?
Go to Android **Settings** > **Apps** > **Default apps** > **Home app**, and choose your original launcher. You can then uninstall Loom normally like any Android app.

---

## Privacy Policy

Loom stores nothing but your own launch history locally on your device. Currently, it operates 100% offline with zero network permissions and zero remote telemetry.

In future releases, optional anonymous usage statistics (strictly designed to train and refine the habit scoring model) may be introduced. This will strictly be **opt-in** and never enabled by default.

Read the full [Privacy Policy](docs/privacy.md).

---

## License

Open source under the [Apache 2.0 License](LICENSE).

