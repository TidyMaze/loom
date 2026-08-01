package com.yrolland.loom

import android.content.Context

class GridStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getColumnCount(): Int = coerceColumns(prefs.getInt(KEY_COLUMNS, DEFAULT_COLUMNS))

    fun setColumnCount(count: Int) {
        prefs.edit().putInt(KEY_COLUMNS, coerceColumns(count)).apply()
    }

    fun getIconScale(): String = prefs.getString(KEY_ICON_SCALE, "medium") ?: "medium"

    fun setIconScale(scale: String) {
        val valid = if (scale in listOf("compact", "medium", "spacious")) scale else "medium"
        prefs.edit().putString(KEY_ICON_SCALE, valid).apply()
    }

    fun getIconSizeDp(): Int = when (getIconScale()) {
        "compact" -> 36
        "spacious" -> 54
        else -> 44
    }

    fun getItemHeightDp(): Int = when (getIconScale()) {
        "compact" -> 64
        "spacious" -> 88
        else -> 76
    }

    companion object {
        private const val PREFS = "loom_grid"
        private const val KEY_COLUMNS = "grid_columns"
        private const val KEY_ICON_SCALE = "icon_scale"
        const val DEFAULT_COLUMNS = 4

        fun coerceColumns(count: Int): Int = count.coerceIn(3, 6)
    }
}
