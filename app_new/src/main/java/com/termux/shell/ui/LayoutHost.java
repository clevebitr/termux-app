package com.termux.shell.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.termux.shell.R;
import com.termux.shell.pane.PaneLayoutController;
import com.termux.shell.pane.PaneHostView;

/**
 * 外壳布局装配器。
 *
 * <p>目的：把「宽度档位到界面形态」的映射集中在一处，使 {@code ShellActivity} 只负责生命周期与事件分发。
 *
 * <p>骨架阶段职责：
 * <ul>
 *     <li>按档位显示或隐藏常驻侧栏，并调整其宽度；</li>
 *     <li>刷新头部摘要文本，用于验证档位计算与窗格树的同步性；</li>
 *     <li>把当前窗格树交给 {@link PaneHostView} 绘制。</li>
 * </ul>
 *
 * <p>约束：本类型不得持有 Activity 引用，仅持有视图引用；视图由调用方负责生命周期。
 */
public final class LayoutHost {

    private static final int RAIL_WIDTH_MEDIUM_DP = 80;
    private static final int RAIL_WIDTH_EXPANDED_DP = 200;

    private final View mRailContainer;
    private final ViewGroup mRoot;
    private final android.widget.TextView mHeader;
    private final PaneHostView mPaneHost;

    public LayoutHost(@NonNull ViewGroup root, @NonNull View railContainer, @NonNull android.widget.TextView header,
                      @NonNull PaneHostView paneHost) {
        mRoot = root;
        mRailContainer = railContainer;
        mHeader = header;
        mPaneHost = paneHost;
    }

    /**
     * 应用指定宽度档位的布局形态。
     *
     * @param widthClass 当前宽度档位。
     * @param controller 当前窗格树控制器，用于绘制与统计。
     */
    public void apply(@NonNull WindowWidthClass widthClass, @NonNull PaneLayoutController controller) {
        float density = mRoot.getResources().getDisplayMetrics().density;

        if (widthClass.hasPersistentRail()) {
            int widthDp = widthClass == WindowWidthClass.EXPANDED
                ? RAIL_WIDTH_EXPANDED_DP : RAIL_WIDTH_MEDIUM_DP;
            ViewGroup.LayoutParams params = mRailContainer.getLayoutParams();
            params.width = Math.round(widthDp * density);
            mRailContainer.setLayoutParams(params);
            mRailContainer.setVisibility(View.VISIBLE);
        } else {
            mRailContainer.setVisibility(View.GONE);
        }

        mHeader.setText(mRoot.getResources().getString(
            R.string.shell_header,
            mRoot.getResources().getString(labelOf(widthClass)),
            controller.collectLeaves().size()));

        mPaneHost.setController(controller);
    }

    private static int labelOf(@NonNull WindowWidthClass widthClass) {
        switch (widthClass) {
            case MEDIUM:
                return R.string.size_class_medium;
            case EXPANDED:
                return R.string.size_class_expanded;
            case COMPACT:
            default:
                return R.string.size_class_compact;
        }
    }
}
