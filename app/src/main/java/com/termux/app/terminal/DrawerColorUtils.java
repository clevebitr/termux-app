package com.termux.app.terminal;

import androidx.annotation.NonNull;

/**
 * The helpers that derive the colors of the sessions drawer from the colors of the terminal, so that the
 * drawer looks like the terminal it belongs to instead of having its own hardcoded black and white.
 *
 * The functions are pure, so that the alpha and the blending of the colors can be tested without an
 * activity.
 */
public final class DrawerColorUtils {

    private DrawerColorUtils() {
    }

    /** @return {@code color} with its alpha replaced by {@code alpha}, which must be between 0 and 255. */
    public static int withAlpha(int color, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (clampedAlpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * @return The color that is {@code ratio} of the way from {@code base} to {@code accent}, where the
     *         ratio is clamped to the range from 0 to 1.
     *
     * A small ratio is used to tint a background with the accent color, like for the row of the session
     * that is shown in the terminal, which keeps the text readable on it.
     */
    public static int blend(int base, int accent, float ratio) {
        float clampedRatio = Math.max(0f, Math.min(1f, ratio));

        int baseAlpha = (base >>> 24) & 0xFF;
        int accentAlpha = (accent >>> 24) & 0xFF;
        int alpha = Math.round(baseAlpha + (accentAlpha - baseAlpha) * clampedRatio);

        return (alpha << 24)
            | (blendComponent(base, accent, clampedRatio, 16))
            | (blendComponent(base, accent, clampedRatio, 8))
            | blendComponent(base, accent, clampedRatio, 0);
    }

    private static int blendComponent(int base, int accent, float ratio, int shift) {
        int baseComponent = (base >> shift) & 0xFF;
        int accentComponent = (accent >> shift) & 0xFF;
        return Math.round(baseComponent + (accentComponent - baseComponent) * ratio) << shift;
    }

    /**
     * @return Whether the text of the drawer must be dark to be readable on {@code backgroundColor}.
     *
     * The perceived brightness is used instead of the luminance of the color, since a saturated color like
     * a bright green would otherwise be treated as dark.
     */
    public static boolean isLightBackground(int backgroundColor) {
        int red = (backgroundColor >> 16) & 0xFF;
        int green = (backgroundColor >> 8) & 0xFF;
        int blue = backgroundColor & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 > 128;
    }

    /** @return A color that can be used as the text of the drawer on {@code backgroundColor}. */
    public static int contrastingTextColor(int backgroundColor) {
        return isLightBackground(backgroundColor) ? 0xFF212121 : 0xFFEEEEEE;
    }

    /**
     * @return {@code accent} if it can be told apart from {@code backgroundColor}, or the contrasting text
     *         color when it cannot, like when the color scheme sets both to the same value.
     */
    public static int visibleAccentColor(int accent, int backgroundColor) {
        return accent == backgroundColor ? contrastingTextColor(backgroundColor) : accent;
    }

    /** @return A human readable description of {@code color}, like {@code #ff00ff00}, for the logs. */
    @NonNull
    public static String toHexString(int color) {
        return String.format("#%08x", color);
    }
}
