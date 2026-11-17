package com.termux.app.terminal;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the registration of the `mouse-hover-sends-mouse-motion` property, which decides whether mouse
 * movement without a pressed button is reported to terminal programs that asked for it with `DECSET 1003`.
 *
 * A property key that is missing from one of the boolean property lists of
 * {@link TermuxPropertyConstants} makes {@code SharedProperties.loadPropertiesFromDisk()} throw an
 * {@link IllegalArgumentException} when the properties are loaded, which crashes the app on startup, so the
 * registration is tested instead of being reviewed by hand.
 */
public class MouseHoverMotionPropertyTest {

    @Test
    public void testKeyIsLoadedFromThePropertiesFile() {
        Assert.assertTrue(TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_MOUSE_HOVER_SENDS_MOUSE_MOTION));
    }

    @Test
    public void testKeyDefaultsToFalse() {
        // Reporting hover movement costs main thread time for every event, so it is opt-in.
        Assert.assertTrue(TermuxPropertyConstants.TERMUX_DEFAULT_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_MOUSE_HOVER_SENDS_MOUSE_MOTION));
        Assert.assertFalse(TermuxPropertyConstants.TERMUX_DEFAULT_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_MOUSE_HOVER_SENDS_MOUSE_MOTION));
        Assert.assertFalse(TermuxPropertyConstants.TERMUX_DEFAULT_INVERETED_FALSE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_MOUSE_HOVER_SENDS_MOUSE_MOTION));
        Assert.assertFalse(TermuxPropertyConstants.TERMUX_DEFAULT_INVERETED_TRUE_BOOLEAN_BEHAVIOUR_PROPERTIES_LIST
            .contains(TermuxPropertyConstants.KEY_MOUSE_HOVER_SENDS_MOUSE_MOTION));
    }

    @Test
    public void testPropertyName() {
        // The key is written into `termux.properties` by the settings page and is documented in FORK.md, so
        // it must not be renamed without updating both.
        Assert.assertEquals("mouse-hover-sends-mouse-motion",
            TermuxPropertyConstants.KEY_MOUSE_HOVER_SENDS_MOUSE_MOTION);
    }
}
