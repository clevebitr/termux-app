package com.termux.app.ext.api;

/**
 * 目的 — 让扩展请求宿主重新计算装饰区域的布局，而不必知道宿主是哪个 Activity。
 *
 * <p>行为 — 扩展改变了所占区域的高度或可见性后调用 {@link #requestExtensionLayout()}，宿主重算窗格的内边距、
 * 叠层的位置等只由宿主掌握的信息。</p>
 *
 * <p>约束 — 由宿主 Activity 实现，{@link ExtensionContext#requestExtensionLayout()} 会在宿主未实现时静默跳过，
 * 因此扩展不需要判断宿主类型。</p>
 */
public interface ExtensionLayoutHost {

    /** Re-run the layout of the regions that the extensions decorate. */
    void requestExtensionLayout();
}
