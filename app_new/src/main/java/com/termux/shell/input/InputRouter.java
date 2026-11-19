package com.termux.shell.input;

import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

/**
 * 输入路由契约。
 *
 * <p>目的：为键盘、指针与拖放提供统一的入口，使外壳快捷键、窗格导航与终端键序编码的优先级在一处判定。
 *
 * <p>优先级约定（实现方必须遵守）：
 * <ol>
 *     <li>外壳级快捷键（由 {@link KeymapTable} 解析）优先于终端键序编码；</li>
 *     <li>外壳级快捷键被禁用时，全部按键交予终端键序编码处理；</li>
 *     <li>拖放事件不得被终端键序编码处理。</li>
 * </ol>
 *
 * <p>约束：全部方法运行在主线程，且必须返回是否已消费该事件。
 */
public interface InputRouter {

    boolean onKeyDown(@NonNull KeyEvent event);

    boolean onKeyUp(@NonNull KeyEvent event);

    /**
     * 处理滚轮、触控笔与鼠标移动等通用运动事件。
     */
    boolean onGenericMotion(@NonNull MotionEvent event);

    boolean onDrop(@NonNull DragEvent event);
}
