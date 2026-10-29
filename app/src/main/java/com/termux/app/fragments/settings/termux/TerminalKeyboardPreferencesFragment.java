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

        setupVolumeKeyActionPreferences();
    }

    private void setupVolumeKeyActionPreferences() {
        setupVolumeKeyActionPreferences("volume-up-action", "volume-up-long-action", "volume-up-send-keys");
        setupVolumeKeyActionPreferences("volume-down-action", "volume-down-long-action", "volume-down-send-keys");

        Preference resetPreference = findPreference("reset-volume-key-actions");
        if (resetPreference != null) {
            resetPreference.setOnPreferenceClickListener(preference -> {
                resetVolumeKeyActions();
                return true;
            });
        }
    }

    /**
     * The key sequence of a volume key is only sent if the action of its single press or long press is
     * {@link TermuxPropertyConstants#IVALUE_VOLUME_KEY_ACTION_SEND_KEYS}, so its preference is only
     * shown if that is the case, otherwise the settings screen would be cluttered with two entries that
     * do not do anything for most users.
     */
    private void setupVolumeKeyActionPreferences(String actionKey, String longActionKey, String sendKeysKey) {
        ListPreference actionPreference = findPreference(actionKey);
        ListPreference longActionPreference = findPreference(longActionKey);
        EditTextPreference sendKeysPreference = findPreference(sendKeysKey);
        if (actionPreference == null || longActionPreference == null || sendKeysPreference == null) return;

        // The listener is called before the new value is persisted, so the current value of the
        // preference that was not changed is used for the other action.
        Preference.OnPreferenceChangeListener listener = (preference, newValue) -> {
            boolean isActionChanged = actionPreference.getKey().equals(preference.getKey());
            String actionValue = isActionChanged ? (String) newValue : actionPreference.getValue();
            String longActionValue = isActionChanged ? longActionPreference.getValue() : (String) newValue;

            sendKeysPreference.setVisible(isSendKeysAction(actionValue) || isSendKeysAction(longActionValue));
            return true;
        };

        actionPreference.setOnPreferenceChangeListener(listener);
        longActionPreference.setOnPreferenceChangeListener(listener);

        sendKeysPreference.setVisible(isSendKeysAction(actionPreference.getValue()) || isSendKeysAction(longActionPreference.getValue()));
    }

    private static boolean isSendKeysAction(String action) {
        return TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_SEND_KEYS.equals(action);
    }

    /** Comment out the volume key action properties, so that their default values are used again. */
    private void resetVolumeKeyActions() {
        Context context = getContext();
        if (context == null) return;

        TerminalKeyboardPreferencesDataStore.getInstance(context).resetVolumeKeyActions();

        Toast.makeText(context, R.string.termux_volume_key_actions_reset_done, Toast.LENGTH_SHORT).show();

        // Recreate the preferences, so that the restored default values are shown.
        setPreferencesFromResource(R.xml.termux_keyboard_preferences, mRootKey);
        setupVolumeKeyActionPreferences();
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

    /** Comment out the volume key action properties from the `termux.properties` file, so that the
     * default values defined by {@link TermuxPropertyConstants} are used again. */
    void resetVolumeKeyActions() {
        setTermuxPropertyValue(TermuxPropertyConstants.KEY_VOLUME_UP_ACTION, null);
        setTermuxPropertyValue(TermuxPropertyConstants.KEY_VOLUME_UP_LONG_ACTION, null);
        setTermuxPropertyValue(TermuxPropertyConstants.KEY_VOLUME_UP_SEND_KEYS, null);
        setTermuxPropertyValue(TermuxPropertyConstants.KEY_VOLUME_DOWN_ACTION, null);
        setTermuxPropertyValue(TermuxPropertyConstants.KEY_VOLUME_DOWN_LONG_ACTION, null);
        setTermuxPropertyValue(TermuxPropertyConstants.KEY_VOLUME_DOWN_SEND_KEYS, null);
    }

}
