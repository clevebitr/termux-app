package com.termux.shell.config

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Properties

/**
 * 基于属性文件的配置实现。
 *
 * 目的：以外壳自己的属性文件作为唯一配置来源，替代旧模块中属性文件、`PreferenceDataStore`
 * 与 `SharedPreferences` 三条通路并存的结构。
 *
 * 行为：
 * - 构造时同步读取一次文件形成内存快照，之后全部读取操作只访问快照；
 * - 文件不存在时写入带说明的默认模板，随后按默认值运行；
 * - 读取时按键定义归一化取值，非法取值被忽略并回退到调用方给出的默认值，键名记入非法列表；
 * - 文件中的未知键保留在快照中，便于诊断，但不参与界面构建。
 *
 * 约束：[reload] 会访问磁盘，必须由调用方在后台线程或初始化阶段调用；其余方法可在主线程调用。
 */
class PropertiesSettingsRepository private constructor(private val file: File) : SettingsRepository {

    @Volatile
    private var values: Map<String, String> = emptyMap()

    @Volatile
    private var invalidKeys: List<String> = emptyList()

    /** 返回最近一次读取中被判定为非法的键名，供日志输出。 */
    fun getInvalidKeys(): List<String> = invalidKeys

    /**
     * 重新读取配置文件并替换内存快照。
     *
     * @return 是否读取成功。文件不存在或读取失败时返回 false，快照保持不变。
     */
    @Synchronized
    fun reload(): Boolean {
        if (!file.isFile) {
            return false
        }
        val properties = Properties()
        try {
            FileInputStream(file).use { input: InputStream -> properties.load(input) }
        } catch (e: IOException) {
            return false
        }
        val newValues = HashMap<String, String>()
        val newInvalid = ArrayList<String>()
        for (key in properties.stringPropertyNames()) {
            val raw = properties.getProperty(key)
            val entry = SettingsSpec.find(key)
            if (entry == null) {
                newValues[key] = raw?.trim().orEmpty()
                continue
            }
            val normalized = entry.normalize(raw)
            if (normalized == null) {
                newInvalid += key
            } else {
                newValues[key] = normalized
            }
        }
        values = newValues
        invalidKeys = newInvalid
        return true
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = when (values[key]) {
        "true" -> true
        "false" -> false
        else -> defaultValue
    }

    override fun getInt(key: String, defaultValue: Int): Int =
        values[key]?.trim()?.toIntOrNull() ?: defaultValue

    override fun getString(key: String, defaultValue: String?): String? {
        val raw = values[key] ?: return defaultValue
        val entry = SettingsSpec.find(key) ?: return raw
        return entry.normalize(raw) ?: defaultValue
    }

    override fun keys(): Set<String> = values.keys.toSet()

    /**
     * 写入单项配置并立即落盘。
     *
     * 行为：先按配置项定义归一化取值，随后重写配置文件，仅替换该键所在行，其余行（含注释与未知键）
     * 原样保留；写入采用「临时文件 + 重命名」，避免写到一半时留下残缺文件。
     *
     * 约束：会访问磁盘，必须由调用方在后台线程调用；写入失败时内存快照保持不变。
     */
    @Synchronized
    override fun put(key: String, value: String): Boolean {
        val entry = SettingsSpec.find(key) ?: return false
        val normalized = entry.normalize(value) ?: return false
        if (values[key] == normalized) {
            return true
        }
        if (!writeValue(key, normalized)) {
            return false
        }
        values = HashMap(values).apply { put(key, normalized) }
        return true
    }

    /** 把单项写入替换到已有行；文件缺少该键时追加到末尾。写入通过原子替换完成。 */
    private fun writeValue(key: String, value: String): Boolean {
        val lines = try {
            if (file.isFile) file.readLines(StandardCharsets.UTF_8) else emptyList()
        } catch (e: IOException) {
            return false
        }
        val prefix = "$key="
        var replaced = false
        val newLines = ArrayList<String>(lines.size + 1)
        for (line in lines) {
            if (!replaced && line.startsWith(prefix)) {
                newLines += prefix + value
                replaced = true
            } else {
                newLines += line
            }
        }
        if (!replaced) {
            newLines += prefix + value
        }
        return AtomicFileReplace.writeText(file, newLines.joinToString("\n", postfix = "\n"))
    }

    companion object {

        /**
         * 打开配置文件并完成首次读取。
         *
         * 文件不存在时写入默认模板。写入或读取失败不抛出异常，方法返回按默认值运行的实例，
         * 调用方可通过 [PropertiesSettingsRepository] 的日志与 [getInvalidKeys] 判断是否需要提示用户。
         */
        fun open(file: File): PropertiesSettingsRepository {
            val repository = PropertiesSettingsRepository(file)
            if (!file.isFile) {
                writeDefaultTemplate(file)
            }
            repository.reload()
            return repository
        }

        private fun writeDefaultTemplate(file: File) {
            val parent = file.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                return
            }
            try {
                OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8).use { writer ->
                    writer.write(SettingsSpec.buildDefaultFileContent())
                }
            } catch (e: IOException) {
                // 写入失败时按默认值运行；下次启动会重试写入。
            }
        }
    }
}
