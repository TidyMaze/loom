package com.yrolland.loom

import androidx.annotation.StringRes

data class OnboardingStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val iconRes: Int
)

object OnboardingContent {
    val steps = listOf(
        OnboardingStep(
            titleRes = R.string.onboarding_welcome_title,
            bodyRes = R.string.onboarding_welcome_body,
            iconRes = R.drawable.ic_onboarding_welcome
        ),
        OnboardingStep(
            titleRes = R.string.onboarding_ranking_title,
            bodyRes = R.string.onboarding_ranking_body,
            iconRes = R.drawable.ic_onboarding_ranking
        ),
        OnboardingStep(
            titleRes = R.string.onboarding_context_title,
            bodyRes = R.string.onboarding_context_body,
            iconRes = R.drawable.ic_onboarding_permissions
        ),
        OnboardingStep(
            titleRes = R.string.onboarding_search_title,
            bodyRes = R.string.onboarding_search_body,
            iconRes = R.drawable.ic_onboarding_search
        ),
        OnboardingStep(
            titleRes = R.string.onboarding_privacy_title,
            bodyRes = R.string.onboarding_privacy_body,
            iconRes = R.drawable.ic_onboarding_privacy
        )
    )
}

/** Clamps a step index into [0, count - 1]; used by the onboarding pager for swipe, back and next. */
internal fun clampOnboardingStep(index: Int, count: Int): Int =
    index.coerceIn(0, (count - 1).coerceAtLeast(0))
