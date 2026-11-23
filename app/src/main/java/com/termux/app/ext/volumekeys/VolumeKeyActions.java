package com.termux.app.ext.volumekeys;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

/**
 * 目的 — 执行音量键动作，把「`volume-*-action` 属性的取值」翻译为一次具体的宿主行为。
 *
 * <p>行为 — {@link #perform} 是一个纯派发：属性值与动作的对应关系在
 * {@link TermuxPropertyConstants} 中定义，本类只负责调用 {@link Host} 上的对应方法；调节音量这一项不需要宿主，
 * 因此就地通过 {@link AudioManager} 完成。</p>
 *
 * <p>约束 — 不得缓存宿主对象之外的任何状态；动作是否适用（例如当前是否选中文本）由宿主方法自行判断并安全返回。
 * {@code default} 与 {@code none} 两个取值的语义差异由调用方处理，本类对它们不做任何事。</p>
 */
public final class VolumeKeyActions {

    private static final String LOG_TAG = "VolumeKeyActions";

    /** The host behaviours that a volume key action may trigger. */
    public interface Host {
        void toggleSoftKeyboard();

        void toggleExtraKeys();

        void scrollToPreviousCommand();

        void scrollToNextCommand();

        void switchToPreviousSession();

        void switchToNextSession();

        void addNewSession();

        void paste();

        /** Send the key sequence that the `volume-up-send-keys` or `volume-down-send-keys` property defines. */
        void sendKeySequence(boolean isVolumeUp);

        /** Send a hardware key, which the terminal encodes for the current session. */
        void sendHardwareKey(int keyCode);
    }

    @NonNull
    private final Context mContext;
    @NonNull
    private final Host mHost;

    public VolumeKeyActions(@NonNull Context context, @NonNull Host host) {
        mContext = context;
        mHost = host;
    }

    /**
     * Perform the action of one volume key.
     *
     * @param action     The value of the `volume-*-action` property.
     * @param isVolumeUp Whether the key is the volume up key, which decides the direction of the actions.
     * @return Whether the action was handled; an unknown value is logged and not handled.
     */
    public boolean perform(@Nullable String action, boolean isVolumeUp) {
        if (action == null) return false;

        switch (action) {
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_DEFAULT:
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_NONE:
                // The virtual modifier is applied by the caller while the key is held down.
                return false;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_VOLUME:
                AudioManager audio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if (audio != null)
                    audio.adjustSuggestedStreamVolume(
                        isVolumeUp ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                        AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                return true;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_KEYBOARD:
                mHost.toggleSoftKeyboard();
                return true;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_TOGGLE_EXTRA_KEYS:
                mHost.toggleExtraKeys();
                return true;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_PAGE_UP:
                mHost.sendHardwareKey(KeyEvent.KEYCODE_PAGE_UP);
                return true;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_PAGE_DOWN:
                mHost.sendHardwareKey(KeyEvent.KEYCODE_PAGE_DOWN);
                return true;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_PREV_COMMAND:
                mHost.scrollToPreviousCommand();
                return true;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_NEXT_COMMAND:
                mHost.scrollToNextCommand();
                return true;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_PREV_SESSION:
                mHost.switchToPreviousSession();
                return true;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_NEXT_SESSION:
                mHost.switchToNextSession();
                return true;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_NEW_SESSION:
                mHost.addNewSession();
                return true;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_PASTE:
                mHost.paste();
                return true;
            case TermuxPropertyConstants.IVALUE_VOLUME_KEY_ACTION_SEND_KEYS:
                mHost.sendKeySequence(isVolumeUp);
                return true;
            default:
                Logger.logError(LOG_TAG, "Ignoring unknown volume key action: \"" + action + "\"");
                return false;
        }
    }
}
