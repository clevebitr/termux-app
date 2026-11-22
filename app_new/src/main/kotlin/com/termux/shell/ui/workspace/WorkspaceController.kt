package com.termux.shell.ui.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.shell.pane.PaneDirection
import com.termux.shell.pane.SplitOrientation
import com.termux.shell.session.SessionDescriptor
import com.termux.shell.session.SessionProfile
import com.termux.shell.session.SessionRecord
import com.termux.shell.session.SessionRegistry
import com.termux.shell.tabs.TabWorkspace
import com.termux.shell.ui.SessionBinder
import com.termux.shell.ui.ShellNotice

/**
 * 标签与窗格的状态持有者。
 *
 * 目的：把「工作区结构 + 结构变更动作 + 会话绑定」从窗口状态持有者中分离，
 * 使标签、窗格与分屏的规则集中在一处，并可在不涉及导航与设置的情况下单独测试。
 *
 * 行为：
 * - [workspace] 为不可变快照，任何变更都由内部方法整体替换，界面按快照重组；
 * - 关闭标签或窗格时同步结束其会话；新建或分屏时通过 [SessionBinder] 为叶子补齐会话；
 * - 动作失败（数量上限、至少保留一个标签）通过 [onNotice] 上报，不抛出异常。
 *
 * 约束：本类型只在主线程调用；不持有 `Context`、资源与视图；不负责导航与设置。
 */
class WorkspaceController(
    initial: TabWorkspace,
    private val titleFactory: (Int) -> String,
    private val binder: SessionBinder,
    private val describeSession: (String) -> SessionDescriptor?,
    private val onNotice: (ShellNotice) -> Unit,
) {

    /** 当前工作区快照。 */
    var workspace: TabWorkspace by mutableStateOf(initial)
        private set

    /** 标签数量。 */
    val size: Int
        get() = workspace.size

    /** 为初始工作区中失效的会话绑定重建会话；窗口创建时调用一次。 */
    fun bindInitialSessions() {
        workspace = binder.bind(workspace)
    }

    /** 按工作区派生会话注册表，并用宿主描述填充标题与运行状态。 */
    fun sessions(): SessionRegistry = SessionRegistry.fromWorkspace(workspace, describeSession)

    /** 选中指定标签。 */
    fun select(index: Int) {
        workspace.select(index)?.let { workspace = it }
    }

    /** 相对当前位置切换标签。 */
    fun selectRelative(offset: Int) {
        workspace.selectRelative(offset)?.let { workspace = it }
    }

    /** 关闭指定标签并结束其会话；最后一个标签不允许关闭。 */
    fun closeTabAt(index: Int) {
        val closingSessionIds = workspace.tabs.getOrNull(index)?.leaves
            ?.mapNotNull { it.sessionId }
            .orEmpty()
        val closed = workspace.closeTab(index)
        if (closed == null) {
            onNotice(ShellNotice.TAB_CLOSE_DENIED)
            return
        }
        workspace = closed
        closingSessionIds.forEach { sessionId -> binder.close(sessionId) }
    }

    /** 把标签移动到指定位置。 */
    fun moveTabTo(from: Int, to: Int) {
        workspace.moveTab(from, to)?.let { workspace = it }
    }

    /** 相对当前标签移动。 */
    fun moveTabBy(offset: Int) {
        moveTabTo(workspace.activeIndex, workspace.activeIndex + offset)
    }

    /** 聚焦指定窗格。 */
    fun focusLeaf(leafId: String) {
        workspace.focusLeaf(leafId)?.let { workspace = it }
    }

    /** 按会话标识聚焦所在窗格；终端视图获得焦点时据此同步外壳焦点。 */
    fun focusLeafBySessionId(sessionId: String) {
        val tabIndex = workspace.tabs.indexOfFirst { tab ->
            tab.leaves.any { it.sessionId == sessionId }
        }
        if (tabIndex < 0) {
            return
        }
        val leaf = workspace.tabs[tabIndex].leaves.firstOrNull { it.sessionId == sessionId } ?: return
        var next = workspace
        next.select(tabIndex)?.let { next = it }
        next.focusLeaf(leaf.id)?.let { next = it }
        workspace = next
    }

    /** 聚焦会话所在的窗格。 */
    fun focusSession(record: SessionRecord): Boolean {
        val tabId = record.tabId
        val leafId = record.leafId
        if (tabId == null || leafId == null) {
            onNotice(ShellNotice.SESSION_ACTION_UNAVAILABLE)
            return false
        }
        val tabIndex = workspace.tabs.indexOfFirst { it.id == tabId }
        if (tabIndex < 0) {
            return false
        }
        var next = workspace
        next.select(tabIndex)?.let { next = it }
        next.focusLeaf(leafId)?.let { next = it }
        workspace = next
        return true
    }

    /** 回写分割比例；越界比例由窗格模型截断。 */
    fun setSplitRatio(splitId: String, ratio: Float) {
        workspace.setRatio(splitId, ratio)?.let { workspace = it }
    }

    /** 新建标签并设为当前标签。 */
    fun newTab(profile: SessionProfile?) {
        val title = profile?.name ?: titleFactory(workspace.nextTabNumber)
        workspace.newTab(title, profile?.id)?.let { workspace = binder.bind(it) }
            ?: onNotice(ShellNotice.TAB_LIMIT)
    }

    /** 分割当前窗格。 */
    fun split(orientation: SplitOrientation) {
        val (newWorkspace, newLeaf) = workspace.splitFocused(orientation)
        if (newLeaf == null) {
            onNotice(ShellNotice.PANE_LIMIT)
            return
        }
        workspace = binder.bind(newWorkspace)
    }

    /** 关闭当前窗格并结束其会话；仅剩一个窗格时不允许关闭。 */
    fun closePane() {
        val closingSessionId = workspace.activeTab.focusedLeaf.sessionId
        val closed = workspace.closeFocusedPane()
        if (closed == null) {
            onNotice(ShellNotice.PANE_CLOSE_DENIED)
            return
        }
        workspace = closed
        closingSessionId?.let { sessionId -> binder.close(sessionId) }
    }

    /** 相对当前窗格切换焦点。 */
    fun focusRelative(offset: Int) {
        workspace.focusRelative(offset)?.let { workspace = it }
    }

    /** 按方向聚焦相邻窗格；该方向没有相邻窗格时保持焦点不变。 */
    fun focusDirection(direction: PaneDirection) {
        workspace.focusDirection(direction)?.let { workspace = it }
    }

    /** 把使用该配置的标签标题改为配置名。 */
    fun renameTabsForProfile(profile: SessionProfile) {
        workspace = workspace.copy(
            tabs = workspace.tabs.map { tab ->
                if (tab.profileId == profile.id) tab.copy(title = profile.name) else tab
            },
        )
    }

    /** 清除标签对已删除配置的绑定，标题保持不变。 */
    fun clearProfileBinding(profileId: String) {
        workspace = workspace.copy(
            tabs = workspace.tabs.map { tab ->
                if (tab.profileId == profileId) tab.copy(profileId = null) else tab
            },
        )
    }

    /** 是否允许关闭标签。 */
    fun canCloseTab(): Boolean = workspace.size > 1

    /** 是否允许继续分割。 */
    fun canSplitPane(): Boolean =
        workspace.activeTab.leaves.size < TabWorkspace.MAX_LEAF_COUNT

    /** 是否允许关闭当前窗格。 */
    fun canClosePane(): Boolean = workspace.activeTab.leaves.size > 1
}
