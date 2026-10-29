package com.termux.app.fragments.settings.termux;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Guards that every page of the Termux settings installs the long press listener that resets a setting, since
 * a page that forgets it silently loses the feature and the click cannot be reached from a pure JVM test,
 * which has no android view.
 */
public class PreferenceResetSourceTest {

    /** The pages of the settings of the Termux app, which all edit `termux.properties`. */
    private static final String[] PAGES = new String[]{
        "AppearancePreferencesFragment",
        "BackupPreferencesFragment",
        "DebuggingPreferencesFragment",
        "NotificationPreferencesFragment",
        "StatusBarPreferencesFragment",
        "TerminalIOPreferencesFragment",
        "TerminalKeyboardPreferencesFragment",
        "TerminalMenuPreferencesFragment",
        "TerminalViewPreferencesFragment",
    };

    private static String readSource(Path path) throws IOException {
        Assert.assertTrue("The source file must be readable for this test, looked in \""
            + path.toAbsolutePath() + "\"", Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void testEverySettingsPageInstallsTheResetListener() throws IOException {
        for (String page : PAGES) {
            Path path = Paths.get("src/main/java/com/termux/app/fragments/settings/termux/" + page + ".java");
            String source = readSource(path);
            Assert.assertTrue("The page \"" + page + "\" must call the installer of the reset listener",
                source.contains("setPreferencesFromResource(") &&
                    source.contains("PreferenceResetUtils.installResetOnLongClick(this);"));
        }
    }

    @Test
    public void testThePropertyDataStoreCanResetAProperty() throws IOException {
        String source = readSource(
            Paths.get("src/main/java/com/termux/app/fragments/settings/termux/TermuxPropertyPreferenceDataStore.java"));
        Assert.assertTrue("The data store must be able to reset a property of the file",
            source.contains("public void resetToDefault("));
        // A property is reset by commenting it out, which makes the app use the default value again.
        Assert.assertTrue("The reset must write an empty value instead of the previous one",
            source.contains("setTermuxPropertyValue(key, null);"));
    }
}
