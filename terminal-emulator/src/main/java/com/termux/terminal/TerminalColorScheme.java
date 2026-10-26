package com.termux.terminal;

import java.util.Map;
import java.util.Properties;

/**
 * Color scheme for a terminal with default colors, which may be overridden (and then reset) from the shell using
 * Operating System Control (OSC) sequences.
 *
 * @see TerminalColors
 */
public final class TerminalColorScheme {

    /** The key for the text color of the extra keys view buttons. */
    public static final String KEY_EXTRA_KEYS_FOREGROUND = "extra-keys-foreground";
    /** The key for the background color of the extra keys view buttons. */
    public static final String KEY_EXTRA_KEYS_BACKGROUND = "extra-keys-background";
    /** The key for the text color of the extra keys view buttons when they are active. */
    public static final String KEY_EXTRA_KEYS_ACTIVE_FOREGROUND = "extra-keys-active-foreground";
    /** The key for the background color of the extra keys view buttons when they are active. */
    public static final String KEY_EXTRA_KEYS_ACTIVE_BACKGROUND = "extra-keys-active-background";
    /** The key for the background color of the tiles that are drawn behind the extra keys view
     * buttons when the `extra-keys-tile-background` property is enabled. */
    public static final String KEY_EXTRA_KEYS_TILE_BACKGROUND = "extra-keys-tile-background-color";
    /** The key for the background color of the tiles that are drawn behind the extra keys view
     * buttons when they are active. */
    public static final String KEY_EXTRA_KEYS_TILE_ACTIVE_BACKGROUND = "extra-keys-tile-active-background-color";

    /**
     * The ratio of the foreground color that is blended into the background color to get the
     * default active background color of the extra keys view buttons, if the
     * {@link #KEY_EXTRA_KEYS_ACTIVE_BACKGROUND} key is not defined.
     */
    public static final double EXTRA_KEYS_ACTIVE_BACKGROUND_COLOR_BLEND_RATIO = 0.25;

    /**
     * The ratio of the foreground color that is blended into the background color to get the
     * default background color of the tiles that are drawn behind the extra keys view buttons, if
     * the {@link #KEY_EXTRA_KEYS_TILE_BACKGROUND} key is not defined. It is intentionally lower
     * than {@link #EXTRA_KEYS_ACTIVE_BACKGROUND_COLOR_BLEND_RATIO} so that the tiles are only
     * slightly visible and the terminal colors are not drowned out.
     */
    public static final double EXTRA_KEYS_TILE_BACKGROUND_COLOR_BLEND_RATIO = 0.08;

    /** http://upload.wikimedia.org/wikipedia/en/1/15/Xterm_256color_chart.svg, but with blue color brighter. */
    private static final int[] DEFAULT_COLORSCHEME = {
        // 16 original colors. First 8 are dim.
        0xff000000, // black
        0xffcd0000, // dim red
        0xff00cd00, // dim green
        0xffcdcd00, // dim yellow
        0xff6495ed, // dim blue
        0xffcd00cd, // dim magenta
        0xff00cdcd, // dim cyan
        0xffe5e5e5, // dim white
        // Second 8 are bright:
        0xff7f7f7f, // medium grey
        0xffff0000, // bright red
        0xff00ff00, // bright green
        0xffffff00, // bright yellow
        0xff5c5cff, // light blue
        0xffff00ff, // bright magenta
        0xff00ffff, // bright cyan
        0xffffffff, // bright white

        // 216 color cube, six shades of each color:
        0xff000000, 0xff00005f, 0xff000087, 0xff0000af, 0xff0000d7, 0xff0000ff, 0xff005f00, 0xff005f5f, 0xff005f87, 0xff005faf, 0xff005fd7, 0xff005fff,
        0xff008700, 0xff00875f, 0xff008787, 0xff0087af, 0xff0087d7, 0xff0087ff, 0xff00af00, 0xff00af5f, 0xff00af87, 0xff00afaf, 0xff00afd7, 0xff00afff,
        0xff00d700, 0xff00d75f, 0xff00d787, 0xff00d7af, 0xff00d7d7, 0xff00d7ff, 0xff00ff00, 0xff00ff5f, 0xff00ff87, 0xff00ffaf, 0xff00ffd7, 0xff00ffff,
        0xff5f0000, 0xff5f005f, 0xff5f0087, 0xff5f00af, 0xff5f00d7, 0xff5f00ff, 0xff5f5f00, 0xff5f5f5f, 0xff5f5f87, 0xff5f5faf, 0xff5f5fd7, 0xff5f5fff,
        0xff5f8700, 0xff5f875f, 0xff5f8787, 0xff5f87af, 0xff5f87d7, 0xff5f87ff, 0xff5faf00, 0xff5faf5f, 0xff5faf87, 0xff5fafaf, 0xff5fafd7, 0xff5fafff,
        0xff5fd700, 0xff5fd75f, 0xff5fd787, 0xff5fd7af, 0xff5fd7d7, 0xff5fd7ff, 0xff5fff00, 0xff5fff5f, 0xff5fff87, 0xff5fffaf, 0xff5fffd7, 0xff5fffff,
        0xff870000, 0xff87005f, 0xff870087, 0xff8700af, 0xff8700d7, 0xff8700ff, 0xff875f00, 0xff875f5f, 0xff875f87, 0xff875faf, 0xff875fd7, 0xff875fff,
        0xff878700, 0xff87875f, 0xff878787, 0xff8787af, 0xff8787d7, 0xff8787ff, 0xff87af00, 0xff87af5f, 0xff87af87, 0xff87afaf, 0xff87afd7, 0xff87afff,
        0xff87d700, 0xff87d75f, 0xff87d787, 0xff87d7af, 0xff87d7d7, 0xff87d7ff, 0xff87ff00, 0xff87ff5f, 0xff87ff87, 0xff87ffaf, 0xff87ffd7, 0xff87ffff,
        0xffaf0000, 0xffaf005f, 0xffaf0087, 0xffaf00af, 0xffaf00d7, 0xffaf00ff, 0xffaf5f00, 0xffaf5f5f, 0xffaf5f87, 0xffaf5faf, 0xffaf5fd7, 0xffaf5fff,
        0xffaf8700, 0xffaf875f, 0xffaf8787, 0xffaf87af, 0xffaf87d7, 0xffaf87ff, 0xffafaf00, 0xffafaf5f, 0xffafaf87, 0xffafafaf, 0xffafafd7, 0xffafafff,
        0xffafd700, 0xffafd75f, 0xffafd787, 0xffafd7af, 0xffafd7d7, 0xffafd7ff, 0xffafff00, 0xffafff5f, 0xffafff87, 0xffafffaf, 0xffafffd7, 0xffafffff,
        0xffd70000, 0xffd7005f, 0xffd70087, 0xffd700af, 0xffd700d7, 0xffd700ff, 0xffd75f00, 0xffd75f5f, 0xffd75f87, 0xffd75faf, 0xffd75fd7, 0xffd75fff,
        0xffd78700, 0xffd7875f, 0xffd78787, 0xffd787af, 0xffd787d7, 0xffd787ff, 0xffd7af00, 0xffd7af5f, 0xffd7af87, 0xffd7afaf, 0xffd7afd7, 0xffd7afff,
        0xffd7d700, 0xffd7d75f, 0xffd7d787, 0xffd7d7af, 0xffd7d7d7, 0xffd7d7ff, 0xffd7ff00, 0xffd7ff5f, 0xffd7ff87, 0xffd7ffaf, 0xffd7ffd7, 0xffd7ffff,
        0xffff0000, 0xffff005f, 0xffff0087, 0xffff00af, 0xffff00d7, 0xffff00ff, 0xffff5f00, 0xffff5f5f, 0xffff5f87, 0xffff5faf, 0xffff5fd7, 0xffff5fff,
        0xffff8700, 0xffff875f, 0xffff8787, 0xffff87af, 0xffff87d7, 0xffff87ff, 0xffffaf00, 0xffffaf5f, 0xffffaf87, 0xffffafaf, 0xffffafd7, 0xffffafff,
        0xffffd700, 0xffffd75f, 0xffffd787, 0xffffd7af, 0xffffd7d7, 0xffffd7ff, 0xffffff00, 0xffffff5f, 0xffffff87, 0xffffffaf, 0xffffffd7, 0xffffffff,

        // 24 grey scale ramp:
        0xff080808, 0xff121212, 0xff1c1c1c, 0xff262626, 0xff303030, 0xff3a3a3a, 0xff444444, 0xff4e4e4e, 0xff585858, 0xff626262, 0xff6c6c6c, 0xff767676,
        0xff808080, 0xff8a8a8a, 0xff949494, 0xff9e9e9e, 0xffa8a8a8, 0xffb2b2b2, 0xffbcbcbc, 0xffc6c6c6, 0xffd0d0d0, 0xffdadada, 0xffe4e4e4, 0xffeeeeee,

        // COLOR_INDEX_DEFAULT_FOREGROUND, COLOR_INDEX_DEFAULT_BACKGROUND and COLOR_INDEX_DEFAULT_CURSOR:
        0xffffffff, 0xff000000, 0xffffffff};

    public final int[] mDefaultColors = new int[TextStyle.NUM_INDEXED_COLORS];

    /**
     * The text color for the extra keys view buttons. A value of {@code 0} means that the color is
     * not defined, in which case it defaults to {@link TextStyle#COLOR_INDEX_FOREGROUND}.
     */
    private int mExtraKeysForegroundColor;
    /**
     * The background color for the extra keys view buttons. A value of {@code 0} means that the
     * color is not defined, in which case it defaults to {@link TextStyle#COLOR_INDEX_BACKGROUND}.
     */
    private int mExtraKeysBackgroundColor;
    /**
     * The text color for the extra keys view buttons when they are active. A value of {@code 0}
     * means that the color is not defined, in which case it defaults to
     * {@link TextStyle#COLOR_INDEX_CURSOR}.
     */
    private int mExtraKeysActiveForegroundColor;
    /**
     * The background color for the extra keys view buttons when they are active. A value of
     * {@code 0} means that the color is not defined, in which case it defaults to the
     * {@link #mDefaultColors} foreground color blended into the background color with the
     * {@link #EXTRA_KEYS_ACTIVE_BACKGROUND_COLOR_BLEND_RATIO} ratio.
     */
    private int mExtraKeysActiveBackgroundColor;
    /**
     * The background color of the tiles drawn behind the extra keys view buttons. A value of
     * {@code 0} means that the color is not defined, in which case it defaults to the
     * {@link #mDefaultColors} foreground color blended into the background color with the
     * {@link #EXTRA_KEYS_TILE_BACKGROUND_COLOR_BLEND_RATIO} ratio.
     */
    private int mExtraKeysTileBackgroundColor;
    /**
     * The background color of the tiles drawn behind the extra keys view buttons when they are
     * active. A value of {@code 0} means that the color is not defined, in which case it defaults to
     * the foreground color blended into the tile background color with the
     * {@link #EXTRA_KEYS_ACTIVE_BACKGROUND_COLOR_BLEND_RATIO} ratio.
     */
    private int mExtraKeysTileActiveBackgroundColor;

    public TerminalColorScheme() {
        reset();
    }

    private void reset() {
        System.arraycopy(DEFAULT_COLORSCHEME, 0, mDefaultColors, 0, TextStyle.NUM_INDEXED_COLORS);
        mExtraKeysForegroundColor = 0;
        mExtraKeysBackgroundColor = 0;
        mExtraKeysActiveForegroundColor = 0;
        mExtraKeysActiveBackgroundColor = 0;
        mExtraKeysTileBackgroundColor = 0;
        mExtraKeysTileActiveBackgroundColor = 0;
    }

    public void updateWith(Properties props) {
        reset();
        boolean cursorPropExists = false;
        for (Map.Entry<Object, Object> entries : props.entrySet()) {
            String key = (String) entries.getKey();
            String value = (String) entries.getValue();
            int colorIndex;

            // The extra keys view colors are optional and are derived from the other colors if not
            // defined, so that extra keys view follows the color scheme set by Termux:Styling.
            if (key.equals(KEY_EXTRA_KEYS_FOREGROUND)) {
                mExtraKeysForegroundColor = getColorValueForProperty(key, value);
                continue;
            } else if (key.equals(KEY_EXTRA_KEYS_BACKGROUND)) {
                mExtraKeysBackgroundColor = getColorValueForProperty(key, value);
                continue;
            } else if (key.equals(KEY_EXTRA_KEYS_ACTIVE_FOREGROUND)) {
                mExtraKeysActiveForegroundColor = getColorValueForProperty(key, value);
                continue;
            } else if (key.equals(KEY_EXTRA_KEYS_ACTIVE_BACKGROUND)) {
                mExtraKeysActiveBackgroundColor = getColorValueForProperty(key, value);
                continue;
            } else if (key.equals(KEY_EXTRA_KEYS_TILE_BACKGROUND)) {
                mExtraKeysTileBackgroundColor = getColorValueForProperty(key, value);
                continue;
            } else if (key.equals(KEY_EXTRA_KEYS_TILE_ACTIVE_BACKGROUND)) {
                mExtraKeysTileActiveBackgroundColor = getColorValueForProperty(key, value);
                continue;
            }

            if (key.equals("foreground")) {
                colorIndex = TextStyle.COLOR_INDEX_FOREGROUND;
            } else if (key.equals("background")) {
                colorIndex = TextStyle.COLOR_INDEX_BACKGROUND;
            } else if (key.equals("cursor")) {
                colorIndex = TextStyle.COLOR_INDEX_CURSOR;
                cursorPropExists = true;
            } else if (key.startsWith("color")) {
                try {
                    colorIndex = Integer.parseInt(key.substring(5));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid property: '" + key + "'");
                }
            } else {
                throw new IllegalArgumentException("Invalid property: '" + key + "'");
            }

            int colorValue = TerminalColors.parse(value);
            if (colorValue == 0)
                throw new IllegalArgumentException("Property '" + key + "' has invalid color: '" + value + "'");

            mDefaultColors[colorIndex] = colorValue;
        }

        if (!cursorPropExists)
            setCursorColorForBackground();

        setExtraKeysColorsForBackground();
    }

    /** Get the color value for a property or throw an {@link IllegalArgumentException} if it is invalid. */
    private static int getColorValueForProperty(String key, String value) {
        int colorValue = TerminalColors.parse(value);
        if (colorValue == 0)
            throw new IllegalArgumentException("Property '" + key + "' has invalid color: '" + value + "'");
        return colorValue;
    }

    /**
     * Set the extra keys view colors that are not defined in the `colors.properties` file based on
     * the other colors of the color scheme, so that the extra keys view matches the terminal colors.
     */
    private void setExtraKeysColorsForBackground() {
        if (mExtraKeysForegroundColor == 0)
            mExtraKeysForegroundColor = mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND];
        if (mExtraKeysBackgroundColor == 0)
            mExtraKeysBackgroundColor = mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND];
        if (mExtraKeysActiveForegroundColor == 0)
            mExtraKeysActiveForegroundColor = mDefaultColors[TextStyle.COLOR_INDEX_CURSOR];
        if (mExtraKeysActiveBackgroundColor == 0)
            mExtraKeysActiveBackgroundColor = blendColors(mExtraKeysBackgroundColor, mExtraKeysForegroundColor,
                EXTRA_KEYS_ACTIVE_BACKGROUND_COLOR_BLEND_RATIO);
        if (mExtraKeysTileBackgroundColor == 0)
            mExtraKeysTileBackgroundColor = blendColors(mExtraKeysBackgroundColor, mExtraKeysForegroundColor,
                EXTRA_KEYS_TILE_BACKGROUND_COLOR_BLEND_RATIO);
        if (mExtraKeysTileActiveBackgroundColor == 0)
            mExtraKeysTileActiveBackgroundColor = blendColors(mExtraKeysTileBackgroundColor, mExtraKeysForegroundColor,
                EXTRA_KEYS_ACTIVE_BACKGROUND_COLOR_BLEND_RATIO);
    }

    /** Get the text color for the extra keys view buttons. */
    public int getExtraKeysForegroundColor() {
        return mExtraKeysForegroundColor;
    }

    /** Get the background color for the extra keys view buttons. */
    public int getExtraKeysBackgroundColor() {
        return mExtraKeysBackgroundColor;
    }

    /** Get the text color for the extra keys view buttons when they are active. */
    public int getExtraKeysActiveForegroundColor() {
        return mExtraKeysActiveForegroundColor;
    }

    /** Get the background color for the extra keys view buttons when they are active. */
    public int getExtraKeysActiveBackgroundColor() {
        return mExtraKeysActiveBackgroundColor;
    }

    /** Get the background color of the tiles drawn behind the extra keys view buttons. */
    public int getExtraKeysTileBackgroundColor() {
        return mExtraKeysTileBackgroundColor;
    }

    /** Get the background color of the tiles drawn behind the extra keys view buttons when they
     * are active. */
    public int getExtraKeysTileActiveBackgroundColor() {
        return mExtraKeysTileActiveBackgroundColor;
    }

    /**
     * Blend the {@code foregroundColor} into the {@code backgroundColor} with the given {@code ratio}.
     *
     * @param backgroundColor The color to blend into, its alpha is ignored and the returned color
     *                        is fully opaque.
     * @param foregroundColor The color to blend, its alpha is ignored.
     * @param ratio The ratio of the foreground color, must be between {@code 0} and {@code 1}.
     * @return Returns the blended color.
     */
    public static int blendColors(int backgroundColor, int foregroundColor, double ratio) {
        int backgroundRed = (backgroundColor >> 16) & 0xFF;
        int backgroundGreen = (backgroundColor >> 8) & 0xFF;
        int backgroundBlue = backgroundColor & 0xFF;

        int foregroundRed = (foregroundColor >> 16) & 0xFF;
        int foregroundGreen = (foregroundColor >> 8) & 0xFF;
        int foregroundBlue = foregroundColor & 0xFF;

        int red = (int) (backgroundRed + (foregroundRed - backgroundRed) * ratio);
        int green = (int) (backgroundGreen + (foregroundGreen - backgroundGreen) * ratio);
        int blue = (int) (backgroundBlue + (foregroundBlue - backgroundBlue) * ratio);

        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /**
     * If the "cursor" color is not set by user, we need to decide on the appropriate color that will
     * be visible on the current terminal background. White will not be visible on light backgrounds
     * and black won't be visible on dark backgrounds. So we find the perceived brightness of the
     * background color and if its below the threshold (too dark), we use white cursor and if its
     * above (too bright), we use black cursor.
     */
    public void setCursorColorForBackground() {
        int backgroundColor = mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND];
        int brightness = TerminalColors.getPerceivedBrightnessOfColor(backgroundColor);
        if (brightness > 0) {
            if (brightness < 130)
                mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = 0xffffffff;
            else
                mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = 0xff000000;
        }
    }

}
