package com.yrolland.loom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpadNavigationTest {

    @Test
    fun `isDpadKey identifies all directional pad and enter keys`() {
        // Standard Android KeyCodes
        val dpadUp = 19
        val dpadDown = 20
        val dpadLeft = 21
        val dpadRight = 22
        val dpadCenter = 23
        val enter = 66
        val numpadEnter = 160
        val keyA = 29

        assertTrue(DpadNavigation.isDpadKey(dpadUp))
        assertTrue(DpadNavigation.isDpadKey(dpadDown))
        assertTrue(DpadNavigation.isDpadKey(dpadLeft))
        assertTrue(DpadNavigation.isDpadKey(dpadRight))
        assertTrue(DpadNavigation.isDpadKey(dpadCenter))
        assertTrue(DpadNavigation.isDpadKey(enter))
        assertTrue(DpadNavigation.isDpadKey(numpadEnter))
        assertFalse(DpadNavigation.isDpadKey(keyA))
    }

    @Test
    fun `shouldForwardToSearch returns true for printable characters when search is closed`() {
        val keyA = 'a'.code
        val key1 = '1'.code

        assertTrue(DpadNavigation.isPrintableKey(keyA))
        assertTrue(DpadNavigation.isPrintableKey(key1))
        assertFalse(DpadNavigation.isPrintableKey(0))
        assertFalse(DpadNavigation.isPrintableKey(27)) // ESC
    }

    @Test
    fun `calculateNextPosition moves correctly across columns and rows in grid`() {
        val total = 10
        val cols = 3

        // Grid positions:
        // [0, 1, 2]
        // [3, 4, 5]
        // [6, 7, 8]
        // [9]

        // Move Right
        assertEquals(1, DpadNavigation.calculateNextPosition(0, DpadDirection.RIGHT, cols, total))
        assertEquals(2, DpadNavigation.calculateNextPosition(1, DpadDirection.RIGHT, cols, total))
        assertEquals(2, DpadNavigation.calculateNextPosition(2, DpadDirection.RIGHT, cols, total)) // edge boundary

        // Move Left
        assertEquals(0, DpadNavigation.calculateNextPosition(1, DpadDirection.LEFT, cols, total))
        assertEquals(0, DpadNavigation.calculateNextPosition(0, DpadDirection.LEFT, cols, total)) // edge boundary
        assertEquals(3, DpadNavigation.calculateNextPosition(3, DpadDirection.LEFT, cols, total)) // row start

        // Move Down
        assertEquals(3, DpadNavigation.calculateNextPosition(0, DpadDirection.DOWN, cols, total))
        assertEquals(6, DpadNavigation.calculateNextPosition(3, DpadDirection.DOWN, cols, total))
        assertEquals(9, DpadNavigation.calculateNextPosition(6, DpadDirection.DOWN, cols, total))
        assertEquals(9, DpadNavigation.calculateNextPosition(9, DpadDirection.DOWN, cols, total)) // last item boundary

        // Move Up
        assertEquals(-1, DpadNavigation.calculateNextPosition(0, DpadDirection.UP, cols, total)) // reached top
        assertEquals(-1, DpadNavigation.calculateNextPosition(1, DpadDirection.UP, cols, total)) // reached top
        assertEquals(0, DpadNavigation.calculateNextPosition(3, DpadDirection.UP, cols, total))
    }

    // ---- Non-uniform spans (hero = 4 cols, row = 2 cols, tile = 1 col) ----

    // Layout used below, cols = 4:
    //   row 0: [0 hero span4]
    //   row 1: [1 row span2][2 row span2]
    //   row 2: [3][4][5][6]           (tiles)
    private val mixedSpans = intArrayOf(4, 2, 2, 1, 1, 1, 1)

    @Test
    fun `spans - UP from a row item lands on the hero above, not on search`() {
        assertEquals(0, DpadNavigation.calculateNextPosition(1, DpadDirection.UP, 4, mixedSpans))
        assertEquals(0, DpadNavigation.calculateNextPosition(2, DpadDirection.UP, 4, mixedSpans))
    }

    @Test
    fun `spans - UP from the top row returns -1 so focus goes to search`() {
        assertEquals(-1, DpadNavigation.calculateNextPosition(0, DpadDirection.UP, 4, mixedSpans))
    }

    @Test
    fun `spans - UP from a tile picks the item whose centre is closest`() {
        // Tile 6 (centre 3.5) is nearer to row-item 2 (centre 3.0) than to row-item 1 (centre 1.0).
        assertEquals(2, DpadNavigation.calculateNextPosition(6, DpadDirection.UP, 4, mixedSpans))
        // Tile 3 (centre 0.5) is nearer to row-item 1 (centre 1.0).
        assertEquals(1, DpadNavigation.calculateNextPosition(3, DpadDirection.UP, 4, mixedSpans))
    }

    @Test
    fun `spans - DOWN from the hero goes to the first row item on a tie`() {
        // Hero centre 2.0 is equidistant from items 1 (centre 1.0) and 2 (centre 3.0): first wins.
        assertEquals(1, DpadNavigation.calculateNextPosition(0, DpadDirection.DOWN, 4, mixedSpans))
    }

    @Test
    fun `spans - DOWN from a row item goes to the tile under it`() {
        assertEquals(5, DpadNavigation.calculateNextPosition(2, DpadDirection.DOWN, 4, mixedSpans))
    }

    @Test
    fun `spans - DOWN on the last row stays put`() {
        assertEquals(6, DpadNavigation.calculateNextPosition(6, DpadDirection.DOWN, 4, mixedSpans))
    }

    @Test
    fun `spans - LEFT and RIGHT stay inside the row`() {
        // Row 1 holds items 1 and 2; RIGHT from 2 must not jump to row 2.
        assertEquals(2, DpadNavigation.calculateNextPosition(1, DpadDirection.RIGHT, 4, mixedSpans))
        assertEquals(2, DpadNavigation.calculateNextPosition(2, DpadDirection.RIGHT, 4, mixedSpans))
        assertEquals(1, DpadNavigation.calculateNextPosition(2, DpadDirection.LEFT, 4, mixedSpans))
        assertEquals(1, DpadNavigation.calculateNextPosition(1, DpadDirection.LEFT, 4, mixedSpans))
        // The hero is alone in its row.
        assertEquals(0, DpadNavigation.calculateNextPosition(0, DpadDirection.RIGHT, 4, mixedSpans))
        assertEquals(0, DpadNavigation.calculateNextPosition(0, DpadDirection.LEFT, 4, mixedSpans))
        // Row start stays put.
        assertEquals(3, DpadNavigation.calculateNextPosition(3, DpadDirection.LEFT, 4, mixedSpans))
    }

    @Test
    fun `spans - a hero that does not fit wraps and leaves a gap that navigation skips`() {
        // cols 4: [row span2][hero span4] -> hero wraps to row 1, row 0 has only item 0.
        val spans = intArrayOf(2, 4, 1)
        assertEquals(1, DpadNavigation.calculateNextPosition(0, DpadDirection.DOWN, 4, spans))
        assertEquals(0, DpadNavigation.calculateNextPosition(1, DpadDirection.UP, 4, spans))
        assertEquals(2, DpadNavigation.calculateNextPosition(1, DpadDirection.DOWN, 4, spans))
        // Item 2 sits in row 2; the row above holds only the hero (item 1).
        assertEquals(1, DpadNavigation.calculateNextPosition(2, DpadDirection.UP, 4, spans))
    }

    @Test
    fun `spans - DOWN into a shorter row goes to the nearest item instead of staying put`() {
        // cols 4, 6 tiles: row 0 = [0..3], row 1 = [4, 5]. From 3 (centre 3.5) the nearest is 5 (centre 1.5).
        val spans = IntArray(6) { 1 }
        assertEquals(5, DpadNavigation.calculateNextPosition(3, DpadDirection.DOWN, 4, spans))
    }

    @Test
    fun `spans - a span wider than the grid is clamped to the column count`() {
        val spans = intArrayOf(6, 1)
        assertEquals(0, DpadNavigation.calculateNextPosition(1, DpadDirection.UP, 4, spans))
        assertEquals(-1, DpadNavigation.calculateNextPosition(0, DpadDirection.UP, 4, spans))
    }

    @Test
    fun `spans - empty list and out-of-range current keep the existing contract`() {
        assertEquals(-1, DpadNavigation.calculateNextPosition(0, DpadDirection.DOWN, 4, IntArray(0)))
        assertEquals(0, DpadNavigation.calculateNextPosition(42, DpadDirection.DOWN, 4, mixedSpans))
    }

    @Test
    fun `spans - all spans equal to one give the same answers as the uniform grid`() {
        val ones = IntArray(10) { 1 }
        for (current in 0 until 10) {
            for (direction in DpadDirection.values()) {
                assertEquals(
                    "current=$current direction=$direction",
                    DpadNavigation.calculateNextPosition(current, direction, 3, 10),
                    DpadNavigation.calculateNextPosition(current, direction, 3, ones)
                )
            }
        }
    }

    // ---- 3..6 columns, spans as TileLayout produces them (heroes, then rows, then tiles) ----

    private fun tileSpans(cols: Int, heroes: Int, rows: Int, tiles: Int): IntArray {
        val types = List(heroes) { TileLayout.TYPE_HERO } +
            List(rows) { TileLayout.TYPE_ROW } +
            List(tiles) { TileLayout.TYPE_TILE }
        return types.map { TileLayout.spanFor(it, cols) }.toIntArray()
    }

    /** Line of each item, found by walking RIGHT: a new line starts where RIGHT from the previous item does not reach it. */
    private fun lineIndexOf(spans: IntArray, cols: Int): IntArray {
        val line = IntArray(spans.size)
        for (i in spans.indices) {
            if (i == 0) continue
            val sameLine = DpadNavigation.calculateNextPosition(i - 1, DpadDirection.RIGHT, cols, spans) == i
            line[i] = if (sameLine) line[i - 1] else line[i - 1] + 1
        }
        return line
    }

    @Test
    fun `tile layout - every line but the last is exactly full for 3 to 6 columns`() {
        for (cols in 3..6) for (heroes in 0..2) for (rows in 0..4) for (tiles in 0..6) {
            if (heroes + rows + tiles == 0) continue
            val spans = tileSpans(cols, heroes, rows, tiles)
            val line = lineIndexOf(spans, cols)
            val lastLine = line.last()
            for (l in 0 until lastLine) {
                val used = spans.indices.filter { line[it] == l }.sumOf { spans[it] }
                assertEquals("cols=$cols heroes=$heroes rows=$rows tiles=$tiles line=$l", cols, used)
            }
        }
    }

    @Test
    fun `tile layout - DOWN lands in the line directly below and the last line stays put`() {
        for (cols in 3..6) for (heroes in 0..2) for (rows in 0..4) for (tiles in 0..6) {
            if (heroes + rows + tiles == 0) continue
            val spans = tileSpans(cols, heroes, rows, tiles)
            val line = lineIndexOf(spans, cols)
            val lastLine = line.last()
            for (i in spans.indices) {
                val next = DpadNavigation.calculateNextPosition(i, DpadDirection.DOWN, cols, spans)
                val where = "cols=$cols heroes=$heroes rows=$rows tiles=$tiles item=$i"
                if (line[i] == lastLine) {
                    assertEquals(where, i, next)
                } else {
                    assertEquals(where, line[i] + 1, line[next])
                }
            }
        }
    }

    @Test
    fun `spans - three columns, hero then two rows then five tiles`() {
        // Lines: [0] [1] [2] [3 4 5] [6 7]
        val spans = tileSpans(cols = 3, heroes = 1, rows = 2, tiles = 5)
        assertEquals(1, DpadNavigation.calculateNextPosition(0, DpadDirection.DOWN, 3, spans))
        assertEquals(2, DpadNavigation.calculateNextPosition(1, DpadDirection.DOWN, 3, spans))
        // Row 2 (centre 1.5) goes to the middle tile of the next line, not the first.
        assertEquals(4, DpadNavigation.calculateNextPosition(2, DpadDirection.DOWN, 3, spans))
        assertEquals(6, DpadNavigation.calculateNextPosition(3, DpadDirection.DOWN, 3, spans))
        assertEquals(7, DpadNavigation.calculateNextPosition(5, DpadDirection.DOWN, 3, spans))
        assertEquals(7, DpadNavigation.calculateNextPosition(7, DpadDirection.DOWN, 3, spans)) // last line
    }

    @Test
    fun `spans - five columns, a full-width row above a short last line`() {
        // Lines: [0 (5 wide)] [1 2 3 4 5] [6]: the lone tile on the last line is reached from every tile above.
        val spans = tileSpans(cols = 5, heroes = 1, rows = 0, tiles = 6)
        assertEquals(3, DpadNavigation.calculateNextPosition(0, DpadDirection.DOWN, 5, spans))
        assertEquals(6, DpadNavigation.calculateNextPosition(1, DpadDirection.DOWN, 5, spans))
        assertEquals(6, DpadNavigation.calculateNextPosition(5, DpadDirection.DOWN, 5, spans))
        assertEquals(6, DpadNavigation.calculateNextPosition(6, DpadDirection.DOWN, 5, spans))
    }

    @Test
    fun `tile layout - LEFT and RIGHT stay in the same line and stay put at its edges`() {
        // The grid handles LEFT/RIGHT itself and consumes the key at a line edge (focus must not reach the gear).
        for (cols in 3..6) for (heroes in 0..2) for (rows in 0..4) for (tiles in 0..6) {
            if (heroes + rows + tiles == 0) continue
            val spans = tileSpans(cols, heroes, rows, tiles)
            val line = lineIndexOf(spans, cols)
            for (i in spans.indices) {
                val where = "cols=$cols heroes=$heroes rows=$rows tiles=$tiles item=$i"
                val right = DpadNavigation.calculateNextPosition(i, DpadDirection.RIGHT, cols, spans)
                val left = DpadNavigation.calculateNextPosition(i, DpadDirection.LEFT, cols, spans)
                assertEquals(where, line[i], line[right])
                assertEquals(where, line[i], line[left])
                if (right != i) assertEquals(where, i + 1, right)
                if (left != i) assertEquals(where, i - 1, left)
            }
        }
    }

    @Test
    fun `tile layout - UP returns -1 exactly for the first line, which is where the gear is reached`() {
        // MainActivity sends UP from the first line to the gear; UP from any other line stays in the grid.
        for (cols in 3..6) for (heroes in 0..2) for (rows in 0..4) for (tiles in 0..6) {
            if (heroes + rows + tiles == 0) continue
            val spans = tileSpans(cols, heroes, rows, tiles)
            val line = lineIndexOf(spans, cols)
            for (i in spans.indices) {
                val where = "cols=$cols heroes=$heroes rows=$rows tiles=$tiles item=$i"
                val up = DpadNavigation.calculateNextPosition(i, DpadDirection.UP, cols, spans)
                if (line[i] == 0) assertEquals(where, -1, up) else assertTrue(where, up >= 0)
            }
        }
    }

    @Test
    fun `tile layout - a full-width hero or row has no neighbour, so LEFT and RIGHT stay put`() {
        for (cols in 3..6) {
            val spans = tileSpans(cols, heroes = 1, rows = 1, tiles = 0)
            // Hero (item 0) spans the whole grid; with 3 or 5 columns the row (item 1) does too.
            assertEquals(0, DpadNavigation.calculateNextPosition(0, DpadDirection.RIGHT, cols, spans))
            assertEquals(0, DpadNavigation.calculateNextPosition(0, DpadDirection.LEFT, cols, spans))
        }
        for (cols in listOf(3, 5)) {
            val spans = tileSpans(cols, heroes = 0, rows = 2, tiles = 0)
            assertEquals(0, DpadNavigation.calculateNextPosition(0, DpadDirection.RIGHT, cols, spans))
            assertEquals(1, DpadNavigation.calculateNextPosition(1, DpadDirection.LEFT, cols, spans))
            assertEquals(1, DpadNavigation.calculateNextPosition(1, DpadDirection.RIGHT, cols, spans))
        }
    }

    @Test
    fun `spans - six columns, rows of three next to tiles`() {
        // Lines: [0 (6 wide)] [1 (c0-2) 2 (c3-5)] [3 (c0-2) 4 5 6] [7]
        val spans = tileSpans(cols = 6, heroes = 1, rows = 3, tiles = 4)
        assertEquals(1, DpadNavigation.calculateNextPosition(0, DpadDirection.DOWN, 6, spans)) // tie: first
        assertEquals(3, DpadNavigation.calculateNextPosition(1, DpadDirection.DOWN, 6, spans))
        assertEquals(5, DpadNavigation.calculateNextPosition(2, DpadDirection.DOWN, 6, spans))
        assertEquals(7, DpadNavigation.calculateNextPosition(3, DpadDirection.DOWN, 6, spans))
        assertEquals(7, DpadNavigation.calculateNextPosition(4, DpadDirection.DOWN, 6, spans))
        assertEquals(7, DpadNavigation.calculateNextPosition(7, DpadDirection.DOWN, 6, spans)) // last line
    }
}
