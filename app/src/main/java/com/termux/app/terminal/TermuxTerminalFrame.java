package com.termux.app.terminal;

import android.content.Context;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalEmulator;
import com.termux.view.TerminalView;

/**
 * The container of a terminal view, which forwards the secondary mouse button to the terminal program when
 * it tracks the mouse.
 *
 * <p>Upstream always shows the terminal context menu for the secondary button, which is right for touch but
 * means that a program that enabled mouse tracking, like {@code vim}, {@code tmux} or {@code htop}, never
 * sees a right click on a desktop. The fork keeps the menu as the fallback and only forwards the button
 * while the emulator reports that the program asked for mouse events.</p>
 *
 * <p>The fork cannot subclass {@code TerminalView}, since it is final in the read-only view module, so the
 * behaviour is added by a container that intercepts the event before the view sees it. The emulator and the
 * coordinate mapping of the view are public API, so the fork does not have to patch the rendering or input
 * code of the view.</p>
 */
public class TermuxTerminalFrame extends FrameLayout {

    /** The button code of the right button in the x10/SGR mouse protocol. */
    private static final int MOUSE_RIGHT_BUTTON = 2;

    public TermuxTerminalFrame(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        TerminalView terminalView = findTerminalView();
        if (terminalView != null && forwardSecondaryButton(terminalView, event)) return true;
        return super.dispatchTouchEvent(event);
    }

    private boolean forwardSecondaryButton(@NonNull TerminalView terminalView, @NonNull MotionEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false;
        if (!event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) return false;

        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP) return false;

        TerminalEmulator emulator = terminalView.mEmulator;
        if (emulator == null || !emulator.isMouseTrackingActive()) return false;

        MotionEvent local = toTerminalCoordinates(terminalView, event);
        int[] columnAndRow = terminalView.getColumnAndRow(local, false);
        if (local != event) local.recycle();
        if (columnAndRow == null || columnAndRow.length < 2) return false;

        // The emulator encodes the protocol and writes it to the session, so the fork only has to provide the
        // cell that the pointer is over and whether the button was pressed or released.
        emulator.sendMouseEvent(MOUSE_RIGHT_BUTTON, columnAndRow[0], columnAndRow[1],
            action == MotionEvent.ACTION_DOWN);
        return true;
    }

    /** Translate the event into the coordinate space of the terminal view, which fills this container. */
    @NonNull
    private MotionEvent toTerminalCoordinates(@NonNull TerminalView terminalView, @NonNull MotionEvent event) {
        float deltaX = terminalView.getLeft() - getLeft();
        float deltaY = terminalView.getTop() - getTop();
        if (deltaX == 0 && deltaY == 0) return event;
        MotionEvent copy = MotionEvent.obtain(event);
        copy.offsetLocation(-deltaX, -deltaY);
        return copy;
    }

    @Nullable
    private TerminalView findTerminalView() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof TerminalView) return (TerminalView) getChildAt(i);
        }
        return null;
    }
}
