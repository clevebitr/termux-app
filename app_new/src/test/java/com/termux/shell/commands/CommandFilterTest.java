package com.termux.shell.commands;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link CommandFilter} 的匹配规则测试。
 */
public final class CommandFilterTest {

    @Test
    public void emptyQueryMatchesEverything() {
        assertTrue(CommandFilter.matches(null, "New tab", "Tab"));
        assertTrue(CommandFilter.matches("", "New tab", "Tab"));
        assertTrue(CommandFilter.matches("   ", "New tab", "Tab"));
    }

    @Test
    public void substringMatchesIgnoringCase() {
        assertTrue(CommandFilter.matches("tab", "New tab", "Tab"));
        assertTrue(CommandFilter.matches("NEW", "New tab", "Tab"));
    }

    @Test
    public void subsequenceMatchesAbbreviation() {
        assertTrue(CommandFilter.matches("nt", "New tab", "Tab"));
        assertTrue(CommandFilter.matches("splt", "Split right", "Pane"));
    }

    @Test
    public void allTokensMustMatch() {
        assertTrue(CommandFilter.matches("split pane", "Split right", "Pane"));
        assertFalse(CommandFilter.matches("split window", "Split right", "Pane"));
    }

    @Test
    public void matchesCategoryTextField() {
        assertTrue(CommandFilter.matches("pane", "Close pane", "Pane"));
        assertFalse(CommandFilter.matches("pane", "New window", "Window"));
    }

    @Test
    public void unmatchedQueryIsRejected() {
        assertFalse(CommandFilter.matches("zzz", "New tab", "Tab"));
    }

    @Test
    public void nullCandidateIsTolerated() {
        assertTrue(CommandFilter.matches("tab", "New tab", null));
        assertFalse(CommandFilter.matches("zzz", null, null));
    }
}
