package com.termux.app.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalNotification;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shows the notifications in the system notification shade when the app is not visible, so that the
 * user is told about a failed command or a notification of a program while using another app.
 *
 * The notifications use their own channel, and only need the notification permission that the app
 * already has for its foreground service notifications, since the app targets api level 28, which is
 * before the notification permission became a runtime permission. The user can still turn the channel
 * off in the android settings, in which case nothing is posted.
 */
public final class BackgroundNotifier {

    private static final String LOG_TAG = "BackgroundNotifier";

    /** The id of the notification channel of the terminal notifications. */
    private static final String CHANNEL_ID = "termux-terminal-notifications";
    private static final String CHANNEL_NAME = "Terminal notifications";

    /** The ids of the notifications, which are only used to replace the previous notification. */
    private static final AtomicInteger NEXT_NOTIFICATION_ID = new AtomicInteger(1000);

    @NonNull
    private final Context mContext;
    private boolean mChannelCreated;

    public BackgroundNotifier(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * Post a system notification for {@code notification} if the user has not turned the notifications
     * of the app off in the android settings.
     */
    public void post(@NonNull TerminalNotification notification) {
        NotificationManager notificationManager =
            (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        // NotificationManagerCompat is used instead of NotificationManager, since the method that checks
        // whether the notifications are enabled requires api level 24 on the latter.
        if (!NotificationManagerCompat.from(mContext).areNotificationsEnabled()) {
            Logger.logVerbose(LOG_TAG, "Not posting a background notification, they are disabled for the app");
            return;
        }

        createChannel(notificationManager);

        CharSequence title = notification.getTitle() != null ? notification.getTitle()
            : notification.getBody() != null ? notification.getBody() : CHANNEL_NAME;
        String text = notification.getTitle() != null ? notification.getBody() : null;

        Intent intent = TermuxActivity.newInstance(mContext);
        // The immutable flag is only available on api level 23, and is not required before api level 31.
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, pendingIntentFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_terminal_notification)
            .setContentTitle(title)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (text != null && !text.isEmpty()) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
            builder.setContentText(text);
        }

        try {
            notificationManager.notify(NEXT_NOTIFICATION_ID.getAndIncrement(), builder.build());
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to post a background notification", e);
        }
    }

    /** Create the notification channel, which is only needed once per process. */
    private void createChannel(@NonNull NotificationManager notificationManager) {
        if (mChannelCreated || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            mChannelCreated = true;
            return;
        }

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Notifications of failed commands and of terminal programs");
        notificationManager.createNotificationChannel(channel);
        mChannelCreated = true;
    }
}
