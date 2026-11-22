package com.termux.shell.ui

import com.termux.shell.config.ConfigSource
import com.termux.shell.config.MemorySettingsRepository
import com.termux.shell.config.SettingsRepository
import com.termux.shell.config.SettingsSpec
import com.termux.shell.input.KeymapTable
import com.termux.shell.session.SessionRecord
import com.termux.shell.session.SessionState
import com.termux.shell.tabs.TabWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ShellViewModel] 的状态变更、设置写入与一次性事件测试。 */
class ShellViewModelTest {

    private class FakeConfigSource : ConfigSource {

        val repository: SettingsRepository = MemorySettingsRepository()

        var keymapTable: KeymapTable = KeymapTable.parse("ctrl+shift+t = new-tab")

        var reloadResult: Boolean = true

        var reloadCount: Int = 0

        override fun settings(): SettingsRepository = repository

        override fun keymap(): KeymapTable = keymapTable

        override fun reload(): Boolean {
            reloadCount++
            return reloadResult
        }

        override val configFilePath: String = "/tmp/shell.properties"

        override val keymapFilePath: String = "/tmp/keymap.properties"
    }

    private class FakePersister : ShellViewModel.SettingsPersister {

        val writes: MutableList<Pair<String, String>> = mutableListOf()

        var failNext: Boolean = false

        override fun persist(key: String, value: String, onFailure: () -> Unit) {
            writes += key to value
            if (failNext) {
                failNext = false
                onFailure()
            }
        }
    }

    private fun newViewModel(
        config: FakeConfigSource = FakeConfigSource(),
        persister: FakePersister = FakePersister(),
    ): ShellViewModel = ShellViewModel(
        configSource = config,
        initialWorkspace = TabWorkspace.initial("one"),
        titleFactory = { index -> "Shell $index" },
        persister = persister,
    )

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
    fun settingWriteFailurePublishesNoticeAndKeepsState() {
        val persister = FakePersister().apply { failNext = true }
        val viewModel = newViewModel(persister = persister)

        viewModel.updateFullscreen(false)

        assertFalse(viewModel.fullscreen)
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
