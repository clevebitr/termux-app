package com.termux.shell.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
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
import com.termux.shell.session.SessionRecord
import com.termux.shell.session.SessionRegistry
import com.termux.shell.tabs.TabWorkspace
import java.util.concurrent.Executors

/**
 * 外壳窗口控制器。
 *
 * 目的：把窗口内全部状态变更集中在单一对象，并以 Compose 状态暴露给界面。界面只调用本类型的方法，
 * 不直接修改 [TabWorkspace] 或配置文件，因此标签、窗格与会话模型都保持为不可变数据。
 *
 * 行为：
 * - 状态以 [androidx.compose.runtime.mutableStateOf] 持有，变更后自动触发重组；
 * - 设置页的修改先更新内存状态再落盘，落盘在独立单线程上完成，避免界面线程访问磁盘；
 * - 快捷键、命令面板、溢出菜单与设置页共用 [execute] 与同名的设置写入方法，保证行为一致。
 *
 * 约束：除 [ShellController] 的构造外，全部方法仅允许在主线程调用；落盘线程只访问配置仓库。
 */
class ShellController(
    private val context: Context,
    initialWorkspace: TabWorkspace,
) : CommandCatalog.Actions, CommandCatalog.State {

    private val settings get() = ShellApplication.settings()

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 单线程写入器，保证配置文件不会被并发重写。 */
    private val settingsWriter = Executors.newSingleThreadExecutor { task ->
        Thread(task, "shell-settings-writer").apply { isDaemon = true }
    }

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

    var layoutMode: String by mutableStateOf(
        settings.getString(SettingsSpec.KEY_LAYOUT_MODE, SettingsSpec.LAYOUT_AUTO) ?: SettingsSpec.LAYOUT_AUTO,
    )
        private set

    var sessionRailVisible: Boolean by mutableStateOf(
        settings.getBoolean(SettingsSpec.KEY_SESSION_RAIL_VISIBLE, true),
    )
        private set

    var fullscreen: Boolean by mutableStateOf(
        settings.getBoolean(SettingsSpec.KEY_FULLSCREEN, true),
    )
        private set

    var commandPaletteEnabled: Boolean by mutableStateOf(
        settings.getBoolean(SettingsSpec.KEY_COMMAND_PALETTE_ENABLED, true),
    )
        private set

    var keymapEnabled: Boolean by mutableStateOf(
        settings.getBoolean(SettingsSpec.KEY_KEYMAP_ENABLED, true),
    )
        private set

    var paletteVisible: Boolean by mutableStateOf(false)
        private set

    var settingsVisible: Boolean by mutableStateOf(false)
        private set

    /** 手机端会话抽屉。宽屏不显示抽屉，会话列表由侧栏常驻。 */
    var sessionDrawerVisible: Boolean by mutableStateOf(false)
        private set

    /** 新建窗口请求；窗口宿主负责创建任务。 */
    var onNewWindow: () -> Unit = {}

    /** 提示信息回调，参数为字符串资源标识。 */
    var onMessage: (Int) -> Unit = {}

    /** 外壳快捷键开关变化回调；窗口宿主据此更新输入路由。 */
    var onKeymapEnabledChanged: (Boolean) -> Unit = {}

    /** 配置文件路径，设置页「关于」分组展示。 */
    val configFilePath: String
        get() = ShellApplication.paths().settingsFile.absolutePath

    /** 键位表路径，设置页「关于」分组展示。 */
    val keymapFilePath: String
        get() = ShellApplication.paths().keymapFile.absolutePath

    /** 按工作区派生的会话注册表。会话宿主接入后，绑定关系由工作区的叶子承载。 */
    fun sessions(): SessionRegistry = SessionRegistry.fromWorkspace(workspace)

    fun openPalette() {
        if (!commandPaletteEnabled) {
            return
        }
        paletteVisible = true
    }

    fun closePalette() {
        paletteVisible = false
    }

    fun openSettings() {
        settingsVisible = true
        paletteVisible = false
    }

    fun closeSettings() {
        settingsVisible = false
    }

    fun openSessionDrawer() {
        sessionDrawerVisible = true
    }

    fun closeSessionDrawer() {
        sessionDrawerVisible = false
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

    /**
     * 聚焦会话所在的窗格。
     *
     * 行为：先切换到会话附着的标签，再把焦点移到对应窗格；脱离会话没有视口，仅关闭抽屉并提示。
     *
     * @return 是否成功切换。
     */
    fun focusSession(record: SessionRecord): Boolean {
        val tabId = record.tabId
        val leafId = record.leafId
        if (tabId == null || leafId == null) {
            onMessage(R.string.session_action_unavailable)
            return false
        }
        val tabIndex = workspace.tabs.indexOfFirst { it.id == tabId }
        if (tabIndex < 0) {
            return false
        }
        var next = workspace
        if (next.activeIndex != tabIndex) {
            next.select(tabIndex)?.let { next = it }
        }
        next.focusLeaf(leafId)?.let { next = it }
        workspace = next
        sessionDrawerVisible = false
        return true
    }

    // 设置写入。界面调用这些方法后立即得到新状态，落盘在后台线程完成。

    fun updateThemeMode(value: String) {
        persist(SettingsSpec.KEY_APP_THEME, value) { themeMode = value }
    }

    fun updateLayoutMode(value: String) {
        persist(SettingsSpec.KEY_LAYOUT_MODE, value) { layoutMode = value }
    }

    fun updateSessionRailVisible(visible: Boolean) {
        persist(SettingsSpec.KEY_SESSION_RAIL_VISIBLE, visible.toString()) { sessionRailVisible = visible }
    }

    /** 切换会话列表显隐；宽屏下侧栏与标题栏按钮共用本方法。 */
    fun toggleSessionRail() {
        updateSessionRailVisible(!sessionRailVisible)
    }

    fun updateFullscreen(enabled: Boolean) {
        persist(SettingsSpec.KEY_FULLSCREEN, enabled.toString()) { fullscreen = enabled }
    }

    fun setTabBarVisible(visible: Boolean) {
        persist(SettingsSpec.KEY_TAB_BAR_VISIBLE, visible.toString()) {
            viewOptions = viewOptions.withTabBarVisible(visible)
        }
    }

    fun setTabBarPosition(position: String) {
        persist(SettingsSpec.KEY_TAB_BAR_POSITION, position) {
            // 直接按新取值更新内存状态：配置文件写入在后台线程完成，此时仓库快照可能尚未更新。
            viewOptions = viewOptions.copy(tabBarAtBottom = position == SettingsSpec.TAB_POSITION_BOTTOM)
        }
    }

    fun setTabCloseButtonVisible(visible: Boolean) {
        persist(SettingsSpec.KEY_TAB_CLOSE_BUTTON, visible.toString()) {
            viewOptions = viewOptions.copy(showTabCloseButton = visible)
        }
    }

    fun setTabWidthMode(mode: String) {
        persist(SettingsSpec.KEY_TAB_WIDTH_MODE, mode) {
            viewOptions = viewOptions.copy(equalTabWidth = mode != SettingsSpec.TAB_WIDTH_COMPACT)
        }
    }

    fun updateCommandPaletteEnabled(enabled: Boolean) {
        persist(SettingsSpec.KEY_COMMAND_PALETTE_ENABLED, enabled.toString()) {
            commandPaletteEnabled = enabled
            if (!enabled) {
                paletteVisible = false
            }
        }
    }

    fun updateKeymapEnabled(enabled: Boolean) {
        persist(SettingsSpec.KEY_KEYMAP_ENABLED, enabled.toString()) {
            keymapEnabled = enabled
            onKeymapEnabledChanged(enabled)
        }
    }

    /** 按配置文件重建视图选项；运行时开关（标签栏显隐）以配置为准。 */
    private fun refreshViewOptions() {
        viewOptions = ShellViewOptions.fromSettings(
            settings,
            settings.getBoolean(SettingsSpec.KEY_TAB_BAR_VISIBLE, true),
        )
    }

    /**
     * 先应用内存状态，再把修改写入配置文件。
     *
     * 约束：[applyState] 在主线程执行；写入失败时通过 [onMessage] 提示，界面状态保持乐观更新的结果。
     */
    private fun persist(key: String, value: String, applyState: () -> Unit) {
        applyState()
        settingsWriter.execute {
            if (!settings.put(key, value)) {
                mainHandler.post { onMessage(R.string.settings_save_failed) }
            }
        }
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
        layoutMode = settings.getString(SettingsSpec.KEY_LAYOUT_MODE, SettingsSpec.LAYOUT_AUTO)
            ?: SettingsSpec.LAYOUT_AUTO
        sessionRailVisible = settings.getBoolean(SettingsSpec.KEY_SESSION_RAIL_VISIBLE, true)
        fullscreen = settings.getBoolean(SettingsSpec.KEY_FULLSCREEN, true)
        commandPaletteEnabled = settings.getBoolean(SettingsSpec.KEY_COMMAND_PALETTE_ENABLED, true)
        val keymap = settings.getBoolean(SettingsSpec.KEY_KEYMAP_ENABLED, true)
        if (keymap != keymapEnabled) {
            keymapEnabled = keymap
            onKeymapEnabledChanged(keymap)
        }
        refreshViewOptions()
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
