package com.termux.shell.ui

import com.termux.shell.config.ConfigSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置落盘抽象。
 *
 * 目的：把「写回配置文件」的线程调度与失败回调从状态持有者中分离，使生产实现把磁盘访问放到
 * IO 协程，单元测试可传入同步实现。
 *
 * 约束：实现方必须在失败时调用 [persist] 的回调，且回调可在任意线程触发。
 */
fun interface SettingsPersister {
    fun persist(key: String, value: String, onFailure: () -> Unit)
}

/**
 * 界面设置的写回管道。
 *
 * 目的：集中设置写入的线程策略：默认实现把写盘放到 IO 协程，失败时回到主线程通知界面；
 * 调用方只负责先更新内存状态并给出失败提示。
 *
 * 行为：[persist] 本身不阻塞调用方；[ConfigSource.settings] 的写入失败时回调 [onFailure]。
 *
 * 约束：本类型不持有 Android 上下文，可在 JVM 单元测试中以同步 [SettingsPersister] 替代；
 * 协程作用域由调用方提供并负责取消。
 */
class SettingsWriter(
    private val configSource: ConfigSource,
    private val scope: CoroutineScope,
    persister: SettingsPersister? = null,
) {

    private val persister: SettingsPersister = persister ?: SettingsPersister { key, value, onFailure ->
        scope.launch {
            val saved = runCatching { configSource.settings().put(key, value) }.getOrDefault(false)
            if (!saved) {
                withContext(Dispatchers.Main) { onFailure() }
            }
        }
    }

    /** 异步写回单项设置；失败时在主线程调用 [onFailure]。 */
    fun persist(key: String, value: String, onFailure: () -> Unit) {
        persister.persist(key, value, onFailure)
    }
}
