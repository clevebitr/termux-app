package com.termux.app.terminal.menu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.view.textselection.TextSelectionCursorController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The configuration of the terminal context menu and of the floating text selection toolbar, parsed
 * from the `terminal-menu-*` and `selection-toolbar-items` properties of the `termux.properties` file.
 *
 * Like the `status-bar-*` and `notification-*` keys, these keys are not part of
 * {@link TermuxPropertyConstants#TERMUX_APP_PROPERTIES_LIST}, so the properties must be read with
 * {@code TermuxAppSharedProperties.getProperties(false)}, which reads the file instead of returning the
 * memory cache that is filtered by that list.
 */
public final class TerminalMenuConfig {

    private static final String LOG_TAG = "TerminalMenuConfig";

    /**
     * The actions that are shown in the terminal context menu by default. The interesting actions that
     * are not listed here, like `styling`, `keep-screen-on` and `help`, are only shown when the user
     * adds them to the `terminal-menu-items` property.
     */
    public static final String DEFAULT_MENU_ITEMS = "search,previous-command,next-command,"
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

    public static final int DEFAULT_FONT_SIZE_SP = 13;
    public static final int MIN_FONT_SIZE_SP = 8;
    public static final int MAX_FONT_SIZE_SP = 24;

    /** The radius of the corners in dp, 0 for the square corners of a tui window. */
    public static final int DEFAULT_CORNER_RADIUS_DP = 0;
    public static final int MIN_CORNER_RADIUS_DP = 0;
    public static final int MAX_CORNER_RADIUS_DP = 32;

    /** The width of the border around the dialog in dp, 0 for no border. */
    public static final int DEFAULT_BORDER_WIDTH_DP = 1;
    public static final int MIN_BORDER_WIDTH_DP = 0;
    public static final int MAX_BORDER_WIDTH_DP = 8;

    /** The actions that are shown in the terminal context menu, in the order they are shown. */
    @NonNull
    public final List<TerminalMenuAction> menuItems = new ArrayList<>();

    /**
     * The actions of the floating text selection toolbar, in the order they are shown, as the
     * {@code ACTION_*} constants of {@link TextSelectionCursorController}.
     */
    @NonNull
    public final List<Integer> selectionToolbarActions = new ArrayList<>();

    public int fontSizeSp = DEFAULT_FONT_SIZE_SP;
    public int cornerRadiusDp = DEFAULT_CORNER_RADIUS_DP;
    public int borderWidthDp = DEFAULT_BORDER_WIDTH_DP;
    /** Whether the text is drawn with the monospace font of the terminal. */
    public boolean monospace = true;
    /** Whether the items show an icon on their right edge. */
    public boolean showIcons = true;
    /** The background color, empty to derive it from the color scheme. */
    @NonNull
    public String backgroundColor = "";
    /** The text color, empty to use the foreground color of the color scheme. */
    @NonNull
    public String foregroundColor = "";
    /** The accent color, empty to use the `color4` of the color scheme. */
    @NonNull
    public String accentColor = "";

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
        for (String id : split(menuItems)) {
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
        for (String id : split(selectionItems)) {
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

        config.fontSizeSp = getInt(properties, TermuxPropertyConstants.KEY_TERMINAL_MENU_FONT_SIZE,
            DEFAULT_FONT_SIZE_SP, MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP);
        config.cornerRadiusDp = getInt(properties, TermuxPropertyConstants.KEY_TERMINAL_MENU_CORNER_RADIUS,
            DEFAULT_CORNER_RADIUS_DP, MIN_CORNER_RADIUS_DP, MAX_CORNER_RADIUS_DP);
        config.borderWidthDp = getInt(properties, TermuxPropertyConstants.KEY_TERMINAL_MENU_BORDER_WIDTH,
            DEFAULT_BORDER_WIDTH_DP, MIN_BORDER_WIDTH_DP, MAX_BORDER_WIDTH_DP);
        config.monospace = getBoolean(properties, TermuxPropertyConstants.KEY_TERMINAL_MENU_MONOSPACE, true);
        config.showIcons = getBoolean(properties, TermuxPropertyConstants.KEY_TERMINAL_MENU_SHOW_ICONS, true);
        config.backgroundColor = getString(properties, TermuxPropertyConstants.KEY_TERMINAL_MENU_BACKGROUND_COLOR, "").trim();
        config.foregroundColor = getString(properties, TermuxPropertyConstants.KEY_TERMINAL_MENU_FOREGROUND_COLOR, "").trim();
        config.accentColor = getString(properties, TermuxPropertyConstants.KEY_TERMINAL_MENU_ACCENT_COLOR, "").trim();

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

    /** @return The comma separated {@code value}, in lower case, without duplicates and empty values. */
    @NonNull
    private static List<String> split(@Nullable String value) {
        List<String> list = new ArrayList<>();
        if (DataUtils.isNullOrEmpty(value)) return list;

        for (String item : value.split(",")) {
            item = item.trim().toLowerCase(Locale.ROOT);
            if (item.isEmpty() || list.contains(item)) continue;
            list.add(item);
        }

        return list;
    }

    @NonNull
    private static String getString(@NonNull Properties properties, @NonNull String key, @NonNull String defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : value;
    }

    private static boolean getBoolean(@NonNull Properties properties, @NonNull String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (DataUtils.isNullOrEmpty(value)) return defaultValue;
        return Boolean.parseBoolean(value.trim());
    }

    private static int getInt(@NonNull Properties properties, @NonNull String key, int defaultValue, int min, int max) {
        String value = properties.getProperty(key);
        if (DataUtils.isNullOrEmpty(value)) return defaultValue;

        int intValue = DataUtils.getIntFromString(value, defaultValue);
        if (intValue < min || intValue > max) {
            Logger.logWarn(LOG_TAG, "The value \"" + value + "\" for the \"" + key + "\" property is out of range [" + min + ", " + max + "], using default value \"" + defaultValue + "\"");
            return defaultValue;
        }

        return intValue;
    }
}
