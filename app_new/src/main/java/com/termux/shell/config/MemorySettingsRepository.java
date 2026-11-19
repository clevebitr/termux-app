package com.termux.shell.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 内存实现，用于骨架阶段与单元测试。
 *
 * <p>目的：在持久化实现（属性文件快照）落地之前，提供可注入的配置来源。
 * <p>约束：本实现不做持久化，进程退出后内容丢失；不得在生产路径中使用。
 */
public final class MemorySettingsRepository implements SettingsRepository {

    private final Map<String, Object> mValues;

    public MemorySettingsRepository() {
        this(Collections.emptyMap());
    }

    public MemorySettingsRepository(@NonNull Map<String, Object> initialValues) {
        mValues = new HashMap<>(initialValues);
    }

    @Override
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        Object value = mValues.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    @Override
    public int getInt(@NonNull String key, int defaultValue) {
        Object value = mValues.get(key);
        return value instanceof Integer ? (Integer) value : defaultValue;
    }

    @Nullable
    @Override
    public String getString(@NonNull String key, @Nullable String defaultValue) {
        Object value = mValues.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    @NonNull
    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(mValues.keySet());
    }

    public void putBoolean(@NonNull String key, boolean value) {
        mValues.put(key, value);
    }

    public void putInt(@NonNull String key, int value) {
        mValues.put(key, value);
    }

    public void putString(@NonNull String key, @NonNull String value) {
        mValues.put(key, value);
    }
}
