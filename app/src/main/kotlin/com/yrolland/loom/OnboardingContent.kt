package com.yrolland.loom

data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val iconRes: Int
)

object OnboardingContent {
    val steps = listOf(
        OnboardingStep(
            title = "Welcome to Loom",
            subtitle = "A minimal launcher that quietly learns your habits without clutter, folders, or widgets.",
            iconRes = R.drawable.ic_onboarding_welcome
        ),
        OnboardingStep(
            title = "Smart Ranking",
            subtitle = "Your apps surface automatically based on the time of day, day of week, and routines.",
            iconRes = R.drawable.ic_onboarding_ranking
        ),
        OnboardingStep(
            title = "Context Awareness",
            subtitle = "If you allow it, Loom also uses your location, calendar events and connected Bluetooth devices to predict what you need. Everything is processed on your phone.",
            iconRes = R.drawable.ic_onboarding_permissions
        ),
        OnboardingStep(
            title = "Swipe Down to Search",
            subtitle = "Need an app right now? Swipe down anywhere on your home screen to filter instantly.",
            iconRes = R.drawable.ic_onboarding_search
        ),
        OnboardingStep(
            title = "Private by Default",
            subtitle = "Loom works offline: no account, no ads, nothing sent anywhere. If anonymous usage stats are added one day to improve the ranking, they will stay off unless you turn them on.",
            iconRes = R.drawable.ic_onboarding_privacy
        )
    )
}
