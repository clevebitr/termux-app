package com.termux.shell.config

import com.termux.shell.input.KeymapTable

/**
 * 外壳配置与键位表的访问入口。
 *
 * 目的：把「配置从哪里来」与「界面如何消费配置」分开，使窗口状态持有者只依赖本契约，
 * 不直接引用进程入口的静态字段，也不持有 Android 上下文，从而可以在 JVM 单元测试中替换为假实现。
 *
 * 行为：
 * - [settings] 与 [keymap] 返回当前快照，调用方不得跨 [reload] 缓存；
 * - [reload] 重新读取配置文件与键位表并返回是否成功；
 * - 两个路径用于设置页展示，避免界面自行拼接路径。
 *
 * 约束：本接口不涉及 Android 资源与界面对象；[reload] 会访问磁盘，须由调用方在允许磁盘访问的时机触发。
 */
interface ConfigSource {

    fun settings(): SettingsRepository

    fun keymap(): KeymapTable

    /** 会话配置存储。多个窗口共享同一实例，列表变化通过其状态流同步。 */
    fun profiles(): ProfileStore

    fun reload(): Boolean

    val configFilePath: String

    val keymapFilePath: String
}
