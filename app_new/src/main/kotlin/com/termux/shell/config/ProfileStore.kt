package com.termux.shell.config

import com.termux.shell.session.SessionProfile
import kotlinx.coroutines.flow.StateFlow

/**
 * 会话配置存储契约。
 *
 * 目的：让所有窗口共享同一份会话配置列表。实现负责持久化并暴露可订阅的只读快照，
 * 因此在一个窗口新增或修改配置后，其他窗口无需重载即可看到变化。
 *
 * 约束：
 * - 读写方法会访问磁盘，必须由调用方在后台线程执行；
 * - [save] 与 [delete] 成功后必须更新 [profiles]；失败时保持原值并返回 false；
 * - 列表内容不可变，读取方不得依赖对象同一性。
 */
interface ProfileStore {

    /** 当前配置列表。界面通过订阅该状态实现跨窗口同步。 */
    val profiles: StateFlow<List<SessionProfile>>

    /**
     * 新增或按标识覆盖一条配置。
     *
     * @return 是否写入成功。校验失败、磁盘写入失败或标识冲突以外的异常均为 false。
     */
    fun save(profile: SessionProfile): Boolean

    /**
     * 删除指定配置。
     *
     * @return 是否实际删除。标识不存在时返回 false。
     */
    fun delete(id: String): Boolean

    /**
     * 从磁盘重新读取。读取失败时保持旧列表并返回 false。
     */
    fun reload(): Boolean
}
