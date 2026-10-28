package com.termux.app.notifications;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
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
 * The default style is flat and square like a tui window, so that the cards look like part of the
 * terminal instead of like material design cards: no rounded corners, no elevation and a 1dp border in
 * the accent color. All of it can be tuned with the style that {@link #setCardStyle} is given.
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

    /** The colors and metrics that a card is drawn with, resolved by the overlay from the configuration. */
    public static final class Style {
        public final int backgroundColor;
        public final int foregroundColor;
        public final int accentColor;
        /** The radius of the corners in pixels, 0 for the square corners of a tui window. */
        public final int cornerRadiusPx;
        /** The elevation of the card in pixels, 0 for the flat look of a tui window. */
        public final int elevationPx;
        /** The width of the border around the card in pixels, 0 for no border. */
        public final int borderWidthPx;
        /** The width of the accent bar on the left edge of the card in pixels, 0 for no accent bar. */
        public final int accentWidthPx;
        /** Whether the text of the card is drawn with the monospace font of the terminal. */
        public final boolean monospace;
        /** The maximum number of lines of the body of the card. */
        public final int bodyMaxLines;
        /** The font size of the title of the card in sp. */
        public final int fontSizeSp;

        public Style(int backgroundColor, int foregroundColor, int accentColor, int cornerRadiusPx,
                     int elevationPx, int borderWidthPx, int accentWidthPx, boolean monospace, int bodyMaxLines,
                     int fontSizeSp) {
            this.backgroundColor = backgroundColor;
            this.foregroundColor = foregroundColor;
            this.accentColor = accentColor;
            this.cornerRadiusPx = Math.max(0, cornerRadiusPx);
            this.elevationPx = Math.max(0, elevationPx);
            this.borderWidthPx = Math.max(0, borderWidthPx);
            this.accentWidthPx = Math.max(0, accentWidthPx);
            this.monospace = monospace;
            this.bodyMaxLines = Math.max(1, bodyMaxLines);
            this.fontSizeSp = Math.max(6, fontSizeSp);
        }
    }

    private static final float CARD_PADDING_HORIZONTAL_DP = 10;
    private static final float CARD_PADDING_VERTICAL_DP = 7;
    private static final float TITLE_TEXT_SIZE_SP = 13;
    private static final float BODY_TEXT_SIZE_SP = 12;
    private static final float META_TEXT_SIZE_SP = 10.5f;

    @NonNull
    private final LinearLayout mContent;
    @NonNull
    private final TextView mTitleView;
    @NonNull
    private final TextView mBodyView;
    @NonNull
    private final TextView mMetaView;
    @NonNull
    private final TextView mCloseView;
    /** The paint of the accent bar, which is drawn by the card itself instead of by a child view. */
    @NonNull
    private final Paint mAccentPaint = new Paint();

    @Nullable
    private TerminalNotification mNotification;
    @Nullable
    private OnCardActionListener mListener;

    /** The fixed width of the card in pixels, or 0 to fit its content. */
    private int mFixedWidthPx;
    /** The maximum width of the card in pixels when it fits its content, or 0 to not limit it. */
    private int mMaxWidthPx;
    /** The width of the accent bar in pixels, 0 for none. */
    private int mAccentWidthPx;
    private boolean mMonospace;

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

        mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        mDismissThresholdPx = dp(56);

        setClickable(true);
        setFocusable(true);
        // A minimum width keeps a card with a single short word from looking cramped.
        setMinimumWidth(dp(152));

        mContent = new LinearLayout(context);
        mContent.setOrientation(LinearLayout.HORIZONTAL);
        mContent.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(mContent);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        mContent.addView(texts);

        mTitleView = new TextView(context);
        mTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE_SP);
        mTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        mTitleView.setMaxLines(2);
        mTitleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(mTitleView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mBodyView = new TextView(context);
        mBodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_TEXT_SIZE_SP);
        mBodyView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(mBodyView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mMetaView = new TextView(context);
        mMetaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, META_TEXT_SIZE_SP);
        mMetaView.setMaxLines(1);
        mMetaView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(mMetaView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // The close symbol is a plain text view with the same glyph as the close button of the search
        // bar, so that no new drawable has to be added for every color scheme.
        mCloseView = new TextView(context);
        mCloseView.setText(R.string.action_search_close_symbol);
        mCloseView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mCloseView.setGravity(Gravity.CENTER);
        mCloseView.setPadding(dp(4), 0, dp(4), 0);
        mCloseView.setMinWidth(dp(28));
        mCloseView.setMinHeight(dp(28));
        mCloseView.setContentDescription(context.getString(R.string.notification_close));
        mCloseView.setClickable(true);
        mCloseView.setOnClickListener(v -> dismiss(false));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        closeParams.gravity = Gravity.TOP;
        mContent.addView(mCloseView, closeParams);

        // Apply the default style, so that a card that is added to a layout without a style does not
        // look broken, like in the layout preview of an ide.
        setCardStyle(new Style(0xFF000000, 0xFFFFFFFF, 0xFFFF5252,
            0, 0, dp(1), dp(4), true, 2, 13));
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

    /** Apply the colors and metrics of the card, like the corner radius and the border. */
    public void setCardStyle(@NonNull Style style) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(style.cornerRadiusPx);
        background.setColor(style.backgroundColor);
        if (style.borderWidthPx > 0)
            background.setStroke(style.borderWidthPx, style.accentColor);
        setBackground(background);

        setElevation(style.elevationPx);

        mAccentWidthPx = style.accentWidthPx;
        mAccentPaint.setColor(style.accentColor);
        mAccentPaint.setStyle(Paint.Style.FILL);

        mContent.setPadding(style.accentWidthPx + dp(CARD_PADDING_HORIZONTAL_DP), dp(CARD_PADDING_VERTICAL_DP),
            dp(2), dp(CARD_PADDING_VERTICAL_DP));

        // The body and the details line are slightly smaller than the title, like in the cards of the tui
        // notification plugins, and are clamped so that they stay readable at small font sizes.
        mTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSizeSp);
        mBodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(6, style.fontSizeSp - 1));
        mMetaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(6, style.fontSizeSp - 2.5f));
        mCloseView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(6, style.fontSizeSp - 1));

        mMonospace = style.monospace;
        Typeface typeface = mMonospace ? Typeface.MONOSPACE : null;
        mTitleView.setTypeface(mMonospace ? Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) : Typeface.DEFAULT_BOLD);
        mBodyView.setTypeface(typeface);
        mMetaView.setTypeface(typeface);
        mCloseView.setTypeface(typeface);

        mTitleView.setTextColor(style.foregroundColor);
        mBodyView.setTextColor(style.foregroundColor);
        mMetaView.setTextColor(withAlpha(style.foregroundColor, 0.65f));
        mCloseView.setTextColor(withAlpha(style.foregroundColor, 0.65f));

        mBodyView.setMaxLines(style.bodyMaxLines);

        invalidate();
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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // The accent bar is drawn by the card instead of by a child view, since a child view with
        // `layout_height="match_parent"` would be measured with the `AT_MOST` spec of the card and
        // would then take the whole available height, which made the card fill the screen.
        if (mAccentWidthPx > 0)
            canvas.drawRect(0, 0, mAccentWidthPx, getHeight(), mAccentPaint);
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

    private int dp(float dp) {
        return Math.round(ViewUtils.dpToPx(getContext(), dp));
    }
}
