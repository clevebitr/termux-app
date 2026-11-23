package com.termux.app.commands;

import com.termux.app.terminal.menu.TerminalMenuAction;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Guards the mapping from a terminal menu action to its implementation.
 *
 * <p>The mapping replaced a switch, whose missing branch silently did nothing; a dropped action would now
 * only surface when the user picks it. The test therefore drives every value of the enum through the runner
 * and checks which host method ran, which also fails when an action is added without an implementation.</p>
 */
public class TerminalActionRunnerTest {

    /** Records the name of every host method that ran, so that the mapping can be checked without a UI. */
    private static final class RecordingHost implements TerminalActionRunner.Host {

        final List<String> calls = new ArrayList<>();

        @Override public void showTerminalSearch() { calls.add("showTerminalSearch"); }
        @Override public void openSessionsDrawer() { calls.add("openSessionsDrawer"); }
        @Override public void showCommandPalette() { calls.add("showCommandPalette"); }
        @Override public void scrollToPreviousCommand() { calls.add("scrollToPreviousCommand"); }
        @Override public void scrollToNextCommand() { calls.add("scrollToNextCommand"); }
        @Override public void splitScreenVertically() { calls.add("splitScreenVertically"); }
        @Override public void splitScreenHorizontally() { calls.add("splitScreenHorizontally"); }
        @Override public void closeSplitScreen() { calls.add("closeSplitScreen"); }
        @Override public void selectUrl() { calls.add("selectUrl"); }
        @Override public void shareTranscript() { calls.add("shareTranscript"); }
        @Override public void shareSelectedText() { calls.add("shareSelectedText"); }
        @Override public void autofillUsername() { calls.add("autofillUsername"); }
        @Override public void autofillPassword() { calls.add("autofillPassword"); }
        @Override public void resetTerminal() { calls.add("resetTerminal"); }
        @Override public void killSession() { calls.add("killSession"); }
        @Override public void showStylingDialog() { calls.add("showStylingDialog"); }
        @Override public void toggleKeepScreenOn() { calls.add("toggleKeepScreenOn"); }
        @Override public void openHelp() { calls.add("openHelp"); }
        @Override public void openSettings() { calls.add("openSettings"); }
        @Override public void reportIssue() { calls.add("reportIssue"); }
        @Override public void showRecentNotifications() { calls.add("showRecentNotifications"); }
        @Override public void sendTestNotification() { calls.add("sendTestNotification"); }
    }

    /** The host method that every action has to run. */
    private static final Map<TerminalMenuAction, String> EXPECTED = new EnumMap<>(TerminalMenuAction.class);

    static {
        EXPECTED.put(TerminalMenuAction.SEARCH, "showTerminalSearch");
        EXPECTED.put(TerminalMenuAction.SESSIONS, "openSessionsDrawer");
        EXPECTED.put(TerminalMenuAction.COMMAND_PALETTE, "showCommandPalette");
        EXPECTED.put(TerminalMenuAction.PREVIOUS_COMMAND, "scrollToPreviousCommand");
        EXPECTED.put(TerminalMenuAction.NEXT_COMMAND, "scrollToNextCommand");
        EXPECTED.put(TerminalMenuAction.SPLIT_VERTICAL, "splitScreenVertically");
        EXPECTED.put(TerminalMenuAction.SPLIT_HORIZONTAL, "splitScreenHorizontally");
        EXPECTED.put(TerminalMenuAction.SPLIT_CLOSE, "closeSplitScreen");
        EXPECTED.put(TerminalMenuAction.SELECT_URL, "selectUrl");
        EXPECTED.put(TerminalMenuAction.SHARE_TRANSCRIPT, "shareTranscript");
        EXPECTED.put(TerminalMenuAction.SHARE_SELECTED_TEXT, "shareSelectedText");
        EXPECTED.put(TerminalMenuAction.AUTOFILL_USERNAME, "autofillUsername");
        EXPECTED.put(TerminalMenuAction.AUTOFILL_PASSWORD, "autofillPassword");
        EXPECTED.put(TerminalMenuAction.RESET_TERMINAL, "resetTerminal");
        EXPECTED.put(TerminalMenuAction.KILL_PROCESS, "killSession");
        EXPECTED.put(TerminalMenuAction.STYLING, "showStylingDialog");
        EXPECTED.put(TerminalMenuAction.KEEP_SCREEN_ON, "toggleKeepScreenOn");
        EXPECTED.put(TerminalMenuAction.HELP, "openHelp");
        EXPECTED.put(TerminalMenuAction.SETTINGS, "openSettings");
        EXPECTED.put(TerminalMenuAction.REPORT, "reportIssue");
        EXPECTED.put(TerminalMenuAction.RECENT_NOTIFICATIONS, "showRecentNotifications");
        EXPECTED.put(TerminalMenuAction.TEST_NOTIFICATION, "sendTestNotification");
    }

    @Test
    public void testEveryActionRunsExactlyOneHostMethod() {
        for (TerminalMenuAction action : TerminalMenuAction.values()) {
            RecordingHost host = new RecordingHost();
            new TerminalActionRunner(host).run(action);

            Assert.assertEquals("The action \"" + action.getId() + "\" must run exactly one host method",
                1, host.calls.size());
            Assert.assertEquals("The action \"" + action.getId() + "\" runs the wrong host method",
                EXPECTED.get(action), host.calls.get(0));
        }
    }

    @Test
    public void testEveryActionIsHandled() {
        TerminalActionRunner runner = new TerminalActionRunner(new RecordingHost());
        for (TerminalMenuAction action : TerminalMenuAction.values()) {
            Assert.assertTrue("The action \"" + action.getId() + "\" has no implementation", runner.handles(action));
        }
    }

    @Test
    public void testTheExpectedTableCoversEveryAction() {
        for (TerminalMenuAction action : TerminalMenuAction.values()) {
            Assert.assertTrue("The action \"" + action.getId() + "\" is missing from the expectations",
                EXPECTED.containsKey(action));
        }
        Assert.assertEquals(TerminalMenuAction.values().length, EXPECTED.size());
    }
}
