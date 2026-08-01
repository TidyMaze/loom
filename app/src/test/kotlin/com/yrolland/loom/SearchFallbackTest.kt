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
    fun `buildChromeUrl formats search query correctly`() {
        val url = SearchFallback.buildChromeUrl("quantum computing")
        assertEquals("https://www.google.com/search?q=quantum+computing", url)
    }

    @Test
    fun `buildGeminiUrl formats prompt query correctly`() {
        val url = SearchFallback.buildGeminiUrl("quantum computing")
        assertEquals("https://gemini.google.com/app?q=Tell+me+about+quantum+computing", url)
    }
}
