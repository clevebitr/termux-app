package com.termux.app.terminal;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.terminal.TerminalEmulator;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the registration of the `keep-scroll-position-on-output` property, which decides whether the view
 * keeps its scroll position when output arrives while it is scrolled back into the transcript.
 *
 * A property key that is missing from one of the boolean property lists of
 * {@link TermuxPropertyConstants} makes {@code SharedProperties.loadPropertiesFromDisk()} throw an
 * {@link IllegalArgumentException} when the properties are loaded, which crashes the app on startup, so the
 * registration is tested instead of being reviewed by hand.
 */
public class KeepScrollPositionPropertyTest {

    @Test
    public void testKeyIsLoadedFromThePropertiesFile() {
        Assert.assertTrue(TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_KEEP_SCROLL_POSITION_ON_OUTPUT));
    }

    @Test
    public void testKeyDefaultsToTrue() {
        Assert.assertTrue(TermuxPropertyConstants.TERMUX_DEFAULT_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_KEEP_SCROLL_POSITION_ON_OUTPUT));
        Assert.assertFalse(TermuxPropertyConstants.TERMUX_DEFAULT_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_KEEP_SCROLL_POSITION_ON_OUTPUT));
        Assert.assertFalse(TermuxPropertyConstants.TERMUX_DEFAULT_INVERETED_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_KEEP_SCROLL_POSITION_ON_OUTPUT));
        Assert.assertFalse(TermuxPropertyConstants.TERMUX_DEFAULT_INVERETED_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_KEEP_SCROLL_POSITION_ON_OUTPUT));
    }

    @Test
    public void testPropertyName() {
        // The key is written into `termux.properties` by the settings page and is documented in FORK.md, so
        // it must not be renamed without updating both.
        Assert.assertEquals("keep-scroll-position-on-output",
            TermuxPropertyConstants.KEY_KEEP_SCROLL_POSITION_ON_OUTPUT);
    }

    @Test
    public void testDefaultTranscriptRowsIs5000() {
        // The upstream default of 2000 rows is filled quickly by a TUI or a build log, and the rows that are
        // scrolled past are lost. Every row costs about 1KB with 80 columns, so 5000 rows is about 5MB.
        Assert.assertEquals(5000, TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS);
        Assert.assertTrue(TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS >= TerminalEmulator.TERMINAL_TRANSCRIPT_ROWS_MIN);
        Assert.assertTrue(TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS <= TerminalEmulator.TERMINAL_TRANSCRIPT_ROWS_MAX);
        Assert.assertEquals(5000, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS);
    }
}
