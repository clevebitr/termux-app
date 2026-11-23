package com.termux.app.ext.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.ext.api.ExtensionViewSlot;

import java.util.Collections;
import java.util.List;

/**
 * 目的 — 把「终端内搜索」作为可启用的扩展提供：在屏幕与回滚记录中查找查询词、高亮匹配并在匹配之间跳转。
 *
 * <p>行为 — 启用时接管布局预留的搜索条（{@code R.id.terminal_search_bar}），创建
 * {@link TerminalSearchController} 并装配按钮；宿主把「搜索」动作转发进来（终端菜单、命令面板与工具条）。
 * 禁用时隐藏搜索条并释放监听，搜索动作变为无操作。</p>
 *
 * <p>约束 — 搜索条是宿主布局的一部分，扩展只接管其行为，声明槽位是为了让归属可校验；搜索选项与历史存于
 * {@link TerminalSearchPreferences}，不得写进 `termux.properties`。</p>
 */
public final class SearchExtension implements Extension {

    /** The stable id of the extension. */
    public static final String ID = "search";

    @Nullable
    private TermuxActivity mActivity;
    @Nullable
    private TerminalSearchController mController;

    @NonNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public int titleRes() {
        return R.string.termux_search_extension_title;
    }

    @Override
    public int summaryRes() {
        return R.string.termux_search_extension_summary;
    }

    @NonNull
    @Override
    public List<ExtensionViewSlot> slots() {
        return Collections.singletonList(new ExtensionViewSlot(R.id.terminal_search_bar, "terminal_search_bar",
            "The search bar that is overlaid on the terminal"));
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public void onEnable(@NonNull ExtensionContext context) {
        if (!(context.activity() instanceof TermuxActivity)) {
            context.logWarn("The terminal search needs the terminal activity, not " + context.activity());
            return;
        }

        mActivity = (TermuxActivity) context.activity();
        mController = new TerminalSearchController(mActivity);
        mController.setUp();
        context.logInfo("Terminal search enabled");
    }

    @Override
    public void onDisable() {
        if (mController != null) mController.hide();
        mController = null;
        mActivity = null;
    }

    /** Show the search bar and focus its text input, so that the user can type a query. */
    public void show() {
        if (mController != null) mController.show();
    }

    /** Hide the search bar and clear the highlights. */
    public void hide() {
        if (mController != null) mController.hide();
    }

    /** @return Whether the search bar is shown. */
    public boolean isVisible() {
        return mController != null && mController.isVisible();
    }

    /**
     * Apply the query again, which is required after a session switch since the matches of the previous buffer
     * are invalid.
     */
    public void refresh() {
        if (mController != null) mController.refresh();
    }
}
