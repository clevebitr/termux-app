package com.termux.app.terminal;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests the history of the search bar, since the list is stored as a single string and a broken
 * round trip would either lose the recent queries or grow the list without bounds.
 *
 * The list operations are pure, so no context is needed.
 */
public class TerminalSearchPreferencesTest {

    @Test
    public void testQueryIsAddedToTheFront() {
        List<String> history = TerminalSearchPreferences.addQuery(Arrays.asList("make", "git"), "ls");

        Assert.assertEquals(Arrays.asList("ls", "make", "git"), history);
    }

    @Test
    public void testAnExistingQueryIsMovedToTheFront() {
        List<String> history = TerminalSearchPreferences.addQuery(Arrays.asList("make", "git"), "git");

        Assert.assertEquals(Arrays.asList("git", "make"), history);
    }

    @Test
    public void testAnExistingQueryWithOtherCaseIsNotDuplicated() {
        // The search ignores case by default, so "Git" and "git" are the same query for the user.
        List<String> history = TerminalSearchPreferences.addQuery(Arrays.asList("make", "git"), "Git");

        Assert.assertEquals(Arrays.asList("Git", "make"), history);
    }

    @Test
    public void testHistoryIsCapped() {
        List<String> history = new ArrayList<>();
        for (int i = 0; i < TerminalSearchPreferences.MAX_HISTORY + 5; i++)
            history = TerminalSearchPreferences.addQuery(history, "query" + i);

        Assert.assertEquals(TerminalSearchPreferences.MAX_HISTORY, history.size());
        Assert.assertEquals("query" + (TerminalSearchPreferences.MAX_HISTORY + 4), history.get(0));
    }

    @Test
    public void testBlankQueriesAreIgnored() {
        List<String> history = Arrays.asList("make");

        Assert.assertEquals(history, TerminalSearchPreferences.addQuery(history, null));
        Assert.assertEquals(history, TerminalSearchPreferences.addQuery(history, ""));
        Assert.assertEquals(history, TerminalSearchPreferences.addQuery(history, "   "));
    }

    @Test
    public void testLineBreaksAreReplaced() {
        // The queries are stored one per line, so a pasted multi line query must not break the list.
        List<String> history = TerminalSearchPreferences.addQuery(new ArrayList<>(), "a\nb\r\nc");

        Assert.assertEquals(1, history.size());
        Assert.assertEquals("a b  c", history.get(0));
    }

    @Test
    public void testRoundTrip() {
        List<String> history = Arrays.asList("git log", "a.*b", "  spaces  ");

        String stored = TerminalSearchPreferences.joinHistory(history);
        Assert.assertEquals(history, TerminalSearchPreferences.splitHistory(stored));
    }

    @Test
    public void testSplitIgnoresEmptyAndDuplicateEntries() {
        Assert.assertTrue(TerminalSearchPreferences.splitHistory(null).isEmpty());
        Assert.assertTrue(TerminalSearchPreferences.splitHistory("").isEmpty());

        // A hand edited file could contain blank lines and duplicates.
        List<String> history = TerminalSearchPreferences.splitHistory("git\n\ngit\nls");
        Assert.assertEquals(Arrays.asList("git", "ls"), history);
    }
}
