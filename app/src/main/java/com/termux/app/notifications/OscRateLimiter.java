package com.termux.app.notifications;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Limits how many `OSC` notifications a single terminal session may show per second, so that a program
 * that spams notifications cannot block the terminal, since a card has to be laid out for each of them.
 *
 * The times of the notifications that were shown are kept per session, in a sliding window, so that a
 * session that shows its three notifications gets a new one as soon as the oldest one leaves the window.
 * The clock is passed in instead of being read from the system, so that the window can be tested.
 *
 * The sessions are used as keys, which is why the map is weak: a closed session drops out of the limit
 * as soon as nothing else refers to it. {@code Object} keys are used so that the class can be tested
 * with plain objects.
 */
final class OscRateLimiter {

    /** The length of the window in milliseconds. */
    static final long WINDOW_MS = 1000;

    @NonNull
    private final Map<Object, ArrayDeque<Long>> mTimestamps = new WeakHashMap<>();

    /**
     * Record a notification of {@code sessionKey} if it is not over the limit.
     *
     * @param sessionKey The key of the session that the notification belongs to.
     * @param limit The maximum number of notifications per window, or 0 to never limit.
     * @param now The current time in milliseconds, which must be monotonic, like `SystemClock.uptimeMillis()`.
     * @return Whether the notification is over the limit, in which case it must be dropped and no time is
     *         recorded for it, so that a program cannot keep itself out of the limit by spamming.
     */
    boolean isRateLimited(@NonNull Object sessionKey, int limit, long now) {
        if (limit <= 0) return false;

        ArrayDeque<Long> timestamps = mTimestamps.get(sessionKey);
        if (timestamps == null) {
            timestamps = new ArrayDeque<>();
            mTimestamps.put(sessionKey, timestamps);
        }

        // Drop the notifications that left the window, so that the size of the deque is the number of
        // notifications that were shown in the last second.
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= WINDOW_MS)
            timestamps.pollFirst();

        if (timestamps.size() >= limit) return true;

        timestamps.addLast(now);
        return false;
    }
}
