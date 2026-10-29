package com.termux.app.terminal;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.view.ViewUtils;

/**
 * The layout of the split screen, which shows two terminal panes next to each other, or above each other,
 * with a divider between them that can be dragged to change their relative sizes.
 *
 * The controller only owns the state and the geometry of the panes, and not the sessions or the terminal
 * views, since those are managed by the activity, which also has to react when the active pane changes.
 */
public final class TerminalSplitScreenController {

    /** The size of the divider between the panes in dp, which is thicker than the drawn line. */
    private static final float DIVIDER_SIZE_DP = 6;
    /** The minimum and the maximum size of the first pane, so that neither pane can be dragged away. */
    private static final float MIN_PANE_RATIO = 0.2f;
    private static final float MAX_PANE_RATIO = 0.8f;
    /** The interval at which a drag of the divider may resize the panes, since that resizes the sessions. */
    private static final long DIVIDER_DRAG_INTERVAL_MS = 40;

    @NonNull
    private final TermuxActivity mActivity;
    @NonNull
    private final FrameLayout[] mPaneContainers;
    @Nullable
    private final LinearLayout mPanesContainer;
    @Nullable
    private final View mDivider;
    /** The relative sizes of the panes, as the weights of the {@link LinearLayout} that holds them. */
    @NonNull
    private final float[] mPaneWeights = new float[]{1f, 1f};

    private boolean mEnabled;
    private int mOrientation = LinearLayout.VERTICAL;

    /** The location and the size of the panes container when a drag of the divider started. */
    @NonNull
    private final int[] mDividerContainerLocation = new int[2];
    private int mDividerContainerSize;
    private long mDividerLastUpdateTime;

    public TerminalSplitScreenController(@NonNull TermuxActivity activity, @NonNull FrameLayout[] paneContainers,
                                         @Nullable LinearLayout panesContainer, @Nullable View divider) {
        mActivity = activity;
        mPaneContainers = paneContainers;
        mPanesContainer = panesContainer;
        mDivider = divider;
    }

    /** @return Whether the split screen is enabled, i.e. whether the secondary pane is shown. */
    public boolean isEnabled() {
        return mEnabled;
    }

    /** Show or hide the secondary pane, and make the divider visible with it. */
    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        mPaneContainers[1].setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (mDivider != null) mDivider.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    /** @return The orientation of the panes, either {@link LinearLayout#VERTICAL} or {@link LinearLayout#HORIZONTAL}. */
    public int getOrientation() {
        return mOrientation;
    }

    /** @return The size of the first pane relative to the container, between 0.2 and 0.8. */
    public float getPaneRatio() {
        return mPaneWeights[0];
    }

    /**
     * Set the relative sizes of the panes, like to restore them after the activity was recreated.
     *
     * The views are not touched, so that this can be called before {@link #updateLayout()}, which applies
     * the new sizes together with the orientation and the visibility of the panes.
     */
    public void setPaneRatio(float ratio) {
        if (Float.isNaN(ratio) || Float.isInfinite(ratio)) return;

        mPaneWeights[0] = clampPaneRatio(ratio);
        mPaneWeights[1] = 1f - mPaneWeights[0];
    }

    /**
     * @return The ratio of the first pane, clamped to the range that keeps both panes visible, so that a
     *         restored or dragged value can never hide one of them.
     */
    static float clampPaneRatio(float ratio) {
        return Math.max(MIN_PANE_RATIO, Math.min(MAX_PANE_RATIO, ratio));
    }

    /** @param orientation The orientation of the panes, either {@link LinearLayout#VERTICAL} or {@link LinearLayout#HORIZONTAL}. */
    public void setOrientation(int orientation) {
        if (orientation != LinearLayout.VERTICAL && orientation != LinearLayout.HORIZONTAL) return;
        mOrientation = orientation;
    }

    /** Apply the orientation and the sizes of the panes to their views. */
    public void updateLayout() {
        if (mPanesContainer == null) return;

        final boolean isVertical = mOrientation == LinearLayout.VERTICAL;
        mPanesContainer.setOrientation(mOrientation);

        if (!mEnabled) {
            mPaneContainers[0].setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
            return;
        }

        for (int i = 0; i < mPaneContainers.length; i++) {
            mPaneContainers[i].setLayoutParams(new LinearLayout.LayoutParams(
                isVertical ? LinearLayout.LayoutParams.MATCH_PARENT : 0,
                isVertical ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                mPaneWeights[i]));
        }

        if (mDivider != null) {
            int dividerSize = dp(DIVIDER_SIZE_DP);
            mDivider.setLayoutParams(new LinearLayout.LayoutParams(
                isVertical ? LinearLayout.LayoutParams.MATCH_PARENT : dividerSize,
                isVertical ? dividerSize : LinearLayout.LayoutParams.MATCH_PARENT));
        }
    }

    /** Draw a border around {@code activeIndex}, so that the active pane can be told apart from the other. */
    public void updatePaneActiveStates(int activeIndex) {
        for (int i = 0; i < mPaneContainers.length; i++) {
            FrameLayout container = mPaneContainers[i];
            if (container == null) continue;

            container.setForeground(mEnabled && i == activeIndex
                ? ContextCompat.getDrawable(mActivity, R.drawable.terminal_pane_active_border) : null);
        }
    }

    /** Make the divider between the panes draggable, so that their relative sizes can be changed. */
    public void setUpDividerTouchListener() {
        if (mDivider == null) return;

        mDivider.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    if (mPanesContainer != null) mPanesContainer.getLocationOnScreen(mDividerContainerLocation);
                    mDividerContainerSize = mOrientation == LinearLayout.VERTICAL
                        ? containerHeight() : containerWidth();
                    mDividerLastUpdateTime = 0;
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    // Throttle the updates, since changing the size of the panes resizes the terminal
                    // sessions and sends a SIGWINCH to their processes.
                    long now = SystemClock.uptimeMillis();
                    if (now - mDividerLastUpdateTime < DIVIDER_DRAG_INTERVAL_MS) return true;
                    mDividerLastUpdateTime = now;
                    updatePaneWeights(event.getRawX(), event.getRawY());
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                }
                default:
                    return false;
            }
        });
    }

    /** Change the relative sizes of the panes from the position of a drag event of the divider. */
    private void updatePaneWeights(float rawX, float rawY) {
        if (!mEnabled || mDividerContainerSize <= 0) return;

        final boolean isVertical = mOrientation == LinearLayout.VERTICAL;
        float position = (isVertical ? rawY : rawX) -
            (isVertical ? mDividerContainerLocation[1] : mDividerContainerLocation[0]);
        float ratio = clampPaneRatio(position / mDividerContainerSize);
        if (Math.abs(ratio - mPaneWeights[0]) < 0.01f) return;

        setPaneRatio(ratio);
        for (int i = 0; i < mPaneContainers.length; i++) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mPaneContainers[i].getLayoutParams();
            params.weight = mPaneWeights[i];
            mPaneContainers[i].setLayoutParams(params);
        }
    }

    private int containerHeight() {
        return mPanesContainer != null ? mPanesContainer.getHeight() : 0;
    }

    private int containerWidth() {
        return mPanesContainer != null ? mPanesContainer.getWidth() : 0;
    }

    private int dp(float dp) {
        return Math.round(ViewUtils.dpToPx(mActivity, dp));
    }
}
