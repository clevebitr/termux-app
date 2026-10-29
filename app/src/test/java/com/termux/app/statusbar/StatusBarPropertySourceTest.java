package com.termux.app.statusbar;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Guards the property source of the configurations of the fork.
 *
 * {@code SharedProperties.loadPropertiesFromDisk()} only keeps the keys that are listed in
 * {@code TERMUX_APP_PROPERTIES_LIST} in its in-memory {@link java.util.Properties} cache, and the
 * `status-bar-*`, `notification-*` and `terminal-menu-*` keys are intentionally not part of that list,
 * since the per segment options and the list values are dynamic keys that the shared library cannot type.
 * So reading the cached properties with {@code getProperties(true)} silently returns a
 * {@link java.util.Properties} without any of those keys, which makes the configuration always use its
 * defaults and never show up, no matter how the properties file is configured.
 *
 * This is not observable from a pure JVM test without an android Context, so the sources are checked
 * instead of the behaviour, which is enough to catch the regression.
 */
public class StatusBarPropertySourceTest {

    private static final Path FORK_PROPERTIES_SOURCE =
        Paths.get("src/main/java/com/termux/app/properties/ForkProperties.java");

    /** The classes that reload a configuration of the fork, which must read it with the helper. */
    private static final Path[] CONFIGURATION_SOURCES = new Path[]{
        Paths.get("src/main/java/com/termux/app/statusbar/StatusBarController.java"),
        Paths.get("src/main/java/com/termux/app/notifications/TerminalNotificationManager.java"),
    };

    private static String readSource(Path path) throws IOException {
        Assert.assertTrue("The source file must be readable for this test, looked in \""
            + path.toAbsolutePath() + "\"", Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void testPropertiesAreReadFromDiskAndNotFromTheFilteredCache() throws IOException {
        String forkProperties = readSource(FORK_PROPERTIES_SOURCE);

        Assert.assertTrue(
            "ForkProperties must read the properties with getProperties(false), since the cached properties "
                + "only contain the keys of TERMUX_APP_PROPERTIES_LIST, which does not include the keys of the fork",
            forkProperties.contains("getProperties(false)"));
        Assert.assertFalse(
            "ForkProperties must not read the properties with getProperties(true)",
            forkProperties.contains("sharedProperties.getProperties(true)"));
    }

    @Test
    public void testConfigurationsUseTheForkPropertiesHelper() throws IOException {
        for (Path path : CONFIGURATION_SOURCES) {
            String source = readSource(path);

            Assert.assertTrue(
                path.getFileName() + " must reload its configuration with ForkProperties.readTermuxProperties(), "
                    + "so that the properties are read from the disk instead of the filtered cache",
                source.contains("ForkProperties.readTermuxProperties()"));
            // The comments of the files mention `getProperties(true)`, so only the call is checked.
            Assert.assertFalse(
                path.getFileName() + " must not read the properties itself, only ForkProperties may",
                source.contains("sharedProperties.getProperties("));
        }
    }
}
