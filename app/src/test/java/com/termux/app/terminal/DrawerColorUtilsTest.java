package com.termux.app.terminal;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the colors of the sessions drawer, which are derived from the color scheme of the terminal, since a
 * color scheme with a light background must not produce a drawer with an unreadable text and the accent of
 * the current session must stay visible on any background.
 */
public class DrawerColorUtilsTest {

    @Test
    public void testWithAlpha() {
        Assert.assertEquals(0x8000FF00, DrawerColorUtils.withAlpha(0xFF00FF00, 0x80));
        // A color without an alpha channel, like the value that a color scheme may contain.
        Assert.assertEquals(0xB300FF00, DrawerColorUtils.withAlpha(0x0000FF00, 0xB3));
        Assert.assertEquals(0x0000FF00, DrawerColorUtils.withAlpha(0xFF00FF00, 0));
        Assert.assertEquals(0xFF00FF00, DrawerColorUtils.withAlpha(0xFF00FF00, 0xFF));
        // Out of range values must not wrap around into a fully transparent or a fully opaque color.
        Assert.assertEquals(0x0000FF00, DrawerColorUtils.withAlpha(0xFF00FF00, -10));
        Assert.assertEquals(0xFF00FF00, DrawerColorUtils.withAlpha(0xFF00FF00, 300));
    }

    @Test
    public void testBlend() {
        int base = 0xFF000000;
        int accent = 0xFFFFFFFF;

        Assert.assertEquals(base, DrawerColorUtils.blend(base, accent, 0f));
        Assert.assertEquals(accent, DrawerColorUtils.blend(base, accent, 1f));
        Assert.assertEquals(0xFF808080, DrawerColorUtils.blend(base, accent, 0.5f));
        // The ratio is clamped, so a value from a drag or from a broken config cannot invert the colors.
        Assert.assertEquals(base, DrawerColorUtils.blend(base, accent, -1f));
        Assert.assertEquals(accent, DrawerColorUtils.blend(base, accent, 5f));
    }

    @Test
    public void testIsLightBackground() {
        Assert.assertTrue(DrawerColorUtils.isLightBackground(0xFFFFFFFF));
        Assert.assertTrue(DrawerColorUtils.isLightBackground(0xFFF0F0F0));
        Assert.assertFalse(DrawerColorUtils.isLightBackground(0xFF000000));
        Assert.assertFalse(DrawerColorUtils.isLightBackground(0xFF202020));
        // A saturated green is bright even though its blue channel is zero.
        Assert.assertTrue(DrawerColorUtils.isLightBackground(0xFF00FF00));
    }

    @Test
    public void testContrastingTextColor() {
        Assert.assertEquals(0xFF212121, DrawerColorUtils.contrastingTextColor(0xFFFFFFFF));
        Assert.assertEquals(0xFFEEEEEE, DrawerColorUtils.contrastingTextColor(0xFF000000));
    }

    @Test
    public void testVisibleAccentColor() {
        // An accent that is the same color as the background would make the strip of the current session
        // invisible, so the text color is used instead.
        Assert.assertEquals(0xFFEEEEEE, DrawerColorUtils.visibleAccentColor(0xFF000000, 0xFF000000));
        Assert.assertEquals(0xFF0000FF, DrawerColorUtils.visibleAccentColor(0xFF0000FF, 0xFF000000));
    }
}
