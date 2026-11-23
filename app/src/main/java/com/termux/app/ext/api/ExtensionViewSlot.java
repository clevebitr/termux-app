package com.termux.app.ext.api;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

/**
 * 目的 — 声明布局中的一块区域归某个扩展所有，使「谁接管了哪个视图」成为可登记、可校验的事实。
 *
 * <p>行为 — 扩展在 {@link Extension#slots()} 中列出自己接管的视图；宿主据此判断某块区域是否由扩展提供，
 * 禁用时该区域回到宿主默认（隐藏或不占空间），而不是留下一段无人管理的布局。{@link #viewName} 是布局文件
 * 中 {@code @+id} 的名字，守卫测试用它核对槽位确实存在，避免资源 id 改名后静默失效。</p>
 *
 * <p>约束 — 一个视图只能归一个扩展，{@code ExtensionRegistrySourceTest} 会拒绝重复声明；扩展不得接管宿主
 * 自己的核心视图（终端视图、窗格容器的结构、抽屉布局），只能接管以装饰或叠层为目的的区域。</p>
 */
public final class ExtensionViewSlot {

    /** The id of the view in the layout, which the extension receives from {@link ExtensionContext#view}. */
    @IdRes
    public final int viewId;

    /**
     * The name of the id in the layout resource, like {@code status_bar_view}.
     *
     * <p>Duplicated from {@link #viewId} because a resource id cannot be turned back into a name at test time
     * without an Android runtime, and a guard test that cannot read the name cannot catch a renamed view.</p>
     */
    @NonNull
    public final String viewName;

    /** What the region is, shown in diagnostics and in the extensions settings page. */
    @NonNull
    public final String description;

    public ExtensionViewSlot(@IdRes int viewId, @NonNull String viewName, @NonNull String description) {
        this.viewId = viewId;
        this.viewName = viewName;
        this.description = description;
    }
}
