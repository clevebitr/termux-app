package com.termux.shell.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shell.R;
import com.termux.shell.pane.PaneHostView;
import com.termux.shell.tabs.TabWorkspace;
import com.termux.shell.tabs.WorkspaceTab;

/**
 * 外壳布局装配器。
 *
 * <p>目的：把「窗口宽度档位与视图选项到界面形态」的映射集中在一处，使 {@code ShellActivity}
 * 只负责生命周期与事件分发。
 *
 * <p>行为：
 * <ul>
 *     <li>按档位显示或隐藏常驻侧栏，并调整其宽度；</li>
 *     <li>按选项切换标签栏的可见性与位置（内容区顶部或底部）；</li>
 *     <li>把当前标签的窗格树交给窗格宿主视图，并刷新标题行与侧栏摘要。</li>
 * </ul>
 *
 * <p>约束：本类型不持有 Activity 或配置来源，只持有视图引用；视图生命周期由调用方负责。
 */
public final class LayoutHost {

    private static final int RAIL_WIDTH_MEDIUM_DP = 88;
    private static final int RAIL_WIDTH_EXPANDED_DP = 220;

    /** 标签栏位于标题行下方时的子视图索引。 */
    private static final int TAB_BAR_TOP_INDEX = 1;

    private final ViewGroup mRoot;
    private final View mRail;
    private final TextView mRailSummary;
    private final ViewGroup mContent;
    private final View mTabBar;
    private final TextView mHeader;
    private final TabStripView mTabStrip;
    private final PaneHostView mPaneHost;

    public LayoutHost(@NonNull ViewGroup root, @NonNull View rail, @NonNull TextView railSummary,
                      @NonNull ViewGroup content, @NonNull View tabBar, @NonNull TextView header,
                      @NonNull TabStripView tabStrip, @NonNull PaneHostView paneHost) {
        mRoot = root;
        mRail = rail;
        mRailSummary = railSummary;
        mContent = content;
        mTabBar = tabBar;
        mHeader = header;
        mTabStrip = tabStrip;
        mPaneHost = paneHost;
    }

    /**
     * 应用当前档位与选项。
     *
     * @param widthClass 当前窗口宽度档位。
     * @param workspace  当前窗口的标签工作区。
     * @param options    标签栏相关的视图选项。
     */
    public void apply(@NonNull WindowWidthClass widthClass, @NonNull TabWorkspace workspace,
                      @NonNull ShellViewOptions options) {
        applyRail(widthClass);

        WorkspaceTab activeTab = workspace.getActiveTab();
        int paneCount = activeTab == null ? 0 : activeTab.getPanes().getLeafCount();
        String activeTitle = activeTab == null ? "" : activeTab.getTitle();
        mHeader.setText(mRoot.getResources().getString(R.string.shell_header, activeTitle, paneCount));
        mRailSummary.setText(mRoot.getResources().getString(
            R.string.shell_rail_summary, workspace.size(), paneCount));

        mTabStrip.setWorkspace(workspace);
        mTabStrip.setShowCloseButton(options.isShowTabCloseButton());
        mTabStrip.setEqualWidth(options.isEqualTabWidth());
        mTabBar.setVisibility(options.isTabBarVisible() ? View.VISIBLE : View.GONE);
        positionTabBar(options.isTabBarAtBottom());

        mPaneHost.setController(activeTab == null ? null : activeTab.getPanes());
        mPaneHost.invalidate();
    }

    private void applyRail(@NonNull WindowWidthClass widthClass) {
        if (!widthClass.hasPersistentRail()) {
            mRail.setVisibility(View.GONE);
            return;
        }
        float density = mRoot.getResources().getDisplayMetrics().density;
        int widthDp = widthClass == WindowWidthClass.EXPANDED
            ? RAIL_WIDTH_EXPANDED_DP : RAIL_WIDTH_MEDIUM_DP;
        ViewGroup.LayoutParams params = mRail.getLayoutParams();
        int width = Math.round(widthDp * density);
        if (params.width != width) {
            params.width = width;
            mRail.setLayoutParams(params);
        }
        mRail.setVisibility(View.VISIBLE);
    }

    /**
     * 把标签栏移动到内容区的顶部或底部。
     *
     * <p>说明：位置切换通过调整子视图顺序完成，不重建视图，因此标签栏的滚动位置与按压状态保持有效。
     */
    private void positionTabBar(boolean atBottom) {
        int current = mContent.indexOfChild(mTabBar);
        if (current < 0) {
            return;
        }
        int desired = atBottom ? mContent.getChildCount() - 1 : TAB_BAR_TOP_INDEX;
        if (current == desired) {
            return;
        }
        mContent.removeViewAt(current);
        mContent.addView(mTabBar, Math.min(desired, mContent.getChildCount()));
    }

    /**
     * 返回标签栏容器视图，供测试或调试查询当前可见性。
     */
    @NonNull
    public View getTabBarView() {
        return mTabBar;
    }
}
