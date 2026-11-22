package com.termux.shell.ui.settings

import androidx.annotation.StringRes
import com.termux.shell.R
import com.termux.shell.config.SettingsSpec
import com.termux.shell.session.SessionProfile
import com.termux.shell.ui.ShellViewModel

/**
 * 设置项展示模型。
 *
 * 目的：把「设置页有哪些项、每项取什么值、改动时调用哪个动作」从界面布局中分离，
 * 使设置页按数据渲染，新增配置项只需在 [ShellSettingsCatalog] 中登记一次。
 *
 * 约束：本类型只描述展示所需的信息，不持有 `Context` 与资源字符串；文案以资源标识表达，
 * 由界面层解析，因此可在纯 JVM 环境中构造与比对。
 */
sealed interface SettingsItem {

    /** 稳定标识，用于列表键与搜索结果的去重。 */
    val id: String

    /** 项目标题。 */
    @get:StringRes
    val titleRes: Int

    /** 项目说明；无说明时为 null。 */
    @get:StringRes
    val descriptionRes: Int?
}

/** 选项及其文案。 */
data class ChoiceOption(val value: String, @get:StringRes val labelRes: Int)

/** 开关项：整行可点，右侧以开关呈现当前取值。 */
data class ToggleItem(
    override val id: String,
    @get:StringRes override val titleRes: Int,
    @get:StringRes override val descriptionRes: Int? = null,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
) : SettingsItem

/** 选项项：以筛选项（filter chip）呈现互斥取值。 */
data class ChoiceItem(
    override val id: String,
    @get:StringRes override val titleRes: Int,
    @get:StringRes override val descriptionRes: Int? = null,
    val options: List<ChoiceOption>,
    val selected: String,
    val onSelect: (String) -> Unit,
) : SettingsItem

/** 动作项：点击后执行一次操作，例如重新加载配置文件。 */
data class ActionItem(
    override val id: String,
    @get:StringRes override val titleRes: Int,
    @get:StringRes override val descriptionRes: Int? = null,
    val onClick: () -> Unit,
) : SettingsItem

/** 信息项：只读展示，例如版本号与配置文件路径。 */
data class InfoItem(
    override val id: String,
    @get:StringRes override val titleRes: Int,
    val value: String,
) : SettingsItem {
    override val descriptionRes: Int? get() = null
}

/** 设置分组。 */
data class SettingsSection(val id: String, @get:StringRes val titleRes: Int, val items: List<SettingsItem>)

/** 设置页的显示块。会话配置不是普通配置项，因此单独作为一种块。 */
sealed interface SettingsBlock {

    /** 稳定标识，用于列表键。 */
    val key: String
}

/** 普通配置分组块。 */
data class SectionBlock(val section: SettingsSection) : SettingsBlock {
    override val key: String get() = "section-" + section.id
}

/** 会话配置块。块内内容由界面层渲染，配置数量只用于摘要显示。 */
data class ProfilesBlock(val count: Int) : SettingsBlock {
    override val key: String get() = "profiles"
}

/**
 * 设置页的数据来源。
 *
 * 目的：集中声明设置页的全部条目及其读写动作，使界面只负责呈现与搜索。
 *
 * 行为：按「外观、会话、会话配置、输入、关于」的顺序返回显示块；取值一律来自控制器的状态快照，
 * 因此外部重载配置后界面会随之刷新。
 *
 * 约束：本对象属于界面层，可引用 Android 资源标识与控制器动作；不读写磁盘、不持有状态。
 */
object ShellSettingsCatalog {

    const val SECTION_APPEARANCE = "appearance"
    const val SECTION_SESSIONS = "sessions"
    const val SECTION_INPUT = "input"
    const val SECTION_ABOUT = "about"

    /** 返回设置页的全部显示块。 */
    fun blocks(controller: ShellViewModel, profiles: List<SessionProfile>): List<SettingsBlock> = listOf(
        SectionBlock(appearance(controller)),
        SectionBlock(sessions(controller)),
        ProfilesBlock(profiles.size),
        SectionBlock(input(controller)),
        SectionBlock(about(controller)),
    )

    private fun appearance(controller: ShellViewModel): SettingsSection = SettingsSection(
        id = SECTION_APPEARANCE,
        titleRes = R.string.settings_section_appearance,
        items = listOf(
            ChoiceItem(
                id = "theme",
                titleRes = R.string.settings_theme,
                descriptionRes = R.string.settings_theme_hint,
                options = listOf(
                    ChoiceOption(SettingsSpec.THEME_SYSTEM, R.string.settings_theme_system),
                    ChoiceOption(SettingsSpec.THEME_LIGHT, R.string.settings_theme_light),
                    ChoiceOption(SettingsSpec.THEME_DARK, R.string.settings_theme_dark),
                ),
                selected = controller.themeMode,
                onSelect = controller::updateThemeMode,
            ),
            ChoiceItem(
                id = "layout-mode",
                titleRes = R.string.settings_layout_mode,
                descriptionRes = R.string.settings_layout_mode_hint,
                options = listOf(
                    ChoiceOption(SettingsSpec.LAYOUT_AUTO, R.string.settings_layout_auto),
                    ChoiceOption(SettingsSpec.LAYOUT_PHONE, R.string.settings_layout_phone),
                    ChoiceOption(SettingsSpec.LAYOUT_TABLET, R.string.settings_layout_tablet),
                ),
                selected = controller.layoutMode,
                onSelect = controller::updateLayoutMode,
            ),
            ToggleItem(
                id = "fullscreen",
                titleRes = R.string.settings_fullscreen,
                descriptionRes = R.string.settings_fullscreen_hint,
                checked = controller.fullscreen,
                onCheckedChange = controller::updateFullscreen,
            ),
            ToggleItem(
                id = "tab-bar-visible",
                titleRes = R.string.settings_tab_bar_visible,
                descriptionRes = R.string.settings_tab_bar_visible_hint,
                checked = controller.viewOptions.tabBarVisible,
                onCheckedChange = controller::setTabBarVisible,
            ),
            ChoiceItem(
                id = "tab-bar-position",
                titleRes = R.string.settings_tab_bar_position,
                options = listOf(
                    ChoiceOption(SettingsSpec.TAB_POSITION_TOP, R.string.settings_position_top),
                    ChoiceOption(SettingsSpec.TAB_POSITION_BOTTOM, R.string.settings_position_bottom),
                ),
                selected = if (controller.viewOptions.tabBarAtBottom) {
                    SettingsSpec.TAB_POSITION_BOTTOM
                } else {
                    SettingsSpec.TAB_POSITION_TOP
                },
                onSelect = controller::setTabBarPosition,
            ),
            ChoiceItem(
                id = "tab-width-mode",
                titleRes = R.string.settings_tab_width_mode,
                options = listOf(
                    ChoiceOption(SettingsSpec.TAB_WIDTH_EQUAL, R.string.settings_tab_width_equal),
                    ChoiceOption(SettingsSpec.TAB_WIDTH_COMPACT, R.string.settings_tab_width_compact),
                ),
                selected = if (controller.viewOptions.equalTabWidth) {
                    SettingsSpec.TAB_WIDTH_EQUAL
                } else {
                    SettingsSpec.TAB_WIDTH_COMPACT
                },
                onSelect = controller::setTabWidthMode,
            ),
            ToggleItem(
                id = "tab-close-button",
                titleRes = R.string.settings_tab_close_button,
                checked = controller.viewOptions.showTabCloseButton,
                onCheckedChange = controller::setTabCloseButtonVisible,
            ),
        ),
    )

    private fun sessions(controller: ShellViewModel): SettingsSection = SettingsSection(
        id = SECTION_SESSIONS,
        titleRes = R.string.settings_section_sessions,
        items = listOf(
            ToggleItem(
                id = "session-rail-visible",
                titleRes = R.string.settings_session_rail,
                descriptionRes = R.string.settings_session_rail_hint,
                checked = controller.sessionRailVisible,
                onCheckedChange = controller::updateSessionRailVisible,
            ),
        ),
    )

    private fun input(controller: ShellViewModel): SettingsSection = SettingsSection(
        id = SECTION_INPUT,
        titleRes = R.string.settings_section_input,
        items = listOf(
            ToggleItem(
                id = "keymap-enabled",
                titleRes = R.string.settings_keymap_enabled,
                descriptionRes = R.string.settings_keymap_enabled_hint,
                checked = controller.keymapEnabled,
                onCheckedChange = controller::updateKeymapEnabled,
            ),
            ToggleItem(
                id = "command-palette-enabled",
                titleRes = R.string.settings_command_palette,
                descriptionRes = R.string.settings_command_palette_hint,
                checked = controller.commandPaletteEnabled,
                onCheckedChange = controller::updateCommandPaletteEnabled,
            ),
        ),
    )

    private fun about(controller: ShellViewModel): SettingsSection = SettingsSection(
        id = SECTION_ABOUT,
        titleRes = R.string.settings_section_about,
        items = listOf(
            InfoItem(
                id = "version",
                titleRes = R.string.settings_version,
                value = com.termux.shell.BuildConfig.VERSION_NAME,
            ),
            InfoItem(
                id = "config-file",
                titleRes = R.string.settings_config_file,
                value = controller.configFilePath,
            ),
            InfoItem(
                id = "keymap-file",
                titleRes = R.string.settings_keymap_file,
                value = controller.keymapFilePath,
            ),
            ActionItem(
                id = "reload-config",
                titleRes = R.string.settings_reload_config,
                descriptionRes = R.string.settings_reload_config_hint,
                onClick = controller::reloadConfig,
            ),
        ),
    )
}
