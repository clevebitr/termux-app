package com.termux.shell.ui.settings

import androidx.annotation.StringRes

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

