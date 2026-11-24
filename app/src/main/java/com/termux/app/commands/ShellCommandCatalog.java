package com.termux.app.commands;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.ext.menu.TerminalMenuAction;
import com.termux.app.ext.menu.TerminalMenuItems;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the list of commands that the palette shows.
 *
 * <p>The list reuses the visibility and enabled rules of the terminal actions through
 * {@link TerminalMenuItems}, so that an action that cannot be used is greyed out in the palette exactly as
 * it is hidden or disabled in the menu. The actions and their order come from the `terminal-menu-items`
 * property, which therefore decides which terminal actions the palette shows; the commands that the palette
 * adds on top of them, like the session and keyboard commands, follow after them.</p>
 *
 * <p>The shortcuts of the commands are not part of the catalog: they come from {@link CommandShortcuts}, so
 * that the label of a command and the key that runs it are read from the same configuration.</p>
 */
public final class ShellCommandCatalog {

    /** The actions that the command palette and the buttons of the sessions drawer run, dispatched by the host. */
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

    /** The category of the commands that act on the terminal content. */
    public static final String CATEGORY_TERMINAL = "Terminal";
    /** The category of the commands that act on the app. */
    public static final String CATEGORY_APP = "App";

    /**
     * The actions that the palette always shows after the configured ones, so that the settings and the help
     * of the app stay reachable even if they were removed from the `terminal-menu-items` property, which the
     * old context menu allowed.
     */
    private static final List<TerminalMenuAction> ALWAYS_SHOWN_ACTIONS =
        Arrays.asList(TerminalMenuAction.SETTINGS, TerminalMenuAction.HELP);

    private ShellCommandCatalog() {
    }

    /**
     * Build the commands for the current state.
     *
     * @param menuActions The terminal actions of the `terminal-menu-items` property, in their order.
     * @param state       The snapshot that decides whether an action is shown or enabled.
     * @param shortcuts   The shortcuts of the commands, which are shown in the labels.
     * @param session     The session whose state the titles depend on, or {@code null} for the default titles.
     * @param host        Runs the commands.
     */
    @NonNull
    public static List<ShellCommand> build(@NonNull Context context,
                                           @NonNull List<TerminalMenuAction> menuActions,
                                           @NonNull TerminalMenuItems.State state,
                                           @NonNull CommandShortcuts shortcuts,
                                           @NonNull TerminalSession session,
                                           @NonNull Host host) {
        String terminal = context.getString(R.string.command_category_terminal);
        String app = context.getString(R.string.command_category_app);
        List<ShellCommand> commands = new ArrayList<>();
        Set<TerminalMenuAction> added = EnumSet.noneOf(TerminalMenuAction.class);

        for (TerminalMenuAction action : menuActions) {
            // The palette would open itself, so its own action is not a command of the palette.
            if (action == TerminalMenuAction.COMMAND_PALETTE) continue;
            if (addMenuCommand(context, commands, action, categoryOf(action, terminal, app), state, shortcuts,
                session, host))
                added.add(action);
        }

        addPaletteCommand(commands, ID_NEW_SESSION, context.getString(R.string.action_new_session),
            terminal, shortcuts, true, host::addNewSession);
        addPaletteCommand(commands, ID_NEW_NAMED_SESSION,
            context.getString(R.string.command_new_named_session), terminal, shortcuts, true,
            host::addNamedSession);
        addPaletteCommand(commands, ID_TOGGLE_KEYBOARD,
            context.getString(R.string.action_toggle_soft_keyboard), terminal, shortcuts, true,
            host::toggleSoftKeyboard);

        for (TerminalMenuAction action : ALWAYS_SHOWN_ACTIONS) {
            if (added.contains(action)) continue;
            addMenuCommand(context, commands, action, categoryOf(action, terminal, app), state, shortcuts,
                session, host);
        }

        return commands;
    }

    /** @return The category of the action, which is the app category for the actions that act on the app. */
    @NonNull
    private static String categoryOf(@NonNull TerminalMenuAction action, @NonNull String terminal,
                                     @NonNull String app) {
        switch (action) {
            case SETTINGS:
            case HELP:
            case REPORT:
            case STYLING:
            case KEEP_SCREEN_ON:
            case RECENT_NOTIFICATIONS:
            case TEST_NOTIFICATION:
                return app;
            default:
                return terminal;
        }
    }

    /**
     * Add the command of {@code action}, unless it is hidden in the current state.
     *
     * @return Whether the command was added.
     */
    private static boolean addMenuCommand(@NonNull Context context, @NonNull List<ShellCommand> commands,
                                          @NonNull TerminalMenuAction action, @NonNull String category,
                                          @NonNull TerminalMenuItems.State state,
                                          @NonNull CommandShortcuts shortcuts,
                                          @NonNull TerminalSession session, @NonNull Host host) {
        if (!TerminalMenuItems.isVisible(action, state)) return false;
        commands.add(new ShellCommand(action.getId(), action.getTitle(context, session), category,
            shortcuts.shortcutFor(action.getId()), TerminalMenuItems.isEnabled(action, state),
            () -> host.runTerminalMenuAction(action)));
        return true;
    }

    /** Add a command that the palette itself defines, like the session commands. */
    private static void addPaletteCommand(@NonNull List<ShellCommand> commands, @NonNull String id,
                                          @NonNull CharSequence title, @NonNull String category,
                                          @NonNull CommandShortcuts shortcuts, boolean enabled,
                                          @NonNull Runnable action) {
        commands.add(new ShellCommand(id, title, category, shortcuts.shortcutFor(id), enabled, action));
    }
}
