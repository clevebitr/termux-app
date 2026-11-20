package com.termux.shell.tabs

import com.termux.shell.pane.PaneLayout
import com.termux.shell.pane.PaneLeaf
import com.termux.shell.pane.SplitOrientation

/**
 * 窗口级标签集合。
 *
 * 目的：集中维护一个窗口内的标签顺序、当前标签与数量上限，使界面只读取状态并通过返回新实例的
 * 操作请求变更，从而与 Compose 的状态驱动渲染自然配合。
 *
 * 行为：
 * - 标签按顺序排列，索引从 0 开始；当前标签索引始终指向有效标签；
 * - 关闭当前标签后优先选中其右侧标签，右侧不存在时选中左侧；
 * - 新标签与窗格标识由内部序号生成，恢复布局时跳过已占用的序号。
 *
 * 约束：本类型不可变；至少保留一个标签，关闭最后一个标签的请求返回 null。
 */
data class TabWorkspace(
    val tabs: List<WorkspaceTab>,
    val activeIndex: Int,
    val nextTabNumber: Int,
) {

    init {
        require(tabs.isNotEmpty()) { "标签列表不得为空" }
        require(activeIndex in tabs.indices) { "当前标签索引越界：$activeIndex" }
    }

    val size: Int
        get() = tabs.size

    val activeTab: WorkspaceTab
        get() = tabs[activeIndex]

    /** 追加标签并设为当前标签。标签数量已达上限时返回 null。 */
    fun newTab(title: String): TabWorkspace? {
        if (title.isEmpty() || tabs.size >= MAX_TAB_COUNT) {
            return null
        }
        val tab = WorkspaceTab("tab-$nextTabNumber", title, PaneLeaf("pane-1"))
        return copy(tabs = tabs + tab, activeIndex = tabs.size, nextTabNumber = nextTabNumber + 1)
    }

    /** 选中指定标签。索引越界或已是当前标签时返回 null。 */
    fun select(index: Int): TabWorkspace? {
        if (index !in tabs.indices || index == activeIndex) {
            return null
        }
        return copy(activeIndex = index)
    }

    /** 选中相对当前标签偏移若干位置的标签并循环回绕。标签少于两个时返回 null。 */
    fun selectRelative(offset: Int): TabWorkspace? {
        if (tabs.size < 2 || offset == 0) {
            return null
        }
        val target = ((activeIndex + offset) % tabs.size + tabs.size) % tabs.size
        return copy(activeIndex = target)
    }

    /** 关闭指定标签。索引越界或仅剩一个标签时返回 null。 */
    fun closeTab(index: Int): TabWorkspace? {
        if (index !in tabs.indices || tabs.size <= 1) {
            return null
        }
        val newTabs = tabs.toMutableList().apply { removeAt(index) }
        val newActive = when {
            index < activeIndex -> activeIndex - 1
            index == activeIndex -> minOf(index, newTabs.size - 1)
            else -> activeIndex
        }
        return copy(tabs = newTabs, activeIndex = newActive)
    }

    /** 移动标签位置。索引越界或源与目标相同时返回 null。 */
    fun moveTab(from: Int, to: Int): TabWorkspace? {
        if (from == to || from !in tabs.indices || to !in tabs.indices) {
            return null
        }
        val newTabs = tabs.toMutableList()
        val tab = newTabs.removeAt(from)
        newTabs.add(to, tab)
        val newActive = when {
            activeIndex == from -> to
            from < activeIndex && to >= activeIndex -> activeIndex - 1
            from > activeIndex && to <= activeIndex -> activeIndex + 1
            else -> activeIndex
        }
        return copy(tabs = newTabs, activeIndex = newActive)
    }

    /** 用变换函数更新当前标签。 */
    fun updateActiveTab(transform: (WorkspaceTab) -> WorkspaceTab): TabWorkspace {
        val newTabs = tabs.toMutableList()
        newTabs[activeIndex] = transform(newTabs[activeIndex])
        return copy(tabs = newTabs)
    }

    /**
     * 分割当前标签的焦点窗格。
     *
     * @return 新工作区与新叶子；窗格数已达上限或当前标签缺失时新叶子为 null。
     */
    fun splitFocused(orientation: SplitOrientation): Pair<TabWorkspace, PaneLeaf?> {
        val tab = activeTab
        if (tab.leaves.size >= MAX_LEAF_COUNT) {
            return this to null
        }
        val result = PaneLayout.split(tab.root, tab.focusedLeaf.id, orientation)
            ?: return this to null
        val updated = updateActiveTab { it.copy(root = result.root, focusedLeafId = result.newLeafId) }
        return updated to PaneLayout.findLeaf(result.root, result.newLeafId)
    }

    /** 关闭当前标签的焦点窗格。仅剩一个窗格或目标不存在时返回 null。 */
    fun closeFocusedPane(): TabWorkspace? {
        val tab = activeTab
        if (tab.leaves.size <= 1) {
            return null
        }
        val result = PaneLayout.close(tab.root, tab.focusedLeaf.id) ?: return null
        return updateActiveTab { it.copy(root = result.root, focusedLeafId = result.focusLeafId) }
    }

    /** 在焦点顺序中循环迁移。叶子不足两个时返回 null。 */
    fun focusRelative(delta: Int): TabWorkspace? {
        val tab = activeTab
        val target = PaneLayout.focusRelative(tab.leaves, tab.focusedLeaf.id, delta) ?: return null
        return updateActiveTab { it.copy(focusedLeafId = target) }
    }

    /** 把焦点切换到指定叶子。叶子不存在或已是焦点时返回 null。 */
    fun focusLeaf(leafId: String): TabWorkspace? {
        val tab = activeTab
        if (tab.focusedLeaf.id == leafId || PaneLayout.findLeaf(tab.root, leafId) == null) {
            return null
        }
        return updateActiveTab { it.copy(focusedLeafId = leafId) }
    }

    /** 设置当前标签内某个分割节点的比例。目标不存在时返回 null。 */
    fun setRatio(splitId: String, ratio: Float): TabWorkspace? {
        val tab = activeTab
        val newRoot = PaneLayout.setRatio(tab.root, splitId, ratio) ?: return null
        return updateActiveTab { it.copy(root = newRoot) }
    }

    /** 返回调试用的状态摘要。 */
    fun describeState(): String = "tabs=$size active=$activeIndex"

    companion object {

        /**
         * 单窗口允许的最大标签数量。
         *
         * 取值理由：每个标签持有独立的窗格树与终端视图，8 个标签已足以覆盖桌面级使用，
         * 同时避免误操作导致内存持续增长。
         */
        const val MAX_TAB_COUNT: Int = 8

        /** 单标签内允许的最大窗格数量。 */
        const val MAX_LEAF_COUNT: Int = 8

        /** 创建只含一个标签的工作区。 */
        fun initial(title: String): TabWorkspace = TabWorkspace(
            tabs = listOf(WorkspaceTab("tab-1", title, PaneLeaf("pane-1"))),
            activeIndex = 0,
            nextTabNumber = 2,
        )

        /** 用已解码的标签列表恢复工作区，当前标签索引越界时截断到最近的有效索引。 */
        fun restore(tabs: List<WorkspaceTab>, activeIndex: Int): TabWorkspace {
            require(tabs.isNotEmpty()) { "恢复的标签列表不得为空" }
            require(tabs.size <= MAX_TAB_COUNT) { "恢复的标签数量超过上限：${tabs.size}" }
            var next = 1
            for (tab in tabs) {
                val number = tab.id.substringAfterLast('-').toIntOrNull() ?: continue
                if (number >= next) {
                    next = number + 1
                }
            }
            return TabWorkspace(tabs, activeIndex.coerceIn(0, tabs.size - 1), next)
        }
    }
}
