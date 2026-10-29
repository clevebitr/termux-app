package com.termux.app.properties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The helpers that the fork uses to read its own properties from the `termux.properties` file, like the
 * `status-bar-*`, `notification-*` and `terminal-menu-*` keys.
 *
 * Those keys are not part of the {@code TERMUX_APP_PROPERTIES_LIST} that the shared library converts to
 * internal values, and {@code getProperties(true)} only returns the keys of that list, since
 * {@code SharedProperties.loadPropertiesFromDisk()} drops every other key. So the properties must be read
 * from the disk directly, otherwise the configuration of the fork always gets the default values and
 * never shows up. Reading is fine, since the configuration is only reloaded when the activity is created
 * or when the settings change.
 *
 * All the getters fall back to the default value for an invalid or out of range value instead of throwing,
 * so that a broken configuration never prevents the app from starting.
 */
public final class ForkProperties {

    private ForkProperties() {
    }

    /**
     * @return The properties of the `termux.properties` file, read from the disk, or {@code null} if the
     *         properties of the app are not loaded yet.
     */
    @Nullable
    public static Properties readTermuxProperties() {
        TermuxAppSharedProperties sharedProperties = TermuxAppSharedProperties.getProperties();
        return sharedProperties != null ? sharedProperties.getProperties(false) : null;
    }

    /** @return The value of the {@code key} property, or {@code defaultValue} if it is not set. */
    @Nullable
    public static String getString(@NonNull Properties properties, @NonNull String key, @Nullable String defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : value;
    }

    /** @return The value of the {@code key} property as a boolean, or {@code defaultValue}. */
    public static boolean getBoolean(@NonNull Properties properties, @NonNull String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (DataUtils.isNullOrEmpty(value)) return defaultValue;
        return Boolean.parseBoolean(value.trim());
    }

    /**
     * @return The value of the {@code key} property as an integer, or {@code defaultValue} if it is not set
     *         or out of the range [{@code min}, {@code max}].
     *
     * @param logTag The tag that the warning about an out of range value is logged with, which is the tag
     *               of the caller so that the log says which configuration is broken.
     */
    public static int getInt(@NonNull String logTag, @NonNull Properties properties, @NonNull String key,
                             int defaultValue, int min, int max) {
        String value = properties.getProperty(key);
        if (DataUtils.isNullOrEmpty(value)) return defaultValue;

        int intValue = DataUtils.getIntFromString(value, defaultValue);
        if (intValue < min || intValue > max) {
            Logger.logWarn(logTag, "The value \"" + value + "\" for the \"" + key + "\" property is out of range ["
                + min + ", " + max + "], using default value \"" + defaultValue + "\"");
            return defaultValue;
        }

        return intValue;
    }

    /** @return The value of the {@code key} property as a double, or {@code null} if it is not a number. */
    @Nullable
    public static Double getDouble(@NonNull Properties properties, @NonNull String key) {
        String value = properties.getProperty(key);
        if (DataUtils.isNullOrEmpty(value)) return null;

        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return The comma separated values of the {@code key} property, in lower case and without duplicates,
     *         or the values of {@code defaultValue} if the property is not set.
     */
    @NonNull
    public static List<String> getList(@NonNull Properties properties, @NonNull String key, @Nullable String defaultValue) {
        String value = properties.getProperty(key);
        return parseList(value == null ? defaultValue : value);
    }

    /**
     * @return The comma separated {@code value} as a list, in lower case, without duplicates and without
     *         the empty values.
     */
    @NonNull
    public static List<String> parseList(@Nullable String value) {
        List<String> list = new ArrayList<>();
        if (DataUtils.isNullOrEmpty(value)) return list;

        for (String item : value.split(",")) {
            item = item.trim().toLowerCase(Locale.ROOT);
            if (item.isEmpty() || list.contains(item)) continue;
            list.add(item);
        }

        return list;
    }
}
