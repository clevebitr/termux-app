package com.termux.shell.config

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/** [PropertiesSettingsRepository] 的读取、校验与模板写入测试。 */
class PropertiesSettingsRepositoryTest {

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File(System.getProperty("java.io.tmpdir"), "shell-settings-${System.nanoTime()}")
        assertTrue(directory.mkdirs())
    }

    @After
    fun tearDown() {
        directory.listFiles()?.forEach { assertTrue(it.delete()) }
        assertTrue(directory.delete())
    }

    @Test
    fun writesTemplateWhenFileIsMissing() {
        val file = File(directory, "shell.properties")
        val repository = PropertiesSettingsRepository.open(file)

        assertTrue(file.isFile)
        assertEquals("true", repository.getString(SettingsSpec.KEY_TAB_BAR_VISIBLE, null))
        assertEquals(SettingsSpec.THEME_SYSTEM, repository.getString(SettingsSpec.KEY_APP_THEME, null))
        // 模板文件应覆盖全部已定义键，避免用户看到空文件。
        assertEquals(SettingsSpec.keys().size, repository.keys().size)
    }

    @Test
    fun validValuesOverrideDefaults() {
        val file = File(directory, "shell.properties")
        writeFile(
            file,
            "app-theme=dark\n" +
                "tab-bar-position=bottom\n" +
                "tab-close-button=false\n" +
                "tab-width-mode=compact\n",
        )

        val repository = PropertiesSettingsRepository.open(file)
        assertEquals(SettingsSpec.THEME_DARK, repository.getString(SettingsSpec.KEY_APP_THEME, null))
        assertEquals(SettingsSpec.TAB_POSITION_BOTTOM, repository.getString(SettingsSpec.KEY_TAB_BAR_POSITION, null))
        assertFalse(repository.getBoolean(SettingsSpec.KEY_TAB_CLOSE_BUTTON, true))
        assertEquals(SettingsSpec.TAB_WIDTH_COMPACT, repository.getString(SettingsSpec.KEY_TAB_WIDTH_MODE, null))
        // 未出现在文件中的键回退到调用方默认值。
        assertTrue(repository.getBoolean(SettingsSpec.KEY_TAB_BAR_VISIBLE, true))
        assertTrue(repository.getInvalidKeys().isEmpty())
    }

    @Test
    fun invalidValuesAreIgnoredAndReported() {
        val file = File(directory, "shell.properties")
        writeFile(file, "app-theme=purple\ntab-bar-visible=maybe\ntab-bar-position=\n")

        val repository = PropertiesSettingsRepository.open(file)
        assertEquals(
            SettingsSpec.THEME_SYSTEM,
            repository.getString(SettingsSpec.KEY_APP_THEME, SettingsSpec.THEME_SYSTEM),
        )
        assertTrue(repository.getBoolean(SettingsSpec.KEY_TAB_BAR_VISIBLE, true))
        assertEquals(
            SettingsSpec.TAB_POSITION_TOP,
            repository.getString(SettingsSpec.KEY_TAB_BAR_POSITION, SettingsSpec.TAB_POSITION_TOP),
        )
        assertTrue(repository.getInvalidKeys().contains(SettingsSpec.KEY_APP_THEME))
        assertTrue(repository.getInvalidKeys().contains(SettingsSpec.KEY_TAB_BAR_VISIBLE))
    }

    @Test
    fun unknownKeysAreRetainedWithoutAffectingKnownOnes() {
        val file = File(directory, "shell.properties")
        writeFile(file, "experimental-key=value\napp-theme=light\n")

        val repository = PropertiesSettingsRepository.open(file)
        assertTrue(repository.keys().contains("experimental-key"))
        assertEquals("value", repository.getString("experimental-key", null))
        assertEquals(SettingsSpec.THEME_LIGHT, repository.getString(SettingsSpec.KEY_APP_THEME, null))
    }

    @Test
    fun reloadPicksUpFileChanges() {
        val file = File(directory, "shell.properties")
        val repository = PropertiesSettingsRepository.open(file)
        assertEquals(SettingsSpec.THEME_SYSTEM, repository.getString(SettingsSpec.KEY_APP_THEME, null))

        writeFile(file, "app-theme=light\n")
        assertTrue(repository.reload())
        assertEquals(SettingsSpec.THEME_LIGHT, repository.getString(SettingsSpec.KEY_APP_THEME, null))
    }

    @Test
    fun reloadFailsWhenFileIsRemoved() {
        val file = File(directory, "shell.properties")
        val repository = PropertiesSettingsRepository.open(file)
        assertTrue(file.delete())
        assertFalse(repository.reload())
        assertNotNull(repository.getInvalidKeys())
    }

    @Test
    fun putRewritesOnlyTargetLineAndKeepsComments() {
        val file = File(directory, "shell.properties")
        writeFile(
            file,
            "# 顶部注释\napp-theme=system\n# 段间注释\nexperimental-key=value\ntab-bar-visible=true\n",
        )
        val repository = PropertiesSettingsRepository.open(file)

        assertTrue(repository.put(SettingsSpec.KEY_APP_THEME, SettingsSpec.THEME_DARK))
        assertEquals(SettingsSpec.THEME_DARK, repository.getString(SettingsSpec.KEY_APP_THEME, null))

        val lines = file.readLines(StandardCharsets.UTF_8)
        assertEquals("# 顶部注释", lines[0])
        assertEquals("app-theme=dark", lines[1])
        assertEquals("# 段间注释", lines[2])
        assertEquals("experimental-key=value", lines[3])
        assertTrue(repository.keys().contains("experimental-key"))
    }

    @Test
    fun putAppendsKeyMissingFromFile() {
        val file = File(directory, "shell.properties")
        writeFile(file, "app-theme=light\n")
        val repository = PropertiesSettingsRepository.open(file)

        assertTrue(repository.put(SettingsSpec.KEY_LAYOUT_MODE, SettingsSpec.LAYOUT_TABLET))
        assertEquals(SettingsSpec.LAYOUT_TABLET, repository.getString(SettingsSpec.KEY_LAYOUT_MODE, null))
        assertTrue(file.readLines(StandardCharsets.UTF_8).contains("layout-mode=tablet"))
    }

    @Test
    fun putRejectsUnknownKeyAndInvalidValue() {
        val file = File(directory, "shell.properties")
        val repository = PropertiesSettingsRepository.open(file)

        assertFalse(repository.put("experimental-key", "value"))
        assertFalse(repository.put(SettingsSpec.KEY_APP_THEME, "purple"))
        assertEquals(SettingsSpec.THEME_SYSTEM, repository.getString(SettingsSpec.KEY_APP_THEME, null))
    }

    @Test
    fun putIsVisibleAfterReload() {
        val file = File(directory, "shell.properties")
        val repository = PropertiesSettingsRepository.open(file)
        assertTrue(repository.put(SettingsSpec.KEY_LAYOUT_MODE, SettingsSpec.LAYOUT_PHONE))

        val reopened = PropertiesSettingsRepository.open(file)
        assertEquals(SettingsSpec.LAYOUT_PHONE, reopened.getString(SettingsSpec.KEY_LAYOUT_MODE, null))
    }

    private fun writeFile(file: File, content: String) {
        OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }
}
