# F-Droid RFP (Request For Packaging) Submission

Open a new issue on the [F-Droid RFP Issue Tracker](https://gitlab.com/fdroid/rfp/-/issues/new).

## Issue Title
`RFP: com.yrolland.loom - Loom (Minimalist Android Launcher)`

## Issue Body

```markdown
### Application ID
`com.yrolland.loom`

### Application Name
Loom

### Summary
Minimal Android launcher that quietly learns how you use your phone

### Description
Loom is a minimal icon-grid launcher (no labels, badges or widgets). It orders apps by how likely you are to open them now, using a small scoring model that runs on the device (recency, time of day, day of week, app-to-app transitions).

It has no INTERNET permission, no analytics, no ads and no Google Play Services (removed in 1.3.1 for F-Droid). If anonymous usage stats are ever added to improve the model, they will be opt-in. The app works without granting any runtime permission.

### Source Code
https://github.com/TidyMaze/loom

### Issue Tracker
https://github.com/TidyMaze/loom/issues

### License
Apache-2.0

### Build System
Gradle (Kotlin DSL), Android SDK 36, Java 17

### F-Droid Metadata Recipe
A ready-to-use metadata recipe is already located in the repository:
`metadata/com.yrolland.loom.yml`
```
