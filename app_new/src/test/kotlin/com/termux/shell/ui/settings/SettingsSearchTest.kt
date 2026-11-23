package com.termux.shell.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SettingsSearch] 的筛选规则测试：不依赖 Android 资源，文案由测试注入。 */
class SettingsSearchTest {

    private val texts = mapOf(
        1 to "Theme",
        2 to "Follow the system setting",
        3 to "Tab bar",
        4 to "Shell shortcuts",
        5 to "Every key goes to the terminal",
    )

    private fun text(id: Int): String = texts[id].orEmpty()

    private fun toggle(id: String, titleRes: Int, descriptionRes: Int? = null) = ToggleItem(
        id = id,
        titleRes = titleRes,
        descriptionRes = descriptionRes,
        checked = false,
        onCheckedChange = { },
    )

    private fun blocks(): List<SettingsBlock> = listOf(
        SectionBlock(
            SettingsSection(
                id = "appearance",
                titleRes = 1,
                items = listOf(
                    toggle("theme", 1, 2),
                    toggle("tab-bar", 3),
                ),
            ),
        ),
        ProfilesBlock(count = 2),
        SectionBlock(
            SettingsSection(
                id = "input",
                titleRes = 4,
                items = listOf(toggle("keymap", 4, 5)),
            ),
        ),
    )

    @Test
    fun blankQueryKeepsEveryBlock() {
        val result = SettingsSearch.filter(blocks(), "   ", ::text)

        assertEquals(3, result.size)
        assertTrue(result.any { it is ProfilesBlock })
    }

    @Test
    fun titleMatchKeepsOnlyMatchingItems() {
        val result = SettingsSearch.filter(blocks(), "tab", ::text)

        val section = result.filterIsInstance<SectionBlock>().single()
        assertEquals(listOf("tab-bar"), section.section.items.map { it.id })
    }

    @Test
    fun descriptionMatchIsCaseInsensitive() {
        val result = SettingsSearch.filter(blocks(), "system SETTING", ::text)

        val section = result.filterIsInstance<SectionBlock>().single()
        assertEquals(listOf("theme"), section.section.items.map { it.id })
    }

    @Test
    fun profilesBlockIsHiddenWhileSearching() {
        val result = SettingsSearch.filter(blocks(), "shell", ::text)

        assertTrue(result.none { it is ProfilesBlock })
        assertEquals(listOf("input"), result.filterIsInstance<SectionBlock>().map { it.section.id })
    }

    @Test
    fun noMatchProducesEmptyResult() {
        assertTrue(SettingsSearch.filter(blocks(), "nothing-matches", ::text).isEmpty())
    }
}
