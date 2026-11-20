package com.termux.shell.tabs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 窗口级标签集合。
 *
 * <p>目的：集中维护一个窗口内的标签顺序、当前标签与数量上限，使窗口宿主与标签栏视图只读取状态并向本类型请求变更。
 *
 * <p>行为：
 * <ul>
 *     <li>标签按顺序排列，索引从 0 开始；当前标签索引始终指向有效标签；</li>
 *     <li>关闭当前标签后优先选中其右侧标签，右侧不存在时选中左侧；</li>
 *     <li>新标签标识由内部序号生成，恢复布局时跳过已占用的序号。</li>
 * </ul>
 *
 * <p>约束：非线程安全，仅允许在主线程调用；至少保留一个标签，关闭最后一个标签的请求返回 false。
 */
public final class TabWorkspace {

    /**
     * 单窗口允许的最大标签数量。
     *
     * <p>取值理由：标签栏在紧凑档位可横向滚动，真正的限制来自每棵窗格树各自持有的终端视图与回滚缓冲，
     * 8 个标签已足以覆盖桌面级使用，同时避免误操作导致内存持续增长。
     */
    public static final int MAX_TAB_COUNT = 8;

    private static final String ID_PREFIX = "tab-";

    private final List<WorkspaceTab> mTabs = new ArrayList<>();
    private int mActiveIndex = -1;
    private int mNextTabNumber = 1;

    /**
     * 创建一个空工作区。调用方应在首次使用时通过 {@link #newTab(String)} 建立首个标签。
     */
    public TabWorkspace() {
    }

    /**
     * 追加标签并设为当前标签。
     *
     * @param title 初始标题。标题为空或标签数量已达上限时返回 null。
     */
    @Nullable
    public WorkspaceTab newTab(@NonNull String title) {
        if (title.isEmpty() || mTabs.size() >= MAX_TAB_COUNT) {
            return null;
        }
        WorkspaceTab tab = new WorkspaceTab(ID_PREFIX + mNextTabNumber, title);
        mNextTabNumber++;
        mTabs.add(tab);
        mActiveIndex = mTabs.size() - 1;
        return tab;
    }

    /**
     * 按从左到右的顺序返回标签列表。
     */
    @NonNull
    public List<WorkspaceTab> getTabs() {
        return Collections.unmodifiableList(mTabs);
    }

    public int size() {
        return mTabs.size();
    }

    public boolean isEmpty() {
        return mTabs.isEmpty();
    }

    /**
     * 返回当前标签索引；工作区为空时返回 -1。
     */
    public int getActiveIndex() {
        return mActiveIndex;
    }

    /**
     * 返回当前标签；工作区为空时返回 null。
     */
    @Nullable
    public WorkspaceTab getActiveTab() {
        if (mActiveIndex < 0 || mActiveIndex >= mTabs.size()) {
            return null;
        }
        return mTabs.get(mActiveIndex);
    }

    /**
     * 返回指定位置的标签；索引越界时返回 null。
     */
    @Nullable
    public WorkspaceTab getTab(int index) {
        if (index < 0 || index >= mTabs.size()) {
            return null;
        }
        return mTabs.get(index);
    }

    /**
     * 选中指定标签。
     *
     * @return 是否发生变化。索引越界或已是当前标签时返回 false。
     */
    public boolean select(int index) {
        if (index < 0 || index >= mTabs.size() || index == mActiveIndex) {
            return false;
        }
        mActiveIndex = index;
        return true;
    }

    /**
     * 选中相对当前标签偏移若干位置的标签，并循环回绕。
     *
     * @return 是否发生变化。工作区标签少于两个时返回 false。
     */
    public boolean selectRelative(int offset) {
        if (mTabs.size() < 2 || offset == 0) {
            return false;
        }
        int count = mTabs.size();
        int target = ((mActiveIndex + offset) % count + count) % count;
        mActiveIndex = target;
        return true;
    }

    /**
     * 关闭指定标签。
     *
     * <p>当前标签被关闭时，优先选中右侧标签，右侧不存在时选中左侧；当前标签左侧的标签被关闭时，
     * 当前索引随之前移，保证仍指向同一标签。
     *
     * @return 是否实际关闭。索引越界或仅剩一个标签时返回 false。
     */
    public boolean close(int index) {
        if (index < 0 || index >= mTabs.size() || mTabs.size() <= 1) {
            return false;
        }
        mTabs.remove(index);
        if (index < mActiveIndex) {
            mActiveIndex--;
        } else if (index == mActiveIndex) {
            mActiveIndex = Math.min(index, mTabs.size() - 1);
        }
        return true;
    }

    /**
     * 移动标签位置。
     *
     * @return 是否发生变化。索引越界或源与目标相同时返回 false。
     */
    public boolean move(int from, int to) {
        if (from == to || from < 0 || from >= mTabs.size() || to < 0 || to >= mTabs.size()) {
            return false;
        }
        WorkspaceTab tab = mTabs.remove(from);
        mTabs.add(to, tab);
        if (mActiveIndex == from) {
            mActiveIndex = to;
        } else if (from < mActiveIndex && to >= mActiveIndex) {
            mActiveIndex--;
        } else if (from > mActiveIndex && to <= mActiveIndex) {
            mActiveIndex++;
        }
        return true;
    }

    /**
     * 用已解码的标签列表替换当前状态，用于布局恢复。
     *
     * @param tabs        标签列表，不得为空且数量不得超过 {@link #MAX_TAB_COUNT}。
     * @param activeIndex 当前标签索引，越界时截断到最近的有效索引。
     */
    public void restore(@NonNull List<WorkspaceTab> tabs, int activeIndex) {
        if (tabs.isEmpty()) {
            throw new IllegalArgumentException("恢复的标签列表不得为空");
        }
        if (tabs.size() > MAX_TAB_COUNT) {
            throw new IllegalArgumentException("恢复的标签数量超过上限：" + tabs.size());
        }
        mTabs.clear();
        mTabs.addAll(tabs);
        mActiveIndex = Math.max(0, Math.min(activeIndex, mTabs.size() - 1));
        mNextTabNumber = 1;
        for (WorkspaceTab tab : mTabs) {
            String id = tab.getId();
            if (!id.startsWith(ID_PREFIX)) {
                continue;
            }
            try {
                int number = Integer.parseInt(id.substring(ID_PREFIX.length()));
                if (number >= mNextTabNumber) {
                    mNextTabNumber = number + 1;
                }
            } catch (NumberFormatException ignored) {
                // 标识由本类型生成，非标准序号来自手工编辑的布局文件，忽略即可。
            }
        }
    }

    /**
     * 返回下一个标签标识将使用的序号，供日志与调试输出。
     */
    @NonNull
    public String describeState() {
        return String.format(Locale.ROOT, "tabs=%d active=%d", mTabs.size(), mActiveIndex);
    }
}
