# Viral Share Feature Specification: "Share My Setup"

## Goal
Allow users to generate and share a clean, high-resolution aesthetic screenshot card of their personalized Loom homescreen to Reddit (`r/androidthemes`, `r/digitalminimalism`), Twitter/X, and Instagram.

## UX Flow
1. User opens Settings or long-presses background.
2. Selects **Share Minimalist Setup**.
3. Loom renders a 1080x1920 or square card containing:
   - Their top 5 ranked apps for the current time of day.
   - Current time & clean date indicator.
   - Minimalist footer text: `Setup on Loom • Minimalist Android Launcher`.
4. Opens standard Android share sheet (`Intent.ACTION_SEND` with image URI via FileProvider).

## Privacy Guarantee
- Only package labels are shown, no personal data, no notification content, no location.
