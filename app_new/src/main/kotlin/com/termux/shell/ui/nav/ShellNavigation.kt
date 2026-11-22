package com.termux.shell.ui.nav

/**
 * 外壳界面层级。
 *
 * 目的：把「主界面之上叠加了哪个页面」表达为可枚举的层级，替代散布在状态持有者中的多个布尔量。
 *
 * 约束：枚举顺序即层级顺序，栈底恒为 [MAIN]；任何时刻 [ShellNavigation.layers] 的首项都必须是 [MAIN]。
 */
enum class ShellLayer {

    /** 标签栏、窗格区与会话列表所在的主界面。该层始终存在，不会因返回键弹出。 */
    MAIN,

    /** 设置页。叠加在主界面之上，返回键先关闭该层。 */
    SETTINGS,
}

/**
 * 覆盖层。
 *
 * 目的：表达「以覆盖方式出现、关闭后回到原层级」的临时界面，避免与 [ShellLayer] 混用同一个栈。
 *
 * 约束：同一时刻至多存在一个覆盖层；覆盖层可能叠在设置页之上（例如在设置页打开命令面板）。
 */
enum class ShellOverlay {

    /** 无覆盖层。 */
    NONE,

    /** 命令面板。 */
    PALETTE,

    /** 手机端会话抽屉。仅在紧凑档存在，且不叠加在设置页之上。 */
    SESSION_DRAWER,
}

/**
 * 外壳导航状态。
 *
 * 目的：以不可变值描述当前可见的页面与覆盖层，使返回键分派、抽屉开合与命令面板显隐共享同一真源，
 * 不再由多个互相同步的布尔量表达。
 *
 * 行为：
 * - [openSettings] 压入设置页并关闭覆盖层；设置页已在栈顶时不产生新状态；
 * - [openPalette]／[openSessionDrawer] 只替换覆盖层，不改变层级栈；
 * - [back] 按「覆盖层 → 设置页」的固定顺序逐层关闭，无处置时返回 null，由窗口决定是否结束活动；
 * - [onSessionDrawerClosed] 供抽屉手势关闭回写，仅在覆盖层为抽屉时生效。
 *
 * 约束：本类型不可变且不依赖 Android 运行时，可在 JVM 单元测试中直接验证；[layers] 不得为空。
 */
data class ShellNavigation(
    val layers: List<ShellLayer> = listOf(ShellLayer.MAIN),
    val overlay: ShellOverlay = ShellOverlay.NONE,
) {

    init {
        require(layers.isNotEmpty()) { "层级栈不得为空" }
        require(layers.first() == ShellLayer.MAIN) { "层级栈首项必须为主界面" }
    }

    /** 当前层级。 */
    val current: ShellLayer
        get() = layers.last()

    /** 设置页是否可见。 */
    val settingsVisible: Boolean
        get() = current == ShellLayer.SETTINGS

    /** 命令面板是否可见。 */
    val paletteVisible: Boolean
        get() = overlay == ShellOverlay.PALETTE

    /** 会话抽屉是否可见。 */
    val sessionDrawerVisible: Boolean
        get() = overlay == ShellOverlay.SESSION_DRAWER

    /** 是否存在可被返回键处置的界面。 */
    val canGoBack: Boolean
        get() = overlay != ShellOverlay.NONE || layers.size > 1

    /** 打开设置页。 */
    fun openSettings(): ShellNavigation =
        if (settingsVisible) copy(overlay = ShellOverlay.NONE) else copy(
            layers = layers + ShellLayer.SETTINGS,
            overlay = ShellOverlay.NONE,
        )

    /** 关闭设置页。 */
    fun closeSettings(): ShellNavigation =
        if (settingsVisible) copy(layers = layers.dropLast(1)) else this

    /** 打开命令面板；开关关闭时不改变状态。 */
    fun openPalette(enabled: Boolean): ShellNavigation =
        if (enabled) copy(overlay = ShellOverlay.PALETTE) else this

    /** 关闭命令面板。 */
    fun closePalette(): ShellNavigation =
        if (paletteVisible) copy(overlay = ShellOverlay.NONE) else this

    /** 请求打开会话抽屉。设置页可见时不生效，因为抽屉属于主界面。 */
    fun openSessionDrawer(): ShellNavigation =
        if (settingsVisible) this else copy(overlay = ShellOverlay.SESSION_DRAWER)

    /** 请求关闭会话抽屉。 */
    fun closeSessionDrawer(): ShellNavigation =
        if (sessionDrawerVisible) copy(overlay = ShellOverlay.NONE) else this

    /** 抽屉已通过手势或返回键关闭时的回写；覆盖层不是抽屉时返回自身。 */
    fun onSessionDrawerClosed(): ShellNavigation =
        if (sessionDrawerVisible) copy(overlay = ShellOverlay.NONE) else this

    /**
     * 返回键处置。
     *
     * @return 新状态；无处置对象时返回 null。
     */
    fun back(): ShellNavigation? = when {
        overlay != ShellOverlay.NONE -> copy(overlay = ShellOverlay.NONE)
        layers.size > 1 -> copy(layers = layers.dropLast(1))
        else -> null
    }
}
