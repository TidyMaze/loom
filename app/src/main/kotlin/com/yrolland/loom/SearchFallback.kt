package com.yrolland.loom

import android.app.SearchManager
import android.content.Context
import android.content.Intent

object SearchFallback {

    fun shouldShowEmptyState(query: String, hasAppMatches: Boolean, webFallbackDisabled: Boolean = false): Boolean =
        !webFallbackDisabled && query.isNotBlank() && !hasAppMatches

    fun filterApps(fullList: List<AppEntry>, query: String): List<AppEntry> {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            return fullList.filter { FuzzyMatcher.matches(trimmed, it.label) }
        }
        // Launched apps first; while history is short, fill the screen with the rest so a new user isn't left with one icon.
        val (launched, rest) = fullList.partition { it.launchCount > 0 }
        return launched + rest.take((HOME_MIN_TILES - launched.size).coerceAtLeast(0))
    }

    const val HOME_MIN_TILES = 12

    /** Hands the query to the user's default search app instead of a hardcoded engine. */
    fun createWebSearchIntent(query: String): Intent =
        Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)

    fun buildGeminiPrompt(query: String): String =
        "Tell me about $query"

    fun createGeminiIntent(context: Context, query: String): Intent {
        val prompt = buildGeminiPrompt(query)
        val directIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, prompt)
            setClassName("com.google.android.apps.bard", "com.google.android.apps.bard.shellapp.BardEntryPointActivity")
        }
        val isCallable = runCatching {
            context.packageManager.queryIntentActivities(directIntent, 0).isNotEmpty()
        }.getOrDefault(false)

        return if (isCallable) {
            directIntent
        } else {
            // No Gemini app: let the user pick any app that accepts text.
            Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, prompt)
            }, null)
        }
    }
}
