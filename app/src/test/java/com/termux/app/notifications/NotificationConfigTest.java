package com.termux.app.notifications;

import com.termux.shared.logger.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

/**
 * Tests the parsing of the `notification-*` properties, since the notification cards can be
 * configured with a lot of properties and an invalid value must never break the app or show cards
 * that the user did not enable.
 *
 * The tests do not need a {@code RobolectricTestRunner}, since only the properties are parsed, so
 * the android logging is turned off instead of being mocked.
 */
public class NotificationConfigTest {

    private int mPreviousLogLevel;

    @Before
    public void disableLogging() {
        mPreviousLogLevel = Logger.getLogLevel();
        Logger.setLogLevel(null, Logger.LOG_LEVEL_OFF);
    }

    @After
    public void restoreLogging() {
        Logger.setLogLevel(null, mPreviousLogLevel);
    }

    private NotificationConfig parse(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return NotificationConfig.parse(properties);
    }

    @Test
    public void testDefaultsAreOptIn() {
        NotificationConfig config = parse();

        Assert.assertFalse("The cards must not be shown unless they are enabled", config.enabled);
        Assert.assertEquals(NotificationConfig.POSITION_TOP_END, config.position);
        Assert.assertEquals(NotificationConfig.DEFAULT_MAX_VISIBLE, config.maxVisible);
        Assert.assertEquals(NotificationConfig.DEFAULT_DURATION_MS, config.durationMs);
        Assert.assertEquals(NotificationConfig.WIDTH_AUTO, config.widthDp);
        // The cards look like part of the terminal: square corners, flat, with a 1dp border and a
        // 4dp accent bar, drawn with the monospace font of the terminal.
        Assert.assertEquals(0, config.cornerRadiusDp);
        Assert.assertEquals(0, config.elevationDp);
        Assert.assertEquals(1, config.borderWidthDp);
        Assert.assertEquals(4, config.accentWidthDp);
        Assert.assertTrue(config.monospace);
        Assert.assertEquals(NotificationConfig.DEFAULT_BODY_MAX_LINES, config.bodyMaxLines);
        Assert.assertEquals("", config.backgroundColor);
        Assert.assertEquals("", config.foregroundColor);
        Assert.assertEquals("", config.accentFailureColor);

        Assert.assertTrue(config.exitCodeEnabled);
        Assert.assertTrue(config.exitCodeShowCwd);
        Assert.assertTrue(config.exitCodeShowSession);
        Assert.assertEquals(NotificationConfig.DEFAULT_MIN_DURATION_SECONDS, config.exitCodeMinDurationSeconds);
        Assert.assertTrue(config.exitCodeIgnoreCommands.contains("grep"));

        Assert.assertFalse("App event cards must be opt-in", config.eventsEnabled);
        Assert.assertTrue(config.events.contains(NotificationConfig.EVENT_SESSION_EXIT));

        Assert.assertFalse("OSC cards must be opt-in", config.oscEnabled);
        Assert.assertTrue(config.oscAllowApp.isEmpty());
        Assert.assertTrue(config.oscReportClick);
        Assert.assertFalse(config.oscIcons);

        Assert.assertFalse(config.backgroundEnabled);
        Assert.assertEquals(NotificationConfig.DEFAULT_HISTORY_SIZE, config.historySize);
    }

    @Test
    public void testStyleProperties() {
        NotificationConfig config = parse(
            "notification-corner-radius", "8",
            "notification-elevation", "4",
            "notification-border-width", "0",
            "notification-accent-width", "0",
            "notification-monospace", "false",
            "notification-body-max-lines", "4");

        Assert.assertEquals(8, config.cornerRadiusDp);
        Assert.assertEquals(4, config.elevationDp);
        Assert.assertEquals(0, config.borderWidthDp);
        Assert.assertEquals(0, config.accentWidthDp);
        Assert.assertFalse(config.monospace);
        Assert.assertEquals(4, config.bodyMaxLines);
    }

    @Test
    public void testOutOfRangeStyleValuesFallBackToDefaults() {
        Assert.assertEquals(NotificationConfig.DEFAULT_CORNER_RADIUS_DP,
            parse("notification-corner-radius", "999").cornerRadiusDp);
        Assert.assertEquals(NotificationConfig.DEFAULT_ELEVATION_DP,
            parse("notification-elevation", "-1").elevationDp);
        Assert.assertEquals(NotificationConfig.DEFAULT_ACCENT_WIDTH_DP,
            parse("notification-accent-width", "99").accentWidthDp);
        // A body without any line would be invisible, so 0 falls back to the minimum instead.
        Assert.assertEquals(NotificationConfig.DEFAULT_BODY_MAX_LINES,
            parse("notification-body-max-lines", "0").bodyMaxLines);
    }

    @Test
    public void testInvalidPositionFallsBackToDefault() {
        Assert.assertEquals(NotificationConfig.POSITION_TOP_END, parse("notification-position", "middle").position);
        Assert.assertEquals(NotificationConfig.POSITION_TOP_START, parse("notification-position", " TOP-START ").position);
        Assert.assertEquals(NotificationConfig.POSITION_BOTTOM_END, parse("notification-position", "bottom-end").position);
        Assert.assertEquals(NotificationConfig.POSITION_BOTTOM_START, parse("notification-position", "bottom-start").position);
    }

    @Test
    public void testPositions() {
        Assert.assertTrue(parse("notification-position", "top-end").isEndPosition());
        Assert.assertFalse(parse("notification-position", "top-end").isBottomPosition());
        Assert.assertFalse(parse("notification-position", "top-start").isEndPosition());
        Assert.assertFalse(parse("notification-position", "top-start").isBottomPosition());
        Assert.assertTrue(parse("notification-position", "bottom-start").isBottomPosition());
        Assert.assertFalse(parse("notification-position", "bottom-start").isEndPosition());
        Assert.assertTrue(parse("notification-position", "bottom-end").isBottomPosition());
    }

    @Test
    public void testOutOfRangeValuesFallBackToDefaults() {
        Assert.assertEquals(NotificationConfig.DEFAULT_MAX_VISIBLE, parse("notification-max-visible", "0").maxVisible);
        Assert.assertEquals(NotificationConfig.DEFAULT_MAX_VISIBLE, parse("notification-max-visible", "99").maxVisible);
        Assert.assertEquals(5, parse("notification-max-visible", "5").maxVisible);

        Assert.assertEquals(NotificationConfig.DEFAULT_DURATION_MS, parse("notification-duration", "-1").durationMs);
        Assert.assertEquals(NotificationConfig.MIN_DURATION_MS, parse("notification-duration", "0").durationMs);
        Assert.assertEquals(8000, parse("notification-duration", "8000").durationMs);

        Assert.assertEquals(NotificationConfig.DEFAULT_HISTORY_SIZE, parse("notification-history-size", "1000").historySize);
        Assert.assertEquals(0, parse("notification-history-size", "0").historySize);

        Assert.assertEquals(NotificationConfig.DEFAULT_MAX_VISIBLE, parse("notification-max-visible", "abc").maxVisible);
    }

    @Test
    public void testIgnoreCommandsListIsNormalised() {
        NotificationConfig config = parse("notification-exit-code-ignore-commands", " GREP, Test ,,grep ");

        Assert.assertEquals(2, config.exitCodeIgnoreCommands.size());
        Assert.assertTrue(config.exitCodeIgnoreCommands.contains("grep"));
        Assert.assertTrue(config.exitCodeIgnoreCommands.contains("test"));
    }

    @Test
    public void testEmptyIgnoreCommandsListDisablesTheFilter() {
        Assert.assertTrue(parse("notification-exit-code-ignore-commands", "").exitCodeIgnoreCommands.isEmpty());
    }

    @Test
    public void testUnknownEventsAreIgnored() {
        NotificationConfig config = parse("notification-events", "session-exit,unknown-event,Bell");
        Assert.assertEquals(2, config.events.size());
        Assert.assertTrue(config.events.contains(NotificationConfig.EVENT_SESSION_EXIT));
        Assert.assertTrue(config.events.contains(NotificationConfig.EVENT_BELL));
    }

    @Test
    public void testIsEventEnabled() {
        NotificationConfig config = parse("notification-events", "session-exit");
        Assert.assertTrue(config.isEventEnabled(NotificationConfig.EVENT_SESSION_EXIT));
        Assert.assertFalse(config.isEventEnabled(NotificationConfig.EVENT_TITLE));
    }

    @Test
    public void testOscAppAllowList() {
        NotificationConfig config = parse("notification-osc-allow-app", "nvim, com.termux ");
        Assert.assertTrue(config.isOscAppAllowed("nvim"));
        Assert.assertTrue(config.isOscAppAllowed("NVIM"));
        Assert.assertTrue(config.isOscAppAllowed("com.termux"));
        Assert.assertFalse(config.isOscAppAllowed("other"));
        Assert.assertFalse("An app name is required when an allow list is set", config.isOscAppAllowed(null));

        Assert.assertTrue("An empty allow list allows every application", parse().isOscAppAllowed("anything"));
        Assert.assertTrue("A wildcard allows every application", parse("notification-osc-allow-app", "*").isOscAppAllowed("anything"));
    }

    @Test
    public void testColorValuesAreKeptVerbatim() {
        NotificationConfig config = parse(
            "notification-background-color", "#ff112233",
            "notification-foreground-color", "theme:foreground",
            "notification-accent-failure-color", "theme:color9");

        Assert.assertEquals("#ff112233", config.backgroundColor);
        Assert.assertEquals("theme:foreground", config.foregroundColor);
        Assert.assertEquals("theme:color9", config.accentFailureColor);
    }
}
