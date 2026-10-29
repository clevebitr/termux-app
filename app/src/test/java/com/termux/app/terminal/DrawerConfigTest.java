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
    public void testNullPropertiesUseTheDefaults() {
        DrawerConfig config = DrawerConfig.parse(null);

        Assert.assertEquals(DrawerConfig.DEFAULT_MODE, config.mode);
    }
}
