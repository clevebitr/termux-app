package com.termux.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.shell.R
import com.termux.shell.tabs.TabWorkspace
import kotlin.math.roundToInt

/**
 * 外壳主界面。
 *
 * 目的：以 Compose 描述「侧栏 + 标题行 + 标签栏 + 窗格区」的三段结构与底部标签栏变体，
 * 替换原先 XML 布局与自绘视图。
 *
 * 行为：
 * - 宽度档位由 [BoxWithConstraints] 的可用宽度推导，折叠屏展开、分屏与桌面窗口下自动切换；
 * - 标签栏位置、可见性与关闭按钮由配置项与运行时选项共同决定；
 * - 窗格区按不可变窗格树递归绘制，分割条拖拽与点选聚焦回调到 [ShellController]。
 *
 * 约束：界面不持有业务状态，全部变更通过 [ShellController] 发起。
 */
@Composable
fun ShellScreen(controller: ShellController) {
    val workspace = controller.workspace
    val options = controller.viewOptions

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val widthClass = WindowWidthClass.fromWidthDp(maxWidth.value.roundToInt())
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (widthClass.hasPersistentRail) {
                SessionRail(widthClass, workspace, Modifier.fillMaxHeight())
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ShellHeaderBar(controller, Modifier.fillMaxWidth())
                if (options.tabBarVisible && !options.tabBarAtBottom) {
                    TabStripBar(workspace, options, controller, Modifier.fillMaxWidth())
                }
                PaneArea(
                    tab = workspace.activeTab,
                    controller = controller,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (options.tabBarVisible && options.tabBarAtBottom) {
                    TabStripBar(workspace, options, controller, Modifier.fillMaxWidth())
                }
            }
        }

        if (controller.paletteVisible) {
            val commands = remember { controller.commands() }
            CommandPaletteView(commands = commands, onDismiss = controller::closePalette)
        }
    }
}

/** 标题行：当前标签标题与窗格数量，以及命令面板、新建标签与窗口菜单入口。 */
@Composable
private fun ShellHeaderBar(controller: ShellController, modifier: Modifier) {
    val tab = controller.workspace.activeTab
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier.padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.shell_header, tab.title, tab.leaves.size),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (controller.commandPaletteEnabled) {
            IconButton(onClick = controller::openPalette) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.shell_command_palette))
            }
        }
        IconButton(onClick = controller::newTab) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shell_new_tab))
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.shell_overflow))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                WindowMenu(controller) { menuExpanded = false }
            }
        }
    }
}

/** 窗口菜单项。与快捷键、命令面板共用同一套动作实现。 */
@Composable
private fun WindowMenu(controller: ShellController, close: () -> Unit) {
    val items = listOf(
        R.string.command_new_tab to { controller.newTab() },
        R.string.command_split_right to { controller.splitRight() },
        R.string.command_split_down to { controller.splitDown() },
        R.string.command_close_pane to { controller.closePane() },
        R.string.command_toggle_tab_bar to { controller.toggleTabBar() },
        R.string.shell_command_palette to { controller.openPalette() },
        R.string.command_reload_config to { controller.reloadConfig() },
        R.string.command_new_window to { controller.newWindow() },
    )
    for ((titleRes, action) in items) {
        DropdownMenuItem(
            text = { Text(stringResource(titleRes)) },
            onClick = {
                close()
                action()
            },
        )
    }
}

/** 侧栏：显示标签与窗格数量摘要；会话列表在终端接入后填充。 */
@Composable
private fun SessionRail(widthClass: WindowWidthClass, workspace: TabWorkspace, modifier: Modifier) {
    val width = if (widthClass == WindowWidthClass.EXPANDED) 220.dp else 88.dp
    Column(
        modifier
            .width(width)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(R.string.shell_rail_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(
                R.string.shell_rail_summary,
                workspace.size,
                workspace.activeTab.leaves.size,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.shell_rail_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
