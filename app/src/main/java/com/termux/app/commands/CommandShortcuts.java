package com.termux.app.commands;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.ext.menu.TerminalMenuAction;
import com.termux.app.properties.ForkPropertyKeys;
import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 目的 — 作为命令面板与终端按键处理的唯一快捷键来源，使面板上显示的组合键与实际按键行为不会漂移。
 *
 * <p>行为 — 从 `command-shortcuts` 属性解析「命令 id = 快捷键」的映射，例如
 * {@code search=Ctrl+Alt+F,palette=Ctrl+Shift+P}；未列出的命令使用默认值，写成 {@code search=} 则取消该命令的
 * 快捷键。解析失败或未知的命令 id 会记录警告并忽略，不会让应用启动失败。{@link #shortcutFor} 供面板显示标签，
 * {@link #matches} 供按键分发判断，两者读取同一份映射。</p>
 *
 * <p>约束 — 快捷键文本必须能被 {@link ShellCommandShortcut} 解析（修饰键 + 主键，如 `Ctrl+Alt+F`），无法解析的
 * 文本永不匹配；调用方只能通过 {@link #parse} 构建，不要在别处另写一份解析。</p>
 */
public final class CommandShortcuts {

    private static final String LOG_TAG = "CommandShortcuts";

    /** The id of the command that opens the palette itself, which is also the id of its menu action. */
    public static final String ID_PALETTE = TerminalMenuAction.COMMAND_PALETTE.getId();

    /** The shortcut that the palette shows and takes by default, as `id=shortcut` entries. */
    public static final String DEFAULT = ID_PALETTE + "=Ctrl+Shift+P,"
        + TerminalMenuAction.SEARCH.getId() + "=Ctrl+Alt+F,"
        + ShellCommandCatalog.ID_TOGGLE_KEYBOARD + "=Ctrl+Alt+K,"
        + ShellCommandCatalog.ID_NEW_SESSION + "=Ctrl+Alt+C";

    @NonNull
    private final Map<String, String> mShortcuts;

    private CommandShortcuts(@NonNull Map<String, String> shortcuts) {
        mShortcuts = shortcuts;
    }

    /** @return The default shortcuts, which are the combinations that the terminal view client always took. */
    @NonNull
    public static CommandShortcuts defaults() {
        return parseValue(DEFAULT);
    }

    /** @return The shortcuts of the {@code properties} of the `termux.properties` file. */
    @NonNull
    public static CommandShortcuts parse(@Nullable Properties properties) {
        String value = properties != null ? properties.getProperty(ForkPropertyKeys.COMMAND_SHORTCUTS) : null;
        return DataUtils.isNullOrEmpty(value) ? defaults() : parseValue(value);
    }

    /** @return The shortcuts of the {@code value} of the `command-shortcuts` property. */
    @NonNull
    static CommandShortcuts parseValue(@Nullable String value) {
        // The defaults are the starting point, so that a configuration only has to list what it changes.
        Map<String, String> shortcuts = new LinkedHashMap<>();
        readInto(shortcuts, DEFAULT);

        Map<String, String> parsed = new LinkedHashMap<>();
        if (readInto(parsed, value)) shortcuts.putAll(parsed);

        return new CommandShortcuts(Collections.unmodifiableMap(shortcuts));
    }

    /**
     * Read the `id=shortcut` entries of {@code value} into {@code out}.
     *
     * @return Whether at least one valid entry was read.
     */
    private static boolean readInto(@NonNull Map<String, String> out, @Nullable String value) {
        if (DataUtils.isNullOrEmpty(value)) return false;

        boolean readAny = false;
        for (String entry : value.split(",")) {
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                Logger.logWarn(LOG_TAG, "Ignoring \"" + entry.trim() + "\", the entries of the \""
                    + ForkPropertyKeys.COMMAND_SHORTCUTS + "\" property must be \"id=shortcut\" pairs");
                continue;
            }

            String id = entry.substring(0, separator).trim().toLowerCase(java.util.Locale.ROOT);
            String shortcut = entry.substring(separator + 1).trim();
            if (!isKnownId(id)) {
                Logger.logWarn(LOG_TAG, "Ignoring the shortcut of the unknown command id \"" + id
                    + "\" of the \"" + ForkPropertyKeys.COMMAND_SHORTCUTS + "\" property");
                continue;
            }

            out.put(id, shortcut);
            readAny = true;
        }
        return readAny;
    }

    /**
     * @return The shortcut that is shown and taken for the command, or {@code null} if it has none, either
     *         because none is configured or because the entry disabled it with an empty value.
     */
    @Nullable
    public String shortcutFor(@Nullable String commandId) {
        if (commandId == null) return null;
        String shortcut = mShortcuts.get(commandId.toLowerCase(java.util.Locale.ROOT));
        return DataUtils.isNullOrEmpty(shortcut) ? null : shortcut;
    }

    /** @return Whether {@code event} is the shortcut of the command, so that the caller can run it. */
    public boolean matches(@Nullable String commandId, @NonNull KeyEvent event) {
        return ShellCommandShortcut.matches(shortcutFor(commandId), event);
    }

    /** @return Whether {@code id} belongs to a known command, so that a typo does not silently do nothing. */
    public static boolean isKnownId(@Nullable String id) {
        if (id == null) return false;
        if (ShellCommandCatalog.ID_NEW_NAMED_SESSION.equals(id)
            || ShellCommandCatalog.ID_TOGGLE_KEYBOARD.equals(id)
            || ShellCommandCatalog.ID_NEW_SESSION.equals(id)) return true;
        return TerminalMenuAction.valueOfId(id) != null;
    }
}
