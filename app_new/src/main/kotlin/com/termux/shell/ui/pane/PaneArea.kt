package com.termux.shell.ui.pane

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.termux.shell.pane.PaneLeaf
import com.termux.shell.pane.PaneNode
import com.termux.shell.pane.PaneSplit
import com.termux.shell.pane.SplitOrientation
import com.termux.shell.tabs.WorkspaceTab
import com.termux.shell.terminal.TerminalViewPool
import com.termux.shell.ui.ShellViewModel

/**
 * 窗格区。
 *
 * 目的：按不可变窗格树递归绘制窗格与分割条，替代原先的自绘 [android.view.View]。
 *
 * 行为：
 * - 分割节点用 [Row] 或 [Column] 加权重按比例分配空间；
 * - 分割条拖拽把位移换算为比例并通过 [ShellViewModel.setSplitRatio] 回写，比例被截断在允许区间；
 * - 已绑定会话的叶子用 `AndroidView` 承载终端视图，未绑定时显示占位内容；
 * - 点击占位内容切换焦点，终端视图获得焦点时通过视图池回调同步焦点窗格。
 *
 * 约束：终端视图不进入 Compose 合成，保持自绘与脏区增量；视图实例由 [TerminalViewPool] 复用。
 */
@Composable
fun PaneArea(
    tab: WorkspaceTab,
    controller: ShellViewModel,
    modifier: Modifier = Modifier,
    pool: TerminalViewPool? = null,
) {
    Box(modifier.padding(4.dp)) {
        PaneNodeView(
            node = tab.root,
            focusedLeafId = tab.focusedLeaf.id,
            controller = controller,
            pool = pool,
        )
    }
}

@Composable
private fun PaneNodeView(
    node: PaneNode,
    focusedLeafId: String,
    controller: ShellViewModel,
    pool: TerminalViewPool?,
) {
    when (node) {
        is PaneLeaf -> PaneLeafView(
            leaf = node,
            focused = node.id == focusedLeafId,
            controller = controller,
            pool = pool,
        )

        is PaneSplit -> PaneSplitView(
            split = node,
            focusedLeafId = focusedLeafId,
            controller = controller,
            pool = pool,
        )
    }
}

@Composable
private fun PaneSplitView(
    split: PaneSplit,
    focusedLeafId: String,
    controller: ShellViewModel,
    pool: TerminalViewPool?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        when (split.orientation) {
            SplitOrientation.HORIZONTAL -> {
                val totalPx = with(density) { maxWidth.toPx() }
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(split.ratio).fillMaxHeight()) {
                        PaneNodeView(split.first, focusedLeafId, controller, pool)
                    }
                    SplitHandle(vertical = true, ratio = split.ratio, totalPx = totalPx) {
                        controller.setSplitRatio(split.id, it)
                    }
                    Box(Modifier.weight(1f - split.ratio).fillMaxHeight()) {
                        PaneNodeView(split.second, focusedLeafId, controller, pool)
                    }
                }
            }

            SplitOrientation.VERTICAL -> {
                val totalPx = with(density) { maxHeight.toPx() }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(split.ratio).fillMaxWidth()) {
                        PaneNodeView(split.first, focusedLeafId, controller, pool)
                    }
                    SplitHandle(vertical = false, ratio = split.ratio, totalPx = totalPx) {
                        controller.setSplitRatio(split.id, it)
                    }
                    Box(Modifier.weight(1f - split.ratio).fillMaxWidth()) {
                        PaneNodeView(split.second, focusedLeafId, controller, pool)
                    }
                }
            }
        }
    }
}
