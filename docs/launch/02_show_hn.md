# Show HN (Hacker News) Launch Post

Submit on [news.ycombinator.com/submit](https://news.ycombinator.com/submit).

## Title
`Show HN: Loom, an Android launcher that ranks apps by what you usually open at this time`

## URL
`https://github.com/TidyMaze/loom`

## First Comment (from Author)

```markdown
Hi HN,

I wanted a calmer home screen: no icons, no badges, no widgets. The text-only launchers I tried all had the same problem though. Either I scrolled an alphabetical list all day, or I pinned four favorites that were right in the morning and wrong by lunch.

So Loom is a black screen with a list of app names, sorted by how likely I am to open each one right now. The scoring is a hand-tuned linear model in plain Kotlin, running on the phone. It looks at how recently I opened an app, the hour and weekday (Gaussian kernels), and which app I just left (Markov transitions, so "Maps then Spotify" gets picked up).

I measured it on my own log, 10,472 launches over 112 days. The app I wanted was #1 on screen 31.8% of the time and in the top 10 83% of the time. MRR is 0.483, 37% better than sorting by most recent.

There's no INTERNET permission in the manifest, so nothing leaves the phone. I might later add anonymous usage stats to tune the model on more than one person, but that would be opt-in and off by default. The APK is 790 KB and doesn't depend on Google Play Services.

Honest status: it's an early alpha and I built a lot of it with an AI coding assistant. The scoring and gestures are tested, the rest moves fast. I'd especially like to hear whether the ranking holds up on someone else's habits, since so far it's only been tuned on mine.

Code (Apache 2.0): https://github.com/TidyMaze/loom
APK: https://github.com/TidyMaze/loom/releases/latest/download/app-release.apk
```
