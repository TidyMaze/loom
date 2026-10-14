# Launch Communication Plan (ordered by popularity tier)

Each channel is placed at the **minimum popularity it needs** so a post doesn't fall flat. Plans are in English; the messages themselves are in the language of their audience (`fr/` holds the French ones).

---

## Tier 1: Day 0 (0 stars, 0 users, 0 followers)
*These channels judge the project on its own merits. No existing audience needed.*
*Play Store: **not required** (GitHub and F-Droid are enough).*

| Channel | Action | Status | Why it works at 0 stars | Document |
|---|---|---|---|---|
| **F-Droid (RFP)** | Request for packaging | ✅ Submitted 2026-10-14 ([#4573](https://gitlab.com/fdroid/rfp/-/work_items/4573)) | Maintainers review code and license, not social proof. | [01_fdroid_rfp.md](01_fdroid_rfp.md) |
| **Hacker News (Show HN)** | Show HN post | To do | HN likes raw, open-source projects without marketing. | [02_show_hn.md](02_show_hn.md) |
| **Niche Reddit** | `r/opensource`, `r/dumbphones` (not `r/digitalminimalism`, see below) | To do, **write in your own words** | These communities look for concrete tools. A modest post works. | [03_reddit_posts.md](03_reddit_posts.md) |
| **Shorts / TikTok / Reels** | 2-3 short videos | To do | Distribution depends on retention in the first seconds, not followers. | [07_short_video_scripts.md](07_short_video_scripts.md) / [fr/](fr/07_short_video_scripts_fr.md) |

---

## Tier 2: Early traction (50 to 150 stars, ~100 users)
*Needs some social proof and stability confirmed on several devices.*
*Play Store: **strongly recommended** to remove the sideloading barrier.*

| Channel | Action | Play Store needed? | Prerequisite | Document |
|---|---|---|---|---|
| **Large Reddit** | `r/androidapps`, `r/france`, `r/developpeurs` | Strongly recommended for r/androidapps | Stricter moderation. Have feedback from real testers first. | [03_reddit_posts.md](03_reddit_posts.md) / [fr/](fr/03_reddit_posts_fr.md) |
| **Awesome list PRs** | PRs to `awesome-android` and `awesome-privacy` | No (GitHub link) | Many maintainers turn down repos under ~50 stars. | [05_awesome_lists.md](05_awesome_lists.md) |
| **Google Play Store** | End of 14-day closed testing, public release | This is the goal of the tier | Needs 12 to 20 active testers, recruited in Tier 1. | [09_play_store_aso_matrix.md](09_play_store_aso_matrix.md) / [fr/](fr/09_play_store_aso_matrix_fr.md) |

---

## Tier 3: Established community (300 to 500+ stars, 1,000+ installs)
*Launching these at 0 is a near-certain failure.*
*Play Store: **required** (press and Product Hunt audiences expect a store link).*

| Channel | Action | Play Store needed? | Prerequisite | Document |
|---|---|---|---|---|
| **Product Hunt** | Launch day at 00:01 PT (09:01 CET) | Yes (most visitors won't sideload an APK) | Without users ready to upvote on launch day, the project ends outside the top 10. | [04_product_hunt.md](04_product_hunt.md) |
| **Tech press** | Android Police, Android Authority, Numerama, Frandroid | Yes | Journalists cover projects that already have some buzz or a track record. | [06_press_pitch.md](06_press_pitch.md) / [fr/](fr/06_press_pitch_fr.md) |
| **Open-source press** | Korben, LinuxFr | No (GitHub / F-Droid welcome) | These outlets like pure open-source alternatives. | [fr/06_press_pitch_fr.md](fr/06_press_pitch_fr.md) |
| **In-app sharing** | "Share my setup" feature | Yes (people want a store button) | Only produces visible volume with thousands of daily users. | [08_viral_share_feature_spec.md](08_viral_share_feature_spec.md) |

---

## Subreddit rules (checked 2026-10-14)

- **r/digitalminimalism: do not post.** Rule 2 bans promotion of any app, "zero tolerance, permanent ban". Rule 3 bans AI-generated posts, even AI translation. The only allowed place for apps, the "Screen time apps to explore" megathread, is locked and archived.
- **r/opensource: allowed with care.** Use the **Promotional** flair. The repo needs an OSI license (Apache 2.0, OK). "All AI-generated content is low-effort and ban worthy", and drive-by posts get removed: write the post yourself and stay to answer comments.
- **r/dumbphones: OK, best fit.** No promotion ban. The pinned "State of the Dumbphone 2026" post recommends customizing the smartphone you already have ("If you have an Android device... customize your device"), which is exactly Loom. Maker posts do well there: "Nokia-style Android launcher | Simple Dumbphone" and "My DIY Dumbphone Project" are among the top posts of the past year. Flair: **General discussion** (no app/showcase flair exists). A mod bot restricts some flairs by weekday; its schedule isn't public, so if the post is auto-removed, retry another day. Say plainly that you made it, write the text yourself, add screenshots, and stay to answer. **Posting requires an acknowledgement in the "Read The Rules" app** (subreddit menu "...", then Read The Rules; valid 90 days), otherwise the post is auto-removed. Posted 2026-10-14: https://www.reddit.com/r/dumbphones/comments/1x612io/
- **General rule:** the drafts in this folder are notes. Several communities ban AI-written posts, so rewrite them in your own words before posting, and never post them verbatim.

## Shared privacy message (all channels)

- **Today**: 100% local, no INTERNET permission, no data sent.
- **Later**: anonymous usage stats (to improve the model) may be collected, **opt-in only**, never on by default.
- Never write "no data will ever leave the phone" without "for now": the promise must stay true once opt-in ships.
- When opt-in ships: update [privacy.md](../privacy.md), the README, the onboarding screen and the Play Store "Data safety" form before the release.

## Shared product description (all channels)

- The home screen is a **dark grid of app icons** (no labels, badges, folders or widgets). It has not been a text-only list since August 2026, so don't describe it that way.
- Be upfront: early alpha, built largely with an AI coding assistant, ranking tuned on one person's data so far.

---

## Play Store dependency

1. **Tier 1 can start today without the Play Store.** F-Droid, Hacker News and the minimalism subreddits are happy with direct open-source releases. Don't wait for Google to start getting the first stars and feedback.
2. **Play Store closed testing (14 days) runs in parallel.** Recruit the 12 to 20 testers Google requires from the first Tier 1 users, so the public release lands around Tier 2 and opens `r/androidapps`, Product Hunt and the press.

---

## Recommended schedule

1. **Week 1 (day 0 to 7)**:
   - ✅ Open the F-Droid RFP.
   - Post on Hacker News (mid-week, Tuesday to Thursday, 14:00 to 16:00 CET).
   - Post on `r/digitalminimalism` and `r/opensource`.
   - Post the first video on TikTok / Shorts.
   - Start Play Store closed testing as soon as testers sign up.
2. **Weeks 2 to 3 (from ~50 stars)**:
   - Send the PRs to `awesome-android` and `awesome-privacy`.
   - Post on `r/androidapps` and `r/developpeurs`.
3. **Month 2 (from ~300 stars)**:
   - Plan the Product Hunt launch.
   - Send personalised pitches to the tech press with usage numbers.
