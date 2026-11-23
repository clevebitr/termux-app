package com.termux.app.ext.api;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;

/**
 * 目的 — 声明一个设置页归属于某个扩展。
 *
 * <p>行为 — 宿主用这份声明构建设置搜索的页面表：禁用扩展后该页的条目不再出现在搜索结果里，而扩展自身
 * 不必在搜索索引中登记一个字面量。页面在搜索结果里的标题取这里的 {@link #titleRes}，与扩展标题无关，
 * 因此一个扩展可以有多个标题不同的页面。</p>
 *
 * <p>约束 — {@link #fragmentClassName} 必须指向一个真实存在、并且会自行检查所属扩展是否启用的
 * {@code PreferenceFragmentCompat}；单纯隐藏入口不构成禁用，任何进入该页的路径都必须被页面自己拒绝。</p>
 */
public final class ExtensionSettingsPage {

    /** The title of the page, which the search shows as the page of a result. */
    @StringRes
    public final int titleRes;

    /** The XML resource that the fragment inflates. */
    @XmlRes
    public final int xmlRes;

    /** The class name of the {@code PreferenceFragmentCompat} that shows the page. */
    @NonNull
    public final String fragmentClassName;

    public ExtensionSettingsPage(@StringRes int titleRes, @XmlRes int xmlRes,
                                 @NonNull String fragmentClassName) {
        this.titleRes = titleRes;
        this.xmlRes = xmlRes;
        this.fragmentClassName = fragmentClassName;
    }
}
