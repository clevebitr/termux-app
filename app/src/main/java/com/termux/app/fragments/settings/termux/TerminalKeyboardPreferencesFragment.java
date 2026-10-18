package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;

@Keep
public class TerminalKeyboardPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TerminalKeyboardPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_keyboard_preferences, rootKey);
    }

}

/**
 * A {@link PreferenceDataStore} that persists the preference values in the `termux.properties` file
 * instead of shared preferences, since the preferences shown in
 * {@link TerminalKeyboardPreferencesFragment} are termux properties that can also be set by the user
 * in the file itself, by Termux:Styling or by scripts.
 */
class TerminalKeyboardPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;

    private static TerminalKeyboardPreferencesDataStore mInstance;

    private TerminalKeyboardPreferencesDataStore(Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized TerminalKeyboardPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TerminalKeyboardPreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        setTermuxPropertyValue(key, String.valueOf(value));
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        String value = TermuxSharedProperties.getTermuxPropertyValue(mContext, key);
        if (DataUtils.isNullOrEmpty(value)) return defValue;

        return Boolean.parseBoolean(value.trim());
    }

    @Override
    public void putString(String key, @Nullable String value) {
        setTermuxPropertyValue(key, DataUtils.isNullOrEmpty(value) ? null : value);
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        String value = TermuxSharedProperties.getTermuxPropertyValue(mContext, key);
        return !DataUtils.isNullOrEmpty(value) ? value : defValue;
    }

    /**
     * Set the value of the property in the properties file and apply the changes to the running
     * termux app, so that they take effect immediately, without having to restart the app.
     */
    private void setTermuxPropertyValue(String key, @Nullable String value) {
        if (key == null) return;
        if (!TermuxSharedProperties.setTermuxPropertyValue(mContext, key, value)) return;

        TermuxAppSharedProperties properties = TermuxAppSharedProperties.getProperties();
        if (properties != null) properties.loadTermuxPropertiesFromDisk();

        // Make sure that the changes are applied to the running TermuxActivity, like the extra keys
        // view colors and the app theme.
        TermuxActivity.updateTermuxActivityStyling(mContext, false);
    }

}
