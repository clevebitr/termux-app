package com.termux.app.widget;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.text.Selection;
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
 * <p>行为 — 背景、文字与提示色取自当前会话的配色方案（与抽屉同一来源），窗口由
 * {@link TerminalDialogBuilder} 绘制，因此使用 Material 3 圆角与配色方案强调色；输入框获得焦点并选中初始
 * 文本，回车等同点击确认按钮。</p>
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

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int padding = TerminalDialogStyles.dialogPaddingPx(activity);
        layout.setPadding(padding, TerminalDialogStyles.dp(activity, 4), padding, 0);
        layout.addView(input);

        TerminalDialogBuilder.Style style = new TerminalDialogBuilder.Style(foregroundColor, backgroundColor,
            TerminalDialogStyles.getAccentColor(null, foregroundColor), TerminalDialogStyles.DIALOG_CORNER_DP, 1,
            15, false);

        TerminalDialogBuilder builder = TerminalDialogBuilder.create(activity, style)
            .setTitle(activity.getString(titleText))
            .setContent(layout);

        // The dialog does not exist before it is shown, so the confirm action reaches it through this holder.
        final Dialog[] dialogHolder = new Dialog[1];
        Runnable confirm = () -> {
            onPositive.onTextSet(input.getText().toString());
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
        };

        builder.addButton(activity.getString(android.R.string.cancel), null);
        builder.addButton(activity.getString(positiveText), confirm);

        input.setImeActionLabel(activity.getString(positiveText), KeyEvent.KEYCODE_ENTER);
        input.setOnEditorActionListener((view, actionId, event) -> {
            confirm.run();
            return true;
        });

        dialogHolder[0] = builder.show();
        if (dialogHolder[0].getWindow() != null) dialogHolder[0].getWindow().setSoftInputMode(
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
