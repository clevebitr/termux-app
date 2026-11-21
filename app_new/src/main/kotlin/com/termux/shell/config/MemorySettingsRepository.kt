package com.termux.shell.config

/**
 * 内存实现，用于单元测试与骨架阶段的注入。
 *
 * 约束：本实现不做持久化，进程退出后内容丢失；生产路径使用 [PropertiesSettingsRepository]。
 */
class MemorySettingsRepository(initialValues: Map<String, Any> = emptyMap()) : SettingsRepository {

    private val values: MutableMap<String, Any> = HashMap(initialValues)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val value = values[key]
        return value as? Boolean ?: defaultValue
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        val value = values[key]
        return value as? Int ?: defaultValue
    }

    override fun getString(key: String, defaultValue: String?): String? {
        val value = values[key]
        return value as? String ?: defaultValue
    }

    override fun keys(): Set<String> = values.keys.toSet()

    override fun put(key: String, value: String): Boolean {
        val entry = SettingsSpec.find(key) ?: return false
        val normalized = entry.normalize(value) ?: return false
        values[key] = normalized
        return true
    }

    fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    fun putInt(key: String, value: Int) {
        values[key] = value
    }

    fun putString(key: String, value: String) {
        values[key] = value
    }
}
