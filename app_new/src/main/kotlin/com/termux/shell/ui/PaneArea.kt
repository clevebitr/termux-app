package com.termux.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termux.shell.R
import com.termux.shell.pane.PaneLeaf
import com.termux.shell.pane.PaneNode
import com.termux.shell.pane.PaneSplit
import com.termux.shell.pane.SplitOrientation
import com.termux.shell.tabs.WorkspaceTab

/** 分割条宽度。命中区域与视觉宽度一致，触屏下仍可拖动。 */
private val DividerThickness = 6.dp

/**
 * 窗格区。
 *
 * 目的：按不可变窗格树递归绘制窗格与分割条，替代原先的自绘 [android.view.View]。
 *
 * 行为：
 * - 分割节点用 [Row] 或 [Column] 加权重按比例分配空间；
 * - 分割条拖拽把位移换算为比例并通过 [ShellController.setSplitRatio] 回写，比例被截断在允许区间；
 * - 点击叶子切换焦点，焦点叶子使用强调色边框。
 *
 * 约束：终端视图接入后由叶子内容改为 `AndroidView` 承载，布局与拖拽逻辑保持不变。
 */
@Composable
fun PaneArea(tab: WorkspaceTab, controller: ShellController, modifier: Modifier = Modifier) {
    Box(modifier.padding(4.dp)) {
        PaneNodeView(
            node = tab.root,
            focusedLeafId = tab.focusedLeaf.id,
            controller = controller,
        )
    }
}

@Composable
private fun PaneNodeView(node: PaneNode, focusedLeafId: String, controller: ShellController) {
    when (node) {
        is PaneLeaf -> PaneLeafView(
            leaf = node,
            focused = node.id == focusedLeafId,
            controller = controller,
        )

        is PaneSplit -> PaneSplitView(
            split = node,
            focusedLeafId = focusedLeafId,
            controller = controller,
        )
    }
}

@Composable
private fun PaneSplitView(split: PaneSplit, focusedLeafId: String, controller: ShellController) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        when (split.orientation) {
            SplitOrientation.HORIZONTAL -> {
                val totalPx = with(density) { maxWidth.toPx() }
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(split.ratio).fillMaxHeight()) {
                        PaneNodeView(split.first, focusedLeafId, controller)
                    }
                    SplitHandle(vertical = true, ratio = split.ratio, totalPx = totalPx) {
                        controller.setSplitRatio(split.id, it)
                    }
                    Box(Modifier.weight(1f - split.ratio).fillMaxHeight()) {
                        PaneNodeView(split.second, focusedLeafId, controller)
                    }
                }
            }

            SplitOrientation.VERTICAL -> {
                val totalPx = with(density) { maxHeight.toPx() }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(split.ratio).fillMaxWidth()) {
                        PaneNodeView(split.first, focusedLeafId, controller)
                    }
                    SplitHandle(vertical = false, ratio = split.ratio, totalPx = totalPx) {
                        controller.setSplitRatio(split.id, it)
                    }
                    Box(Modifier.weight(1f - split.ratio).fillMaxWidth()) {
                        PaneNodeView(split.second, focusedLeafId, controller)
                    }
                }
            }
        }
    }
}

/**
 * 分割条。
 *
 * 说明：拖拽过程中按累计位移换算比例并回写状态，因此可以持续拖动；比例截断由控制器完成。
 */
@Composable
private fun SplitHandle(
    vertical: Boolean,
    ratio: Float,
    totalPx: Float,
    onRatioChange: (Float) -> Unit,
) {
    val currentRatio by rememberUpdatedState(ratio)
    val color = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .then(
                if (vertical) {
                    Modifier.fillMaxHeight().width(DividerThickness)
                } else {
                    Modifier.fillMaxWidth().height(DividerThickness)
                },
            )
            .background(color)
            .pointerInput(totalPx) {
                if (totalPx <= 0f) {
                    return@pointerInput
                }
                detectDragGestures { _, dragAmount ->
                    val delta = if (vertical) dragAmount.x else dragAmount.y
                    onRatioChange(currentRatio + delta / totalPx)
                }
            },
    )
}

@Composable
private fun PaneLeafView(leaf: PaneLeaf, focused: Boolean, controller: ShellController) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (focused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                controller.focusLeaf(leaf.id)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.shell_pane_placeholder, leaf.id),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
