package com.termux.app.fragments.settings;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Tests the filtering of the settings search, since a result that is missing or that is ranked below a less
 * relevant one makes the user think that the setting does not exist.
 */
public class SettingsSearchIndexTest {

    private static SettingsSearchIndex.Entry entry(String title, String summary) {
        return new SettingsSearchIndex.Entry("Page", "PageFragment", "key-" + title, title, summary);
    }

    private static final List<SettingsSearchIndex.Entry> ENTRIES = Arrays.asList(
        entry("Font size", "The size of the text of the terminal"),
        entry("Notification cards", "Show a card when a command fails"),
        entry("Notifications", "Cards, OSC and background notifications"),
        entry("Bell", null));

    @Test
    public void testEmptyQueryReturnsEverything() {
        Assert.assertEquals(ENTRIES.size(), SettingsSearchIndex.search(ENTRIES, null).size());
        Assert.assertEquals(ENTRIES.size(), SettingsSearchIndex.search(ENTRIES, "").size());
        Assert.assertEquals(ENTRIES.size(), SettingsSearchIndex.search(ENTRIES, "  ").size());
    }

    @Test
    public void testMatchesTheTitleAndTheSummary() {
        Assert.assertEquals(1, SettingsSearchIndex.search(ENTRIES, "bell").size());
        Assert.assertEquals(1, SettingsSearchIndex.search(ENTRIES, "font").size());
        // The summary of the font entry contains "text", which no title contains.
        Assert.assertEquals(1, SettingsSearchIndex.search(ENTRIES, "text").size());
    }

    @Test
    public void testIgnoresCase() {
        Assert.assertEquals(1, SettingsSearchIndex.search(ENTRIES, "BELL").size());
        Assert.assertEquals(1, SettingsSearchIndex.search(ENTRIES, "nOtIfIcAtIoN cArDs").size());
    }

    @Test
    public void testTitleMatchesComeBeforeSummaryMatches() {
        List<SettingsSearchIndex.Entry> matches = SettingsSearchIndex.search(ENTRIES, "notification");

        Assert.assertEquals(2, matches.size());
        // "Notification cards" and "Notifications" both match by title, so they keep the order of the page.
        Assert.assertEquals("Notification cards", matches.get(0).title.toString());
        Assert.assertEquals("Notifications", matches.get(1).title.toString());

        // "card" is in the summary of one entry and in the title of the other, so the title match comes first.
        matches = SettingsSearchIndex.search(ENTRIES, "cards");
        Assert.assertEquals("Notification cards", matches.get(0).title.toString());
        Assert.assertEquals("Notifications", matches.get(1).title.toString());
    }

    @Test
    public void testMatchesThePageTitle() {
        SettingsSearchIndex.Entry entry = new SettingsSearchIndex.Entry("Status bar", "PageFragment", "key",
            "Time format", null);

        Assert.assertEquals(1, SettingsSearchIndex.search(Arrays.asList(entry), "status").size());
        Assert.assertEquals(0, SettingsSearchIndex.search(Arrays.asList(entry), "keyboard").size());
    }

    @Test
    public void testNoMatches() {
        Assert.assertTrue(SettingsSearchIndex.search(ENTRIES, "keyboard").isEmpty());
    }
}
