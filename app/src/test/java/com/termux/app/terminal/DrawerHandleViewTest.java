package com.termux.app.terminal;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests what a touch on the edge handle means, since a tap has to open the drawer and a diagonal drag must
 * not, or the drawer would open while the user scrolls the terminal from its left edge.
 */
public class DrawerHandleViewTest {

    private static final int TOUCH_SLOP = 8;
    private static final float SWIPE_THRESHOLD = 16;

    private static DrawerHandleView.Gesture resolve(float dx, float dy) {
        return DrawerHandleView.resolveGesture(dx, dy, TOUCH_SLOP, SWIPE_THRESHOLD);
    }

    @Test
    public void testTap() {
        Assert.assertEquals(DrawerHandleView.Gesture.TAP, resolve(0, 0));
        Assert.assertEquals(DrawerHandleView.Gesture.TAP, resolve(5, -4));
        Assert.assertEquals(DrawerHandleView.Gesture.TAP, resolve(-TOUCH_SLOP, TOUCH_SLOP));
    }

    @Test
    public void testSwipeToTheRightOpensTheDrawer() {
        Assert.assertEquals(DrawerHandleView.Gesture.OPEN, resolve(SWIPE_THRESHOLD, 0));
        Assert.assertEquals(DrawerHandleView.Gesture.OPEN, resolve(40, -6));
        Assert.assertEquals(DrawerHandleView.Gesture.OPEN, resolve(40, 6));
    }

    @Test
    public void testSwipeThatIsNotHorizontalDoesNothing() {
        // A diagonal drag is a scroll of the terminal, even if it starts to the right.
        Assert.assertEquals(DrawerHandleView.Gesture.NONE, resolve(40, 40));
        Assert.assertEquals(DrawerHandleView.Gesture.NONE, resolve(20, -30));
    }

    @Test
    public void testOtherGesturesDoNothing() {
        Assert.assertEquals(DrawerHandleView.Gesture.NONE, resolve(-40, 0));
        Assert.assertEquals(DrawerHandleView.Gesture.NONE, resolve(2, 30));
        Assert.assertEquals(DrawerHandleView.Gesture.NONE, resolve(12, 0));
    }
}
