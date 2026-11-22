package com.termux.shell.ui

import com.termux.shell.config.ConfigSource
import com.termux.shell.config.LayoutStore
import com.termux.shell.config.MemorySettingsRepository
import com.termux.shell.config.SettingsRepository
import com.termux.shell.config.ProfileStore
import com.termux.shell.config.SettingsSpec
import com.termux.shell.input.KeymapTable
import com.termux.shell.pane.PaneLeaf
import com.termux.shell.session.SessionProvider
import com.termux.shell.session.SessionDescriptor
import com.termux.shell.session.SessionEnvironment
import com.termux.shell.session.SessionHost
import com.termux.shell.session.SessionId
import com.termux.shell.session.SessionProfile
import com.termux.shell.session.SessionRecord
import com.termux.shell.terminal.TerminalSession
import com.termux.shell.termview.TerminalView
import com.termux.shell.session.SessionState
import com.termux.shell.tabs.TabWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [ShellViewModel] 的状态变更、设置写入与一次性事件测试。 */
class ShellViewModelTest {

    @Test
    fun newTabUsesTitleFactoryAndSelectsIt() {
        val viewModel = newViewModel()
        viewModel.newTab()

        assertEquals(2, viewModel.workspace.size)
        assertEquals("Shell 2", viewModel.workspace.activeTab.title)
        assertNull(viewModel.notice)
    }

    @Test
    fun newTabAtLimitPublishesNotice() {
        val viewModel = newViewModel()
        repeat(TabWorkspace.MAX_TAB_COUNT - 1) { viewModel.newTab() }
        assertEquals(TabWorkspace.MAX_TAB_COUNT, viewModel.workspace.size)

        viewModel.newTab()
        assertEquals(ShellNotice.TAB_LIMIT, viewModel.notice)
        assertEquals(TabWorkspace.MAX_TAB_COUNT, viewModel.workspace.size)

        // 一次性事件：读取后清除。
        assertEquals(ShellNotice.TAB_LIMIT, viewModel.consumeNotice())
        assertNull(viewModel.notice)
    }

    @Test
    fun closingLastTabPublishesNotice() {
        val viewModel = newViewModel()
        viewModel.closeTab()
        assertEquals(ShellNotice.TAB_CLOSE_DENIED, viewModel.notice)
        assertEquals(1, viewModel.workspace.size)
    }

    @Test
    fun splitAtLimitPublishesNotice() {
        val viewModel = newViewModel()
        repeat(TabWorkspace.MAX_LEAF_COUNT - 1) { viewModel.splitRight() }
        assertEquals(TabWorkspace.MAX_LEAF_COUNT, viewModel.workspace.activeTab.leaves.size)

        viewModel.splitDown()
        assertEquals(ShellNotice.PANE_LIMIT, viewModel.notice)
        assertEquals(TabWorkspace.MAX_LEAF_COUNT, viewModel.workspace.activeTab.leaves.size)
    }

    @Test
    fun splitThenCloseRestoresSinglePane() {
        val viewModel = newViewModel()
        viewModel.splitRight()
        assertEquals(2, viewModel.workspace.activeTab.leaves.size)

        viewModel.closePane()
        assertEquals(1, viewModel.workspace.activeTab.leaves.size)
        assertNull(viewModel.notice)
    }

    @Test
    fun toggleTabBarIsSessionLocalAndNotPersisted() {
        val persister = FakePersister()
        val viewModel = newViewModel(persister = persister)
        val before = viewModel.viewOptions.tabBarVisible

        viewModel.toggleTabBar()

        assertEquals(!before, viewModel.viewOptions.tabBarVisible)
        assertTrue(persister.writes.isEmpty())
    }

    @Test
    fun settingUpdateAppliesStateAndPersistsKey() {
        val persister = FakePersister()
        val viewModel = newViewModel(persister = persister)

        viewModel.updateThemeMode(SettingsSpec.THEME_DARK)

        assertEquals(SettingsSpec.THEME_DARK, viewModel.themeMode)
        assertEquals(listOf(SettingsSpec.KEY_APP_THEME to SettingsSpec.THEME_DARK), persister.writes)
    }

    @Test
    fun settingWriteFailureRollsBackAndPublishesNotice() {
        val persister = FakePersister().apply { failNext = true }
        val viewModel = newViewModel(persister = persister)
        assertTrue(viewModel.fullscreen)

        viewModel.updateFullscreen(false)

        // 写盘失败时回到修改前的取值，避免界面停留在一个磁盘上没有的状态。
        assertTrue(viewModel.fullscreen)
        assertEquals(ShellNotice.SETTINGS_SAVE_FAILED, viewModel.notice)
    }

    @Test
    fun settingWriteFailureKeepsLaterSuccessfulChange() {
        val persister = FakePersister().apply { failNext = true }
        val viewModel = newViewModel(persister = persister)

        viewModel.updateFullscreen(false)
        viewModel.updateThemeMode(SettingsSpec.THEME_DARK)

        // 先失败的写入不得回滚后一次成功修改。
        assertEquals(SettingsSpec.THEME_DARK, viewModel.themeMode)
        assertTrue(viewModel.fullscreen)
        assertEquals(ShellNotice.SETTINGS_SAVE_FAILED, viewModel.notice)
    }

    @Test
    fun keywordSettingsAreGroupedAndReplacedOnReload() {
        val config = FakeConfigSource()
        config.repository.put(SettingsSpec.KEY_APP_THEME, SettingsSpec.THEME_LIGHT)
        val viewModel = newViewModel(config = config)
        assertEquals(SettingsSpec.THEME_LIGHT, viewModel.themeMode)

        config.repository.put(SettingsSpec.KEY_APP_THEME, SettingsSpec.THEME_DARK)
        config.keymapTable = KeymapTable.parse("ctrl+alt+t = new-tab")
        viewModel.reloadConfig()

        assertEquals(SettingsSpec.THEME_DARK, viewModel.themeMode)
        assertEquals(1, config.reloadCount)
        assertEquals(1, viewModel.keymap.size)
        assertEquals(ShellNotice.CONFIG_RELOADED, viewModel.notice)
    }

    @Test
    fun reloadFailureKeepsPreviousSettingsAndPublishesNotice() {
        val config = FakeConfigSource().apply { reloadResult = false }
        val viewModel = newViewModel(config = config)
        viewModel.updateThemeMode(SettingsSpec.THEME_DARK)

        viewModel.reloadConfig()

        assertEquals(SettingsSpec.THEME_DARK, viewModel.themeMode)
        assertEquals(ShellNotice.CONFIG_RELOAD_FAILED, viewModel.notice)
    }

    @Test
    fun disableKeymapIsReflectedOptimistically() {
        val viewModel = newViewModel()
        assertTrue(viewModel.keymapEnabled)

        viewModel.updateKeymapEnabled(false)

        assertFalse(viewModel.keymapEnabled)
    }

    @Test
    fun newWindowRequestsRaiseToken() {
        val viewModel = newViewModel()
        assertEquals(0, viewModel.newWindowRequestToken)

        viewModel.newWindow()
        viewModel.newWindow()

        assertEquals(2, viewModel.newWindowRequestToken)
    }

    @Test
    fun focusSessionSwitchesTabAndPane() {
        val viewModel = newViewModel()
        viewModel.newTab()
        viewModel.splitRight()
        val tab = viewModel.workspace.activeTab
        val splitLeafId = tab.focusedLeaf.id
        val tabId = tab.id

        // 切回第一个标签后再聚焦第二个标签中的窗格。
        viewModel.selectTab(0)
        assertTrue(viewModel.focusSession(record(tabId, splitLeafId)))

        assertEquals(tabId, viewModel.workspace.activeTab.id)
        assertEquals(splitLeafId, viewModel.workspace.activeTab.focusedLeaf.id)
        assertFalse(viewModel.sessionDrawerVisible)
    }

    @Test
    fun focusDetachedSessionPublishesNotice() {
        val viewModel = newViewModel()
        assertFalse(viewModel.focusSession(record(tabId = null, leafId = null)))
        assertEquals(ShellNotice.SESSION_ACTION_UNAVAILABLE, viewModel.notice)
    }

    @Test
    fun newTabWithProfileUsesProfileNameAndBinding() {
        val config = FakeConfigSource()
        val profile = SessionProfile(id = "p1", name = "dev", command = "bash -l")
        config.profileStore.save(profile)
        val viewModel = newViewModel(config = config)

        viewModel.newTab(profile)

        assertEquals("dev", viewModel.workspace.activeTab.title)
        assertEquals("p1", viewModel.workspace.activeTab.profileId)
    }

    @Test
    fun titleNumberingDoesNotRepeatAfterClosingTabs() {
        val viewModel = newViewModel()
        viewModel.newTab()
        viewModel.newTab()
        val titles = viewModel.workspace.tabs.map { it.title }
        assertEquals(listOf("one", "Shell 2", "Shell 3"), titles)

        viewModel.closeTabAt(1)
        viewModel.newTab()

        val afterClose = viewModel.workspace.tabs.map { it.title }
        assertEquals(afterClose.size, afterClose.toSet().size)
        assertEquals("Shell 4", viewModel.workspace.activeTab.title)
    }

    @Test
    fun profileLookupReturnsSharedStoreEntries() {
        val config = FakeConfigSource()
        val profile = SessionProfile(id = "p1", name = "dev")
        config.profileStore.save(profile)
        val viewModel = newViewModel(config = config)

        assertEquals(profile, viewModel.profileById("p1"))
        assertNull(viewModel.profileById("missing"))
        assertNull(viewModel.profileById(null))
    }

    private class FakeSessionProvider : SessionProvider {

        private val sessions = LinkedHashMap<String, SessionDescriptor>()

        private var counter = 0

        override fun createSession(request: SessionHost.SessionRequest): SessionId {
            counter++
            val id = SessionId.of("session-$counter")
            sessions[id.value] = SessionDescriptor(
                id = id,
                title = null,
                workingDirectory = request.workingDirectory,
                pid = 100 + counter,
                running = true,
            )
            return id
        }

        override fun write(id: SessionId, data: ByteArray, offset: Int, length: Int) = Unit

        override fun resize(id: SessionId, columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) = Unit

        override fun closeSession(id: SessionId) {
            sessions.remove(id.value)
        }

        override fun describe(id: SessionId): SessionDescriptor? = sessions[id.value]

        override fun describeAll(): List<SessionDescriptor> = sessions.values.toList()

        private val observers = mutableListOf<SessionHost.Observer>()

        override fun addObserver(observer: SessionHost.Observer) {
            observers += observer
        }

        override fun removeObserver(observer: SessionHost.Observer) {
            observers -= observer
        }

        /** 模拟进程自行结束：移除描述并按契约通知观察者。 */
        fun finish(id: String) {
            sessions.remove(id)
            observers.forEach { observer -> observer.onExit(SessionId.of(id), 0) }
        }

        override fun terminalSession(id: SessionId): TerminalSession? = null

        override fun attachView(id: SessionId, view: TerminalView): Boolean = false

        override fun detachView(id: SessionId, view: TerminalView) = Unit
    }

    private fun newHost(): FakeSessionProvider = FakeSessionProvider()

    private fun newViewModelWithHost(host: SessionProvider): ShellViewModel = ShellViewModel(
        configSource = FakeConfigSource(),
        initialWorkspace = TabWorkspace.initial("one"),
        titleFactory = { index -> "Shell $index" },
        persister = FakePersister(),
        sessionHost = host,
        sessionEnvironment = SessionEnvironment(homeDirectory = "/tmp/home", tempDirectory = "/tmp/cache"),
    )

    private class RecordingLayoutStore : LayoutStore {

        private val latch = java.util.concurrent.CountDownLatch(1)

        @Volatile
        private var saved: TabWorkspace? = null

        override fun read(): TabWorkspace? = null

        override fun write(workspace: TabWorkspace): Boolean {
            saved = workspace
            latch.countDown()
            return true
        }

        fun awaitSaved(): TabWorkspace? {
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            return saved
        }
    }

    @Test
    fun persistLayoutWritesCurrentWorkspace() {
        val store = RecordingLayoutStore()
        val viewModel = ShellViewModel(
            configSource = FakeConfigSource(),
            initialWorkspace = TabWorkspace.initial("one"),
            titleFactory = { index -> "Shell $index" },
            persister = FakePersister(),
            layoutStore = store,
        )
        viewModel.newTab()

        viewModel.persistLayout()

        assertEquals(viewModel.workspace, store.awaitSaved())
    }

    @Test
    fun persistLayoutWithoutStoreIsNoOp() {
        val viewModel = newViewModel()

        viewModel.persistLayout()

        assertEquals(1, viewModel.workspace.size)
    }

    @Test
    fun sessionExitBumpsRevisionAndMarksRecordExited() {
        val host = newHost()
        val viewModel = newViewModelWithHost(host)
        val sessionId = viewModel.workspace.activeTab.focusedLeaf.sessionId
        assertNotNull(sessionId)

        val before = viewModel.sessionRevision.value
        host.finish(sessionId!!)

        assertTrue(viewModel.sessionRevision.value > before)
        val record = viewModel.sessions().records.single()
        assertEquals(SessionState.EXITED, record.state)
    }

    @Test
    fun sessionsCarryDescriptorMetadataFromHost() {
        val host = newHost()
        val viewModel = newViewModelWithHost(host)
        val sessionId = viewModel.workspace.activeTab.focusedLeaf.sessionId
        assertNotNull(sessionId)
        val descriptor = host.describe(SessionId.of(sessionId!!))
        assertNotNull(descriptor)

        val record = viewModel.sessions().records.single()

        assertEquals(SessionState.RUNNING, record.state)
        assertEquals(descriptor!!.workingDirectory, record.workingDirectory)
        assertEquals(descriptor.title, record.title)
    }

    @Test
    fun restoredWorkspaceBindsSessionsForEveryLeaf() {
        val host = newHost()
        val viewModel = newViewModelWithHost(host)

        val sessionId = viewModel.workspace.activeTab.leaves.first().sessionId
        assertNotNull(sessionId)
        assertNotNull(host.describe(SessionId.of(sessionId!!)))
    }

    @Test
    fun newTabBindsSessionForNewLeaf() {
        val host = newHost()
        val viewModel = newViewModelWithHost(host)

        viewModel.newTab()
        val sessionId = viewModel.workspace.activeTab.leaves.first().sessionId

        assertNotNull(sessionId)
        assertEquals(2, host.describeAll().size)
    }

    @Test
    fun closingTabClosesItsSessions() {
        val host = newHost()
        val viewModel = newViewModelWithHost(host)
        viewModel.newTab()
        val sessionId = viewModel.workspace.activeTab.leaves.first().sessionId
        assertNotNull(sessionId)

        viewModel.closeTabAt(1)

        assertNull(host.describe(SessionId.of(sessionId!!)))
        assertEquals(1, host.describeAll().size)
    }

    @Test
    fun splitBindsAdditionalSession() {
        val host = newHost()
        val viewModel = newViewModelWithHost(host)

        viewModel.splitRight()

        assertEquals(2, viewModel.workspace.activeTab.leaves.size)
        assertEquals(2, viewModel.workspace.activeTab.leaves.mapNotNull { it.sessionId }.toSet().size)
        assertEquals(2, host.describeAll().size)
    }

    @Test
    fun staleSessionIdFromPreviousProcessIsReplaced() {
        val host = newHost()
        val staleWorkspace = TabWorkspace.initial("one").updateActiveTab { tab ->
            tab.copy(root = PaneLeaf(id = "pane-1", sessionId = "dead-session"))
        }
        val viewModel = ShellViewModel(
            configSource = FakeConfigSource(),
            initialWorkspace = staleWorkspace,
            titleFactory = { index -> "Shell $index" },
            persister = FakePersister(),
            sessionHost = host,
            sessionEnvironment = SessionEnvironment(homeDirectory = "/tmp/home", tempDirectory = "/tmp/cache"),
        )

        val sessionId = viewModel.workspace.activeTab.leaves.first().sessionId
        assertNotEquals("dead-session", sessionId)
        assertNotNull(host.describe(SessionId.of(sessionId!!)))
    }

    @Test
    fun settingsSnapshotCarriesAllConfiguredValues() {
        val config = FakeConfigSource()
        config.repository.put(SettingsSpec.KEY_LAYOUT_MODE, SettingsSpec.LAYOUT_TABLET)
        config.repository.put(SettingsSpec.KEY_TAB_BAR_POSITION, SettingsSpec.TAB_POSITION_BOTTOM)
        config.repository.put(SettingsSpec.KEY_TAB_WIDTH_MODE, SettingsSpec.TAB_WIDTH_COMPACT)
        config.repository.put(SettingsSpec.KEY_FULLSCREEN, "false")
        val viewModel = newViewModel(config = config)

        assertEquals(SettingsSpec.LAYOUT_TABLET, viewModel.layoutMode)
        assertTrue(viewModel.viewOptions.tabBarAtBottom)
        assertFalse(viewModel.viewOptions.equalTabWidth)
        assertFalse(viewModel.fullscreen)
        assertNotNull(viewModel.configFilePath)
    }

    private fun record(tabId: String?, leafId: String?): SessionRecord = SessionRecord(
        id = "session-1",
        leafId = leafId,
        tabId = tabId,
        tabTitle = "one",
        title = null,
        workingDirectory = null,
        state = SessionState.PENDING,
    )
}
