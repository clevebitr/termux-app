package com.termux.shell.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [WindowWidthClass] 的断点判定测试。 */
class WindowWidthClassTest {

    @Test
    fun widthBelowFirstBreakpointIsCompact() {
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(0))
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(599))
    }

    @Test
    fun widthAtFirstBreakpointIsMedium() {
        assertEquals(WindowWidthClass.MEDIUM, WindowWidthClass.fromWidthDp(600))
        assertEquals(WindowWidthClass.MEDIUM, WindowWidthClass.fromWidthDp(839))
    }

    @Test
    fun widthAtSecondBreakpointIsExpanded() {
        assertEquals(WindowWidthClass.EXPANDED, WindowWidthClass.fromWidthDp(840))
        assertEquals(WindowWidthClass.EXPANDED, WindowWidthClass.fromWidthDp(1920))
    }

    @Test
    fun negativeWidthIsTreatedAsCompact() {
        assertEquals(WindowWidthClass.COMPACT, WindowWidthClass.fromWidthDp(-1))
    }

    @Test
    fun onlyCompactHidesPersistentRail() {
        assertFalse(WindowWidthClass.COMPACT.hasPersistentRail)
        assertTrue(WindowWidthClass.MEDIUM.hasPersistentRail)
        assertTrue(WindowWidthClass.EXPANDED.hasPersistentRail)
    }
}
