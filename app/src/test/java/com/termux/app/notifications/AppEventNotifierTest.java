package com.termux.app.notifications;

import com.termux.terminal.TerminalNotification;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the parts of the app event notifications that do not need an activity, like the accent color of
 * a session that exited, since a session that runs successfully must not look like a failure.
 */
public class AppEventNotifierTest {

    @Test
    public void testSessionExitKind() {
        Assert.assertEquals(TerminalNotification.Kind.INFO, AppEventNotifier.getSessionExitKind(0));
        Assert.assertEquals(TerminalNotification.Kind.WARNING, AppEventNotifier.getSessionExitKind(1));
        // A session that was killed by a signal has a negative exit status, like -9 for SIGKILL.
        Assert.assertEquals(TerminalNotification.Kind.WARNING, AppEventNotifier.getSessionExitKind(-9));
    }
}
