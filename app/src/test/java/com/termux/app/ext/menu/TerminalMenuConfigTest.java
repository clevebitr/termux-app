package com.termux.app.ext.menu;

import com.termux.shared.logger.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Properties;

/**
 * Tests the parsing of the `terminal-menu-items` and `selection-toolbar-items` properties, since the
 * order of the actions and the fallback to the default actions decide what the user sees in the
 * terminal context menu and in the text selection toolbar.
 *
 * The tests do not need a {@code RobolectricTestRunner}, since only the properties are parsed, so the
 * android logging is turned off instead of being mocked.
 */
public class TerminalMenuConfigTest {

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

    private TerminalMenuConfig parse(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return TerminalMenuConfig.parse(properties);
    }

    @Test
    public void testDefaultMenuItems() {
        TerminalMenuConfig config = parse();

        Assert.assertEquals(TerminalMenuAction.DEFAULT_ITEMS, config.menuItems);
        Assert.assertEquals(13, config.menuItems.size());
        Assert.assertEquals(TerminalMenuAction.SEARCH, config.menuItems.get(0));
        Assert.assertEquals(TerminalMenuAction.SESSIONS, config.menuItems.get(1));
        Assert.assertEquals(TerminalMenuAction.RECENT_NOTIFICATIONS, config.menuItems.get(10));
        Assert.assertEquals(TerminalMenuAction.SETTINGS, config.menuItems.get(11));
        Assert.assertEquals(TerminalMenuAction.REPORT, config.menuItems.get(12));
    }

    @Test
    public void testMenuItemsKeepTheConfiguredOrder() {
        TerminalMenuConfig config = parse("terminal-menu-items", "kill-process,search,settings");

        Assert.assertEquals(Arrays.asList(TerminalMenuAction.KILL_PROCESS,
            TerminalMenuAction.SEARCH, TerminalMenuAction.SETTINGS), config.menuItems);
    }

    @Test
    public void testUnknownMenuItemsAreIgnored() {
        TerminalMenuConfig config = parse("terminal-menu-items", "search,not-an-action,settings");

        Assert.assertEquals(Arrays.asList(TerminalMenuAction.SEARCH, TerminalMenuAction.SETTINGS), config.menuItems);
    }

    @Test
    public void testDuplicateMenuItemsAreRemoved() {
        TerminalMenuConfig config = parse("terminal-menu-items", "search,SEARCH, search ,settings");

        Assert.assertEquals(Arrays.asList(TerminalMenuAction.SEARCH, TerminalMenuAction.SETTINGS), config.menuItems);
    }

    @Test
    public void testEmptyMenuItemsList() {
        // An empty list is allowed, since a user may only want the actions of the extra keys.
        Assert.assertTrue(parse("terminal-menu-items", "").menuItems.isEmpty());
    }

    @Test
    public void testOptionalMenuItemsCanBeAdded() {
        TerminalMenuConfig config = parse("terminal-menu-items", "styling,keep-screen-on,help,test-notification");

        Assert.assertEquals(Arrays.asList(TerminalMenuAction.STYLING, TerminalMenuAction.KEEP_SCREEN_ON,
            TerminalMenuAction.HELP, TerminalMenuAction.TEST_NOTIFICATION), config.menuItems);
    }

    @Test
    public void testDefaultSelectionToolbarItems() {
        TerminalMenuConfig config = parse();

        Assert.assertEquals(TerminalMenuConfig.getDefaultSelectionToolbarActions(), config.selectionToolbarActions);
    }

    @Test
    public void testSelectionToolbarItemsKeepTheConfiguredOrder() {
        TerminalMenuConfig config = parse("selection-toolbar-items", "more,copy");

        Assert.assertEquals(Arrays.asList(TerminalMenuConfig.getSelectionToolbarAction("more"),
            TerminalMenuConfig.getSelectionToolbarAction("copy")), config.selectionToolbarActions);
    }

    @Test
    public void testEmptySelectionToolbarFallsBackToTheDefaults() {
        // The selection toolbar cannot be empty, otherwise the user could not copy the selected text.
        Assert.assertEquals(TerminalMenuConfig.getDefaultSelectionToolbarActions(),
            parse("selection-toolbar-items", "").selectionToolbarActions);
        Assert.assertEquals(TerminalMenuConfig.getDefaultSelectionToolbarActions(),
            parse("selection-toolbar-items", "unknown-action").selectionToolbarActions);
    }

    @Test
    public void testSelectionToolbarIdsMapToTheActions() {
        Assert.assertEquals(Integer.valueOf(com.termux.view.textselection.TextSelectionCursorController.ACTION_COPY),
            Integer.valueOf(TerminalMenuConfig.getSelectionToolbarAction("copy")));
        Assert.assertEquals(Integer.valueOf(com.termux.view.textselection.TextSelectionCursorController.ACTION_PASTE),
            Integer.valueOf(TerminalMenuConfig.getSelectionToolbarAction("paste")));
        Assert.assertEquals(Integer.valueOf(com.termux.view.textselection.TextSelectionCursorController.ACTION_MORE),
            Integer.valueOf(TerminalMenuConfig.getSelectionToolbarAction("more")));
        Assert.assertEquals(0, TerminalMenuConfig.getSelectionToolbarAction("unknown"));
        Assert.assertEquals(0, TerminalMenuConfig.getSelectionToolbarAction(null));
    }

    @Test
    public void testMenuItemsOrderIsKeptAndDuplicatesDropped() {
        TerminalMenuConfig config = parse(
            "terminal-menu-items", "settings, search, settings, unknown, split-vertical");

        Assert.assertEquals(3, config.menuItems.size());
        Assert.assertEquals(TerminalMenuAction.SETTINGS, config.menuItems.get(0));
        Assert.assertEquals(TerminalMenuAction.SEARCH, config.menuItems.get(1));
        Assert.assertEquals(TerminalMenuAction.SPLIT_VERTICAL, config.menuItems.get(2));
    }

    @Test
    public void testActionIdsRoundTrip() {
        for (TerminalMenuAction action : TerminalMenuAction.values()) {
            Assert.assertEquals(action, TerminalMenuAction.valueOfId(action.getId()));
            Assert.assertEquals(action, TerminalMenuAction.valueOfId("  " + action.getId().toUpperCase() + " "));
        }

        Assert.assertNull(TerminalMenuAction.valueOfId(null));
        Assert.assertNull(TerminalMenuAction.valueOfId("not-an-action"));
    }
}
