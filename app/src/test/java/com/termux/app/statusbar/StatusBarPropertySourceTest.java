package com.termux.app.statusbar;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Guards the property source of the status bar configuration.
 *
 * {@code SharedProperties.loadPropertiesFromDisk()} only keeps the keys that are listed in
 * {@code TERMUX_APP_PROPERTIES_LIST} in its in-memory {@link java.util.Properties} cache, and the
 * `status-bar-*` keys are intentionally not part of that list, since the per segment options are
 * dynamic keys that the shared library cannot type. So reading the cached properties with
 * {@code getProperties(true)} silently returns a {@link java.util.Properties} without any
 * `status-bar-*` key, which makes the status bar always use its defaults and never show up, no
 * matter how the properties file is configured.
 *
 * This is not observable from a pure JVM test without an android Context, so the source is checked
 * instead of the behaviour, which is enough to catch the regression.
 */
public class StatusBarPropertySourceTest {

    private static final Path CONTROLLER_SOURCE =
        Paths.get("src/main/java/com/termux/app/statusbar/StatusBarController.java");

    @Test
    public void testPropertiesAreReadFromDiskAndNotFromTheFilteredCache() throws IOException {
        Assert.assertTrue("The controller source file must be readable for this test, looked in \""
            + CONTROLLER_SOURCE.toAbsolutePath() + "\"", Files.exists(CONTROLLER_SOURCE));

        String source = new String(Files.readAllBytes(CONTROLLER_SOURCE), StandardCharsets.UTF_8);

        Assert.assertTrue(
            "StatusBarController must read the properties with getProperties(false), since the cached "
                + "properties only contain the keys of TERMUX_APP_PROPERTIES_LIST, which does not "
                + "include the status-bar-* keys",
            source.contains("sharedProperties.getProperties(false)"));
        Assert.assertFalse(
            "StatusBarController must not read the properties with getProperties(true)",
            source.contains("sharedProperties.getProperties(true)"));
    }

}
