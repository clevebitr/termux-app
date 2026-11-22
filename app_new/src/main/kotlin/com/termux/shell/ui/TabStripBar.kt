package com.termux.shell.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.shell.R
import com.termux.shell.tabs.TabWorkspace

private val TabShape = RoundedCornerShape(12.dp)
private val MinTabWidth = 96.dp
private val CompactTabWidth = 160.dp

/** 标签内容行高。触摸目标不低于 48dp，满足 Material 的最小交互尺寸。 */
private val TabHeight = 48.dp

/** 关闭按钮的布局尺寸；实际触摸目标由 IconButton 扩展到 48dp。 */
private val CloseButtonSize = 32.dp

/** 关闭图标尺寸。 */
private val CloseIconSize = 16.dp

/**
 * 窗口级标签栏。
 *
 * 行为：
 * - `equal` 模式下标签平均分配可用宽度，可用宽度不足时退化为紧凑模式并允许横向滚动；
 * - `compact` 模式下标签固定宽度，按标题省略显示；
 * - 点击切换、点击关闭按钮关闭、长按弹出菜单（新建、左移、右移、关闭）；
 * - 末尾固定「新建标签」按钮。
 *
 * 约束：本组件不修改标签集合，全部变更通过 [ShellViewModel] 发起。
 */
@Composable
fun TabStripBar(
    workspace: TabWorkspace,
    options: ShellViewOptions,
    controller: ShellViewModel,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val useEqualWidth = options.equalTabWidth && MinTabWidth * workspace.size <= maxWidth
        if (useEqualWidth) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (index in workspace.tabs.indices) {
                    TabItem(
                        workspace = workspace,
                        index = index,
                        showCloseButton = options.showTabCloseButton,
                        controller = controller,
                        modifier = Modifier.weight(1f),
                    )
                }
                NewTabButton(controller)
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (index in workspace.tabs.indices) {
                    TabItem(
                        workspace = workspace,
                        index = index,
                        showCloseButton = options.showTabCloseButton,
                        controller = controller,
                        modifier = Modifier.width(CompactTabWidth),
                    )
                }
                NewTabButton(controller)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun TabItem(
    workspace: TabWorkspace,
    index: Int,
    showCloseButton: Boolean,
    controller: ShellViewModel,
    modifier: Modifier,
) {
    val tab = workspace.tabs[index]
    val active = index == workspace.activeIndex
    var menuExpanded by remember { mutableStateOf(false) }
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

    Box(modifier) {
        Surface(
            shape = TabShape,
            color = background,
            contentColor = contentColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp)
                .clip(TabShape)
                .hoverable(interactionSource)
                .pointerInput(Unit) {
                    // 指针右键与长按等价：直接打开标签菜单，避免只能长按才出现菜单。
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
                .semantics { selected = active }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = { controller.selectTab(index) },
                    onLongClick = {
                        // 长按与桌面右键等价，给出触觉反馈表明菜单由长按触发。
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuExpanded = true
                    },
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
                        .height(2.dp)
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

/** 末尾的「新建标签」按钮。使用 IconButton 以获得 48dp 触摸目标与标准涟漪反馈。 */
@Composable
private fun NewTabButton(controller: ShellViewModel) {
    IconButton(
        onClick = controller::newTab,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.shell_new_tab),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
