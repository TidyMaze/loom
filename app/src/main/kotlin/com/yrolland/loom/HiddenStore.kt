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

    fun clear() = prefs.edit().remove(KEY).apply()

    companion object {
        private const val PREFS = "loom_hidden"
        private const val KEY = "hidden_packages"
    }
}
