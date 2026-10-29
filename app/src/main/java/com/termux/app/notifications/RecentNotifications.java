package com.termux.app.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalNotification;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The notifications that were shown recently, so that the user can look at them again after their
 * cards have disappeared, like when a command failed while the terminal was showing something else.
 *
 * The notifications are only kept in memory and are lost when the app is closed, since they can contain
 * the output of commands, which should not be written to the storage without the user asking for it.
 */
public final class RecentNotifications {

    /** A notification that was shown, with the action that is run when it is clicked in the list. */
    public static final class Entry {
        @NonNull
        public final TerminalNotification notification;
        /** The time at which the notification was shown, from {@link System#currentTimeMillis()}. */
        public final long timestampMs;
        /** The action that jumps to the command of the notification, or {@code null} if it has none. */
        @Nullable
        public final Runnable onOpen;

        Entry(@NonNull TerminalNotification notification, long timestampMs, @Nullable Runnable onOpen) {
            this.notification = notification;
            this.timestampMs = timestampMs;
            this.onOpen = onOpen;
        }
    }

    private final Deque<Entry> mEntries = new ArrayDeque<>();
    private int mCapacity;

    public RecentNotifications(int capacity) {
        mCapacity = Math.max(0, capacity);
    }

    /** Set the maximum number of notifications that are kept, dropping the oldest ones if needed. */
    public void setCapacity(int capacity) {
        mCapacity = Math.max(0, capacity);
        trim();
    }

    /** Add the notification that was just shown, dropping the oldest one if the list is full. */
    public void add(@NonNull TerminalNotification notification, @Nullable Runnable onOpen) {
        if (mCapacity == 0) return;

        mEntries.addFirst(new Entry(notification, System.currentTimeMillis(), onOpen));
        trim();
    }

    /** @return The notifications that were shown, newest first. */
    @NonNull
    public List<Entry> getEntries() {
        return new ArrayList<>(mEntries);
    }

    /** @return Whether no notification was shown since the list was created or cleared. */
    public boolean isEmpty() {
        return mEntries.isEmpty();
    }

    /** Remove every notification from the list. */
    public void clear() {
        mEntries.clear();
    }

    /** Drop the oldest notifications until the list fits its capacity. */
    private void trim() {
        while (mEntries.size() > mCapacity) mEntries.removeLast();
    }
}
