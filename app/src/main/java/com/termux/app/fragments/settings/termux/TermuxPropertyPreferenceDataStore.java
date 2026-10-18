package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import com.termux.app.TermuxActivity;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;

import java.util.HashMap;
import java.util.Map;

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
