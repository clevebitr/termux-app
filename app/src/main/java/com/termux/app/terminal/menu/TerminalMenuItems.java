package com.termux.app.terminal.menu;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the items of the terminal context menu from the configured actions and the state of the terminal.
 *
 * <p>The rules that decide whether an action is shown or enabled used to live in the activity, which made
 * them impossible to test and duplicated them wherever the same action was also offered, like the command
 * palette that is being added. They are a pure function of the action and a {@link State} snapshot, so they
 * can be tested on the JVM and reused by every entry point that shows the same actions.</p>
 */
public final class TerminalMenuItems {

    private TerminalMenuItems() {
    }

    /** The terminal state that the visibility and enabled rules depend on. */
    public static final class State {
        public final boolean splitScreenEnabled;
        public final boolean hasSelectedText;
        public final boolean autoFillEnabled;
        public final boolean notificationsEnabled;
        public final boolean hasRecentNotifications;
        public final boolean sessionRunning;

        public State(boolean splitScreenEnabled, boolean hasSelectedText, boolean autoFillEnabled,
                     boolean notificationsEnabled, boolean hasRecentNotifications,
                     boolean sessionRunning) {
            this.splitScreenEnabled = splitScreenEnabled;
            this.hasSelectedText = hasSelectedText;
            this.autoFillEnabled = autoFillEnabled;
            this.notificationsEnabled = notificationsEnabled;
            this.hasRecentNotifications = hasRecentNotifications;
            this.sessionRunning = sessionRunning;
        }
    }

    /** Resolves the title of an action, which depends on the session and on the language of the app. */
    public interface TitleResolver {
        @NonNull
        CharSequence titleOf(@NonNull TerminalMenuAction action);
    }

    /** Whether the action is shown at all in the current state. */
    public static boolean isVisible(@NonNull TerminalMenuAction action, @NonNull State state) {
        switch (action) {
            case SPLIT_CLOSE:
                return state.splitScreenEnabled;
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

    /**
     * Build the items for the configured actions, skipping the ones that are not visible.
     *
     * @return The items in the configured order; empty when every action is hidden.
     */
    @NonNull
    public static List<TerminalMenuDialog.Item> build(@NonNull List<TerminalMenuAction> actions,
                                                      @NonNull State state,
                                                      @NonNull TitleResolver titles) {
        List<TerminalMenuDialog.Item> items = new ArrayList<>();
        for (TerminalMenuAction action : actions) {
            if (!isVisible(action, state)) continue;
            items.add(new TerminalMenuDialog.Item(action, titles.titleOf(action), isEnabled(action, state)));
        }
        return items;
    }
}
