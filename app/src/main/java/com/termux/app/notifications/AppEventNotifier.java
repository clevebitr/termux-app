package com.termux.app.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalNotification;
import com.termux.terminal.TerminalSession;

/**
 * Turns the events of the app into notification cards, like a session that exited or a bell that a
 * terminal program rang, so that the user is told about them in the same way as about a failed command.
 *
 * The cards of the events are disabled by default, since the app already shows a toast for a session that
 * exits, and since a bell is meant to be heard and not to be read. Each event can be enabled on its own
 * with the `notification-events` property.
 */
public final class AppEventNotifier {

    private static final String LOG_TAG = "AppEventNotifier";

    @NonNull
    private final TermuxActivity mActivity;
    @NonNull
    private final TerminalNotificationManager mManager;

    public AppEventNotifier(@NonNull TermuxActivity activity, @NonNull TerminalNotificationManager manager) {
        mActivity = activity;
        mManager = manager;
    }

    /**
     * Show a card for a session that finished.
     *
     * @param session The session that finished.
     * @param isPluginCommand Whether the session ran a command of a plugin, like of Termux:Tasker.
     * @return Whether a card was shown, so that the caller does not show its own toast for the event.
     */
    public boolean onSessionFinished(@NonNull TerminalSession session, boolean isPluginCommand) {
        if (isPluginCommand) {
            if (!isEventEnabled(NotificationConfig.EVENT_PLUGIN_RESULT)) return false;
            return showSessionNotification(session, TerminalNotification.Kind.INFO,
                mActivity.getString(R.string.notification_event_plugin_result_title), session.getExitStatus());
        }

        if (!isEventEnabled(NotificationConfig.EVENT_SESSION_EXIT)) return false;

        // A session that exits with a signal or a non-zero exit code is more interesting than one that was
        // closed normally, so its card uses the accent color of a warning.
        TerminalNotification.Kind kind = session.getExitStatus() == 0
            ? TerminalNotification.Kind.INFO : TerminalNotification.Kind.WARNING;
        return showSessionNotification(session, kind,
            mActivity.getString(R.string.notification_event_session_exit_title), session.getExitStatus());
    }

    /**
     * Show a card for the title of a session that changed.
     *
     * @return Whether a card was shown, so that the caller does not show its own toast for the event.
     */
    public boolean onTitleChanged(@NonNull TerminalSession session) {
        if (!isEventEnabled(NotificationConfig.EVENT_TITLE)) return false;

        TerminalNotification notification = new TerminalNotification.Builder(
            TerminalNotification.Kind.INFO, TerminalNotification.Source.APP_EVENT)
            .setTitle(mActivity.getString(R.string.notification_event_title_changed_title))
            .setBody(getSessionTitle(session).toString())
            .setMeta(session.getCwd())
            .build();

        return mManager.notify(notification);
    }

    /**
     * Show a card for a bell that a terminal program rang.
     *
     * @return Whether a card was shown.
     */
    public boolean onBell(@NonNull TerminalSession session) {
        if (!isEventEnabled(NotificationConfig.EVENT_BELL)) return false;

        TerminalNotification notification = new TerminalNotification.Builder(
            TerminalNotification.Kind.INFO, TerminalNotification.Source.APP_EVENT)
            .setTitle(mActivity.getString(R.string.notification_event_bell_title))
            .setBody(getSessionTitle(session).toString())
            .setMeta(session.getCwd())
            .build();

        return mManager.notify(notification);
    }

    /** @return Whether the card of the app event {@code eventId} is enabled by the configuration. */
    private boolean isEventEnabled(@NonNull String eventId) {
        NotificationConfig config = mManager.getConfig();
        if (!config.enabled || !config.eventsEnabled) return false;
        if (config.isEventEnabled(eventId)) return true;

        Logger.logVerbose(LOG_TAG, "Not showing a card for the \"" + eventId + "\" event, it is not enabled");
        return false;
    }

    /** Show a card for an event of {@code session}, with the exit status in its details line. */
    private boolean showSessionNotification(@NonNull TerminalSession session,
                                           @NonNull TerminalNotification.Kind kind,
                                           @NonNull CharSequence title, int exitStatus) {
        String meta = session.getCwd();
        if (exitStatus != 0) {
            StringBuilder builder = new StringBuilder();
            if (meta != null) builder.append(meta);
            if (builder.length() > 0)
                builder.append(' ').append(mActivity.getString(R.string.notification_meta_separator)).append(' ');
            builder.append(mActivity.getString(R.string.notification_event_exit_status, exitStatus));
            meta = builder.toString();
        }

        TerminalNotification notification = new TerminalNotification.Builder(kind, TerminalNotification.Source.APP_EVENT)
            .setTitle(title.toString())
            .setBody(getSessionTitle(session).toString())
            .setMeta(meta)
            .build();

        return mManager.notify(notification);
    }

    /** @return The name of the session, or its pid if it does not have a name. */
    @NonNull
    private CharSequence getSessionTitle(@NonNull TerminalSession session) {
        if (session.mSessionName != null && !session.mSessionName.trim().isEmpty()) return session.mSessionName;
        return mActivity.getString(R.string.notification_event_session_name, session.getPid());
    }
}
