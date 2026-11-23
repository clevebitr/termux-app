package com.termux.shell.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 会话配置写回的线程抽象。
 *
 * 目的：把「在后台线程写盘、回到主线程报告结果」这一模式从状态持有者中分离，
 * 使保存、删除与撤销的流程可以在纯 JVM 测试中用同步实现验证。
 *
 * 约束：实现方必须在完成后调用 [persist] 的回调；回调可能在主线程之外触发。
 */
fun interface ProfilePersister {
    fun persist(operation: () -> Boolean, onResult: (Boolean) -> Unit)
}

/**
 * 会话配置写回管道。
 *
 * 行为：默认实现把 [operation] 放到 IO 协程执行，成功与否回到主线程通过 [onResult] 报告；
 * 操作抛异常时按失败处理，不向调用方传播。
 *
 * 约束：协程作用域由调用方提供并负责取消；本类型不持有 Android 上下文。
 */
class ProfileWriter(
    scope: CoroutineScope,
    persister: ProfilePersister? = null,
) {

    private val persister: ProfilePersister = persister ?: ProfilePersister { operation, onResult ->
        scope.launch {
            val succeeded = runCatching(operation).getOrDefault(false)
            withContext(Dispatchers.Main) { onResult(succeeded) }
        }
    }

    /** 执行一次写回操作，并在完成后报告是否成功。 */
    fun persist(operation: () -> Boolean, onResult: (Boolean) -> Unit) {
        persister.persist(operation, onResult)
    }
}
