package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;

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
