package com.termux.app.statusbar;

import android.content.Context;
import android.util.AttributeSet;

import com.termux.app.notifications.NotificationOverlayView;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.view.TerminalView;

import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies that the custom views that are referenced from the `activity_termux` layout can be
 * created by the {@link android.view.LayoutInflater}.
 *
 * The inflater creates a view by reflection with the `(Context, AttributeSet)` constructor, so a
 * custom view that only has the `(Context)` constructor makes the app crash on start with an
 * `InflateException` / `NoSuchMethodException`, which is not caught by the compiler.
 */
public class CustomViewConstructorTest {

    private static final Class<?>[] INFLATER_CONSTRUCTOR_PARAMETERS = new Class<?>[]{
        Context.class, AttributeSet.class
    };

    private static void assertHasInflaterConstructor(String className) {
        Class<?> viewClass;
        try {
            viewClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            Assert.fail("The view class " + className + " was not found");
            return;
        }

        try {
            viewClass.getConstructor(INFLATER_CONSTRUCTOR_PARAMETERS);
        } catch (NoSuchMethodException e) {
            Assert.fail("The view class " + className + " must define a (Context, AttributeSet) "
                + "constructor, otherwise inflating a layout that uses it crashes at runtime");
        }
    }

    @Test
    public void testLayoutViewsHaveInflaterConstructor() {
        assertHasInflaterConstructor(StatusBarView.class.getName());
        assertHasInflaterConstructor(SparklineView.class.getName());
        assertHasInflaterConstructor(TerminalView.class.getName());
        assertHasInflaterConstructor(TermuxActivityRootView.class.getName());
        assertHasInflaterConstructor(NotificationOverlayView.class.getName());
    }

}
