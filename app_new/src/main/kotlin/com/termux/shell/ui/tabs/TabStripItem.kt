package com.termux.shell.ui.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.termux.shell.R
import com.termux.shell.tabs.TabWorkspace
import com.termux.shell.ui.ShellViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

private val TabShape = RoundedCornerShape(12.dp)
private val MinTabWidth = 96.dp
private val CompactTabWidth = 160.dp

/** 标签内容行高。触摸目标不低于 48dp，满足 Material 的最小交互尺寸。 */
private val TabHeight = 48.dp

/** 关闭按钮的布局尺寸；实际触摸目标由 IconButton 扩展到 48dp。 */
private val CloseButtonSize = 32.dp

/** 关闭图标尺寸。 */
private val CloseIconSize = 16.dp

/** 末尾「新建标签」按钮占用的宽度，用于计算平均分配模式下的标签宽度。 */
private val NewTabSlotWidth = 48.dp

/** 拖拽超过标签宽度的该比例时视为发生了移动，否则按「长按后抬手」处理为打开菜单。 */
private const val DragThresholdRatio = 0.25f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TabItem(
    workspace: TabWorkspace,
    index: Int,
    showCloseButton: Boolean,
    controller: ShellViewModel,
    itemWidthPx: Float,
    modifier: Modifier,
) {
    val tab = workspace.tabs[index]
    val active = index == workspace.activeIndex
    var menuExpanded by remember { mutableStateOf(false) }
    var dragging by remember(index) { mutableStateOf(false) }
    var dragOffset by remember(index) { mutableFloatStateOf(0f) }
    var moved by remember(index) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        targetValue = when {
            active -> MaterialTheme.colorScheme.secondaryContainer
            hovered -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 140),
        label = "tabBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 140),
        label = "tabContentColor",
    )

    Box(
        modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationX = if (dragging) dragOffset else 0f
                shadowElevation = if (dragging) 8.dp.toPx() else 0f
            },
    ) {
        Surface(
            shape = TabShape,
            color = background,
            contentColor = contentColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = TabSpacing)
                .clip(TabShape)
                .hoverable(interactionSource)
                .pointerInput(index, itemWidthPx) {
                    // 指针右键与菜单入口等价，桌面端无需长按即可打开标签菜单。
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                menuExpanded = true
                                event.changes.forEach { change -> change.consume() }
                            }
                        }
                    }
                }
                .pointerInput(index, itemWidthPx) {
                    if (itemWidthPx <= 0f) {
                        return@pointerInput
                    }
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragging = true
                            dragOffset = 0f
                            moved = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.x
                            if (abs(dragOffset) > itemWidthPx * DragThresholdRatio) {
                                moved = true
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            dragOffset = 0f
                            moved = false
                        },
                        onDragEnd = {
                            val target = (index + (dragOffset / itemWidthPx).roundToInt())
                                .coerceIn(0, workspace.size - 1)
                            val shouldMove = moved && target != index
                            val openMenu = !moved
                            dragging = false
                            dragOffset = 0f
                            moved = false
                            if (shouldMove) {
                                controller.moveTabTo(index, target)
                            } else if (openMenu) {
                                // 长按后原地抬手等价于长按菜单，保留原有单手操作路径。
                                menuExpanded = true
                            }
                        },
                    )
                }
                .semantics { selected = active }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = { controller.selectTab(index) },
                ),
        ) {
            Column {
                Row(
                    Modifier
                        .heightIn(min = TabHeight)
                        .padding(
                            start = 12.dp,
                            end = if (showCloseButton) 4.dp else 12.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 标题占满剩余宽度，关闭按钮因此被推到标签右端；省略号由 maxLines/overflow 处理。
                        modifier = Modifier.weight(1f),
                    )
                    if (showCloseButton && workspace.size > 1) {
                        // 关闭按钮使用 IconButton，触摸目标由 Material 的最小交互尺寸保证为 48dp。
                        IconButton(
                            onClick = { controller.closeTabAt(index) },
                            modifier = Modifier.size(CloseButtonSize),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (active) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.command_close_tab),
                                modifier = Modifier.size(CloseIconSize),
                            )
                        }
                    }
                }
                // 选中指示条固定高度，切换标签时标签高度不跳动。
                val indicatorColor by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(durationMillis = 160),
                    label = "tabIndicator",
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 2.dp)
                        .background(indicatorColor),
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.command_new_tab)) },
                onClick = {
                    menuExpanded = false
                    controller.newTab()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.command_move_tab_left)) },
                onClick = {
                    menuExpanded = false
                    controller.moveTabBy(-1)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.command_move_tab_right)) },
                onClick = {
                    menuExpanded = false
                    controller.moveTabBy(1)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.command_close_tab)) },
                enabled = workspace.size > 1,
                onClick = {
                    menuExpanded = false
                    controller.closeTabAt(index)
                },
            )
        }
    }
}

