package com.termux.app.terminal.menu;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.app.statusbar.StatusBarColors;
import com.termux.shared.data.DataUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalColorScheme;

import java.util.List;

/**
 * A dialog that shows the actions of the terminal context menu, instead of the system context menu,
 * so that it can be styled like a tui window: square corners, a 1dp accent border, the monospace font
 * of the terminal and the colors of the termux color scheme.
 *
 * The dialog is shown by {@link com.termux.app.TermuxActivity#showTerminalMenu()} when the user clicks
 * "more" in the floating text selection toolbar, clicks the secondary mouse button, presses the menu
 * key or uses the `SEARCH`/`SPLIT_*` extra keys, and its items and their order come from the
 * `terminal-menu-items` property.
 */
public final class TerminalMenuDialog {

    /** The listener that is called when an item of the dialog is clicked. */
    public interface OnMenuItemSelectedListener {
        void onTerminalMenuItemSelected(@NonNull TerminalMenuAction action);
    }

    /** An item of the dialog, with the title and the state that it was built with. */
    public static final class Item {
        @NonNull
        public final TerminalMenuAction action;
        @NonNull
        public final CharSequence title;
        /** Whether the item can be clicked, like the kill action of a session that is not running. */
        public final boolean enabled;

        public Item(@NonNull TerminalMenuAction action, @NonNull CharSequence title, boolean enabled) {
            this.action = action;
            this.title = title;
            this.enabled = enabled;
        }
    }

    private static final float MARGIN_DP = 16;
    private static final float ROW_PADDING_HORIZONTAL_DP = 12;
    private static final float ROW_PADDING_VERTICAL_DP = 10;
    private static final float MAX_WIDTH_DP = 360;
    /** The fraction of the screen height that the dialog may use before it scrolls. */
    private static final float MAX_HEIGHT_FRACTION = 0.7f;
    /**
     * The number of items from which the dialog gets a fixed height and scrolls, instead of fitting its
     * content. The default menu has 11 items, which still fits on a normal screen, so the dialog only
     * scrolls for longer menus that the user configured.
     */
    private static final int SCROLL_ITEM_COUNT = 12;

    private TerminalMenuDialog() {
    }

    /**
     * Show the dialog.
     *
     * @param activity The activity that the dialog is shown in.
     * @param config The configuration with the style of the dialog.
     * @param items The items to show, in the order they are shown.
     * @param listener The listener that is called when an item is clicked.
     * @param onDismissed The runnable that is run when the dialog is dismissed, like to clear the text
     *                    that was stored for the "share selected text" action.
     */
    public static void show(@NonNull Activity activity, @NonNull TerminalMenuConfig config,
                            @NonNull List<Item> items, @NonNull OnMenuItemSelectedListener listener,
                            @NonNull Runnable onDismissed) {
        if (items.isEmpty()) return;

        int foreground = StatusBarColors.resolve(config.foregroundColor, StatusBarColors.THEME_FOREGROUND);
        int accent = StatusBarColors.resolve(config.accentColor, "color4");
        if (Color.alpha(accent) == 0) accent = foreground;
        int background = getBackgroundColor(config, foreground);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(activity, 0), dp(activity, 4), dp(activity, 0), dp(activity, 4));
        scrollView.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (Item item : items) {
            View row = createRow(activity, config, item, foreground, accent);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                listener.onTerminalMenuItemSelected(item.action);
            });
            list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        GradientDrawable dialogBackground = new GradientDrawable();
        dialogBackground.setShape(GradientDrawable.RECTANGLE);
        dialogBackground.setCornerRadius(dp(activity, config.cornerRadiusDp));
        dialogBackground.setColor(background);
        if (config.borderWidthDp > 0)
            dialogBackground.setStroke(dp(activity, config.borderWidthDp), accent);
        scrollView.setBackground(dialogBackground);

        dialog.setContentView(scrollView);
        dialog.setOnDismissListener(d -> onDismissed.run());
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            // The dialog draws its own background, so that the border and the square corners of the tui
            // style are not covered by the default dialog background.
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
            int width = Math.min(screenWidth - dp(activity, MARGIN_DP * 2), dp(activity, MAX_WIDTH_DP));

            int height = WindowManager.LayoutParams.WRAP_CONTENT;
            if (items.size() > SCROLL_ITEM_COUNT)
                height = Math.round(screenHeight * MAX_HEIGHT_FRACTION);

            window.setLayout(width, height);
        }
    }

    /** @return The row of {@code item}, which shows its title on the left and its icon on the right. */
    @NonNull
    private static View createRow(@NonNull Activity activity, @NonNull TerminalMenuConfig config,
                                  @NonNull Item item, int foreground, int accent) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, ROW_PADDING_HORIZONTAL_DP), dp(activity, ROW_PADDING_VERTICAL_DP),
            dp(activity, ROW_PADDING_HORIZONTAL_DP), dp(activity, ROW_PADDING_VERTICAL_DP));
        row.setEnabled(item.enabled);
        row.setClickable(item.enabled);
        row.setBackground(createRowBackground(activity, accent, config.cornerRadiusDp));

        int textColor = item.enabled ? foreground : withAlpha(foreground, 0.38f);

        TextView title = new TextView(activity);
        title.setText(item.title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSizeSp);
        title.setTextColor(textColor);
        if (config.monospace) title.setTypeface(Typeface.MONOSPACE);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (config.showIcons) {
            TextView icon = new TextView(activity);
            icon.setText(item.action.getIcon());
            icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSizeSp);
            icon.setTextColor(withAlpha(textColor, 0.75f));
            if (config.monospace) icon.setTypeface(Typeface.MONOSPACE);
            icon.setGravity(Gravity.CENTER_VERTICAL);
            icon.setPadding(dp(activity, 8), 0, 0, 0);
            row.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        return row;
    }

    /**
     * @return The background of a row, which highlights it with the accent color while it is pressed,
     *         instead of a ripple, so that the rows look like part of a tui window.
     */
    @NonNull
    private static StateListDrawable createRowBackground(@NonNull Activity activity, int accent, int cornerRadiusDp) {
        StateListDrawable background = new StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setCornerRadius(dp(activity, cornerRadiusDp));
        pressed.setColor(withAlpha(accent, 0.30f));

        background.addState(new int[]{android.R.attr.state_pressed}, pressed);
        background.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));

        return background;
    }

    /**
     * @return The background color of the dialog, which is derived from the background color of the
     *         color scheme if `terminal-menu-background-color` is not set, so that the dialog is visible
     *         on top of the terminal background.
     */
    private static int getBackgroundColor(@NonNull TerminalMenuConfig config, int foregroundColor) {
        if (!DataUtils.isNullOrEmpty(config.backgroundColor))
            return StatusBarColors.resolve(config.backgroundColor, StatusBarColors.THEME_BACKGROUND);

        int backgroundColor = StatusBarColors.getThemeColor(StatusBarColors.THEME_BACKGROUND);
        // Blend the background towards the foreground color, so that the dialog is visible when it would
        // otherwise have the same color as the terminal background behind it.
        return TerminalColorScheme.blendColors(backgroundColor, foregroundColor, 0.10);
    }

    private static int withAlpha(int color, float alpha) {
        int colorAlpha = Math.round(Color.alpha(color) * alpha);
        return (color & 0x00FFFFFF) | (colorAlpha << 24);
    }

    private static int dp(@NonNull Activity activity, float dp) {
        return Math.round(ViewUtils.dpToPx(activity, dp));
    }
}
