package com.termux.app.fragments.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.fragments.settings.termux.AppearancePreferencesFragment;
import com.termux.app.fragments.settings.termux.BackupPreferencesFragment;
import com.termux.app.fragments.settings.termux.DebuggingPreferencesFragment;
import com.termux.app.fragments.settings.termux.NotificationPreferencesFragment;
import com.termux.app.fragments.settings.termux.StatusBarPreferencesFragment;
import com.termux.app.fragments.settings.termux.TerminalIOPreferencesFragment;
import com.termux.app.fragments.settings.termux.TerminalKeyboardPreferencesFragment;
import com.termux.app.fragments.settings.termux.TerminalMenuPreferencesFragment;
import com.termux.app.fragments.settings.termux.TerminalViewPreferencesFragment;
import com.termux.shared.logger.Logger;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The index of the entries of the settings of the Termux app, which the search of the settings screen
 * searches through.
 *
 * The entries are read from the XML resources of the pages instead of being listed by hand, so that a
 * setting that is added to a page is found without touching the index. The filtering is a pure function, so
 * that it can be tested without a context.
 */
public final class SettingsSearchIndex {

    private static final String LOG_TAG = "SettingsSearchIndex";

    private static final String ATTRIBUTE_NAMESPACE_RES_AUTO = "http://schemas.android.com/apk/res-auto";
    private static final String ATTRIBUTE_NAMESPACE_ANDROID = "http://schemas.android.com/apk/res/android";

    /** A page of the settings of the Termux app, which is opened from the settings screen. */
    private static final class Page {
        final int titleRes;
        final int xmlRes;
        final String fragmentClassName;

        Page(int titleRes, int xmlRes, String fragmentClassName) {
            this.titleRes = titleRes;
            this.xmlRes = xmlRes;
            this.fragmentClassName = fragmentClassName;
        }
    }

    private static final Page[] PAGES = new Page[]{
        new Page(R.string.termux_appearance_preferences_title, R.xml.termux_appearance_preferences,
            AppearancePreferencesFragment.class.getName()),
        new Page(R.string.termux_keyboard_preferences_title, R.xml.termux_keyboard_preferences,
            TerminalKeyboardPreferencesFragment.class.getName()),
        new Page(R.string.termux_terminal_view_preferences_title, R.xml.termux_terminal_view_preferences,
            TerminalViewPreferencesFragment.class.getName()),
        new Page(R.string.termux_terminal_io_preferences_title, R.xml.termux_terminal_io_preferences,
            TerminalIOPreferencesFragment.class.getName()),
        new Page(R.string.termux_status_bar_preferences_title, R.xml.termux_status_bar_preferences,
            StatusBarPreferencesFragment.class.getName()),
        new Page(R.string.termux_terminal_menu_preferences_title, R.xml.termux_terminal_menu_preferences,
            TerminalMenuPreferencesFragment.class.getName()),
        new Page(R.string.termux_notification_preferences_title, R.xml.termux_notification_preferences,
            NotificationPreferencesFragment.class.getName()),
        new Page(R.string.termux_backup_preferences_title, R.xml.termux_backup_preferences,
            BackupPreferencesFragment.class.getName()),
        new Page(R.string.termux_debugging_preferences_title, R.xml.termux_debugging_preferences,
            DebuggingPreferencesFragment.class.getName()),
    };

    /** An entry of a page, like a list or a switch preference, that the search can find. */
    public static final class Entry {
        /** The title of the page that the entry is on, for the breadcrumb of the result. */
        @NonNull
        public final CharSequence pageTitle;
        /** The name of the fragment of the page, which is opened to show the entry. */
        @NonNull
        public final String fragmentClassName;
        /** The key of the preference, which is highlighted when the page is opened. */
        @NonNull
        public final String key;
        @NonNull
        public final CharSequence title;
        @Nullable
        public final CharSequence summary;

        Entry(@NonNull CharSequence pageTitle, @NonNull String fragmentClassName, @NonNull String key,
              @NonNull CharSequence title, @Nullable CharSequence summary) {
            this.pageTitle = pageTitle;
            this.fragmentClassName = fragmentClassName;
            this.key = key;
            this.title = title;
            this.summary = summary;
        }
    }

    private SettingsSearchIndex() {
    }

    /** @return The entries of all pages of the settings, in the order of the pages and of their entries. */
    @NonNull
    public static List<Entry> build(@NonNull Context context) {
        List<Entry> entries = new ArrayList<>();
        for (Page page : PAGES) addPageEntries(context, page, entries);
        return entries;
    }

    private static void addPageEntries(@NonNull Context context, @NonNull Page page, @NonNull List<Entry> entries) {
        CharSequence pageTitle;
        try {
            pageTitle = context.getString(page.titleRes);
        } catch (Resources.NotFoundException e) {
            return;
        }

        try (XmlResourceParser parser = context.getResources().getXml(page.xmlRes)) {
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    // The header of a category is not a setting, so it is not searchable on its own.
                    if (!"PreferenceCategory".equals(parser.getName()))
                        addEntry(context, page, pageTitle, parser, entries);
                }
                event = parser.next();
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read the entries of the settings page of \""
                + pageTitle + "\"", e);
        }
    }

    private static void addEntry(@NonNull Context context, @NonNull Page page, @NonNull CharSequence pageTitle,
                                 @NonNull XmlResourceParser parser, @NonNull List<Entry> entries) {
        String key = getAttribute(parser, "key");
        if (key == null || key.isEmpty()) return;

        CharSequence title = resolve(context, getAttribute(parser, "title"));
        if (title == null) return;

        entries.add(new Entry(pageTitle, page.fragmentClassName, key, title,
            resolve(context, getAttribute(parser, "summary"))));
    }

    /** @return The value of the attribute {@code name} of the current tag of {@code parser}, or {@code null}. */
    @Nullable
    private static String getAttribute(@NonNull XmlResourceParser parser, @NonNull String name) {
        String value = parser.getAttributeValue(ATTRIBUTE_NAMESPACE_RES_AUTO, name);
        if (value == null) value = parser.getAttributeValue(ATTRIBUTE_NAMESPACE_ANDROID, name);
        if (value == null) value = parser.getAttributeValue(null, name);
        return value;
    }

    /**
     * @return The text of {@code value}, which is resolved when it is a reference to a resource like
     *         {@code @string/...}, or {@code null} if it cannot be resolved.
     */
    // A reference that the resource compiler left as a name can only be resolved by looking it up, which is
    // the fallback for the case that the reference is not the id of the resource.
    @SuppressLint("DiscouragedApi")
    @Nullable
    private static CharSequence resolve(@NonNull Context context, @Nullable String value) {
        if (value == null) return null;
        if (!value.startsWith("@")) return value;

        // The XML parser returns a reference to a resource as the `@` followed by the id of the resource,
        // which is decimal in most builds, but other tools write it as a hexadecimal number or as the name of
        // the resource, so all of them are handled.
        String reference = value.substring(1);
        try {
            int id = reference.startsWith("0x")
                ? Integer.parseInt(reference.substring(2), 16) : Integer.parseInt(reference);
            return context.getText(id);
        } catch (Exception ignored) {
            // Fall through to the name of the resource.
        }

        int slashIndex = reference.indexOf('/');
        if (slashIndex > 0 && slashIndex + 1 < reference.length()) {
            String type = reference.substring(0, slashIndex);
            String name = reference.substring(slashIndex + 1);
            int id = context.getResources().getIdentifier(name, type, context.getPackageName());
            if (id != 0) {
                try {
                    return context.getText(id);
                } catch (Exception e) {
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * @return The entries that match {@code query} in their title, their summary or the title of their page,
     *         with the entries whose title matches first, since those are the ones that the user means.
     */
    @NonNull
    public static List<Entry> search(@NonNull List<Entry> entries, @Nullable String query) {
        String trimmedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (trimmedQuery.isEmpty()) return new ArrayList<>(entries);

        List<Entry> titleMatches = new ArrayList<>();
        List<Entry> otherMatches = new ArrayList<>();
        for (Entry entry : entries) {
            if (contains(entry.title, trimmedQuery)) {
                titleMatches.add(entry);
            } else if (contains(entry.summary, trimmedQuery) || contains(entry.pageTitle, trimmedQuery)) {
                otherMatches.add(entry);
            }
        }

        titleMatches.addAll(otherMatches);
        return titleMatches;
    }

    private static boolean contains(@Nullable CharSequence text, @NonNull String lowerCaseQuery) {
        return text != null && text.toString().toLowerCase(Locale.ROOT).contains(lowerCaseQuery);
    }
}
