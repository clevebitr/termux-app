package com.termux.app.notifications;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests the ids of the notifications that were posted in the system notification shade, since they are
 * reported to a program that asks which of its notifications are still shown with
 * `OSC 99 ; i=<id> : p=alive ;`.
 */
public class BackgroundNotificationRegistryTest {

    private final BackgroundNotificationRegistry mRegistry = new BackgroundNotificationRegistry();

    @Test
    public void testIdsAreKeptInPostingOrder() {
        mRegistry.remember("a");
        mRegistry.remember("b");
        Assert.assertEquals(Arrays.asList("a", "b"), mRegistry.getIds());
    }

    @Test
    public void testAnIdThatIsPostedAgainMovesToTheEnd() {
        mRegistry.remember("a");
        mRegistry.remember("b");
        mRegistry.remember("a");
        Assert.assertEquals(Arrays.asList("b", "a"), mRegistry.getIds());
    }

    @Test
    public void testOldIdsAreDropped() {
        for (int i = 0; i < BackgroundNotificationRegistry.MAX_IDS + 5; i++)
            mRegistry.remember("id" + i);

        List<String> ids = mRegistry.getIds();
        Assert.assertEquals(BackgroundNotificationRegistry.MAX_IDS, ids.size());
        Assert.assertEquals("id5", ids.get(0));
        Assert.assertEquals("id" + (BackgroundNotificationRegistry.MAX_IDS + 4), ids.get(ids.size() - 1));
    }

    @Test
    public void testForget() {
        mRegistry.remember("a");
        mRegistry.remember("b");
        mRegistry.forget("a");
        mRegistry.forget(null);
        mRegistry.forget("unknown");
        Assert.assertEquals(Arrays.asList("b"), mRegistry.getIds());
    }

    @Test
    public void testAddMissingIdsTo() {
        mRegistry.remember("a");
        mRegistry.remember("b");
        mRegistry.remember("c");

        // The ids of the cards of the overlay come first, and a notification that was reported when it was
        // posted in the shade must not be reported twice.
        List<String> ids = new ArrayList<>(Arrays.asList("c", "card"));
        mRegistry.addMissingIdsTo(ids);
        Assert.assertEquals(Arrays.asList("c", "card", "a", "b"), ids);
    }
}
