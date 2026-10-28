package com.termux.app.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The configuration of the notification cards, parsed from the `notification-*` properties of the
 * `termux.properties` file.
 *
 * Every option can also be set from the settings GUI, which writes the same property keys to the
 * file, so the file and the GUI are two views of the same configuration and can be mixed freely.
 *
 * Like the `status-bar-*` keys, the `notification-*` keys are not part of
 * {@link TermuxPropertyConstants#TERMUX_APP_PROPERTIES_LIST}, so the properties must be read with
 * {@code TermuxAppSharedProperties.getProperties(false)}, which reads the file instead of returning
 * the memory cache that is filtered by that list.
 */
public final class NotificationConfig {

    private static final String LOG_TAG = "NotificationConfig";

    /** The value of the `notification-position` property to show the cards in the top right corner. */
    public static final String POSITION_TOP_END = "top-end";
    /** The value of the `notification-position` property to show the cards in the top left corner. */
    public static final String POSITION_TOP_START = "top-start";
    /** The value of the `notification-position` property to show the cards in the bottom right corner. */
    public static final String POSITION_BOTTOM_END = "bottom-end";
    /** The value of the `notification-position` property to show the cards in the bottom left corner. */
    public static final String POSITION_BOTTOM_START = "bottom-start";

    /** The positions that the `notification-position` property supports. */
    public static final List<String> POSITIONS = Collections.unmodifiableList(Arrays.asList(
        POSITION_TOP_END, POSITION_TOP_START, POSITION_BOTTOM_END, POSITION_BOTTOM_START));

    /** The id of the app event that is shown when a session exits. */
    public static final String EVENT_SESSION_EXIT = "session-exit";
    /** The id of the app event that is shown when a bell is received. */
    public static final String EVENT_BELL = "bell";
    /** The id of the app event that is shown when the title of a session changes. */
    public static final String EVENT_TITLE = "title";
    /** The id of the app event that is shown when a plugin execution command finishes. */
    public static final String EVENT_PLUGIN_RESULT = "plugin-result";

    /** The app events that can be shown as a card. */
    public static final List<String> EVENT_IDS = Collections.unmodifiableList(Arrays.asList(
        EVENT_SESSION_EXIT, EVENT_BELL, EVENT_TITLE, EVENT_PLUGIN_RESULT));

    /** The default value of the `notification-events` property, so that the user only has to enable the switch. */
    public static final String DEFAULT_EVENTS = EVENT_SESSION_EXIT + "," + EVENT_BELL + "," + EVENT_TITLE + "," + EVENT_PLUGIN_RESULT;

    /**
     * The default value of the `notification-exit-code-ignore-commands` property.
     *
     * These commands return a non-zero exit code by design, like `grep` when it does not find a
     * match, so showing a card for them would only be noise. The value can be set to an empty string
     * to show a card for every command that exits with a non-zero exit code.
     */
    public static final String DEFAULT_EXIT_CODE_IGNORE_COMMANDS = "grep,test,diff,true,false,rg,fd,[";

    public static final int DEFAULT_MAX_VISIBLE = 3;
    public static final int MIN_MAX_VISIBLE = 1;
    public static final int MAX_MAX_VISIBLE = 8;

    public static final int DEFAULT_DURATION_MS = 4000;
    public static final int MIN_DURATION_MS = 0;
    public static final int MAX_DURATION_MS = 120000;

    /** The value of the `notification-width` property that makes the cards fit their content. */
    public static final int WIDTH_AUTO = 0;
    public static final int MIN_WIDTH_DP = 0;
    public static final int MAX_WIDTH_DP = 600;
    /** The maximum width of a card that fits its content, so that long text does not fill the terminal. */
    public static final int AUTO_MAX_WIDTH_DP = 320;

    /**
     * The radius of the corners of the cards in dp. The default is 0, since the cards look like part of
     * the terminal with the square corners of a tui window instead of like material design cards.
     */
    public static final int DEFAULT_CORNER_RADIUS_DP = 0;
    public static final int MIN_CORNER_RADIUS_DP = 0;
    public static final int MAX_CORNER_RADIUS_DP = 32;

    /** The elevation of the cards in dp, which is 0 for the flat look of a tui window. */
    public static final int DEFAULT_ELEVATION_DP = 0;
    public static final int MIN_ELEVATION_DP = 0;
    public static final int MAX_ELEVATION_DP = 24;

    /** The width of the border around the cards in dp, `0` for no border. */
    public static final int DEFAULT_BORDER_WIDTH_DP = 1;
    public static final int MIN_BORDER_WIDTH_DP = 0;
    public static final int MAX_BORDER_WIDTH_DP = 8;

    /** The width of the accent bar on the left edge of the cards in dp, `0` for no accent bar. */
    public static final int DEFAULT_ACCENT_WIDTH_DP = 4;
    public static final int MIN_ACCENT_WIDTH_DP = 0;
    public static final int MAX_ACCENT_WIDTH_DP = 16;

    /** The maximum number of lines of the body of a card. */
    public static final int DEFAULT_BODY_MAX_LINES = 2;
    public static final int MIN_BODY_MAX_LINES = 1;
    public static final int MAX_BODY_MAX_LINES = 6;

    public static final int DEFAULT_MIN_DURATION_SECONDS = 0;
    public static final int MIN_MIN_DURATION_SECONDS = 0;
    public static final int MAX_MIN_DURATION_SECONDS = 3600;

    public static final int DEFAULT_HISTORY_SIZE = 50;
    public static final int MIN_HISTORY_SIZE = 0;
    public static final int MAX_HISTORY_SIZE = 200;

    /** Whether the notification cards are shown. This is the master switch of all channels. */
    public boolean enabled;
    /** The corner of the terminal that the cards are shown in, one of {@link #POSITIONS}. */
    @NonNull
    public String position = POSITION_TOP_END;
    /** The maximum number of cards that are shown at the same time, older cards are closed first. */
    public int maxVisible = DEFAULT_MAX_VISIBLE;
    /** How long a card is shown for in milliseconds, or {@link #MIN_DURATION_MS} to not close it automatically. */
    public int durationMs = DEFAULT_DURATION_MS;
    /** The width of the cards in dp, or {@link #WIDTH_AUTO} to fit their content. */
    public int widthDp = WIDTH_AUTO;
    /** The radius of the corners of the cards in dp, 0 for the square corners of a tui window. */
    public int cornerRadiusDp = DEFAULT_CORNER_RADIUS_DP;
    /** The elevation of the cards in dp, 0 for the flat look of a tui window. */
    public int elevationDp = DEFAULT_ELEVATION_DP;
    /** The width of the border around the cards in dp, 0 for no border. */
    public int borderWidthDp = DEFAULT_BORDER_WIDTH_DP;
    /** The width of the accent bar on the left edge of the cards in dp, 0 for no accent bar. */
    public int accentWidthDp = DEFAULT_ACCENT_WIDTH_DP;
    /** Whether the text of the cards is drawn with the monospace font of the terminal. */
    public boolean monospace = true;
    /** The maximum number of lines of the body of a card. */
    public int bodyMaxLines = DEFAULT_BODY_MAX_LINES;

    /** The background color of the cards, empty to derive it from the color scheme. */
    @NonNull
    public String backgroundColor = "";
    /** The text color of the cards, empty to use the foreground color of the color scheme. */
    @NonNull
    public String foregroundColor = "";
    /** The accent color of failed commands, empty to use the `color1` of the color scheme. */
    @NonNull
    public String accentFailureColor = "";
    /** The accent color of successful commands, empty to use the `color2` of the color scheme. */
    @NonNull
    public String accentSuccessColor = "";
    /** The accent color of warnings, empty to use the `color3` of the color scheme. */
    @NonNull
    public String accentWarningColor = "";
    /** The accent color of informational notifications, empty to use the `color4` of the color scheme. */
    @NonNull
    public String accentInfoColor = "";

    /** Whether a card is shown when a command exits with a non-zero exit code. */
    public boolean exitCodeEnabled = true;
    /** Whether the working directory is shown in the card of a finished command. */
    public boolean exitCodeShowCwd = true;
    /** Whether the session name is shown in the card of a finished command. */
    public boolean exitCodeShowSession = true;
    /** The minimum duration in seconds a command must have run for to show a card, or 0 for all commands. */
    public int exitCodeMinDurationSeconds = DEFAULT_MIN_DURATION_SECONDS;
    /** The names of the commands that do not show a card when they exit, in lower case. */
    @NonNull
    public final List<String> exitCodeIgnoreCommands = new ArrayList<>();

    /** Whether the cards of app events, like an exited session, are shown. */
    public boolean eventsEnabled = false;
    /** The app events that show a card, in lower case, one or more of {@link #EVENT_IDS}. */
    @NonNull
    public final List<String> events = new ArrayList<>();

    /** Whether the cards of `OSC 9`, `OSC 99` and `OSC 777` notifications of terminal programs are shown. */
    public boolean oscEnabled = false;
    /**
     * The applications whose `OSC` notifications are shown, in lower case, matched against the
     * `OSC 99` application name. An empty list shows the notifications of all applications, and a
     * single `*` also matches all applications.
     */
    @NonNull
    public final List<String> oscAllowApp = new ArrayList<>();
    /** Whether clicking a card sends an `OSC 99` activation report back to the terminal program. */
    public boolean oscReportClick = true;
    /** Whether the icon data that a program sends with `OSC 99 ; p=icon` is shown in the card. */
    public boolean oscIcons = false;

    /** Whether a system notification is shown for the notifications that are received while the app is not visible. */
    public boolean backgroundEnabled = false;
    /** The number of notifications that are kept in the recent notifications list, or 0 to keep none. */
    public int historySize = DEFAULT_HISTORY_SIZE;

    /** @return Whether the cards are shown in the bottom half of the terminal. */
    public boolean isBottomPosition() {
        return POSITION_BOTTOM_END.equals(position) || POSITION_BOTTOM_START.equals(position);
    }

    /** @return Whether the cards are aligned to the right edge of the terminal. */
    public boolean isEndPosition() {
        return POSITION_TOP_END.equals(position) || POSITION_BOTTOM_END.equals(position);
    }

    /** @return Whether the app event {@code eventId} is shown as a card. */
    public boolean isEventEnabled(@NonNull String eventId) {
        return events.contains(eventId.toLowerCase(Locale.ROOT));
    }

    /** @return Whether the `OSC` notifications of the application {@code appName} are shown. */
    public boolean isOscAppAllowed(@Nullable String appName) {
        if (oscAllowApp.isEmpty() || oscAllowApp.contains("*")) return true;
        if (DataUtils.isNullOrEmpty(appName)) return false;
        return oscAllowApp.contains(appName.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Parse the notification configuration from the {@code properties} of the `termux.properties`
     * file. Invalid or out of range values fall back to their defaults instead of throwing, so that
     * a broken configuration never prevents the app from starting.
     */
    @NonNull
    public static NotificationConfig parse(@Nullable Properties properties) {
        NotificationConfig config = new NotificationConfig();
        if (properties == null) properties = new Properties();

        config.enabled = getBoolean(properties, TermuxPropertyConstants.KEY_NOTIFICATION_ENABLED, false);

        String position = getString(properties, TermuxPropertyConstants.KEY_NOTIFICATION_POSITION, POSITION_TOP_END).trim().toLowerCase(Locale.ROOT);
        if (!POSITIONS.contains(position)) {
            Logger.logWarn(LOG_TAG, "Invalid value \"" + position + "\" for the \""
                + TermuxPropertyConstants.KEY_NOTIFICATION_POSITION + "\" property, supported values: " + POSITIONS + ", using \"" + POSITION_TOP_END + "\" instead");
            position = POSITION_TOP_END;
        }
        config.position = position;

        config.maxVisible = getInt(properties, TermuxPropertyConstants.KEY_NOTIFICATION_MAX_VISIBLE,
            DEFAULT_MAX_VISIBLE, MIN_MAX_VISIBLE, MAX_MAX_VISIBLE);
        config.durationMs = getInt(properties, TermuxPropertyConstants.KEY_NOTIFICATION_DURATION,
            DEFAULT_DURATION_MS, MIN_DURATION_MS, MAX_DURATION_MS);
        config.widthDp = getInt(properties, TermuxPropertyConstants.KEY_NOTIFICATION_WIDTH,
            WIDTH_AUTO, MIN_WIDTH_DP, MAX_WIDTH_DP);
        config.cornerRadiusDp = getInt(properties, TermuxPropertyConstants.KEY_NOTIFICATION_CORNER_RADIUS,
            DEFAULT_CORNER_RADIUS_DP, MIN_CORNER_RADIUS_DP, MAX_CORNER_RADIUS_DP);
        config.elevationDp = getInt(properties, TermuxPropertyConstants.KEY_NOTIFICATION_ELEVATION,
            DEFAULT_ELEVATION_DP, MIN_ELEVATION_DP, MAX_ELEVATION_DP);
        config.borderWidthDp = getInt(properties, TermuxPropertyConstants.KEY_NOTIFICATION_BORDER_WIDTH,
            DEFAULT_BORDER_WIDTH_DP, MIN_BORDER_WIDTH_DP, MAX_BORDER_WIDTH_DP);
        config.accentWidthDp = getInt(properties, TermuxPropertyConstants.KEY_NOTIFICATION_ACCENT_WIDTH,
            DEFAULT_ACCENT_WIDTH_DP, MIN_ACCENT_WIDTH_DP, MAX_ACCENT_WIDTH_DP);
        config.monospace = getBoolean(properties, TermuxPropertyConstants.KEY_NOTIFICATION_MONOSPACE, true);
        config.bodyMaxLines = getInt(properties, TermuxPropertyConstants.KEY_NOTIFICATION_BODY_MAX_LINES,
            DEFAULT_BODY_MAX_LINES, MIN_BODY_MAX_LINES, MAX_BODY_MAX_LINES);

        config.backgroundColor = getString(properties, TermuxPropertyConstants.KEY_NOTIFICATION_BACKGROUND_COLOR, "").trim();
        config.foregroundColor = getString(properties, TermuxPropertyConstants.KEY_NOTIFICATION_FOREGROUND_COLOR, "").trim();
        config.accentFailureColor = getString(properties, TermuxPropertyConstants.KEY_NOTIFICATION_ACCENT_FAILURE_COLOR, "").trim();
        config.accentSuccessColor = getString(properties, TermuxPropertyConstants.KEY_NOTIFICATION_ACCENT_SUCCESS_COLOR, "").trim();
        config.accentWarningColor = getString(properties, TermuxPropertyConstants.KEY_NOTIFICATION_ACCENT_WARNING_COLOR, "").trim();
        config.accentInfoColor = getString(properties, TermuxPropertyConstants.KEY_NOTIFICATION_ACCENT_INFO_COLOR, "").trim();

        config.exitCodeEnabled = getBoolean(properties, TermuxPropertyConstants.KEY_NOTIFICATION_EXIT_CODE_ENABLED, true);
        config.exitCodeShowCwd = getBoolean(properties, TermuxPropertyConstants.KEY_NOTIFICATION_EXIT_CODE_SHOW_CWD, true);
        config.exitCodeShowSession = getBoolean(properties, TermuxPropertyConstants.KEY_NOTIFICATION_EXIT_CODE_SHOW_SESSION, true);
        config.exitCodeMinDurationSeconds = getInt(properties, TermuxPropertyConstants.KEY_NOTIFICATION_EXIT_CODE_MIN_DURATION,
            DEFAULT_MIN_DURATION_SECONDS, MIN_MIN_DURATION_SECONDS, MAX_MIN_DURATION_SECONDS);
        config.exitCodeIgnoreCommands.addAll(getList(properties,
            TermuxPropertyConstants.KEY_NOTIFICATION_EXIT_CODE_IGNORE_COMMANDS, DEFAULT_EXIT_CODE_IGNORE_COMMANDS));

        config.eventsEnabled = getBoolean(properties, TermuxPropertyConstants.KEY_NOTIFICATION_EVENTS_ENABLED, false);
        String events = getString(properties, TermuxPropertyConstants.KEY_NOTIFICATION_EVENTS, DEFAULT_EVENTS);
        for (String eventId : getList(events)) {
            if (!EVENT_IDS.contains(eventId)) {
                Logger.logWarn(LOG_TAG, "Ignoring unknown value \"" + eventId + "\" for the \""
                    + TermuxPropertyConstants.KEY_NOTIFICATION_EVENTS + "\" property, supported values: " + EVENT_IDS);
                continue;
            }
            if (!config.events.contains(eventId)) config.events.add(eventId);
        }

        config.oscEnabled = getBoolean(properties, TermuxPropertyConstants.KEY_NOTIFICATION_OSC_ENABLED, false);
        config.oscAllowApp.addAll(getList(properties, TermuxPropertyConstants.KEY_NOTIFICATION_OSC_ALLOW_APP, ""));
        config.oscReportClick = getBoolean(properties, TermuxPropertyConstants.KEY_NOTIFICATION_OSC_REPORT_CLICK, true);
        config.oscIcons = getBoolean(properties, TermuxPropertyConstants.KEY_NOTIFICATION_OSC_ICONS, false);

        config.backgroundEnabled = getBoolean(properties, TermuxPropertyConstants.KEY_NOTIFICATION_BACKGROUND_ENABLED, false);
        config.historySize = getInt(properties, TermuxPropertyConstants.KEY_NOTIFICATION_HISTORY_SIZE,
            DEFAULT_HISTORY_SIZE, MIN_HISTORY_SIZE, MAX_HISTORY_SIZE);

        return config;
    }

    /** @return The comma separated values of the {@code key} property, in lower case and without duplicates. */
    @NonNull
    private static List<String> getList(@NonNull Properties properties, @NonNull String key, @NonNull String defaultValue) {
        String value = properties.getProperty(key);
        return getList(value == null ? defaultValue : value);
    }

    /** @return The comma separated {@code value}, in lower case and without duplicates. */
    @NonNull
    private static List<String> getList(@Nullable String value) {
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
