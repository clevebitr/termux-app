package com.termux.shell.render;

import androidx.annotation.NonNull;

/**
 * 帧调度契约。
 *
 * <p>目的：把「输出到达」与「屏幕刷新」解耦。终端输出可能在单帧内到达多次，直接逐次重绘会造成
 * 无效绘制与撕裂；本契约要求实现方把一帧内的多次请求合并为一次提交。
 *
 * <p>骨架阶段仅定义契约，实现将随里程碑 M12 提供，需同时满足：
 * <ul>
 *     <li>按 {@link DirtyRows} 只提交受影响的区域；</li>
 *     <li>同步输出（{@code DECSET 2026}）置位期间累积请求，复位后一次性提交；</li>
 *     <li>同步输出未在限定时间内复位时强制提交，避免画面停留在半帧状态。</li>
 * </ul>
 *
 * <p>约束：全部方法运行在主线程。
 */
public interface FrameScheduler {

    /**
     * 请求在下一帧重绘指定行区间。
     */
    void requestRender(@NonNull DirtyRows rows);

    /**
     * 进入同步输出状态。该状态下不得提交任何帧。
     */
    void beginSynchronizedUpdate();

    /**
     * 退出同步输出状态并提交累积的脏行区间。
     */
    void endSynchronizedUpdate();

    /**
     * 立即提交全部累积的脏行区间。用于同步输出超时、尺寸变化与主题切换等必须立即生效的场景。
     */
    void flush();
}
