package com.termux.app.notifications;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalNotification;

/**
 * A single notification card, which shows the title, the body and an optional details line of a
 * {@link TerminalNotification} next to a colored accent bar on its left edge, like the notification
 * cards of `nvim-notify` and other tui notification plugins.
 *
 * The card is dismissed when it is clicked and {@link OnCardActionListener#onNotificationCardClicked}
 * is called, or when it is swiped sideways, when its close button is clicked or when it expires.
 */
public class NotificationCardView extends FrameLayout {

    /** The listener that is called when the user interacts with the card. */
    public interface OnCardActionListener {
        /**
         * Called when the card itself is clicked, like to jump to the command it is about.
         *
         * @param card The card that was clicked.
         * @param notification The notification that the card shows.
         */
        void onNotificationCardClicked(@NonNull NotificationCardView card, @NonNull TerminalNotification notification);

        /**
         * Called when the card must be removed, because it was swiped away, its close button was
         * clicked or it expired.
         *
         * @param card The card that must be removed.
         * @param notification The notification that the card shows.
         * @param expired Whether the card expired instead of being closed by the user.
         */
        void onNotificationCardClosed(@NonNull NotificationCardView card, @NonNull TerminalNotification notification, boolean expired);
    }

    /** The maximum number of lines of the body, so that a long message does not cover the terminal. */
    private static final int MAX_BODY_LINES = 3;

    private static final float CORNER_RADIUS_DP = 8;
    private static final float CARD_ELEVATION_DP = 6;

    @NonNull
    private final View mAccentBar;
    @NonNull
    private final TextView mTitleView;
    @NonNull
    private final TextView mBodyView;
    @NonNull
    private final TextView mMetaView;
    @NonNull
    private final TextView mCloseView;

    @Nullable
    private TerminalNotification mNotification;
    @Nullable
    private OnCardActionListener mListener;

    /** The fixed width of the card in pixels, or 0 to fit its content. */
    private int mFixedWidthPx;
    /** The maximum width of the card in pixels when it fits its content, or 0 to not limit it. */
    private int mMaxWidthPx;
    private final int mCornerRadiusPx;
    private final int mTouchSlopPx;
    private final int mDismissThresholdPx;

    private float mTouchStartX;
    private boolean mIsDragging;
    /** Whether the card is being animated away, so that a second dismissal is ignored. */
    private boolean mIsDismissing;

    public NotificationCardView(@NonNull Context context) {
        this(context, null);
    }

    public NotificationCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mCornerRadiusPx = Math.round(ViewUtils.dpToPx(context, CORNER_RADIUS_DP));
        mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        mDismissThresholdPx = dp(56);

        setElevation(ViewUtils.dpToPx(context, CARD_ELEVATION_DP));
        // A minimum width keeps a card with a single short word from looking cramped.
        setMinimumWidth(dp(152));
        setClickable(true);
        setFocusable(true);

        mAccentBar = new View(context);
        LayoutParams accentBarParams = new LayoutParams(dp(4), LayoutParams.MATCH_PARENT);
        accentBarParams.gravity = Gravity.START;
        mAccentBar.setLayoutParams(accentBarParams);
        addView(mAccentBar);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        // The start padding includes the width of the accent bar, which is drawn below the content.
        content.setPadding(dp(4) + dp(10), dp(8), dp(2), dp(8));
        content.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(content);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        content.addView(texts);

        mTitleView = new TextView(context);
        mTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        mTitleView.setMaxLines(2);
        mTitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(mTitleView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mBodyView = new TextView(context);
        mBodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mBodyView.setMaxLines(MAX_BODY_LINES);
        mBodyView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(mBodyView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mMetaView = new TextView(context);
        mMetaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mMetaView.setMaxLines(1);
        mMetaView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(mMetaView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // The close symbol is a plain text view with the same glyph as the close button of the search
        // bar, so that no new drawable has to be added for every color scheme.
        mCloseView = new TextView(context);
        mCloseView.setText(R.string.action_search_close_symbol);
        mCloseView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mCloseView.setGravity(Gravity.CENTER);
        mCloseView.setPadding(dp(6), 0, dp(6), 0);
        mCloseView.setMinWidth(dp(32));
        mCloseView.setMinHeight(dp(32));
        mCloseView.setContentDescription(context.getString(R.string.notification_close));
        mCloseView.setClickable(true);
        mCloseView.setOnClickListener(v -> dismiss(false));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        closeParams.gravity = Gravity.TOP;
        content.addView(mCloseView, closeParams);
    }

    /** Set the listener that is called when the user interacts with the card. */
    public void setOnCardActionListener(@Nullable OnCardActionListener listener) {
        mListener = listener;
    }

    /** @return The notification that the card shows, or {@code null} if none has been set yet. */
    @Nullable
    public TerminalNotification getNotification() {
        return mNotification;
    }

    /**
     * Set the width of the card.
     *
     * @param fixedWidthPx The width of the card in pixels, or 0 to fit its content.
     * @param maxWidthPx The maximum width of the card in pixels when it fits its content, or 0 to not
     *                   limit it. It is only used when {@code fixedWidthPx} is 0.
     */
    public void setCardWidth(int fixedWidthPx, int maxWidthPx) {
        mFixedWidthPx = Math.max(0, fixedWidthPx);
        mMaxWidthPx = Math.max(0, maxWidthPx);
        requestLayout();
    }

    /** Set the notification that the card shows. */
    public void setNotification(@NonNull TerminalNotification notification) {
        mNotification = notification;

        setTextViewText(mTitleView, notification.getTitle());
        setTextViewText(mBodyView, notification.getBody());
        setTextViewText(mMetaView, notification.getMeta());

        setContentDescription(notification.getTitle() != null ? notification.getTitle()
            : notification.getBody() != null ? notification.getBody() : "");
    }

    /**
     * Apply the colors of the notification cards.
     *
     * @param backgroundColor The background color of the card.
     * @param foregroundColor The color of the title and the body of the card.
     * @param accentColor The color of the accent bar on the left edge of the card.
     */
    public void setCardColors(int backgroundColor, int foregroundColor, int accentColor) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(mCornerRadiusPx);
        background.setColor(backgroundColor);
        setBackground(background);

        // Clip the accent bar, which is a rectangle, to the rounded corners of the card.
        setClipToOutline(true);

        mAccentBar.setBackgroundColor(accentColor);

        mTitleView.setTextColor(foregroundColor);
        mBodyView.setTextColor(foregroundColor);
        mMetaView.setTextColor(withAlpha(foregroundColor, 0.65f));
        mCloseView.setTextColor(withAlpha(foregroundColor, 0.65f));
    }

    /** @return Whether the card is being animated away, so that it must not be dismissed again. */
    public boolean isDismissing() {
        return mIsDismissing;
    }

    /** Dismiss the card with an animation and tell the listener to remove it. */
    public void dismiss(boolean expired) {
        if (mIsDismissing || mNotification == null) return;
        mIsDismissing = true;

        float targetTranslationX = getTranslationX() >= 0 ? getWidth() : -getWidth();
        animate()
            .alpha(0f)
            .translationX(targetTranslationX)
            .setDuration(150)
            .withEndAction(() -> {
                if (mListener != null && mNotification != null)
                    mListener.onNotificationCardClosed(this, mNotification, expired);
            })
            .start();
    }

    /** Animate the card in from the edge of the terminal. */
    public void animateIn(int horizontalOffsetPx) {
        setAlpha(0f);
        setTranslationX(horizontalOffsetPx);
        animate().alpha(1f).translationX(0f).setDuration(180).start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Only the width is controlled, since the height of the card fits its content. A card that
        // fits its content would otherwise grow as wide as its longest line.
        if (mFixedWidthPx > 0) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(mFixedWidthPx, MeasureSpec.EXACTLY);
        } else if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY && mMaxWidthPx > 0) {
            int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
            if (availableWidth == 0 || availableWidth > mMaxWidthPx)
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(mMaxWidthPx, MeasureSpec.AT_MOST);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mIsDismissing) return true;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTouchStartX = event.getRawX();
                mIsDragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getRawX() - mTouchStartX;
                if (!mIsDragging && Math.abs(deltaX) > mTouchSlopPx) mIsDragging = true;
                if (mIsDragging) setTranslationX(deltaX);
                return true;
            case MotionEvent.ACTION_UP:
                float draggedX = event.getRawX() - mTouchStartX;
                if (mIsDragging && Math.abs(draggedX) > mDismissThresholdPx) {
                    dismiss(false);
                } else if (mIsDragging) {
                    animate().translationX(0f).setDuration(120).start();
                } else {
                    performClick();
                }
                mIsDragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                mIsDragging = false;
                animate().translationX(0f).setDuration(120).start();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();

        if (mNotification != null && mListener != null)
            mListener.onNotificationCardClicked(this, mNotification);

        // A clicked card is consumed by the action that it runs, so it is closed right away instead of
        // staying in the corner until it expires.
        dismiss(false);

        return true;
    }

    /** Set the text of a text view and hide it when the text is empty. */
    private static void setTextViewText(@NonNull TextView textView, @Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            textView.setText(null);
            textView.setVisibility(View.GONE);
        } else {
            textView.setText(text);
            textView.setVisibility(View.VISIBLE);
        }
    }

    /** @return The {@code color} with the {@code alpha} factor applied to its alpha channel. */
    private static int withAlpha(int color, float alpha) {
        int colorAlpha = Math.round(Color.alpha(color) * alpha);
        return (color & 0x00FFFFFF) | (colorAlpha << 24);
    }

    private int dp(int dp) {
        return Math.round(ViewUtils.dpToPx(getContext(), dp));
    }
}
