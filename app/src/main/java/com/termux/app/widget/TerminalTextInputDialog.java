package com.termux.app.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Selection;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.app.ext.drawer.DrawerColorUtils;
import com.termux.terminal.TerminalSession;

/**
 * 目的 — 提供与终端配色一致的单行文本输入对话框，用于重命名会话与新建命名会话。
 *
 * <p>行为 — 背景、文字与提示色取自当前会话的配色方案（与抽屉同一来源），因此深色配色下不会出现黑字黑底；
 * 输入框获得焦点并选中初始文本，回车等同点击确认按钮。</p>
 *
 * <p>约束 — 只用于单行短文本；调用方负责在会话为空或文本为空时自行处理。对话框不使用只读模块的
 * {@code TextInputDialogUtils}，因为后者无法设置文字颜色。</p>
 */
public final class TerminalTextInputDialog {

    /** The receiver of the text that the user confirmed. */
    public interface OnTextSetListener {
        void onTextSet(@NonNull String text);
    }

    private TerminalTextInputDialog() {
    }

    /**
     * Show the dialog.
     *
     * @param session       The session whose colors the dialog follows, or {@code null} for the theme colors.
     * @param titleText     The title of the dialog.
     * @param initialText   The text that the input starts with, or {@code null}.
     * @param positiveText  The label of the confirm button.
     * @param onPositive    The receiver of the text of the input, called for the confirm button and the enter key.
     */
    public static void show(@NonNull Activity activity, @Nullable TerminalSession session,
                            @StringRes int titleText, @Nullable String initialText,
                            @StringRes int positiveText, @NonNull OnTextSetListener onPositive) {
        int foregroundColor = foregroundOf(session);
        int backgroundColor = backgroundColorOf(session, foregroundColor);

        EditText input = new EditText(activity);
        input.setSingleLine();
        input.setTextColor(foregroundColor);
        input.setHintTextColor(DrawerColorUtils.withAlpha(foregroundColor, 0xB3));
        input.setHighlightColor(DrawerColorUtils.withAlpha(foregroundColor, 0x33));
        if (initialText != null) {
            input.setText(initialText);
            Selection.setSelection(input.getText(), initialText.length());
        }

        float density = activity.getResources().getDisplayMetrics().density;
        int padding = Math.round(20 * density);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.setPadding(padding, padding / 2, padding, 0);
        layout.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle(titleText)
            .setView(layout)
            .setPositiveButton(positiveText, (which, unused) -> {
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        // The window background is drawn by the dialog itself, so the square window of the fork is applied here
        // instead of by the app theme, which keeps the dialogs consistent with the terminal menu.
        GradientDrawable background = TerminalDialogStyles.createDialogBackground(activity, backgroundColor,
            TerminalDialogStyles.getAccentColor(null, foregroundColor), 0, 1);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(background);

        // Set after showing, so that the listener replaces the one of the builder and both the button and the enter
        // key hand the text of the input to the caller.
        dialog.setOnShowListener(shown -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
                onPositive.onTextSet(input.getText().toString());
                dialog.dismiss();
            });
        });

        input.setImeActionLabel(activity.getString(positiveText), KeyEvent.KEYCODE_ENTER);
        input.setOnEditorActionListener((view, actionId, event) -> {
            onPositive.onTextSet(input.getText().toString());
            dialog.dismiss();
            return true;
        });

        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        input.requestFocus();
    }

    /** @return The text color of the dialog, which is the foreground color of the color scheme. */
    private static int foregroundOf(@Nullable TerminalSession session) {
        int[] colors = session != null && session.getEmulator() != null
            ? session.getEmulator().mColors.mCurrentColors : null;
        if (colors != null) return colors[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND];
        return ColorSchemeColors.getThemeColor(ColorSchemeColors.THEME_FOREGROUND);
    }

    /** @return The background of the dialog, which is the background color of the color scheme. */
    private static int backgroundColorOf(@Nullable TerminalSession session, int foregroundColor) {
        int[] colors = session != null && session.getEmulator() != null
            ? session.getEmulator().mColors.mCurrentColors : null;
        if (colors != null) return colors[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND];
        return TerminalDialogStyles.getBackgroundColor(null, foregroundColor);
    }

}
