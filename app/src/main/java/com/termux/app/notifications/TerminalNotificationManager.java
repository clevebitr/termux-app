package com.termux.app.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.terminal.TerminalNotification;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The manager of the notification cards, which reads the `notification-*` properties, applies them
 * to the {@link NotificationOverlayView} and decides whether a {@link TerminalNotification} is shown.
 *
 * The cards are only shown when the `notification-enabled` property is set, and each source has its
 * own switch, like `notification-exit-code-enabled` for the cards of commands that exit with a non
 * zero exit code, so that the behavior of the fork is opt-in and the app does not show notifications
 * that the user did not ask for.
 */
public final class TerminalNotificationManager {

    private static final String LOG_TAG = "TerminalNotificationManager";

    /** The listener that is called when the user interacts with a card. */
    public interface Listener {
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

    @NonNull
    private final Context mContext;
    @NonNull
    private final NotificationOverlayView mOverlay;
    @NonNull
    private NotificationConfig mConfig = new NotificationConfig();
    @Nullable
    private Listener mListener;
    /**
     * The action that is run when the card of a notification is clicked, like jumping to the command
     * that the notification is about. The keys are compared by identity, since a notification is
     * replaced by a new instance when its card is updated.
     */
    @NonNull
    private final Map<TerminalNotification, Runnable> mClickActions = new IdentityHashMap<>();

    public TerminalNotificationManager(@NonNull Context context, @NonNull NotificationOverlayView overlay) {
        mContext = context;
        mOverlay = overlay;

        mOverlay.setOnNotificationActionListener(new NotificationOverlayView.OnNotificationActionListener() {
            @Override
            public void onTerminalNotificationClicked(@NonNull TerminalNotification notification) {
                Runnable clickAction = mClickActions.remove(notification);
                if (clickAction != null) {
                    clickAction.run();
                    return;
                }

                if (mListener != null) mListener.onTerminalNotificationClicked(notification);
            }

            @Override
            public void onTerminalNotificationClosed(@NonNull TerminalNotification notification, boolean expired) {
                mClickActions.remove(notification);
                if (mListener != null) mListener.onTerminalNotificationClosed(notification, expired);
            }
        });
    }

    /** Set the listener that is called when the user interacts with a card. */
    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * Reload the configuration from the `termux.properties` file and apply it to the overlay.
     *
     * The `notification-*` properties are not part of the `TERMUX_APP_PROPERTIES_LIST` that the
     * shared library converts to internal values, and `getProperties(true)` only returns the keys of
     * that list, so the properties must be read from disk directly, like for the `status-bar-*`
     * properties. Reloads are rare (activity start and styling changes), so reading the file here is
     * fine.
     */
    public void reload() {
        Properties properties = null;
        TermuxAppSharedProperties sharedProperties = TermuxAppSharedProperties.getProperties();
        if (sharedProperties != null) properties = sharedProperties.getProperties(false);

        mConfig = NotificationConfig.parse(properties);
        mOverlay.applyConfig(mConfig);

        Logger.logDebug(LOG_TAG, "Reloaded notification config, enabled: " + mConfig.enabled
            + ", position: " + mConfig.position + ", max visible: " + mConfig.maxVisible);
    }

    /** @return The configuration that was loaded by the last call to {@link #reload()}. */
    @NonNull
    public NotificationConfig getConfig() {
        return mConfig;
    }

    /** @return Whether the notification cards are enabled by the `notification-enabled` property. */
    public boolean isEnabled() {
        return mConfig.enabled;
    }

    /**
     * Show a card for {@code notification} if the cards of its source are enabled.
     *
     * @param notification The notification to show.
     * @return Whether the notification was passed to the overlay.
     */
    public boolean notify(@NonNull TerminalNotification notification) {
        return notify(notification, null);
    }

    /**
     * Show a card for {@code notification} if the cards of its source are enabled, and run
     * {@code onClickAction} when the card is clicked.
     *
     * @param notification The notification to show.
     * @param onClickAction The action to run when the card is clicked, or {@code null} to tell the
     *                      {@link Listener} about the click instead.
     * @return Whether the notification was passed to the overlay.
     */
    public boolean notify(@NonNull TerminalNotification notification, @Nullable Runnable onClickAction) {
        if (!isChannelEnabled(notification)) return false;

        showNotification(notification, onClickAction);
        return true;
    }

    /**
     * Show a card for {@code notification} even if the cards of its source are disabled, which is
     * used by the "send test notification" action of the terminal menu, so that the user can tune the
     * appearance of the cards without having to enable a channel.
     */
    public void notifyForTesting(@NonNull TerminalNotification notification) {
        showNotification(notification, null);
    }

    /** @return Whether the cards of the source of {@code notification} are enabled. */
    public boolean isChannelEnabled(@NonNull TerminalNotification notification) {
        if (!mConfig.enabled) return false;

        switch (notification.getSource()) {
            case EXIT_CODE:
                return mConfig.exitCodeEnabled;
            case OSC:
                return mConfig.oscEnabled && mConfig.isOscAppAllowed(notification.getAppName());
            case APP_EVENT:
            default:
                return mConfig.eventsEnabled;
        }
    }

    /** Close all cards. */
    public void clear() {
        mOverlay.clearNotifications();
    }

    /** Show the card of {@code notification} without checking whether its channel is enabled. */
    private void showNotification(@NonNull TerminalNotification notification, @Nullable Runnable onClickAction) {
        if (notification.isEmpty()) {
            Logger.logWarn(LOG_TAG, "Not showing an empty notification from \"" + notification.getSource() + "\"");
            return;
        }

        if (onClickAction != null) mClickActions.put(notification, onClickAction);
        mOverlay.showNotification(notification);
    }
}
