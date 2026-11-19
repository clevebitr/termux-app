package com.termux.shell.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/**
 * {@link KeymapTable} 的语法解析与匹配测试。
 *
 * <p>说明：测试仅使用 {@link KeyEvent} 与 {@code KeyEvent.META_*} 的编译期常量，因此无需 Android 运行时。
 */
public final class KeymapTableTest {

    @Test
    public void parsesSingleModifierBinding() {
        KeymapTable table = KeymapTable.parse("ctrl+t = new-tab");
        assertEquals(1, table.size());
        assertEquals("new-tab", table.findAction(KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON));
    }

    @Test
    public void parsesCombinedModifiersInAnyOrder() {
        KeymapTable table = KeymapTable.parse("shift+ctrl+w = close-tab");
        assertEquals("close-tab",
            table.findAction(KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON));
    }

    @Test
    public void ignoresCommentsAndBlankLines() {
        KeymapTable table = KeymapTable.parse("# 注释\n\n   \nalt+left = focus-left");
        assertEquals(1, table.size());
        assertEquals("focus-left", table.findAction(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_ALT_ON));
    }

    @Test
    public void skipsMalformedLines() {
        KeymapTable table = KeymapTable.parse("broken\nunknown+key = action\nctrl+ = empty\nalt+right = ok");
        assertEquals(1, table.size());
        assertEquals("ok", table.findAction(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_ALT_ON));
    }

    @Test
    public void firstBindingWinsOnDuplicateAccelerator() {
        KeymapTable table = KeymapTable.parse("ctrl+t = first\nctrl+t = second");
        assertEquals(1, table.size());
        assertEquals("first", table.findAction(KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON));
    }

    @Test
    public void leftAndRightModifierVariantsMatchGenericBinding() {
        KeymapTable table = KeymapTable.parse("ctrl+shift+p = command-palette");
        assertEquals("command-palette",
            table.findAction(KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_SHIFT_LEFT_ON));
    }

    @Test
    public void unboundCombinationReturnsNull() {
        KeymapTable table = KeymapTable.parse("ctrl+t = new-tab");
        assertNull(table.findAction(KeyEvent.KEYCODE_T, 0));
        assertNull(table.findAction(KeyEvent.KEYCODE_Y, KeyEvent.META_CTRL_ON));
        assertNull(KeymapTable.EMPTY.findAction(KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON));
    }

    @Test
    public void digitsAndNamedKeysAreSupported() {
        KeymapTable table = KeymapTable.parse("alt+1 = focus-pane-1\nctrl+tab = next-tab\nctrl+pageup = prev-tab");
        assertEquals("focus-pane-1", table.findAction(KeyEvent.KEYCODE_1, KeyEvent.META_ALT_ON));
        assertEquals("next-tab", table.findAction(KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON));
        assertEquals("prev-tab", table.findAction(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.META_CTRL_ON));
        assertTrue(table.size() == 3);
    }
}
