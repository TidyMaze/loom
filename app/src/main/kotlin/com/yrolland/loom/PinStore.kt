package com.yrolland.loom

import android.content.Context

class PinStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    fun isPinned(pkg: String): Boolean = all().contains(pkg)

    fun setPinned(pkg: String, pinned: Boolean) {
        val next = all().toMutableSet().apply {
            if (pinned) add(pkg) else remove(pkg)
        }
        prefs.edit().putStringSet(KEY, next).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    companion object {
        private const val PREFS = "loom_pins"
        private const val KEY = "pinned_packages"
    }
}
