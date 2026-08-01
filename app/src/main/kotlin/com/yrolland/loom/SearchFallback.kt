package com.yrolland.loom

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

object SearchFallback {

    fun shouldShowEmptyState(query: String, hasAppMatches: Boolean): Boolean =
        query.trim().isNotEmpty() && !hasAppMatches

    fun buildChromeUrl(query: String): String =
        "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")

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
            val genericSendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, prompt)
            }
            val genericCallable = runCatching {
                context.packageManager.queryIntentActivities(genericSendIntent, 0).isNotEmpty()
            }.getOrDefault(false)

            if (genericCallable) {
                genericSendIntent
            } else {
                val webUrl = "https://gemini.google.com/app?q=" + URLEncoder.encode(prompt, "UTF-8")
                Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
            }
        }
    }
}
