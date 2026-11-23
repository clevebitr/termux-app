package com.termux.shell.ui.settings

import com.termux.shell.config.SettingsSpec
import com.termux.shell.session.SessionProfile
import com.termux.shell.ui.FakePersister
import com.termux.shell.ui.newViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ShellSettingsCatalog] 的结构与写回接线测试。
 *
 * 目的：保证设置页的每一项都落在真实配置键上，避免出现「界面能点但写不进配置文件」或
 * 「配置项定义了但设置页没有入口」这两类回归。
 */
class ShellSettingsCatalogTest {

    private fun items(persister: FakePersister): List<SettingsItem> =
        ShellSettingsCatalog.blocks(newViewModel(persister = persister), emptyList())
            .filterIsInstance<SectionBlock>()
            .flatMap { it.section.items }

    @Test
    fun itemIdsAreUnique() {
        val ids = items(FakePersister()).map { it.id }

        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyToggleWritesExactlyOneKnownSetting() {
        val persister = FakePersister()
        val toggles = items(persister).filterIsInstance<ToggleItem>()
        assertTrue(toggles.isNotEmpty())

        for (toggle in toggles) {
            val before = persister.writes.size
            toggle.onCheckedChange(!toggle.checked)

            val written = persister.writes.drop(before)
            assertEquals("开关 ${toggle.id} 应写入一项配置", 1, written.size)
            val key = written.single().first
            assertNotNull("开关 ${toggle.id} 写入了未定义的配置键：$key", SettingsSpec.find(key))
        }
    }

    @Test
    fun everyChoiceWritesOneLegalValue() {
        val persister = FakePersister()
        val choices = items(persister).filterIsInstance<ChoiceItem>()
        assertTrue(choices.isNotEmpty())

        for (choice in choices) {
            assertTrue(
                "选项项 ${choice.id} 的当前值不在候选中",
                choice.options.any { it.value == choice.selected },
            )
            val alternative = choice.options.first { it.value != choice.selected }
            val before = persister.writes.size
            choice.onSelect(alternative.value)

            val written = persister.writes.drop(before)
            assertEquals("选项项 ${choice.id} 应写入一项配置", 1, written.size)
            val (key, value) = written.single()
            val entry = SettingsSpec.find(key)
            assertNotNull("选项项 ${choice.id} 写入了未定义的配置键：$key", entry)
            assertEquals("选项项 ${choice.id} 写入了非法取值：$value", alternative.value, value)
            assertTrue("选项项 ${choice.id} 的取值不在配置定义中", entry!!.choices.contains(value))
        }
    }

    @Test
    fun aboutSectionExposesConfigPathsAndReloadAction() {
        val persister = FakePersister()
        val about = ShellSettingsCatalog.blocks(newViewModel(persister = persister), emptyList())
            .filterIsInstance<SectionBlock>()
            .single { it.section.id == ShellSettingsCatalog.SECTION_ABOUT }
            .section

        assertTrue(about.items.any { it is InfoItem && it.id == "config-file" })
        assertTrue(about.items.any { it is InfoItem && it.id == "keymap-file" })
        assertTrue(about.items.any { it is ActionItem && it.id == "reload-config" })
    }

    @Test
    fun profilesBlockReportsConfiguredCount() {
        val blocks = ShellSettingsCatalog.blocks(
            newViewModel(),
            listOf(
                SessionProfile(id = "a", name = "dev"),
                SessionProfile(id = "b", name = "build"),
            ),
        )

        val profiles = blocks.filterIsInstance<ProfilesBlock>().single()
        assertEquals(2, profiles.count)
    }
}
