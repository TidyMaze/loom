package com.yrolland.loom

import android.content.Context

class HiddenStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    fun isHidden(pkg: String): Boolean = all().contains(pkg)

    fun setHidden(pkg: String, hidden: Boolean) {
        val next = all().toMutableSet().apply {
            if (hidden) add(pkg) else remove(pkg)
        }
        prefs.edit().putStringSet(KEY, next).apply()
    }

    fun isWebSearchFallbackDisabled(): Boolean = prefs.getBoolean(KEY_DISABLE_WEB_SEARCH, false)

    fun setWebSearchFallbackDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISABLE_WEB_SEARCH, disabled).apply()
    }

    fun isSearchIncludeHidden(): Boolean = prefs.getBoolean(KEY_INCLUDE_HIDDEN_SEARCH, false)

    fun setSearchIncludeHidden(include: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_HIDDEN_SEARCH, include).apply()
    }

    fun hideAll(packages: Collection<String>) {
        val next = all().toMutableSet().apply { addAll(packages) }
        prefs.edit().putStringSet(KEY, next).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    companion object {
        private const val PREFS = "loom_hidden"
        private const val KEY = "hidden_packages"
        private const val KEY_DISABLE_WEB_SEARCH = "disable_web_search_fallback"
        private const val KEY_INCLUDE_HIDDEN_SEARCH = "include_hidden_search"
    }
}
