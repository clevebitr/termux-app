package com.termux.app.terminal.tabs;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.util.List;

/**
 * 目的 — 显示终端的标签栏：每个标签一行标题与一个关闭按钮，末尾是新建按钮，供终端活动切换、新建、关闭与重命名
 * 标签页。
 *
 * <p>行为 — {@link #setTabs(List, int, String)} 按顺序重建标签项，活动标签用主题强调色与描边标出，其余用次要文字
 * 颜色；标签数量变化后自动把活动标签滚动到可见区域。点击标签、关闭按钮、新建按钮与长按标签分别回调
 * {@link Listener} 的对应方法。</p>
 *
 * <p>约束 — 只负责渲染与上报交互，不持有标签状态、不创建或结束会话；列表内容由调用方在标签变化后重新设置。
 * 标签数量较少时重建代价可忽略，因此不做增量更新。</p>
 */
public final class TerminalTabBar extends HorizontalScrollView {

    /** The callbacks of the tab bar, which the terminal activity implements. */
    public interface Listener {
        /** The user tapped the tab with {@code tabId}. */
        void onTabSelected(int tabId);

        /** The user tapped the new tab button. */
        void onNewTab();

        /** The user tapped the close button of the tab with {@code tabId}. */
        void onCloseTab(int tabId);

        /** The user long pressed the tab with {@code tabId}. */
        void onRenameTab(int tabId);
    }

    @NonNull
    private final LinearLayout mItemsContainer;

    @Nullable
    private Listener mListener;

    public TerminalTabBar(@NonNull Context context) {
        this(context, null);
    }

    public TerminalTabBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);

        mItemsContainer = new LinearLayout(context);
        mItemsContainer.setOrientation(LinearLayout.HORIZONTAL);
        mItemsContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        addView(mItemsContainer, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    }

    /** Set the listener of the user interactions, which may be {@code null} to ignore them. */
    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * Rebuild the tabs.
     *
     * @param tabs The tabs in display order.
     * @param activeIndex The index of the active tab, or {@code -1} if there is none.
     * @param defaultTitle The title of a tab that has neither a custom title nor a session title.
     */
    public void setTabs(@NonNull List<TerminalTab> tabs, int activeIndex, @NonNull String defaultTitle) {
        mItemsContainer.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < tabs.size(); i++) {
            TerminalTab tab = tabs.get(i);
            View item = inflater.inflate(R.layout.view_terminal_tab, mItemsContainer, false);
            bindTab(item, tab, i == activeIndex, defaultTitle);
            mItemsContainer.addView(item);
        }

        mItemsContainer.addView(createNewTabButton());

        // Keep the active tab visible, which matters when a tab was added or closed at the other end.
        View activeItem = mItemsContainer.getChildAt(activeIndex);
        if (activeItem != null) {
            post(() -> smoothScrollTo(activeItem.getLeft() - getWidth() / 4, 0));
        }
    }

    private void bindTab(@NonNull View item, @NonNull TerminalTab tab, boolean active, @NonNull String defaultTitle) {
        int onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.GRAY);
        int onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        int primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, onSurface);

        TextView titleView = item.findViewById(R.id.terminal_tab_title);
        titleView.setText(tab.getTitle(defaultTitle));
        titleView.setTextColor(active ? primary : onSurfaceVariant);
        titleView.getPaint().setFakeBoldText(active);

        TextView closeView = item.findViewById(R.id.terminal_tab_close);
        closeView.setTextColor(active ? primary : onSurfaceVariant);

        // The active tab gets a rounded container in the surface variant color, so that it stands out without a
        // second accent color that would clash with the terminal color scheme.
        if (active) {
            item.setBackgroundResource(R.drawable.terminal_tab_active_background);
        } else {
            item.setBackgroundResource(R.drawable.terminal_tab_background);
        }

        item.setOnClickListener(view -> {
            if (mListener != null) mListener.onTabSelected(tab.getId());
        });
        item.setOnLongClickListener(view -> {
            if (mListener != null) mListener.onRenameTab(tab.getId());
            return true;
        });
        closeView.setOnClickListener(view -> {
            if (mListener != null) mListener.onCloseTab(tab.getId());
        });
    }

    @NonNull
    private View createNewTabButton() {
        int onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);

        TextView button = new TextView(getContext());
        button.setText("+");
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
            getResources().getDimension(R.dimen.termux_tab_text_size));
        button.setTextColor(onSurfaceVariant);
        button.setGravity(android.view.Gravity.CENTER);
        button.setContentDescription(getContext().getString(R.string.termux_tab_new_description));
        button.setBackgroundResource(R.drawable.terminal_tab_background);
        int padding = getResources().getDimensionPixelSize(R.dimen.termux_tab_padding);
        button.setPadding(padding, 0, padding, 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, getResources().getDimensionPixelSize(R.dimen.termux_tab_height)));
        button.setOnClickListener(view -> {
            if (mListener != null) mListener.onNewTab();
        });
        return button;
    }
}
