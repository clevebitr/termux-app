package com.termux.shell.ui;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * 主题属性颜色解析。
 *
 * <p>目的：自绘视图需要从当前主题取得颜色，而 Android 未提供直接取色 API；本类型把解析逻辑集中一处，
 * 避免各视图重复实现并保持回退值一致。
 *
 * <p>行为：优先解析资源引用，其次接受直接颜色值；两者均不成立时返回调用方给出的回退值。
 *
 * <p>约束：必须在有主题的上下文中调用；解析结果不缓存，主题切换后再次调用即得到新颜色。
 */
public final class ThemeColors {

    private ThemeColors() {
    }

    /**
     * 解析主题属性颜色。
     *
     * @param context   带主题的上下文。
     * @param attribute 主题属性，通常取 {@code com.google.android.material.R.attr} 中的颜色属性。
     * @param fallback  属性缺失时的回退颜色。
     */
    @ColorInt
    public static int resolve(@NonNull Context context, @AttrRes int attribute, @ColorInt int fallback) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            return context.getColor(value.resourceId);
        }
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return fallback;
    }
}
