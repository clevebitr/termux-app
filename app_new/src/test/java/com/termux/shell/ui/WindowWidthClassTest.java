package com.termux.shell.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link WindowWidthClass} 的断点判定测试。
 */
public final class WindowWidthClassTest {

    @Test
    public void widthBelowFirstBreakpointIsCompact() {
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(0));
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(599));
    }

    @Test
    public void widthAtFirstBreakpointIsMedium() {
        assertEquals(WindowWidthClass.MEDIUM, WindowWidthClass.fromWidthDp(600));
        assertEquals(WindowWidthClass.MEDIUM, WindowWidthClass.fromWidthDp(839));
    }

    @Test
    public void widthAtSecondBreakpointIsExpanded() {
        assertEquals(WindowWidthClass.EXPANDED, WindowWidthClass.fromWidthDp(840));
        assertEquals(WindowWidthClass.EXPANDED, WindowWidthClass.fromWidthDp(1920));
    }

    @Test
    public void negativeWidthIsTreatedAsCompact() {
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(-1));
    }

    @Test
    public void onlyCompactHidesPersistentRail() {
        assertFalse(WindowWidthClass.COMPACT.hasPersistentRail());
        assertTrue(WindowWidthClass.MEDIUM.hasPersistentRail());
        assertTrue(WindowWidthClass.EXPANDED.hasPersistentRail());
    }
}
