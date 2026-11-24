package com.termux.app.ext.menu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.app.properties.ForkProperties;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.view.textselection.TextSelectionCursorController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The configuration of the terminal actions of the command palette and of the floating text selection
 * toolbar, parsed from the `terminal-menu-items` and `selection-toolbar-items` properties of the
 * `termux.properties` file.
 *
 * <p>The actions and their order decide which terminal actions the command palette shows; the appearance of
 * the old context menu dialog is not configurable anymore, since the palette follows the color scheme of the
 * terminal and the shape tokens of the app.</p>
 *
 * <p>Like the `status-bar-*` and `notification-*` keys, these keys are not part of
 * {@link TermuxPropertyConstants#TERMUX_APP_PROPERTIES_LIST}, so the properties must be read with
 * {@code TermuxAppSharedProperties.getProperties(false)}, which reads the file instead of returning the
 * memory cache that is filtered by that list.</p>
 */
public final class TerminalMenuConfig {

    private static final String LOG_TAG = "TerminalMenuConfig";

    /**
     * The actions that are shown in the terminal context menu by default. The interesting actions that
     * are not listed here, like `styling`, `keep-screen-on` and `help`, are only shown when the user
     * adds them to the `terminal-menu-items` property.
     */
    public static final String DEFAULT_MENU_ITEMS = "search,sessions,previous-command,next-command,"
        + "split-vertical,split-horizontal,select-url,share-transcript,"
        + "reset-terminal,kill-process,recent-notifications,settings,report";

    /** The actions that the floating text selection toolbar shows by default. */
    public static final String DEFAULT_SELECTION_TOOLBAR_ITEMS = "copy,paste,more";

    /** The id of the copy action of the selection toolbar, see {@link #selectionToolbarActions}. */
    public static final String SELECTION_TOOLBAR_COPY = "copy";
    /** The id of the paste action of the selection toolbar, see {@link #selectionToolbarActions}. */
    public static final String SELECTION_TOOLBAR_PASTE = "paste";
    /** The id of the more action of the selection toolbar, see {@link #selectionToolbarActions}. */
    public static final String SELECTION_TOOLBAR_MORE = "more";

    /** The ids that the `selection-toolbar-items` property supports. */
    public static final List<String> SELECTION_TOOLBAR_IDS = Collections.unmodifiableList(Arrays.asList(
        SELECTION_TOOLBAR_COPY, SELECTION_TOOLBAR_PASTE, SELECTION_TOOLBAR_MORE));

    /** The actions that are shown in the terminal context menu, in the order they are shown. */
    @NonNull
    public final List<TerminalMenuAction> menuItems = new ArrayList<>();

    /**
     * The actions of the floating text selection toolbar, in the order they are shown, as the
     * {@code ACTION_*} constants of {@link TextSelectionCursorController}.
     */
    @NonNull
    public final List<Integer> selectionToolbarActions = new ArrayList<>();

    /**
     * @return The configuration with the default actions and the default selection toolbar, which is used while
     *         the extension is disabled, so that the palette and the text selection toolbar keep working instead
     *         of showing no action at all.
     */
    @NonNull
    public static TerminalMenuConfig defaults() {
        return parse(null);
    }

    /**
     * Parse the configuration from the {@code properties} of the `termux.properties` file. Invalid or
     * out of range values fall back to their defaults instead of throwing, so that a broken
     * configuration never prevents the app from starting.
     */
    @NonNull
    public static TerminalMenuConfig parse(@Nullable Properties properties) {
        TerminalMenuConfig config = new TerminalMenuConfig();
        if (properties == null) properties = new Properties();

        String menuItems = properties.getProperty(TermuxPropertyConstants.KEY_TERMINAL_MENU_ITEMS, DEFAULT_MENU_ITEMS);
        for (String id : ForkProperties.parseList(menuItems)) {
            TerminalMenuAction action = TerminalMenuAction.valueOfId(id);
            if (action == null) {
                Logger.logWarn(LOG_TAG, "Ignoring unknown value \"" + id + "\" for the \""
                    + TermuxPropertyConstants.KEY_TERMINAL_MENU_ITEMS + "\" property, supported values: "
                    + getSupportedMenuItems());
                continue;
            }
            if (!config.menuItems.contains(action)) config.menuItems.add(action);
        }

        String selectionItems = properties.getProperty(TermuxPropertyConstants.KEY_TERMINAL_MENU_SELECTION_TOOLBAR_ITEMS,
            DEFAULT_SELECTION_TOOLBAR_ITEMS);
        for (String id : ForkProperties.parseList(selectionItems)) {
            int action = getSelectionToolbarAction(id);
            if (action == 0) {
                Logger.logWarn(LOG_TAG, "Ignoring unknown value \"" + id + "\" for the \""
                    + TermuxPropertyConstants.KEY_TERMINAL_MENU_SELECTION_TOOLBAR_ITEMS + "\" property, supported values: "
                    + SELECTION_TOOLBAR_IDS);
                continue;
            }
            if (!config.selectionToolbarActions.contains(action)) config.selectionToolbarActions.add(action);
        }

        // An empty toolbar would make the selection unusable, since the user could not copy the selected
        // text anymore, so the default actions are used if all of them were removed.
        if (config.selectionToolbarActions.isEmpty())
            config.selectionToolbarActions.addAll(getDefaultSelectionToolbarActions());

        return config;
    }

    /** @return The {@link TextSelectionCursorController} action of the selection toolbar id, or 0. */
    public static int getSelectionToolbarAction(@Nullable String id) {
        if (DataUtils.isNullOrEmpty(id)) return 0;
        switch (id.trim().toLowerCase(Locale.ROOT)) {
            case SELECTION_TOOLBAR_COPY:
                return TextSelectionCursorController.ACTION_COPY;
            case SELECTION_TOOLBAR_PASTE:
                return TextSelectionCursorController.ACTION_PASTE;
            case SELECTION_TOOLBAR_MORE:
                return TextSelectionCursorController.ACTION_MORE;
            default:
                return 0;
        }
    }

    /** @return The actions of the selection toolbar by default, in the order they are shown. */
    @NonNull
    public static List<Integer> getDefaultSelectionToolbarActions() {
        return Arrays.asList(TextSelectionCursorController.ACTION_COPY,
            TextSelectionCursorController.ACTION_PASTE,
            TextSelectionCursorController.ACTION_MORE);
    }

    /** @return The ids of the actions that the `terminal-menu-items` property supports. */
    @NonNull
    private static String getSupportedMenuItems() {
        StringBuilder builder = new StringBuilder();
        for (TerminalMenuAction action : TerminalMenuAction.values()) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(action.getId());
        }
        return builder.toString();
    }

}
