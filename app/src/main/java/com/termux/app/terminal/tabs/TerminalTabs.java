package com.termux.app.terminal.tabs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 目的 — 管理标签页的顺序与活动标签，使标签栏、终端活动与会话客户端读写同一份列表，避免活动里再散落一份索引。
 *
 * <p>行为 — 新建的标签页被追加到末尾并立即成为活动标签。移除活动标签后激活其右侧的标签，右侧没有时激活左侧的；
 * 列表为空时活动索引为 {@code -1}。越界的活动索引被忽略，查询返回 {@code null}。标签 id 单调递增且不复用。</p>
 *
 * <p>约束 — 只管理标签对象本身，不创建会话、不结束会话；调用方在移除标签前后负责会话的结束与视图解绑。</p>
 */
public final class TerminalTabs {

    @NonNull
    private final List<TerminalTab> mTabs = new ArrayList<>();

    private int mActiveIndex = -1;
    private int mNextId = 1;

    /** @return A new tab with a fresh id, appended to the list and made active. */
    @NonNull
    public TerminalTab addTab() {
        TerminalTab tab = new TerminalTab(mNextId++);
        addTab(tab);
        return tab;
    }

    /**
     * Append an existing tab, like one restored from a saved state, and make it active. A tab whose id is not lower
     * than the next id to assign raises the counter, so that the ids stay unique.
     */
    public void addTab(@NonNull TerminalTab tab) {
        mTabs.add(tab);
        mActiveIndex = mTabs.size() - 1;
        if (tab.getId() >= mNextId) mNextId = tab.getId() + 1;
    }

    /** @return The tabs in display order. The list belongs to this class and must only be read. */
    @NonNull
    public List<TerminalTab> getTabs() {
        return Collections.unmodifiableList(mTabs);
    }

    /** @return The number of tabs, which is {@code 0} before the first session was created. */
    public int size() {
        return mTabs.size();
    }

    /** @return Whether there are no tabs at all. */
    public boolean isEmpty() {
        return mTabs.isEmpty();
    }

    /** @return The active tab, or {@code null} if there is none. */
    @Nullable
    public TerminalTab getActiveTab() {
        return getTab(mActiveIndex);
    }

    /** @return The index of the active tab, or {@code -1} if there is none. */
    public int getActiveIndex() {
        return mActiveIndex;
    }

    /** Make the tab at {@code index} the active one, which is ignored when the index is out of range. */
    public void setActiveIndex(int index) {
        if (index < 0 || index >= mTabs.size()) return;
        mActiveIndex = index;
    }

    /** Make {@code tab} the active one, which is ignored when the tab is not in the list. */
    public void setActiveTab(@Nullable TerminalTab tab) {
        if (tab == null) return;

        int index = mTabs.indexOf(tab);
        if (index >= 0) mActiveIndex = index;
    }

    /** @return The tab at {@code index}, or {@code null} if the index is out of range. */
    @Nullable
    public TerminalTab getTab(int index) {
        return index >= 0 && index < mTabs.size() ? mTabs.get(index) : null;
    }

    /** @return The tab that shows {@code session}, or {@code null} if no tab shows it. */
    @Nullable
    public TerminalTab findTabForSession(@Nullable TerminalSession session) {
        if (session == null) return null;

        for (TerminalTab tab : mTabs) {
            if (tab.hasSession(session)) return tab;
        }
        return null;
    }

    /**
     * Remove {@code tab} and keep the active index in range: the tab to its right becomes active, or the one to its
     * left when it was the last one.
     *
     * @return Whether the tab was in the list.
     */
    public boolean removeTab(@Nullable TerminalTab tab) {
        if (tab == null) return false;

        int index = mTabs.indexOf(tab);
        if (index < 0) return false;

        mTabs.remove(index);
        if (mTabs.isEmpty()) {
            mActiveIndex = -1;
        } else if (index < mActiveIndex || (index == mActiveIndex && mActiveIndex >= mTabs.size())) {
            mActiveIndex--;
        }
        return true;
    }

    /** Remove every tab, which is used when the activity is recreated from a saved state. */
    public void clear() {
        mTabs.clear();
        mActiveIndex = -1;
    }
}
