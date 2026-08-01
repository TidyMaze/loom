package com.yrolland.loom

import java.net.URLEncoder

object SearchFallback {

    fun shouldShowEmptyState(query: String, hasAppMatches: Boolean): Boolean =
        query.trim().isNotEmpty() && !hasAppMatches

    fun buildChromeUrl(query: String): String =
        "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")

    fun buildGeminiUrl(query: String): String =
        "https://gemini.google.com/app?q=" + URLEncoder.encode("Tell me about $query", "UTF-8")
}
