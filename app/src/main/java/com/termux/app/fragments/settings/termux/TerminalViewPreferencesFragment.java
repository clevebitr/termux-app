package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

@Keep
public class TerminalViewPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TerminalViewPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_terminal_view_preferences, rootKey);
    }

}

/**
 * A {@link TermuxPropertyPreferenceDataStore} for the `termux.properties` file entries shown in
 * {@link TerminalViewPreferencesFragment}.
 *
 * The terminal margin adjustment preference is stored in shared preferences instead of the properties
 * file, since it is specific to the app and not applicable to terminal programs.
 */
class TerminalViewPreferencesDataStore extends TermuxPropertyPreferenceDataStore {

    /** The key of the preference that is stored in shared preferences instead of the properties file. */
    private static final String KEY_TERMINAL_MARGIN_ADJUSTMENT = "terminal_margin_adjustment";

    private final TermuxAppSharedPreferences mPreferences;

    private static TerminalViewPreferencesDataStore mInstance;

    private TerminalViewPreferencesDataStore(Context context) {
        // The properties shown in this screen are read whenever they are needed, so the activity does
        // not have to be recreated for the changes to take effect.
        super(context, false);
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized TerminalViewPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TerminalViewPreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (KEY_TERMINAL_MARGIN_ADJUSTMENT.equals(key)) {
            if (mPreferences == null) return;
            mPreferences.setTerminalMarginAdjustment(value);

            // The margin adjustment is applied when the activity is started, so the activity does not
            // have to be recreated or the styling reloaded for the change to take effect.
            return;
        }

        super.putBoolean(key, value);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (KEY_TERMINAL_MARGIN_ADJUSTMENT.equals(key)) {
            if (mPreferences == null) return defValue;
            return mPreferences.isTerminalMarginAdjustmentEnabled();
        }

        return super.getBoolean(key, defValue);
    }

}
