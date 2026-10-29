package com.termux.app.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The ids of the notifications that were posted in the system notification shade, which are remembered
 * so that an `OSC 99 ; i=<id> : p=alive ;` query of a program also reports them, since the shade does
 * not tell the app when the user dismisses a notification.
 *
 * Only the last {@link #MAX_IDS} ids are kept, since the oldest ones are certainly gone by the time the
 * list is full.
 */
final class BackgroundNotificationRegistry {

    /** The number of notification ids that are remembered. */
    static final int MAX_IDS = 32;

    @NonNull
    private final Deque<String> mIds = new ArrayDeque<>();

    /** Remember the id of a notification that was posted in the system notification shade. */
    void remember(@NonNull String id) {
        mIds.remove(id);
        mIds.addLast(id);
        while (mIds.size() > MAX_IDS) mIds.removeFirst();
    }

    /** Forget the id of a notification that was closed, like by an `OSC 99 ; p=close` request. */
    void forget(@Nullable String id) {
        if (id != null) mIds.remove(id);
    }

    /** @return The ids of the notifications that were posted, oldest first. */
    @NonNull
    List<String> getIds() {
        return new ArrayList<>(mIds);
    }

    /**
     * Add the ids that are not in {@code ids} yet to it, in the order that they were posted in.
     *
     * @param ids The ids to add to, which may already contain the ids of cards of the overlay.
     */
    void addMissingIdsTo(@NonNull List<String> ids) {
        for (String id : mIds) {
            if (!ids.contains(id)) ids.add(id);
        }
    }
}
