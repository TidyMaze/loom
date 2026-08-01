package com.yrolland.loom

import org.junit.Assert.assertEquals
import org.junit.Test

class GridStoreTest {

    @Test
    fun `coerceColumns keeps valid range between 3 and 6`() {
        assertEquals(3, GridStore.coerceColumns(2))
        assertEquals(3, GridStore.coerceColumns(3))
        assertEquals(4, GridStore.coerceColumns(4))
        assertEquals(5, GridStore.coerceColumns(5))
        assertEquals(6, GridStore.coerceColumns(6))
        assertEquals(6, GridStore.coerceColumns(10))
    }
}
