package com.termux.app.notifications;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the sliding window of the rate limit of the `OSC` notifications, since a program that spams
 * notifications must be thinned out without ever blocking a program that only shows a few of them.
 */
public class OscRateLimiterTest {

    private final OscRateLimiter mLimiter = new OscRateLimiter();

    @Test
    public void testLimitIsPerWindow() {
        Object session = new Object();

        Assert.assertFalse(mLimiter.isRateLimited(session, 3, 0));
        Assert.assertFalse(mLimiter.isRateLimited(session, 3, 10));
        Assert.assertFalse(mLimiter.isRateLimited(session, 3, 20));
        Assert.assertTrue(mLimiter.isRateLimited(session, 3, 30));

        // A notification that is dropped must not take a slot of the window, otherwise a program that
        // keeps sending notifications would never get out of the limit.
        Assert.assertTrue(mLimiter.isRateLimited(session, 3, 40));
        Assert.assertTrue(mLimiter.isRateLimited(session, 3, 50));
    }

    @Test
    public void testWindowSlides() {
        Object session = new Object();

        Assert.assertFalse(mLimiter.isRateLimited(session, 1, 0));
        Assert.assertTrue(mLimiter.isRateLimited(session, 1, 999));
        // The first notification leaves the window after a second, so the next one is shown.
        Assert.assertFalse(mLimiter.isRateLimited(session, 1, 1000));
        Assert.assertTrue(mLimiter.isRateLimited(session, 1, 1500));
        Assert.assertFalse(mLimiter.isRateLimited(session, 1, 2000));
    }

    @Test
    public void testSessionsAreLimitedSeparately() {
        Object first = new Object();
        Object second = new Object();

        Assert.assertFalse(mLimiter.isRateLimited(first, 1, 0));
        Assert.assertTrue(mLimiter.isRateLimited(first, 1, 10));
        Assert.assertFalse(mLimiter.isRateLimited(second, 1, 20));
    }

    @Test
    public void testZeroLimitShowsEverything() {
        Object session = new Object();

        for (int i = 0; i < 100; i++) {
            Assert.assertFalse(mLimiter.isRateLimited(session, 0, i));
        }
    }
}
