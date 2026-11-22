package com.termux.shell.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.termux.shell.R
import com.termux.shell.config.ApplicationConfigSource
import com.termux.shell.config.ConfigSource
import com.termux.shell.config.LayoutStore
import com.termux.shell.input.KeymapTable
import com.termux.shell.input.ShellAction
import com.termux.shell.pane.PaneDirection
import com.termux.shell.pane.SplitOrientation
import com.termux.shell.session.SessionEnvironment
import com.termux.shell.session.SessionHost
import com.termux.shell.session.SessionId
import com.termux.shell.session.SessionProfile
import com.termux.shell.session.SessionProvider
import com.termux.shell.session.SessionRecord
import com.termux.shell.session.SessionRegistry
import com.termux.shell.tabs.TabWorkspace
import com.termux.shell.ui.nav.ShellNavigation
import com.termux.shell.ui.nav.ShellNavigator
import com.termux.shell.ui.profiles.ProfileController
import com.termux.shell.ui.settings.SettingsController
import com.termux.shell.ui.workspace.WorkspaceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 外壳窗口的协调者。
 *
 * 目的：作为窗口唯一的注入点与动作入口，把导航、工作区、界面设置与会话配置四块状态组合起来，
 * 并暴露给 Compose 界面；具体规则分别由 [ShellNavigator]、[WorkspaceController]、
 * [SettingsController] 与 [ProfileController] 承担。
 *
 * 行为：
 * - 只读属性直接转发到对应控制器，界面读取的仍是 Compose 快照状态，重组语义不变；
 * - 动作方法做一层转发，跨控制器的联动（聚焦会话后关闭抽屉、配置改名后同步标签）在此完成；
 * - 一次性提示、键位表、新窗口请求、会话状态版本与布局写入仍由本类型持有，因为它们与窗口生命周期绑定。
 *
 * 约束：
 * - 不持有 `Context`、Android 资源与进程静态单例：配置通过 [ConfigSource] 注入，
 *   标题工厂与持久化实现可替换，因此可在 JVM 单元测试中直接构造；
 * - 界面不得到控制器上另行取值，统一走本类型的转发属性，避免出现两套入口。
 *
 * 生命周期：作为 `ViewModel` 存活于窗口重配置期间；进程被回收后由窗口从实例状态或布局文件恢复工作区。
 */
class ShellViewModel(
    private val configSource: ConfigSource,
    initialWorkspace: TabWorkspace,
    titleFactory: (Int) -> String,
    persister: SettingsPersister? = null,
    profilePersister: ProfilePersister? = null,
    private val sessionHost: SessionProvider? = null,
    sessionEnvironment: SessionEnvironment = SessionEnvironment("/", "/"),
    private val layoutStore: LayoutStore? = null,
) : ViewModel(), ShellCommandCatalog.Actions, ShellCommandCatalog.State {

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sessionRevisionState = MutableStateFlow(0L)

    /**
     * 会话状态版本。
     *
     * 目的：把「会话标题变化、进程结束或关闭」这类宿主侧事件转换为界面可观察的版本号，
     * 使会话列表无需复制一份会话状态即可重新派生。
     *
     * 约束：只表示「可能已变化」，不携带具体内容；宿主为空时永不递增。
     */
    val sessionRevision: StateFlow<Long> = sessionRevisionState

    private val sessionObserver = object : SessionHost.Observer {
        override fun onOutput(id: SessionId, data: ByteArray, length: Int) = Unit

        override fun onExit(id: SessionId, exitCode: Int) {
            sessionRevisionState.update { revision -> revision + 1 }
        }

        override fun onTitleChanged(id: SessionId, title: String?) {
            sessionRevisionState.update { revision -> revision + 1 }
        }
    }

    private val navigator = ShellNavigator()

    private val profileController: ProfileController = ProfileController(
        configSource = configSource,
        scope = writeScope,
        persister = profilePersister,
        onSaved = { profile -> workspaceController.renameTabsForProfile(profile) },
        onUnbound = { profileId -> workspaceController.clearProfileBinding(profileId) },
        onNotice = ::publishNotice,
    )

    private val settingsController: SettingsController = SettingsController(
        configSource = configSource,
        scope = writeScope,
        persister = persister,
        onWriteFailed = { publishNotice(ShellNotice.SETTINGS_SAVE_FAILED) },
        onCommandPaletteDisabled = { navigator.closePalette() },
    )

    private val sessionBinder: SessionBinder = SessionBinder(
        host = sessionHost,
        profileLookup = { profileId -> profileController.profileById(profileId) },
        environment = sessionEnvironment,
    )

    private val workspaceController: WorkspaceController = WorkspaceController(
        initial = initialWorkspace,
        titleFactory = titleFactory,
        binder = sessionBinder,
        describeSession = { sessionId -> sessionHost?.describe(SessionId.of(sessionId)) },
        onNotice = ::publishNotice,
    )

    init {
        // 恢复的标签可能引用已随进程结束的会话：为每个叶子补齐会话，失效标识由宿主校验后重建。
        sessionHost?.let { host ->
            workspaceController.bindInitialSessions()
            host.addObserver(sessionObserver)
        }
    }

    /** 当前键位表。重载配置后更新，窗口据此刷新输入路由。 */
    var keymap: KeymapTable by mutableStateOf(configSource.keymap())
        private set

    /** 一次性提示。界面读取后调用 [consumeNotice] 清除。 */
    var notice: ShellNotice? by mutableStateOf(null)
        private set

    /** 新建窗口请求令牌。数值变化表示存在一次待处理请求。 */
    // 使用装箱状态而非 mutableIntStateOf：后者在纯 JVM 单元测试中依赖 Android Parcel 实现。
    @Suppress("AutoboxingStateCreation")
    var newWindowRequestToken: Int by mutableStateOf(0)
        private set

    /** 工作区快照，转发自 [WorkspaceController]。 */
    val workspace: TabWorkspace
        get() = workspaceController.workspace

    /** 界面设置快照，转发自 [SettingsController]。 */
    val uiSettings: ShellSettings
        get() = settingsController.settings

    /** 导航状态，转发自 [ShellNavigator]。 */
    val navigation: ShellNavigation
        get() = navigator.state

    val paletteVisible: Boolean get() = navigation.paletteVisible

    val settingsVisible: Boolean get() = navigation.settingsVisible

    /** 手机端会话抽屉。宽屏不显示抽屉，会话列表由侧栏常驻。 */
    val sessionDrawerVisible: Boolean get() = navigation.sessionDrawerVisible

    /** 最近一次被删除的会话配置，供提示条的「撤销」动作恢复。 */
    val undoableProfileDelete: SessionProfile? get() = profileController.undoableDelete

    val themeMode: String get() = uiSettings.themeMode

    val layoutMode: String get() = uiSettings.layoutMode

    val sessionRailVisible: Boolean get() = uiSettings.sessionRailVisible

    val fullscreen: Boolean get() = uiSettings.fullscreen

    val commandPaletteEnabled: Boolean get() = uiSettings.commandPaletteEnabled

    val keymapEnabled: Boolean get() = uiSettings.keymapEnabled

    val viewOptions: ShellViewOptions get() = uiSettings.viewOptions

    val configFilePath: String get() = configSource.configFilePath

    val keymapFilePath: String get() = configSource.keymapFilePath

    /** 会话配置列表。所有窗口共享同一状态流，任一窗口修改后其他窗口自动刷新。 */
    val profiles: StateFlow<List<SessionProfile>> get() = profileController.profiles

    /** 按标识查找会话配置；不存在时返回 null。 */
    fun profileById(profileId: String?): SessionProfile? = profileController.profileById(profileId)

    /** 进程级会话宿主；终端视图池据此取得会话。单元测试中可能为空。 */
    val terminalHost: SessionProvider?
        get() = sessionHost

    /** 按工作区派生的会话注册表，叶子绑定的会话通过宿主查询真实状态。 */
    fun sessions(): SessionRegistry = workspaceController.sessions()

    // 导航

    fun openPalette() {
        navigator.openPalette(commandPaletteEnabled)
    }

    fun closePalette() {
        navigator.closePalette()
    }

    /** 打开设置页。设置页叠加在主界面之上，主界面不销毁。 */
    fun openSettings() {
        navigator.openSettings()
    }

    fun closeSettings() {
        navigator.closeSettings()
    }

    fun openSessionDrawer() {
        navigator.openSessionDrawer()
    }

    fun closeSessionDrawer() {
        navigator.closeSessionDrawer()
    }

    /** 抽屉已由手势或返回键关闭时的回写。 */
    fun onSessionDrawerClosed() {
        navigator.onSessionDrawerClosed()
    }

    /**
     * 返回键处置。
     *
     * 行为：先关闭覆盖层（命令面板、会话抽屉），再弹出设置页；由窗口统一调用。
     *
     * @return 是否消费本次返回键。返回 false 时窗口可结束活动。
     */
    fun navigateBack(): Boolean = navigator.back()

    /** 消费一次性提示。重复调用安全。 */
    fun consumeNotice(): ShellNotice? {
        val current = notice
        notice = null
        return current
    }

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
            ShellAction.FOCUS_PANE_LEFT -> focusPane(PaneDirection.LEFT)
            ShellAction.FOCUS_PANE_RIGHT -> focusPane(PaneDirection.RIGHT)
            ShellAction.FOCUS_PANE_UP -> focusPane(PaneDirection.UP)
            ShellAction.FOCUS_PANE_DOWN -> focusPane(PaneDirection.DOWN)
            ShellAction.NEW_WINDOW -> newWindow()
            ShellAction.COMMAND_PALETTE -> openPalette()
            ShellAction.TOGGLE_TAB_BAR -> toggleTabBar()
            ShellAction.RELOAD_CONFIG -> reloadConfig()
            else -> return false
        }
        return true
    }

    // 工作区

    fun selectTab(index: Int) {
        workspaceController.select(index)
    }

    fun closeTabAt(index: Int) {
        workspaceController.closeTabAt(index)
    }

    fun moveTabBy(offset: Int) {
        workspaceController.moveTabBy(offset)
    }

    /** 把标签移动到指定位置，供标签栏拖拽排序使用。 */
    fun moveTabTo(from: Int, to: Int) {
        workspaceController.moveTabTo(from, to)
    }

    fun focusLeaf(leafId: String) {
        workspaceController.focusLeaf(leafId)
    }

    /** 按会话标识聚焦所在窗格；终端视图获得焦点时据此同步外壳焦点。 */
    fun focusLeafBySessionId(sessionId: String) {
        workspaceController.focusLeafBySessionId(sessionId)
    }

    fun setSplitRatio(splitId: String, ratio: Float) {
        workspaceController.setSplitRatio(splitId, ratio)
    }

    /**
     * 聚焦会话所在的窗格。
     *
     * 行为：先切换到会话附着的标签，再把焦点移到对应窗格，成功后收起会话抽屉。
     *
     * @return 是否成功切换。
     */
    fun focusSession(record: SessionRecord): Boolean {
        val focused = workspaceController.focusSession(record)
        if (focused) {
            navigator.closeSessionDrawer()
        }
        return focused
    }

    override fun closeTab() {
        closeTabAt(workspace.activeIndex)
    }

    override fun nextTab() {
        workspaceController.selectRelative(1)
    }

    override fun previousTab() {
        workspaceController.selectRelative(-1)
    }

    override fun moveTabLeft() {
        moveTabBy(-1)
    }

    override fun moveTabRight() {
        moveTabBy(1)
    }

    override fun splitRight() {
        workspaceController.split(SplitOrientation.HORIZONTAL)
    }

    override fun splitDown() {
        workspaceController.split(SplitOrientation.VERTICAL)
    }

    override fun closePane() {
        workspaceController.closePane()
    }

    override fun focusNextPane() {
        workspaceController.focusRelative(1)
    }

    override fun focusPreviousPane() {
        workspaceController.focusRelative(-1)
    }

    override fun focusPane(direction: PaneDirection) {
        workspaceController.focusDirection(direction)
    }

    override fun newTab() {
        newTab(null)
    }

    /**
     * 新建标签。
     *
     * @param profile 启动主会话所用的会话配置；为空表示使用默认 shell。
     */
    fun newTab(profile: SessionProfile?) {
        workspaceController.newTab(profile)
    }

    /** 按配置标识新建标签；标识不存在时回退为默认 shell。 */
    fun newTabWithProfile(profileId: String) {
        newTab(profileById(profileId))
    }

    override fun canCloseTab(): Boolean = workspaceController.canCloseTab()

    override fun canSplitPane(): Boolean = workspaceController.canSplitPane()

    override fun canClosePane(): Boolean = workspaceController.canClosePane()

    // 设置

    fun updateThemeMode(value: String) {
        settingsController.updateThemeMode(value)
    }

    fun updateLayoutMode(value: String) {
        settingsController.updateLayoutMode(value)
    }

    fun updateSessionRailVisible(visible: Boolean) {
        settingsController.updateSessionRailVisible(visible)
    }

    /** 切换会话列表显隐；宽屏下侧栏与标题栏按钮共用本方法。 */
    fun toggleSessionRail() {
        settingsController.toggleSessionRail()
    }

    fun updateFullscreen(enabled: Boolean) {
        settingsController.updateFullscreen(enabled)
    }

    fun setTabBarVisible(visible: Boolean) {
        settingsController.setTabBarVisible(visible)
    }

    fun setTabBarPosition(position: String) {
        settingsController.setTabBarPosition(position)
    }

    fun setTabCloseButtonVisible(visible: Boolean) {
        settingsController.setTabCloseButtonVisible(visible)
    }

    fun setTabWidthMode(mode: String) {
        settingsController.setTabWidthMode(mode)
    }

    fun updateCommandPaletteEnabled(enabled: Boolean) {
        settingsController.updateCommandPaletteEnabled(enabled)
    }

    fun updateKeymapEnabled(enabled: Boolean) {
        settingsController.updateKeymapEnabled(enabled)
    }

    /** 切换标签栏显隐。该开关是会话内的临时状态，不写入配置文件。 */
    override fun toggleTabBar() {
        settingsController.toggleTabBarInSession()
    }

    override fun reloadConfig() {
        if (!configSource.reload()) {
            publishNotice(ShellNotice.CONFIG_RELOAD_FAILED)
            return
        }
        settingsController.reloadFromSource()
        keymap = configSource.keymap()
        if (!commandPaletteEnabled) {
            navigator.closePalette()
        }
        publishNotice(ShellNotice.CONFIG_RELOADED)
    }

    // 会话配置

    /** 保存会话配置；成功后同步使用该配置的标签标题。 */
    fun saveProfile(profile: SessionProfile) {
        profileController.save(profile)
    }

    /** 删除会话配置；成功后清除标签绑定并提供撤销。 */
    fun deleteProfile(profileId: String) {
        profileController.delete(profileId)
    }

    /** 撤销最近一次会话配置删除。 */
    fun undoProfileDelete() {
        profileController.undoDelete()
    }

    // 窗口

    override fun newWindow() {
        newWindowRequestToken++
    }

    /**
     * 消费新建窗口请求。
     *
     * 说明：窗口宿主在首次组合时也会执行一次副作用，因此请求必须可消费，否则窗口重建会重复创建新窗口。
     *
     * @return 是否存在待处理请求。
     */
    fun consumeNewWindowRequest(): Boolean {
        if (newWindowRequestToken == 0) {
            return false
        }
        newWindowRequestToken = 0
        return true
    }

    /**
     * 保存当前工作区结构。
     *
     * 行为：把标签、窗格树与当前标签写入布局存储，供进程被回收后恢复；写入在 IO 协程完成。
     *
     * 约束：由窗口在退到后台时调用；多个窗口共享同一布局文件，最后写入者生效。
     */
    fun persistLayout() {
        val store = layoutStore ?: return
        val snapshot = workspace
        writeScope.launch { store.write(snapshot) }
    }

    override fun onCleared() {
        sessionHost?.removeObserver(sessionObserver)
        writeScope.cancel()
        super.onCleared()
    }

    private fun publishNotice(notice: ShellNotice) {
        this.notice = notice
    }
}
