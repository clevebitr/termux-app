package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The configuration of the status bar, parsed from the `status-bar-*` properties of the
 * `termux.properties` file.
 *
 * Every option can also be set from the settings GUI, which writes the same property keys to the
 * file, so the file and the GUI are two views of the same configuration and can be mixed freely.
 *
 * The global options are read with their own keys, while the options of a segment are read with the
 * `status-bar-segment-<id>-<option>` key pattern, so that new segments can be added without having
 * to define a new property key for each of their options.
 */
public final class StatusBarConfig {

    /** The ids of the segments that are supported, in the order they are shown by default. */
    public static final String SEGMENT_CLOCK = "clock";
    public static final String SEGMENT_BATTERY = "battery";
    public static final String SEGMENT_MEMORY = "memory";
    public static final String SEGMENT_CPU = "cpu";
    public static final String SEGMENT_NET = "net";
    public static final String SEGMENT_IP = "ip";
    public static final String SEGMENT_PROXY = "proxy";

    public static final List<String> SEGMENT_IDS = Collections.unmodifiableList(Arrays.asList(
        SEGMENT_CLOCK, SEGMENT_BATTERY, SEGMENT_MEMORY, SEGMENT_CPU, SEGMENT_NET, SEGMENT_IP, SEGMENT_PROXY));

    /** The value of the `status-bar-position` property to show the status bar above the terminal */
    public static final String POSITION_TOP = "top";
    /** The value of the `status-bar-position` property to show the status bar below the terminal */
    public static final String POSITION_BOTTOM = "bottom";

    /** The value of the `status-bar-align` property to align the segments at the start */
    public static final String ALIGN_START = "start";
    /** The value of the `status-bar-align` property to align the segments at the center */
    public static final String ALIGN_CENTER = "center";
    /** The value of the `status-bar-align` property to align the segments at the end */
    public static final String ALIGN_END = "end";

    /** The value of the `status-bar-segment-<id>-style` property to show the segment as text */
    public static final String STYLE_TEXT = "text";
    /** The value of the `status-bar-segment-<id>-style` property to show a chart of the value history */
    public static final String STYLE_CHART = "chart";
    /** The value of the `status-bar-segment-<id>-style` property to show the text and the chart */
    public static final String STYLE_TEXT_CHART = "text-chart";

    /** The value of the `status-bar-segment-<id>-action` property to not do anything on click */
    public static final String ACTION_NONE = "none";
    /** The value of the `status-bar-segment-<id>-action` property to open the wifi settings */
    public static final String ACTION_WIFI_SETTINGS = "wifi-settings";
    /** The value of the `status-bar-segment-<id>-action` property to open the battery settings */
    public static final String ACTION_BATTERY_SETTINGS = "battery-settings";
    /** The value of the `status-bar-segment-<id>-action` property to open the mobile data settings */
    public static final String ACTION_DATA_SETTINGS = "data-settings";
    /** The value of the `status-bar-segment-<id>-action` property to open the app info of termux */
    public static final String ACTION_APP_SETTINGS = "app-settings";
    /** The value of the `status-bar-segment-<id>-action` property to create a new terminal session */
    public static final String ACTION_NEW_SESSION = "new-session";

    /** The default value of the `status-bar-segments` property, the ip and proxy segments are opt-in. */
    public static final String DEFAULT_SEGMENTS = "clock,battery,memory,cpu,net";

    public static final int DEFAULT_HEIGHT_DP = 24;
    public static final int MIN_HEIGHT_DP = 14;
    public static final int MAX_HEIGHT_DP = 120;

    public static final int DEFAULT_FONT_SIZE_SP = 11;
    public static final int MIN_FONT_SIZE_SP = 5;
    public static final int MAX_FONT_SIZE_SP = 32;

    public static final int DEFAULT_PADDING_HORIZONTAL_DP = 8;
    public static final int MIN_PADDING_HORIZONTAL_DP = 0;
    public static final int MAX_PADDING_HORIZONTAL_DP = 64;

    public static final int DEFAULT_REFRESH_INTERVAL_MS = 2000;
    public static final int MIN_REFRESH_INTERVAL_MS = 250;
    public static final int MAX_REFRESH_INTERVAL_MS = 60000;

    public static final String DEFAULT_SEPARATOR = "  ";

    /** The default format of a segment, which is used if the `...-format` property is not set. */
    private static final Map<String, String> DEFAULT_FORMATS;
    /** The default refresh interval of a segment in milliseconds. */
    private static final Map<String, Integer> DEFAULT_INTERVALS;
    /** The default style of a segment, `text` or `chart`. */
    private static final Map<String, String> DEFAULT_STYLES;

    static {
        Map<String, String> formats = new HashMap<>();
        // The format of the clock segment is a java `SimpleDateFormat` pattern.
        formats.put(SEGMENT_CLOCK, "HH:mm");
        formats.put(SEGMENT_BATTERY, "%p");
        formats.put(SEGMENT_MEMORY, "%u/%t");
        formats.put(SEGMENT_CPU, "%p");
        formats.put(SEGMENT_NET, "\u2193%rx \u2191%tx");
        formats.put(SEGMENT_IP, "%4");
        formats.put(SEGMENT_PROXY, "%p");
        DEFAULT_FORMATS = Collections.unmodifiableMap(formats);

        Map<String, Integer> intervals = new HashMap<>();
        intervals.put(SEGMENT_CLOCK, 1000);
        intervals.put(SEGMENT_BATTERY, 10000);
        intervals.put(SEGMENT_MEMORY, 3000);
        intervals.put(SEGMENT_CPU, 2000);
        intervals.put(SEGMENT_NET, 2000);
        intervals.put(SEGMENT_IP, 5000);
        intervals.put(SEGMENT_PROXY, 5000);
        DEFAULT_INTERVALS = Collections.unmodifiableMap(intervals);

        Map<String, String> styles = new HashMap<>();
        styles.put(SEGMENT_CLOCK, STYLE_TEXT);
        styles.put(SEGMENT_BATTERY, STYLE_TEXT);
        styles.put(SEGMENT_MEMORY, STYLE_TEXT_CHART);
        styles.put(SEGMENT_CPU, STYLE_TEXT_CHART);
        styles.put(SEGMENT_NET, STYLE_TEXT_CHART);
        styles.put(SEGMENT_IP, STYLE_TEXT);
        styles.put(SEGMENT_PROXY, STYLE_TEXT);
        DEFAULT_STYLES = Collections.unmodifiableMap(styles);
    }

    private static final String LOG_TAG = "StatusBarConfig";

    /** Whether the status bar is shown. */
    public boolean enabled;
    /** Whether the status bar is shown below the terminal instead of above it. */
    public boolean positionBottom;
    /** Whether the status bar is drawn over the terminal instead of insetting it. */
    public boolean overlay;
    public int heightDp;
    public int fontSizeSp;
    public int paddingHorizontalDp;
    public String separator;
    public String align;
    public String backgroundColor;
    public String foregroundColor;
    public boolean showIcons;
    /** Whether the icon of a segment is drawn after its text instead of before it. */
    public boolean iconsAtEnd;

    /** The segments that are shown, in the order that they are listed in the `status-bar-segments` property. */
    @NonNull
    public final List<Segment> segments = new ArrayList<>();

    /** The configuration of a single segment of the status bar. */
    public static final class Segment {
        @NonNull public final String id;
        @NonNull public String format;
        @NonNull public String label = "";
        /** The color of the segment, `theme:<name>`, `#aarrggbb` or empty to use the foreground color. */
        @NonNull public String color = "";
        @NonNull public String style;
        @NonNull public String action = ACTION_NONE;
        public int intervalMs;
        /** The value above which the segment is drawn with {@link #warnColor}, or null if not set. */
        @Nullable public Double warnAbove;
        /** The value below which the segment is drawn with {@link #warnColor}, or null if not set. */
        @Nullable public Double warnBelow;
        /** The color of the segment when a warning threshold is reached, or empty for the default. */
        @NonNull public String warnColor = "";

        Segment(@NonNull String id) {
            this.id = id;
            this.format = DEFAULT_FORMATS.containsKey(id) ? DEFAULT_FORMATS.get(id) : "%p";
            this.style = DEFAULT_STYLES.containsKey(id) ? DEFAULT_STYLES.get(id) : STYLE_TEXT;
            this.intervalMs = DEFAULT_INTERVALS.containsKey(id) ? DEFAULT_INTERVALS.get(id) : DEFAULT_REFRESH_INTERVAL_MS;
        }

        /** @return Whether the segment should be drawn with a chart of its value history. */
        public boolean shouldShowChart() {
            return STYLE_CHART.equals(style) || STYLE_TEXT_CHART.equals(style);
        }

        /** @return Whether the segment should be drawn with its text value. */
        public boolean shouldShowText() {
            return !STYLE_CHART.equals(style);
        }

        /** @return The maximum number of samples that the chart of the segment keeps. */
        public int getChartSampleCount() {
            return 32;
        }
    }

    /** @return The default format of the segment {@code id}. */
    @NonNull
    public static String getDefaultFormat(@NonNull String id) {
        String format = DEFAULT_FORMATS.get(id);
        return format != null ? format : "%p";
    }

    /**
     * Parse the status bar configuration from the {@code properties} of the `termux.properties`
     * file. Invalid or out of range values fall back to their defaults instead of throwing, so that
     * a broken configuration never prevents the app from starting.
     */
    @NonNull
    public static StatusBarConfig parse(@Nullable Properties properties) {
        StatusBarConfig config = new StatusBarConfig();
        if (properties == null) properties = new Properties();

        config.enabled = getBoolean(properties, TermuxPropertyConstants.KEY_STATUS_BAR_ENABLED, false);
        config.positionBottom = POSITION_BOTTOM.equalsIgnoreCase(
            getString(properties, TermuxPropertyConstants.KEY_STATUS_BAR_POSITION, POSITION_TOP).trim());
        config.overlay = getBoolean(properties, TermuxPropertyConstants.KEY_STATUS_BAR_OVERLAY, false);
        config.heightDp = getInt(properties, TermuxPropertyConstants.KEY_STATUS_BAR_HEIGHT,
            DEFAULT_HEIGHT_DP, MIN_HEIGHT_DP, MAX_HEIGHT_DP);
        config.fontSizeSp = getInt(properties, TermuxPropertyConstants.KEY_STATUS_BAR_FONT_SIZE,
            DEFAULT_FONT_SIZE_SP, MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP);
        config.paddingHorizontalDp = getInt(properties, TermuxPropertyConstants.KEY_STATUS_BAR_PADDING_HORIZONTAL,
            DEFAULT_PADDING_HORIZONTAL_DP, MIN_PADDING_HORIZONTAL_DP, MAX_PADDING_HORIZONTAL_DP);

        String separator = getString(properties, TermuxPropertyConstants.KEY_STATUS_BAR_SEPARATOR, DEFAULT_SEPARATOR);
        // A `\t`, `\n` or `\\` in the property value is stored escaped in the properties file.
        config.separator = unescape(separator);

        String align = getString(properties, TermuxPropertyConstants.KEY_STATUS_BAR_ALIGN, ALIGN_START).trim();
        if (!ALIGN_CENTER.equals(align) && !ALIGN_END.equals(align)) align = ALIGN_START;
        config.align = align;

        config.backgroundColor = getString(properties, TermuxPropertyConstants.KEY_STATUS_BAR_BACKGROUND_COLOR, "").trim();
        config.foregroundColor = getString(properties, TermuxPropertyConstants.KEY_STATUS_BAR_FOREGROUND_COLOR, "").trim();
        config.showIcons = getBoolean(properties, TermuxPropertyConstants.KEY_STATUS_BAR_SHOW_ICONS, true);
        config.iconsAtEnd = "end".equalsIgnoreCase(
            getString(properties, TermuxPropertyConstants.KEY_STATUS_BAR_ICONS_POSITION, "start").trim());

        String enabledSegments = getString(properties, TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENTS, DEFAULT_SEGMENTS);
        for (String segmentId : enabledSegments.split(",")) {
            segmentId = segmentId.trim().toLowerCase();
            if (segmentId.isEmpty()) continue;
            if (!SEGMENT_IDS.contains(segmentId)) {
                Logger.logWarn(LOG_TAG, "Ignoring unknown status bar segment \"" + segmentId + "\", supported segments: " + SEGMENT_IDS);
                continue;
            }
            if (hasSegment(config.segments, segmentId)) continue;

            Segment segment = parseSegment(properties, segmentId);
            if (segment != null) config.segments.add(segment);
        }

        return config;
    }

    /** @return The segment configuration for {@code segmentId}, or {@code null} if it is disabled. */
    @Nullable
    private static Segment parseSegment(@NonNull Properties properties, @NonNull String segmentId) {
        String keyPrefix = TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_PREFIX + segmentId + "-";

        if (!getBoolean(properties, keyPrefix + TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_SUFFIX_ENABLED, true))
            return null;

        Segment segment = new Segment(segmentId);

        String format = getString(properties, keyPrefix + TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_SUFFIX_FORMAT, null);
        if (format != null && !format.isEmpty()) segment.format = format;

        String label = getString(properties, keyPrefix + TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_SUFFIX_LABEL, null);
        if (label != null) segment.label = label;

        String color = getString(properties, keyPrefix + TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_SUFFIX_COLOR, null);
        if (color != null) segment.color = color.trim();

        String style = getString(properties, keyPrefix + TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_SUFFIX_STYLE, null);
        if (style != null) {
            style = style.trim();
            if (STYLE_CHART.equals(style) || STYLE_TEXT_CHART.equals(style) || STYLE_TEXT.equals(style))
                segment.style = style;
            else
                Logger.logWarn(LOG_TAG, "Invalid style \"" + style + "\" for the \"" + segmentId + "\" status bar segment, using \"" + segment.style + "\" instead");
        }

        String action = getString(properties, keyPrefix + TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_SUFFIX_ACTION, null);
        if (action != null) segment.action = action.trim();

        String interval = getString(properties, keyPrefix + TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_SUFFIX_INTERVAL, null);
        if (interval != null)
            segment.intervalMs = getInt(properties, keyPrefix + TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_SUFFIX_INTERVAL,
                segment.intervalMs, MIN_REFRESH_INTERVAL_MS, MAX_REFRESH_INTERVAL_MS);

        segment.warnAbove = getDouble(properties, keyPrefix + TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_SUFFIX_WARN_ABOVE);
        segment.warnBelow = getDouble(properties, keyPrefix + TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_SUFFIX_WARN_BELOW);

        String warnColor = getString(properties, keyPrefix + TermuxPropertyConstants.KEY_STATUS_BAR_SEGMENT_SUFFIX_WARN_COLOR, null);
        if (warnColor != null) segment.warnColor = warnColor.trim();

        return segment;
    }

    private static boolean hasSegment(@NonNull List<Segment> segments, @NonNull String id) {
        for (Segment segment : segments) {
            if (segment.id.equals(id)) return true;
        }
        return false;
    }

    @Nullable
    private static String getString(@NonNull Properties properties, @NonNull String key, @Nullable String defaultValue) {
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

    @Nullable
    private static Double getDouble(@NonNull Properties properties, @NonNull String key) {
        String value = properties.getProperty(key);
        if (DataUtils.isNullOrEmpty(value)) return null;

        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            Logger.logWarn(LOG_TAG, "Invalid number \"" + value + "\" for the \"" + key + "\" property, ignoring it");
            return null;
        }
    }

    /** Replace the escape sequences that the properties file format uses with the actual characters. */
    @NonNull
    private static String unescape(@NonNull String value) {
        if (value.indexOf('\\') < 0) return value;

        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case 't': builder.append('\t'); break;
                    case 'n': builder.append('\n'); break;
                    case '\\': builder.append('\\'); break;
                    default: builder.append('\\').append(next); break;
                }
            } else {
                builder.append(c);
            }
        }

        return builder.toString();
    }

    /** @return A map of the ids of the segments that are shown to their configuration. */
    @NonNull
    public Map<String, Segment> getSegmentsById() {
        Map<String, Segment> segmentsById = new LinkedHashMap<>();
        for (Segment segment : segments) segmentsById.put(segment.id, segment);
        return segmentsById;
    }

}
