package com.termux.shell.pane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shell.session.SessionId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 叶子窗格。
 *
 * <p>目的：描述一个可显示区域及其承载的页签集合。一个叶子在界面上对应一个 {@code TerminalView} 实例，
 * 切换页签时不重建该视图，以避免渲染器状态丢失。
 *
 * <p>骨架阶段说明：页签列表可以为空，此时该叶子显示空窗格占位内容。会话创建在运行时接入后启用。
 *
 * <p>约束：页签索引与列表长度必须保持一致；本类型非线程安全，仅在主线程或控制器内部使用。
 */
public final class PaneLeaf extends PaneNode {

    private final List<SessionId> mTabs = new ArrayList<>();
    private int mActiveTabIndex = -1;
    private int mScrollRow;

    public PaneLeaf(@NonNull String id) {
        super(id);
    }

    @NonNull
    public List<SessionId> getTabs() {
        return Collections.unmodifiableList(mTabs);
    }

    @Nullable
    public SessionId getActiveTab() {
        if (mActiveTabIndex < 0 || mActiveTabIndex >= mTabs.size()) {
            return null;
        }
        return mTabs.get(mActiveTabIndex);
    }

    public int getActiveTabIndex() {
        return mActiveTabIndex;
    }

    /**
     * 追加页签并将其设为当前页签。
     */
    public void addTab(@NonNull SessionId sessionId) {
        mTabs.add(sessionId);
        mActiveTabIndex = mTabs.size() - 1;
    }

    /**
     * 移除指定页签。
     *
     * @return 是否实际移除。索引越界时返回 false。
     */
    public boolean removeTab(int index) {
        if (index < 0 || index >= mTabs.size()) {
            return false;
        }
        mTabs.remove(index);
        if (mTabs.isEmpty()) {
            mActiveTabIndex = -1;
        } else if (mActiveTabIndex >= mTabs.size()) {
            mActiveTabIndex = mTabs.size() - 1;
        }
        return true;
    }

    public boolean selectTab(int index) {
        if (index < 0 || index >= mTabs.size()) {
            return false;
        }
        mActiveTabIndex = index;
        return true;
    }

    public boolean hasSessions() {
        return !mTabs.isEmpty();
    }

    public int getScrollRow() {
        return mScrollRow;
    }

    public void setScrollRow(int scrollRow) {
        mScrollRow = Math.max(scrollRow, 0);
    }
}
