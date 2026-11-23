package com.termux.app.ext.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.ext.api.ExtensionViewSlot;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 目的 — 把「会话抽屉」作为可启用的扩展提供：左侧抽屉、边缘把手、头部概要与会话列表。
 *
 * <p>行为 — 启用时接管布局预留的抽屉与把手视图，创建 {@link TerminalDrawerController} 并按 `drawer-*`
 * 属性装配；会话列表适配器由扩展创建并绑定，宿主只负责把抽屉打开或关闭。禁用时抽屉不再打开、把手隐藏，
 * 会话仍可从命令面板与终端菜单切换。</p>
 *
 * <p>约束 — 适配器读取会话客户端（重命名、切换）与属性，均由扩展通过宿主公开方法获取；扩展不得持有宿主的
 * 会话集合的副本，列表随服务的会话列表变化而刷新。</p>
 */
public final class DrawerExtension implements Extension {

    private static final String LOG_TAG = "DrawerExtension";

    /** The stable id of the extension. */
    public static final String ID = "drawer";

    @Nullable
    private TermuxActivity mActivity;
    @Nullable
    private TerminalDrawerController mController;
    @Nullable
    private TermuxSessionsListViewController mSessionsAdapter;

    @NonNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public int titleRes() {
        return R.string.termux_drawer_extension_title;
    }

    @Override
    public int summaryRes() {
        return R.string.termux_drawer_extension_summary;
    }

    @NonNull
    @Override
    public List<ExtensionViewSlot> slots() {
        return Collections.unmodifiableList(Arrays.asList(
            new ExtensionViewSlot(R.id.left_drawer, "left_drawer",
                "The drawer that lists the sessions"),
            new ExtensionViewSlot(R.id.drawer_handle, "drawer_handle",
                "The handle on the left edge that opens the drawer")));
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public void onEnable(@NonNull ExtensionContext context) {
        if (!(context.activity() instanceof TermuxActivity)) {
            context.logWarn("The sessions drawer needs the terminal activity, not " + context.activity());
            return;
        }

        mActivity = (TermuxActivity) context.activity();
        mController = new TerminalDrawerController(mActivity);
        mController.reloadConfig();
        mController.setUp();
        mController.updateColors(mActivity.getCurrentSession());

        setupSessionsList(mActivity);
        context.logInfo("Sessions drawer enabled");
    }

    @Override
    public void onReload() {
        if (mController == null) return;
        mController.reloadConfig();
        mController.updateColors(mActivity != null ? mActivity.getCurrentSession() : null);
    }

    @Override
    public void onDisable() {
        if (mController != null) {
            mController.setSessionsListAdapter(null);
            mController.closeDrawer();
        }
        mSessionsAdapter = null;
        mController = null;
        mActivity = null;
    }

    /**
     * Bind the sessions to the list once the service is connected.
     *
     * <p>The drawer is enabled before the terminal service is connected, so the adapter has no sessions to show
     * at that point; the host calls this method when the connection is up.</p>
     */
    public void onSessionsAvailable() {
        if (mActivity == null || mController == null) return;
        setupSessionsList(mActivity);
    }

    /** @return The controller of the drawer, or {@code null} while the extension is disabled. */
    @Nullable
    public TerminalDrawerController getController() {
        return mController;
    }

    /** Open the drawer, like from the handle, the terminal menu or a virtual key. */
    public void openDrawer() {
        if (mController != null) mController.openDrawer();
    }

    /** Close the drawer if it is open. */
    public void closeDrawer() {
        if (mController != null) mController.closeDrawer();
    }

    /** @return Whether the drawer is open. */
    public boolean isDrawerOpen() {
        return mController != null && mController.isOpen();
    }

    /** Enable or disable the edge swipe of the drawer, which the terminal view client needs for copy mode. */
    public void setDrawerLockMode(int lockMode) {
        if (mController != null) mController.setLockMode(lockMode);
    }

    /** Apply the color scheme of {@code session} to the drawer and to the rows of the session list. */
    public void updateColors(@Nullable com.termux.terminal.TerminalSession session) {
        if (mController != null) mController.updateColors(session);
    }

    /** Refresh the rows of the session list, since the details of a session may have changed. */
    public void notifySessionsChanged() {
        if (mSessionsAdapter != null) mSessionsAdapter.notifyDataSetChanged();
    }

    /** Show the summary of the current session in the header of the drawer. */
    public void updateSummary() {
        if (mController != null) mController.updateSummary();
    }

    private void setupSessionsList(@NonNull TermuxActivity activity) {
        TermuxTerminalSessionActivityClient sessions = activity.getTermuxTerminalSessionClient();
        if (sessions == null) {
            Logger.logWarn(LOG_TAG, "The session client is not available yet, the list stays empty");
            return;
        }

        android.widget.ListView listView = mController != null ? mController.getSessionsListView() : null;
        if (listView == null) return;

        mSessionsAdapter = new TermuxSessionsListViewController(activity,
            activity.getTermuxService() != null
                ? activity.getTermuxService().getTermuxSessions() : new java.util.ArrayList<>(),
            mController);
        listView.setAdapter(mSessionsAdapter);
        listView.setOnItemClickListener(mSessionsAdapter);
        listView.setOnItemLongClickListener(mSessionsAdapter);
        if (mController != null) mController.setSessionsListAdapter(mSessionsAdapter);
    }
}
