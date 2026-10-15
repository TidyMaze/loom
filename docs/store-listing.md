# Loom: Play Store Listing

## Short description
A minimal Android launcher that quietly learns how you use your phone.

## Full description
Loom is a minimal Android launcher that quietly learns how you use your phone.

The home screen is a dark grid of app icons: no labels, no badges, no folders, no widgets. Apps you use are ordered by how likely you are to open them now, based on time of day, day of the week and the app you just closed. Apps you never open stay off the home screen.

**How it works**
Every time you open an app, Loom remembers. The ranking is plain arithmetic computed on the phone (no LLM, no neural network, no server), and it runs only when the home screen opens.

**Features**
• Smart ranking: apps surface when you actually use them
• Search: swipe down to find any app
• Hide any app: long-press > Hide, or choose several in Settings
• Option to turn off the "Search the web" button
• D-pad and keyboard navigation
• Long-press options: app shortcuts, hide app, reset history, app info
• Pure black UI, easy on OLED screens
• Works without granting any permission

**Open Source**
Loom is fully open source: https://github.com/TidyMaze/loom

**Privacy**
No INTERNET permission: your launch history stays on your device. No account, no ads. If anonymous usage stats are ever added to improve the ranking, they will be off unless you turn them on.
