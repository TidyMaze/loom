# Show HN (Hacker News)

## HN rules that apply (checked 2026-10-15)

- **"Don't post generated text or AI-edited text."** and **"please don't automate posting"** ([guidelines](https://news.ycombinator.com/newsguidelines.html)). So the title and the first comment must be written by Yann, without AI editing, and posted by hand. This file holds facts and notes only, not a text to paste.
- Show HN must be something people can try now, without signup ([Show HN rules](https://news.ycombinator.com/showhn.html)). The direct APK link covers that.
- "Don't post quickly-generated one-offs [...] Share something deeply personal and interesting to you. Explain how and why." Say you've used it daily for 6 months and why you built it. Be upfront that a lot of the code was written with an AI assistant; HN will ask.
- You must be around to discuss: post when you have 2 to 3 hours free to answer comments.
- Title must start with "Show HN:". No gratuitous numbers or superlatives in the title.

## Submit

- Form: https://news.ycombinator.com/submit
- URL field: `https://github.com/TidyMaze/loom`
- Timing: Tuesday to Thursday, 14:00 to 16:00 CET (US morning).
- Then post your first comment right away in the thread.

## Facts to draw from (all verified in the repo)

- What: minimal Android launcher, dark grid of app icons, no labels/badges/folders/widgets. Swipe down to search. Apps you never open stay off the home screen.
- Ranking: hand-tuned linear model in Kotlin, on the device. Features: recency (exponential decay), hour and weekday (Gaussian kernels), previous app (Markov transitions). Two regimes depending on the gap since the last launch (in-session <= 70 s vs cold start). Weights tuned offline with `scripts/bench.py` (Optuna).
- Latest evaluation (ScoreEngine v17, commit 2a4cd4c, 2026-09-29): trained on the first 80% of a 15,615-event personal log, tested once on 3,123 held-out launches. hit@1 38.6% (the app you want is first), hit@5 70.9%, MRR 0.533. Previous version: hit@1 35.9%, MRR 0.510.
- ⚠ The older draft claimed "37% better than sorting by recency" and "83% in the top 10" on 10,472 launches. Those came from an older model and evaluation; re-run `scripts/bench.py` against a recency baseline before quoting them.
- Privacy: no INTERNET permission, no Google Play Services (removed in 1.3.1), no account, no ads. Anonymous opt-in stats might come later.
- Size: APK 790 KB, Android 8+, Apache 2.0.
- Status: early alpha, used daily as the only launcher for 6 months. F-Droid inclusion pending (fdroid/fdroiddata!52561).
- Inspiration: Yasan Launcher (https://play.google.com/store/apps/details?id=yasan.space.mnml.ai.launcher). Credit it.
- Links: repo https://github.com/TidyMaze/loom, APK https://github.com/TidyMaze/loom/releases/latest/download/app-release.apk

## Questions HN is likely to ask (prepare your own answers)

- Why a hand-tuned linear model instead of a trained one (on-device size, explainability, one user's data)?
- How do you evaluate it, and against which baseline?
- How much of the code is AI-written, and how do you test it?
- Battery: does it run anything in the background? (Check before answering: notification listener and usage-access sync are optional.)
- Why not just use Yasan Launcher, Niagara, Olauncher...?
