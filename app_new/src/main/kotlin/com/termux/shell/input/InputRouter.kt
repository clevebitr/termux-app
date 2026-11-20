package com.termux.shell.input

import android.view.DragEvent
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * 输入路由契约。
 *
 * 目的：为键盘、指针与拖放提供统一入口，使外壳快捷键、窗格导航与终端键序编码的优先级在一处判定。
 *
 * 优先级约定（实现方必须遵守）：
 * 1. 外壳级快捷键（由 [KeymapTable] 解析）优先于终端键序编码；
 * 2. 外壳级快捷键被禁用时，全部按键交予终端键序编码处理；
 * 3. 拖放事件不得被终端键序编码处理。
 *
 * 约束：全部方法运行在主线程，且必须返回是否已消费该事件。
 */
interface InputRouter {

    fun onKeyDown(event: KeyEvent): Boolean

    fun onKeyUp(event: KeyEvent): Boolean

    /** 处理滚轮、触控笔与鼠标移动等通用运动事件。 */
    fun onGenericMotion(event: MotionEvent): Boolean

    fun onDrop(event: DragEvent): Boolean
}
