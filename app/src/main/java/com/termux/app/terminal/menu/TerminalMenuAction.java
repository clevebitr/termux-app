package com.termux.app.terminal.menu;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.terminal.TerminalSession;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The actions that can be shown in the terminal context menu, which is shown with a long press and
 * between a text selection, and in the floating text selection toolbar.
 *
 * The ids are used in the `terminal-menu-items` and `selection-toolbar-items` properties, so they must
 * not be changed, only new actions may be added. The order of the ids in those properties is the order
 * that the actions are shown in.
 *
 * The icon is a single unicode character that is shown on the right edge of the item when
 * `terminal-menu-show-icons` is enabled, like the symbols of the extra keys, so that no drawable has to
 * be added for every color scheme.
 */
public enum TerminalMenuAction {

    SEARCH("search", R.string.action_search_terminal, "\u2315"),
    SESSIONS("sessions", R.string.action_sessions_drawer, "\u2630"),
    PREVIOUS_COMMAND("previous-command", R.string.action_previous_command, "\u2191"),
    NEXT_COMMAND("next-command", R.string.action_next_command, "\u2193"),
    SPLIT_VERTICAL("split-vertical", R.string.action_split_screen_vertical, "\u2b13"),
    SPLIT_HORIZONTAL("split-horizontal", R.string.action_split_screen_horizontal, "\u25eb"),
    SPLIT_CLOSE("split-close", R.string.action_split_screen_close, "\u2715"),
    SELECT_URL("select-url", R.string.action_select_url, "\u2197"),
    SHARE_TRANSCRIPT("share-transcript", R.string.action_share_transcript, "\u2026"),
    SHARE_SELECTED_TEXT("share-selected-text", R.string.action_share_selected_text, "\u2398"),
    AUTOFILL_USERNAME("autofill-username", R.string.action_autofill_username, "\u2192"),
    AUTOFILL_PASSWORD("autofill-password", R.string.action_autofill_password, "\u2192"),
    RESET_TERMINAL("reset-terminal", R.string.action_reset_terminal, "\u27f3"),
    KILL_PROCESS("kill-process", R.string.action_kill_process, "\u2715"),
    STYLING("styling", R.string.action_style_terminal, "\u25d0"),
    KEEP_SCREEN_ON("keep-screen-on", R.string.action_toggle_keep_screen_on, "\u2600"),
    HELP("help", R.string.action_open_help, "?"),
    SETTINGS("settings", R.string.action_open_settings, "\u2699"),
    REPORT("report", R.string.action_report_issue, "!"),
    RECENT_NOTIFICATIONS("recent-notifications", R.string.action_recent_notifications, "\u2261"),
    TEST_NOTIFICATION("test-notification", R.string.action_send_test_notification, "\u2691");

    /** The actions that are shown by default, see {@link TerminalMenuConfig#DEFAULT_MENU_ITEMS}. */
    public static final List<TerminalMenuAction> DEFAULT_ITEMS = Collections.unmodifiableList(Arrays.asList(
        SEARCH, SESSIONS, PREVIOUS_COMMAND, NEXT_COMMAND, SPLIT_VERTICAL, SPLIT_HORIZONTAL,
        SELECT_URL, SHARE_TRANSCRIPT, RESET_TERMINAL, KILL_PROCESS, RECENT_NOTIFICATIONS, SETTINGS, REPORT));

    @NonNull
    private final String mId;
    @StringRes
    private final int mTitleRes;
    @NonNull
    private final String mIcon;

    TerminalMenuAction(@NonNull String id, @StringRes int titleRes, @NonNull String icon) {
        mId = id;
        mTitleRes = titleRes;
        mIcon = icon;
    }

    /** @return The id of the action, as used in the `terminal-menu-items` property. */
    @NonNull
    public String getId() {
        return mId;
    }

    @StringRes
    public int getTitleRes() {
        return mTitleRes;
    }

    /** @return The unicode character that is shown on the right edge of the item. */
    @NonNull
    public String getIcon() {
        return mIcon;
    }

    /**
     * @return The resource of the title of the action, which is the label without the pid for the action
     *         that kills the process when {@code session} is {@code null}, since the pid is only known for
     *         a session.
     */
    @StringRes
    int getTitleRes(@Nullable TerminalSession session) {
        if (this == KILL_PROCESS && session == null)
            return R.string.termux_terminal_menu_kill_process_label;
        return mTitleRes;
    }

    /**
     * @return The title of the action, like the label of the item of the context menu, with the pid of
     *         the session filled in for the action that kills the process, and without it when
     *         {@code session} is {@code null}, since the label must never show the raw format argument.
     */
    @NonNull
    public CharSequence getTitle(@NonNull Context context, @Nullable TerminalSession session) {
        if (this == KILL_PROCESS && session != null)
            return context.getString(mTitleRes, session.getPid());
        return context.getString(getTitleRes(session));
    }

    /** @return The action with the id {@code id}, or {@code null} if it is not known. */
    @Nullable
    public static TerminalMenuAction valueOfId(@Nullable String id) {
        if (id == null) return null;
        id = id.trim().toLowerCase(Locale.ROOT);
        for (TerminalMenuAction action : values()) {
            if (action.mId.equals(id)) return action;
        }
        return null;
    }
}
