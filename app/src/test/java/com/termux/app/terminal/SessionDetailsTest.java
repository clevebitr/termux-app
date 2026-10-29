package com.termux.app.terminal;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Tests the parts of the session details that do not read {@code /proc}, like the shortening of the home
 * directory and the joining of the details, since a missing detail must not leave a separator at the start
 * or the end of the line of the drawer.
 */
public class SessionDetailsTest {

    @Test
    public void testShortenHomeDirectory() {
        String home = "/data/data/com.termux/files/home";

        Assert.assertEquals("~", SessionDetails.shortenHomeDirectory(home, home));
        Assert.assertEquals("~/projects", SessionDetails.shortenHomeDirectory(home + "/projects", home));
        Assert.assertEquals("/sdcard", SessionDetails.shortenHomeDirectory("/sdcard", home));
        // A directory that only starts with the same prefix is not in the home directory.
        Assert.assertEquals("/data/data/com.termux/files/home2",
            SessionDetails.shortenHomeDirectory("/data/data/com.termux/files/home2", home));
        // An empty home directory must not turn every path into a relative one.
        Assert.assertEquals("/sdcard", SessionDetails.shortenHomeDirectory("/sdcard", ""));
    }

    @Test
    public void testJoinSkipsEmptyParts() {
        Assert.assertEquals("vim · ~/src", SessionDetails.join(Arrays.asList("vim", "~/src"), " · "));
        Assert.assertEquals("vim", SessionDetails.join(Arrays.asList("vim", null, "  "), " · "));
        Assert.assertEquals("", SessionDetails.join(Collections.emptyList(), " · "));
    }

    @Test
    public void testDetailsOfAnInvalidPid() {
        // A session that is not running has the pid 0 or -1, which has no proc files.
        Assert.assertNull(SessionDetails.getForegroundProcessName(0));
        Assert.assertNull(SessionDetails.getProcessName(-1));
        Assert.assertNull(SessionDetails.getWorkingDirectory(0));
        Assert.assertNull(SessionDetails.getShortDetails(0));
    }
}
