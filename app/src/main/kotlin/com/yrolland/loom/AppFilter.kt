package com.yrolland.loom

object AppFilter {

    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "com.brave.browser",
        "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.touch",
        "com.microsoft.emmx",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "org.torproject.torbrowser",
        "mark.via.gp",
        "acr.browser.barebones"
    )

    fun isBrowser(packageName: String): Boolean {
        return packageName in BROWSER_PACKAGES ||
            packageName.contains("browser", ignoreCase = true) ||
            packageName.contains("chrome", ignoreCase = true) ||
            packageName.contains("firefox", ignoreCase = true)
    }

    fun filter(
        apps: List<AppEntry>,
        hiddenPackages: Set<String>,
        query: String,
        includeHiddenInSearch: Boolean
    ): List<AppEntry> {
        val q = query.trim()
        if (q.isEmpty()) {
            return apps.filter { it.packageName !in hiddenPackages }
        }

        val matching = apps.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }

        return if (includeHiddenInSearch) {
            matching
        } else {
            matching.filter { it.packageName !in hiddenPackages }
        }
    }

    fun shouldShowWebFallback(
        query: String,
        hasResults: Boolean,
        webFallbackDisabled: Boolean
    ): Boolean {
        if (webFallbackDisabled) return false
        return query.isNotBlank() && !hasResults
    }
}
