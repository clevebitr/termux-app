package com.termux.app.terminal.layout;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the pane bookkeeping of the two pane layout, which decides which view receives the input.
 *
 * <p>The layout is only asked for state here, without any view being created, so the tests cover the index handling
 * and the null safety that the activity relies on while the views do not exist yet.</p>
 */
public class TwoPaneTerminalLayoutTest {

    @Test
    public void testTwoPanesWithTheFirstOneActive() {
        TwoPaneTerminalLayout layout = new TwoPaneTerminalLayout();

        Assert.assertEquals(2, layout.getPaneCount());
        Assert.assertEquals(0, layout.getActiveIndex());
        Assert.assertEquals(1, layout.getInactiveIndex());
        Assert.assertNull(layout.getInactiveView());
    }

    @Test
    public void testTheInactiveIndexFollowsTheActiveOne() {
        TwoPaneTerminalLayout layout = new TwoPaneTerminalLayout();

        layout.setActiveIndex(1);

        Assert.assertEquals(1, layout.getActiveIndex());
        Assert.assertEquals(0, layout.getInactiveIndex());
    }

    @Test
    public void testAnOutOfRangeIndexIsIgnored() {
        TwoPaneTerminalLayout layout = new TwoPaneTerminalLayout();
        layout.setActiveIndex(1);

        layout.setActiveIndex(-1);
        layout.setActiveIndex(2);

        Assert.assertEquals(1, layout.getActiveIndex());
        Assert.assertNull(layout.getView(-1));
        Assert.assertNull(layout.getView(2));
        Assert.assertNull(layout.getViewClient(2));
    }

    @Test
    public void testMissingViewsReportNoSession() {
        TwoPaneTerminalLayout layout = new TwoPaneTerminalLayout();

        Assert.assertNull(layout.getSession(0));
        Assert.assertNull(layout.getSession(1));
        Assert.assertNull(layout.findViewForSession(null));
    }
}
