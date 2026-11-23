package com.termux.app.ext.menu;

import androidx.annotation.NonNull;

/**
 * Decides whether an action of the terminal is shown or enabled, for every entry point that offers it.
 *
 * <p>The rules used to live in the activity, which made them impossible to test and duplicated them wherever
 * the same action was also offered, like the command palette. They are a pure function of the action and a
 * {@link State} snapshot, so they can be tested on the JVM and reused by every entry point that shows the
 * same actions.</p>
 */
public final class TerminalMenuItems {

    private TerminalMenuItems() {
    }

    /** The terminal state that the visibility and enabled rules depend on. */
    public static final class State {
        /** Whether the split screen extension is running, i.e. whether its actions can do anything. */
        public final boolean splitScreenAvailable;
        /** Whether split screen is currently on, i.e. whether the secondary pane is visible. */
        public final boolean splitScreenEnabled;
        /** Whether the search extension is running. */
        public final boolean searchAvailable;
        /** Whether the sessions drawer extension is running. */
        public final boolean sessionsDrawerAvailable;
        public final boolean hasSelectedText;
        public final boolean autoFillEnabled;
        public final boolean notificationsEnabled;
        public final boolean hasRecentNotifications;
        public final boolean sessionRunning;

        public State(boolean splitScreenAvailable, boolean splitScreenEnabled, boolean searchAvailable,
                     boolean sessionsDrawerAvailable, boolean hasSelectedText, boolean autoFillEnabled,
                     boolean notificationsEnabled, boolean hasRecentNotifications,
                     boolean sessionRunning) {
            this.splitScreenAvailable = splitScreenAvailable;
            this.splitScreenEnabled = splitScreenEnabled;
            this.searchAvailable = searchAvailable;
            this.sessionsDrawerAvailable = sessionsDrawerAvailable;
            this.hasSelectedText = hasSelectedText;
            this.autoFillEnabled = autoFillEnabled;
            this.notificationsEnabled = notificationsEnabled;
            this.hasRecentNotifications = hasRecentNotifications;
            this.sessionRunning = sessionRunning;
        }
    }

    /**
     * Whether the action is shown at all in the current state.
     *
     * <p>An action that belongs to a disabled extension is hidden instead of shown as a no-op, since the
     * extension owns its entry points and a disabled extension must not be reachable from the menu or the
     * command palette. The same rule is why the split actions depend on the extension being available and on
     * the current state of the panes.</p>
     */
    public static boolean isVisible(@NonNull TerminalMenuAction action, @NonNull State state) {
        switch (action) {
            case SEARCH:
                return state.searchAvailable;
            case SESSIONS:
                return state.sessionsDrawerAvailable;
            case SPLIT_VERTICAL:
            case SPLIT_HORIZONTAL:
                return state.splitScreenAvailable;
            case SPLIT_CLOSE:
                return state.splitScreenAvailable && state.splitScreenEnabled;
            case SHARE_SELECTED_TEXT:
                return state.hasSelectedText;
            case AUTOFILL_USERNAME:
            case AUTOFILL_PASSWORD:
                return state.autoFillEnabled;
            case TEST_NOTIFICATION:
                return state.notificationsEnabled;
            case RECENT_NOTIFICATIONS:
                return state.hasRecentNotifications;
            default:
                return true;
        }
    }

    /** Whether the action can be run in the current state. */
    public static boolean isEnabled(@NonNull TerminalMenuAction action, @NonNull State state) {
        if (action == TerminalMenuAction.KILL_PROCESS) return state.sessionRunning;
        return true;
    }
}
