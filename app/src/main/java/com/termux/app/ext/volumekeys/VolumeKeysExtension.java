package com.termux.app.ext.volumekeys;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.ext.api.ExtensionSettingsPage;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 目的 — 把「音量键动作」作为可启用的扩展提供：音量键未按字面音量键处理时，单按与长按执行用户配置的动作，
 * 例如发送 Page Up、切换会话、粘贴或发送自定义按键序列。
 *
 * <p>行为 — 扩展本身不持有视图或定时器，只提供两件事：设置页归属（{@link #settingsPages()}）与动作执行
 * （{@link #perform}）。音量键的状态机（虚拟 CTRL/FN 修饰键、长按判定）留在终端视图客户端，因为它参与
 * `readControlKey()`/`readFnKey()` 的判断，属于输入层而不是功能层。</p>
 *
 * <p>约束 — 禁用后宿主不再把音量键交给本扩展，音量键回到系统的字面音量行为；{@link #perform} 每次调用都
 * 需要宿主传入 {@link VolumeKeyActions.Host}，扩展不得保存宿主视图或客户端引用。</p>
 */
public final class VolumeKeysExtension implements Extension {

    /** The stable id of the extension. */
    public static final String ID = "volume-keys";

    /** The preference key of the settings entry that this extension owns. */
    public static final String PREFERENCE_KEY = "volume-key-actions";

    @NonNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public int titleRes() {
        return R.string.termux_volume_keys_preferences_title;
    }

    @Override
    public int summaryRes() {
        return R.string.termux_volume_keys_preferences_summary;
    }

    @NonNull
    @Override
    public List<String> preferenceKeys() {
        return Collections.singletonList(PREFERENCE_KEY);
    }

    @NonNull
    @Override
    public List<ExtensionSettingsPage> settingsPages() {
        return Collections.singletonList(new ExtensionSettingsPage(
            R.string.termux_volume_keys_preferences_title, R.xml.termux_volume_keys_preferences,
            VolumeKeysPreferencesFragment.class.getName()));
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public void onEnable(@NonNull ExtensionContext context) {
        context.logInfo("Volume key actions enabled");
    }

    @Override
    public void onDisable() {
        // Nothing to release: the actions are performed by the view client, which stops asking for them.
    }

    /**
     * Perform the action of one volume key.
     *
     * @param context    The context of the host, used for the stream volume of the volume action.
     * @param host       The behaviours that the action may trigger.
     * @param action     The value of the `volume-*-action` property.
     * @param isVolumeUp Whether the key is the volume up key.
     * @return Whether the action was handled.
     */
    public boolean perform(@NonNull android.content.Context context,
                           @NonNull VolumeKeyActions.Host host,
                           @Nullable String action, boolean isVolumeUp) {
        return new VolumeKeyActions(context, host).perform(action, isVolumeUp);
    }

    /** @return The properties that this extension owns, for the settings page of the extensions screen. */
    @NonNull
    public static List<String> ownedProperties() {
        return Collections.unmodifiableList(Arrays.asList(
            TermuxPropertyConstants.KEY_VOLUME_UP_ACTION,
            TermuxPropertyConstants.KEY_VOLUME_UP_LONG_ACTION,
            TermuxPropertyConstants.KEY_VOLUME_UP_SEND_KEYS,
            TermuxPropertyConstants.KEY_VOLUME_DOWN_ACTION,
            TermuxPropertyConstants.KEY_VOLUME_DOWN_LONG_ACTION,
            TermuxPropertyConstants.KEY_VOLUME_DOWN_SEND_KEYS));
    }
}
