package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsSearchNavigation;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;

@Keep
public class AppearancePreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(AppearancePreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_appearance_preferences, rootKey);

        PreferenceResetUtils.installResetOnLongClick(this);

        SettingsSearchNavigation.applyPendingHighlight(this);
    }

}

/**
 * A {@link TermuxPropertyPreferenceDataStore} for the `termux.properties` file entries shown in
 * {@link AppearancePreferencesFragment}.
 */
class AppearancePreferencesDataStore extends TermuxPropertyPreferenceDataStore {

    /** The key of the seek bar preference that stores the terminal toolbar height as a percentage of
     * its default height, since a seek bar can only store integer values, while the
     * `terminal-toolbar-height` termux property is a float scale factor. */
    private static final String KEY_TERMINAL_TOOLBAR_HEIGHT = "terminal-toolbar-height";

    private static AppearancePreferencesDataStore mInstance;

    private AppearancePreferencesDataStore(Context context) {
        // The app theme, the fullscreen flags and the terminal toolbar height are only applied when
        // the TermuxActivity is created, so it must be recreated for the changes to take effect.
        super(context, true);
    }

    public static synchronized AppearancePreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new AppearancePreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    public void putInt(String key, int value) {
        if (KEY_TERMINAL_TOOLBAR_HEIGHT.equals(key)) {
            if (isLastReadValue(key, String.valueOf(value))) return;
            setTermuxPropertyValue(key, String.valueOf(value / 100f));
            return;
        }

        super.putInt(key, value);
    }

    @Override
    public int getInt(String key, int defValue) {
        String value = TermuxSharedProperties.getTermuxPropertyValue(getContext(), key);
        int intValue;
        if (DataUtils.isNullOrEmpty(value)) {
            // A SeekBarPreference may pass its own minimum as the default value, so fall back to the
            // default scale factor of the property instead of using `defValue`.
            intValue = KEY_TERMINAL_TOOLBAR_HEIGHT.equals(key) ? 100 : defValue;
        } else if (KEY_TERMINAL_TOOLBAR_HEIGHT.equals(key)) {
            intValue = Math.round(DataUtils.getFloatFromString(value, defValue / 100f) * 100);
        } else {
            intValue = DataUtils.getIntFromString(value, defValue);
        }

        setLastReadValue(key, String.valueOf(intValue));
        return intValue;
    }

}
