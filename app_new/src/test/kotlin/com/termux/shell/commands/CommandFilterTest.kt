package com.termux.shell.commands

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CommandFilter] 的匹配规则测试。 */
class CommandFilterTest {

    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(CommandFilter.matches(null, "New tab", "Tab"))
        assertTrue(CommandFilter.matches("", "New tab", "Tab"))
        assertTrue(CommandFilter.matches("   ", "New tab", "Tab"))
    }

    @Test
    fun substringMatchesIgnoringCase() {
        assertTrue(CommandFilter.matches("tab", "New tab", "Tab"))
        assertTrue(CommandFilter.matches("NEW", "New tab", "Tab"))
    }

    @Test
    fun subsequenceMatchesAbbreviation() {
        assertTrue(CommandFilter.matches("nt", "New tab", "Tab"))
        assertTrue(CommandFilter.matches("splt", "Split right", "Pane"))
    }

    @Test
    fun allTokensMustMatch() {
        assertTrue(CommandFilter.matches("split pane", "Split right", "Pane"))
        assertFalse(CommandFilter.matches("split window", "Split right", "Pane"))
    }

    @Test
    fun matchesCategoryTextField() {
        assertTrue(CommandFilter.matches("pane", "Close pane", "Pane"))
        assertFalse(CommandFilter.matches("pane", "New window", "Window"))
    }

    @Test
    fun unmatchedQueryIsRejected() {
        assertFalse(CommandFilter.matches("zzz", "New tab", "Tab"))
    }

    @Test
    fun nullCandidateIsTolerated() {
        assertTrue(CommandFilter.matches("tab", "New tab", null))
        assertFalse(CommandFilter.matches("zzz", null, null))
    }
}
