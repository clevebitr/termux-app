package com.termux.app.fragments.settings;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.termux.shared.logger.Logger;

/**
 * Opens the page of an entry that the search of the settings found, and remembers which entry the user
 * selected, so that the page can highlight it once it is created.
 *
 * The page is opened by clicking the preference that opens it, which is the same path that the user takes, so
 * the navigation of androidx.preference does not have to be called by hand.
 */
public final class SettingsSearchNavigation {

    private static final String LOG_TAG = "SettingsSearchNavigation";

    /** How long the title of the entry that the search opened stays highlighted. */
    private static final long HIGHLIGHT_DURATION_MS = 1500;
    /** The color of the highlight of the title, which is a translucent amber that works in both themes. */
    private static final int HIGHLIGHT_COLOR = 0x66FFC107;

    /** The class name of the page whose entry has to be highlighted, or {@code null}. */
    @Nullable
    private static String sPendingFragmentClassName;
    /** The key of the entry that has to be highlighted, or {@code null}. */
    @Nullable
    private static String sPendingKey;

    private SettingsSearchNavigation() {
    }

    /**
     * Open the page of {@code entry} from the settings screen of {@code source}, and highlight the entry when
     * the page is created.
     */
    public static void open(@NonNull PreferenceFragmentCompat source, @NonNull SettingsSearchIndex.Entry entry) {
        sPendingFragmentClassName = entry.fragmentClassName;
        sPendingKey = entry.key;

        PreferenceScreen screen = source.getPreferenceScreen();
        Preference pagePreference = screen != null ? findPagePreference(screen, entry.fragmentClassName) : null;
        if (pagePreference == null) {
            Logger.logWarn(LOG_TAG, "The page \"" + entry.fragmentClassName + "\" is not on the settings screen");
            sPendingFragmentClassName = null;
            sPendingKey = null;
            return;
        }

        // The same hook that androidx.preference calls when the user clicks the entry, which opens the page
        // that the `fragment` attribute of the preference names.
        source.onPreferenceTreeClick(pagePreference);
    }

    /** @return The preference of {@code group} that opens the page of {@code fragmentClassName}, or {@code null}. */
    @Nullable
    private static Preference findPagePreference(@NonNull PreferenceGroup group, @NonNull String fragmentClassName) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            if (preference instanceof PreferenceGroup) {
                Preference nested = findPagePreference((PreferenceGroup) preference, fragmentClassName);
                if (nested != null) return nested;
                continue;
            }

            if (fragmentClassName.equals(preference.getFragment())) return preference;
        }

        return null;
    }

    /**
     * Highlight the entry that the search selected, if {@code fragment} is the page that it is on.
     *
     * The title of the entry is given a background color for a moment instead of the entry's view, since
     * androidx.preference does not expose the view of a preference and the rows are recycled. The entry is
     * also scrolled into view, since it may be at the end of a long page.
     */
    public static void applyPendingHighlight(@NonNull PreferenceFragmentCompat fragment) {
        final String key = sPendingKey;
        if (key == null || !fragment.getClass().getName().equals(sPendingFragmentClassName)) return;

        // The highlight is only applied once, so that the page is not highlighted again when it is reopened.
        sPendingKey = null;
        sPendingFragmentClassName = null;

        final Preference preference = fragment.findPreference(key);
        if (preference == null) return;

        // The entry is scrolled into view and highlighted with the view of the page, but the preferences are
        // created before that view exists, so it is deferred, which also prevents the crash that a null view
        // used to cause.
        runWhenViewCreated(fragment, () -> {
            fragment.scrollToPreference(preference);
            highlightTitle(fragment, preference);
        });
    }

    /** Run {@code action} with the view of {@code fragment}, which is created after its preferences. */
    private static void runWhenViewCreated(@NonNull PreferenceFragmentCompat fragment, @NonNull Runnable action) {
        View view = fragment.getView();
        if (view != null) {
            action.run();
            return;
        }

        fragment.getViewLifecycleOwnerLiveData().observe(fragment, owner -> {
            if (fragment.getView() != null) action.run();
        });
    }

    private static void highlightTitle(@NonNull PreferenceFragmentCompat fragment, @NonNull Preference preference) {
        final CharSequence title = preference.getTitle();
        if (title == null || title.length() == 0) return;

        SpannableString highlighted = new SpannableString(title);
        highlighted.setSpan(new BackgroundColorSpan(HIGHLIGHT_COLOR),
            0, highlighted.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        preference.setTitle(highlighted);

        View view = fragment.getView();
        if (view == null) return;

        view.postDelayed(() -> {
            // The fragment may have been closed in the meantime, in which case its preference does not matter.
            if (!fragment.isAdded()) return;
            preference.setTitle(title);
        }, HIGHLIGHT_DURATION_MS);
    }
}
