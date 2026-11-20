package com.termux.shell.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.shell.R
import com.termux.shell.ShellApplication
import com.termux.shell.commands.CommandCatalog
import com.termux.shell.commands.ShellCommand
import com.termux.shell.config.SettingsSpec
import com.termux.shell.input.ShellAction
import com.termux.shell.pane.SplitOrientation
import com.termux.shell.tabs.TabWorkspace

/**
 * 外壳窗口控制器。
 *
 * 目的：把窗口内全部状态变更集中在单一对象，并以 Compose 状态暴露给界面。界面只调用本类型的方法，
 * 不直接修改 [TabWorkspace]，因此标签与窗格模型可以保持为不可变数据。
 *
 * 行为：
 * - 状态以 [androidx.compose.runtime.mutableStateOf] 持有，变更后自动触发重组；
 * - 快捷键、命令面板与溢出菜单共用 [execute]，保证同一动作行为一致；
 * - 可执行状态由当前工作区推导，命令面板据此决定是否置灰；
 * - 提示信息通过 [onMessage] 交给窗口显示，本类型不直接操作界面组件。
 *
 * 约束：仅允许在主线程调用；[onNewWindow] 与 [onMessage] 由窗口在创建时注入。
 */
class ShellController(
    private val context: Context,
    initialWorkspace: TabWorkspace,
) : CommandCatalog.Actions, CommandCatalog.State {

    private val settings get() = ShellApplication.settings()

    var workspace: TabWorkspace by mutableStateOf(initialWorkspace)
        private set

    var viewOptions: ShellViewOptions by mutableStateOf(
        ShellViewOptions.fromSettings(settings, settings.getBoolean(SettingsSpec.KEY_TAB_BAR_VISIBLE, true)),
    )
        private set

    var themeMode: String by mutableStateOf(
        settings.getString(SettingsSpec.KEY_APP_THEME, SettingsSpec.THEME_SYSTEM) ?: SettingsSpec.THEME_SYSTEM,
    )
        private set

    var commandPaletteEnabled: Boolean by mutableStateOf(
        settings.getBoolean(SettingsSpec.KEY_COMMAND_PALETTE_ENABLED, true),
    )
        private set

    var paletteVisible: Boolean by mutableStateOf(false)
        private set

    /** 新建窗口请求；窗口宿主负责创建任务。 */
    var onNewWindow: () -> Unit = {}

    /** 提示信息回调，参数为字符串资源标识。 */
    var onMessage: (Int) -> Unit = {}

    fun openPalette() {
        if (!commandPaletteEnabled) {
            return
        }
        paletteVisible = true
    }

    fun closePalette() {
        paletteVisible = false
    }

    /** 构建命令清单。面板打开时调用一次，避免每次重组都重建。 */
    fun commands(): List<ShellCommand> =
        CommandCatalog.create(context, ShellApplication.keymap(), this, this)

    /**
     * 执行外壳动作。
     *
     * @return 动作是否被识别。
     */
    fun execute(action: String): Boolean {
        when (action) {
            ShellAction.NEW_TAB -> newTab()
            ShellAction.CLOSE_TAB -> closeTab()
            ShellAction.NEXT_TAB -> nextTab()
            ShellAction.PREVIOUS_TAB -> previousTab()
            ShellAction.MOVE_TAB_LEFT -> moveTabLeft()
            ShellAction.MOVE_TAB_RIGHT -> moveTabRight()
            ShellAction.SPLIT_RIGHT -> splitRight()
            ShellAction.SPLIT_DOWN -> splitDown()
            ShellAction.CLOSE_PANE -> closePane()
            ShellAction.FOCUS_NEXT_PANE -> focusNextPane()
            ShellAction.FOCUS_PREVIOUS_PANE -> focusPreviousPane()
            ShellAction.NEW_WINDOW -> newWindow()
            ShellAction.COMMAND_PALETTE -> openPalette()
            ShellAction.TOGGLE_TAB_BAR -> toggleTabBar()
            ShellAction.RELOAD_CONFIG -> reloadConfig()
            else -> return false
        }
        return true
    }

    fun selectTab(index: Int) {
        workspace.select(index)?.let { workspace = it }
    }

    fun closeTabAt(index: Int) {
        workspace.closeTab(index)?.let { workspace = it }
            ?: onMessage(R.string.shell_tab_close_denied)
    }

    fun moveTabBy(offset: Int) {
        workspace.moveTab(workspace.activeIndex, workspace.activeIndex + offset)?.let { workspace = it }
    }

    fun focusLeaf(leafId: String) {
        workspace.focusLeaf(leafId)?.let { workspace = it }
    }

    fun setSplitRatio(splitId: String, ratio: Float) {
        workspace.setRatio(splitId, ratio)?.let { workspace = it }
    }

    override fun newTab() {
        val title = context.getString(R.string.shell_tab_title, workspace.size + 1)
        workspace.newTab(title)?.let { workspace = it }
            ?: onMessage(R.string.shell_tab_limit)
    }

    override fun closeTab() {
        closeTabAt(workspace.activeIndex)
    }

    override fun nextTab() {
        workspace.selectRelative(1)?.let { workspace = it }
    }

    override fun previousTab() {
        workspace.selectRelative(-1)?.let { workspace = it }
    }

    override fun moveTabLeft() {
        moveTabBy(-1)
    }

    override fun moveTabRight() {
        moveTabBy(1)
    }

    override fun splitRight() {
        split(SplitOrientation.HORIZONTAL)
    }

    override fun splitDown() {
        split(SplitOrientation.VERTICAL)
    }

    override fun closePane() {
        workspace.closeFocusedPane()?.let { workspace = it }
            ?: onMessage(R.string.shell_pane_close_denied)
    }

    override fun focusNextPane() {
        workspace.focusRelative(1)?.let { workspace = it }
    }

    override fun focusPreviousPane() {
        workspace.focusRelative(-1)?.let { workspace = it }
    }

    override fun newWindow() {
        onNewWindow()
    }

    override fun toggleTabBar() {
        viewOptions = viewOptions.withTabBarVisible(!viewOptions.tabBarVisible)
    }

    override fun reloadConfig() {
        if (!ShellApplication.reloadConfig()) {
            onMessage(R.string.shell_config_reload_failed)
            return
        }
        themeMode = settings.getString(SettingsSpec.KEY_APP_THEME, SettingsSpec.THEME_SYSTEM)
            ?: SettingsSpec.THEME_SYSTEM
        commandPaletteEnabled = settings.getBoolean(SettingsSpec.KEY_COMMAND_PALETTE_ENABLED, true)
        viewOptions = ShellViewOptions.fromSettings(
            settings,
            settings.getBoolean(SettingsSpec.KEY_TAB_BAR_VISIBLE, true),
        )
        onMessage(R.string.shell_config_reloaded)
    }

    override fun canCloseTab(): Boolean = workspace.size > 1

    override fun canSplitPane(): Boolean = workspace.activeTab.leaves.size < TabWorkspace.MAX_LEAF_COUNT

    override fun canClosePane(): Boolean = workspace.activeTab.leaves.size > 1

    private fun split(orientation: SplitOrientation) {
        val (newWorkspace, newLeaf) = workspace.splitFocused(orientation)
        if (newLeaf == null) {
            onMessage(R.string.shell_pane_limit)
            return
        }
        workspace = newWorkspace
    }
}
