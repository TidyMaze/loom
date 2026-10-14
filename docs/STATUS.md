# Loom status (2026-10-14)

## App

- **Version:** v1.3.1 (versionCode 4), released 2026-10-14 on [GitHub](https://github.com/TidyMaze/loom/releases/tag/v1.3.1). Signed APK 790 KB, plus an AAB for the Play Store.
- **What changed in 1.3.1:** Google Play Services removed (activity recognition was logged but never used by the ranking), so the app is fully free software and eligible for F-Droid. The `ACTIVITY_RECOGNITION` permission is gone.
- **Privacy:** no INTERNET permission, everything on the device. Anonymous usage stats may come later, opt-in only.
- **Tests:** 36 unit tests green. Full manual end-to-end run on an emulator passed, see [testing/NON_REGRESSION.md](testing/NON_REGRESSION.md) and its screenshots. The ranking still learns: after 3 launches, Clock moves to first place once the 70 s session window has passed.
- **Known issues:** 6 found during the test run, all filed on [GitHub Issues](https://github.com/TidyMaze/loom/issues). The most visible: home collapses to a single icon after the first launch on a fresh install, and tiles have no accessible label for screen readers.
- **Stage:** early alpha, built largely with an AI coding assistant, ranking tuned on one person's data so far.

## Communication plan

Full plan: [launch/00_communication_plan.md](launch/00_communication_plan.md).

| Step | Status |
|---|---|
| F-Droid request for packaging | Submitted 2026-10-14, [fdroid/rfp#4573](https://gitlab.com/fdroid/rfp/-/work_items/4573), waiting for review |
| Show HN | Draft ready ([02_show_hn.md](launch/02_show_hn.md)), post Tuesday to Thursday 14:00 to 16:00 CET |
| Reddit r/digitalminimalism, r/opensource, r/dumbphones | Drafts ready ([03_reddit_posts.md](launch/03_reddit_posts.md)) |
| Short videos | Scripts ready, promo video in `docs/loom_promo.mp4` |
| Play Store closed testing | Not started (needs a developer account and 12 to 20 testers) |
| Tier 2 and 3 (awesome lists, large subreddits, Product Hunt, press) | Waiting for stars and the Play Store release |

All drafts were rewritten in a plain first-person voice on 2026-10-14 and now describe the app as it is (icon grid, 790 KB, no Play Services).
