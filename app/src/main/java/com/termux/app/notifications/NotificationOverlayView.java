package com.termux.app.notifications;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.statusbar.StatusBarColors;
import com.termux.shared.data.DataUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TerminalNotification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The overlay that shows the {@link NotificationCardView cards} of the notifications in a corner of
 * the terminal, like the notifications of `nvim-notify` and other tui notification plugins.
 *
 * The overlay is a child of the content container of the sessions drawer, so that the drawer is
 * always drawn over it, and it does not resize the terminal, so that the terminal grid and the rows
 * that the terminal programs see do not change while a card is shown.
 */
public class NotificationOverlayView extends FrameLayout {

    /** The listener that is called when a card is clicked or closed. */
    public interface OnNotificationActionListener {
        /**
         * Called when a card is clicked, like to jump to the command that it is about.
         *
         * @param notification The notification of the card that was clicked.
         */
        void onTerminalNotificationClicked(@NonNull TerminalNotification notification);

        /**
         * Called when a card is closed by the user or because it expired.
         *
         * @param notification The notification of the card that was closed.
         * @param expired Whether the card expired instead of being closed by the user.
         */
        void onTerminalNotificationClosed(@NonNull TerminalNotification notification, boolean expired);
    }

    private static final String LOG_TAG = "NotificationOverlayView";

    private static final float CARD_MARGIN_DP = 8;
    private static final float CARD_SLIDE_IN_OFFSET_DP = 24;

    /** The set of colors that a card is drawn with, resolved from the configuration and the color scheme. */
    private static final class CardColors {
        final int background;
        final int foreground;
        final int accent;

        CardColors(int background, int foreground, int accent) {
            this.background = background;
            this.foreground = foreground;
            this.accent = accent;
        }
    }

    @NonNull
    private final LinearLayout mCardsContainer;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** The runnable that closes a card when it expires, for the cards that have one. */
    @NonNull
    private final Map<NotificationCardView, Runnable> mExpiryRunnables = new HashMap<>();

    @NonNull
    private NotificationConfig mConfig = new NotificationConfig();
    @Nullable
    private OnNotificationActionListener mListener;
    /** The height of the status bar in pixels, so that the cards are not drawn over it. */
    private int mStatusBarInsetPx;

    private final NotificationCardView.OnCardActionListener mCardActionListener = new NotificationCardView.OnCardActionListener() {
        @Override
        public void onNotificationCardClicked(@NonNull NotificationCardView card, @NonNull TerminalNotification notification) {
            if (mListener != null) mListener.onTerminalNotificationClicked(notification);
        }

        @Override
        public void onNotificationCardClosed(@NonNull NotificationCardView card, @NonNull TerminalNotification notification, boolean expired) {
            removeCard(card, notification, expired);
        }
    };

    public NotificationOverlayView(@NonNull Context context) {
        this(context, null);
    }

    public NotificationOverlayView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mCardsContainer = new LinearLayout(context);
        mCardsContainer.setOrientation(LinearLayout.VERTICAL);
        addView(mCardsContainer, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        setClipChildren(false);
        setClipToPadding(false);
        // The overlay must not consume the touches of the terminal, only the cards may do that.
        setClickable(false);
        setFocusable(false);
        setVisibility(View.GONE);
    }

    /** Set the listener that is called when a card is clicked or closed. */
    public void setOnNotificationActionListener(@Nullable OnNotificationActionListener listener) {
        mListener = listener;
    }

    /** Apply the configuration, which decides the corner that the cards are shown in and their width. */
    public void applyConfig(@NonNull NotificationConfig config) {
        mConfig = config;

        LayoutParams layoutParams = getLayoutParams() instanceof LayoutParams ? (LayoutParams) getLayoutParams() : null;
        if (layoutParams != null) {
            layoutParams.gravity = getConfiguredGravity();
            applyMargins(layoutParams);
            setLayoutParams(layoutParams);
        }

        for (int i = 0; i < mCardsContainer.getChildCount(); i++) {
            View child = mCardsContainer.getChildAt(i);
            if (child instanceof NotificationCardView) {
                NotificationCardView card = (NotificationCardView) child;
                applyCardWidth(card);
                TerminalNotification notification = card.getNotification();
                if (notification != null) applyCardColors(card, notification);
            }
        }
    }

    /**
     * Set the height of the status bar in pixels, so that the cards are moved below it when they are
     * shown in the top corner, since the status bar is drawn in the same container.
     */
    public void setStatusBarInsetPx(int insetPx) {
        if (mStatusBarInsetPx == insetPx) return;
        mStatusBarInsetPx = Math.max(0, insetPx);

        LayoutParams layoutParams = getLayoutParams() instanceof LayoutParams ? (LayoutParams) getLayoutParams() : null;
        if (layoutParams != null) {
            applyMargins(layoutParams);
            setLayoutParams(layoutParams);
        }
    }

    /** Show a card for {@code notification}, or update the card that already shows it if it has an id. */
    public void showNotification(@NonNull TerminalNotification notification) {
        if (notification.isEmpty()) return;

        if (updateNotification(notification)) return;

        NotificationCardView card = new NotificationCardView(getContext());
        card.setOnCardActionListener(mCardActionListener);
        card.setNotification(notification);
        applyCardColors(card, notification);
        applyCardWidth(card);

        // The newest card is added at the edge that it is shown at, so that the cards stack away from
        // the edge instead of pushing the newest one out of view.
        if (mConfig.isBottomPosition())
            mCardsContainer.addView(card);
        else
            mCardsContainer.addView(card, 0);

        int offsetPx = Math.round(ViewUtils.dpToPx(getContext(), CARD_SLIDE_IN_OFFSET_DP));
        card.animateIn(mConfig.isEndPosition() ? offsetPx : -offsetPx);

        setVisibility(View.VISIBLE);
        enforceMaxVisible();
        scheduleExpiry(card, notification);
    }

    /**
     * Update the card of the notification with the same id as {@code notification}.
     *
     * @return Whether a card was updated, or {@code false} if no card shows a notification with that id.
     */
    public boolean updateNotification(@NonNull TerminalNotification notification) {
        NotificationCardView card = findCardById(notification.getId());
        if (card == null) return false;

        card.setNotification(notification);
        applyCardColors(card, notification);
        scheduleExpiry(card, notification);
        return true;
    }

    /** Close the card of the notification with the id {@code id}, if one is shown. */
    public void closeNotification(@Nullable String id) {
        NotificationCardView card = findCardById(id);
        if (card != null) card.dismiss(false);
    }

    /** Close all cards. */
    public void clearNotifications() {
        List<NotificationCardView> cards = new ArrayList<>();
        for (int i = 0; i < mCardsContainer.getChildCount(); i++) {
            View child = mCardsContainer.getChildAt(i);
            if (child instanceof NotificationCardView) cards.add((NotificationCardView) child);
        }

        for (NotificationCardView card : cards) card.dismiss(false);
    }

    /** @return The number of cards that are currently shown. */
    public int getNotificationCount() {
        return mCardsContainer.getChildCount();
    }

    /** @return The ids of the cards that are currently shown, in the order they are shown. */
    @NonNull
    public List<String> getNotificationIds() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < mCardsContainer.getChildCount(); i++) {
            View child = mCardsContainer.getChildAt(i);
            if (!(child instanceof NotificationCardView)) continue;
            TerminalNotification notification = ((NotificationCardView) child).getNotification();
            if (notification != null && notification.getId() != null) ids.add(notification.getId());
        }
        return ids;
    }

    /** @return The card that shows the notification with the id {@code id}, or {@code null}. */
    @Nullable
    private NotificationCardView findCardById(@Nullable String id) {
        if (id == null) return null;

        for (int i = 0; i < mCardsContainer.getChildCount(); i++) {
            View child = mCardsContainer.getChildAt(i);
            if (!(child instanceof NotificationCardView)) continue;
            NotificationCardView card = (NotificationCardView) child;
            TerminalNotification notification = card.getNotification();
            if (notification != null && id.equals(notification.getId())) return card;
        }

        return null;
    }

    /** Remove the card {@code card} from the overlay and tell the listener about it. */
    private void removeCard(@NonNull NotificationCardView card, @NonNull TerminalNotification notification, boolean expired) {
        Runnable expiryRunnable = mExpiryRunnables.remove(card);
        if (expiryRunnable != null) mHandler.removeCallbacks(expiryRunnable);

        mCardsContainer.removeView(card);
        if (mCardsContainer.getChildCount() == 0) setVisibility(View.GONE);

        if (mListener != null) mListener.onTerminalNotificationClosed(notification, expired);
    }

    /** Close the oldest cards until at most `notification-max-visible` cards are shown. */
    private void enforceMaxVisible() {
        int maxVisible = Math.max(NotificationConfig.MIN_MAX_VISIBLE, mConfig.maxVisible);

        // The cards that are already being dismissed still occupy a child until their animation has
        // finished, so they are skipped when the excess is calculated.
        List<NotificationCardView> cards = new ArrayList<>();
        for (int i = 0; i < mCardsContainer.getChildCount(); i++) {
            View child = mCardsContainer.getChildAt(i);
            if (!(child instanceof NotificationCardView)) continue;
            NotificationCardView card = (NotificationCardView) child;
            if (card.isDismissing()) continue;
            cards.add(card);
        }

        int excess = cards.size() - maxVisible;
        if (excess <= 0) return;

        // The oldest card is the last one for the top positions and the first one for the bottom
        // positions, since the newest card is always added at the edge that the cards are shown at.
        for (int i = 0; i < cards.size() && excess > 0; i++) {
            NotificationCardView card = mConfig.isBottomPosition() ? cards.get(i) : cards.get(cards.size() - 1 - i);
            card.dismiss(false);
            excess--;
        }
    }

    /** Schedule the card to be closed when its expiry time has passed. */
    private void scheduleExpiry(@NonNull NotificationCardView card, @NonNull TerminalNotification notification) {
        Runnable previousRunnable = mExpiryRunnables.remove(card);
        if (previousRunnable != null) mHandler.removeCallbacks(previousRunnable);

        long expiryMs = notification.getExpiryMs();
        if (expiryMs == TerminalNotification.EXPIRY_DEFAULT) expiryMs = mConfig.durationMs;
        // A negative or zero expiry means that the card is not closed automatically.
        if (expiryMs <= TerminalNotification.EXPIRY_NEVER) return;

        Runnable expiryRunnable = () -> {
            if (mExpiryRunnables.get(card) != null) card.dismiss(true);
        };

        mExpiryRunnables.put(card, expiryRunnable);
        mHandler.postDelayed(expiryRunnable, expiryMs);
    }

    /** @return The gravity of the overlay for the configured `notification-position`. */
    private int getConfiguredGravity() {
        if (mConfig.isBottomPosition())
            return mConfig.isEndPosition() ? Gravity.BOTTOM | Gravity.END : Gravity.BOTTOM | Gravity.START;
        else
            return mConfig.isEndPosition() ? Gravity.TOP | Gravity.END : Gravity.TOP | Gravity.START;
    }

    /** Apply the margins of the overlay for the configured position and the height of the status bar. */
    private void applyMargins(@NonNull LayoutParams layoutParams) {
        int marginPx = Math.round(ViewUtils.dpToPx(getContext(), CARD_MARGIN_DP));
        int edgeMarginPx = marginPx + mStatusBarInsetPx;

        layoutParams.leftMargin = marginPx;
        layoutParams.rightMargin = marginPx;
        layoutParams.topMargin = mConfig.isBottomPosition() ? marginPx : edgeMarginPx;
        layoutParams.bottomMargin = mConfig.isBottomPosition() ? edgeMarginPx : marginPx;
    }

    /** Apply the width of the cards from the `notification-width` property to {@code card}. */
    private void applyCardWidth(@NonNull NotificationCardView card) {
        if (mConfig.widthDp > NotificationConfig.WIDTH_AUTO) {
            card.setCardWidth(Math.round(ViewUtils.dpToPx(getContext(), mConfig.widthDp)), 0);
        } else {
            card.setCardWidth(0, Math.round(ViewUtils.dpToPx(getContext(), NotificationConfig.AUTO_MAX_WIDTH_DP)));
        }
    }

    /** Apply the colors of {@code notification} to {@code card}. */
    private void applyCardColors(@NonNull NotificationCardView card, @NonNull TerminalNotification notification) {
        CardColors colors = getCardColors(notification);
        card.setCardColors(colors.background, colors.foreground, colors.accent);
    }

    /** @return The colors that the card of {@code notification} is drawn with. */
    @NonNull
    private CardColors getCardColors(@NonNull TerminalNotification notification) {
        int foreground = StatusBarColors.resolve(mConfig.foregroundColor, StatusBarColors.THEME_FOREGROUND);
        int background = getBackgroundColor(foreground);
        int accent = getAccentColor(notification.getKind());

        return new CardColors(background, foreground, accent);
    }

    /**
     * @return The background color of the cards, which is derived from the background color of the
     *         color scheme if `notification-background-color` is not set, so that the cards are
     *         visible on top of the terminal background.
     */
    private int getBackgroundColor(int foregroundColor) {
        if (!DataUtils.isNullOrEmpty(mConfig.backgroundColor))
            return StatusBarColors.resolve(mConfig.backgroundColor, StatusBarColors.THEME_BACKGROUND);

        int backgroundColor = StatusBarColors.getThemeColor(StatusBarColors.THEME_BACKGROUND);
        // Blend the background towards the foreground color, so that the cards are visible when the
        // terminal background and the cards would otherwise have the same color.
        return TerminalColorScheme.blendColors(backgroundColor, foregroundColor, 0.12) | 0xFF000000;
    }

    /** @return The accent color of the cards of {@code kind}. */
    private int getAccentColor(@NonNull TerminalNotification.Kind kind) {
        String value;
        String defaultThemeColorName;
        switch (kind) {
            case FAILURE:
                value = mConfig.accentFailureColor;
                defaultThemeColorName = "color1";
                break;
            case SUCCESS:
                value = mConfig.accentSuccessColor;
                defaultThemeColorName = "color2";
                break;
            case WARNING:
                value = mConfig.accentWarningColor;
                defaultThemeColorName = "color3";
                break;
            case INFO:
            default:
                value = mConfig.accentInfoColor;
                defaultThemeColorName = "color4";
                break;
        }

        int accent = StatusBarColors.resolve(value, defaultThemeColorName);
        // An accent color of a color scheme without a color set for it can be fully transparent, in
        // which case the card would have no visible accent bar, so fall back to an opaque color.
        if (Color.alpha(accent) == 0) accent = StatusBarColors.getThemeColor(StatusBarColors.THEME_FOREGROUND);
        return accent | 0xFF000000;
    }
}
