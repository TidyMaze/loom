# Show HN (Hacker News) Launch Post

Submit on [news.ycombinator.com/submit](https://news.ycombinator.com/submit).

## Title
`Show HN: Loom – Minimalist Android launcher that quietly learns your habits (100% on-device)`

## URL
`https://github.com/TidyMaze/loom`

## First Comment (from Author)

```markdown
Hi HN!

I built Loom because I was fed up with dopamine-trap Android homescreens packed with red badges, colorful icons, and widgets fighting for my attention.

At the same time, existing "minimalist" launchers often force you to scroll through alphabetical lists or set static favorites that quickly become obsolete throughout the day.

Loom takes a different approach:
It provides a distraction-free, pure black typography list, backed by a lightweight on-device scoring engine written in pure Kotlin.

How it works:
- Recency decay: Exponential time decay based on last launch timestamp.
- Time-of-day & day-of-week affinity: Gaussian kernel centered around current hour and weekday vs weekend patterns.
- Transition bigrams: Probabilistic Markov transitions (P(App B | App A)) to anticipate multi-app workflows.
- Zero analytics / zero tracking: No internet permission in AndroidManifest.xml. Not a single byte ever leaves the phone.
- Micro footprint: APK size is under 1 MB (< 969 KB), sub-millisecond inference time, and zero background battery drain.

On my own real-world usage log (10,472 app launches across 112 days):
- 31.8% of launches are already ranked #1 on screen.
- Over 83% of launches are in the top 10 (no search or scrolling needed).
- MRR of 0.483, outperforming a naive recency baseline by +37% relative.

The project is completely open source under Apache 2.0. APK downloads are on the GitHub releases page, and an F-Droid recipe is in the works.

Note on project status:
Loom is in its early Alpha stage and actively vibe-coded. The core local math engine and gesture handling are solid and tested, but expect rapid iterations and evolving features. Your bug reports, feedback on edge cases, and thoughts on focus-first UI are super valuable!

GitHub: https://github.com/TidyMaze/loom
Direct APK: https://github.com/TidyMaze/loom/releases/latest/download/app-release.apk

I would love to hear your thoughts, feedback on the scoring model, or features you value in a focus-first launcher!
```
