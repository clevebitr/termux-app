package com.termux.app.ext.statusbar;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.ext.api.Extension;
import com.termux.app.ext.api.ExtensionContext;
import com.termux.app.ext.api.ExtensionSettingsPage;
import com.termux.shared.logger.Logger;

import java.util.Collections;
import java.util.List;

/**
 * 目的 — 把「状态栏」作为可启用的扩展提供：它在终端上方或下方显示时间、电池、内存、处理器、网络、
 * ip 与代理等分段。
 *
 * <p>行为 — 启用时接管布局中预留的状态栏视图（{@code R.id.status_bar_view}），创建
 * {@link StatusBarController} 并立即按 `status-bar-*` 属性重建；活动可见时开始定时刷新，不可见时停止；
 * 属性或配色变化时重新读取；禁用时停止刷新并清空视图内容。状态栏是否占位由宿主按其配置决定，所以每次
 * 布局变化后通过 {@link TermuxActivity#onStatusBarLayoutChanged()} 通知宿主重算内边距。</p>
 *
 * <p>约束 — 只能通过 {@link ExtensionContext#activity()} 暴露的公开方法访问宿主，不得读取
 * {@link TermuxActivity} 的字段；禁止在 {@link #onEnable} 之外假设视图存在，布局可能在活动重建时更换。</p>
 */
public final class StatusBarExtension implements Extension {

    private static final String LOG_TAG = "StatusBarExtension";

    /** The stable id of the extension. */
    public static final String ID = "statusbar";

    /** The preference key of the settings entry that this extension owns. */
    public static final String PREFERENCE_KEY = "status-bar";

    @Nullable
    private StatusBarController mController;
    @Nullable
    private StatusBarView mView;
    @Nullable
    private TermuxActivity mActivity;

    @NonNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public int titleRes() {
        return R.string.termux_status_bar_preferences_title;
    }

    @Override
    public int summaryRes() {
        return R.string.termux_status_bar_preferences_summary;
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
            R.string.termux_status_bar_preferences_title, R.xml.termux_status_bar_preferences,
            StatusBarPreferencesFragment.class.getName()));
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public void onEnable(@NonNull ExtensionContext context) {
        if (!(context.activity() instanceof TermuxActivity)) {
            context.logWarn("The status bar needs the terminal activity, not " + context.activity());
            return;
        }
        mActivity = (TermuxActivity) context.activity();

        mView = mActivity.findViewById(R.id.status_bar_view);
        if (mView == null) {
            context.logWarn("The layout has no status bar view, the status bar is not shown");
            return;
        }

        mController = new StatusBarController(mActivity, mView, this::onSegmentClicked);
        mController.reload();
        applyLayout();

        context.logInfo("Status bar enabled");
    }

    @Override
    public void onStart() {
        if (mController == null) return;
        mController.start();
        applyLayout();
    }

    @Override
    public void onStop() {
        if (mController == null) return;
        mController.stop();
    }

    @Override
    public void onReload() {
        if (mController == null) return;
        mController.reload();
        applyLayout();
    }

    @Override
    public void onDisable() {
        if (mController != null) mController.stop();
        mController = null;

        // The view belongs to the layout of the host, so it is emptied instead of removed, otherwise enabling
        // the extension again would have nowhere to draw into.
        if (mView != null) {
            mView.removeAllViews();
            mView.setVisibility(View.GONE);
        }
        mView = null;

        if (mActivity != null) mActivity.onStatusBarLayoutChanged();
        mActivity = null;
    }

    /** @return The configuration that the controller last read, or {@code null} while the extension is off. */
    @Nullable
    public StatusBarConfig getConfig() {
        return mController != null ? mController.getConfig() : null;
    }

    /**
     * Apply the visibility and the elevation of the view, and let the host recompute the space that the
     * status bar takes from the terminal.
     */
    private void applyLayout() {
        if (mView == null || mController == null || mActivity == null) return;

        StatusBarConfig config = mController.getConfig();
        if (config == null) return;

        boolean visible = config.enabled && !config.segments.isEmpty();
        mView.setVisibility(visible ? View.VISIBLE : View.GONE);
        // Only an overlay needs an elevation, since the terminal is otherwise inset by the height of the status
        // bar by the host and nothing is drawn below it.
        mView.setElevation(visible && config.overlay ? dpToPx(4) : 0);

        mActivity.onStatusBarLayoutChanged();
    }

    /**
     * Run the action of a clicked segment, as the `status-bar-segment-<id>-action` property configures it.
     *
     * <p>The action belongs to the feature and is therefore implemented here instead of in the host. The one
     * that creates a session goes through the public session client of the activity, which is the only way an
     * extension may change the sessions.</p>
     */
    private void onSegmentClicked(@NonNull StatusBarConfig.Segment segment) {
        TermuxActivity activity = mActivity;
        if (activity == null) return;

        try {
            switch (segment.action) {
                case StatusBarConfig.ACTION_WIFI_SETTINGS:
                    activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    break;
                case StatusBarConfig.ACTION_BATTERY_SETTINGS:
                    activity.startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
                    break;
                case StatusBarConfig.ACTION_DATA_SETTINGS:
                    activity.startActivity(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS));
                    break;
                case StatusBarConfig.ACTION_APP_SETTINGS:
                    activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity.getPackageName())));
                    break;
                case StatusBarConfig.ACTION_NEW_SESSION:
                    if (activity.getTermuxTerminalSessionClient() != null)
                        activity.getTermuxTerminalSessionClient().addNewSession(false, null);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,
                "Failed to run the action \"" + segment.action + "\" of the segment \"" + segment.id + "\"", e);
            Logger.showToast(activity,
                activity.getString(R.string.msg_status_bar_action_failed, segment.action), true);
        }
    }

    private int dpToPx(int dp) {
        if (mActivity == null) return dp;
        return Math.round(com.termux.shared.view.ViewUtils.dpToPx(mActivity, dp));
    }
}
