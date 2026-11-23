package com.termux.app.commands;

import com.termux.shared.logger.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

/**
 * Tests the parsing of the `command-shortcuts` property, which is the single source of the labels of the palette
 * and of the keys that the terminal takes.
 *
 * <p>A mistake here is user visible in two ways: a command shows a shortcut that does not run it, or a shortcut
 * that the user removed still takes the key from the terminal program.</p>
 */
public class CommandShortcutsTest {

    private int mPreviousLogLevel;

    @Before
    public void disableLogging() {
        mPreviousLogLevel = Logger.getLogLevel();
        Logger.setLogLevel(null, Logger.LOG_LEVEL_OFF);
    }

    @After
    public void restoreLogging() {
        Logger.setLogLevel(null, mPreviousLogLevel);
    }

    private static CommandShortcuts parse(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return CommandShortcuts.parse(properties);
    }

    @Test
    public void testDefaultsAreUsedWhenThePropertyIsNotSet() {
        CommandShortcuts shortcuts = parse();

        Assert.assertEquals("Ctrl+Shift+P", shortcuts.shortcutFor(CommandShortcuts.ID_PALETTE));
        Assert.assertEquals("Ctrl+Alt+F", shortcuts.shortcutFor("search"));
        Assert.assertEquals("Ctrl+Alt+K", shortcuts.shortcutFor(ShellCommandCatalog.ID_TOGGLE_KEYBOARD));
        Assert.assertEquals("Ctrl+Alt+C", shortcuts.shortcutFor(ShellCommandCatalog.ID_NEW_SESSION));
        Assert.assertNull(shortcuts.shortcutFor("split-vertical"));
    }

    @Test
    public void testAnEntryOnlyOverridesItsOwnCommand() {
        CommandShortcuts shortcuts = parse("command-shortcuts", "search=Ctrl+Shift+S");

        Assert.assertEquals("Ctrl+Shift+S", shortcuts.shortcutFor("search"));
        // The commands that are not listed keep their default, so a configuration only lists what it changes.
        Assert.assertEquals("Ctrl+Alt+K", shortcuts.shortcutFor(ShellCommandCatalog.ID_TOGGLE_KEYBOARD));
        Assert.assertEquals("Ctrl+Shift+P", shortcuts.shortcutFor(CommandShortcuts.ID_PALETTE));
    }

    @Test
    public void testAnEmptyShortcutRemovesTheShortcut() {
        CommandShortcuts shortcuts = parse("command-shortcuts", "search=,new-session=");

        Assert.assertNull(shortcuts.shortcutFor("search"));
        Assert.assertNull(shortcuts.shortcutFor(ShellCommandCatalog.ID_NEW_SESSION));
        Assert.assertEquals("Ctrl+Alt+K", shortcuts.shortcutFor(ShellCommandCatalog.ID_TOGGLE_KEYBOARD));
    }

    @Test
    public void testUnknownCommandIdsAreIgnored() {
        CommandShortcuts shortcuts = parse("command-shortcuts", "not-a-command=Ctrl+Q,search=Ctrl+Alt+G");

        Assert.assertEquals("Ctrl+Alt+G", shortcuts.shortcutFor("search"));
        Assert.assertNull(shortcuts.shortcutFor("not-a-command"));
    }

    @Test
    public void testShortcutsOfTheTerminalActionsCanBeRebound() {
        CommandShortcuts shortcuts = parse(
            "command-shortcuts", "split-vertical=Ctrl+Alt+Up,sessions=Ctrl+Alt+D");

        Assert.assertEquals("Ctrl+Alt+Up", shortcuts.shortcutFor("split-vertical"));
        Assert.assertEquals("Ctrl+Alt+D", shortcuts.shortcutFor("sessions"));
    }
}
