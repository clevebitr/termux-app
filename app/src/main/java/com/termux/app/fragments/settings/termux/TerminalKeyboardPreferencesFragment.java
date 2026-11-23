package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.PreferenceAccentUtils;
import com.termux.app.fragments.settings.SettingsSearchNavigation;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

@Keep
public class TerminalKeyboardPreferencesFragment extends PreferenceFragmentCompat {

    /** The root key the preferences were created with, required to recreate them. */
    private String mRootKey;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        mRootKey = rootKey;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TerminalKeyboardPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_keyboard_preferences, rootKey);

        PreferenceResetUtils.installResetOnLongClick(this);

        SettingsSearchNavigation.applyPendingHighlight(this);

        PreferenceAccentUtils.applyAccent(this);
    }

}

/**
 * A {@link TermuxPropertyPreferenceDataStore} for the `termux.properties` file entries shown in
 * {@link TerminalKeyboardPreferencesFragment}, which are the input method, key behaviour and extra
 * keys colors related properties.
 */
class TerminalKeyboardPreferencesDataStore extends TermuxPropertyPreferenceDataStore {

    private static TerminalKeyboardPreferencesDataStore mInstance;

    private TerminalKeyboardPreferencesDataStore(Context context) {
        // The properties shown in this screen are read whenever they are needed, so the activity
        // does not have to be recreated for the changes to take effect.
        super(context, false);
    }

    public static synchronized TerminalKeyboardPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TerminalKeyboardPreferencesDataStore(context);
        }
        return mInstance;
    }


}
