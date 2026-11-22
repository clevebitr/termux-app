package com.termux.shell.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.termux.shell.R
import com.termux.shell.commands.ShellCommand
import com.termux.shell.terminal.TerminalViewPool
import com.termux.shell.ui.ShellCommandCatalog
import com.termux.shell.ui.ShellNotice
import com.termux.shell.ui.stringRes
import com.termux.shell.ui.ShellViewModel
import com.termux.shell.ui.palette.CommandPaletteView
import com.termux.shell.ui.session.CompactRailWidth
import com.termux.shell.ui.session.DrawerWidth
import com.termux.shell.ui.session.ExpandedRailWidth
import com.termux.shell.ui.session.SessionList
import com.termux.shell.ui.settings.SettingsScreen

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
        val message = context.getString(notice.stringRes)
        if (notice != ShellNotice.PROFILE_DELETED) {
            snackbarHostState.showSnackbar(message)
            return@LaunchedEffect
        }
        // 删除会话配置可通过提示条撤销；撤销动作由状态持有者重新写回该配置。
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = context.getString(R.string.settings_profile_undo),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoProfileDelete()
        }
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
