package com.termux.shell.ui

import com.termux.shell.config.ConfigSource
import com.termux.shell.config.MemorySettingsRepository
import com.termux.shell.config.ProfileStore
import com.termux.shell.config.SettingsRepository
import com.termux.shell.input.KeymapTable
import com.termux.shell.session.SessionProfile
import com.termux.shell.tabs.TabWorkspace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 界面层测试的公共替身。
 *
 * 目的：让视图模型与设置页的测试共用同一组假实现，避免每个测试文件各写一份配置来源与持久化桩。
 *
 * 约束：本文件只用于测试源集，不参与应用打包。
 */

/** 内存中的会话配置存储，行为与 [com.termux.shell.config.FileProfileStore] 的可观察部分一致。 */
internal class FakeProfileStore : ProfileStore {

    private val state = MutableStateFlow<List<SessionProfile>>(emptyList())

    override val profiles: StateFlow<List<SessionProfile>> = state

    override fun save(profile: SessionProfile): Boolean {
        state.value = state.value.filterNot { it.id == profile.id } + profile
        return true
    }

    override fun delete(id: String): Boolean {
        val before = state.value
        state.value = before.filterNot { it.id == id }
        return before.size != state.value.size
    }

    override fun reload(): Boolean = true
}

/** 假配置来源：设置写入内存，键位表与重载结果可注入。 */
internal class FakeConfigSource : ConfigSource {

    val repository: SettingsRepository = MemorySettingsRepository()

    val profileStore: FakeProfileStore = FakeProfileStore()

    var keymapTable: KeymapTable = KeymapTable.parse("ctrl+shift+t = new-tab")

    var reloadResult: Boolean = true

    var reloadCount: Int = 0

    override fun settings(): SettingsRepository = repository

    override fun keymap(): KeymapTable = keymapTable

    override fun reload(): Boolean {
        reloadCount++
        return reloadResult
    }

    override fun profiles(): ProfileStore = profileStore

    override val configFilePath: String = "config/shell.properties"

    override val keymapFilePath: String = "config/keymap.properties"
}

/** 同步执行的设置持久化桩，可注入一次失败。 */
internal class FakePersister : SettingsPersister {

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

/** 同步执行的会话配置写回桩，避免单元测试依赖主线程调度器。 */
internal val syncProfilePersister = ProfilePersister { operation, onResult -> onResult(operation()) }

/** 构造使用内存配置来源与同步持久化的视图模型。 */
internal fun newViewModel(
    config: FakeConfigSource = FakeConfigSource(),
    persister: FakePersister = FakePersister(),
    profilePersister: ProfilePersister? = syncProfilePersister,
): ShellViewModel = ShellViewModel(
    configSource = config,
    initialWorkspace = TabWorkspace.initial("one"),
    titleFactory = { index -> "Shell $index" },
    persister = persister,
    profilePersister = profilePersister,
)
