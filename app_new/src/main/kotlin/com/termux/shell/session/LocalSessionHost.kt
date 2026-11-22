package com.termux.shell.session

import android.content.Context
import com.termux.shell.log.ShellLog
import com.termux.shell.terminal.TerminalSession
import com.termux.shell.terminal.TerminalSessionClientAdapter
import com.termux.shell.termview.TerminalView
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 进程内会话宿主。
 *
 * 目的：在应用进程内创建并持有伪终端会话，作为外壳与 `terminal-core` 之间的唯一桥梁。
 * 会话生命周期与进程一致，窗口只通过 [SessionId] 引用；视图挂载与解绑由终端视图池负责。
 *
 * 行为：
 * - [createSession] 只构造 [TerminalSession]，不启动进程；首次挂载视图时由终端视图按实际尺寸
 *   调用 `updateSize` 触发 PTY 创建，因此关闭未启动会话不会误杀进程；
 * - 每个会话只允许挂载一个视图，[attachView] 会替换旧视图引用；
 * - 文本变化直接由会话客户端转发给挂载视图重绘；输出字节流不再经过本类型，避免与终端仿真
 *   重复搬运（跨进程宿主的 `Observer.onOutput` 将在运行时拆分后实现）；
 * - 标题、进程号与会话结束通过 [Observer] 通知界面层。
 *
 * 约束：除 [createSession] 外，公开方法均在主线程调用；[Observer] 回调在主线程触发，
 * 实现方不得抛出异常。
 */
class LocalSessionHost(
    private val context: Context?,
    private val environment: SessionEnvironment,
) : SessionProvider {

    private class Entry(
        val id: SessionId,
        val request: SessionHost.SessionRequest,
        val session: TerminalSession,
    ) {
        @Volatile
        var view: TerminalView? = null
    }

    private val entries = LinkedHashMap<String, Entry>()

    private val observers = CopyOnWriteArrayList<SessionHost.Observer>()

    /** 返回会话对应的终端会话实例；不存在时返回 null。视图池据此挂载。 */
    @Synchronized
    override fun terminalSession(id: SessionId): TerminalSession? = entries[id.value]?.session

    /**
     * 把终端视图挂载到会话。
     *
     * @return 是否挂载成功。会话不存在时返回 false。
     */
    @Synchronized
    override fun attachView(id: SessionId, view: TerminalView): Boolean {
        val entry = entries[id.value] ?: return false
        entry.view = view
        return true
    }

    /** 解除视图挂载；仅当传入视图仍是当前挂载视图时生效。 */
    @Synchronized
    override fun detachView(id: SessionId, view: TerminalView) {
        val entry = entries[id.value] ?: return
        if (entry.view === view) {
            entry.view = null
        }
    }

    @Synchronized
    override fun createSession(request: SessionHost.SessionRequest): SessionId? {
        val id = SessionId.of(UUID.randomUUID().toString())
        val client = TerminalSessionClientAdapter(
            context = context,
            viewProvider = { entries[id.value]?.view },
            onTitleChanged = { notifyTitleChanged(id) },
            onSessionFinished = { exitCode -> notifySessionFinished(id, exitCode) },
            onPidChanged = { pid -> ShellLog.debug(SUB_TAG, "会话 $id 进程号为 $pid") },
        )
        val argv = request.argv
        val session = try {
            TerminalSession(
                argv.first(),
                request.workingDirectory ?: environment.homeDirectory,
                argv.drop(1).toTypedArray(),
                request.environment.map { (key, value) -> "$key=$value" }.toTypedArray(),
                TRANSCRIPT_ROWS,
                client,
            )
        } catch (e: Exception) {
            ShellLog.warn(SUB_TAG, "创建会话失败：${e.message}", e)
            return null
        }
        entries[id.value] = Entry(id, request, session)
        ShellLog.info(SUB_TAG, "会话已创建：$id，argv=${request.argv}")
        return id
    }

    @Synchronized
    override fun write(id: SessionId, data: ByteArray, offset: Int, length: Int) {
        entries[id.value]?.session?.write(data, offset, length)
    }

    @Synchronized
    override fun resize(id: SessionId, columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        entries[id.value]?.session?.updateSize(columns, rows, cellWidthPixels, cellHeightPixels)
    }

    override fun closeSession(id: SessionId) {
        val entry = synchronized(this) { entries.remove(id.value) } ?: return
        entry.view = null
        // 只有已启动的会话才发送 SIGKILL：未启动会话的 pid 为 0，直接发送会误伤当前进程组。
        val pid = entry.session.pid
        if (pid > 0) {
            runCatching { entry.session.finishIfRunning() }
                .onFailure { ShellLog.warn(SUB_TAG, "关闭会话失败：$id", it) }
        }
        ShellLog.info(SUB_TAG, "会话已关闭：$id")
    }

    @Synchronized
    override fun describe(id: SessionId): SessionDescriptor? {
        val entry = entries[id.value] ?: return null
        return descriptorOf(entry)
    }

    @Synchronized
    override fun describeAll(): List<SessionDescriptor> = entries.values.map { descriptorOf(it) }

    override fun addObserver(observer: SessionHost.Observer) {
        observers.addIfAbsent(observer)
    }

    override fun removeObserver(observer: SessionHost.Observer) {
        observers.remove(observer)
    }

    private fun descriptorOf(entry: Entry): SessionDescriptor = SessionDescriptor(
        id = entry.id,
        title = entry.session.title,
        workingDirectory = entry.request.workingDirectory,
        pid = entry.session.pid,
        // 未挂载视图时 pid 为 0，此时进程尚未启动，不能按 isRunning 判定。
        running = entry.session.pid > 0 && entry.session.isRunning,
    )

    private fun notifyTitleChanged(id: SessionId) {
        val title = terminalSession(id)?.title
        for (observer in observers) {
            runCatching { observer.onTitleChanged(id, title) }
        }
    }

    private fun notifySessionFinished(id: SessionId, exitCode: Int) {
        for (observer in observers) {
            runCatching { observer.onExit(id, exitCode) }
        }
    }

    private companion object {
        const val SUB_TAG = "LocalSessionHost"
        const val TRANSCRIPT_ROWS = 5000
    }
}
