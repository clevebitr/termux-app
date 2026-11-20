package com.termux.shell.ui

import com.termux.shell.config.SettingsRepository
import com.termux.shell.config.SettingsSpec

/**
 * 外壳视图选项。
 *
 * 目的：把配置文件中与界面形态相关的键解析为不可变值对象，使 Compose 界面只接收已解释的选项，
 * 而不直接读取配置。
 *
 * 约束：本类型不可变；配置文件中的非法取值一律回退到 [SettingsSpec] 给出的默认值。
 */
data class ShellViewOptions(
    val tabBarVisible: Boolean,
    val tabBarAtBottom: Boolean,
    val showTabCloseButton: Boolean,
    val equalTabWidth: Boolean,
) {

    fun withTabBarVisible(visible: Boolean): ShellViewOptions = copy(tabBarVisible = visible)

    companion object {

        /**
         * 按配置文件构造视图选项。
         *
         * @param settings 配置来源。
         * @param tabBarVisible 标签栏是否可见。该值可能被运行时切换覆盖，因此由调用方显式传入。
         */
        fun fromSettings(settings: SettingsRepository, tabBarVisible: Boolean): ShellViewOptions {
            val atBottom = SettingsSpec.TAB_POSITION_BOTTOM ==
                settings.getString(SettingsSpec.KEY_TAB_BAR_POSITION, SettingsSpec.TAB_POSITION_TOP)
            val showClose = settings.getBoolean(SettingsSpec.KEY_TAB_CLOSE_BUTTON, true)
            val widthMode = settings.getString(SettingsSpec.KEY_TAB_WIDTH_MODE, SettingsSpec.TAB_WIDTH_EQUAL)
            return ShellViewOptions(
                tabBarVisible = tabBarVisible,
                tabBarAtBottom = atBottom,
                showTabCloseButton = showClose,
                equalTabWidth = SettingsSpec.TAB_WIDTH_COMPACT != widthMode,
            )
        }
    }
}
