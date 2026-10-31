package com.yrolland.loom

import android.content.Context
import androidx.core.content.ContextCompat

/**
 * Resolved colours for text on the home screen. Built from the loom_* tokens
 * (values/colors.xml and values-night/colors.xml), so light and dark both work.
 * Rank bar and focus colours come straight from the drawables and tokens.
 */
data class Accent(
    val greeting: Int,
    val now: Int,
    val textSecondary: Int
)

// Single gold palette. Greeting text still shifts by hour ("Good morning." etc),
// but the accent colour stays gold throughout the day.
fun accentForNow(context: Context): Accent = Accent(
    greeting = ContextCompat.getColor(context, R.color.loom_text),
    now = ContextCompat.getColor(context, R.color.loom_accent),
    textSecondary = ContextCompat.getColor(context, R.color.loom_text_2)
)
