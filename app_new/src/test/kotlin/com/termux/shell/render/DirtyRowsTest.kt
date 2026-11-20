package com.termux.shell.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [DirtyRows] 的区间合并与边界测试。 */
class DirtyRowsTest {

    @Test
    fun emptyIntervalReportsNoRows() {
        assertTrue(DirtyRows.EMPTY.isEmpty)
        assertEquals(0, DirtyRows.EMPTY.rowCount())
    }

    @Test
    fun intervalCoversInclusiveRowRange() {
        val rows = DirtyRows.of(3, 7)
        assertFalse(rows.isEmpty)
        assertEquals(3, rows.minRow)
        assertEquals(7, rows.maxRow)
        assertEquals(5, rows.rowCount())
    }

    @Test(expected = IllegalArgumentException::class)
    fun reversedIntervalIsRejected() {
        DirtyRows.of(7, 3)
    }

    @Test
    fun unionReturnsSmallestCoveringInterval() {
        assertEquals(DirtyRows.of(2, 9), DirtyRows.of(2, 5).union(DirtyRows.of(7, 9)))
        assertEquals(DirtyRows.of(1, 20), DirtyRows.of(10, 20).union(DirtyRows.of(1, 4)))
    }

    @Test
    fun unionWithEmptyKeepsOriginalInstance() {
        val rows = DirtyRows.of(4, 6)
        assertSame(rows, rows.union(DirtyRows.EMPTY))
        assertSame(rows, DirtyRows.EMPTY.union(rows))
    }
}
