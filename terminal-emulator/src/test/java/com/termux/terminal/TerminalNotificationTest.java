package com.termux.terminal;

import android.graphics.Bitmap;

import junit.framework.TestCase;

import java.lang.reflect.Constructor;

/**
 * Tests what makes a notification empty, since a card or a system notification for a notification
 * without any text tells the user nothing.
 *
 * A bitmap cannot be created by a unit test, since the stub of android only provides the signature, so
 * the empty constructor of the bitmap is called reflectively: the notification only stores it and never
 * calls it, so an unusable instance is enough.
 */
public class TerminalNotificationTest extends TestCase {

    private static Bitmap createBitmap() throws Exception {
        Constructor<Bitmap> constructor = Bitmap.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static TerminalNotification.Builder builder() {
        return new TerminalNotification.Builder(
            TerminalNotification.Kind.INFO, TerminalNotification.Source.OSC);
    }

    public void testNotificationWithoutTextIsEmpty() throws Exception {
        assertTrue(builder().build().isEmpty());
        assertTrue(builder().setTitle("  ").setBody("\n").build().isEmpty());
    }

    public void testIconAloneIsNotContent() throws Exception {
        TerminalNotification notification = builder().setIcon(createBitmap()).build();

        assertNotNull(notification.getIcon());
        assertTrue("An icon alone must not show a card", notification.isEmpty());
    }

    public void testIconWithTitleIsNotEmpty() throws Exception {
        TerminalNotification notification = builder().setTitle("Build finished").setIcon(createBitmap()).build();

        assertFalse(notification.isEmpty());
    }
}
