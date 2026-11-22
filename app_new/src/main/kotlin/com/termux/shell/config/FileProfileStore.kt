package com.termux.shell.config

import com.termux.shell.session.SessionProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * 基于 JSON 文件的会话配置存储。
 *
 * 目的：把会话配置保存在 `~/.termux/shell-profiles.json`，并以 [StateFlow] 暴露快照，
 * 使多个窗口共享同一份列表，并在任一窗口修改后同步刷新。
 *
 * 行为：
 * - 文件不存在时视为空列表，不主动写入模板；
 * - 写入采用「临时文件 + 重命名」，避免写到一半留下残缺文件；
 * - 读取或解析失败时保留旧列表并返回 false，由调用方提示用户。
 *
 * 约束：与 [PropertiesSettingsRepository] 相同，读写方法会访问磁盘，必须在后台线程调用。
 */
class FileProfileStore(private val file: File) : ProfileStore {

    private val state = MutableStateFlow<List<SessionProfile>>(emptyList())

    override val profiles: StateFlow<List<SessionProfile>> = state.asStateFlow()

    init {
        reload()
    }

    @Synchronized
    override fun save(profile: SessionProfile): Boolean {
        val current = state.value
        val existingIndex = current.indexOfFirst { it.id == profile.id }
        val updated = if (existingIndex >= 0) {
            current.toMutableList().apply { set(existingIndex, profile) }
        } else {
            current + profile
        }
        if (!write(updated)) {
            return false
        }
        state.value = updated
        return true
    }

    @Synchronized
    override fun delete(id: String): Boolean {
        val current = state.value
        val updated = current.filterNot { it.id == id }
        if (updated.size == current.size) {
            return false
        }
        if (!write(updated)) {
            return false
        }
        state.value = updated
        return true
    }

    @Synchronized
    override fun reload(): Boolean {
        if (!file.isFile) {
            state.value = emptyList()
            return true
        }
        val document = try {
            file.readText(StandardCharsets.UTF_8)
        } catch (e: IOException) {
            return false
        }
        val decoded = ProfileCodec.decode(document) ?: return false
        state.value = decoded
        return true
    }

    private fun write(profiles: List<SessionProfile>): Boolean {
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            return false
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        return try {
            FileOutputStream(temp).use { output ->
                output.write(ProfileCodec.encode(profiles).toByteArray(StandardCharsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            if (file.exists() && !file.delete()) {
                temp.delete()
                return false
            }
            if (!temp.renameTo(file)) {
                temp.delete()
                return false
            }
            true
        } catch (e: IOException) {
            temp.delete()
            false
        }
    }
}
