package com.termux.app.commands;

import android.view.KeyEvent;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the parsing of the shortcut labels that the palette shows.
 *
 * <p>The labels are what the user reads and what the palette compares a pressed key against, so a label that
 * does not parse silently makes a shortcut unusable, and a label with the wrong modifiers would take a key that
 * belongs to the terminal.</p>
 */
public class ShellCommandShortcutTest {

    @Test
    public void testLetterShortcutMatchesExactModifiers() {
        Assert.assertTrue(ShellCommandShortcut.matches("Ctrl+Alt+F",
            KeyEvent.KEYCODE_F, 'f', true, true, false));
        // A missing or an extra modifier must not match, otherwise the label claims a key it does not own.
        Assert.assertFalse(ShellCommandShortcut.matches("Ctrl+Alt+F",
            KeyEvent.KEYCODE_F, 'f', true, false, false));
        Assert.assertFalse(ShellCommandShortcut.matches("Ctrl+Alt+F",
            KeyEvent.KEYCODE_F, 'f', true, true, true));
    }

    @Test
    public void testLetterShortcutFollowsTheUnmodifiedCharacter() {
        // A layout where F is typed from a key that is not KEYCODE_F must still run the command, since the label
        // describes the character and not the physical key.
        Assert.assertTrue(ShellCommandShortcut.matches("Ctrl+Alt+F",
            KeyEvent.KEYCODE_UNKNOWN, 'f', true, true, false));
    }

    @Test
    public void testNamedKeyShortcut() {
        Assert.assertTrue(ShellCommandShortcut.matches("Ctrl+Alt+Up",
            KeyEvent.KEYCODE_DPAD_UP, 0, true, true, false));
        Assert.assertFalse(ShellCommandShortcut.matches("Ctrl+Alt+Up",
            KeyEvent.KEYCODE_DPAD_DOWN, 0, true, true, false));
    }

    @Test
    public void testUnparsableShortcutNeverMatches() {
        Assert.assertFalse(ShellCommandShortcut.matches(null, KeyEvent.KEYCODE_F, 'f', true, true, false));
        Assert.assertFalse(ShellCommandShortcut.matches("", KeyEvent.KEYCODE_F, 'f', true, true, false));
        Assert.assertFalse(ShellCommandShortcut.matches("Ctrl+Alt",
            KeyEvent.KEYCODE_F, 'f', true, true, false));
        Assert.assertFalse(ShellCommandShortcut.matches("UnknownKey",
            KeyEvent.KEYCODE_F, 'f', false, false, false));
    }

    @Test
    public void testNormalizePutsTheModifiersInAFixedOrder() {
        Assert.assertEquals("ctrl+alt+f", ShellCommandShortcut.normalize("Alt+Ctrl+F"));
        Assert.assertEquals("ctrl+alt+f", ShellCommandShortcut.normalize(" ctrl + alt + f "));
        Assert.assertEquals("ctrl+shift+p", ShellCommandShortcut.normalize("Shift+Ctrl+P"));
        Assert.assertEquals("", ShellCommandShortcut.normalize(null));
    }
}
