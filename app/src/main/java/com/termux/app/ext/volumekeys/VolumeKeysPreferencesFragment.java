package com.termux.app.ext.volumekeys;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.ext.host.ExtensionHost;
import com.termux.app.fragments.settings.PreferenceAccentUtils;
import com.termux.app.fragments.settings.SettingsSearchNavigation;
import com.termux.app.fragments.settings.termux.PreferenceResetUtils;
import com.termux.app.fragments.settings.termux.TermuxPropertyPreferenceDataStore;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

/**
 * 目的 — 配置音量键行为：是否把音量键当作虚拟键，以及单按与长按分别执行什么动作。
 *
 * <p>行为 — 偏好项写入 `volume-*` 属性，与用户手改 `termux.properties` 等价；`volume-*-send-keys` 只在对应动作
 * 为「发送按键序列」时显示，避免大多数用户看到两个无效条目；复位入口把六个属性注释掉，回到默认值。</p>
 *
 * <p>约束 — 必须复用 {@link TermuxPropertyPreferenceDataStore} 以避免各页重复实现属性读写；页面进入前必须
 * 检查所属扩展是否启用，禁用时只显示停用说明。</p>
 */
@Keep
public class VolumeKeysPreferencesFragment extends PreferenceFragmentCompat {

    /** The root key the preferences were created with, required to recreate them after a reset. */
    private String mRootKey;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        // The page can be reached from a search result or a restored back stack even while the extension is
        // disabled, so it refuses to edit the properties instead of relying on the hidden entry alone.
        if (!ExtensionHost.get(context).isRunning(VolumeKeysExtension.ID)) {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
            Preference notice = new Preference(context);
            notice.setTitle(R.string.termux_extensions_page_disabled_title);
            notice.setSummary(R.string.termux_extensions_page_disabled_summary);
            notice.setSelectable(false);
            screen.addPreference(notice);
            setPreferenceScreen(screen);
            PreferenceAccentUtils.applyAccent(this);
            return;
        }

        mRootKey = rootKey;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(VolumeKeysPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_volume_keys_preferences, rootKey);

        PreferenceResetUtils.installResetOnLongClick(this);

        SettingsSearchNavigation.applyPendingHighlight(this);

        PreferenceAccentUtils.applyAccent(this);

        setupActionPreferences();
    }

    private void setupActionPreferences() {
        setupActionPreferences("volume-up-action", "volume-up-long-action", "volume-up-send-keys");
        setupActionPreferences("volume-down-action", "volume-down-long-action", "volume-down-send-keys");

        Preference resetPreference = findPreference("reset-volume-key-actions");
        if (resetPreference != null) {
            resetPreference.setOnPreferenceClickListener(preference -> {
                resetActions();
                return true;
            });
        }
    }

    /**
     * The key sequence of a volume key is only sent if the action of its single press or long press is
     * {@link TermuxPropertyConstants#IVALUE_VOLUME_KEY_ACTION_SEND_KEYS}, so its preference is only shown if that
     * is the case, otherwise the page would offer two entries that do nothing for most users.
     */
    private void setupActionPreferences(String actionKey, String longActionKey, String sendKeysKey) {
        ListPreference actionPreference = findPreference(actionKey);
        ListPreference longActionPreference = findPreference(longActionKey);
        EditTextPreference sendKeysPreference = findPreference(sendKeysKey);
        if (actionPreference == null || longActionPreference == null || sendKeysPreference == null) return;

        // The listener is called before the new value is persisted, so the value of the other preference is
        // still the current one.
        Preference.OnPreferenceChangeListener listener = (preference, newValue) -> {
            boolean isActionChanged = actionPreference.getKey().equals(preference.getKey());
            String actionValue = isActionChanged ? (String) newValue : actionPreference.getValue();
            String longActionValue = isActionChanged ? longActionPreference.getValue() : (String) newValue;

            sendKeysPreference.setVisible(isSendKeysAction(actionValue) || isSendKeysAction(longActionValue));
            return true;
        };

        actionPreference.setOnPreferenceChangeListener(listener);
        longActionPreference.setOnPreferenceChangeListener(listener);

        sendKeysPreference.setVisible(isSendKeysAction(actionPreference.getValue())
            || isSendKeysAction(longActionPreference.getValue()));
    }

    private static boolean isSendKeysAction(String action) {
        return TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_SEND_KEYS.equals(action);
    }

    /** Comment out the volume key action properties, so that their default values are used again. */
    private void resetActions() {
        Context context = getContext();
        if (context == null) return;

        VolumeKeysPreferencesDataStore.getInstance(context).resetActions();

        Toast.makeText(context, R.string.termux_volume_key_actions_reset_done, Toast.LENGTH_SHORT).show();

        // Recreate the preferences, so that the restored default values are shown.
        setPreferencesFromResource(R.xml.termux_volume_keys_preferences, mRootKey);
        setupActionPreferences();
    }
}

/**
 * A {@link TermuxPropertyPreferenceDataStore} for the `volume-*` properties, which are read whenever a key is
 * pressed, so the activity does not have to be recreated for a change to take effect.
 */
class VolumeKeysPreferencesDataStore extends TermuxPropertyPreferenceDataStore {

    private static VolumeKeysPreferencesDataStore mInstance;

    private VolumeKeysPreferencesDataStore(Context context) {
        super(context, false);
    }

    static synchronized VolumeKeysPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new VolumeKeysPreferencesDataStore(context);
        }
        return mInstance;
    }

    /** Comment out the action properties, so that the defaults of {@link TermuxPropertyConstants} are used. */
    void resetActions() {
        for (String key : VolumeKeysExtension.ownedProperties()) {
            setTermuxPropertyValue(key, null);
        }
    }
}
