package com.termux.app.widget;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.statusbar.StatusBarColors;
import com.termux.shared.data.DataUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalColorScheme;

/**
 * The colors and the drawables that the dialogs of the fork share, like the terminal context menu and the
 * recent notifications dialog, so that they all look like a tui window with square corners, a 1dp accent
 * border and the colors of the termux color scheme.
 */
public final class TerminalDialogStyles {

    private TerminalDialogStyles() {
    }

    /** @return The background color of a dialog, which is derived from the color scheme if not set. */
    public static int getBackgroundColor(@Nullable String value, int foregroundColor) {
        if (!DataUtils.isNullOrEmpty(value))
            return StatusBarColors.resolve(value, StatusBarColors.THEME_BACKGROUND);

        int backgroundColor = StatusBarColors.getThemeColor(StatusBarColors.THEME_BACKGROUND);
        // Blend the background towards the foreground color, so that the dialog is visible when it would
        // otherwise have the same color as the terminal background behind it.
        return TerminalColorScheme.blendColors(backgroundColor, foregroundColor, 0.10);
    }

    /** @return The accent color of a dialog, which is the `color4` of the color scheme by default. */
    public static int getAccentColor(@Nullable String value, int foregroundColor) {
        int accent = StatusBarColors.resolve(value, "color4");
        // An accent color of a color scheme without a color set for it can be fully transparent, in which
        // case the border would not be visible, so fall back to the foreground color.
        if (Color.alpha(accent) == 0) accent = foregroundColor;
        return accent | 0xFF000000;
    }

    /** @return The background of a dialog, with square corners and a 1dp accent border by default. */
    @NonNull
    public static GradientDrawable createDialogBackground(@NonNull Context context, int backgroundColor,
                                                         int accentColor, int cornerRadiusDp, int borderWidthDp) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, cornerRadiusDp));
        background.setColor(backgroundColor);
        if (borderWidthDp > 0)
            background.setStroke(dp(context, borderWidthDp), accentColor);
        return background;
    }

    /**
     * @return The background of a row of a dialog, which highlights it with the accent color while it is
     *         pressed, instead of a ripple, so that the rows look like part of a tui window.
     */
    @NonNull
    public static StateListDrawable createRowBackground(@NonNull Context context, int accentColor, int cornerRadiusDp) {
        StateListDrawable background = new StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setCornerRadius(dp(context, cornerRadiusDp));
        pressed.setColor(withAlpha(accentColor, 0.30f));

        background.addState(new int[]{android.R.attr.state_pressed}, pressed);
        background.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));

        return background;
    }

    /**
     * @return A text view for a row of a dialog, with the given font size and the monospace font if
     *         {@code monospace} is set.
     */
    @NonNull
    public static TextView createTextView(@NonNull Context context, int textColor, float textSizeSp,
                                         boolean monospace, boolean bold) {
        TextView textView = new TextView(context);
        textView.setTextColor(textColor);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        if (monospace)
            textView.setTypeface(bold ? android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD) : android.graphics.Typeface.MONOSPACE);
        else if (bold)
            textView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return textView;
    }

    /** @return The {@code color} with the {@code alpha} factor applied to its alpha channel. */
    public static int withAlpha(int color, float alpha) {
        int colorAlpha = Math.round(Color.alpha(color) * alpha);
        return (color & 0x00FFFFFF) | (colorAlpha << 24);
    }

    /** @return The {@code dp} value in pixels. */
    public static int dp(@NonNull Context context, float dp) {
        return Math.round(ViewUtils.dpToPx(context, dp));
    }
}
