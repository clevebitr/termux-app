package com.termux.shell.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.termux.shell.terminal.TerminalViewPool
import com.termux.shell.ui.AdaptiveLayout
import com.termux.shell.ui.ShellViewModel
import com.termux.shell.ui.WindowWidthClass
import com.termux.shell.ui.pane.PaneArea
import com.termux.shell.ui.session.CompactRailWidth
import com.termux.shell.ui.session.DrawerWidth
import com.termux.shell.ui.session.ExpandedRailWidth
import com.termux.shell.ui.session.SessionList
import com.termux.shell.ui.tabs.TabStripBar
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

/** 主界面内容：按布局档位选择单栏或双栏形态。 */
@Composable
internal fun ShellContent(viewModel: ShellViewModel, terminalPool: TerminalViewPool?) {
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
internal fun Modifier.consumePointerEvents(): Modifier =
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
internal fun CompactShell(viewModel: ShellViewModel, terminalPool: TerminalViewPool?) {
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
internal fun WideShell(
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

