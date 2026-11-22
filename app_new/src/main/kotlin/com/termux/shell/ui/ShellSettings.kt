package com.termux.shell.ui

import com.termux.shell.config.SettingsRepository
import com.termux.shell.config.SettingsSpec

/**
 * 界面设置快照。
 *
 * 目的：把分散在多个配置键中的界面状态聚合为一个不可变值，使状态持有者只维护一份设置状态，
 * 重载配置时一次性替换，避免逐项赋值出现中间态。
 *
 * 行为：由 [from] 从配置仓库快照构造；设置页修改时通过 `copy` 生成新值。
 *
 * 约束：本类型不可变，不读取磁盘；[ShellViewOptions] 中的标签栏开关以配置为准。
 */
data class ShellSettings(
    val themeMode: String,
    val layoutMode: String,
    val sessionRailVisible: Boolean,
    val fullscreen: Boolean,
    val commandPaletteEnabled: Boolean,
    val keymapEnabled: Boolean,
    val viewOptions: ShellViewOptions,
) {

    companion object {

        /** 从配置仓库快照构造界面设置。非法取值由仓库层回退到默认值。 */
        fun from(settings: SettingsRepository): ShellSettings = ShellSettings(
            themeMode = settings.getString(SettingsSpec.KEY_APP_THEME, SettingsSpec.THEME_SYSTEM)
                ?: SettingsSpec.THEME_SYSTEM,
            layoutMode = settings.getString(SettingsSpec.KEY_LAYOUT_MODE, SettingsSpec.LAYOUT_AUTO)
                ?: SettingsSpec.LAYOUT_AUTO,
            sessionRailVisible = settings.getBoolean(SettingsSpec.KEY_SESSION_RAIL_VISIBLE, true),
            fullscreen = settings.getBoolean(SettingsSpec.KEY_FULLSCREEN, true),
            commandPaletteEnabled = settings.getBoolean(SettingsSpec.KEY_COMMAND_PALETTE_ENABLED, true),
            keymapEnabled = settings.getBoolean(SettingsSpec.KEY_KEYMAP_ENABLED, true),
            viewOptions = ShellViewOptions.fromSettings(
                settings,
                settings.getBoolean(SettingsSpec.KEY_TAB_BAR_VISIBLE, true),
            ),
        )
    }
}
