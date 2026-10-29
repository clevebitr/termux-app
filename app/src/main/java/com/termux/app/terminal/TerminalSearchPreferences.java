package com.termux.app.terminal;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The preferences and the recent queries of the terminal search, which are kept in their own shared
 * preferences file so that the main preferences file does not grow with a list that is only used by the
 * search bar.
 *
 * The history is stored as a single string with the queries separated by newlines, newest first, since the
 * queries are typed in a one line text input. The list operations are pure static methods, so that they can
 * be tested without a context.
 */
public final class TerminalSearchPreferences {

    /** The maximum number of recent queries that are kept. */
    static final int MAX_HISTORY = 10;

    private static final String PREFERENCES_NAME = "termux-terminal-search";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_CASE_SENSITIVE = "case-sensitive";
    private static final String KEY_REGEX = "regex";

    /** The separator of the queries in the history, which cannot be typed in the search bar. */
    private static final String SEPARATOR = "\n";

    @NonNull
    private final SharedPreferences mPreferences;

    public TerminalSearchPreferences(@NonNull Context context) {
        mPreferences = context.getApplicationContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    /** @return Whether the search matches the case of the text, which is off by default. */
    public boolean isCaseSensitive() {
        return mPreferences.getBoolean(KEY_CASE_SENSITIVE, false);
    }

    public void setCaseSensitive(boolean caseSensitive) {
        mPreferences.edit().putBoolean(KEY_CASE_SENSITIVE, caseSensitive).apply();
    }

    /** @return Whether the query of the search is a regular expression, which is off by default. */
    public boolean isRegex() {
        return mPreferences.getBoolean(KEY_REGEX, false);
    }

    public void setRegex(boolean regex) {
        mPreferences.edit().putBoolean(KEY_REGEX, regex).apply();
    }

    /** @return The recent queries, newest first. */
    @NonNull
    public List<String> getHistory() {
        return splitHistory(mPreferences.getString(KEY_HISTORY, null));
    }

    /** Add {@code query} to the front of the history, so that it can be picked again from there. */
    public void addToHistory(@Nullable String query) {
        List<String> entries = addQuery(getHistory(), query);
        mPreferences.edit().putString(KEY_HISTORY, joinHistory(entries)).apply();
    }

    /**
     * @return The queries of {@code value}, newest first, which is the format that
     *         {@link #joinHistory(List)} writes.
     */
    @NonNull
    static List<String> splitHistory(@Nullable String value) {
        if (value == null || value.isEmpty()) return new ArrayList<>();

        List<String> entries = new ArrayList<>();
        for (String entry : value.split(SEPARATOR, -1)) {
            if (entry.isEmpty() || entries.contains(entry)) continue;
            entries.add(entry);
        }
        return entries;
    }

    /** @return The {@code entries} as they are stored, newest first. */
    @NonNull
    static String joinHistory(@NonNull List<String> entries) {
        // A StringBuilder is used instead of String.join(), which requires api level 26.
        StringBuilder builder = new StringBuilder();
        for (String entry : entries) {
            if (builder.length() > 0) builder.append(SEPARATOR);
            builder.append(entry);
        }
        return builder.toString();
    }

    /**
     * @return The {@code entries} with {@code query} moved to the front, without a duplicate of it that
     *         only differs in case, and capped at {@link #MAX_HISTORY} entries.
     *
     * The query is trimmed and its line breaks are replaced by spaces, since the history is stored as one
     * query per line.
     */
    @NonNull
    static List<String> addQuery(@NonNull List<String> entries, @Nullable String query) {
        List<String> updated = new ArrayList<>();
        if (query != null) {
            String trimmed = query.replace('\n', ' ').replace('\r', ' ').trim();
            if (!trimmed.isEmpty()) updated.add(trimmed);
        }

        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) continue;
            if (!updated.isEmpty() && updated.get(0).equalsIgnoreCase(entry)) continue;
            if (updated.contains(entry)) continue;
            updated.add(entry);
        }

        if (updated.size() > MAX_HISTORY) return new ArrayList<>(updated.subList(0, MAX_HISTORY));
        return updated;
    }

}
