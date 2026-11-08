package com.termux.app.terminal;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the registration of the `shell-history-sync` property, which the shell sessions are started with.
 *
 * A property key that is missing from one of the boolean property lists of
 * {@link TermuxPropertyConstants} makes {@code SharedProperties.loadPropertiesFromDisk()} throw an
 * {@link IllegalArgumentException} when the properties are loaded, which crashes the app on startup, so the
 * registration is tested instead of being reviewed by hand.
 */
public class ShellHistorySyncTest {

    @Test
    public void testKeyIsLoadedFromThePropertiesFile() {
        Assert.assertTrue(TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_SHELL_HISTORY_SYNC));
    }

    @Test
    public void testKeyDefaultsToTrue() {
        Assert.assertTrue(TermuxPropertyConstants.TERMUX_DEFAULT_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_SHELL_HISTORY_SYNC));
        Assert.assertFalse(TermuxPropertyConstants.TERMUX_DEFAULT_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_SHELL_HISTORY_SYNC));
        Assert.assertFalse(TermuxPropertyConstants.TERMUX_DEFAULT_INVERETED_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_SHELL_HISTORY_SYNC));
        Assert.assertFalse(TermuxPropertyConstants.TERMUX_DEFAULT_INVERETED_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_SHELL_HISTORY_SYNC));
    }

    @Test
    public void testPropertyName() {
        // The key is written into `termux.properties` by the settings page and is documented in FORK.md, so
        // it must not be renamed without updating both.
        Assert.assertEquals("shell-history-sync", TermuxPropertyConstants.KEY_SHELL_HISTORY_SYNC);
    }

    @Test
    public void testPromptCommandAppendsAndReadsTheHistory() {
        String promptCommand = TermuxShellEnvironment.PROMPT_COMMAND_HISTORY_SYNC;

        // `history -a` writes the commands of this session to the history file, so that they are not lost
        // when the app kills the shell, and `history -n` reads the commands that the other sessions wrote.
        Assert.assertTrue(promptCommand.contains("history -a"));
        Assert.assertTrue(promptCommand.contains("history -n"));
        Assert.assertTrue(promptCommand.indexOf("history -a") < promptCommand.indexOf("history -n"));
    }
}
