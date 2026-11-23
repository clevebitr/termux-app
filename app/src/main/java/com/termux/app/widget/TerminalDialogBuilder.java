package com.termux.app.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 目的 — 为终端内的自绘对话框（终端菜单、命名输入、最近通知历史）提供统一的窗口、标题、正文与按钮栏，
 * 使这些对话框共用同一套 Material 3 圆角、边距、文字层级与按下态，而不是各自拼装窗口。
 *
 * <p>行为 — 窗口背景由 {@link TerminalDialogStyles#createDialogBackgroundPx} 按样式绘制，因此圆角来自主题令牌
 * {@code termux_dialog_corner_radius} 或调用方给定的半径，描边来自配色方案的强调色；正文自动包进滚动容器，并在
 * 超过屏幕高度的七成时滚动；按钮栏为右对齐的强调色文字按钮，按下态与行一致。颜色仍取自终端配色方案，
 * 不随 Material 3 表面色变化。</p>
 *
 * <p>约束 — 只在主线程创建与显示；一个构建器只能显示一次。调用方负责设置标题之前准备好配色，标题与按钮
 * 的可读性依赖 {@link Style#foregroundColor} 与 {@link Style#accentColor} 的对比度。</p>
 */
public final class TerminalDialogBuilder {

    /** The corner radius and border width are in dp, the text style is the one of the color scheme. */
    public static final class Style {

        public final int foregroundColor;
        public final int backgroundColor;
        public final int accentColor;
        public final int cornerRadiusDp;
        public final int borderWidthDp;
        public final float fontSizeSp;
        public final boolean monospace;

        public Style(int foregroundColor, int backgroundColor, int accentColor, int cornerRadiusDp,
                     int borderWidthDp, float fontSizeSp, boolean monospace) {
            this.foregroundColor = foregroundColor;
            this.backgroundColor = backgroundColor;
            this.accentColor = accentColor;
            this.cornerRadiusDp = Math.max(0, cornerRadiusDp);
            this.borderWidthDp = Math.max(0, borderWidthDp);
            this.fontSizeSp = fontSizeSp;
            this.monospace = monospace;
        }
    }

    /** The fraction of the screen height that the body of a dialog may use before it scrolls. */
    private static final float MAX_BODY_HEIGHT_FRACTION = 0.7f;
    /** The space that the title and the footer of a dialog may take from the body at most, in dp. */
    private static final float CHROME_HEIGHT_DP = 104;
    private static final float TITLE_FONT_SIZE_SP = 18;
    private static final float BUTTON_FONT_SIZE_SP = 14;

    @NonNull
    private final Context mContext;
    @NonNull
    private final Style mStyle;
    @NonNull
    private final List<Button> mButtons = new ArrayList<>();

    @Nullable
    private CharSequence mTitle;
    @Nullable
    private View mContent;
    @Nullable
    private Runnable mOnDismissed;
    @Nullable
    private android.app.Dialog mDialog;

    private boolean mShown;

    private TerminalDialogBuilder(@NonNull Context context, @NonNull Style style) {
        mContext = context;
        mStyle = style;
    }

    /** Create a builder for a dialog with the colors and the shape of {@code style}. */
    @NonNull
    public static TerminalDialogBuilder create(@NonNull Context context, @NonNull Style style) {
        return new TerminalDialogBuilder(context, style);
    }

    /** Set the title of the dialog, which is shown above the content in the title style of a Material 3 dialog. */
    @NonNull
    public TerminalDialogBuilder setTitle(@Nullable CharSequence title) {
        mTitle = title;
        return this;
    }

    /** Set the body of the dialog, which is wrapped in a scroll container and scrolls when it is too tall. */
    @NonNull
    public TerminalDialogBuilder setContent(@Nullable View content) {
        mContent = content;
        return this;
    }

    /**
     * Add a button to the footer of the dialog, from left to right in the order of the calls, so that the
     * caller can put the dismissive action before the confirming one.
     */
    @NonNull
    public TerminalDialogBuilder addButton(@NonNull CharSequence text, @Nullable Runnable onClick) {
        mButtons.add(new Button(text, onClick));
        return this;
    }

    /** Set the runnable that is run when the dialog is dismissed, like to clear a cached selection. */
    @NonNull
    public TerminalDialogBuilder setOnDismissed(@Nullable Runnable onDismissed) {
        mOnDismissed = onDismissed;
        return this;
    }

    /** Build and show the dialog. */
    @NonNull
    public android.app.Dialog show() {
        if (mShown) throw new IllegalStateException("A TerminalDialogBuilder can only be shown once");
        mShown = true;

        android.app.Dialog dialog = new android.app.Dialog(mContext);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog = dialog;

        int width = dialogWidth();
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);

        if (mTitle != null) root.addView(createTitleView(mTitle));

        View body = mContent;
        if (body != null) {
            ScrollView scrollView = new ScrollView(mContext);
            scrollView.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, bodyHeight(body, width)));
            root.addView(scrollView);
        }

        if (!mButtons.isEmpty()) root.addView(createFooter());

        dialog.setContentView(root);
        if (mOnDismissed != null) dialog.setOnDismissListener(dismissed -> mOnDismissed.run());

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            // The dialog draws its own background, so that the corners and the accent border of the style are not
            // covered by the default window background of the platform, whose corners are not the token of the app.
            window.setBackgroundDrawable(TerminalDialogStyles.createDialogBackgroundPx(mContext,
                mStyle.backgroundColor, mStyle.accentColor,
                TerminalDialogStyles.dp(mContext, mStyle.cornerRadiusDp),
                TerminalDialogStyles.dp(mContext, mStyle.borderWidthDp)));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        return dialog;
    }

    /** @return The width of the dialog, which is the content width of the screen, capped for wide screens. */
    private int dialogWidth() {
        int screenWidth = mContext.getResources().getDisplayMetrics().widthPixels;
        int margins = 2 * TerminalDialogStyles.dialogMarginPx(mContext);
        return Math.min(screenWidth - margins, TerminalDialogStyles.dialogMaxWidthPx(mContext));
    }

    /**
     * @return The height of the scroll container of {@code body}, which is the height of the content until it would
     *         take more than {@link #MAX_BODY_HEIGHT_FRACTION} of the screen, and the height of the screen it may
     *         use otherwise, so that long lists scroll instead of pushing the title and the buttons off screen.
     */
    private int bodyHeight(@NonNull View body, int width) {
        body.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        int screenHeight = mContext.getResources().getDisplayMetrics().heightPixels;
        int maxBodyHeight = Math.round(screenHeight * MAX_BODY_HEIGHT_FRACTION)
            - TerminalDialogStyles.dp(mContext, CHROME_HEIGHT_DP);
        maxBodyHeight = Math.max(TerminalDialogStyles.dp(mContext, 120), maxBodyHeight);

        int contentHeight = body.getMeasuredHeight();
        return contentHeight > 0 ? Math.min(contentHeight, maxBodyHeight) : ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    /** @return The title view, which uses the color of the text of the dialog and the title style of Material 3. */
    @NonNull
    private TextView createTitleView(@NonNull CharSequence title) {
        TextView titleView = TerminalDialogStyles.createTextView(mContext, mStyle.foregroundColor,
            TITLE_FONT_SIZE_SP, mStyle.monospace, true);
        titleView.setText(title);
        titleView.setPadding(TerminalDialogStyles.dialogPaddingPx(mContext),
            TerminalDialogStyles.dp(mContext, 20),
            TerminalDialogStyles.dialogPaddingPx(mContext),
            TerminalDialogStyles.dp(mContext, 4));
        return titleView;
    }

    /** @return The footer with the buttons of the dialog, right aligned like the actions of a Material 3 dialog. */
    @NonNull
    private LinearLayout createFooter() {
        LinearLayout footer = new LinearLayout(mContext);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END);
        footer.setPadding(TerminalDialogStyles.dp(mContext, 12), TerminalDialogStyles.dp(mContext, 4),
            TerminalDialogStyles.dp(mContext, 12), TerminalDialogStyles.dp(mContext, 8));

        for (Button button : mButtons) {
            TextView view = TerminalDialogStyles.createTextView(mContext, mStyle.accentColor,
                BUTTON_FONT_SIZE_SP, mStyle.monospace, true);
            view.setText(button.text);
            view.setGravity(Gravity.CENTER);
            view.setMinHeight(TerminalDialogStyles.dialogButtonHeightPx(mContext));
            view.setPadding(TerminalDialogStyles.dp(mContext, 16), TerminalDialogStyles.dp(mContext, 8),
                TerminalDialogStyles.dp(mContext, 16), TerminalDialogStyles.dp(mContext, 8));
            view.setBackground(TerminalDialogStyles.createRowBackground(mContext, mStyle.accentColor));
            if (button.onClick != null) {
                view.setOnClickListener(v -> button.onClick.run());
            } else {
                // A button without an action still has to dismiss the dialog, like the cancel button does.
                view.setOnClickListener(v -> {
                    if (mShown && mDialog != null) mDialog.dismiss();
                });
            }
            footer.addView(view);
        }

        return footer;
    }

    /** A button of the footer, which runs {@code onClick} when it is clicked and is null for a dismiss button. */
    private static final class Button {
        final CharSequence text;
        @Nullable
        final Runnable onClick;

        Button(@NonNull CharSequence text, @Nullable Runnable onClick) {
            this.text = text;
            this.onClick = onClick;
        }
    }
}
