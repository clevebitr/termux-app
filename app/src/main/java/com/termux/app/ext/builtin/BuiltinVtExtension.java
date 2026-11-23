package com.termux.app.ext.builtin;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.ext.api.ExtensionSettingsPage;

import java.util.Collections;
import java.util.List;

/**
 * 目的 — 声明「核心终端能力」这一组设置项，使 `builtin.vt` 的归属与其它扩展一致，同时保持不可禁用。
 *
 * <p>行为 — 只提供设置页归属与 `preferenceKeys()`；没有视图、定时器或事件，`onEnable`/`onDisable` 无可释放的
 * 资源。{@link #isUserDisableable()} 返回 {@code false}，因此宿主永远把它当作已启用，终端能力不会被用户关掉。</p>
 *
 * <p>约束 — 本扩展只收纳**只能在只读模块内部实现**的行为开关（OSC 52 剪贴板、滚动位置保持、鼠标悬停上报、
 * 终端内边距）。这些开关关闭的是行为而不是实现，新增条目时必须核对行为确实由只读模块实现。</p>
 */
public final class BuiltinVtExtension implements Extension {

    /** The stable id of the extension. */
    public static final String ID = "builtin.vt";

    /** The preference key of the settings entry that this extension owns. */
    public static final String PREFERENCE_KEY = "builtin-vt";

    @NonNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public int titleRes() {
        return R.string.termux_builtin_vt_preferences_title;
    }

    @Override
    public int summaryRes() {
        return R.string.termux_builtin_vt_preferences_summary;
    }

    /** The user may not disable the terminal itself, only the behaviours on the page. */
    @Override
    public boolean isUserDisableable() {
        return false;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
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
            R.string.termux_builtin_vt_preferences_title, R.xml.termux_builtin_vt_preferences,
            BuiltinVtPreferencesFragment.class.getName()));
    }

    @Override
    public void onEnable(@NonNull ExtensionContext context) {
        context.logInfo("Built-in terminal behaviours are available");
    }

    @Override
    public void onDisable() {
        // Nothing to release: the behaviours are implemented inside the read-only terminal modules.
    }
}
