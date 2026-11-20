package com.termux.shell.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 基于属性文件的配置实现。
 *
 * <p>目的：以外壳自己的属性文件作为唯一配置来源，替代旧模块中属性文件、{@code PreferenceDataStore}
 * 与 {@code SharedPreferences} 三条通路并存的结构。
 *
 * <p>行为：
 * <ul>
 *     <li>构造时同步读取一次文件形成内存快照，之后全部读取操作只访问快照；</li>
 *     <li>文件不存在时写入带说明的默认模板，随后按默认值运行；</li>
 *     <li>读取时按键定义归一化取值，非法取值被忽略并回退到调用方给出的默认值，键名记入非法列表；</li>
 *     <li>文件中的未知键保留在快照中，便于诊断，但不参与界面构建。</li>
 * </ul>
 *
 * <p>约束：{@link #reload()} 会访问磁盘，必须由调用方在后台线程或初始化阶段调用；其余方法可在主线程调用。
 */
public final class PropertiesSettingsRepository implements SettingsRepository {

    private final File mFile;
    private volatile Map<String, String> mValues = Collections.emptyMap();
    private volatile List<String> mInvalidKeys = Collections.emptyList();

    private PropertiesSettingsRepository(@NonNull File file) {
        mFile = file;
    }

    /**
     * 打开配置文件并完成首次读取。
     *
     * <p>文件不存在时写入默认模板。写入或读取失败不抛出异常，方法返回按默认值运行的实例，
     * 调用方可通过 {@link #getFilePath()} 与实际读取结果判断是否需要提示用户。
     */
    @NonNull
    public static PropertiesSettingsRepository open(@NonNull File file) {
        PropertiesSettingsRepository repository = new PropertiesSettingsRepository(file);
        if (!file.isFile()) {
            writeDefaultTemplate(file);
        }
        repository.reload();
        return repository;
    }

    @NonNull
    public File getFilePath() {
        return mFile;
    }

    /**
     * 重新读取配置文件并替换内存快照。
     *
     * @return 是否读取成功。文件不存在或读取失败时返回 false，快照保持不变。
     */
    public synchronized boolean reload() {
        if (!mFile.isFile()) {
            return false;
        }
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(mFile)) {
            properties.load(input);
        } catch (IOException e) {
            return false;
        }
        Map<String, String> values = new HashMap<>();
        List<String> invalidKeys = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            String raw = properties.getProperty(key);
            SettingsSpec.Entry entry = SettingsSpec.find(key);
            if (entry == null) {
                values.put(key, raw == null ? "" : raw.trim());
                continue;
            }
            String normalized = entry.normalize(raw);
            if (normalized == null) {
                invalidKeys.add(key);
                continue;
            }
            values.put(key, normalized);
        }
        mValues = Collections.unmodifiableMap(values);
        mInvalidKeys = Collections.unmodifiableList(invalidKeys);
        return true;
    }

    @Override
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        String raw = mValues.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if ("true".equals(raw)) {
            return true;
        }
        if ("false".equals(raw)) {
            return false;
        }
        return defaultValue;
    }

    @Override
    public int getInt(@NonNull String key, int defaultValue) {
        String raw = mValues.get(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Nullable
    @Override
    public String getString(@NonNull String key, @Nullable String defaultValue) {
        String raw = mValues.get(key);
        if (raw == null) {
            return defaultValue;
        }
        SettingsSpec.Entry entry = SettingsSpec.find(key);
        if (entry == null) {
            return raw;
        }
        String normalized = entry.normalize(raw);
        return normalized == null ? defaultValue : normalized;
    }

    @NonNull
    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(mValues.keySet()));
    }

    /**
     * 返回最近一次读取中被判定为非法的键名，供日志输出。
     */
    @NonNull
    public List<String> getInvalidKeys() {
        return mInvalidKeys;
    }

    private static void writeDefaultTemplate(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(SettingsSpec.buildDefaultFileContent());
        } catch (IOException e) {
            // 写入失败时按默认值运行；下次启动会重试写入。
        }
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "PropertiesSettingsRepository(%s, keys=%d)",
            mFile.getName(), mValues.size());
    }
}
