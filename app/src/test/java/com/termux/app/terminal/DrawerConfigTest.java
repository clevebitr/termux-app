package com.termux.app.terminal;

import com.termux.shared.logger.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

/**
 * Tests the parsing of the `drawer-open-mode` property, since a mode that disables the wrong thing either
 * leaves the drawer impossible to open or makes the edge swipe close the app window instead of opening the
 * drawer, which is what the property exists to avoid.
 */
public class DrawerConfigTest {

    private int mPreviousLogLevel;

    @Before
    public void disableLogging() {
        mPreviousLogLevel = Logger.getLogLevel();
        Logger.setLogLevel(null, Logger.LOG_LEVEL_OFF);
    }

    @After
    public void restoreLogging() {
        Logger.setLogLevel(null, mPreviousLogLevel);
    }

    private static DrawerConfig parse(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2)
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        return DrawerConfig.parse(properties);
    }

    @Test
    public void testDefaultShowsBoth() {
        DrawerConfig config = parse();

        Assert.assertEquals(DrawerConfig.MODE_AUTO, config.mode);
        Assert.assertTrue(config.isHandleVisible());
        Assert.assertTrue(config.isEdgeSwipeEnabled());
    }

    @Test
    public void testModes() {
        DrawerConfig handle = parse("drawer-open-mode", "handle");
        Assert.assertTrue(handle.isHandleVisible());
        Assert.assertFalse(handle.isEdgeSwipeEnabled());

        DrawerConfig edge = parse("drawer-open-mode", "edge");
        Assert.assertFalse(edge.isHandleVisible());
        Assert.assertTrue(edge.isEdgeSwipeEnabled());

        DrawerConfig none = parse("drawer-open-mode", "none");
        Assert.assertFalse(none.isHandleVisible());
        Assert.assertFalse(none.isEdgeSwipeEnabled());
    }

    @Test
    public void testModeIsCaseAndWhitespaceInsensitive() {
        DrawerConfig config = parse("drawer-open-mode", "  HANDLE \n");

        Assert.assertEquals(DrawerConfig.MODE_HANDLE, config.mode);
    }

    @Test
    public void testInvalidModeFallsBackToTheDefault() {
        DrawerConfig config = parse("drawer-open-mode", "disabled");

        Assert.assertEquals(DrawerConfig.DEFAULT_MODE, config.mode);
        Assert.assertTrue(config.isHandleVisible());
        Assert.assertTrue(config.isEdgeSwipeEnabled());
    }

    @Test
    public void testHandleDefaults() {
        DrawerConfig config = parse();

        Assert.assertEquals(DrawerConfig.DEFAULT_HANDLE_LENGTH_DP, config.handleLengthDp);
        Assert.assertEquals(DrawerConfig.DEFAULT_HANDLE_OPACITY_PERCENT, config.handleOpacityPercent);
        Assert.assertEquals(DrawerConfig.DEFAULT_HANDLE_POSITION_PERCENT, config.handlePositionPercent);
    }

    @Test
    public void testHandleIsConfigured() {
        DrawerConfig config = parse("drawer-handle-length", "140", "drawer-handle-opacity", "80",
            "drawer-handle-position", "20");

        Assert.assertEquals(140, config.handleLengthDp);
        Assert.assertEquals(80, config.handleOpacityPercent);
        Assert.assertEquals(20, config.handlePositionPercent);
    }

    @Test
    public void testOutOfRangeHandleValuesUseTheDefaults() {
        // The length is limited, since the system only excludes a small area of the edge from its gestures,
        // and an out of range value of any of the properties falls back to the default instead of being
        // clamped, like for the other properties of the fork.
        DrawerConfig config = parse("drawer-handle-length", "1000", "drawer-handle-opacity", "0",
            "drawer-handle-position", "-10");

        Assert.assertEquals(DrawerConfig.DEFAULT_HANDLE_LENGTH_DP, config.handleLengthDp);
        Assert.assertEquals(DrawerConfig.DEFAULT_HANDLE_OPACITY_PERCENT, config.handleOpacityPercent);
        Assert.assertEquals(DrawerConfig.DEFAULT_HANDLE_POSITION_PERCENT, config.handlePositionPercent);
    }

    @Test
    public void testNullPropertiesUseTheDefaults() {
        DrawerConfig config = DrawerConfig.parse(null);

        Assert.assertEquals(DrawerConfig.DEFAULT_MODE, config.mode);
    }
}
