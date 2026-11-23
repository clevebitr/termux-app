package com.termux.app.commands;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The filtering rule of the command palette. */
public class ShellCommandPaletteTest {

    private static ShellCommand command(String id, String title, String shortcut) {
        return new ShellCommand(id, title, "Terminal", shortcut, true, () -> { });
    }

    private static List<ShellCommand> all() {
        return Arrays.asList(
            command("search", "Search", null),
            command("sessions", "Sessions", null),
            command("settings", "Settings", "Ctrl+Shift+P"));
    }

    private static List<String> idsOf(List<ShellCommand> commands) {
        List<String> ids = new ArrayList<>();
        for (ShellCommand c : commands) ids.add(c.id);
        return ids;
    }

    @Test
    public void blankQueryKeepsEverything() {
        List<ShellCommand> out = new ArrayList<>();
        ShellCommandPalette.filter(all(), out, "   ");
        assertEquals(3, out.size());
    }

    @Test
    public void matchesTitleCaseInsensitively() {
        List<ShellCommand> out = new ArrayList<>();
        ShellCommandPalette.filter(all(), out, "SESS");
        assertEquals(Collections.singletonList("sessions"), idsOf(out));
    }

    @Test
    public void matchesShortcutToo() {
        List<ShellCommand> out = new ArrayList<>();
        ShellCommandPalette.filter(all(), out, "shift+p");
        assertEquals(Collections.singletonList("settings"), idsOf(out));
    }

    @Test
    public void noMatchProducesEmptyList() {
        List<ShellCommand> out = new ArrayList<>();
        ShellCommandPalette.filter(all(), out, "nothing-matches");
        assertTrue(out.isEmpty());
    }
}
