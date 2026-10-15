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
}
