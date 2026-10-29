package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsSearchNavigation;

/**
 * The settings of the notification cards, which are shown in a corner of the terminal when a command
 * exits with a non-zero exit code, like the notifications of the tui notification plugins.
 *
 * The preferences write the same `notification-*` properties to the `termux.properties` file as the user
 * can set in the file itself, so both can be mixed freely. The changes are applied to the running
 * activity when the settings screen is closed.
 */
@Keep
public class NotificationPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(NotificationPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_notification_preferences, rootKey);

        PreferenceResetUtils.installResetOnLongClick(this);

        SettingsSearchNavigation.applyPendingHighlight(this);
    }

}

/**
 * A {@link TermuxPropertyPreferenceDataStore} for the `termux.properties` file entries shown in
 * {@link NotificationPreferencesFragment}.
 *
 * The notification configuration is reloaded whenever the properties are reloaded, so the activity does
 * not have to be recreated for the changes to take effect.
 */
class NotificationPreferencesDataStore extends TermuxPropertyPreferenceDataStore {

    private static NotificationPreferencesDataStore mInstance;

    private NotificationPreferencesDataStore(Context context) {
        super(context, false);
    }

    public static synchronized NotificationPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotificationPreferencesDataStore(context);
        }
        return mInstance;
    }

}
