package com.termux.app.notifications;

import com.termux.terminal.TerminalNotification;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the decisions that are taken before a notification is shown, since a wrong decision either shows
 * a card that the user disabled or hides one that the user asked for, and because the rules are spread
 * over the manager, the `OSC` handler and the app event notifier.
 *
 * The tests are pure, so no activity, view or terminal session is needed.
 */
public class NotificationPolicyTest {

    private static TerminalNotification notification(TerminalNotification.Source source, String title) {
        return new TerminalNotification.Builder(TerminalNotification.Kind.INFO, source)
            .setTitle(title)
            .build();
    }

    private static TerminalNotification notification(TerminalNotification.Source source, String title,
                                                     String appName) {
        return new TerminalNotification.Builder(TerminalNotification.Kind.INFO, source)
            .setTitle(title)
            .setAppName(appName)
            .build();
    }

    /** A configuration with the master switch and one channel enabled, like the user would set it. */
    private static NotificationConfig enabledConfig() {
        NotificationConfig config = new NotificationConfig();
        config.enabled = true;
        return config;
    }

    @Test
    public void testMasterSwitchDisablesEveryChannel() {
        NotificationConfig config = enabledConfig();
        config.enabled = false;

        Assert.assertFalse(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.EXIT_CODE, "make")));
        Assert.assertFalse(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.OSC, "Build finished")));
        Assert.assertFalse(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.APP_EVENT, "Session exited")));
    }

    @Test
    public void testEachSourceHasItsOwnSwitch() {
        NotificationConfig config = enabledConfig();

        config.exitCodeEnabled = false;
        config.oscEnabled = true;
        config.eventsEnabled = true;
        Assert.assertFalse(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.EXIT_CODE, "make")));
        Assert.assertTrue(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.OSC, "Build finished")));
        Assert.assertTrue(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.APP_EVENT, "Session exited")));

        config.exitCodeEnabled = true;
        config.oscEnabled = false;
        config.eventsEnabled = false;
        Assert.assertTrue(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.EXIT_CODE, "make")));
        Assert.assertFalse(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.OSC, "Build finished")));
        Assert.assertFalse(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.APP_EVENT, "Session exited")));
    }

    @Test
    public void testOscNotificationsOfOtherAppsCanBeBlocked() {
        NotificationConfig config = enabledConfig();
        config.oscEnabled = true;

        // An empty allow list allows every application, which is the default.
        Assert.assertTrue(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.OSC, "Build finished", "nvim")));

        config.oscAllowApp.add("nvim");
        Assert.assertTrue(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.OSC, "Build finished", "nvim")));
        Assert.assertFalse(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.OSC, "Build finished", "weechat")));
        // A notification without an application name cannot match a non empty allow list.
        Assert.assertFalse(NotificationPolicy.isChannelEnabled(config,
            notification(TerminalNotification.Source.OSC, "Build finished")));
    }

    @Test
    public void testEmptyNotificationsAreIgnored() {
        NotificationConfig config = enabledConfig();
        config.exitCodeEnabled = true;

        Assert.assertEquals(NotificationPolicy.Route.IGNORED, NotificationPolicy.route(config,
            notification(TerminalNotification.Source.EXIT_CODE, null), true));
        // Whitespace is not content either, since a card of spaces tells the user nothing.
        Assert.assertEquals(NotificationPolicy.Route.IGNORED, NotificationPolicy.route(config,
            notification(TerminalNotification.Source.EXIT_CODE, "  \n\t "), true));
        Assert.assertEquals(NotificationPolicy.Route.CARD, NotificationPolicy.route(config,
            notification(TerminalNotification.Source.EXIT_CODE, "make"), true));
    }

    @Test
    public void testVisibleAppShowsCards() {
        NotificationConfig config = enabledConfig();
        config.exitCodeEnabled = true;
        config.backgroundEnabled = true;

        Assert.assertEquals(NotificationPolicy.Route.CARD, NotificationPolicy.route(config,
            notification(TerminalNotification.Source.EXIT_CODE, "make"), true));
    }

    @Test
    public void testHiddenAppUsesTheShadeOnlyWhenEnabled() {
        NotificationConfig config = enabledConfig();
        config.exitCodeEnabled = true;
        TerminalNotification notification = notification(TerminalNotification.Source.EXIT_CODE, "make");

        config.backgroundEnabled = false;
        Assert.assertEquals(NotificationPolicy.Route.CARD,
            NotificationPolicy.route(config, notification, false));

        config.backgroundEnabled = true;
        Assert.assertEquals(NotificationPolicy.Route.BACKGROUND,
            NotificationPolicy.route(config, notification, false));
    }

    @Test
    public void testDisabledChannelIsIgnoredEvenWhenTheAppIsHidden() {
        NotificationConfig config = enabledConfig();
        config.exitCodeEnabled = false;
        config.backgroundEnabled = true;

        Assert.assertEquals(NotificationPolicy.Route.IGNORED, NotificationPolicy.route(config,
            notification(TerminalNotification.Source.EXIT_CODE, "make"), false));
    }

    @Test
    public void testOccasion() {
        Assert.assertTrue(NotificationPolicy.isOccasionAllowed(
            TerminalNotification.Occasion.ALWAYS, true, true));
        Assert.assertTrue(NotificationPolicy.isOccasionAllowed(
            TerminalNotification.Occasion.ALWAYS, false, false));

        // `unfocused` and `invisible` request the notification for the times when the user cannot see the
        // card, which is the opposite of what the cards do, so they are dropped in those cases.
        Assert.assertFalse(NotificationPolicy.isOccasionAllowed(
            TerminalNotification.Occasion.UNFOCUSED, true, true));
        Assert.assertTrue(NotificationPolicy.isOccasionAllowed(
            TerminalNotification.Occasion.UNFOCUSED, false, true));

        Assert.assertFalse(NotificationPolicy.isOccasionAllowed(
            TerminalNotification.Occasion.INVISIBLE, true, true));
        Assert.assertTrue(NotificationPolicy.isOccasionAllowed(
            TerminalNotification.Occasion.INVISIBLE, true, false));
    }

    @Test
    public void testEventEnabled() {
        NotificationConfig config = new NotificationConfig();
        config.events.add(NotificationConfig.EVENT_BELL);

        Assert.assertFalse(NotificationPolicy.isEventEnabled(config, NotificationConfig.EVENT_BELL));

        config.enabled = true;
        Assert.assertFalse(NotificationPolicy.isEventEnabled(config, NotificationConfig.EVENT_BELL));

        config.eventsEnabled = true;
        Assert.assertTrue(NotificationPolicy.isEventEnabled(config, NotificationConfig.EVENT_BELL));
        Assert.assertFalse(NotificationPolicy.isEventEnabled(config, NotificationConfig.EVENT_SESSION_EXIT));
    }

    @Test
    public void testExitStatusMeta() {
        String separator = "\u00b7";
        String exitStatusText = "exit 2";

        // A command that succeeded only has its working directory in the details line.
        Assert.assertEquals("/home", NotificationPolicy.buildExitStatusMeta("/home", 0, separator, exitStatusText));
        Assert.assertNull(NotificationPolicy.buildExitStatusMeta(null, 0, separator, exitStatusText));

        Assert.assertEquals("/home " + separator + " exit 2",
            NotificationPolicy.buildExitStatusMeta("/home", 2, separator, exitStatusText));
        Assert.assertEquals("exit 2",
            NotificationPolicy.buildExitStatusMeta(null, 2, separator, exitStatusText));
        // An empty working directory must not leave a dangling separator at the start of the line.
        Assert.assertEquals("exit 2",
            NotificationPolicy.buildExitStatusMeta("  ", 2, separator, exitStatusText));
    }
}
