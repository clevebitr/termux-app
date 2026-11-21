package com.termux.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.roundToInt

/**
 * 外壳主界面。
 *
 * 目的：以 Compose 描述「会话列表 + 标题行 + 标签栏 + 窗格区」的结构，并按窗口宽度选择手机、中等与
 * 展开三种形态，使同一份界面在手机竖屏、折叠屏与桌面窗口中都能保持合理的信息密度。
 *
 * 行为：
 * - 紧凑档：单栏布局，会话列表以抽屉承载，标题行只保留必要操作，纵向空间优先给终端；
 * - 中等档：常驻紧凑侧栏与标签栏，标题行显示窗格数量；
 * - 展开档：常驻完整侧栏，标签栏与多窗格并排显示，操作入口与桌面习惯一致；
 * - 设置页打开时整窗切换为设置内容，命令面板作为覆盖层叠加。
 *
 * 约束：界面不持有业务状态，全部变更通过 [ShellController] 发起；终端视图接入后由窗格叶子以
 * `AndroidView` 承载，不进入 Compose 合成。
 */
@Composable
fun ShellScreen(controller: ShellController) {
    BackHandler(enabled = controller.settingsVisible) { controller.closeSettings() }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (controller.settingsVisible) {
            SettingsScreen(controller)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthClass = AdaptiveLayout.widthClassFor(controller.layoutMode, maxWidth.value.roundToInt())
                if (widthClass == WindowWidthClass.COMPACT) {
                    CompactShell(controller)
                } else {
                    WideShell(controller, widthClass)
                }
            }
        }

        if (controller.paletteVisible) {
            val commands = remember { controller.commands() }
            CommandPaletteView(commands = commands, onDismiss = controller::closePalette)
        }
    }
}

/** 紧凑档：单栏 + 会话抽屉。 */
@Composable
private fun CompactShell(controller: ShellController) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sessions = remember(controller.workspace) { controller.sessions() }

    LaunchedEffect(controller.sessionDrawerVisible) {
        if (controller.sessionDrawerVisible) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed && controller.sessionDrawerVisible) {
            controller.closeSessionDrawer()
        }
    }
    BackHandler(enabled = controller.sessionDrawerVisible) { controller.closeSessionDrawer() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(DrawerWidth)) {
                SessionList(
                    registry = sessions,
                    focusedLeafId = controller.workspace.activeTab.focusedLeaf.id,
                    compact = false,
                    onFocus = { controller.focusSession(it) },
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        },
    ) {
        ShellBody(controller, compactLayout = true)
    }
}

/** 中等与展开档：常驻侧栏 + 主栏。 */
@Composable
private fun WideShell(controller: ShellController, widthClass: WindowWidthClass) {
    val sessions = remember(controller.workspace) { controller.sessions() }
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (controller.sessionRailVisible) {
            SessionList(
                registry = sessions,
                focusedLeafId = controller.workspace.activeTab.focusedLeaf.id,
                compact = widthClass == WindowWidthClass.MEDIUM,
                onFocus = { controller.focusSession(it) },
                modifier = Modifier
                    .width(if (widthClass == WindowWidthClass.EXPANDED) ExpandedRailWidth else CompactRailWidth)
                    .fillMaxHeight(),
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            ShellBody(controller, compactLayout = false)
        }
    }
}

/** 主栏：标题行、标签栏与窗格区。 */
@Composable
private fun ShellBody(controller: ShellController, compactLayout: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (compactLayout) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier),
    ) {
        ShellHeaderBar(controller, compactLayout)
        if (controller.viewOptions.tabBarVisible && !controller.viewOptions.tabBarAtBottom) {
            TabStripBar(controller.workspace, controller.viewOptions, controller, Modifier.fillMaxWidth())
        }
        PaneArea(
            tab = controller.workspace.activeTab,
            controller = controller,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (controller.viewOptions.tabBarVisible && controller.viewOptions.tabBarAtBottom) {
            TabStripBar(controller.workspace, controller.viewOptions, controller, Modifier.fillMaxWidth())
        }
    }
}

/**
 * 标题行。
 *
 * 行为：紧凑档只显示当前标签标题与必要按钮，并把会话抽屉入口放在最左；宽屏额外显示窗格数量。
 */
@Composable
private fun ShellHeaderBar(controller: ShellController, compactLayout: Boolean) {
    val tab = controller.workspace.activeTab
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(start = if (compactLayout) 4.dp else 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compactLayout) {
            IconButton(onClick = controller::openSessionDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.shell_open_sessions))
            }
        } else {
            // 宽屏下会话列表可随时收起，收起后由本按钮重新展开。
            IconButton(onClick = controller::toggleSessionRail) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.command_toggle_session_rail))
            }
        }
        Text(
            text = if (compactLayout) {
                tab.title
            } else {
                stringResource(R.string.shell_header, tab.title, tab.leaves.size)
            },
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
        if (!compactLayout) {
            IconButton(onClick = controller::openSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_open))
            }
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

/** 窗口菜单项。与快捷键、命令面板、设置页共用同一套动作实现。 */
@Composable
private fun WindowMenu(controller: ShellController, close: () -> Unit) {
    val items = listOf(
        R.string.command_new_tab to { controller.newTab() },
        R.string.command_split_right to { controller.splitRight() },
        R.string.command_split_down to { controller.splitDown() },
        R.string.command_close_pane to { controller.closePane() },
        R.string.command_toggle_tab_bar to { controller.toggleTabBar() },
        R.string.shell_command_palette to { controller.openPalette() },
        R.string.command_toggle_session_rail to { controller.toggleSessionRail() },
        R.string.settings_open to { controller.openSettings() },
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