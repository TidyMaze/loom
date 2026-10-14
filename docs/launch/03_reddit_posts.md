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

The idea comes from Yasan Launcher (https://play.google.com/store/apps/details?id=yasan.space.mnml.ai.launcher), thanks to its developer. Loom is an open-source take on it.

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

Inspired by Yasan Launcher (https://play.google.com/store/apps/details?id=yasan.space.mnml.ai.launcher), thanks to its developer for the idea.

Issues and PRs welcome, especially from people with very different phone habits than mine.
```

---

## 4. r/dumbphones (posted 2026-10-14)

Live post: https://www.reddit.com/r/dumbphones/comments/1x612io/ (flair: General discussion, image: `docs/screenshots/01_home.png`). Text as published, after two edits (APK link, Yasan credit):

**Post Title**:
`I couldn't go full dumbphone, so I made my Android launcher as boring as I could`

**Post Body**:
```markdown
I need a few smart apps (bank, 2FA, maps, WhatsApp for family), so a real dumbphone never stuck for me. Following the "customize what you already have" route from the pinned post, the part that bugged me most was the home screen.

Minimal launchers helped, but I always ended up either scrolling an alphabetical list or pinning favorites that were only right half the day.

So I made Loom. It's brand new as a public project, but it has been my only launcher for the last 6 months. I like the idea of a launcher that does very little but does it well. It's a black screen with a small grid of icons. No names, no notification badges, no widgets, no feed.

Apps you don't use stay off the home screen. In my experience most installed apps are rarely or never opened, so there's no point in looking at them every day. (On a fresh install it shows a few extra so the screen isn't empty while it learns.) Everything else is one swipe down away, in search.

The apps you do use are ordered by a small model that learns your habits: time of day, day of week, which app you just closed. It's a step up from sorting by "last opened": over the last three months of my usage log (about 10k app opens) it ranked the app I wanted 37% better than a plain recency sort, and that app was the first icon about one time in three.

A few things people here might care about: it has no internet permission at all, no ads, no account, and it's free and open source. It's an early alpha, and I wrote a good part of it with an AI coding assistant, so expect rough edges.

https://github.com/TidyMaze/loom

Happy to hear what you'd strip out even further.

Direct APK download: https://github.com/TidyMaze/loom/releases/latest/download/app-release.apk

Credit where it's due: the idea comes from Yasan Launcher (https://play.google.com/store/apps/details?id=yasan.space.mnml.ai.launcher), which ranks apps by context. Thanks to its developer. Loom is my own open-source take on it.
```
