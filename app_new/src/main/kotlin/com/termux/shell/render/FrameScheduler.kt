package com.termux.shell.render

/**
 * 帧调度契约。
 *
 * 目的：把「输出到达」与「屏幕刷新」解耦。终端输出可能在单帧内到达多次，直接逐次重绘会造成
 * 无效绘制与撕裂；本契约要求实现方把一帧内的多次请求合并为一次提交。
 *
 * 实现需同时满足：
 * - 按 [DirtyRows] 只提交受影响的区域；
 * - 同步输出（`DECSET 2026`）置位期间累积请求，复位后一次性提交；
 * - 同步输出未在限定时间内复位时强制提交，避免画面停留在半帧状态。
 *
 * 约束：全部方法运行在主线程。
 */
interface FrameScheduler {

    /** 请求在下一帧重绘指定行区间。 */
    fun requestRender(rows: DirtyRows)

    /** 进入同步输出状态。该状态下不得提交任何帧。 */
    fun beginSynchronizedUpdate()

    /** 退出同步输出状态并提交累积的脏行区间。 */
    fun endSynchronizedUpdate()

    /** 立即提交全部累积的脏行区间。用于同步输出超时、尺寸变化与主题切换等必须立即生效的场景。 */
    fun flush()
}
