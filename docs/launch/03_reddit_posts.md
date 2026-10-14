# Reddit Launch Posts (Ready to Copy-Paste)

---

## 1. r/digitalminimalism

**Post Title**:
`I got tired of colorful icons triggering my phone addiction, so I built an open-source minimalist launcher that learns habits offline`

**Post Body**:
```markdown
Hey everyone,

Like many of you here, I went through multiple attempts to make my phone less distracting: grayscale mode, dumbphone transitions, minimalistic launchers.

What always frustrated me with existing minimalist launchers was having to scroll endlessly through an alphabetical list, or setting static "favorite" apps that never matched what I actually needed throughout the day (work tools in the morning, messaging midday, commute/music in the evening).

So I built **Loom**, a pure-black launcher designed specifically for focus:
- **No icons, no widgets, no folders**: Pure typography-first design on an OLED-black background.
- **Smart habit learning**: An on-device scoring engine looks at the time of day, day of week, and your previous app transitions.
- **Zero data collection**: It does not even request the `INTERNET` permission in Android. Everything is processed and stored locally in encrypted storage. Later on I may add anonymous usage stats to improve the model, strictly opt-in and off by default.
- **Micro footprint**: Less than 1 MB in size, zero battery drain, and instant (<1ms) response.

On my own real daily usage over 3 months (10,000+ app opens), the exact app I want is already waiting at the very top 1 out of 3 times, and over 80% of the time it is right in view without typing a single letter.

It's completely free and open-source (Apache 2.0). If you want to try it, the direct signed APK is on GitHub:
https://github.com/TidyMaze/loom

*Note: Loom is at the very beginning of the project (early Alpha) and actively vibe-coded. The core engine is rock solid, but feedback on edge cases and feature requests are super welcome!*

Feedback and ideas for further decluttering are very welcome!
```

---

## 2. r/androidapps

**Post Title**:
`[DEV] Loom – A 969 KB distraction-free launcher that quietly predicts your next app (100% on-device, no internet permission)`

**Post Body**:
```markdown
Hi r/androidapps!

I wanted to share **Loom**, an ultra-minimalist Android launcher I've been developing.

Key highlights:
- **Under 1 MB APK**: Fast install, zero bloat, pure native Kotlin.
- **Smart prediction**: Dual-regime scoring model combining exponential recency decay, Gaussian time-of-day kernels, and Markov transition bigrams (P(App B | App A)).
- **Zero internet access**: The app does not declare the `android.permission.INTERNET` permission at all. Your app habits are 100% private. Anonymous usage stats to improve the model may come later, opt-in only.
- **Predictive back & edge-to-edge**: Fully targeted for Android 16 (API 36) with smooth 120Hz gesture navigation.
- **Instant search**: Pull down anywhere to fuzzy-search across your full app catalog.

GitHub & Downloads: https://github.com/TidyMaze/loom
Direct APK: https://github.com/TidyMaze/loom/releases/latest/download/app-release.apk

Looking forward to your bug reports, feature suggestions, or general feedback!
```

---

## 3. r/opensource

**Post Title**:
`Loom: Open-source, offline minimalist Android launcher with on-device routine learning (Apache 2.0)`

**Post Body**:
```markdown
Hello r/opensource,

I wanted to share Loom, an open-source Android launcher built with a strict focus on privacy, minimalism, and performance.

Code & Release: https://github.com/TidyMaze/loom
License: Apache 2.0

Why it's built this way:
- **No telemetry, zero trackers**: No network permission declared in the manifest. Any future anonymous usage stats (to improve the model) will be opt-in only.
- **On-device ML**: Predicts and ranks apps using an offline mathematical scoring engine without cloud dependencies.
- **F-Droid ready**: Recipe prepared and build verified without proprietary blobs.

PRs and feedback welcome!
```

---

## 4. r/dumbphones

**Post Title**:
`Turn any Android smartphone into a distraction-free tool with Loom (open-source & offline)`

**Post Body**:
```markdown
Hi all,

If you want the calm, intentional experience of a minimalist phone without sacrificing essential smartphone tools (banking, 2FA, maps, WhatsApp for family), you might find Loom useful.

It replaces the home screen with a distraction-free monochrome list of text-only apps. No red notification dots, no algorithmic feeds, no dopamine-inducing graphics.

It's free, open-source, and has no internet permission:
https://github.com/TidyMaze/loom
```
