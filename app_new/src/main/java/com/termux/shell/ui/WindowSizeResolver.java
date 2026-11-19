package com.termux.shell.ui;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

/**
 * 由当前窗口度量推导宽度档位。
 *
 * <p>目的：将平台窗口度量与档位判定解耦，便于在桌面窗口、分屏与折叠屏场景下复用同一判定逻辑。
 *
 * <p>约束：必须按窗口而非屏幕度量计算；在桌面窗口与分屏模式下二者可能显著不同。
 */
public final class WindowSizeResolver {

    private WindowSizeResolver() {
    }

    /**
     * 计算当前窗口的可用宽度（dp）。
     *
     * <p>说明：骨架阶段直接使用窗口边界宽度。待接入既定的内边距处理后，需扣除系统栏与侧栏占位宽度，
     * 对应的调整在里程碑 M13 中完成。
     */
    public static int currentWidthDp(@NonNull Activity activity) {
        WindowMetrics metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        if (density <= 0f) {
            return 0;
        }
        // 密度取自资源配置而非窗口度量：androidx.window 1.1 的 WindowMetrics 未提供密度访问方法。
        return Math.round(metrics.getBounds().width() / density);
    }

    @NonNull
    public static WindowWidthClass currentWidthClass(@NonNull Activity activity) {
        return WindowWidthClass.fromWidthDp(currentWidthDp(activity));
    }
}
