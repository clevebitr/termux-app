package com.termux.shell.tabs

import com.termux.shell.pane.PaneLayout
import com.termux.shell.pane.PaneLeaf
import com.termux.shell.pane.PaneNode

/**
 * 工作区标签。
 *
 * 目的：表达「一个标签对应一棵独立窗格树」的窗口模型，与 Windows Terminal 的标签语义一致。
 * 切换标签等价于整体替换当前窗格树，标签内部仍可继续分割为多个窗格。
 *
 * 约束：本类型不可变；[id] 在标签存活期间不得变更，并作为布局编码中的稳定键。
 */
data class WorkspaceTab(
    val id: String,
    val title: String,
    val root: PaneNode,
    val focusedLeafId: String = PaneLayout.firstLeaf(root).id,
) {

    init {
        require(id.isNotEmpty()) { "标签标识不得为空" }
        require(title.isNotEmpty()) { "标签标题不得为空" }
    }

    val leaves: List<PaneLeaf>
        get() = PaneLayout.leaves(root)

    /** 返回焦点叶子；焦点标识失效时回退到第一个叶子。 */
    val focusedLeaf: PaneLeaf
        get() = PaneLayout.findLeaf(root, focusedLeafId) ?: leaves.first()
}
