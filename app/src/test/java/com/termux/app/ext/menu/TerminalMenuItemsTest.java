package com.termux.app.ext.menu;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The visibility and enabled rules that every entry point of the terminal actions shares. */
public class TerminalMenuItemsTest {

    private static TerminalMenuItems.State state(boolean split, boolean selection, boolean autoFill,
                                                 boolean notifications, boolean recent,
                                                 boolean running) {
        return new TerminalMenuItems.State(split, selection, autoFill, notifications, recent, running);
    }

    private static final TerminalMenuItems.State IDLE =
        state(false, false, false, false, false, true);

    @Test
    public void itemsWithoutConditionsAreAlwaysVisible() {
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.SEARCH, IDLE));
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.SESSIONS, IDLE));
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.SETTINGS, IDLE));
    }

    @Test
    public void splitCloseNeedsAnActiveSplit() {
        assertFalse(TerminalMenuItems.isVisible(TerminalMenuAction.SPLIT_CLOSE, IDLE));
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.SPLIT_CLOSE,
            state(true, false, false, false, false, true)));
    }

    @Test
    public void shareSelectedTextNeedsASelection() {
        assertFalse(TerminalMenuItems.isVisible(TerminalMenuAction.SHARE_SELECTED_TEXT, IDLE));
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.SHARE_SELECTED_TEXT,
            state(false, true, false, false, false, true)));
    }

    @Test
    public void autofillNeedsAutoFillSupport() {
        for (TerminalMenuAction action : Arrays.asList(
            TerminalMenuAction.AUTOFILL_USERNAME, TerminalMenuAction.AUTOFILL_PASSWORD)) {
            assertFalse(TerminalMenuItems.isVisible(action, IDLE));
            assertTrue(TerminalMenuItems.isVisible(action, state(false, false, true, false, false, true)));
        }
    }

    @Test
    public void notificationItemsFollowTheNotificationState() {
        assertFalse(TerminalMenuItems.isVisible(TerminalMenuAction.TEST_NOTIFICATION, IDLE));
        assertFalse(TerminalMenuItems.isVisible(TerminalMenuAction.RECENT_NOTIFICATIONS, IDLE));
        TerminalMenuItems.State notifications = state(false, false, false, true, true, true);
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.TEST_NOTIFICATION, notifications));
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.RECENT_NOTIFICATIONS, notifications));
    }

    @Test
    public void killProcessIsDisabledWhenTheSessionIsNotRunning() {
        TerminalMenuItems.State exited = state(false, false, false, true, true, false);
        assertFalse(TerminalMenuItems.isEnabled(TerminalMenuAction.KILL_PROCESS, exited));
        assertTrue(TerminalMenuItems.isEnabled(TerminalMenuAction.KILL_PROCESS, IDLE));
        assertTrue(TerminalMenuItems.isEnabled(TerminalMenuAction.SEARCH, exited));
    }

    @Test
    public void buildKeepsTheConfiguredOrderAndSkipsHiddenActions() {
        List<TerminalMenuAction> configured = Arrays.asList(
            TerminalMenuAction.SEARCH,
            TerminalMenuAction.SPLIT_CLOSE,
            TerminalMenuAction.SETTINGS);

        List<TerminalMenuDialog.Item> items = TerminalMenuItems.build(
            configured, IDLE, action -> action.name());

        assertEquals(2, items.size());
        assertEquals(TerminalMenuAction.SEARCH, items.get(0).action);
        assertEquals(TerminalMenuAction.SETTINGS, items.get(1).action);
        assertEquals("SEARCH", items.get(0).title.toString());
    }

    @Test
    public void buildReturnsAnEmptyListWhenEverythingIsHidden() {
        List<TerminalMenuDialog.Item> items = TerminalMenuItems.build(
            Collections.singletonList(TerminalMenuAction.SPLIT_CLOSE), IDLE, action -> action.name());

        assertTrue(items.isEmpty());
    }
}
