package com.termux.shell.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/**
 * 外壳配置访问契约。
 *
 * <p>目的：统一配置读取入口。旧模块同时存在 {@code termux.properties} 文件、{@code PreferenceDataStore}
 * 与 {@code SharedPreferences} 三条通路，读写时机不同导致默认值回写等问题（详见 {@code DECISIONS.md}）。
 * 新外壳只保留本契约，实现层负责快照与持久化。
 *
 * <p>约束：实现必须保证读取为内存操作，不得在 UI 线程执行磁盘读写。
 */
public interface SettingsRepository {

    boolean getBoolean(@NonNull String key, boolean defaultValue);

    int getInt(@NonNull String key, int defaultValue);

    @Nullable
    String getString(@NonNull String key, @Nullable String defaultValue);

    /**
     * 返回当前快照中出现过的全部键名，用于设置页枚举与调试输出。
     */
    @NonNull
    Set<String> keys();
}
