package com.termux.shell.session

/**
 * 会话宿主契约。
 *
 * 目的：把「终端界面」与「进程与伪终端」彻底分离。骨架阶段由同一进程内的实现承担；
 * 运行时拆分完成后，实现将位于独立进程，本契约即为两进程之间的通信边界。
 *
 * 约束：
 * - 全部方法必须可在任意线程调用，实现方负责线程切换与串行化；
 * - 不得在回调中直接操作视图，观察者需自行切换到主线程；
 * - 输出回调携带的字节数组由实现方复用，观察者必须在返回前完成拷贝。
 */
interface SessionHost {

    /**
     * 创建会话。
     *
     * @return 新会话标识；创建失败时返回 null，失败原因通过日志输出。
     */
    fun createSession(request: SessionRequest): SessionId?

    /** 向会话写入数据。 */
    fun write(id: SessionId, data: ByteArray, offset: Int, length: Int)

    /**
     * 调整会话的终端尺寸。
     *
     * 像素尺寸用于支持以像素为单位定位的图形协议与鼠标上报，缺失时相关功能将退化为字符网格定位。
     */
    fun resize(id: SessionId, columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int)

    /** 关闭会话。实现方应保证重复调用安全。 */
    fun closeSession(id: SessionId)

    fun describe(id: SessionId): SessionDescriptor?

    fun describeAll(): List<SessionDescriptor>

    fun addObserver(observer: Observer)

    fun removeObserver(observer: Observer)

    /**
     * 会话创建参数。
     *
     * 约束：[argv] 至少包含一项；环境变量在运行时侧与默认环境合并，同名键覆盖默认值。
     */
    data class SessionRequest(
        val workingDirectory: String?,
        val argv: List<String>,
        val environment: Map<String, String>,
    ) {
        init {
            require(argv.isNotEmpty()) { "argv 不得为空" }
        }
    }

    /**
     * 会话观察者。
     *
     * 约束：回调可能在任意线程触发，且不得抛出异常；抛出异常将中断实现方的通知循环。
     */
    interface Observer {
        fun onOutput(id: SessionId, data: ByteArray, length: Int)

        fun onExit(id: SessionId, exitCode: Int)

        fun onTitleChanged(id: SessionId, title: String?)
    }
}
