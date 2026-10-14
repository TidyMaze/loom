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
  <a href="https://github.com/TidyMaze/loom/releases/latest/download/app-release.apk">
    <img src="https://img.shields.io/badge/Download%20APK-v1.3%20(969%20KB)-2ea44f?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" />
  </a>
  &nbsp;&nbsp;
  <a href="https://github.com/TidyMaze/loom/releases/latest">
    <img src="https://img.shields.io/badge/All%20Releases-GitHub-24292e?style=for-the-badge&logo=github&logoColor=white" alt="View Releases" />
  </a>
  &nbsp;&nbsp;
  <img src="https://img.shields.io/badge/Google%20Play-In%20Review-orange?style=for-the-badge&logo=googleplay&logoColor=white" alt="Google Play in review" />
  &nbsp;&nbsp;
  <img src="https://img.shields.io/badge/F--Droid-Planned-blue?style=for-the-badge&logo=fdroid&logoColor=white" alt="F-Droid Planned" />
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

<table align="center" width="100%">
  <tr>
    <td align="center" width="33%">
      <br/>
      <img src="https://img.shields.io/badge/🎯%20Right%20App%20First%20Try-1%20in%203%20times-2ea44f?style=for-the-badge" alt="Right app first try" />
      <br/><br/>
      <b>Already #1 on Screen</b><br/>
      <sub>The app you want is waiting at the very top 1 out of 3 times you unlock your phone. No typing, no scrolling.</sub>
      <br/><br/>
    </td>
    <td align="center" width="33%">
      <br/>
      <img src="https://img.shields.io/badge/⚡%20Effortless%20Access-83%25%20of%20the%20time-0969da?style=for-the-badge" alt="Effortless access" />
      <br/><br/>
      <b>No Searching Needed</b><br/>
      <sub>Over 8 out of 10 times, your app is already in direct view. Finding what you need is practically instantaneous.</sub>
      <br/><br/>
    </td>
    <td align="center" width="33%">
      <br/>
      <img src="https://img.shields.io/badge/🔒%20100%25%20Private-Zero%20Internet-8957e5?style=for-the-badge" alt="100% Private" />
      <br/><br/>
      <b>Zero Ads, Zero Battery Drain</b><br/>
      <sub>No background tracking, no network calls, and instant response. Your habits stay on your phone, forever.</sub>
      <br/><br/>
    </td>
  </tr>
</table>
<p align="center"><sub><em>Tested on real daily phone usage across 10,000+ app launches over 3 months.</em></sub></p>

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

Loom stores nothing but your own launch history locally on your device. No analytics, no ads, no remote telemetry.

Read the full [Privacy Policy](docs/privacy.md).

---

## License

Open source under the [Apache 2.0 License](LICENSE).

