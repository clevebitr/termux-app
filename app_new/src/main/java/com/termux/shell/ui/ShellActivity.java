package com.termux.shell.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.termux.shell.R;
import com.termux.shell.log.ShellLog;
import com.termux.shell.pane.PaneHostView;
import com.termux.shell.pane.PaneLayoutCodec;
import com.termux.shell.pane.PaneLayoutController;
import com.termux.shell.pane.PaneLeaf;
import com.termux.shell.pane.PaneNode;
import com.termux.shell.pane.PaneSplit;

/**
 * 外壳窗口宿主。
 *
 * <p>目的：作为单个窗口的唯一入口，负责生命周期、窗口内边距、窗口级操作（新建窗口）与布局装配，
 * 并把窗格结构交给 {@link PaneLayoutController} 与 {@link PaneHostView}。
 *
 * <p>边界：
 * <ul>
 *     <li>本类不持有任何会话状态，会话由 {@code SessionHost} 管理；</li>
 *     <li>本类不实现终端渲染与键序编码，二者分别由渲染层与输入层承担；</li>
 *     <li>骨架阶段窗格内显示占位内容，终端视图挂载在里程碑 M13 完成。</li>
 * </ul>
 *
 * <p>关于多窗口：清单中声明 {@code documentLaunchMode="always"}，使每次「新建窗口」产生独立任务，
 * 从而支持桌面窗口模式与分屏模式下的并行窗口（详见 {@code docs/UPGRADE.md} 第 3 节 W1）。
 */
public final class ShellActivity extends AppCompatActivity {

    private static final String SUB_TAG = "ShellActivity";
    private static final String STATE_PANE_LAYOUT = "pane_layout";

    private PaneLayoutController mPaneLayoutController;
    private LayoutHost mLayoutHost;
    private ViewGroup mRootView;
    private PaneHostView mPaneHostView;
    private TextView mHeaderView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_shell);

        mRootView = findViewById(R.id.shell_root);
        mHeaderView = findViewById(R.id.shell_header);
        mPaneHostView = findViewById(R.id.shell_pane_host);

        mPaneLayoutController = new PaneLayoutController();
        mLayoutHost = new LayoutHost(mRootView, findViewById(R.id.shell_rail), mHeaderView, mPaneHostView);

        restorePaneLayout(savedInstanceState);
        applyWindowInsets();
        bindActions();
        refreshLayout();
        updateSystemBarsAppearance();

        ShellLog.info(SUB_TAG, "窗口已创建，档位=" + WindowSizeResolver.currentWidthClass(this)
            + "，窗格数=" + mPaneLayoutController.getLeafCount());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PANE_LAYOUT, PaneLayoutCodec.encode(mPaneLayoutController.getRoot()));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfiguration) {
        super.onConfigurationChanged(newConfiguration);
        refreshLayout();
        updateSystemBarsAppearance();
    }

    private void restorePaneLayout(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        String encoded = savedInstanceState.getString(STATE_PANE_LAYOUT);
        PaneNode root = PaneLayoutCodec.decode(encoded);
        if (root == null) {
            ShellLog.warn(SUB_TAG, "窗格布局恢复失败，回退到默认单窗格", null);
            return;
        }
        mPaneLayoutController.restore(root);
    }

    /**
     * 应用系统栏内边距。
     *
     * <p>说明：外壳自行绘制背景，因此关闭系统栏自动内边距，改为把内边距施加在根视图上。
     * 该处理在顶部与底部系统栏高度变化时同样生效，包括手势导航与桌面窗口标题栏。
     */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mRootView, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
    }

    private void bindActions() {
        findViewById(R.id.shell_action_split_horizontal).setOnClickListener(
            view -> splitFocusedLeaf(PaneSplit.ORIENTATION_HORIZONTAL));
        findViewById(R.id.shell_action_split_vertical).setOnClickListener(
            view -> splitFocusedLeaf(PaneSplit.ORIENTATION_VERTICAL));
        findViewById(R.id.shell_action_close_pane).setOnClickListener(view -> closeFocusedLeaf());
        findViewById(R.id.shell_action_new_window).setOnClickListener(view -> openNewWindow());
    }

    private void splitFocusedLeaf(int orientation) {
        PaneLeaf newLeaf = mPaneLayoutController.split(mPaneLayoutController.getFocusedLeaf(), orientation);
        if (newLeaf == null) {
            ShellLog.warn(SUB_TAG, "窗格分割被拒绝，当前窗格数已达到上限", null);
            return;
        }
        refreshLayout();
    }

    private void closeFocusedLeaf() {
        if (!mPaneLayoutController.close(mPaneLayoutController.getFocusedLeaf())) {
            ShellLog.warn(SUB_TAG, "窗格关闭被拒绝，至少需要保留一个窗格", null);
            return;
        }
        refreshLayout();
    }

    /**
     * 新建独立窗口。
     *
     * <p>说明：{@code FLAG_ACTIVITY_MULTIPLE_TASK} 与 {@code FLAG_ACTIVITY_NEW_DOCUMENT} 组合使用，
     * 使新窗口在最近任务中独立出现，并可在桌面窗口模式下与前一个窗口并排显示。
     */
    private void openNewWindow() {
        Intent intent = new Intent(this, ShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        startActivity(intent);
    }

    private void refreshLayout() {
        WindowWidthClass widthClass = WindowSizeResolver.currentWidthClass(this);
        mLayoutHost.apply(widthClass, mPaneLayoutController);
        mPaneHostView.invalidate();
    }

    /**
     * 按当前日夜模式设置系统栏图标明暗，使浅色背景下图标为深色。
     */
    private void updateSystemBarsAppearance() {
        boolean nightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), decorView);
        controller.setAppearanceLightStatusBars(!nightMode);
        controller.setAppearanceLightNavigationBars(!nightMode);
    }
}
