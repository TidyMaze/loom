# Non-regression test plan

Manual end-to-end checks to run before every release. Each test lists the steps, the expected result and a reference screenshot in [screenshots/](screenshots/).

**Last run:** 2026-10-15, v1.3.4 (versionCode 7), signed release APK, Android emulator `test_avd` (1080x2400, API 36). Result: all tests pass, including section G (added in 1.3.4). The first run (v1.3.1) found the issues listed at the end; all are fixed in 1.3.2 and re-checked.

## Setup

```bash
./gradlew test assembleRelease          # unit tests + release APK
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell cmd package set-home-activity com.yrolland.loom/.MainActivity
adb logcat -c                            # so crashes from this run are easy to spot
```

Release-build checks: `unzip -l app/build/outputs/apk/release/app-release.apk | grep -ci gms` returns 0 (no Google Play Services), and `apksigner verify` shows the release certificate.

After the run: `adb logcat -d -b crash` must be empty.

## A. Fresh install and onboarding

| # | Steps | Expected | Screenshot |
|---|---|---|---|
| A1 | `adb shell pm clear com.yrolland.loom`, press Home | Onboarding page 1 "Welcome to Loom" with Continue and Skip | `01_onboarding_step1.png` |
| A2 | Tap Continue | Page 2 "Smart Ranking" | `02_onboarding_step2.png` |
| A3 | Tap Continue | Page 3 "Context Awareness" lists exactly the optional signals: location, calendar events, connected Bluetooth devices (no "activity") | `03_onboarding_step3.png` |
| A4 | Tap Continue | Page 4 "Swipe Down to Search" | `04_onboarding_step4.png` |
| A5 | Tap Continue | Page 5 "Private by Default": offline today, future anonymous stats only if the user turns them on (no "ever"). Button reads "Get Started" | `05_onboarding_step5.png` |
| A6 | Tap Get Started | System permission prompts: location, calendar, nearby devices. No activity-recognition prompt (removed in 1.3.1) | `06_onboarding_step6.png` |
| A7 | Deny every prompt | Home shows every installed app, dimmed (nothing learned yet). No crash | `07_home_fresh_all_permissions_denied.png` |

## B. Home and launching

| # | Steps | Expected | Screenshot |
|---|---|---|---|
| B1 | Tap a tile | The app opens (check `dumpsys window` focus) | |
| B2 | Press Home after the first launch on a fresh install | The launched app comes first, followed by other apps up to 12 tiles (not a single icon) | `08_home_after_first_launch.png` |
| B3 | Use a dozen apps a few times each, wait 75 s, go Home | Grid shows only launched apps, most likely ones first | `09_home_with_history.png` |
| B4 | `uiautomator dump` on home | Every tile has a content description equal to the app name (TalkBack reads it) | |

## C. Search

| # | Steps | Expected | Screenshot |
|---|---|---|---|
| C1 | Swipe down on the grid | Search field appears with "Search apps…" and keyboard | `30_search_open.png` |
| C2 | Type `clo` | Grid filters to matching apps (Clock) | `31_search_filtered_clo.png` |
| C3 | Press Enter | The top match (Clock) opens | |
| C4 | Search `zzqxw` | No results; "Search the web" and "Ask Gemini (Google)" buttons appear | `32_search_empty_state_fallbacks.png` |
| C4b | Tap "Search the web" | The default web search app opens with the query (no hardcoded engine) | |
| C4c | Tap "Ask Gemini (Google)" | Gemini app if installed, otherwise the system share sheet | |
| C5 | Press Back twice | Keyboard closes, then search field hides; still on Loom | |

## D. Long-press menu

| # | Steps | Expected | Screenshot |
|---|---|---|---|
| D1 | Long-press a tile | Sheet with app name, app shortcuts (if any), Hide app, Reset stats, App info | `40_longpress_sheet.png` |
| D2 | Tap App info | System app details screen for that app | `42_app_info_from_sheet.png` |
| D3 | Tap Hide app | Tile disappears from home; app is listed under Settings > Hidden | |
| D4 | Tap Reset stats | Confirm dialog "Reset app stats?" naming the app; Reset removes its history | `41_reset_confirm_dialog.png` |

## E. Settings (tap the greeting)

| # | Steps | Expected | Screenshot |
|---|---|---|---|
| E1 | Tap "Good evening." | Loom settings: Layout, Hidden, Tracking, Data | `50_settings.png` |
| E2 | Hidden > Unhide | App leaves the hidden list and comes back on home | |
| E3 | Grid columns > 3 columns | Status shows "3 columns"; home grid uses 3 columns | `10_settings_grid_columns_picker.png`, `11_settings_main.png`, `13_home_3_columns.png` |
| E4 | Icon size | Picker: Compact (36dp), Medium (44dp, default), Spacious (54dp) | `12_settings_icon_size_picker.png` |
| E5 | Usage access | Opens the system usage-access screen; status says "Not granted" until enabled | `51_usage_access_system_screen.png` |
| E6 | Notification access | Opens the system notification-access screen | `52_notification_access_system_screen.png` |
| E7 | Clear usage data | Confirm dialog "Clear usage data?" (Cancel keeps data) | `53_clear_usage_confirm.png` |
| E8 | Reset everything | Confirm dialog (Cancel keeps data) | `54_reset_everything_confirm.png` |

## F. Learning (ranking adapts to usage)

| # | Steps | Expected | Screenshot |
|---|---|---|---|
| F1 | From a state where only Settings has history, open Camera once and Clock 3 times via search, then go Home right away | Order changes. Clock (just closed) is ranked last: the "self penalty" pushes the app you just left down for about 70 s (`ScoreEngine.kt`, `SELF_PENALTY`, `SESSION_MS`) | `20_learning_after_launches.png` |
| F2 | Wait 75 s, open and close Loom settings to refresh | Clock (3 launches) is now first, then Camera, then Settings | `21_learning_after_session_gap.png` |

Read the order from the tiles' content descriptions (`uiautomator dump`), or with a long-press on each tile.

## G. Hiding, web button, D-pad (added in 1.3.4)

| # | Steps | Expected | Screenshot |
|---|---|---|---|
| G1 | Launch 12 different apps, go Home | Only launched apps are on home; an app never opened (e.g. YouTube) is not shown | |
| G2 | Long-press an app > Hide app | It leaves home; searching its name finds nothing | `33_search_hidden_app_not_found.png` |
| G3 | Settings > Choose apps to hide…, tick an app, Done | It appears under the hidden list in Settings and leaves home | `55_settings_choose_apps_to_hide.png`, `56_settings_hidden_list.png` |
| G4 | Settings > Web search button (toggle off), search `zzqxw` | No "Search the web" / "Ask Gemini" buttons | `34_search_no_match_web_button_off.png` |
| G5 | D-pad Down/Left/Right/Up on home | Focus moves tile by tile, focused tile is outlined; Right on the last column stays put | `60_dpad_focus.png` |
| G6 | D-pad Up from the top row, then Down | Up focuses the search field, Down returns to the first tile | |
| G7 | D-pad Center on a tile | That app opens | |
| G8 | Type a letter on a hardware keyboard from home | Search opens with that letter and filters | |

## Issues found on 2026-10-14 (all fixed in 1.3.2)

1. [#2](https://github.com/TidyMaze/loom/issues/2) Onboarding page 3 still mentions "activity", which was removed in 1.3.1.
2. [#3](https://github.com/TidyMaze/loom/issues/3) Onboarding page 5 says "no data sent to servers ... ever", which conflicts with the planned opt-in usage stats.
3. [#4](https://github.com/TidyMaze/loom/issues/4) Home tiles have no accessible name, so TalkBack cannot announce which app a tile opens.
4. [#5](https://github.com/TidyMaze/loom/issues/5) After the first launch on a fresh install, home drops from all apps to that single app.
5. [#6](https://github.com/TidyMaze/loom/issues/6) The app you just closed sinks to last place for ~70 s, which looks like the ranking forgot it (by design, but confusing).
6. [#7](https://github.com/TidyMaze/loom/issues/7) (closed) README, F-Droid metadata and launch posts described a text-only list; the app has been an icon grid since August 2026.
