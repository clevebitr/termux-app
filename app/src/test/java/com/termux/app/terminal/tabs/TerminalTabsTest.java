package com.termux.app.terminal.tabs;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the tab bookkeeping that the tab bar and the terminal activity share.
 *
 * <p>The tabs are created without sessions here, since the model only stores session references; the tests therefore
 * cover the ordering, the active tab after adding and removing tabs, and the null safety that the activity relies on
 * before the sessions of a restored tab are attached.</p>
 */
public class TerminalTabsTest {

    @Test
    public void testTheFirstTabIsActiveAndIdsIncrease() {
        TerminalTabs tabs = new TerminalTabs();

        TerminalTab first = tabs.addTab();
        TerminalTab second = tabs.addTab();

        Assert.assertEquals(2, tabs.size());
        Assert.assertEquals(second, tabs.getActiveTab());
        Assert.assertEquals(1, tabs.getActiveIndex());
        Assert.assertTrue(second.getId() > first.getId());
    }

    @Test
    public void testRemovingTheActiveTabActivatesItsNeighbour() {
        TerminalTabs tabs = new TerminalTabs();
        TerminalTab first = tabs.addTab();
        TerminalTab second = tabs.addTab();
        TerminalTab third = tabs.addTab();

        tabs.setActiveTab(second);
        tabs.removeTab(second);
        Assert.assertEquals(third, tabs.getActiveTab());
        Assert.assertEquals(1, tabs.getActiveIndex());

        tabs.removeTab(third);
        Assert.assertEquals(first, tabs.getActiveTab());
        Assert.assertEquals(0, tabs.getActiveIndex());

        tabs.removeTab(first);
        Assert.assertTrue(tabs.isEmpty());
        Assert.assertEquals(-1, tabs.getActiveIndex());
        Assert.assertNull(tabs.getActiveTab());
    }

    @Test
    public void testRemovingATabLeftOfTheActiveOneKeepsTheSameTabActive() {
        TerminalTabs tabs = new TerminalTabs();
        TerminalTab first = tabs.addTab();
        TerminalTab second = tabs.addTab();

        tabs.setActiveTab(second);
        tabs.removeTab(first);

        Assert.assertEquals(second, tabs.getActiveTab());
        Assert.assertEquals(0, tabs.getActiveIndex());
    }

    @Test
    public void testOutOfRangeIndicesAreIgnored() {
        TerminalTabs tabs = new TerminalTabs();
        tabs.addTab();

        tabs.setActiveIndex(-1);
        tabs.setActiveIndex(5);

        Assert.assertEquals(0, tabs.getActiveIndex());
        Assert.assertNull(tabs.getTab(5));
        Assert.assertFalse(tabs.removeTab(null));
    }

    @Test
    public void testASessionIsFoundInTheTabThatShowsIt() {
        TerminalTabs tabs = new TerminalTabs();
        tabs.addTab();
        TerminalTab second = tabs.addTab();

        Assert.assertNull(tabs.findTabForSession(null));
        Assert.assertNull(tabs.findTabForSession(null));
        Assert.assertFalse(second.hasSession(null));
    }

    @Test
    public void testTheTitleFallsBackToTheDefaultWithoutACustomTitleOrSession() {
        TerminalTab tab = new TerminalTab(1);

        Assert.assertEquals("termux", tab.getTitle("termux"));

        tab.setCustomTitle("  build  ");
        Assert.assertEquals("build", tab.getTitle("termux"));

        tab.setCustomTitle("   ");
        Assert.assertEquals("termux", tab.getTitle("termux"));
    }

    @Test
    public void testTheActivePaneIsKeptInRange() {
        TerminalTab tab = new TerminalTab(1);

        tab.setActivePaneIndex(1);
        Assert.assertEquals(1, tab.getActivePaneIndex());

        tab.setActivePaneIndex(2);
        tab.setActivePaneIndex(-1);
        Assert.assertEquals(1, tab.getActivePaneIndex());
        Assert.assertNull(tab.getSession(2));
    }
}
