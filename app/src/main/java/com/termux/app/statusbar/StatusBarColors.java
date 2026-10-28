package com.termux.app.statusbar;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TextStyle;

import java.util.Locale;

/**
 * Resolves the color values of the `status-bar-*` properties, which can either be a literal
 * `#rgb`, `#argb`, `#rrggbb` or `#aarrggbb` color or a reference to a color of the termux color
 * scheme, so that the status bar follows the colors of the terminal set by the `colors.properties`
 * file, like by Termux:Styling.
 *
 * A color scheme reference has the form `theme:<name>`, where {@code <name>} is one of
 * `background`, `foreground`, `cursor` or `color0` to `color255`.
 */
public final class StatusBarColors {

    private static final String LOG_TAG = "StatusBarColors";

    /** The prefix of a value that references a color of the termux color scheme. */
    public static final String THEME_COLOR_PREFIX = "theme:";

    /** The name of the color scheme color that is used for the status bar background by default. */
    public static final String THEME_BACKGROUND = "background";
    /** The name of the color scheme color that is used for the status bar text by default. */
    public static final String THEME_FOREGROUND = "foreground";

    private StatusBarColors() {
    }

    /**
     * Resolve the {@code value} of a color property to a color.
     *
     * @param value The property value, either a `#...` color, a `theme:<name>` reference or empty.
     * @param defaultThemeColorName The color scheme color that is used if the value is empty.
     * @return The resolved color, or {@link Color#WHITE} if the value is invalid.
     */
    public static int resolve(@Nullable String value, @NonNull String defaultThemeColorName) {
        if (DataUtils.isNullOrEmpty(value)) return getThemeColor(defaultThemeColorName);

        value = value.trim();

        if (value.toLowerCase(Locale.ROOT).startsWith(THEME_COLOR_PREFIX))
            return getThemeColor(value.substring(THEME_COLOR_PREFIX.length()).trim());

        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException e) {
            Logger.logError(LOG_TAG, "Invalid color \"" + value + "\", using the \"" + defaultThemeColorName + "\" color of the color scheme instead");
            return getThemeColor(defaultThemeColorName);
        }
    }

    /**
     * @param name The name of the color scheme color, `background`, `foreground`, `cursor` or
     *             `color0` to `color255`.
     * @return The color of the current termux color scheme, or {@link Color#WHITE} if the name is
     *         not known.
     */
    public static int getThemeColor(@Nullable String name) {
        TerminalColorScheme colorScheme = TerminalColors.COLOR_SCHEME;
        if (colorScheme == null || name == null) return Color.WHITE;

        name = name.trim().toLowerCase(Locale.ROOT);

        switch (name) {
            case THEME_BACKGROUND:
            case "bg":
                return colorScheme.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND];
            case THEME_FOREGROUND:
            case "fg":
                return colorScheme.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND];
            case "cursor":
                return colorScheme.mDefaultColors[TextStyle.COLOR_INDEX_CURSOR];
            default:
                break;
        }

        if (name.startsWith("color")) {
            try {
                int index = Integer.parseInt(name.substring("color".length()));
                if (index >= 0 && index < colorScheme.mDefaultColors.length)
                    return colorScheme.mDefaultColors[index];
            } catch (NumberFormatException e) {
                // Fall through to the error below.
            }
        }

        Logger.logError(LOG_TAG, "Unknown color scheme color \"" + name + "\", using the foreground color instead");
        return colorScheme.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND];
    }

    /**
     * @param value The value to check.
     * @return Whether the value is a reference to a color of the termux color scheme, like
     *         `theme:background` or `theme:color4`.
     */
    public static boolean isThemeColorReference(@Nullable String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).startsWith(THEME_COLOR_PREFIX);
    }

    /**
     * @param value The value to check, like `theme:background` or `theme:color4`.
     * @return Whether the value is a valid reference to a color of the termux color scheme.
     */
    public static boolean isValidThemeColorReference(@Nullable String value) {
        if (!isThemeColorReference(value)) return false;

        String name = value.trim().substring(THEME_COLOR_PREFIX.length()).trim().toLowerCase(Locale.ROOT);
        if (THEME_BACKGROUND.equals(name) || "bg".equals(name) || THEME_FOREGROUND.equals(name) || "fg".equals(name)
            || "cursor".equals(name)) return true;

        if (!name.startsWith("color")) return false;

        try {
            int index = Integer.parseInt(name.substring("color".length()));
            TerminalColorScheme colorScheme = TerminalColors.COLOR_SCHEME;
            return index >= 0 && colorScheme != null && index < colorScheme.mDefaultColors.length;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** @return The {@code color} with its alpha replaced by {@code alpha}, which ranges from 0 to 1. */
    public static int withAlpha(int color, float alpha) {
        int alphaValue = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (color & 0x00ffffff) | (alphaValue << 24);
    }

    /** @return The {@code color} blended towards {@code otherColor} by {@code ratio}, which ranges from 0 to 1. */
    public static int blend(int color, int otherColor, float ratio) {
        float inverseRatio = 1f - ratio;
        int alpha = Math.round(Color.alpha(color) * inverseRatio + Color.alpha(otherColor) * ratio);
        int red = Math.round(Color.red(color) * inverseRatio + Color.red(otherColor) * ratio);
        int green = Math.round(Color.green(color) * inverseRatio + Color.green(otherColor) * ratio);
        int blue = Math.round(Color.blue(color) * inverseRatio + Color.blue(otherColor) * ratio);
        return Color.argb(alpha, red, green, blue);
    }

    /** @return Whether the {@code color} is light, so that dark text should be used on top of it. */
    public static boolean isLight(int color) {
        return TerminalColors.getPerceivedBrightnessOfColor(color) > 0.6;
    }

}
