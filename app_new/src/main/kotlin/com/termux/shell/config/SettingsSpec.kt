package com.termux.shell.config

import java.util.Locale

/**
 * 用户可配置项的定义表。
 *
 * 目的：把键名、类型、默认值、合法取值与说明集中在一处，使「配置文件模板生成」「读取校验」
 * 「默认值回退」共享同一份定义，避免三处各写一遍导致不一致。
 *
 * 约束：
 * - 只定义外壳自身的配置项；终端核心的配置项由终端层另行定义，两者不得混用键名；
 * - 新增配置项必须同时给出中文说明，说明会写入配置文件模板；
 * - 合法取值一律小写，读取时按小写归一化，避免大小写差异导致校验失败。
 */
object SettingsSpec {

    const val KEY_APP_THEME = "app-theme"
    const val KEY_TAB_BAR_VISIBLE = "tab-bar-visible"
    const val KEY_TAB_BAR_POSITION = "tab-bar-position"
    const val KEY_TAB_CLOSE_BUTTON = "tab-close-button"
    const val KEY_TAB_WIDTH_MODE = "tab-width-mode"
    const val KEY_LAYOUT_MODE = "layout-mode"
    const val KEY_SESSION_RAIL_VISIBLE = "session-rail-visible"
    const val KEY_COMMAND_PALETTE_ENABLED = "command-palette-enabled"
    const val KEY_KEYMAP_ENABLED = "keymap-enabled"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val TAB_POSITION_TOP = "top"
    const val TAB_POSITION_BOTTOM = "bottom"

    const val TAB_WIDTH_EQUAL = "equal"
    const val TAB_WIDTH_COMPACT = "compact"

    /** 界面形态：自动按窗口宽度选择，或固定为手机与平板形态。 */
    const val LAYOUT_AUTO = "auto"
    const val LAYOUT_PHONE = "phone"
    const val LAYOUT_TABLET = "tablet"

    /** 配置项类型，取值语义由 [Entry.normalize] 负责。 */
    enum class Type {
        BOOLEAN,
        CHOICE,
    }

    /**
     * 单项配置定义。
     *
     * @param key 键名。
     * @param type 类型。
     * @param defaultValue 默认值的规范文本形式。
     * @param choices 合法取值，布尔项为空。
     * @param description 写入模板的中文说明。
     */
    data class Entry(
        val key: String,
        val type: Type,
        val defaultValue: String,
        val choices: List<String>,
        val description: String,
    ) {

        val defaultBoolean: Boolean
            get() = defaultValue.toBoolean()

        /**
         * 把文件中的原始文本归一化为规范值。
         *
         * @return 规范值；原始值为空、取值非法或类型不符时返回 null，调用方应回退到默认值。
         */
        fun normalize(raw: String?): String? {
            val value = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
            if (value.isEmpty()) {
                return null
            }
            return when (type) {
                Type.BOOLEAN -> when (value) {
                    "true" -> "true"
                    "false" -> "false"
                    else -> null
                }

                Type.CHOICE -> if (value in choices) value else null
            }
        }
    }

    /** 全部配置项定义，顺序与配置文件模板中的顺序一致。 */
    val entries: List<Entry> = listOf(
        Entry(
            KEY_APP_THEME, Type.CHOICE, THEME_SYSTEM,
            listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK),
            "界面主题，取值 system、light 或 dark；修改后在命令面板执行「重新加载配置文件」立即生效。",
        ),
        Entry(
            KEY_TAB_BAR_VISIBLE, Type.BOOLEAN, "true", emptyList(),
            "是否显示标签栏；关闭后仍可通过快捷键与命令面板切换标签。",
        ),
        Entry(
            KEY_TAB_BAR_POSITION, Type.CHOICE, TAB_POSITION_TOP,
            listOf(TAB_POSITION_TOP, TAB_POSITION_BOTTOM),
            "标签栏位置，取值 top 或 bottom。",
        ),
        Entry(
            KEY_TAB_CLOSE_BUTTON, Type.BOOLEAN, "true", emptyList(),
            "标签上是否显示关闭按钮。",
        ),
        Entry(
            KEY_TAB_WIDTH_MODE, Type.CHOICE, TAB_WIDTH_EQUAL,
            listOf(TAB_WIDTH_EQUAL, TAB_WIDTH_COMPACT),
            "标签宽度模式：equal 平均分配宽度，compact 按标题宽度收缩，超出时横向滚动。",
        ),
        Entry(
            KEY_LAYOUT_MODE, Type.CHOICE, LAYOUT_AUTO,
            listOf(LAYOUT_AUTO, LAYOUT_PHONE, LAYOUT_TABLET),
            "界面形态：auto 按窗口宽度自动选择，phone 固定单窗格布局，tablet 固定桌面布局。",
        ),
        Entry(
            KEY_SESSION_RAIL_VISIBLE, Type.BOOLEAN, "true", emptyList(),
            "宽屏下是否常驻会话列表；关闭后会话列表仅通过抽屉打开。",
        ),
        Entry(
            KEY_COMMAND_PALETTE_ENABLED, Type.BOOLEAN, "true", emptyList(),
            "是否允许打开命令面板。",
        ),
        Entry(
            KEY_KEYMAP_ENABLED, Type.BOOLEAN, "true", emptyList(),
            "是否启用键位表中的外壳快捷键；关闭后按键全部交给终端处理。",
        ),
    )

    private val byKey: Map<String, Entry> = entries.associateBy { it.key }

    /** 全部键名。 */
    fun keys(): Set<String> = byKey.keys

    /** 按键名查找定义。 */
    fun find(key: String?): Entry? = if (key == null) null else byKey[key]

    /**
     * 生成配置文件模板内容。
     *
     * 模板由「文件说明 + 每项的说明注释与默认值」组成；用户按键名覆盖默认值，删除某行即回到默认值。
     */
    fun buildDefaultFileContent(): String = buildString {
        append("# Termux Shell 配置文件\n")
        append("# 修改后执行命令面板中的「重新加载配置文件」立即生效。\n")
        append("# 以 # 开头的行与空行被忽略；删除某项即使用默认值。\n")
        for (entry in entries) {
            append('\n')
            append("# ").append(entry.description).append('\n')
            if (entry.type == Type.CHOICE) {
                append("# 取值：").append(entry.choices.joinToString("、")).append('\n')
            }
            append(entry.key).append('=').append(entry.defaultValue).append('\n')
        }
    }
}
