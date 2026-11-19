package com.termux.shell.ui;

/**
 * 宽度档位（window width size class）。
 *
 * <p>目的：以窗口可用宽度为唯一依据选择外壳布局形态，使同一份界面在手机、折叠屏与桌面窗口中
 * 均可保持合理的信息密度。
 *
 * <p>取值依据：断点采用 600dp 与 840dp。前者为紧凑形态与多窗格形态的分界，后者为侧栏与页签栏
 * 同时出现的分界，与 Material 3 自适应布局的常用断点一致。
 *
 * <p>约束：本类型为纯计算类型，不得引入 Android 依赖，以便在单元测试中直接验证。
 */
public enum WindowWidthClass {

    /** 紧凑形态：单窗格，会话列表以抽屉形式覆盖显示。 */
    COMPACT(0, 600),

    /** 中等形态：常驻侧栏与最多双窗格。 */
    MEDIUM(600, 840),

    /** 展开形态：常驻侧栏、页签栏与任意窗格。 */
    EXPANDED(840, Integer.MAX_VALUE);

    private final int mMinWidthDp;
    private final int mMaxWidthDp;

    WindowWidthClass(int minWidthDp, int maxWidthDp) {
        mMinWidthDp = minWidthDp;
        mMaxWidthDp = maxWidthDp;
    }

    public int getMinWidthDp() {
        return mMinWidthDp;
    }

    public int getMaxWidthDp() {
        return mMaxWidthDp;
    }

    /**
     * 返回常驻侧栏是否应当显示。
     */
    public boolean hasPersistentRail() {
        return this != COMPACT;
    }

    /**
     * 按窗口宽度返回对应档位。
     *
     * @param widthDp 窗口可用宽度，单位为 dp；负值按紧凑档位处理。
     */
    public static WindowWidthClass fromWidthDp(int widthDp) {
        if (widthDp < MEDIUM.mMinWidthDp) {
            return COMPACT;
        }
        if (widthDp < EXPANDED.mMinWidthDp) {
            return MEDIUM;
        }
        return EXPANDED;
    }
}
