package com.yrolland.loom

import kotlin.math.abs

enum class DpadDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

object DpadNavigation {
    private const val KEYCODE_DPAD_UP = 19
    private const val KEYCODE_DPAD_DOWN = 20
    private const val KEYCODE_DPAD_LEFT = 21
    private const val KEYCODE_DPAD_RIGHT = 22
    private const val KEYCODE_DPAD_CENTER = 23
    private const val KEYCODE_ENTER = 66
    private const val KEYCODE_NUMPAD_ENTER = 160

    fun isDpadKey(keyCode: Int): Boolean {
        return keyCode == KEYCODE_DPAD_UP ||
            keyCode == KEYCODE_DPAD_DOWN ||
            keyCode == KEYCODE_DPAD_LEFT ||
            keyCode == KEYCODE_DPAD_RIGHT ||
            keyCode == KEYCODE_DPAD_CENTER ||
            keyCode == KEYCODE_ENTER ||
            keyCode == KEYCODE_NUMPAD_ENTER
    }

    fun isPrintableKey(unicodeChar: Int): Boolean {
        return unicodeChar in 32..126 || unicodeChar > 127
    }

    /** Uniform grid: every item spans one column. */
    fun calculateNextPosition(current: Int, direction: DpadDirection, cols: Int, total: Int): Int {
        if (total <= 0) return -1
        return calculateNextPosition(current, direction, cols, IntArray(total) { 1 })
    }

    /**
     * Grid with non-uniform spans (hero = full row, row = half or full, tile = one column; see TileLayout.spanFor).
     * Cells are placed like GridLayoutManager does: greedy, left to right, wrap when an item does not fit.
     * UP/DOWN pick the item in the adjacent row whose centre is closest to the current centre (ties: first).
     * Returns -1 for UP when there is no row above (focus goes to search), and stays put for DOWN on the last row.
     */
    fun calculateNextPosition(current: Int, direction: DpadDirection, cols: Int, spanSizes: IntArray): Int {
        val total = spanSizes.size
        if (total <= 0) return -1
        if (current !in 0 until total) return 0

        val cells = placeCells(spanSizes, cols)
        val cur = cells[current]
        return when (direction) {
            DpadDirection.UP -> nearestInRow(cells, cur.row - 1, cur)
            DpadDirection.DOWN -> nearestInRow(cells, cur.row + 1, cur).takeIf { it >= 0 } ?: current
            DpadDirection.LEFT -> if (current == 0 || cells[current - 1].row != cur.row) current else current - 1
            DpadDirection.RIGHT -> if (current + 1 >= total || cells[current + 1].row != cur.row) current else current + 1
        }
    }

    private class Cell(val row: Int, val start: Int, val span: Int) {
        val center: Double get() = start + span / 2.0
    }

    private fun placeCells(spanSizes: IntArray, cols: Int): List<Cell> {
        val columns = cols.coerceAtLeast(1)
        val cells = ArrayList<Cell>(spanSizes.size)
        var row = 0
        var col = 0
        for (raw in spanSizes) {
            val span = raw.coerceIn(1, columns)
            if (col + span > columns) {
                row++
                col = 0
            }
            cells.add(Cell(row, col, span))
            col += span
        }
        return cells
    }

    private fun nearestInRow(cells: List<Cell>, row: Int, from: Cell): Int {
        var best = -1
        var bestDistance = Double.MAX_VALUE
        cells.forEachIndexed { index, cell ->
            if (cell.row == row) {
                val distance = abs(cell.center - from.center)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = index
                }
            }
        }
        return best
    }
}
