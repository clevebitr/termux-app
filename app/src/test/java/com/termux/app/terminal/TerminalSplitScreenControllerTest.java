package com.termux.app.terminal;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the ratio of the panes of the split screen, since a ratio that is restored from the state of a
 * recreated activity, or that a drag of the divider produced, must never hide one of the panes.
 */
public class TerminalSplitScreenControllerTest {

    @Test
    public void testRatioIsClamped() {
        Assert.assertEquals(0.2f, TerminalSplitScreenController.clampPaneRatio(0f), 0.0001f);
        Assert.assertEquals(0.2f, TerminalSplitScreenController.clampPaneRatio(0.1f), 0.0001f);
        Assert.assertEquals(0.8f, TerminalSplitScreenController.clampPaneRatio(0.9f), 0.0001f);
        Assert.assertEquals(0.8f, TerminalSplitScreenController.clampPaneRatio(1f), 0.0001f);
        // A negative position of a dragged divider must not produce a negative weight.
        Assert.assertEquals(0.2f, TerminalSplitScreenController.clampPaneRatio(-3f), 0.0001f);
    }

    @Test
    public void testRatioInsideTheRangeIsKept() {
        Assert.assertEquals(0.5f, TerminalSplitScreenController.clampPaneRatio(0.5f), 0.0001f);
        Assert.assertEquals(0.2f, TerminalSplitScreenController.clampPaneRatio(0.2f), 0.0001f);
        Assert.assertEquals(0.8f, TerminalSplitScreenController.clampPaneRatio(0.8f), 0.0001f);
    }
}
