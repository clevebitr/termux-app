package com.termux.shell.config

/**
 * 外壳配置访问契约。
 *
 * 目的：统一配置读取入口。旧模块同时存在 `termux.properties` 文件、`PreferenceDataStore`
 * 与 `SharedPreferences` 三条通路，读写时机不同导致默认值回写等问题（详见 `DECISIONS.md`）。
 * 新外壳只保留本契约，实现层负责快照与持久化。
 *
 * 约束：实现必须保证读取为内存操作，不得在界面线程执行磁盘读写。
 */
interface SettingsRepository {

    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun getInt(key: String, defaultValue: Int): Int

    fun getString(key: String, defaultValue: String?): String?

    /** 返回当前快照中出现过的全部键名，用于设置页枚举与调试输出。 */
    fun keys(): Set<String>
}
