package com.termux.shell.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.R;
import com.termux.shell.commands.CommandFilter;
import com.termux.shell.commands.ShellCommand;
import com.termux.shell.log.ShellLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令面板。
 *
 * <p>目的：提供与 Windows Terminal 命令面板一致的入口体验：输入即筛选、方向键选择、回车执行、Esc 关闭。
 *
 * <p>行为：
 * <ul>
 *     <li>面板以浮动卡片形式出现在窗口顶部，背景遮罩，焦点落在查询框并弹出输入法；</li>
 *     <li>查询按 {@link CommandFilter} 规则筛选，列表高度按结果数量收缩，最多显示约六行后滚动；</li>
 *     <li>不可执行的命令仍然显示但置为半透明，点击或回车均不生效；</li>
 *     <li>执行前先关闭面板，再在主线程消息队列中执行命令，避免命令触发重建界面时与本面板的收尾冲突。</li>
 * </ul>
 *
 * <p>约束：仅支持单实例与主线程调用；窗口宿主销毁前必须调用 {@link #dismiss()}。
 */
public final class CommandPaletteController {

    private static final String SUB_TAG = "CommandPalette";
    private static final int WINDOW_MAX_WIDTH_DP = 560;
    private static final int WINDOW_MIN_SIDE_MARGIN_DP = 16;
    private static final int WINDOW_TOP_MARGIN_DP = 72;
    private static final int LIST_MAX_HEIGHT_DP = 320;
    private static final int ROW_HEIGHT_DP = 44;
    private static final float DIM_AMOUNT = 0.45f;

    private final Activity mActivity;
    private final Dialog mDialog;
    private final EditText mQueryView;
    private final ScrollView mScrollView;
    private final LinearLayout mListView;
    private final TextView mEmptyView;

    private final List<ShellCommand> mCommands = new ArrayList<>();
    private final List<View> mRows = new ArrayList<>();
    private final List<ShellCommand> mVisibleCommands = new ArrayList<>();

    private int mSelectedIndex = -1;
    private boolean mRebuilding;

    public CommandPaletteController(@NonNull Activity activity) {
        mActivity = activity;
        mDialog = new Dialog(activity);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(LayoutInflater.from(activity).inflate(
            com.termux.shell.R.layout.dialog_command_palette, null, false));

        mQueryView = mDialog.findViewById(com.termux.shell.R.id.palette_query);
        mScrollView = mDialog.findViewById(com.termux.shell.R.id.palette_scroll);
        mListView = mDialog.findViewById(com.termux.shell.R.id.palette_list);
        mEmptyView = mDialog.findViewById(com.termux.shell.R.id.palette_empty);

        mQueryView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!mRebuilding) {
                    applyFilter(s == null ? "" : s.toString());
                }
            }
        });
        mQueryView.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                executeSelected();
                return true;
            }
            return false;
        });
        mQueryView.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    executeSelected();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    moveSelection(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    moveSelection(-1);
                    return true;
                default:
                    return false;
            }
        });

        Window window = mDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(DIM_AMOUNT);
            window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        mDialog.setOnShowListener(dialog -> {
            applyWindowSize();
            mQueryView.requestFocus();
        });
        mDialog.setOnDismissListener(dialog -> clearSelection());
    }

    /**
     * 返回面板是否正在显示。
     */
    public boolean isShowing() {
        return mDialog.isShowing();
    }

    /**
     * 打开面板。
     *
     * @param commands 本次可用的命令清单；调用方可每次重新构建，以反映最新的可执行状态。
     */
    public void show(@NonNull List<ShellCommand> commands) {
        mCommands.clear();
        mCommands.addAll(commands);
        mRebuilding = true;
        mQueryView.setText("");
        mRebuilding = false;
        if (!mDialog.isShowing()) {
            mDialog.show();
        }
        applyFilter("");
        mQueryView.requestFocus();
    }

    /**
     * 关闭面板。重复调用安全。
     */
    public void dismiss() {
        if (mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

    /**
     * 释放面板资源。窗口宿主销毁时必须调用，重复调用安全。
     */
    public void release() {
        dismiss();
    }

    private void applyWindowSize() {
        Window window = mDialog.getWindow();
        if (window == null) {
            return;
        }
        float density = mActivity.getResources().getDisplayMetrics().density;
        int maxWidth = Math.round(WINDOW_MAX_WIDTH_DP * density);
        int sideMargin = Math.round(WINDOW_MIN_SIDE_MARGIN_DP * density);
        int screenWidth = mActivity.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(maxWidth, Math.max(1, screenWidth - sideMargin * 2));
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);

        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.y = Math.round(WINDOW_TOP_MARGIN_DP * density);
        window.setAttributes(attributes);
    }

    private void applyFilter(@NonNull String query) {
        mVisibleCommands.clear();
        for (ShellCommand command : mCommands) {
            if (CommandFilter.matches(query, command.getTitle(), command.getCategory())) {
                mVisibleCommands.add(command);
            }
        }
        rebuildRows();
    }

    private void rebuildRows() {
        mListView.removeAllViews();
        mRows.clear();

        LayoutInflater inflater = LayoutInflater.from(mActivity);
        for (ShellCommand command : mVisibleCommands) {
            View row = inflater.inflate(com.termux.shell.R.layout.item_command_row, mListView, false);
            TextView titleView = row.findViewById(com.termux.shell.R.id.command_title);
            TextView shortcutView = row.findViewById(com.termux.shell.R.id.command_shortcut);
            titleView.setText(command.getTitle());
            String shortcut = command.getShortcut();
            shortcutView.setText(shortcut == null ? "" : shortcut);
            row.setAlpha(command.isEnabled() ? 1f : 0.45f);
            row.setOnClickListener(view -> {
                if (command.isEnabled()) {
                    dismissAndExecute(command);
                }
            });
            mListView.addView(row);
            mRows.add(row);
        }

        mEmptyView.setVisibility(mVisibleCommands.isEmpty() ? View.VISIBLE : View.GONE);
        mScrollView.getLayoutParams().height = computeListHeight();
        mScrollView.requestLayout();
        mSelectedIndex = firstEnabledIndex();
        updateSelectionHighlight();
    }

    private int computeListHeight() {
        float density = mActivity.getResources().getDisplayMetrics().density;
        int rowHeight = Math.round(ROW_HEIGHT_DP * density);
        int maxHeight = Math.round(LIST_MAX_HEIGHT_DP * density);
        int height = Math.max(rowHeight, mVisibleCommands.size() * rowHeight);
        return Math.min(height, maxHeight);
    }

    private int firstEnabledIndex() {
        for (int i = 0; i < mVisibleCommands.size(); i++) {
            if (mVisibleCommands.get(i).isEnabled()) {
                return i;
            }
        }
        return mVisibleCommands.isEmpty() ? -1 : 0;
    }

    private void moveSelection(int offset) {
        if (mVisibleCommands.isEmpty()) {
            return;
        }
        int count = mVisibleCommands.size();
        int index = mSelectedIndex;
        for (int attempt = 0; attempt < count; attempt++) {
            index = ((index + offset) % count + count) % count;
            if (mVisibleCommands.get(index).isEnabled()) {
                mSelectedIndex = index;
                updateSelectionHighlight();
                return;
            }
        }
    }

    private void updateSelectionHighlight() {
        int selectedBackground = ThemeColors.resolve(mActivity,
            R.attr.colorSurfaceVariant, 0xFFE7E0EC);
        for (int i = 0; i < mRows.size(); i++) {
            View row = mRows.get(i);
            if (i == mSelectedIndex) {
                GradientDrawable highlight = new GradientDrawable();
                highlight.setColor(selectedBackground);
                float radius = 8f * mActivity.getResources().getDisplayMetrics().density;
                highlight.setCornerRadius(radius);
                row.setBackground(highlight);
                scrollSelectionIntoView(row);
            } else {
                row.setBackground(null);
            }
        }
    }

    private void scrollSelectionIntoView(@NonNull View row) {
        if (mSelectedIndex < 0) {
            return;
        }
        int rowTop = 0;
        for (int i = 0; i < mSelectedIndex && i < mRows.size(); i++) {
            rowTop += mRows.get(i).getHeight();
        }
        int rowBottom = rowTop + row.getHeight();
        int visibleTop = mScrollView.getScrollY();
        int visibleBottom = visibleTop + mScrollView.getHeight();
        if (rowTop < visibleTop) {
            mScrollView.smoothScrollTo(0, rowTop);
        } else if (rowBottom > visibleBottom) {
            mScrollView.smoothScrollTo(0, rowBottom - mScrollView.getHeight());
        }
    }

    private void executeSelected() {
        if (mSelectedIndex < 0 || mSelectedIndex >= mVisibleCommands.size()) {
            return;
        }
        ShellCommand command = mVisibleCommands.get(mSelectedIndex);
        if (!command.isEnabled()) {
            return;
        }
        dismissAndExecute(command);
    }

    private void dismissAndExecute(@NonNull ShellCommand command) {
        ShellLog.debug(SUB_TAG, "执行命令：" + command.getId());
        dismiss();
        mActivity.getWindow().getDecorView().post(command::execute);
    }

    private void clearSelection() {
        mSelectedIndex = -1;
        mListView.removeAllViews();
        mRows.clear();
        mVisibleCommands.clear();
        mScrollView.scrollTo(0, 0);
    }

    /**
     * 返回当前可见命令数量，供调试输出使用。
     */
    public int getVisibleCommandCount() {
        return mVisibleCommands.size();
    }
}
