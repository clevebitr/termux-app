package com.termux.shell.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import com.termux.shell.R
import com.termux.shell.pane.PaneLeaf
import com.termux.shell.pane.PaneNode
import com.termux.shell.pane.PaneSplit
import com.termux.shell.pane.SplitOrientation
import com.termux.shell.tabs.WorkspaceTab
import com.termux.shell.terminal.TerminalViewPool

/**
 * 分割条的命中区宽度。
 *
 * 说明：视觉上只显示 2dp 细线，命中区扩大到 12dp，使触屏可以稳定拖动；
 * 命中区过大会挤压窗格内容，因此不采用 48dp 的通用最小交互尺寸，改为配合双击重置与无障碍动作。
 */
private val DividerThickness = 12.dp

/** 分割条细线的视觉宽度。 */
private val DividerLineThickness = 2.dp

/** 无障碍动作与双击重置时使用的比例步长。 */
private const val RatioStep = 0.05f

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
    pool: TerminalViewPool? = null,
    modifier: Modifier = Modifier,
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
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    // 分隔条默认不可见，仅保留拖拽热区；指针悬停或拖拽时显示细线提示可拖动。
    val lineColor by animateColorAsState(
        targetValue = if (hovered || dragging) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "dividerColor",
    )
    val context = LocalContext.current
    val resizeIcon = remember(context, vertical) {
        val type = if (vertical) {
            android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
        } else {
            android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
        }
        PointerIcon(android.view.PointerIcon.getSystemIcon(context, type))
    }
    val handleDescription = stringResource(
        if (vertical) R.string.pane_split_handle_vertical else R.string.pane_split_handle_horizontal,
    )
    val widerLabel = stringResource(R.string.pane_split_wider)
    val narrowerLabel = stringResource(R.string.pane_split_narrower)
    val resetLabel = stringResource(R.string.pane_split_reset)
    Box(
        Modifier
            .then(
                if (vertical) {
                    Modifier.fillMaxHeight().width(DividerThickness)
                } else {
                    Modifier.fillMaxWidth().height(DividerThickness)
                },
            )
            .hoverable(interactionSource)
            .pointerHoverIcon(resizeIcon)
            // 读屏用户无法拖动，提供三个等价动作：扩大、缩小与恢复均分。
            .semantics {
                contentDescription = handleDescription
                customActions = listOf(
                    CustomAccessibilityAction(widerLabel) {
                        onRatioChange((currentRatio + RatioStep).coerceAtMost(PaneSplit.MAX_RATIO))
                        true
                    },
                    CustomAccessibilityAction(narrowerLabel) {
                        onRatioChange((currentRatio - RatioStep).coerceAtLeast(PaneSplit.MIN_RATIO))
                        true
                    },
                    CustomAccessibilityAction(resetLabel) {
                        onRatioChange(PaneSplit.DEFAULT_RATIO)
                        true
                    },
                )
            }
            .pointerInput(totalPx) {
                if (totalPx <= 0f) {
                    return@pointerInput
                }
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { _, dragAmount ->
                    val delta = if (vertical) dragAmount.x else dragAmount.y
                    onRatioChange(currentRatio + delta / totalPx)
                }
            }
            .pointerInput(totalPx) {
                // 双击恢复均分：触屏下比精确拖动更容易命中。
                detectTapGestures(onDoubleTap = { onRatioChange(PaneSplit.DEFAULT_RATIO) })
            },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .then(
                    if (vertical) {
                        Modifier.fillMaxHeight().width(DividerLineThickness)
                    } else {
                        Modifier.fillMaxWidth().height(DividerLineThickness)
                    },
                )
                .background(lineColor),
        )
    }
}

@Composable
private fun PaneLeafView(
    leaf: PaneLeaf,
    focused: Boolean,
    controller: ShellViewModel,
    pool: TerminalViewPool?,
) {
    val sessionId = leaf.sessionId
    val terminalView = remember(sessionId, pool) {
        if (sessionId != null && pool != null) {
            runCatching { pool.obtain(sessionId) }.getOrNull()
        } else {
            null
        }
    }
    LaunchedEffect(focused, terminalView) {
        if (focused && terminalView != null && !terminalView.isFocused) {
            terminalView.requestFocus()
        }
    }

    val shape = RoundedCornerShape(8.dp)
    // 取消常驻边框：只有聚焦窗格显示强调色描边，未聚焦窗格不加线，避免分割线隐藏后仍有两道细线。
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 140),
        label = "paneBorderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 1.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 140),
    )
    val containerModifier = Modifier
        .fillMaxSize()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant, shape)
        .border(borderWidth, borderColor, shape)

    if (terminalView != null) {
        Box(containerModifier) {
            AndroidView(
                factory = {
                    // 视图由池复用，重新挂载前必须先与原父容器解绑，否则会抛「已有父容器」。
                    (terminalView.parent as? ViewGroup)?.removeView(terminalView)
                    terminalView
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view -> (view.parent as? ViewGroup)?.removeView(view) },
            )
        }
    } else {
        Box(
            containerModifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                controller.focusLeaf(leaf.id)
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.shell_pane_placeholder, leaf.id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.shell_pane_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
