package com.termux.app.statusbar;

import com.termux.shared.logger.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Tests the parsing and formatting of the status bar configuration, since the status bar can be
 * configured with a lot of properties and an invalid value must never break the app.
 *
 * The tests do not need a {@code RobolectricTestRunner}, since only the properties are parsed, so
 * the android logging is turned off instead of being mocked.
 */
public class StatusBarTest {

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

    private StatusBarConfig parse(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return StatusBarConfig.parse(properties);
    }

    @Test
    public void testDefaultConfig() {
        StatusBarConfig config = parse();

        Assert.assertFalse(config.enabled);
        Assert.assertFalse(config.positionBottom);
        Assert.assertFalse(config.overlay);
        Assert.assertEquals(StatusBarConfig.DEFAULT_HEIGHT_DP, config.heightDp);
        Assert.assertEquals(StatusBarConfig.DEFAULT_FONT_SIZE_SP, config.fontSizeSp);
        Assert.assertEquals(StatusBarConfig.DEFAULT_PADDING_HORIZONTAL_DP, config.paddingHorizontalDp);
        Assert.assertEquals(StatusBarConfig.ALIGN_START, config.align);
        Assert.assertTrue(config.showIcons);
        Assert.assertFalse(config.iconsAtEnd);

        // The default segments, the ip and proxy segments are opt-in.
        Assert.assertEquals(5, config.segments.size());
        Assert.assertEquals(StatusBarConfig.SEGMENT_CLOCK, config.segments.get(0).id);
        Assert.assertEquals(StatusBarConfig.SEGMENT_NET, config.segments.get(4).id);
    }

    @Test
    public void testGlobalOverrides() {
        StatusBarConfig config = parse(
            "status-bar-enabled", "true",
            "status-bar-position", "bottom",
            "status-bar-overlay", "true",
            "status-bar-height", "32",
            "status-bar-font-size", "14",
            "status-bar-padding-horizontal", "16",
            "status-bar-align", "center",
            "status-bar-show-icons", "false",
            "status-bar-icons-position", "end",
            "status-bar-separator", " | ");

        Assert.assertTrue(config.enabled);
        Assert.assertTrue(config.positionBottom);
        Assert.assertTrue(config.overlay);
        Assert.assertEquals(32, config.heightDp);
        Assert.assertEquals(14, config.fontSizeSp);
        Assert.assertEquals(16, config.paddingHorizontalDp);
        Assert.assertEquals(StatusBarConfig.ALIGN_CENTER, config.align);
        Assert.assertFalse(config.showIcons);
        Assert.assertTrue(config.iconsAtEnd);
        Assert.assertEquals(" | ", config.separator);
    }

    @Test
    public void testOutOfRangeValuesFallBackToDefaults() {
        StatusBarConfig config = parse(
            "status-bar-height", "1000",
            "status-bar-font-size", "0",
            "status-bar-padding-horizontal", "-5",
            "status-bar-align", "diagonal");

        Assert.assertEquals(StatusBarConfig.DEFAULT_HEIGHT_DP, config.heightDp);
        Assert.assertEquals(StatusBarConfig.DEFAULT_FONT_SIZE_SP, config.fontSizeSp);
        Assert.assertEquals(StatusBarConfig.DEFAULT_PADDING_HORIZONTAL_DP, config.paddingHorizontalDp);
        Assert.assertEquals(StatusBarConfig.ALIGN_START, config.align);
    }

    @Test
    public void testSegmentOrderAndUnknownSegments() {
        StatusBarConfig config = parse("status-bar-segments", " proxy , clock,unknown,clock, ip ");

        Assert.assertEquals(3, config.segments.size());
        Assert.assertEquals(StatusBarConfig.SEGMENT_PROXY, config.segments.get(0).id);
        Assert.assertEquals(StatusBarConfig.SEGMENT_CLOCK, config.segments.get(1).id);
        Assert.assertEquals(StatusBarConfig.SEGMENT_IP, config.segments.get(2).id);
    }

    @Test
    public void testDisabledSegmentIsRemoved() {
        StatusBarConfig config = parse(
            "status-bar-segments", "clock,cpu",
            "status-bar-segment-cpu-enabled", "false");

        Assert.assertEquals(1, config.segments.size());
        Assert.assertEquals(StatusBarConfig.SEGMENT_CLOCK, config.segments.get(0).id);
    }

    @Test
    public void testSegmentOverrides() {
        StatusBarConfig config = parse(
            "status-bar-segments", "battery",
            "status-bar-segment-battery-format", "%p %t",
            "status-bar-segment-battery-label", "BAT",
            "status-bar-segment-battery-style", "text-chart",
            "status-bar-segment-battery-action", "battery-settings",
            "status-bar-segment-battery-interval", "500",
            "status-bar-segment-battery-warn-below", "20",
            "status-bar-segment-battery-color", "theme:color4");

        Assert.assertEquals(1, config.segments.size());
        StatusBarConfig.Segment segment = config.segments.get(0);
        Assert.assertEquals("%p %t", segment.format);
        Assert.assertEquals("BAT", segment.label);
        Assert.assertEquals(StatusBarConfig.STYLE_TEXT_CHART, segment.style);
        Assert.assertTrue(segment.shouldShowChart());
        Assert.assertTrue(segment.shouldShowText());
        Assert.assertEquals(StatusBarConfig.ACTION_BATTERY_SETTINGS, segment.action);
        Assert.assertEquals(500, segment.intervalMs);
        Assert.assertEquals(20d, segment.warnBelow, 0.001);
        Assert.assertNull(segment.warnAbove);
        Assert.assertEquals("theme:color4", segment.color);
    }

    @Test
    public void testChartOnlyStyle() {
        StatusBarConfig config = parse(
            "status-bar-segments", "cpu",
            "status-bar-segment-cpu-style", "chart");

        StatusBarConfig.Segment segment = config.segments.get(0);
        Assert.assertTrue(segment.shouldShowChart());
        Assert.assertFalse(segment.shouldShowText());
    }

    @Test
    public void testSeparatorEscapes() {
        StatusBarConfig config = parse("status-bar-separator", "\\t");
        Assert.assertEquals("\t", config.separator);
    }

    @Test
    public void testApplyTokens() {
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("rx", "1M/s");
        tokens.put("tx", "2M/s");
        tokens.put("rxTotal", "10G");

        Assert.assertEquals("1M/s", StatusBarController.applyTokens("%rx", tokens));
        Assert.assertEquals("10G", StatusBarController.applyTokens("%rxTotal", tokens));
        Assert.assertEquals("1M/s / 2M/s", StatusBarController.applyTokens("%rx / %tx", tokens));
        Assert.assertEquals("1M/s%", StatusBarController.applyTokens("%rx%%", tokens));
        // An unknown token is left as it is, and a trailing percent is not a token.
        Assert.assertEquals("%nope 50%", StatusBarController.applyTokens("%nope 50%", tokens));
    }

    @Test
    public void testFormatBytes() {
        Assert.assertEquals("512B", StatusBarMetrics.formatBytes(512));
        Assert.assertEquals("1K", StatusBarMetrics.formatBytes(1024));
        Assert.assertEquals("1M", StatusBarMetrics.formatBytes(1024 * 1024));
        Assert.assertEquals("1.5G", StatusBarMetrics.formatBytes((long) (1.5 * 1024 * 1024 * 1024)));
        Assert.assertEquals("-", StatusBarMetrics.formatBytes(-1));
    }

    @Test
    public void testFormatRate() {
        Assert.assertEquals("512B/s", StatusBarMetrics.formatRate(512));
        Assert.assertEquals("2K/s", StatusBarMetrics.formatRate(2048));
        Assert.assertEquals("1.5M/s", StatusBarMetrics.formatRate((long) (1.5 * 1024 * 1024)));
        Assert.assertEquals("-", StatusBarMetrics.formatRate(-1));
    }

    @Test
    public void testFormatPercent() {
        Assert.assertEquals("42%", StatusBarMetrics.formatPercent(42.4f));
        Assert.assertEquals("-", StatusBarMetrics.formatPercent(StatusBarMetrics.UNKNOWN_PERCENT));
    }

}
