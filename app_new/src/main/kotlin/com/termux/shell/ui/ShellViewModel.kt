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
import com.termux.shell.config.SettingsSpec
import com.termux.shell.input.KeymapTable
import com.termux.shell.input.ShellAction
import com.termux.shell.pane.PaneDirection
import com.termux.shell.pane.PaneLayout
import com.termux.shell.pane.PaneLeaf
import com.termux.shell.pane.SplitOrientation
import com.termux.shell.session.SessionProvider
import com.termux.shell.session.ProfileCommandBuilder
import com.termux.shell.session.SessionEnvironment
import com.termux.shell.session.SessionId
import com.termux.shell.session.SessionProfile
import com.termux.shell.session.SessionRecord
import com.termux.shell.session.SessionRegistry
import com.termux.shell.tabs.TabWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * 外壳窗口状态持有者。
 *
 * 目的：集中窗口内的可变更状态（工作区、界面设置、命令面板与设置页导航、键位表）与动作分发，
 * 使 Compose 界面只读取状态并回调动作。
 *
 * 分层约束：
 * - 本类型不持有 `Context`、Android 资源与进程静态单例：配置通过 [ConfigSource] 注入，
 *   新标签标题通过构造参数注入，提示通过 [ShellNotice] 事件暴露，因此可在 JVM 单元测试中直接构造；
 * - 设置写入通过 [SettingsPersister] 注入，生产实现把磁盘写入放到 IO 协程，测试实现可同步执行；
 * - 界面设置聚合为单个 [ShellSettings] 快照，重载配置时一次性替换，避免多项赋值产生中间态。
 *
 * 生命周期：作为 `ViewModel` 存活于窗口重配置期间；进程被回收后由窗口从实例状态恢复工作区。
 */
class ShellViewModel(
    private val configSource: ConfigSource,
    initialWorkspace: TabWorkspace,
    private val titleFactory: (Int) -> String,
    persister: SettingsPersister? = null,
    private val sessionHost: SessionProvider? = null,
    private val sessionEnvironment: SessionEnvironment = SessionEnvironment("/", "/"),
) : ViewModel(), ShellCommandCatalog.Actions, ShellCommandCatalog.State {

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val settingsPersister: SettingsPersister = persister ?: SettingsPersister { key, value, onFailure ->
        writeScope.launch {
            val saved = runCatching { configSource.settings().put(key, value) }.getOrDefault(false)
            if (!saved) {
                withContext(Dispatchers.Main) { onFailure() }
            }
        }
    }

    /** 界面设置快照。设置页与标题行均从此读取，重载配置后整体替换。 */
    var uiSettings: ShellSettings by mutableStateOf(ShellSettings.from(configSource.settings()))
        private set

    var workspace: TabWorkspace by mutableStateOf(initialWorkspace)
        private set

    init {
        // 恢复的标签可能引用已随进程结束的会话：为每个叶子补齐会话，失效标识由宿主校验后重建。
        sessionHost?.let { workspace = bindLeafSessions(workspace) }
    }

    /** 当前键位表。重载配置后更新，窗口据此刷新输入路由。 */
    var keymap: KeymapTable by mutableStateOf(configSource.keymap())
        private set

    var paletteVisible: Boolean by mutableStateOf(false)
        private set

    var settingsVisible: Boolean by mutableStateOf(false)
        private set

    /** 手机端会话抽屉。宽屏不显示抽屉，会话列表由侧栏常驻。 */
    var sessionDrawerVisible: Boolean by mutableStateOf(false)
        private set

    /** 一次性提示。界面读取后调用 [consumeNotice] 清除。 */
    var notice: ShellNotice? by mutableStateOf(null)
        private set

    /** 新建窗口请求令牌。数值变化表示存在一次待处理请求。 */
    // 使用装箱状态而非 mutableIntStateOf：后者在纯 JVM 单元测试中依赖 Android Parcel 实现。
    var newWindowRequestToken: Int by mutableStateOf(0)
        private set

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
    val profiles: StateFlow<List<SessionProfile>> = configSource.profiles().profiles

    /** 按标识查找会话配置；不存在时返回 null。 */
    fun profileById(profileId: String?): SessionProfile? =
        profileId?.let { id -> profiles.value.firstOrNull { it.id == id } }

    /** 进程级会话宿主；终端视图池据此取得会话。单元测试中可能为空。 */
    val terminalHost: SessionProvider?
        get() = sessionHost

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

    fun selectTab(index: Int) {
        workspace.select(index)?.let { workspace = it }
    }

    fun closeTabAt(index: Int) {
        val closingSessionIds = workspace.tabs.getOrNull(index)?.leaves
            ?.mapNotNull { it.sessionId }
            .orEmpty()
        val closed = workspace.closeTab(index)
        if (closed == null) {
            publishNotice(ShellNotice.TAB_CLOSE_DENIED)
            return
        }
        workspace = closed
        closingSessionIds.forEach { closeSessionById(it) }
    }

    fun moveTabBy(offset: Int) {
        workspace.moveTab(workspace.activeIndex, workspace.activeIndex + offset)?.let { workspace = it }
    }

    fun focusLeaf(leafId: String) {
        workspace.focusLeaf(leafId)?.let { workspace = it }
    }

    /**
     * 按会话标识聚焦所在窗格。
     *
     * 说明：终端视图获得焦点时只知道自己挂载的会话，因此需要反查标签与窗格并切换过去。
     */
    fun focusLeafBySessionId(sessionId: String) {
        val tabIndex = workspace.tabs.indexOfFirst { tab ->
            tab.leaves.any { it.sessionId == sessionId }
        }
        if (tabIndex < 0) {
            return
        }
        val leaf = workspace.tabs[tabIndex].leaves.firstOrNull { it.sessionId == sessionId } ?: return
        var next = workspace
        next.select(tabIndex)?.let { next = it }
        next.focusLeaf(leaf.id)?.let { next = it }
        workspace = next
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
            publishNotice(ShellNotice.SESSION_ACTION_UNAVAILABLE)
            return false
        }
        val tabIndex = workspace.tabs.indexOfFirst { it.id == tabId }
        if (tabIndex < 0) {
            return false
        }
        var next = workspace
        next.select(tabIndex)?.let { next = it }
        next.focusLeaf(leafId)?.let { next = it }
        workspace = next
        sessionDrawerVisible = false
        return true
    }

    // 设置写入：界面先得到新状态，落盘由 SettingsPersister 在后台完成。

    fun updateThemeMode(value: String) {
        updateSetting(SettingsSpec.KEY_APP_THEME, value) { it.copy(themeMode = value) }
    }

    fun updateLayoutMode(value: String) {
        updateSetting(SettingsSpec.KEY_LAYOUT_MODE, value) { it.copy(layoutMode = value) }
    }

    fun updateSessionRailVisible(visible: Boolean) {
        updateSetting(SettingsSpec.KEY_SESSION_RAIL_VISIBLE, visible.toString()) {
            it.copy(sessionRailVisible = visible)
        }
    }

    /** 切换会话列表显隐；宽屏下侧栏与标题栏按钮共用本方法。 */
    fun toggleSessionRail() {
        updateSessionRailVisible(!sessionRailVisible)
    }

    fun updateFullscreen(enabled: Boolean) {
        updateSetting(SettingsSpec.KEY_FULLSCREEN, enabled.toString()) { it.copy(fullscreen = enabled) }
    }

    fun setTabBarVisible(visible: Boolean) {
        updateSetting(SettingsSpec.KEY_TAB_BAR_VISIBLE, visible.toString()) {
            it.copy(viewOptions = it.viewOptions.withTabBarVisible(visible))
        }
    }

    fun setTabBarPosition(position: String) {
        updateSetting(SettingsSpec.KEY_TAB_BAR_POSITION, position) {
            it.copy(
                viewOptions = it.viewOptions.copy(
                    tabBarAtBottom = position == SettingsSpec.TAB_POSITION_BOTTOM,
                ),
            )
        }
    }

    fun setTabCloseButtonVisible(visible: Boolean) {
        updateSetting(SettingsSpec.KEY_TAB_CLOSE_BUTTON, visible.toString()) {
            it.copy(viewOptions = it.viewOptions.copy(showTabCloseButton = visible))
        }
    }

    fun setTabWidthMode(mode: String) {
        updateSetting(SettingsSpec.KEY_TAB_WIDTH_MODE, mode) {
            it.copy(viewOptions = it.viewOptions.copy(equalTabWidth = mode != SettingsSpec.TAB_WIDTH_COMPACT))
        }
    }

    fun updateCommandPaletteEnabled(enabled: Boolean) {
        updateSetting(SettingsSpec.KEY_COMMAND_PALETTE_ENABLED, enabled.toString()) {
            it.copy(commandPaletteEnabled = enabled)
        }
        if (!enabled) {
            paletteVisible = false
        }
    }

    fun updateKeymapEnabled(enabled: Boolean) {
        updateSetting(SettingsSpec.KEY_KEYMAP_ENABLED, enabled.toString()) {
            it.copy(keymapEnabled = enabled)
        }
    }

    override fun newTab() {
        newTab(null)
    }

    /**
     * 新建标签。
     *
     * @param profile 启动主会话所用的会话配置；为空表示使用默认 shell。
     * 分屏产生的附加会话沿用同一配置，启动逻辑由会话宿主在 M14 之后实现。
     */
    fun newTab(profile: SessionProfile?) {
        val title = profile?.name ?: titleFactory(workspace.nextTabNumber)
        workspace.newTab(title, profile?.id)?.let { workspace = bindLeafSessions(it) }
            ?: publishNotice(ShellNotice.TAB_LIMIT)
    }

    /** 按配置标识新建标签；标识不存在时回退为默认 shell。 */
    fun newTabWithProfile(profileId: String) {
        newTab(profileById(profileId))
    }

    /**
     * 保存会话配置。
     *
     * 说明：写入在 IO 线程完成；成功后同步改名使用该配置的标签，避免列表与标签标题不一致。
     */
    fun saveProfile(profile: SessionProfile) {
        writeScope.launch {
            val saved = runCatching { configSource.profiles().save(profile) }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                if (!saved) {
                    publishNotice(ShellNotice.PROFILE_SAVE_FAILED)
                } else {
                    workspace = workspace.copy(
                        tabs = workspace.tabs.map { tab ->
                            if (tab.profileId == profile.id) tab.copy(title = profile.name) else tab
                        },
                    )
                }
            }
        }
    }

    /**
     * 删除会话配置。
     *
     * 说明：使用该配置的标签保留标题，但清除绑定，避免后续启动时引用不存在的配置。
     */
    fun deleteProfile(profileId: String) {
        writeScope.launch {
            val deleted = runCatching { configSource.profiles().delete(profileId) }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                if (!deleted) {
                    publishNotice(ShellNotice.PROFILE_DELETE_FAILED)
                } else {
                    workspace = workspace.copy(
                        tabs = workspace.tabs.map { tab ->
                            if (tab.profileId == profileId) tab.copy(profileId = null) else tab
                        },
                    )
                }
            }
        }
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
        val closingSessionId = workspace.activeTab.focusedLeaf.sessionId
        val closed = workspace.closeFocusedPane()
        if (closed == null) {
            publishNotice(ShellNotice.PANE_CLOSE_DENIED)
            return
        }
        workspace = closed
        closingSessionId?.let { closeSessionById(it) }
    }

    override fun focusNextPane() {
        workspace.focusRelative(1)?.let { workspace = it }
    }

    override fun focusPreviousPane() {
        workspace.focusRelative(-1)?.let { workspace = it }
    }

    /** 按方向聚焦相邻窗格；该方向没有相邻窗格时保持焦点不变。 */
    override fun focusPane(direction: PaneDirection) {
        workspace.focusDirection(direction)?.let { workspace = it }
    }

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

    /** 切换标签栏显隐。该开关是会话内的临时状态，不写入配置文件。 */
    override fun toggleTabBar() {
        uiSettings = uiSettings.copy(viewOptions = viewOptions.withTabBarVisible(!viewOptions.tabBarVisible))
    }

    override fun reloadConfig() {
        if (!configSource.reload()) {
            publishNotice(ShellNotice.CONFIG_RELOAD_FAILED)
            return
        }
        uiSettings = ShellSettings.from(configSource.settings())
        keymap = configSource.keymap()
        if (!commandPaletteEnabled) {
            paletteVisible = false
        }
        publishNotice(ShellNotice.CONFIG_RELOADED)
    }

    override fun canCloseTab(): Boolean = workspace.size > 1

    override fun canSplitPane(): Boolean = workspace.activeTab.leaves.size < TabWorkspace.MAX_LEAF_COUNT

    override fun canClosePane(): Boolean = workspace.activeTab.leaves.size > 1

    override fun onCleared() {
        writeScope.cancel()
        super.onCleared()
    }

    private fun split(orientation: SplitOrientation) {
        val (newWorkspace, newLeaf) = workspace.splitFocused(orientation)
        if (newLeaf == null) {
            publishNotice(ShellNotice.PANE_LIMIT)
            return
        }
        workspace = bindLeafSessions(newWorkspace)
    }

    /** 为尚未绑定或绑定已失效的叶子创建会话，其余叶子保持原绑定。 */
    private fun bindLeafSessions(workspace: TabWorkspace): TabWorkspace {
        val host = sessionHost ?: return workspace
        return workspace.copy(
            tabs = workspace.tabs.map { tab ->
                tab.copy(
                    root = PaneLayout.mapLeaves(tab.root) { leaf ->
                        ensureLeafSession(host, leaf, tab.profileId)
                    },
                )
            },
        )
    }

    private fun ensureLeafSession(host: SessionProvider, leaf: PaneLeaf, profileId: String?): PaneLeaf {
        val existing = leaf.sessionId
        if (existing != null && host.describe(SessionId.of(existing)) != null) {
            return leaf
        }
        val request = ProfileCommandBuilder.build(profileById(profileId), sessionEnvironment)
        val sessionId = runCatching { host.createSession(request) }.getOrNull() ?: return leaf
        return leaf.copy(sessionId = sessionId.value)
    }

    private fun closeSessionById(sessionId: String) {
        val host = sessionHost ?: return
        runCatching { host.closeSession(SessionId.of(sessionId)) }
    }

    /**
     * 先应用内存状态，再异步写入配置文件。
     *
     * 约束：写入失败时保留乐观更新的界面状态并提示用户，避免界面与操作结果反复跳动。
     */
    private fun updateSetting(key: String, value: String, transform: (ShellSettings) -> ShellSettings) {
        uiSettings = transform(uiSettings)
        settingsPersister.persist(key, value) { publishNotice(ShellNotice.SETTINGS_SAVE_FAILED) }
    }

    private fun publishNotice(notice: ShellNotice) {
        this.notice = notice
    }

    /**
     * 设置落盘抽象。
     *
     * 生产实现把磁盘写入放到 IO 协程，并在失败时回到主线程回调；单元测试可传入同步实现。
     */
    fun interface SettingsPersister {
        fun persist(key: String, value: String, onFailure: () -> Unit)
    }

    /**
     * 视图模型工厂。
     *
     * 说明：构造参数包含应用级配置来源、初始工作区与标题工厂；标题工厂使用应用资源，
     * 不持有窗口引用。窗口重配置时不会重建，进程回收后由窗口从实例状态重新构造。
     */
    class Factory(
        private val application: Application,
        private val initialWorkspace: TabWorkspace,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShellViewModel(
                configSource = ApplicationConfigSource(application),
                initialWorkspace = initialWorkspace,
                titleFactory = { index -> application.getString(R.string.shell_tab_title, index) },
                sessionHost = com.termux.shell.ShellApplication.sessions(),
                sessionEnvironment = com.termux.shell.ShellApplication.sessionEnvironment(),
            ) as T
        }
    }
}
