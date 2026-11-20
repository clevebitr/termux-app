package com.termux.shell.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [KeymapLoader] 内置默认键位的测试。测试不访问磁盘，只验证默认文本本身可解析且动作已登记。 */
class KeymapLoaderTest {

    @Test
    fun defaultLinesParseIntoBindings() {
        val table = KeymapTable.parse(KeymapLoader.defaultLines)
        assertFalse(table.isEmpty)
        assertEquals(
            ShellAction.NEW_TAB,
            table.findAction(KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON),
        )
        assertEquals(
            ShellAction.COMMAND_PALETTE,
            table.findAction(KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON),
        )
        assertEquals(
            ShellAction.FOCUS_NEXT_PANE,
            table.findAction(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_ALT_ON),
        )
    }

    @Test
    fun everyDefaultActionIsRegistered() {
        var actionCount = 0
        for (line in KeymapLoader.defaultLines) {
            val trimmed = line.trim()
            val separator = trimmed.indexOf('=')
            if (trimmed.isEmpty() || trimmed.startsWith("#") || separator <= 0) {
                continue
            }
            val action = trimmed.substring(separator + 1).trim()
            assertTrue("未登记的动作：$action", ShellAction.isKnown(action))
            actionCount++
        }
        assertTrue(actionCount >= 10)
    }

    @Test
    fun shortcutDisplayUsesCanonicalSpelling() {
        val table = KeymapTable.parse(KeymapLoader.defaultLines)
        assertEquals("Ctrl+Shift+T", table.findShortcut(ShellAction.NEW_TAB))
        assertEquals("Ctrl+Tab", table.findShortcut(ShellAction.NEXT_TAB))
        assertEquals("Alt+Shift+D", table.findShortcut(ShellAction.SPLIT_RIGHT))
        assertEquals("Alt+Left", table.findShortcut(ShellAction.FOCUS_PREVIOUS_PANE))
        assertNull(table.findShortcut(ShellAction.RELOAD_CONFIG))
    }

    @Test
    fun actionRegistryRejectsUnknownNames() {
        assertTrue(ShellAction.all().isNotEmpty())
        assertTrue(ShellAction.isKnown(ShellAction.NEW_WINDOW))
        assertFalse(ShellAction.isKnown("no-such-action"))
        assertFalse(ShellAction.isKnown(null))
    }
}
