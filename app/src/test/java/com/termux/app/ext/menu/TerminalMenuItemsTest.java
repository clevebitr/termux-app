package com.termux.app.ext.menu;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The visibility and enabled rules that every entry point of the terminal actions shares. */
public class TerminalMenuItemsTest {

    /** @return A state in which every extension is running, so that the tests only vary their own flag. */
    private static TerminalMenuItems.State state(boolean split, boolean selection, boolean autoFill,
                                                 boolean notifications, boolean recent,
                                                 boolean running) {
        return new TerminalMenuItems.State(true, split, true, true, selection, autoFill, notifications,
            recent, running);
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
    public void actionsOfADisabledExtensionAreHidden() {
        TerminalMenuItems.State noExtensions = new TerminalMenuItems.State(
            false, false, false, false, false, false, false, false, true);

        for (TerminalMenuAction action : Arrays.asList(
            TerminalMenuAction.SEARCH, TerminalMenuAction.SESSIONS,
            TerminalMenuAction.SPLIT_VERTICAL, TerminalMenuAction.SPLIT_HORIZONTAL,
            TerminalMenuAction.SPLIT_CLOSE)) {
            assertFalse(action.name() + " must be hidden while its extension is disabled",
                TerminalMenuItems.isVisible(action, noExtensions));
        }

        // The actions that the host implements itself do not depend on an extension.
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.SETTINGS, noExtensions));
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.RESET_TERMINAL, noExtensions));
    }

    @Test
    public void splitActionsFollowAvailabilityAndTheCurrentSplitState() {
        TerminalMenuItems.State availableAndClosed = new TerminalMenuItems.State(
            true, false, true, true, false, false, false, false, true);
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.SPLIT_VERTICAL, availableAndClosed));
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.SPLIT_HORIZONTAL, availableAndClosed));
        assertFalse(TerminalMenuItems.isVisible(TerminalMenuAction.SPLIT_CLOSE, availableAndClosed));

        TerminalMenuItems.State availableAndOpen = new TerminalMenuItems.State(
            true, true, true, true, false, false, false, false, true);
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.SPLIT_VERTICAL, availableAndOpen));
        assertTrue(TerminalMenuItems.isVisible(TerminalMenuAction.SPLIT_CLOSE, availableAndOpen));
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

}
