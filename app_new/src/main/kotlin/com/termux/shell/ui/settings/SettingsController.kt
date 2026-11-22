package com.termux.shell.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.shell.config.ConfigSource
import com.termux.shell.config.SettingsSpec
import com.termux.shell.ui.SettingsPersister
import com.termux.shell.ui.SettingsWriter
import com.termux.shell.ui.ShellSettings
import com.termux.shell.ui.ShellViewOptions
import kotlinx.coroutines.CoroutineScope

/**
 * 界面设置的状态持有者。
 *
 * 目的：把设置快照、写回管道与回滚规则从窗口状态持有者中分离，使设置页只依赖一个对象，
 * 并让「乐观更新 + 写盘失败回滚」只在一处实现。
 *
 * 行为：
 * - [settings] 为不可变快照，重载配置时整体替换；
 * - 每个 `update*`／`set*` 方法先更新内存快照再异步写盘，写盘失败时回滚到修改前的快照；
 * - [toggleTabBarInSession] 只改动内存，用于命令面板与会话内的临时显隐；
 * - 命令面板被关闭时通过 [onCommandPaletteDisabled] 通知调用方收起覆盖层。
 *
 * 约束：本类型不访问磁盘以外的系统能力，不持有 Android 上下文；写盘线程由 [SettingsWriter] 决定。
 */
class SettingsController(
    private val configSource: ConfigSource,
    scope: CoroutineScope,
    persister: SettingsPersister?,
    private val onWriteFailed: () -> Unit,
    private val onCommandPaletteDisabled: () -> Unit,
) {

    private val writer = SettingsWriter(configSource, scope, persister)

    /** 界面设置快照。 */
    var settings: ShellSettings by mutableStateOf(ShellSettings.from(configSource.settings()))
        private set

    val viewOptions: ShellViewOptions
        get() = settings.viewOptions

    /** 重新读取配置文件并整体替换快照。 */
    fun reloadFromSource() {
        settings = ShellSettings.from(configSource.settings())
    }

    fun updateThemeMode(value: String) {
        update(SettingsSpec.KEY_APP_THEME, value) { it.copy(themeMode = value) }
    }

    fun updateLayoutMode(value: String) {
        update(SettingsSpec.KEY_LAYOUT_MODE, value) { it.copy(layoutMode = value) }
    }

    fun updateSessionRailVisible(visible: Boolean) {
        update(SettingsSpec.KEY_SESSION_RAIL_VISIBLE, visible.toString()) {
            it.copy(sessionRailVisible = visible)
        }
    }

    /** 切换常驻会话列表显隐；宽屏侧栏与标题栏按钮共用本方法。 */
    fun toggleSessionRail() {
        updateSessionRailVisible(!settings.sessionRailVisible)
    }

    fun updateFullscreen(enabled: Boolean) {
        update(SettingsSpec.KEY_FULLSCREEN, enabled.toString()) { it.copy(fullscreen = enabled) }
    }

    fun setTabBarVisible(visible: Boolean) {
        update(SettingsSpec.KEY_TAB_BAR_VISIBLE, visible.toString()) {
            it.copy(viewOptions = it.viewOptions.withTabBarVisible(visible))
        }
    }

    fun setTabBarPosition(position: String) {
        update(SettingsSpec.KEY_TAB_BAR_POSITION, position) {
            it.copy(
                viewOptions = it.viewOptions.copy(
                    tabBarAtBottom = position == SettingsSpec.TAB_POSITION_BOTTOM,
                ),
            )
        }
    }

    fun setTabCloseButtonVisible(visible: Boolean) {
        update(SettingsSpec.KEY_TAB_CLOSE_BUTTON, visible.toString()) {
            it.copy(viewOptions = it.viewOptions.copy(showTabCloseButton = visible))
        }
    }

    fun setTabWidthMode(mode: String) {
        update(SettingsSpec.KEY_TAB_WIDTH_MODE, mode) {
            it.copy(viewOptions = it.viewOptions.copy(equalTabWidth = mode != SettingsSpec.TAB_WIDTH_COMPACT))
        }
    }

    fun updateCommandPaletteEnabled(enabled: Boolean) {
        update(SettingsSpec.KEY_COMMAND_PALETTE_ENABLED, enabled.toString()) {
            it.copy(commandPaletteEnabled = enabled)
        }
        if (!enabled) {
            onCommandPaletteDisabled()
        }
    }

    fun updateKeymapEnabled(enabled: Boolean) {
        update(SettingsSpec.KEY_KEYMAP_ENABLED, enabled.toString()) {
            it.copy(keymapEnabled = enabled)
        }
    }

    /** 会话内临时切换标签栏显隐；不写回配置文件。 */
    fun toggleTabBarInSession() {
        settings = settings.copy(
            viewOptions = viewOptions.withTabBarVisible(!viewOptions.tabBarVisible),
        )
    }

    /**
     * 先应用内存状态，再异步写入配置文件。
     *
     * 行为：写入失败时回滚到修改前的快照并通知调用方，使界面与磁盘保持一致；
     * 回滚以「当前快照仍等于本次修改结果」为条件，因此期间其它成功修改不会被覆盖。
     */
    private fun update(key: String, value: String, transform: (ShellSettings) -> ShellSettings) {
        val previous = settings
        val updated = transform(previous)
        settings = updated
        writer.persist(key, value) {
            if (settings == updated) {
                settings = previous
            }
            onWriteFailed()
        }
    }
}
