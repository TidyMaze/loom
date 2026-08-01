package com.yrolland.loom

import android.content.Context

class GridStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getColumnCount(): Int = coerceColumns(prefs.getInt(KEY_COLUMNS, DEFAULT_COLUMNS))

    fun setColumnCount(count: Int) {
        prefs.edit().putInt(KEY_COLUMNS, coerceColumns(count)).apply()
    }

    companion object {
        private const val PREFS = "loom_grid"
        private const val KEY_COLUMNS = "grid_columns"
        const val DEFAULT_COLUMNS = 4

        fun coerceColumns(count: Int): Int = count.coerceIn(3, 6)
    }
}
