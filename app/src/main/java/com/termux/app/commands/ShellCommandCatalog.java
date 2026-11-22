package com.termux.app.commands;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.terminal.menu.TerminalMenuAction;
import com.termux.app.terminal.menu.TerminalMenuItems;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the list of commands that the palette shows.
 *
 * <p>The list reuses the visibility and enabled rules of the terminal context menu through
 * {@link TerminalMenuItems}, so that an action that cannot be used is greyed out in the palette exactly as
 * it is hidden or disabled in the menu. Adding a command here makes it available from every entry point
 * that shows the palette.</p>
 */
public final class ShellCommandCatalog {

    /** The actions that the palette runs, dispatched by the host. */
    public interface Host {
        void runTerminalMenuAction(@NonNull TerminalMenuAction action);
        /** Create a session without a name. */
        void addNewSession();
        /** Ask for a name and then create the session. */
        void addNamedSession();
        /** Toggle the soft keyboard, following the configured soft keyboard behaviour. */
        void toggleSoftKeyboard();
    }

    /** The id of the command that creates a session without a name. */
    public static final String ID_NEW_SESSION = "new-session";
    /** The id of the command that asks for a name and then creates the session. */
    public static final String ID_NEW_NAMED_SESSION = "new-named-session";
    /** The id of the command that toggles the soft keyboard. */
    public static final String ID_TOGGLE_KEYBOARD = "toggle-keyboard";

    private ShellCommandCatalog() {
    }

    /** The category of the commands that act on the terminal content. */
    public static final String CATEGORY_TERMINAL = "Terminal";
    /** The category of the commands that act on the app. */
    public static final String CATEGORY_APP = "App";

    /**
     * Build the commands for the current state.
     *
     * @param state  The snapshot that decides whether an action is shown or enabled.
     * @param titles Resolves the title of a terminal action for the current session.
     * @param host   Runs the commands.
     */
    @NonNull
    public static List<ShellCommand> build(@NonNull Context context,
                                           @NonNull TerminalMenuItems.State state,
                                           @NonNull TerminalMenuItems.TitleResolver titles,
                                           @NonNull Host host) {
        String terminal = context.getString(R.string.command_category_terminal);
        String app = context.getString(R.string.command_category_app);
        List<ShellCommand> commands = new ArrayList<>();

        addMenuCommand(commands, TerminalMenuAction.SEARCH, terminal, null, state, titles, host);
        addMenuCommand(commands, TerminalMenuAction.SESSIONS, terminal, null, state, titles, host);
        addMenuCommand(commands, TerminalMenuAction.PREVIOUS_COMMAND, terminal, "Ctrl+Alt+Up", state, titles, host);
        addMenuCommand(commands, TerminalMenuAction.NEXT_COMMAND, terminal, "Ctrl+Alt+Down", state, titles, host);
        addMenuCommand(commands, TerminalMenuAction.SPLIT_VERTICAL, terminal, null, state, titles, host);
        addMenuCommand(commands, TerminalMenuAction.SPLIT_HORIZONTAL, terminal, null, state, titles, host);
        addMenuCommand(commands, TerminalMenuAction.SPLIT_CLOSE, terminal, null, state, titles, host);
        addMenuCommand(commands, TerminalMenuAction.RESET_TERMINAL, terminal, null, state, titles, host);
        addMenuCommand(commands, TerminalMenuAction.KILL_PROCESS, terminal, null, state, titles, host);

        commands.add(new ShellCommand(ID_NEW_SESSION, context.getString(R.string.action_new_session),
            terminal, null, true, host::addNewSession));
        commands.add(new ShellCommand(ID_NEW_NAMED_SESSION,
            context.getString(R.string.command_new_named_session), terminal, null, true,
            host::addNamedSession));
        commands.add(new ShellCommand(ID_TOGGLE_KEYBOARD,
            context.getString(R.string.action_toggle_soft_keyboard), terminal, null, true,
            host::toggleSoftKeyboard));

        addMenuCommand(commands, TerminalMenuAction.SETTINGS, app, null, state, titles, host);
        addMenuCommand(commands, TerminalMenuAction.HELP, app, null, state, titles, host);
        addMenuCommand(commands, TerminalMenuAction.RECENT_NOTIFICATIONS, app, null, state, titles, host);
        return commands;
    }

    private static void addMenuCommand(@NonNull List<ShellCommand> commands,
                                       @NonNull TerminalMenuAction action,
                                       @NonNull String category,
                                       String shortcut,
                                       @NonNull TerminalMenuItems.State state,
                                       @NonNull TerminalMenuItems.TitleResolver titles,
                                       @NonNull Host host) {
        if (!TerminalMenuItems.isVisible(action, state)) return;
        commands.add(new ShellCommand(action.getId(), titles.titleOf(action), category, shortcut,
            TerminalMenuItems.isEnabled(action, state), () -> host.runTerminalMenuAction(action)));
    }
}
