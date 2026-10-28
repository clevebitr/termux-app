package com.termux.view;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;

import com.termux.terminal.TerminalSession;
import com.termux.view.textselection.TextSelectionCursorController;

import java.util.Arrays;
import java.util.List;

/**
 * The interface for communication between {@link TerminalView} and its client. It allows for getting
 * various  configuration options from the client and for sending back data to the client like logs,
 * key events, both hardware and IME (which makes it different from that available with
 * {@link View#setOnKeyListener(View.OnKeyListener)}, etc. It must be set for the
 * {@link TerminalView} through {@link TerminalView#setTerminalViewClient(TerminalViewClient)}.
 */
public interface TerminalViewClient {

    /**
     * Callback function on scale events according to {@link ScaleGestureDetector#getScaleFactor()}.
     */
    float onScale(float scale);



    /**
     * On a single tap on the terminal if terminal mouse reporting not enabled.
     */
    void onSingleTapUp(MotionEvent e);

    /**
     * Get the actions of the floating text selection toolbar, in the order they are shown, which is
     * shown above the selection handles when text is selected, like after a long press.
     *
     * The actions are the {@code ACTION_*} constants of {@link TextSelectionCursorController}.
     *
     * The default implementation returns copy, paste and more, which is what the app showed before the
     * actions could be configured with the `selection-toolbar-items` property.
     */
    @NonNull
    default List<Integer> getTextSelectionToolbarActions() {
        return Arrays.asList(TextSelectionCursorController.ACTION_COPY,
            TextSelectionCursorController.ACTION_PASTE,
            TextSelectionCursorController.ACTION_MORE);
    }

    /**
     * Called when the context menu of the terminal should be shown, like when "more" is clicked in the
     * text selection toolbar, when the secondary mouse button is clicked or when the menu key is
     * pressed.
     *
     * The default implementation returns {@code false}, so that the system context menu of the
     * terminal view is shown. A client that returns {@code true} shows its own menu instead, like a
     * styled dialog, and the system context menu of the view is not shown.
     */
    default boolean onShowContextMenu(@NonNull TerminalView view) {
        return false;
    }

    boolean shouldBackButtonBeMappedToEscape();

    boolean shouldEnforceCharBasedInput();

    /**
     * If the workaround to be able to use CJK keyboards of IMEs like Gboard in the terminal view
     * should be enabled. If enabled, the terminal view will use a text class input type for the IME
     * and restart the input whenever the IME language is changed.
     * See https://github.com/termux/termux-app/issues/1539.
     */
    boolean isGboardCjkWorkaroundEnabled();

    boolean shouldUseCtrlSpaceWorkaround();

    /**
     * Whether a horizontal swipe on the terminal view should switch to the next or previous session.
     * The gesture is not reported to this client if it is disabled, or if a terminal program is using
     * mouse tracking or text is being selected, since the gesture then belongs to them.
     */
    boolean shouldSwitchSessionOnSwipe();

    /**
     * Called when the user makes a horizontal swipe on the terminal view to switch to the next or
     * previous session.
     *
     * @param forward Whether the swipe was towards the next session, otherwise the previous one.
     */
    void onSwitchSessionSwipe(boolean forward);

    /**
     * Whether a drag with a finger should be reported as a drag of the mouse pointer with the left
     * button pressed, instead of scroll wheel events, when mouse tracking is active, so that terminal
     * programs that support dragging, like for selecting text or resizing panes in a TUI, can handle
     * the drag.
     */
    boolean shouldTouchDragSendMouseDrag();

    boolean isTerminalViewSelected();



    void copyModeChanged(boolean copyMode);



    boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session);

    boolean onKeyUp(int keyCode, KeyEvent e);

    boolean onLongPress(MotionEvent event);



    boolean readControlKey();

    boolean readAltKey();

    boolean readShiftKey();

    boolean readFnKey();



    boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session);


    void onEmulatorSet();


    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);

}
