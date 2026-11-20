package com.termux.shell.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [KeymapTable] 的语法解析与匹配测试。
 *
 * 说明：测试仅使用 [KeyEvent] 与 `KeyEvent.META_*` 的编译期常量，因此无需 Android 运行时。
 */
class KeymapTableTest {

    @Test
    fun parsesSingleModifierBinding() {
        val table = KeymapTable.parse("ctrl+t = new-tab")
        assertEquals(1, table.size)
        assertEquals("new-tab", table.findAction(KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON))
    }

    @Test
    fun parsesCombinedModifiersInAnyOrder() {
        val table = KeymapTable.parse("shift+ctrl+w = close-tab")
        assertEquals(
            "close-tab",
            table.findAction(KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON),
        )
    }

    @Test
    fun ignoresCommentsAndBlankLines() {
        val table = KeymapTable.parse("# 注释\n\n   \nalt+left = focus-left")
        assertEquals(1, table.size)
        assertEquals("focus-left", table.findAction(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_ALT_ON))
    }

    @Test
    fun skipsMalformedLines() {
        val table = KeymapTable.parse("broken\nunknown+key = action\nctrl+ = empty\nalt+right = ok")
        assertEquals(1, table.size)
        assertEquals("ok", table.findAction(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_ALT_ON))
    }

    @Test
    fun firstBindingWinsOnDuplicateAccelerator() {
        val table = KeymapTable.parse("ctrl+t = first\nctrl+t = second")
        assertEquals(1, table.size)
        assertEquals("first", table.findAction(KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON))
    }

    @Test
    fun leftAndRightModifierVariantsMatchGenericBinding() {
        val table = KeymapTable.parse("ctrl+shift+p = command-palette")
        assertEquals(
            "command-palette",
            table.findAction(
                KeyEvent.KEYCODE_P,
                KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_LEFT_ON,
            ),
        )
    }

    @Test
    fun unboundCombinationReturnsNull() {
        val table = KeymapTable.parse("ctrl+t = new-tab")
        assertNull(table.findAction(KeyEvent.KEYCODE_T, 0))
        assertNull(table.findAction(KeyEvent.KEYCODE_Y, KeyEvent.META_CTRL_ON))
        assertNull(KeymapTable.EMPTY.findAction(KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON))
    }

    @Test
    fun digitsAndNamedKeysAreSupported() {
        val table = KeymapTable.parse("alt+1 = focus-pane-1\nctrl+tab = next-tab\nctrl+pageup = prev-tab")
        assertEquals("focus-pane-1", table.findAction(KeyEvent.KEYCODE_1, KeyEvent.META_ALT_ON))
        assertEquals("next-tab", table.findAction(KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON))
        assertEquals("prev-tab", table.findAction(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.META_CTRL_ON))
        assertTrue(table.size == 3)
    }
}
