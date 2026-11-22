package com.termux.shell.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import com.termux.shell.ui.ShellViewModel

/**
 * 标题行。
 *
 * 行为：紧凑档只显示当前标签标题与必要按钮，并把会话抽屉入口放在最左；宽屏额外显示窗格数量。
 * 标签切换时标题交叉淡入，避免文字直接跳变。
 */
@Composable
internal fun ShellHeaderBar(viewModel: ShellViewModel, compactLayout: Boolean) {
    val tab = viewModel.workspace.activeTab
    val titleText = if (compactLayout) {
        tab.title
    } else {
        stringResource(R.string.shell_header, tab.title, tab.leaves.size)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compactLayout) {
            IconButton(onClick = viewModel::openSessionDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.shell_open_sessions))
            }
        } else {
            IconButton(onClick = viewModel::toggleSessionRail) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.command_toggle_session_rail))
            }
        }
        AnimatedContent(
            targetState = titleText,
            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
            label = "headerTitle",
            modifier = Modifier.weight(1f),
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (viewModel.commandPaletteEnabled) {
            IconButton(onClick = viewModel::openPalette) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.shell_command_palette))
            }
        }
        // 标题行固定提供窗口菜单：紧凑档不必先打开会话抽屉即可访问设置与分屏等操作。
        var menuExpanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.shell_overflow),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                WindowMenu(viewModel) { menuExpanded = false }
            }
        }
    }
}

/**
 * 会话栏顶部操作入口。
 *
 * 行为：新建标签、打开设置与窗口菜单；紧凑侧栏中由 FlowRow 自动换行。
 * 约束：按钮只在会话栏内出现，会话栏收起时通过标题栏的切换按钮恢复。
 */
@Composable
internal fun SessionRailActions(viewModel: ShellViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    IconButton(onClick = viewModel::newTab, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.shell_new_tab),
            modifier = Modifier.size(20.dp),
        )
    }
    IconButton(onClick = viewModel::openSettings, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.settings_open),
            modifier = Modifier.size(20.dp),
        )
    }
    Box {
        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.shell_overflow),
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            WindowMenu(viewModel) { menuExpanded = false }
        }
    }
}

/** 窗口菜单项。与快捷键、命令面板、设置页共用同一套动作实现。 */
@Composable
internal fun WindowMenu(viewModel: ShellViewModel, close: () -> Unit) {
    val items = listOf(
        R.string.command_new_tab to { viewModel.newTab() },
        R.string.command_split_right to { viewModel.splitRight() },
        R.string.command_split_down to { viewModel.splitDown() },
        R.string.command_close_pane to { viewModel.closePane() },
        R.string.command_toggle_tab_bar to { viewModel.toggleTabBar() },
        R.string.shell_command_palette to { viewModel.openPalette() },
        R.string.command_toggle_session_rail to { viewModel.toggleSessionRail() },
        R.string.settings_open to { viewModel.openSettings() },
        R.string.command_reload_config to { viewModel.reloadConfig() },
        R.string.command_new_window to { viewModel.newWindow() },
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

