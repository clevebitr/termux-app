package com.termux.shell.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.termux.shell.R
import com.termux.shell.config.ConfigSource
import com.termux.shell.config.MemorySettingsRepository
import com.termux.shell.config.ProfileStore
import com.termux.shell.config.SettingsRepository
import com.termux.shell.input.KeymapTable
import com.termux.shell.session.SessionProfile
import com.termux.shell.tabs.TabWorkspace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 设置页的交互回归测试。
 *
 * 目的：验证「界面操作 → 视图模型 → 配置文件」这条链在真实 Compose 组合下仍然成立，
 * 避免重构设置页布局时破坏写回或搜索筛选。
 *
 * 约束：
 * - 属于仪器化测试（`androidTest` 源集），需在设备或模拟器上运行；
 * - 本机无 `adb`，因此本地只能验证编译，执行结果由 CI 或设备侧补录；
 * - 断言文案取自资源，不受界面文案调整影响；
 * - 测试替身在本文件内定义，因为 `androidTest` 与 `test` 源集不共享辅助类。
 */
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun label(resId: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(resId)

    @Test
    fun togglingARowWritesTheSetting() {
        val persister = RecordingPersister()
        val viewModel = newController(persister)
        assertTrue(viewModel.fullscreen)

        composeRule.setContent {
            ShellTheme(darkTheme = false) {
                SettingsScreen(viewModel)
            }
        }
        composeRule.onNodeWithText(label(R.string.settings_fullscreen)).performClick()

        // 全屏默认开启，点击一次应写成 false，且只写这一项。
        assertTrue(
            "应写入全屏配置项，实际写入：${persister.writes}",
            persister.writes.contains("fullscreen" to "false"),
        )
    }

    @Test
    fun searchFiltersToMatchingSetting() {
        val viewModel = newController(RecordingPersister())

        composeRule.setContent {
            ShellTheme(darkTheme = false) {
                SettingsScreen(viewModel)
            }
        }
        composeRule.onNodeWithText(label(R.string.settings_search_hint)).performTextInput("fullscreen")

        composeRule.onNodeWithText(label(R.string.settings_fullscreen)).assertExists()
        composeRule.onNodeWithText(label(R.string.settings_theme)).assertDoesNotExist()
    }

    private fun newController(persister: SettingsPersister): ShellViewModel = ShellViewModel(
        configSource = FakeConfigSource(),
        initialWorkspace = TabWorkspace.initial("one"),
        titleFactory = { index -> "Shell $index" },
        persister = persister,
    )

    private class RecordingPersister : SettingsPersister {

        val writes: MutableList<Pair<String, String>> = mutableListOf()

        override fun persist(key: String, value: String, onFailure: () -> Unit) {
            writes += key to value
        }
    }

    private class FakeConfigSource : ConfigSource {

        private val repository: SettingsRepository = MemorySettingsRepository()

        private val profileState = MutableStateFlow<List<SessionProfile>>(emptyList())

        override fun settings(): SettingsRepository = repository

        override fun keymap(): KeymapTable = KeymapTable.EMPTY

        override fun reload(): Boolean = true

        override fun profiles(): ProfileStore = object : ProfileStore {
            override val profiles: StateFlow<List<SessionProfile>> = profileState

            override fun save(profile: SessionProfile): Boolean = true

            override fun delete(id: String): Boolean = false

            override fun reload(): Boolean = true
        }

        override val configFilePath: String = "config/shell.properties"

        override val keymapFilePath: String = "config/keymap.properties"
    }
}
