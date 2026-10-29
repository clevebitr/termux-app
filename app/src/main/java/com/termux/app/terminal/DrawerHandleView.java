package com.termux.app.terminal;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.view.ViewUtils;

import java.util.Collections;

/**
 * The handle that is drawn on the left edge of the terminal to open the sessions drawer.
 *
 * On android 10 and later a swipe from the edge of the screen is the system back gesture, which takes
 * priority over the edge drag of the {@code DrawerLayout}, so the drawer can barely be opened by swiping
 * anymore. This view claims a small area of the edge with
 * {@link View#setSystemGestureExclusionRects(java.util.List)}, so that a swipe that starts on it belongs to
 * the app again, and it opens the drawer on a tap as well.
 *
 * Only a small area may be excluded from the system gestures, so the touch area of the handle is 24dp wide
 * and 64dp high, while the bar that is drawn in it is 12dp wide and 48dp high, so that it does not cover
 * too much of the terminal.
 */
public final class DrawerHandleView extends View {

    /** What a touch on the handle means. */
    enum Gesture {
        /** The touch is not a tap and not a swipe to the right, like a swipe to the left or upwards. */
        NONE,
        /** The handle was tapped. */
        TAP,
        /** The handle was swiped to the right, which opens the drawer like the edge swipe does. */
        OPEN
    }

    /** The width of the bar that is drawn in the touch area. */
    private static final float BAR_WIDTH_DP = 12;
    /** The height of the bar that is drawn in the touch area. */
    private static final float BAR_HEIGHT_DP = 48;
    /** How far the finger must move to the right for the touch to be a swipe instead of a tap. */
    private static final float SWIPE_THRESHOLD_DP = 16;

    /** The listener that is called when the user taps or swipes the handle. */
    public interface OnOpenDrawerListener {
        void onOpenDrawer();
    }

    @NonNull
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mTouchSlop;
    private final float mSwipeThreshold;

    private float mDownX;
    private float mDownY;
    /** Whether the gesture of the current touch already opened the drawer, so that the release is ignored. */
    private boolean mGestureHandled;

    @Nullable
    private OnOpenDrawerListener mListener;

    public DrawerHandleView(@NonNull Context context) {
        this(context, null);
    }

    public DrawerHandleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mSwipeThreshold = ViewUtils.dpToPx(context, SWIPE_THRESHOLD_DP);
        mPaint.setColor(0x66FFFFFF);
    }

    public void setOnOpenDrawerListener(@Nullable OnOpenDrawerListener listener) {
        mListener = listener;
    }

    /** Set the color of the bar, which follows the accent color of the terminal color scheme. */
    public void setHandleColor(int color) {
        mPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float barWidth = ViewUtils.dpToPx(getContext(), BAR_WIDTH_DP);
        float barHeight = Math.min(getHeight(), ViewUtils.dpToPx(getContext(), BAR_HEIGHT_DP));
        // The bar is centered in the touch area, which is wider than the bar so that it is easier to hit.
        float left = (getWidth() - barWidth) / 2f;
        float top = (getHeight() - barHeight) / 2f;

        canvas.drawRect(left, top, left + barWidth, top + barHeight, mPaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                mGestureHandled = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (mGestureHandled) return true;
                if (resolveGesture(event.getX() - mDownX, event.getY() - mDownY,
                    mTouchSlop, mSwipeThreshold) != Gesture.OPEN) return true;
                // The drawer is opened as soon as the finger is far enough to the right, since this view
                // cannot drag the drawer itself without reimplementing the layout of the drawer.
                mGestureHandled = true;
                openDrawer();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (!mGestureHandled && resolveGesture(event.getX() - mDownX, event.getY() - mDownY,
                    mTouchSlop, mSwipeThreshold) == Gesture.TAP) {
                    mGestureHandled = true;
                    openDrawer();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                mGestureHandled = false;
                return true;
            default:
                return false;
        }
    }

    /**
     * @return What the touch that moved by {@code dx} and {@code dy} means, where a movement further to the
     *         right than {@code swipeThreshold} is a swipe that opens the drawer and a movement that stays
     *         within {@code touchSlop} in both directions is a tap.
     */
    @NonNull
    static Gesture resolveGesture(float dx, float dy, int touchSlop, float swipeThreshold) {
        // A swipe has to be horizontal, otherwise a diagonal drag that only starts to the right would open
        // the drawer while the user is scrolling the terminal.
        if (dx >= swipeThreshold && dx > Math.abs(dy)) return Gesture.OPEN;
        if (Math.abs(dx) <= touchSlop && Math.abs(dy) <= touchSlop) return Gesture.TAP;
        return Gesture.NONE;
    }

    private void openDrawer() {
        if (mListener != null) mListener.onOpenDrawer();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSystemGestureExclusion();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateSystemGestureExclusion();
    }

    /**
     * Claim the touch area of the handle, so that a swipe that starts on it is not reported as the system
     * back gesture. Only a small part of the edge may be excluded, which is why the handle is small.
     */
    private void updateSystemGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        excludeFromSystemGestures(this, getWidth(), getHeight());
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private static void excludeFromSystemGestures(@NonNull View view, int width, int height) {
        view.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, width, height)));
    }
}
