package com.termux.app.fragments.settings.termux;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.shared.logger.Logger;

/**
 * Lets the user reset a setting by long pressing it, since the settings screen has no other way to go back
 * to the default value of a property without knowing it and editing the `termux.properties` file.
 *
 * A property is reset by commenting it out in the file, since a property that is not set makes the app use
 * its default value, so no defaults have to be duplicated in the settings screen. The preference screen of
 * androidx.preference caches the values that it reads, so the activity is recreated to show the default
 * values again.
 */
final class PreferenceResetUtils {

    private static final String LOG_TAG = "PreferenceResetUtils";

    private PreferenceResetUtils() {
    }

    /**
     * Install the long press listener on the entries of the screen of {@code fragment} that are stored in a
     * property or in the shared preferences, which must be called after the screen was created from its
     * resource.
     *
     * androidx.preference has no long click listener on a preference, so the listener is installed on the
     * list view of the fragment, which only exists once its view was created.
     */
    static void installResetOnLongClick(@NonNull PreferenceFragmentCompat fragment) {
        if (fragment.getPreferenceScreen() == null) return;

        // `getListView()` returns the recycler view that androidx.preference uses to show the preferences.
        RecyclerView recyclerView = (RecyclerView) fragment.getListView();
        if (recyclerView != null) {
            install(fragment, recyclerView);
            return;
        }

        // The view of the fragment is created after its preferences, so wait for it once.
        fragment.getViewLifecycleOwnerLiveData().observe(fragment, owner -> {
            RecyclerView createdRecyclerView = (RecyclerView) fragment.getListView();
            if (createdRecyclerView != null) install(fragment, createdRecyclerView);
        });
    }

    private static void install(@NonNull PreferenceFragmentCompat fragment, @NonNull RecyclerView recyclerView) {
        GestureDetector gestureDetector = new GestureDetector(recyclerView.getContext(),
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public void onLongPress(MotionEvent event) {
                    Preference preference = getPreferenceAt(recyclerView, event.getX(), event.getY());
                    if (!canReset(preference)) return;

                    confirmReset(fragment, preference);
                }
            });

        // The listener only observes the touches, so that a normal tap still clicks the preference.
        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView view, @NonNull MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return false;
            }
        });
    }

    /**
     * @return The preference that is shown at the location of {@code event}, or {@code null} if there is
     *         none, like when the touch is on the padding of the list.
     */
    // `PreferenceGroupAdapter` is only restricted by convention (`@RestrictTo`), it is public at runtime,
    // and it is the only way to map a row of the list to the preference that it shows.
    @SuppressLint("RestrictedApi")
    @Nullable
    private static Preference getPreferenceAt(@NonNull RecyclerView recyclerView, float x, float y) {
        View child = recyclerView.findChildViewUnder(x, y);
        if (child == null) return null;

        int position = recyclerView.getChildAdapterPosition(child);
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (position == RecyclerView.NO_POSITION || !(adapter instanceof PreferenceGroupAdapter)) return null;

        return ((PreferenceGroupAdapter) adapter).getItem(position);
    }

    /** @return Whether the value of {@code preference} is stored somewhere that can be reset. */
    static boolean canReset(@Nullable Preference preference) {
        if (preference == null) return false;
        // The header of a category and the entries that only open a page or act on a click, like the
        // "about" entries, are not stored, so there is nothing to reset for them.
        if (preference instanceof PreferenceGroup) return false;
        if (!preference.isPersistent()) return false;

        String key = preference.getKey();
        return key != null && !key.isEmpty();
    }

    private static void confirmReset(@NonNull PreferenceFragmentCompat fragment, @NonNull Preference preference) {
        Context context = fragment.getContext();
        if (context == null) return;

        CharSequence title = preference.getTitle();
        new AlertDialog.Builder(context)
            .setTitle(title != null ? title : fragment.getString(R.string.action_reset_preference))
            .setMessage(R.string.msg_reset_preference_to_default)
            .setPositiveButton(R.string.action_reset_preference, (dialog, which) -> reset(fragment, preference))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /**
     * Reset the value of {@code preference}, which comments its property out in the `termux.properties` file
     * or removes it from the shared preferences.
     */
    static void reset(@NonNull PreferenceFragmentCompat fragment, @NonNull Preference preference) {
        String key = preference.getKey();
        if (key == null) return;

        Logger.logDebug(LOG_TAG, "Resetting the preference \"" + key + "\" to its default value");

        PreferenceDataStore dataStore = preference.getPreferenceDataStore();
        if (dataStore instanceof TermuxPropertyPreferenceDataStore) {
            ((TermuxPropertyPreferenceDataStore) dataStore).resetToDefault(key);
        } else {
            SharedPreferences sharedPreferences = preference.getSharedPreferences();
            if (sharedPreferences != null) sharedPreferences.edit().remove(key).apply();
        }

        // The screen already holds the values that were read when it was created, so it is recreated to
        // show the default values.
        Activity activity = fragment.getActivity();
        if (activity != null) activity.recreate();
    }
}
