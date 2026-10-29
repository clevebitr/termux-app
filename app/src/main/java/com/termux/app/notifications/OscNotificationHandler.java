package com.termux.app.notifications;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalNotification;
import com.termux.terminal.TerminalNotificationProtocol;
import com.termux.terminal.TerminalSession;

/**
 * Handles the notifications that terminal programs request with the `OSC 99`, `OSC 9` and `OSC 777`
 * escape codes, which are the generic notification channel of the terminal: any program can show a
 * notification card by printing an escape code, like `printf '\033]9;Build finished\007'`.
 *
 * The handler decides whether a notification is shown, by checking the `notification-osc-*` properties
 * and the occasion and the rate limit of the notification, and it answers the program when the user
 * interacts with the card, by writing the activation, button and close reports back to the session, as
 * the protocol requires.
 */
public final class OscNotificationHandler {

    private static final String LOG_TAG = "OscNotificationHandler";

    /** Provides the current monotonic time in milliseconds, so that the rate limit can be tested. */
    interface Clock {
        long uptimeMillis();
    }

    @NonNull
    private final TermuxActivity mActivity;
    @NonNull
    private final TerminalNotificationManager mManager;
    /** The source of the time of the rate limit. */
    @NonNull
    private final Clock mClock;
    /** The times at which a notification was shown for a session, for the rate limit. */
    @NonNull
    private final OscRateLimiter mRateLimiter = new OscRateLimiter();

    public OscNotificationHandler(@NonNull TermuxActivity activity, @NonNull TerminalNotificationManager manager) {
        this(activity, manager, SystemClock::uptimeMillis);
    }

    OscNotificationHandler(@NonNull TermuxActivity activity, @NonNull TerminalNotificationManager manager,
                          @NonNull Clock clock) {
        mActivity = activity;
        mManager = manager;
        mClock = clock;
    }

    /**
     * Show a card for a notification that a program requested, if the cards of programs are enabled and
     * the program is allowed to show them.
     *
     * @param session The session that the program runs in, which the reports are written back to.
     * @param notification The notification that the program requested.
     */
    public void onTerminalNotification(@NonNull TerminalSession session, @NonNull TerminalNotification notification) {
        NotificationConfig config = mManager.getConfig();

        if (!mManager.isChannelEnabled(notification)) {
            Logger.logVerbose(LOG_TAG, "Not showing an OSC notification, the channel or the application is not enabled: " + notification);
            return;
        }

        if (!isOccasionAllowed(notification)) {
            Logger.logVerbose(LOG_TAG, "Not showing an OSC notification, the occasion \"" + notification.getOccasion() + "\" does not apply: " + notification);
            return;
        }

        if (isRateLimited(session, config)) {
            Logger.logWarn(LOG_TAG, "Not showing an OSC notification, " + config.oscRateLimit + " per second is the limit: " + notification);
            return;
        }

        TerminalNotificationManager.NotificationActions actions = new TerminalNotificationManager.NotificationActions() {
            @Override
            public void onNotificationClicked(@NonNull TerminalNotification notification) {
                // The protocol has no way to jump somewhere, so a click only reports the activation and
                // closes the card.
                if (notification.shouldReportActivation())
                    writeReport(session, TerminalNotificationProtocol.buildActivationReport(notification.getId()));
            }

            @Override
            public void onNotificationButtonClicked(@NonNull TerminalNotification notification, int buttonIndex) {
                if (notification.shouldReportActivation())
                    writeReport(session, TerminalNotificationProtocol.buildButtonReport(notification.getId(), buttonIndex + 1));
            }

            @Override
            public void onNotificationClosed(@NonNull TerminalNotification notification, boolean expired) {
                if (notification.shouldReportClose())
                    writeReport(session, TerminalNotificationProtocol.buildCloseReport(notification.getId()));
            }
        };

        mManager.notify(notification, actions);
    }

    /**
     * Close the card of the notification with {@code id}, like when a program requested it with
     * `OSC 99 ; i=<id> : p=close ;`.
     */
    public void onOsc99Close(@Nullable String id) {
        if (id == null) return;
        mManager.closeNotification(id);
    }

    /**
     * Answer the `OSC 99 ; i=<id> : p=alive ;` query of a program with the ids of the notifications whose
     * cards are still shown.
     */
    public void onOsc99AliveQuery(@NonNull TerminalSession session, @Nullable String id) {
        writeReport(session, TerminalNotificationProtocol.buildAliveResponse(id, mManager.getShownNotificationIds()));
    }

    /**
     * @return Whether the occasion of {@code notification} applies to the current state of the app.
     *
     * The cards are only shown in the window of the app that the program runs in, so `unfocused` and
     * `invisible` are honored by hiding the notification when the window is focused or visible, which is
     * the opposite of what they request.
     */
    private boolean isOccasionAllowed(@NonNull TerminalNotification notification) {
        return NotificationPolicy.isOccasionAllowed(notification.getOccasion(),
            mActivity.hasWindowFocus(), mActivity.isVisible());
    }

    /**
     * @return Whether the rate limit of the session is reached, in which case the notification is
     *         dropped instead of being shown.
     */
    private boolean isRateLimited(@NonNull TerminalSession session, @NonNull NotificationConfig config) {
        return mRateLimiter.isRateLimited(session, config.oscRateLimit, mClock.uptimeMillis());
    }

    /** Write an escape code back to the program that requested a notification, if there is one. */
    private static void writeReport(@NonNull TerminalSession session, @Nullable String report) {
        if (report == null) return;
        session.write(report);
    }
}
