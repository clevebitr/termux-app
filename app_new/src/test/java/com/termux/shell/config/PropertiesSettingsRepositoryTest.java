package com.termux.shell.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * {@link PropertiesSettingsRepository} 的读取、校验与模板写入测试。
 */
public final class PropertiesSettingsRepositoryTest {

    private File mDirectory;

    @Before
    public void setUp() {
        mDirectory = new File(System.getProperty("java.io.tmpdir"),
            "shell-settings-" + System.nanoTime());
        assertTrue(mDirectory.mkdirs());
    }

    @After
    public void tearDown() {
        File[] files = mDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                assertTrue(file.delete());
            }
        }
        assertTrue(mDirectory.delete());
    }

    @Test
    public void writesTemplateWhenFileIsMissing() {
        File file = new File(mDirectory, "shell.properties");
        PropertiesSettingsRepository repository = PropertiesSettingsRepository.open(file);

        assertTrue(file.isFile());
        assertEquals("true", repository.getString(SettingsSpec.KEY_TAB_BAR_VISIBLE, null));
        assertEquals(SettingsSpec.THEME_SYSTEM, repository.getString(SettingsSpec.KEY_APP_THEME, null));
        // 模板文件应覆盖全部已定义键，避免用户看到空文件。
        assertEquals(SettingsSpec.keys().size(), repository.keys().size());
    }

    @Test
    public void validValuesOverrideDefaults() throws IOException {
        File file = new File(mDirectory, "shell.properties");
        writeFile(file, "app-theme=dark\n"
            + "tab-bar-position=bottom\n"
            + "tab-close-button=false\n"
            + "tab-width-mode=compact\n");

        PropertiesSettingsRepository repository = PropertiesSettingsRepository.open(file);
        assertEquals(SettingsSpec.THEME_DARK, repository.getString(SettingsSpec.KEY_APP_THEME, null));
        assertEquals(SettingsSpec.TAB_POSITION_BOTTOM,
            repository.getString(SettingsSpec.KEY_TAB_BAR_POSITION, null));
        assertFalse(repository.getBoolean(SettingsSpec.KEY_TAB_CLOSE_BUTTON, true));
        assertEquals(SettingsSpec.TAB_WIDTH_COMPACT,
            repository.getString(SettingsSpec.KEY_TAB_WIDTH_MODE, null));
        // 未出现在文件中的键回退到调用方默认值。
        assertTrue(repository.getBoolean(SettingsSpec.KEY_TAB_BAR_VISIBLE, true));
        assertTrue(repository.getInvalidKeys().isEmpty());
    }

    @Test
    public void invalidValuesAreIgnoredAndReported() throws IOException {
        File file = new File(mDirectory, "shell.properties");
        writeFile(file, "app-theme=purple\ntab-bar-visible=maybe\ntab-bar-position=\n");

        PropertiesSettingsRepository repository = PropertiesSettingsRepository.open(file);
        assertEquals(SettingsSpec.THEME_SYSTEM,
            repository.getString(SettingsSpec.KEY_APP_THEME, SettingsSpec.THEME_SYSTEM));
        assertTrue(repository.getBoolean(SettingsSpec.KEY_TAB_BAR_VISIBLE, true));
        assertEquals(SettingsSpec.TAB_POSITION_TOP,
            repository.getString(SettingsSpec.KEY_TAB_BAR_POSITION, SettingsSpec.TAB_POSITION_TOP));
        assertTrue(repository.getInvalidKeys().contains(SettingsSpec.KEY_APP_THEME));
        assertTrue(repository.getInvalidKeys().contains(SettingsSpec.KEY_TAB_BAR_VISIBLE));
    }

    @Test
    public void unknownKeysAreRetainedWithoutAffectingKnownOnes() throws IOException {
        File file = new File(mDirectory, "shell.properties");
        writeFile(file, "experimental-key=value\napp-theme=light\n");

        PropertiesSettingsRepository repository = PropertiesSettingsRepository.open(file);
        assertTrue(repository.keys().contains("experimental-key"));
        assertEquals("value", repository.getString("experimental-key", null));
        assertEquals(SettingsSpec.THEME_LIGHT, repository.getString(SettingsSpec.KEY_APP_THEME, null));
    }

    @Test
    public void reloadPicksUpFileChanges() throws IOException {
        File file = new File(mDirectory, "shell.properties");
        PropertiesSettingsRepository repository = PropertiesSettingsRepository.open(file);
        assertEquals(SettingsSpec.THEME_SYSTEM, repository.getString(SettingsSpec.KEY_APP_THEME, null));

        writeFile(file, "app-theme=light\n");
        assertTrue(repository.reload());
        assertEquals(SettingsSpec.THEME_LIGHT, repository.getString(SettingsSpec.KEY_APP_THEME, null));
    }

    @Test
    public void reloadFailsWhenFileIsRemoved() {
        File file = new File(mDirectory, "shell.properties");
        PropertiesSettingsRepository repository = PropertiesSettingsRepository.open(file);
        assertTrue(file.delete());
        assertFalse(repository.reload());
        assertNotNull(repository.getFilePath());
    }

    private static void writeFile(File file, String content) throws IOException {
        try (Writer writer = new OutputStreamWriter(
            new java.io.FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }
}
