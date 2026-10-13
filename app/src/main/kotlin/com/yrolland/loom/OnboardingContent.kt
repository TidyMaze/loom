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
            title = "Swipe Down to Search",
            subtitle = "Need an app right now? Swipe down anywhere on your home screen to filter instantly.",
            iconRes = R.drawable.ic_onboarding_search
        ),
        OnboardingStep(
            title = "Zero Tracking. 100% Private",
            subtitle = "Loom operates completely offline. No accounts, no data collected, no ads, ever.",
            iconRes = R.drawable.ic_onboarding_privacy
        )
    )
}
