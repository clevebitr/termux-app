package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.PreferenceAccentUtils;
import com.termux.app.fragments.settings.SettingsSearchNavigation;

/**
 * The settings of the status bar, which is shown above or below the terminal and displays
 * information like the time, the battery level, the memory and cpu usage, the network traffic and
 * the ip and proxy of the device.
 *
 * The preferences write the same `status-bar-*` properties to the `termux.properties` file as the
 * user can set in the file itself, so both can be mixed freely. The changes are applied to the
 * running activity when the settings screen is closed.
 */
@Keep
public class StatusBarPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(StatusBarPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_status_bar_preferences, rootKey);

        PreferenceResetUtils.installResetOnLongClick(this);

        SettingsSearchNavigation.applyPendingHighlight(this);

        PreferenceAccentUtils.applyAccent(this);
    }

}

/**
 * A {@link TermuxPropertyPreferenceDataStore} for the `termux.properties` file entries shown in
 * {@link StatusBarPreferencesFragment}.
 *
 * The status bar is rebuilt whenever the properties are reloaded, so the activity does not have to
 * be recreated for the changes to take effect.
 */
class StatusBarPreferencesDataStore extends TermuxPropertyPreferenceDataStore {

    private static StatusBarPreferencesDataStore mInstance;

    private StatusBarPreferencesDataStore(Context context) {
        super(context, false);
    }

    public static synchronized StatusBarPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new StatusBarPreferencesDataStore(context);
        }
        return mInstance;
    }

}
