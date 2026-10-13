package com.yrolland.loom

import android.content.Context
import android.content.SharedPreferences

class OnboardingStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun isOnboardingCompleted(): Boolean =
        prefs.getBoolean(KEY_COMPLETED, false)

    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    companion object {
        const val PREFS_NAME = "loom_onboarding"
        const val KEY_COMPLETED = "onboarding_completed"
    }
}
