package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import com.termux.app.TermuxActivity;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link PreferenceDataStore} that persists the preference values in the `termux.properties` file
 * instead of shared preferences, since the preferences using it are termux properties that can also
 * be set by the user in the file itself, by Termux:Styling or by scripts.
 *
 * A change made by the user is applied to the running termux app immediately, without having to
 * restart the app. The preference key must be equal to the property key.
 */
abstract class TermuxPropertyPreferenceDataStore extends PreferenceDataStore {

    private final Context mContext;

    /**
     * Whether the {@link TermuxActivity} must be recreated for the changes to take effect, like for
     * properties that are only read when the activity is created, for example the `night-mode` and
     * `fullscreen` properties.
     */
    private final boolean mRecreateActivity;

    /**
     * The values last returned by the getters for each key. {@link androidx.preference.ListPreference},
     * {@link androidx.preference.SwitchPreferenceCompat} and {@link androidx.preference.SeekBarPreference}
     * persist their value again when they are initialized with the value they just read, which must
     * not be treated as a change made by the user, otherwise the properties file would be rewritten
     * and the {@link TermuxActivity} recreated every time the settings screen is opened.
     *
     * The comparison is made against the value read from the file instead of the value being put,
     * since preferences fall back to their default value if the property is not defined in the file.
     */
    private final Map<String, String> mLastReadValues = new HashMap<>();

    protected TermuxPropertyPreferenceDataStore(Context context, boolean recreateActivity) {
        mContext = context.getApplicationContext();
        mRecreateActivity = recreateActivity;
    }

    protected Context getContext() {
        return mContext;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (isLastReadValue(key, String.valueOf(value))) return;

        setTermuxPropertyValue(key, String.valueOf(value));
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        String value = TermuxSharedProperties.getTermuxPropertyValue(mContext, key);
        boolean booleanValue = DataUtils.isNullOrEmpty(value) ? defValue : Boolean.parseBoolean(value.trim());

        setLastReadValue(key, String.valueOf(booleanValue));
        return booleanValue;
    }

    @Override
    public void putString(String key, @Nullable String value) {
        if (isLastReadValue(key, value)) return;

        setTermuxPropertyValue(key, DataUtils.isNullOrEmpty(value) ? null : value);
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        String value = TermuxSharedProperties.getTermuxPropertyValue(mContext, key);
        String stringValue = !DataUtils.isNullOrEmpty(value) ? value : defValue;

        setLastReadValue(key, stringValue);
        return stringValue;
    }

    @Override
    public void putStringSet(String key, @Nullable Set<String> values) {
        // An empty set is written as an empty value instead of commenting the property out, since a
        // commented out property would make the configuration use its default value again, which may
        // select some items, like all of the notification events.
        String value = joinStringSet(values);
        if (isLastReadValue(key, value)) return;

        setTermuxPropertyValue(key, value);
    }

    @NonNull
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        // A property that exists but is empty means that the user selected nothing, while a property that
        // does not exist at all means that the default value of the preference is used.
        String value = TermuxSharedProperties.getTermuxPropertyValue(mContext, key);

        Set<String> values;
        if (value != null)
            values = splitStringSet(value);
        else if (defValues != null)
            values = defValues;
        else
            // Never return null, since MultiSelectListPreference.setValues() passes the value to
            // Set.addAll(), which crashes with a NullPointerException instead of treating it as empty.
            values = new LinkedHashSet<>();

        setLastReadValue(key, joinStringSet(values));
        return values;
    }

    @Override
    public void putInt(String key, int value) {
        if (isLastReadValue(key, String.valueOf(value))) return;

        setTermuxPropertyValue(key, String.valueOf(value));
    }

    @Override
    public int getInt(String key, int defValue) {
        String value = TermuxSharedProperties.getTermuxPropertyValue(mContext, key);
        int intValue = DataUtils.isNullOrEmpty(value) ? defValue : DataUtils.getIntFromString(value, defValue);

        setLastReadValue(key, String.valueOf(intValue));
        return intValue;
    }

    /**
     * @return The comma separated {@code values}, sorted so that the comparison with the value that was
     *         read is stable no matter the iteration order of the set, an empty string if the set is
     *         empty, or {@code null} if there is no set at all, which comments the property out.
     */
    @Nullable
    static String joinStringSet(@Nullable Set<String> values) {
        if (values == null) return null;
        if (values.isEmpty()) return "";

        List<String> sortedValues = new ArrayList<>(values);
        Collections.sort(sortedValues);

        StringBuilder joined = new StringBuilder();
        for (String value : sortedValues) {
            if (DataUtils.isNullOrEmpty(value)) continue;
            if (joined.length() > 0) joined.append(',');
            joined.append(value);
        }

        return joined.toString();
    }

    /** @return The values of the comma separated {@code value}. */
    @NonNull
    static Set<String> splitStringSet(@NonNull String value) {
        Set<String> values = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            item = item.trim();
            if (!item.isEmpty()) values.add(item);
        }
        return values;
    }

    /** Remember the value read for the {@code key}, so that a preference persisting it again can be
     * recognized as a no-op. */
    protected void setLastReadValue(String key, @Nullable String value) {
        if (key == null) return;

        mLastReadValues.put(key, value);
    }

    /** Returns whether the {@code value} is the same as the value last read for the {@code key}. */
    protected boolean isLastReadValue(String key, @Nullable String value) {
        if (key == null) return false;

        String lastReadValue = mLastReadValues.remove(key);
        if (lastReadValue == null) return value == null;

        return lastReadValue.equals(value);
    }

    /**
     * Set the value of the property in the properties file and apply the changes to the running
     * termux app, so that they take effect immediately, without having to restart the app.
     */
    protected void setTermuxPropertyValue(String key, @Nullable String value) {
        if (key == null) return;
        if (!TermuxSharedProperties.setTermuxPropertyValue(mContext, key, value)) return;

        TermuxAppSharedProperties properties = TermuxAppSharedProperties.getProperties();
        if (properties != null) properties.loadTermuxPropertiesFromDisk();

        TermuxActivity.updateTermuxActivityStyling(mContext, mRecreateActivity);
    }

}
