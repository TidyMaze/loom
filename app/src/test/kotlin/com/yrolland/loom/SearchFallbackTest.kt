package com.yrolland.loom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFallbackTest {

    @Test
    fun `shouldShowEmptyState returns false when query is empty`() {
        assertFalse(SearchFallback.shouldShowEmptyState("", hasAppMatches = false))
        assertFalse(SearchFallback.shouldShowEmptyState("   ", hasAppMatches = false))
    }

    @Test
    fun `shouldShowEmptyState returns false when search matches apps`() {
        assertFalse(SearchFallback.shouldShowEmptyState("chrome", hasAppMatches = true))
    }

    @Test
    fun `shouldShowEmptyState returns true when non empty query has zero matches`() {
        assertTrue(SearchFallback.shouldShowEmptyState("quantum computing", hasAppMatches = false))
    }

    @Test
    fun `shouldShowEmptyState returns false when the web search button is turned off`() {
        assertFalse(SearchFallback.shouldShowEmptyState("quantum computing", hasAppMatches = false, webFallbackDisabled = true))
    }

    @Test
    fun `buildGeminiPrompt formats pre-prompt correctly`() {
        val prompt = SearchFallback.buildGeminiPrompt("quantum computing")
        assertEquals("Tell me about quantum computing", prompt)
    }

    @Test
    fun `filterApps falls back to all apps when query is empty and no app has launch count`() {
        val app1 = AppEntry("com.app.one", "Alpha", 0f, launchCount = 0, lastLaunchedMillis = null)
        val app2 = AppEntry("com.app.two", "Beta", 0f, launchCount = 0, lastLaunchedMillis = null)
        val fullList = listOf(app1, app2)

        val result = SearchFallback.filterApps(fullList, "")
        assertEquals(2, result.size)
        assertEquals(listOf(app1, app2), result)
    }

    @Test
    fun `filterApps keeps unlaunched apps after launched ones while history is short`() {
        val unlaunchedFirst = AppEntry("com.app.two", "Beta", 0f, launchCount = 0, lastLaunchedMillis = null)
        val launched = AppEntry("com.app.one", "Alpha", 1f, launchCount = 5, lastLaunchedMillis = 1000L)

        val result = SearchFallback.filterApps(listOf(unlaunchedFirst, launched), "")
        assertEquals(listOf(launched, unlaunchedFirst), result)
    }

    @Test
    fun `filterApps shows only launched apps once they fill the home screen`() {
        val launched = (1..SearchFallback.HOME_MIN_TILES).map {
            AppEntry("com.launched.$it", "L$it", 1f, launchCount = 1, lastLaunchedMillis = 1000L)
        }
        val unlaunched = AppEntry("com.app.none", "Zeta", 0f, launchCount = 0, lastLaunchedMillis = null)

        val result = SearchFallback.filterApps(launched + unlaunched, "")
        assertEquals(launched, result)
    }

    @Test
    fun `filterApps filters by query when query is non empty`() {
        val app1 = AppEntry("com.app.one", "Alpha", 1f, launchCount = 5, lastLaunchedMillis = 1000L)
        val app2 = AppEntry("com.app.two", "Beta", 0f, launchCount = 0, lastLaunchedMillis = null)
        val fullList = listOf(app1, app2)

        val result = SearchFallback.filterApps(fullList, "bet")
        assertEquals(1, result.size)
        assertEquals("Beta", result[0].label)
    }
}
