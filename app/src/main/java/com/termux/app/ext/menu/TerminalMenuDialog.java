package com.termux.app.ext.menu;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.app.widget.ColorSchemeColors;
import com.termux.app.widget.TerminalDialogBuilder;
import com.termux.app.widget.TerminalDialogStyles;

import java.util.List;

/**
 * A dialog that shows the actions of the terminal context menu, instead of the system context menu,
 * so that it can be styled with the rounded corners of Material 3, the 1dp accent border, the monospace font
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

    private static final float ROW_PADDING_HORIZONTAL_DP = 12;
    private static final float ROW_PADDING_VERTICAL_DP = 10;
    /** The margin between the rows and the rounded corners of the window, so that no row is clipped by them. */
    private static final float LIST_PADDING_DP = 8;

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

        int foreground = ColorSchemeColors.resolve(config.foregroundColor, ColorSchemeColors.THEME_FOREGROUND);
        int accent = TerminalDialogStyles.getAccentColor(config.accentColor, foreground);
        int background = TerminalDialogStyles.getBackgroundColor(config.backgroundColor, foreground);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        int listPadding = dp(activity, LIST_PADDING_DP);
        list.setPadding(listPadding, listPadding, listPadding, listPadding);

        // The dialog does not exist before it is shown, so the click of a row reaches it through this holder.
        final Dialog[] dialogHolder = new Dialog[1];

        for (Item item : items) {
            View row = createRow(activity, config, item, foreground, accent);
            row.setOnClickListener(v -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                listener.onTerminalMenuItemSelected(item.action);
            });
            list.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TerminalDialogBuilder.Style style = new TerminalDialogBuilder.Style(foreground, background, accent,
            config.cornerRadiusDp, config.borderWidthDp, config.fontSizeSp, config.monospace);

        dialogHolder[0] = TerminalDialogBuilder.create(activity, style)
            .setContent(list)
            .setOnDismissed(onDismissed)
            .show();
    }

    /** @return The {@code dp} value in pixels. */
    private static int dp(@NonNull Activity activity, float dp) {
        return TerminalDialogStyles.dp(activity, dp);
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
        row.setBackground(TerminalDialogStyles.createRowBackground(activity, accent));

        int textColor = item.enabled ? foreground : TerminalDialogStyles.withAlpha(foreground, 0.38f);

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
            icon.setTextColor(TerminalDialogStyles.withAlpha(textColor, 0.75f));
            if (config.monospace) icon.setTypeface(Typeface.MONOSPACE);
            icon.setGravity(Gravity.CENTER_VERTICAL);
            icon.setPadding(dp(activity, 8), 0, 0, 0);
            row.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        return row;
    }

}
