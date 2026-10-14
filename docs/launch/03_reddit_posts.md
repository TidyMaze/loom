# Reddit Launch Posts (Ready to Copy-Paste)

---

## 1. r/digitalminimalism

**Post Title**:
`I made a minimal launcher that puts the app I need at the top, without me pinning anything`

**Post Body**:
```markdown
I've tried most of the usual tricks: grayscale, deleting apps, a couple of minimalist launchers. The launchers helped, but I kept scrolling an alphabetical list or pinning favorites that were only right for part of the day (work stuff in the morning, messages at lunch, music on the way home).

So I made my own. Loom is a black screen with a small grid of app icons: no labels, no badges, no widgets. It learns when you open what and moves those apps up at the right time of day. You don't set anything up.

On three months of my own use (about 10,000 app opens), the app I wanted was first on the list one time in three, and in the top 10 83% of the time, so I almost never search.

Everything stays on the phone. The app doesn't even have the internet permission. Maybe one day I'll ask people to share anonymous usage stats to improve the ranking, but only if they opt in.

It's free and open source: https://github.com/TidyMaze/loom

Fair warning, it's an early alpha and I built a good part of it with an AI coding assistant. Bugs and "this annoyed me" reports are very welcome.
```

---

## 2. r/androidapps

**Post Title**:
`[DEV] Loom: a 790 KB text launcher that sorts your apps by what you're likely to open next`

**Post Body**:
```markdown
Hi all, I've been working on Loom, a minimal launcher for Android.

The home screen is just a grid of app icons on black, with no labels or badges. The order changes through the day: a small scoring model on the phone looks at how recently you used each app, the time and weekday, and which app you just closed. Pull down anywhere to search the rest.

It has no internet permission and no Google Play Services, and the APK is 790 KB. It targets Android 16 with predictive back and edge-to-edge. If I ever add anonymous usage stats to tune the model, they'll be opt-in.

GitHub: https://github.com/TidyMaze/loom
APK: https://github.com/TidyMaze/loom/releases/latest/download/app-release.apk

It's early, so bug reports help a lot.
```

---

## 3. r/opensource

**Post Title**:
`Loom: open-source Android launcher that learns your routine on-device (Apache 2.0)`

**Post Body**:
```markdown
Sharing a side project: Loom, a minimal Android launcher.

It ranks your apps with a small hand-tuned model that runs on the phone (recency, time of day, app-to-app transitions). There's no network permission, no trackers and no Google Play Services, so it should be fine for F-Droid. I've prepared the recipe. Any future usage stats to improve the model would be opt-in.

Code: https://github.com/TidyMaze/loom
License: Apache 2.0

Issues and PRs welcome, especially from people with very different phone habits than mine.
```

---

## 4. r/dumbphones

**Post Title**:
`For those who can't drop the smartphone entirely: a calm, minimal launcher (free, open source)`

**Post Body**:
```markdown
I need my bank app, 2FA, maps and WhatsApp for family, so a real dumbphone isn't an option for me. What I could change was the home screen.

Loom replaces it with a dark grid of icons. No labels, no notification dots, no widgets, nothing that moves. The apps you need at that time of day float to the top on their own, so you open what you came for and leave.

Free, open source, no internet permission: https://github.com/TidyMaze/loom
```
