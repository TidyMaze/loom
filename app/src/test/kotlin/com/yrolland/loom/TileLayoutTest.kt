package com.yrolland.loom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileLayoutTest {

    /** Usable grid width at 360dp: RecyclerView padding 12dp on each side. */
    private val gridWidth360 = 360 - 24

    @Test
    fun `view type follows rank thresholds`() {
        assertEquals(TileLayout.TYPE_HERO, TileLayout.viewTypeFor(1f))
        assertEquals(TileLayout.TYPE_HERO, TileLayout.viewTypeFor(0.85f))
        assertEquals(TileLayout.TYPE_ROW, TileLayout.viewTypeFor(0.84f))
        assertEquals(TileLayout.TYPE_ROW, TileLayout.viewTypeFor(0.5f))
        assertEquals(TileLayout.TYPE_TILE, TileLayout.viewTypeFor(0.49f))
        assertEquals(TileLayout.TYPE_TILE, TileLayout.viewTypeFor(0f))
    }

    @Test
    fun `spans - hero is a full row, tile is one column, row is half on even grids of 4+ and full otherwise`() {
        assertEquals(4, TileLayout.spanFor(TileLayout.TYPE_HERO, 4))
        assertEquals(2, TileLayout.spanFor(TileLayout.TYPE_ROW, 4))
        assertEquals(1, TileLayout.spanFor(TileLayout.TYPE_TILE, 4))
        assertEquals(6, TileLayout.spanFor(TileLayout.TYPE_HERO, 6))
        assertEquals(3, TileLayout.spanFor(TileLayout.TYPE_ROW, 6))
        assertEquals(3, TileLayout.spanFor(TileLayout.TYPE_ROW, 3)) // odd: full width, not a 1-column sliver
        assertEquals(5, TileLayout.spanFor(TileLayout.TYPE_ROW, 5)) // odd: full width (was 2, left a gap)
        assertEquals(1, TileLayout.spanFor(TileLayout.TYPE_TILE, 5))
    }

    @Test
    fun `heights and icon sizes are derived from the medium scale`() {
        assertEquals(72, TileLayout.heightDp(TileLayout.TYPE_HERO, 76))
        assertEquals(64, TileLayout.heightDp(TileLayout.TYPE_ROW, 76))
        assertEquals(84, TileLayout.heightDp(TileLayout.TYPE_TILE, 76))
        assertEquals(48, TileLayout.iconDp(TileLayout.TYPE_HERO, 44))
        assertEquals(40, TileLayout.iconDp(TileLayout.TYPE_ROW, 44))
        assertEquals(44, TileLayout.iconDp(TileLayout.TYPE_TILE, 44))
    }

    @Test
    fun `no gap for any ordered list of heroes, rows and tiles in 3 to 6 columns`() {
        for (cols in 3..6) {
            for (heroes in 0..3) {
                for (rows in 0..9) {
                    for (tiles in 0..14) {
                        // Ranks chosen so the real viewTypeFor mapping produces the order hero, row, tile.
                        val ranks = List(heroes) { 1f } + List(rows) { 0.6f } + List(tiles) { 0.1f }
                        val spans = IntArray(ranks.size) {
                            TileLayout.spanFor(TileLayout.viewTypeFor(ranks[it]), cols)
                        }
                        assertNoGap(spans, cols, "cols=$cols heroes=$heroes rows=$rows tiles=$tiles")
                    }
                }
            }
        }
    }

    @Test
    fun `every span fits the grid and is at least one column`() {
        for (cols in 3..6) {
            for (type in listOf(TileLayout.TYPE_HERO, TileLayout.TYPE_ROW, TileLayout.TYPE_TILE)) {
                val span = TileLayout.spanFor(type, cols)
                assertTrue("cols=$cols type=$type span=$span", span in 1..cols)
            }
        }
    }

    @Test
    fun `rows keep a readable label width at 360dp in every column count`() {
        for (cols in 3..6) {
            val width = TileLayout.itemWidthDp(TileLayout.TYPE_ROW, cols, gridWidth360)
            val labelWidth = width - TileLayout.insetsDp(TileLayout.TYPE_ROW) - TileLayout.iconDp(TileLayout.TYPE_ROW, 44)
            assertTrue("cols=$cols label=$labelWidth", labelWidth >= 60)
        }
    }

    @Test
    fun `tile label width at 360dp is 40dp or more at 6 columns and more at fewer columns`() {
        // Known limit: at 6 columns on a 360dp screen a tile label has about 40dp (4 to 6 characters per line).
        val expected = mapOf(3 to 96, 4 to 68, 5 to 51, 6 to 40)
        for ((cols, min) in expected) {
            val width = TileLayout.itemWidthDp(TileLayout.TYPE_TILE, cols, gridWidth360)
            assertEquals("cols=$cols", min, width - TileLayout.insetsDp(TileLayout.TYPE_TILE))
        }
    }

    @Test
    fun `item width is the span of the grid minus the outer margin`() {
        assertEquals(328, TileLayout.itemWidthDp(TileLayout.TYPE_ROW, 3, 336)) // full width
        assertEquals(160, TileLayout.itemWidthDp(TileLayout.TYPE_ROW, 6, 336)) // half of 6 columns
        assertEquals(48, TileLayout.itemWidthDp(TileLayout.TYPE_TILE, 6, 336))
        assertEquals(328, TileLayout.itemWidthDp(TileLayout.TYPE_HERO, 4, 336))
    }

    @Test
    fun `icon is fitted to the cell at 5 and 6 columns and keeps the requested size when it fits`() {
        // 6 columns: a 44dp icon (spacious 54dp) cannot fit a 48dp tile; it shrinks to the 40dp room.
        assertEquals(40, TileLayout.fitIconDp(TileLayout.TYPE_TILE, 44, TileLayout.itemWidthDp(TileLayout.TYPE_TILE, 6, 336)))
        assertEquals(40, TileLayout.fitIconDp(TileLayout.TYPE_TILE, 54, TileLayout.itemWidthDp(TileLayout.TYPE_TILE, 6, 336)))
        // 5 columns: the cell is 59dp wide, minus 8dp of tile insets gives 51dp of room: 44 fits, 54 shrinks to 51.
        assertEquals(44, TileLayout.fitIconDp(TileLayout.TYPE_TILE, 44, TileLayout.itemWidthDp(TileLayout.TYPE_TILE, 5, 336)))
        assertEquals(51, TileLayout.fitIconDp(TileLayout.TYPE_TILE, 54, TileLayout.itemWidthDp(TileLayout.TYPE_TILE, 5, 336)))
        // 4 columns and a spacious icon: no reduction needed.
        assertEquals(54, TileLayout.fitIconDp(TileLayout.TYPE_TILE, 54, TileLayout.itemWidthDp(TileLayout.TYPE_TILE, 4, 336)))
    }

    @Test
    fun `icon plus insets never exceeds the item width, down to the minimum icon`() {
        for (widthDp in listOf(280, 320, 360, 411, 600)) {
            val grid = widthDp - 24
            for (cols in 3..6) {
                for (type in listOf(TileLayout.TYPE_HERO, TileLayout.TYPE_ROW, TileLayout.TYPE_TILE)) {
                    for (iconSize in listOf(36, 44, 54)) {
                        val width = TileLayout.itemWidthDp(type, cols, grid)
                        val icon = TileLayout.fitIconDp(type, iconSize, width)
                        assertTrue("w=$widthDp cols=$cols type=$type size=$iconSize icon=$icon",
                            icon >= TileLayout.MIN_ICON_DP)
                        if (width - TileLayout.insetsDp(type) >= TileLayout.MIN_ICON_DP) {
                            assertTrue("w=$widthDp cols=$cols type=$type size=$iconSize icon=$icon width=$width",
                                icon <= width - TileLayout.insetsDp(type))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `unknown width keeps the requested icon size`() {
        assertEquals(44, TileLayout.fitIconDp(TileLayout.TYPE_TILE, 44, 0))
        assertEquals(48, TileLayout.fitIconDp(TileLayout.TYPE_HERO, 44, -1))
    }

    /** Same rule GridLayoutManager applies: a wrap is only allowed when the previous line is full. */
    private fun assertNoGap(spans: IntArray, cols: Int, message: String) {
        var used = 0
        for (span in spans) {
            if (used + span > cols) {
                assertEquals("gap before a wrap: $message", cols, used)
                used = 0
            }
            used += span
        }
    }
}
