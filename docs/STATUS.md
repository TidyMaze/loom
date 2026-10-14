# Loom status (2026-10-14)

## App

- **Version:** v1.3.3 (versionCode 6), released 2026-10-14 on [GitHub](https://github.com/TidyMaze/loom/releases/tag/v1.3.3). Signed APK 790 KB, plus an AAB for the Play Store.
- **What changed in 1.3.3:** no code change. Store descriptions (fastlane), screenshots, icon and changelog now match the current app, for F-Droid.
- **What changed in 1.3.2:** fixes the 5 issues from the first test run (#2 to #6), web search goes to the default search app instead of a hardcoded google.com URL, Gradle wrapper checksum added (both flagged by the F-Droid scanner).
- **What changed in 1.3.1:** Google Play Services removed (activity recognition was logged but never used by the ranking), so the app is fully free software and eligible for F-Droid. The `ACTIVITY_RECOGNITION` permission is gone.
- **Privacy:** no INTERNET permission, everything on the device. Anonymous usage stats may come later, opt-in only.
- **Tests:** 36 unit tests green. Full manual end-to-end run on an emulator passed, see [testing/NON_REGRESSION.md](testing/NON_REGRESSION.md) and its screenshots.
- **Known issues:** the 6 found during the first test run are fixed and closed on [GitHub Issues](https://github.com/TidyMaze/loom/issues).
- **Stage:** early alpha, built largely with an AI coding assistant, ranking tuned on one person's data so far. Inspired by [Yasan Launcher](https://play.google.com/store/apps/details?id=yasan.space.mnml.ai.launcher).
- **Support:** [buymeacoffee.com/tidymaze](https://buymeacoffee.com/tidymaze) is live; payouts (Stripe) not connected yet, so it can't take payments.

## Communication plan

Full plan: [launch/00_communication_plan.md](launch/00_communication_plan.md).

| Step | Status |
|---|---|
| F-Droid request for packaging | [fdroid/rfp#4573](https://gitlab.com/fdroid/rfp/-/work_items/4573), with correction and 1.3.2 follow-up |
| F-Droid merge request (fdroiddata) | Recipe on fork branch `com.yrolland.loom`, fork CI 12/12 green, MR being opened |
| r/dumbphones | Posted: [1x612io](https://www.reddit.com/r/dumbphones/comments/1x612io/), 1 question answered (APK link) |
| r/digitalminimalism | Not allowed: app promotion and AI-written posts are banned |
| r/opensource | To do, must be written by Yann (AI-written content banned), Promotional flair |
| Show HN | Draft ready ([02_show_hn.md](launch/02_show_hn.md)), check HN guidelines, post Tuesday to Thursday 14:00 to 16:00 CET |
| Short videos | Scripts ready, promo video in `docs/loom_promo.mp4` |
| Play Store closed testing | Not started (needs a developer account and 12 to 20 testers) |
| Tier 2 and 3 (awesome lists, large subreddits, Product Hunt, press) | Waiting for stars and the Play Store release |

Rule: read each site's or subreddit's rules before posting, and record them in the plan.
