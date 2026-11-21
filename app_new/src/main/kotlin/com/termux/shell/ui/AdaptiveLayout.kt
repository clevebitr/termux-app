package com.termux.shell.ui

import com.termux.shell.config.SettingsSpec

/**
 * 自适应布局的档位选择。
 *
 * 目的：把「配置中的界面形态」与「窗口实际宽度」合成为唯一的布局档位，使界面只按一个枚举分支，
 * 避免在多个组件里重复判断配置项。
 *
 * 行为：`phone` 与 `tablet` 为固定形态，分别映射到紧凑与展开档；`auto` 按宽度档位计算。
 *
 * 约束：纯计算，不依赖 Android 运行时，可在 JVM 单元测试中直接验证。
 */
object AdaptiveLayout {

    /** 按配置值与窗口宽度返回布局档位；未知配置值按 `auto` 处理。 */
    fun widthClassFor(layoutMode: String?, widthDp: Int): WindowWidthClass = when (layoutMode) {
        SettingsSpec.LAYOUT_PHONE -> WindowWidthClass.COMPACT
        SettingsSpec.LAYOUT_TABLET -> WindowWidthClass.EXPANDED
        else -> WindowWidthClass.fromWidthDp(widthDp)
    }
}
