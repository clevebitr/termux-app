package com.termux.shell.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 用户可配置项的定义表。
 *
 * <p>目的：把键名、类型、默认值、合法取值与说明集中在一处，使「配置文件模板生成」「读取校验」
 * 「默认值回退」共享同一份定义，避免三处各写一遍导致不一致。
 *
 * <p>约束：
 * <ul>
 *     <li>只定义外壳自身的配置项；终端核心的配置项由终端层另行定义，两者不得混用键名；</li>
 *     <li>新增配置项必须同时给出中文说明，说明会写入配置文件模板；</li>
 *     <li>合法取值一律小写，读取时按小写归一化，避免大小写差异导致校验失败。</li>
 * </ul>
 */
public final class SettingsSpec {

    public static final String KEY_APP_THEME = "app-theme";
    public static final String KEY_TAB_BAR_VISIBLE = "tab-bar-visible";
    public static final String KEY_TAB_BAR_POSITION = "tab-bar-position";
    public static final String KEY_TAB_CLOSE_BUTTON = "tab-close-button";
    public static final String KEY_TAB_WIDTH_MODE = "tab-width-mode";
    public static final String KEY_COMMAND_PALETTE_ENABLED = "command-palette-enabled";
    public static final String KEY_KEYMAP_ENABLED = "keymap-enabled";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static final String TAB_POSITION_TOP = "top";
    public static final String TAB_POSITION_BOTTOM = "bottom";

    public static final String TAB_WIDTH_EQUAL = "equal";
    public static final String TAB_WIDTH_COMPACT = "compact";

    /**
     * 配置项类型。取值语义由 {@link Entry#normalize(String)} 负责。
     */
    public enum Type {
        BOOLEAN,
        CHOICE
    }

    private static final List<Entry> ENTRIES;
    private static final Map<String, Entry> BY_KEY;

    static {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(KEY_APP_THEME, Type.CHOICE, THEME_SYSTEM,
            Arrays.asList(THEME_SYSTEM, THEME_LIGHT, THEME_DARK),
            "界面主题，取值 system、light 或 dark；修改后在命令面板执行「重新加载配置文件」生效。"));
        entries.add(new Entry(KEY_TAB_BAR_VISIBLE, Type.BOOLEAN, "true", Collections.<String>emptyList(),
            "是否显示标签栏；关闭后仍可通过快捷键与命令面板切换标签。"));
        entries.add(new Entry(KEY_TAB_BAR_POSITION, Type.CHOICE, TAB_POSITION_TOP,
            Arrays.asList(TAB_POSITION_TOP, TAB_POSITION_BOTTOM),
            "标签栏位置，取值 top 或 bottom。"));
        entries.add(new Entry(KEY_TAB_CLOSE_BUTTON, Type.BOOLEAN, "true", Collections.<String>emptyList(),
            "标签上是否显示关闭按钮。"));
        entries.add(new Entry(KEY_TAB_WIDTH_MODE, Type.CHOICE, TAB_WIDTH_EQUAL,
            Arrays.asList(TAB_WIDTH_EQUAL, TAB_WIDTH_COMPACT),
            "标签宽度模式：equal 平均分配宽度，compact 按标题宽度收缩，超出时横向滚动。"));
        entries.add(new Entry(KEY_COMMAND_PALETTE_ENABLED, Type.BOOLEAN, "true", Collections.<String>emptyList(),
            "是否允许打开命令面板。"));
        entries.add(new Entry(KEY_KEYMAP_ENABLED, Type.BOOLEAN, "true", Collections.<String>emptyList(),
            "是否启用键位表中的外壳快捷键；关闭后按键全部交给终端处理。"));
        ENTRIES = Collections.unmodifiableList(entries);

        Map<String, Entry> byKey = new LinkedHashMap<>();
        for (Entry entry : entries) {
            byKey.put(entry.getKey(), entry);
        }
        BY_KEY = Collections.unmodifiableMap(byKey);
    }

    private SettingsSpec() {
    }

    /**
     * 返回全部配置项定义，顺序与配置文件模板中的顺序一致。
     */
    @NonNull
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /**
     * 返回全部键名。
     */
    @NonNull
    public static Set<String> keys() {
        return BY_KEY.keySet();
    }

    /**
     * 按键名查找定义。
     */
    @Nullable
    public static Entry find(@Nullable String key) {
        return key == null ? null : BY_KEY.get(key);
    }

    /**
     * 生成配置文件模板内容。
     *
     * <p>模板由「文件说明 + 每项的说明注释与默认值」组成；用户按键名覆盖默认值，删除某行即回到默认值。
     */
    @NonNull
    public static String buildDefaultFileContent() {
        StringBuilder builder = new StringBuilder();
        builder.append("# Termux Shell 配置文件\n");
        builder.append("# 修改后执行命令面板中的「重新加载配置文件」，或重启应用后生效。\n");
        builder.append("# 以 # 开头的行与空行被忽略；删除某项即使用默认值。\n");
        for (Entry entry : ENTRIES) {
            builder.append('\n');
            builder.append("# ").append(entry.getDescription()).append('\n');
            if (entry.getType() == Type.CHOICE) {
                builder.append("# 取值：").append(joinChoices(entry.getChoices())).append('\n');
            }
            builder.append(entry.getKey()).append('=').append(entry.getDefaultValue()).append('\n');
        }
        return builder.toString();
    }

    @NonNull
    private static String joinChoices(@NonNull List<String> choices) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < choices.size(); i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(choices.get(i));
        }
        return builder.toString();
    }

    /**
     * 单项配置定义。
     */
    public static final class Entry {

        private final String mKey;
        private final Type mType;
        private final String mDefaultValue;
        private final List<String> mChoices;
        private final String mDescription;

        private Entry(@NonNull String key, @NonNull Type type, @NonNull String defaultValue,
                      @NonNull List<String> choices, @NonNull String description) {
            mKey = key;
            mType = type;
            mDefaultValue = defaultValue;
            mChoices = Collections.unmodifiableList(new ArrayList<>(choices));
            mDescription = description;
        }

        @NonNull
        public String getKey() {
            return mKey;
        }

        @NonNull
        public Type getType() {
            return mType;
        }

        /**
         * 返回默认值的规范文本形式。
         */
        @NonNull
        public String getDefaultValue() {
            return mDefaultValue;
        }

        public boolean getDefaultBoolean() {
            return Boolean.parseBoolean(mDefaultValue);
        }

        /**
         * 返回合法取值；布尔项返回空列表。
         */
        @NonNull
        public List<String> getChoices() {
            return mChoices;
        }

        @NonNull
        public String getDescription() {
            return mDescription;
        }

        /**
         * 把文件中的原始文本归一化为规范值。
         *
         * @return 规范值；原始值为空、取值非法或类型不符时返回 null，调用方应回退到默认值。
         */
        @Nullable
        public String normalize(@Nullable String raw) {
            if (raw == null) {
                return null;
            }
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) {
                return null;
            }
            switch (mType) {
                case BOOLEAN:
                    if ("true".equals(value)) {
                        return "true";
                    }
                    if ("false".equals(value)) {
                        return "false";
                    }
                    return null;
                case CHOICE:
                    return mChoices.contains(value) ? value : null;
                default:
                    return null;
            }
        }
    }
}
