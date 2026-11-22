package com.termux.shell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.shell.R
import com.termux.shell.commands.ShellCommand
import com.termux.shell.terminal.TerminalViewPool
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

/**
 * 外壳主界面。
 *
 * 目的：以 Compose 描述「会话列表 + 标题行 + 标签栏 + 窗格区」的结构，并按窗口宽度选择手机、中等与
 * 展开三种形态，使同一份界面在手机竖屏、折叠屏与桌面窗口中都能保持合理的信息密度。
 *
 * 行为：
 * - 紧凑档：单栏布局，会话列表以抽屉承载，标题行只保留必要操作；
 * - 中等档：常驻紧凑侧栏与标签栏；展开档：常驻完整侧栏与多窗格；
 * - 设置页与主界面之间使用淡入淡出切换，标签栏显隐与侧栏收起带展开动画，标题随标签切换交叉淡入；
 * - 命令面板以覆盖层呈现（淡入 + 轻微缩放），支持返回键与输入法避让；
 * - 一次性提示以 Snackbar 呈现，替代先前的 Toast。
 *
 * 约束：界面不持有业务状态，全部变更通过 [ShellViewModel] 发起；终端视图接入后由窗格叶子以
 * `AndroidView` 承载，不进入 Compose 合成。
 */
@Composable
fun ShellScreen(viewModel: ShellViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    // 终端视图池随窗口存活：切换标签或窗格只重新挂载视图，不重建会话。
    val terminalPool = remember(context, viewModel.terminalHost) {
        viewModel.terminalHost?.let { host ->
            TerminalViewPool(context, host, viewModel::focusLeafBySessionId)
        }
    }
    LaunchedEffect(viewModel.workspace, terminalPool) {
        terminalPool?.retain(
            viewModel.workspace.tabs
                .flatMap { it.leaves }
                .mapNotNull { it.sessionId }
                .toSet(),
        )
    }
    DisposableEffect(terminalPool) {
        onDispose { terminalPool?.clear() }
    }

    LaunchedEffect(viewModel.notice) {
        val notice = viewModel.consumeNotice() ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(notice.stringRes))
    }
    // 单一返回键分派：覆盖层（命令面板、会话抽屉）优先，其次是设置页，最后交给窗口结束活动。
    BackHandler(enabled = viewModel.navigation.canGoBack) { viewModel.navigateBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // 主界面常驻：设置页以覆盖层叠加，避免销毁终端视图、窗格焦点与滚动位置。
        ShellContent(viewModel, terminalPool)

        AnimatedVisibility(
            visible = viewModel.settingsVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxSize()
                    // 遮罩自身消费指针事件，避免点击穿透到下层终端视图。
                    .consumePointerEvents(),
            ) {
                SettingsScreen(viewModel)
            }
        }

        AnimatedVisibility(
            visible = viewModel.paletteVisible,
            enter = fadeIn(tween(120)) + scaleIn(tween(150), initialScale = 0.98f),
            exit = fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.98f),
        ) {
            val commands = remember(viewModel.keymap, profiles) {
                ShellCommandCatalog.create(context, viewModel.keymap, viewModel, viewModel) +
                    profiles.map { profile ->
                        ShellCommand(
                            id = "new-profile-${profile.id}",
                            title = context.getString(R.string.command_new_profile_tab, profile.name),
                            category = context.getString(R.string.command_category_tab),
                            shortcut = null,
                            enabled = true,
                        ) { viewModel.newTab(profile) }
                    }
            }
            CommandPaletteView(commands = commands, onDismiss = viewModel::closePalette)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(16.dp),
        )
    }
}

/** 主界面内容：按布局档位选择单栏或双栏形态。 */
@Composable
private fun ShellContent(viewModel: ShellViewModel, terminalPool: TerminalViewPool?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthClass = AdaptiveLayout.widthClassFor(viewModel.layoutMode, maxWidth.value.roundToInt())
        if (widthClass == WindowWidthClass.COMPACT) {
            CompactShell(viewModel, terminalPool)
        } else {
            WideShell(viewModel, widthClass, terminalPool)
        }
    }
}

/** 遮罩层消费全部指针事件，避免点击与拖动穿透到下层的终端视图。 */
private fun Modifier.consumePointerEvents(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { change -> change.consume() }
            }
        }
    }

/** 紧凑档：单栏 + 会话抽屉。 */
@Composable
private fun CompactShell(viewModel: ShellViewModel, terminalPool: TerminalViewPool?) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sessionRevision by viewModel.sessionRevision.collectAsState()
    val sessions = remember(viewModel.workspace, sessionRevision) { viewModel.sessions() }

    LaunchedEffect(viewModel.sessionDrawerVisible) {
        if (viewModel.sessionDrawerVisible) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }
    // 手势或遮罩关闭抽屉时回写导航状态；导航状态本身不变时该回调无副作用。
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue }
            .filter { it == DrawerValue.Closed }
            .collect { viewModel.onSessionDrawerClosed() }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(DrawerWidth)) {
                SessionList(
                    registry = sessions,
                    focusedLeafId = viewModel.workspace.activeTab.focusedLeaf.id,
                    compact = false,
                    onFocus = { viewModel.focusSession(it) },
                    modifier = Modifier.fillMaxHeight(),
                    headerActions = { SessionRailActions(viewModel) },
                )
            }
        },
    ) {
        ShellBody(viewModel, compactLayout = true, pool = terminalPool)
    }
}

/** 中等与展开档：常驻侧栏 + 主栏。侧栏收起与展开带水平动画。 */
@Composable
private fun WideShell(
    viewModel: ShellViewModel,
    widthClass: WindowWidthClass,
    terminalPool: TerminalViewPool?,
) {
    val sessionRevision by viewModel.sessionRevision.collectAsState()
    val sessions = remember(viewModel.workspace, sessionRevision) { viewModel.sessions() }
    val railWidth by animateDpAsState(
        targetValue = if (widthClass == WindowWidthClass.EXPANDED) ExpandedRailWidth else CompactRailWidth,
        animationSpec = tween(durationMillis = 180),
    )
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        AnimatedVisibility(
            visible = viewModel.sessionRailVisible,
            enter = expandHorizontally(tween(180)) + fadeIn(tween(180)),
            exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120)),
        ) {
            Row(Modifier.fillMaxHeight()) {
                SessionList(
                    registry = sessions,
                    focusedLeafId = viewModel.workspace.activeTab.focusedLeaf.id,
                    compact = widthClass == WindowWidthClass.MEDIUM,
                    onFocus = { viewModel.focusSession(it) },
                    modifier = Modifier
                        .width(railWidth)
                        .fillMaxHeight(),
                    headerActions = { SessionRailActions(viewModel) },
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            ShellBody(viewModel, compactLayout = false, pool = terminalPool)
        }
    }
}

/** 主栏：标题行、标签栏与窗格区。 */
@Composable
private fun ShellBody(
    viewModel: ShellViewModel,
    compactLayout: Boolean,
    pool: TerminalViewPool?,
) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (compactLayout) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier),
    ) {
        ShellHeaderBar(viewModel, compactLayout)
        if (!viewModel.viewOptions.tabBarAtBottom) {
            TabBarSlot(viewModel, atBottom = false)
        }
        PaneArea(
            tab = viewModel.workspace.activeTab,
            controller = viewModel,
            pool = pool,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (viewModel.viewOptions.tabBarAtBottom) {
            TabBarSlot(viewModel, atBottom = true)
        }
    }
}

/** 标签栏槽位。显隐带展开/收起动画，避免开关时内容区突然跳变。 */
@Composable
private fun TabBarSlot(viewModel: ShellViewModel, atBottom: Boolean) {
    AnimatedVisibility(
        visible = viewModel.viewOptions.tabBarVisible,
        enter = fadeIn(tween(120)) + expandVertically(tween(160)),
        exit = fadeOut(tween(90)) + shrinkVertically(tween(140)),
    ) {
        TabStripBar(
            workspace = viewModel.workspace,
            options = viewModel.viewOptions,
            controller = viewModel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 标题行。
 *
 * 行为：紧凑档只显示当前标签标题与必要按钮，并把会话抽屉入口放在最左；宽屏额外显示窗格数量。
 * 标签切换时标题交叉淡入，避免文字直接跳变。
 */
@Composable
private fun ShellHeaderBar(viewModel: ShellViewModel, compactLayout: Boolean) {
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
private fun SessionRailActions(viewModel: ShellViewModel) {
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
private fun WindowMenu(viewModel: ShellViewModel, close: () -> Unit) {
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
