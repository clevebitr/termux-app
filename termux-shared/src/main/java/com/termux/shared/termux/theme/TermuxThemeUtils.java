package com.termux.shared.termux.theme;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.theme.NightMode;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TextStyle;

public class TermuxThemeUtils {

    /**
     * The minimum perceived brightness of the color scheme background color for it to be considered
     * a light background. Same threshold as the one used by {@code TerminalColorScheme} for the
     * cursor color.
     */
    public static final int LIGHT_BACKGROUND_BRIGHTNESS_THRESHOLD = 130;

    /** Get the {@link TermuxPropertyConstants#KEY_NIGHT_MODE} value from the properties file on disk
     * and set it to app wide night mode value. */
    public static void setAppNightMode(@NonNull Context context) {
        NightMode.setAppNightMode(resolveNightMode(TermuxSharedProperties.getNightMode(context)).getName());
    }

    /** Set name as app wide night mode value. */
    public static void setAppNightMode(@Nullable String name) {
        NightMode.setAppNightMode(resolveNightMode(name).getName());
    }

    /**
     * Resolve the night mode defined by {@code name} to an actual night mode, so that
     * {@link NightMode#COLOR_SCHEME} is resolved to {@link NightMode#TRUE} or {@link NightMode#FALSE}
     * depending on the perceived brightness of the termux color scheme background color, so that the
     * app theme follows the color scheme set by Termux:Styling in the `colors.properties` file.
     *
     * @param name The name of the night mode, like `system` or `color-scheme`.
     * @return Returns the resolved {@link NightMode}, which is {@link NightMode#SYSTEM} if
     *         {@code name} is {@code null} or invalid.
     */
    @NonNull
    public static NightMode resolveNightMode(@Nullable String name) {
        NightMode nightMode = NightMode.modeOf(name, NightMode.SYSTEM);
        if (nightMode != NightMode.COLOR_SCHEME) return nightMode;

        return isColorSchemeLight() ? NightMode.FALSE : NightMode.TRUE;
    }

    /** Returns whether the termux color scheme background color is perceived as light. */
    public static boolean isColorSchemeLight() {
        return isLightBackground(TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND]);
    }

    /** Returns whether the {@code color} is perceived as light, based on its brightness. */
    public static boolean isLightBackground(int color) {
        return TerminalColors.getPerceivedBrightnessOfColor(color) >= LIGHT_BACKGROUND_BRIGHTNESS_THRESHOLD;
    }

}
