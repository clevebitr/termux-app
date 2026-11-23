package com.termux.app.terminal;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.view.InputDevice;

import androidx.annotation.NonNull;

/**
 * 目的 — 判断设备当前是否接有物理（硬件）键盘，供 `ime-input-type=hardware` 决定是否把终端以
 * {@code TYPE_NULL} 上报给输入法。
 *
 * <p>行为 — 以 {@link Configuration#keyboard} 为主要依据：不是 {@link Configuration#KEYBOARD_NOKEYS}
 * 即视为已连接；若配置未报告键盘，则枚举 {@link InputManager} 的输入设备，只要存在
 * {@link InputDevice#KEYBOARD_TYPE_ALPHABETIC} 的设备也算已连接。后一条覆盖蓝牙键盘刚连上、系统配置尚未
 * 更新的短暂窗口。</p>
 *
 * <p>约束 — 只在创建输入连接、处理配置变更与输入设备增删时调用；结果不缓存，调用方每次重新查询，
 * 避免键盘拔出后仍按旧状态上报。</p>
 */
public final class HardwareKeyboard {

    private HardwareKeyboard() {
    }

    /** @return Whether a hardware keyboard is available, as described by the class documentation. */
    public static boolean isAttached(@NonNull Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        if (isAttached(configuration.keyboard)) return true;

        InputManager inputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        if (inputManager == null) return false;

        int[] deviceIds;
        try {
            deviceIds = inputManager.getInputDeviceIds();
        } catch (RuntimeException e) {
            // The input service may be unavailable while the device is shutting down.
            return false;
        }
        if (deviceIds == null) return false;

        for (int deviceId : deviceIds) {
            InputDevice device = inputManager.getInputDevice(deviceId);
            if (device != null && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) return true;
        }
        return false;
    }

    /**
     * @param configurationKeyboard The value of {@link Configuration#keyboard}.
     * @return Whether that value reports a hardware keyboard, which is the part of the check that can be
     *         decided without a {@link Context}.
     */
    static boolean isAttached(int configurationKeyboard) {
        return configurationKeyboard != Configuration.KEYBOARD_NOKEYS;
    }
}
