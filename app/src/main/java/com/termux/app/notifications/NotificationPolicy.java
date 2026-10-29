package com.termux.app.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalNotification;

/**
 * The decisions that the notification layer takes before it shows a notification, which are pure
 * functions so that they can be tested without an activity, a view or a terminal session.
 *
 * The {@link TerminalNotificationManager}, the {@link OscNotificationHandler} and the
 * {@link AppEventNotifier} call these instead of checking the configuration field by field, so that
 * the rules for what is shown live in one place.
 */
final class NotificationPolicy {

    /** What happens to a notification that a program or the app itself asked to show. */
    enum Route {
        /** The notification is dropped, since its channel is disabled or it has no content. */
        IGNORED,
        /** A card is shown over the terminal of the app. */
        CARD,
        /** A system notification is posted, since the app is not visible to show a card in. */
        BACKGROUND
    }

    private NotificationPolicy() {
    }

    /**
     * @return The route of {@code notification}, which is {@link Route#BACKGROUND} for the ones that arrive
     *         while the app is not visible, since a card of an app that the user cannot see tells nothing.
     */
    @NonNull
    static Route route(@NonNull NotificationConfig config, @NonNull TerminalNotification notification, boolean appVisible) {
        if (notification.isEmpty()) return Route.IGNORED;
        if (!isChannelEnabled(config, notification)) return Route.IGNORED;
        return !appVisible && config.backgroundEnabled ? Route.BACKGROUND : Route.CARD;
    }

    /** @return Whether the cards of the source of {@code notification} are enabled by the configuration. */
    static boolean isChannelEnabled(@NonNull NotificationConfig config, @NonNull TerminalNotification notification) {
        if (!config.enabled) return false;

        switch (notification.getSource()) {
            case EXIT_CODE:
                return config.exitCodeEnabled;
            case OSC:
                return config.oscEnabled && config.isOscAppAllowed(notification.getAppName());
            case APP_EVENT:
            default:
                return config.eventsEnabled;
        }
    }

    /** @return Whether the card of the app event {@code eventId} is enabled by the configuration. */
    static boolean isEventEnabled(@NonNull NotificationConfig config, @NonNull String eventId) {
        return config.enabled && config.eventsEnabled && config.isEventEnabled(eventId);
    }

    /**
     * @return Whether the occasion of a notification applies to the current state of the app, or
     *         {@code false} if it must not be shown now.
     *
     * The cards are only shown in the window of the app that the program runs in, so `unfocused` and
     * `invisible` are honored by hiding the notification when the window is focused or visible, which is
     * the opposite of what they request.
     */
    static boolean isOccasionAllowed(@NonNull TerminalNotification.Occasion occasion,
                                     boolean windowFocused, boolean appVisible) {
        switch (occasion) {
            case UNFOCUSED:
                return !windowFocused;
            case INVISIBLE:
                return !appVisible;
            case ALWAYS:
            default:
                return true;
        }
    }

    /**
     * @return The details line of the notification of a command that exited with {@code exitStatus}, which
     *         adds the exit status to the working directory of {@code meta}.
     *
     * The texts are passed in instead of being read from the resources, so that this can be tested without
     * a context.
     */
    @Nullable
    static String buildExitStatusMeta(@Nullable String meta, int exitStatus,
                                     @NonNull String separator, @NonNull String exitStatusText) {
        if (exitStatus == 0) return meta;

        if (meta == null || meta.trim().isEmpty()) return exitStatusText;
        return meta + " " + separator + " " + exitStatusText;
    }
}
