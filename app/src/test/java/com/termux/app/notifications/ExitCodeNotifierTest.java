package com.termux.app.notifications;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the helpers that turn a finished command into the text of its notification card, which are
 * pure functions so that they can be tested without an emulator or a device.
 */
public class ExitCodeNotifierTest {

    @Test
    public void testStripBashPrompt() {
        Assert.assertEquals("ls -la", ExitCodeNotifier.stripPrompt("user@host:~$ ls -la"));
        Assert.assertEquals("make -j8", ExitCodeNotifier.stripPrompt("~ $ make -j8"));
        Assert.assertEquals("apt update", ExitCodeNotifier.stripPrompt("root@localhost:/# apt update"));
    }

    @Test
    public void testStripFancyPrompt() {
        Assert.assertEquals("git status", ExitCodeNotifier.stripPrompt("\u276f git status"));
        Assert.assertEquals("git status", ExitCodeNotifier.stripPrompt("\u279c git status"));
        Assert.assertEquals("git status", ExitCodeNotifier.stripPrompt("\u03bb git status"));
        Assert.assertEquals("git status", ExitCodeNotifier.stripPrompt("% git status"));
    }

    @Test
    public void testStripPromptKeepsTheCommandWhenThereIsNoPrompt() {
        Assert.assertEquals("ls -la", ExitCodeNotifier.stripPrompt("ls -la"));
        Assert.assertEquals("", ExitCodeNotifier.stripPrompt(""));
    }

    @Test
    public void testStripPromptIgnoresPromptEndingsFarIntoTheCommand() {
        // A `$ ` that is more than the maximum prompt length into the row is part of the command, so
        // the row must be returned as is instead of cutting the command in half.
        StringBuilder command = new StringBuilder();
        for (int i = 0; i < ExitCodeNotifier.MAX_PROMPT_LENGTH + 10; i++) command.append('a');
        command.append("$ echo hi");

        Assert.assertEquals(command.toString(), ExitCodeNotifier.stripPrompt(command.toString()));
    }

    @Test
    public void testSanitizeCollapsesControlCharacters() {
        Assert.assertEquals("echo a b", ExitCodeNotifier.sanitizeCommand("echo\ta\nb"));
        Assert.assertEquals("ls -la", ExitCodeNotifier.sanitizeCommand("  ls   -la  "));
        Assert.assertEquals("ls -la", ExitCodeNotifier.sanitizeCommand("ls -la"));
    }

    @Test
    public void testSanitizeLimitsTheLength() {
        StringBuilder command = new StringBuilder();
        for (int i = 0; i < ExitCodeNotifier.MAX_COMMAND_LENGTH + 50; i++) command.append('x');

        String sanitized = ExitCodeNotifier.sanitizeCommand(command.toString());
        Assert.assertEquals(ExitCodeNotifier.MAX_COMMAND_LENGTH + 3, sanitized.length());
        Assert.assertTrue(sanitized.endsWith("..."));
    }

    @Test
    public void testIgnoredCommands() {
        NotificationConfig config = new NotificationConfig();
        config.exitCodeIgnoreCommands.add("grep");
        config.exitCodeIgnoreCommands.add("[");

        Assert.assertTrue(ExitCodeNotifier.isIgnoredCommand("grep foo bar", config));
        Assert.assertTrue("The command name is matched without the directory it is in",
            ExitCodeNotifier.isIgnoredCommand("/system/bin/grep foo", config));
        Assert.assertTrue("A leading sudo is skipped, the command that runs is what matters",
            ExitCodeNotifier.isIgnoredCommand("sudo grep foo", config));
        Assert.assertTrue(ExitCodeNotifier.isIgnoredCommand("[ -f file ]", config));
        Assert.assertFalse(ExitCodeNotifier.isIgnoredCommand("git push", config));
        Assert.assertFalse(ExitCodeNotifier.isIgnoredCommand("", config));
    }

    @Test
    public void testIgnoredCommandsAreCaseInsensitive() {
        NotificationConfig config = new NotificationConfig();
        config.exitCodeIgnoreCommands.add("test");

        Assert.assertTrue(ExitCodeNotifier.isIgnoredCommand("TEST -f file", config));
    }

    @Test
    public void testEmptyIgnoreListDisablesTheFilter() {
        Assert.assertFalse(ExitCodeNotifier.isIgnoredCommand("grep foo", new NotificationConfig()));
    }

    @Test
    public void testFormatDuration() {
        Assert.assertEquals("0.4s", ExitCodeNotifier.formatDuration(400));
        Assert.assertEquals("9.9s", ExitCodeNotifier.formatDuration(9900));
        Assert.assertEquals("0.0s", ExitCodeNotifier.formatDuration(0));
        Assert.assertEquals("12s", ExitCodeNotifier.formatDuration(12345));
        Assert.assertEquals("59s", ExitCodeNotifier.formatDuration(59499));
        Assert.assertEquals("1m05s", ExitCodeNotifier.formatDuration(65000));
        Assert.assertEquals("1h01m", ExitCodeNotifier.formatDuration(3660000));
    }
}
