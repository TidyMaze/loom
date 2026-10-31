package com.yrolland.loom

/**
 * Rank-driven tile hierarchy for the home grid ("fewer, bigger").
 * Pure functions only (no Android views), so the thresholds, spans and icon fitting are unit-tested.
 *
 * Rank is shown by the rank bar, the icon size and the label weight; icons are always fully opaque.
 */
object TileLayout {
    const val TYPE_TILE = 0
    const val TYPE_ROW = 1
    const val TYPE_HERO = 2

    /** rank is relative to the list max (0..1). */
    const val HERO_MIN_RANK = 0.85f
    const val ROW_MIN_RANK = 0.5f

    const val FOCUS_SCALE = 1.03f

    /** Smallest icon drawn when a narrow cell cannot hold the requested size. */
    const val MIN_ICON_DP = 24

    /** Outer margin of an item: layout_margin space_4 on both sides (XML). */
    const val ITEM_MARGIN_DP = 8

    fun viewTypeFor(rank: Float): Int = when {
        rank >= HERO_MIN_RANK -> TYPE_HERO
        rank >= ROW_MIN_RANK -> TYPE_ROW
        else -> TYPE_TILE
    }

    /**
     * Grid columns taken by an item.
     * - Hero: always the full row.
     * - Row: half a row on an even grid of 4+ columns (two rows per line, no gap). On an odd grid
     *   (3, 5) or a narrow one, a row takes the full width instead: two half rows cannot share an
     *   odd line without leaving a gap, and a one-column row is too narrow to read.
     * - Tile: one column.
     * Any ordered list (heroes, then rows, then tiles) therefore fills every line but the last.
     */
    fun spanFor(viewType: Int, columns: Int): Int {
        val cols = columns.coerceAtLeast(1)
        return when (viewType) {
            TYPE_HERO -> cols
            TYPE_ROW -> if (cols >= 4 && cols % 2 == 0) cols / 2 else cols
            else -> 1
        }
    }

    /** Minimum item height in dp; the real height is wrap_content above this minimum. */
    fun heightDp(viewType: Int, itemHeightDp: Int): Int = when (viewType) {
        TYPE_HERO -> itemHeightDp - 4
        TYPE_ROW -> itemHeightDp - 12
        else -> itemHeightDp + 8
    }

    /** Icon size in dp, derived from the Settings "Icon size" value (before fitting to the cell). */
    fun iconDp(viewType: Int, iconSizeDp: Int): Int = when (viewType) {
        TYPE_HERO -> iconSizeDp + 4
        TYPE_ROW -> iconSizeDp - 4
        else -> iconSizeDp
    }

    /**
     * Horizontal space inside an item that the icon and label cannot use, in dp (XML):
     * tile padding space_4 on both sides; row and hero padding space_16 on both sides plus the label's start margin space_12.
     */
    fun insetsDp(viewType: Int): Int = when (viewType) {
        TYPE_TILE -> 8
        else -> 44
    }

    /** Width of one item in dp: its span of a grid [gridWidthDp] wide, minus the outer margin. */
    fun itemWidthDp(viewType: Int, columns: Int, gridWidthDp: Int): Int {
        val cols = columns.coerceAtLeast(1)
        return gridWidthDp * spanFor(viewType, cols) / cols - ITEM_MARGIN_DP
    }

    /**
     * Icon size actually drawn: the requested size, reduced when the item is too narrow to hold it
     * with its insets (5-6 columns, large icon size), and never below [MIN_ICON_DP].
     * An unknown width ([itemWidthDp] <= 0) keeps the requested size.
     */
    fun fitIconDp(viewType: Int, iconSizeDp: Int, itemWidthDp: Int): Int {
        val wanted = iconDp(viewType, iconSizeDp)
        if (itemWidthDp <= 0) return wanted
        val room = (itemWidthDp - insetsDp(viewType)).coerceAtLeast(MIN_ICON_DP)
        return minOf(wanted, room)
    }
}
