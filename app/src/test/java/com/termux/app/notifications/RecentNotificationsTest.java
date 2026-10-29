package com.termux.app.notifications;

import com.termux.terminal.TerminalNotification;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Tests the list of the notifications that were shown recently, which decides what the recent
 * notifications dialog shows and which notifications are dropped when the list is full.
 */
public class RecentNotificationsTest {

    private static TerminalNotification notification(String title) {
        return new TerminalNotification.Builder(TerminalNotification.Kind.INFO, TerminalNotification.Source.APP_EVENT)
            .setTitle(title)
            .build();
    }

    @Test
    public void testNewestFirst() {
        RecentNotifications history = new RecentNotifications(10);
        history.add(notification("first"), null);
        history.add(notification("second"), null);

        List<RecentNotifications.Entry> entries = history.getEntries();
        Assert.assertEquals(2, entries.size());
        Assert.assertEquals("second", entries.get(0).notification.getTitle());
        Assert.assertEquals("first", entries.get(1).notification.getTitle());
    }

    @Test
    public void testOldestAreDroppedWhenFull() {
        RecentNotifications history = new RecentNotifications(2);
        history.add(notification("first"), null);
        history.add(notification("second"), null);
        history.add(notification("third"), null);

        List<RecentNotifications.Entry> entries = history.getEntries();
        Assert.assertEquals(2, entries.size());
        Assert.assertEquals("third", entries.get(0).notification.getTitle());
        Assert.assertEquals("second", entries.get(1).notification.getTitle());
    }

    @Test
    public void testCapacityZeroKeepsNothing() {
        RecentNotifications history = new RecentNotifications(0);
        history.add(notification("first"), null);

        Assert.assertTrue(history.isEmpty());
    }

    @Test
    public void testChangingTheCapacityDropsTheOldestEntries() {
        RecentNotifications history = new RecentNotifications(10);
        history.add(notification("first"), null);
        history.add(notification("second"), null);
        history.add(notification("third"), null);

        history.setCapacity(1);

        List<RecentNotifications.Entry> entries = history.getEntries();
        Assert.assertEquals(1, entries.size());
        Assert.assertEquals("third", entries.get(0).notification.getTitle());
    }

    @Test
    public void testClear() {
        RecentNotifications history = new RecentNotifications(10);
        history.add(notification("first"), null);
        history.clear();

        Assert.assertTrue(history.isEmpty());
        Assert.assertTrue(history.getEntries().isEmpty());
    }

    @Test
    public void testTheActionOfAnEntryIsKept() {
        RecentNotifications history = new RecentNotifications(10);
        boolean[] clicked = new boolean[1];
        history.add(notification("first"), () -> clicked[0] = true);

        RecentNotifications.Entry entry = history.getEntries().get(0);
        Assert.assertNotNull(entry.onOpen);
        entry.onOpen.run();
        Assert.assertTrue(clicked[0]);
    }
}
