package com.termux.app.ext.notifications;

import android.app.Dialog;
import android.content.Context;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.widget.ColorSchemeColors;
import com.termux.app.widget.TerminalDialogBuilder;
import com.termux.app.widget.TerminalDialogStyles;
import com.termux.terminal.TerminalNotification;

import java.util.Date;
import java.util.List;

/**
 * A dialog that shows the notifications that were shown recently, including the ones whose cards have
 * already disappeared and the ones that were only posted in the system notification shade, so that the
 * user can look at them again, jump to the command of a failed one or copy its text.
 *
 * The dialog is styled like the terminal context menu and the notification cards, with the rounded corners
 * of Material 3, a 1dp accent border, the monospace font and the colors of the termux color scheme.
 */
public final class RecentNotificationsDialog {

    /** The listener that is called when an entry of the list is clicked. */
    public interface OnEntrySelectedListener {
        void onRecentNotificationSelected(@NonNull RecentNotifications.Entry entry);
    }

    private static final float ROW_PADDING_HORIZONTAL_DP = 12;
    private static final float ROW_PADDING_VERTICAL_DP = 8;
    /** The margin between the rows and the rounded corners of the window, so that no row is clipped by them. */
    private static final float LIST_PADDING_DP = 8;

    private RecentNotificationsDialog() {
    }

    /**
     * Show the dialog.
     *
     * @param context The context that the dialog is shown in.
     * @param config The configuration with the style of the dialog.
     * @param entries The notifications to show, newest first.
     * @param listener The listener that is called when an entry is clicked.
     * @param onCleared The runnable that is run when the user clears the list.
     */
    public static void show(@NonNull Context context, @NonNull NotificationConfig config,
                            @NonNull List<RecentNotifications.Entry> entries,
                            @NonNull OnEntrySelectedListener listener, @NonNull Runnable onCleared) {
        int foreground = ColorSchemeColors.resolve(config.foregroundColor, ColorSchemeColors.THEME_FOREGROUND);
        // The dialog is drawn with the accent color of informational notifications, while the entries
        // that are a failure or a warning are marked with their own accent color.
        int accent = TerminalDialogStyles.getAccentColor(config.accentInfoColor, foreground);
        int background = TerminalDialogStyles.getBackgroundColor(config.backgroundColor, foreground);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = TerminalDialogStyles.dp(context, LIST_PADDING_DP);
        list.setPadding(padding, padding, padding, padding);

        if (entries.isEmpty()) {
            TextView empty = TerminalDialogStyles.createTextView(context,
                TerminalDialogStyles.withAlpha(foreground, 0.6f), config.fontSizeSp, config.monospace, false);
            empty.setText(R.string.notification_history_empty);
            empty.setPadding(TerminalDialogStyles.dp(context, ROW_PADDING_HORIZONTAL_DP),
                TerminalDialogStyles.dp(context, ROW_PADDING_VERTICAL_DP),
                TerminalDialogStyles.dp(context, ROW_PADDING_HORIZONTAL_DP),
                TerminalDialogStyles.dp(context, ROW_PADDING_VERTICAL_DP));
            list.addView(empty);
        }

        // The dialog does not exist before it is shown, so the click of a row reaches it through this holder.
        final Dialog[] dialogHolder = new Dialog[1];

        for (RecentNotifications.Entry entry : entries) {
            View row = createRow(context, config, entry, foreground, accent);
            row.setOnClickListener(v -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                listener.onRecentNotificationSelected(entry);
            });
            list.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // The corner radius of the notifications is the one of the cards over the terminal, while the window
        // of the dialog always uses the radius of a dialog, so that it matches the other dialogs of the app.
        TerminalDialogBuilder.Style style = new TerminalDialogBuilder.Style(foreground, background, accent,
            TerminalDialogStyles.DIALOG_CORNER_DP, config.borderWidthDp, config.fontSizeSp, config.monospace);

        TerminalDialogBuilder builder = TerminalDialogBuilder.create(context, style)
            .setTitle(context.getString(R.string.notification_history_title))
            .setContent(list);

        if (!entries.isEmpty()) {
            builder.addButton(context.getString(R.string.notification_history_clear), () -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                onCleared.run();
            });
        }
        builder.addButton(context.getString(android.R.string.cancel), null);

        dialogHolder[0] = builder.show();
    }

    /** @return The row of {@code entry}, which shows the time, the title and the details of it. */
    @NonNull
    private static View createRow(@NonNull Context context, @NonNull NotificationConfig config,
                                  @NonNull RecentNotifications.Entry entry, int foreground, int accent) {
        TerminalNotification notification = entry.notification;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(TerminalDialogStyles.dp(context, ROW_PADDING_HORIZONTAL_DP),
            TerminalDialogStyles.dp(context, ROW_PADDING_VERTICAL_DP),
            TerminalDialogStyles.dp(context, ROW_PADDING_HORIZONTAL_DP),
            TerminalDialogStyles.dp(context, ROW_PADDING_VERTICAL_DP));
        row.setClickable(true);
        row.setBackground(TerminalDialogStyles.createRowBackground(context, accent));

        // A small accent bar marks the notifications that are a failure or a warning, like the cards do.
        if (notification.getKind() != TerminalNotification.Kind.INFO) {
            View bar = new View(context);
            bar.setBackgroundColor(getKindAccentColor(config, notification.getKind(), foreground));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                TerminalDialogStyles.dp(context, 3), ViewGroup.LayoutParams.MATCH_PARENT);
            barParams.setMarginEnd(TerminalDialogStyles.dp(context, 8));
            row.addView(bar, barParams);
        }

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(texts);

        TextView title = TerminalDialogStyles.createTextView(context, foreground, config.fontSizeSp,
            config.monospace, true);
        title.setText(notification.getTitle() != null ? notification.getTitle() : notification.getBody());
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        CharSequence detail = notification.getTitle() != null ? notification.getBody() : notification.getMeta();
        if (detail != null && !detail.toString().trim().isEmpty()) {
            TextView detailView = TerminalDialogStyles.createTextView(context,
                TerminalDialogStyles.withAlpha(foreground, 0.65f), Math.max(6, config.fontSizeSp - 2),
                config.monospace, false);
            detailView.setText(detail);
            detailView.setSingleLine(true);
            detailView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(detailView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView time = TerminalDialogStyles.createTextView(context,
            TerminalDialogStyles.withAlpha(foreground, 0.5f), Math.max(6, config.fontSizeSp - 2),
            config.monospace, false);
        time.setText(DateFormat.getTimeFormat(context).format(new Date(entry.timestampMs)));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.setMarginStart(TerminalDialogStyles.dp(context, 8));
        row.addView(time, timeParams);

        return row;
    }

    /** @return The accent color of the notifications of {@code kind}, like the one of their cards. */
    private static int getKindAccentColor(@NonNull NotificationConfig config,
                                          @NonNull TerminalNotification.Kind kind, int foreground) {
        switch (kind) {
            case FAILURE:
                return TerminalDialogStyles.getAccentColor(config.accentFailureColor, foreground);
            case SUCCESS:
                return TerminalDialogStyles.getAccentColor(config.accentSuccessColor, foreground);
            case WARNING:
                return TerminalDialogStyles.getAccentColor(config.accentWarningColor, foreground);
            case INFO:
            default:
                return TerminalDialogStyles.getAccentColor(config.accentInfoColor, foreground);
        }
    }

    /** @return The text of {@code notification}, which is copied to the clipboard when it is clicked. */
    @NonNull
    public static String getTextFor(@NonNull TerminalNotification notification) {
        StringBuilder text = new StringBuilder();
        if (notification.getTitle() != null) text.append(notification.getTitle());
        if (notification.getBody() != null) {
            if (text.length() > 0) text.append('\n');
            text.append(notification.getBody());
        }
        if (notification.getMeta() != null) {
            if (text.length() > 0) text.append('\n');
            text.append(notification.getMeta());
        }
        return text.toString();
    }

}
