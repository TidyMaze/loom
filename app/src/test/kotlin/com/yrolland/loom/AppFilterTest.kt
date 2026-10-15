package com.yrolland.loom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterTest {

    private fun createApp(pkg: String, label: String, score: Float = 0.5f) = AppEntry(
        packageName = pkg,
        label = label,
        score = score,
        launchCount = 5,
        lastLaunchedMillis = System.currentTimeMillis(),
        todayCount = 1,
        dailyAvg = 1.0f
    )

    @Test
    fun `isBrowser identifies known Android web browsers`() {
        assertTrue(AppFilter.isBrowser("com.android.chrome"))
        assertTrue(AppFilter.isBrowser("org.mozilla.firefox"))
        assertTrue(AppFilter.isBrowser("com.brave.browser"))
        assertTrue(AppFilter.isBrowser("com.duckduckgo.mobile.android"))
        assertTrue(AppFilter.isBrowser("com.sec.android.app.sbrowser"))
        assertTrue(AppFilter.isBrowser("com.opera.browser"))
        assertTrue(AppFilter.isBrowser("com.microsoft.emmx"))

        assertFalse(AppFilter.isBrowser("com.google.android.calculator"))
        assertFalse(AppFilter.isBrowser("com.whatsapp"))
        assertFalse(AppFilter.isBrowser("com.yrolland.loom"))
    }

    @Test
    fun `filter on home screen (empty query) strictly excludes hidden apps`() {
        val chrome = createApp("com.android.chrome", "Chrome")
        val phone = createApp("com.google.android.dialer", "Phone")
        val apps = listOf(chrome, phone)
        val hidden = setOf("com.android.chrome")

        val result = AppFilter.filter(
            apps = apps,
            hiddenPackages = hidden,
            query = "",
            includeHiddenInSearch = false
        )

        assertEquals(1, result.size)
        assertEquals("com.google.android.dialer", result[0].packageName)
    }

    @Test
    fun `filter in search excludes hidden apps when includeHiddenInSearch is false`() {
        val chrome = createApp("com.android.chrome", "Chrome")
        val phone = createApp("com.google.android.dialer", "Phone")
        val apps = listOf(chrome, phone)
        val hidden = setOf("com.android.chrome")

        val result = AppFilter.filter(
            apps = apps,
            hiddenPackages = hidden,
            query = "Chr",
            includeHiddenInSearch = false
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `filter in search reveals hidden apps when includeHiddenInSearch is true`() {
        val chrome = createApp("com.android.chrome", "Chrome")
        val phone = createApp("com.google.android.dialer", "Phone")
        val apps = listOf(chrome, phone)
        val hidden = setOf("com.android.chrome")

        val result = AppFilter.filter(
            apps = apps,
            hiddenPackages = hidden,
            query = "Chr",
            includeHiddenInSearch = true
        )

        assertEquals(1, result.size)
        assertEquals("com.android.chrome", result[0].packageName)
    }

    @Test
    fun `shouldShowWebFallback obeys webFallbackDisabled setting`() {
        // Normal behavior: empty search results triggers fallback
        assertTrue(AppFilter.shouldShowWebFallback(query = "something", hasResults = false, webFallbackDisabled = false))
        assertFalse(AppFilter.shouldShowWebFallback(query = "something", hasResults = true, webFallbackDisabled = false))
        assertFalse(AppFilter.shouldShowWebFallback(query = "", hasResults = false, webFallbackDisabled = false))

        // Digital detox mode: web search fallback is completely disabled
        assertFalse(AppFilter.shouldShowWebFallback(query = "something", hasResults = false, webFallbackDisabled = true))
        assertFalse(AppFilter.shouldShowWebFallback(query = "something", hasResults = true, webFallbackDisabled = true))
    }
}
