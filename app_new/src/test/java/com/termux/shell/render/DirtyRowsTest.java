package com.termux.shell.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link DirtyRows} 的区间合并与边界测试。
 */
public final class DirtyRowsTest {

    @Test
    public void emptyIntervalReportsNoRows() {
        assertTrue(DirtyRows.EMPTY.isEmpty());
        assertEquals(0, DirtyRows.EMPTY.rowCount());
    }

    @Test
    public void intervalCoversInclusiveRowRange() {
        DirtyRows rows = DirtyRows.of(3, 7);
        assertFalse(rows.isEmpty());
        assertEquals(3, rows.getMinRow());
        assertEquals(7, rows.getMaxRow());
        assertEquals(5, rows.rowCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void reversedIntervalIsRejected() {
        DirtyRows.of(7, 3);
    }

    @Test
    public void unionReturnsSmallestCoveringInterval() {
        assertEquals(DirtyRows.of(2, 9), DirtyRows.of(2, 5).union(DirtyRows.of(7, 9)));
        assertEquals(DirtyRows.of(1, 20), DirtyRows.of(10, 20).union(DirtyRows.of(1, 4)));
    }

    @Test
    public void unionWithEmptyKeepsOriginalInstance() {
        DirtyRows rows = DirtyRows.of(4, 6);
        assertSame(rows, rows.union(DirtyRows.EMPTY));
        assertSame(rows, DirtyRows.EMPTY.union(rows));
    }
}
