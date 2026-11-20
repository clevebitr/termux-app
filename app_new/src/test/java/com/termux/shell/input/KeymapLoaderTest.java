package com.termux.shell.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

import java.util.List;

/**
 * {@link KeymapLoader} 内置默认键位的测试。
 *
 * <p>说明：测试不访问磁盘，仅验证默认键位文本本身可解析、动作名称已登记、展示文本格式正确。
 */
public final class KeymapLoaderTest {

    @Test
    public void defaultLinesParseIntoBindings() {
        KeymapTable table = KeymapTable.parse(KeymapLoader.getDefaultLines());
        assertFalse(table.isEmpty());
        assertEquals(ShellAction.NEW_TAB, table.findAction(
            KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON));
        assertEquals(ShellAction.COMMAND_PALETTE, table.findAction(
            KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON));
        assertEquals(ShellAction.FOCUS_NEXT_PANE, table.findAction(
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_ALT_ON));
    }

    @Test
    public void everyDefaultActionIsRegistered() {
        List<String> lines = KeymapLoader.getDefaultLines();
        int actionCount = 0;
        for (String line : lines) {
            int separator = line.indexOf('=');
            if (line.trim().isEmpty() || line.trim().startsWith("#") || separator <= 0) {
                continue;
            }
            String action = line.substring(separator + 1).trim();
            assertTrue("未登记的动作：" + action, ShellAction.isKnown(action));
            actionCount++;
        }
        assertTrue(actionCount >= 10);
    }

    @Test
    public void shortcutDisplayUsesCanonicalSpelling() {
        KeymapTable table = KeymapTable.parse(KeymapLoader.getDefaultLines());
        assertEquals("Ctrl+Shift+T", table.findShortcut(ShellAction.NEW_TAB));
        assertEquals("Ctrl+Tab", table.findShortcut(ShellAction.NEXT_TAB));
        assertEquals("Alt+Shift+D", table.findShortcut(ShellAction.SPLIT_RIGHT));
        assertEquals("Alt+Left", table.findShortcut(ShellAction.FOCUS_PREVIOUS_PANE));
        assertNull(table.findShortcut(ShellAction.RELOAD_CONFIG));
    }

    @Test
    public void actionRegistryRejectsUnknownNames() {
        assertNotNull(ShellAction.all());
        assertTrue(ShellAction.isKnown(ShellAction.NEW_WINDOW));
        assertFalse(ShellAction.isKnown("no-such-action"));
        assertFalse(ShellAction.isKnown(null));
    }
}
