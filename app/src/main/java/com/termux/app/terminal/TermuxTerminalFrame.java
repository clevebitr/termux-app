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
 * The container of a terminal view, which decides what the secondary mouse button does.
 *
 * <p>Upstream always shows the terminal context menu for the secondary button, which is right for touch but
 * means that a program that enabled mouse tracking, like {@code vim}, {@code tmux} or {@code htop}, never
 * sees a right click on a desktop. The fork forwards the button while the emulator reports that the program
 * asked for mouse events, and the `mouse-right-click` property decides what happens otherwise:</p>
 *
 * <ul>
 *     <li>{@link SecondaryButtonBehaviour#AUTO}: forward while the program tracks the mouse, otherwise let the
 *     terminal view show its context menu, which the client turns into the command palette;</li>
 *     <li>{@link SecondaryButtonBehaviour#FORWARD}: never open the palette, so the click is swallowed when the
 *     program does not track the mouse, which is what a desktop user who does not want a window to pop up on a
 *     misclick expects;</li>
 *     <li>{@link SecondaryButtonBehaviour#PALETTE}: always open the palette, even while the program tracks the
 *     mouse.</li>
 * </ul>
 *
 * <p>The fork cannot subclass {@code TerminalView}, since it is final in the read-only view module, so the
 * behaviour is added by a container that intercepts the event before the view sees it. The emulator and the
 * coordinate mapping of the view are public API, so the fork does not have to patch the rendering or input
 * code of the view.</p>
 */
public class TermuxTerminalFrame extends FrameLayout {

    /** The button code of the right button in the x10/SGR mouse protocol. */
    private static final int MOUSE_RIGHT_BUTTON = 2;

    /** What the secondary mouse button does, see the class documentation. */
    public enum SecondaryButtonBehaviour {
        AUTO,
        FORWARD,
        PALETTE
    }

    @NonNull
    private SecondaryButtonBehaviour mBehaviour = SecondaryButtonBehaviour.AUTO;

    public TermuxTerminalFrame(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Set what the secondary mouse button does, as the `mouse-right-click` property configures it.
     *
     * <p>Called by {@code TermuxActivity} when the properties are loaded and after the panes are created, so the
     * container never reads the `termux.properties` file on a touch event.</p>
     */
    public void setSecondaryButtonBehaviour(@NonNull SecondaryButtonBehaviour behaviour) {
        mBehaviour = behaviour;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        TerminalView terminalView = findTerminalView();
        if (terminalView == null || !isSecondaryButton(event)) return super.dispatchTouchEvent(event);

        switch (mBehaviour) {
            case PALETTE:
                // Let the terminal view show its context menu, which the client turns into the command palette.
                return super.dispatchTouchEvent(event);
            case FORWARD:
                forwardSecondaryButton(terminalView, event);
                // The palette is never opened in this mode, so the click is swallowed even when the program does
                // not track the mouse and therefore ignores it.
                return true;
            case AUTO:
            default:
                if (forwardSecondaryButton(terminalView, event)) return true;
                return super.dispatchTouchEvent(event);
        }
    }

    /** @return Whether the event is a press of the secondary button of a mouse. */
    private boolean isSecondaryButton(@NonNull MotionEvent event) {
        return event.isFromSource(InputDevice.SOURCE_MOUSE)
            && event.isButtonPressed(MotionEvent.BUTTON_SECONDARY);
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
