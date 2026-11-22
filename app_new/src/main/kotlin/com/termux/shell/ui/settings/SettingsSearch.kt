package com.termux.shell.ui.settings

/**
 * 设置页搜索。
 *
 * 目的：把「按查询筛选显示块」的规则从界面中剥离为纯函数，使规则可在 JVM 单元测试中验证，
 * 界面只负责把资源标识解析为可搜索文本。
 *
 * 行为：
 * - 查询为空（仅含空白）时原样返回全部显示块；
 * - 非空时逐项匹配标题与说明，不区分大小写，命中任一项即保留该分组；
 * - 会话配置属于操作型区块而非配置项，搜索期间隐藏，避免与命中项混排。
 *
 * 约束：本类型不读取 Android 资源；文案由 [filter] 的调用方通过 `text` 解析。
 */
internal object SettingsSearch {

    /**
     * 按查询筛选显示块。
     *
     * @param text 资源标识到可搜索文本的解析函数，通常为 `Context::getString`。
     */
    fun filter(
        blocks: List<SettingsBlock>,
        query: String,
        text: (Int) -> String,
    ): List<SettingsBlock> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return blocks
        }
        return blocks.mapNotNull { block ->
            when (block) {
                is SectionBlock -> {
                    val items = block.section.items.filter { item -> matches(item, trimmed, text) }
                    if (items.isEmpty()) null else SectionBlock(block.section.copy(items = items))
                }

                is ProfilesBlock -> null
            }
        }
    }

    private fun matches(item: SettingsItem, query: String, text: (Int) -> String): Boolean {
        if (text(item.titleRes).contains(query, ignoreCase = true)) {
            return true
        }
        val description = item.descriptionRes?.let(text).orEmpty()
        return description.contains(query, ignoreCase = true)
    }
}
