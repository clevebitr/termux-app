package com.termux.shell.ui

/**
 * 宽度档位（window width size class）。
 *
 * 目的：以窗口可用宽度为唯一依据选择外壳布局形态，使同一份界面在手机、折叠屏与桌面窗口中
 * 均可保持合理的信息密度。
 *
 * 取值依据：断点采用 600dp 与 840dp。前者为紧凑形态与多窗格形态的分界，后者为侧栏与标签栏
 * 同时出现的分界，与 Material 3 自适应布局的常用断点一致。
 *
 * 约束：本类型为纯计算类型，不引入 Android 依赖，可在 JVM 单元测试中直接验证。
 */
enum class WindowWidthClass(val minWidthDp: Int, val maxWidthDp: Int) {

    /** 紧凑形态：单窗格，会话列表以抽屉或底部面板形式显示。 */
    COMPACT(0, 600),

    /** 中等形态：常驻侧栏与最多双窗格。 */
    MEDIUM(600, 840),

    /** 展开形态：常驻侧栏、标签栏与任意窗格。 */
    EXPANDED(840, Int.MAX_VALUE);

    /** 返回常驻侧栏是否应当显示。 */
    val hasPersistentRail: Boolean
        get() = this != COMPACT

    companion object {

        /** 按窗口宽度返回对应档位；负值按紧凑档位处理。 */
        fun fromWidthDp(widthDp: Int): WindowWidthClass = when {
            widthDp < MEDIUM.minWidthDp -> COMPACT
            widthDp < EXPANDED.minWidthDp -> MEDIUM
            else -> EXPANDED
        }
    }
}
