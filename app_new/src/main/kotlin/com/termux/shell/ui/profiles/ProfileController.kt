package com.termux.shell.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.shell.config.ConfigSource
import com.termux.shell.session.SessionProfile
import com.termux.shell.ui.ProfilePersister
import com.termux.shell.ui.ProfileWriter
import com.termux.shell.ui.ShellNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * 会话配置的状态持有者。
 *
 * 目的：把配置列表的读取、保存、删除与撤销从窗口状态持有者中分离，
 * 使跨窗口共享的配置状态与标签改名等界面联动各自有明确入口。
 *
 * 行为：
 * - [profiles] 直接暴露存储层的共享状态流，任一窗口修改后其它窗口自动刷新；
 * - [save] 成功后通过 [onSaved] 让调用方同步标签标题；失败发布提示；
 * - [delete] 成功后记录被删配置并通过 [onUnbound] 清除标签绑定，提示条据此提供撤销；
 * - [undoDelete] 把最近一次被删配置重新写回，重复调用安全。
 *
 * 约束：写盘线程由 [ProfileWriter] 决定；本类型不持有 Android 上下文与视图。
 */
class ProfileController(
    configSource: ConfigSource,
    scope: CoroutineScope,
    persister: ProfilePersister?,
    private val onSaved: (SessionProfile) -> Unit,
    private val onUnbound: (String) -> Unit,
    private val onNotice: (ShellNotice) -> Unit,
) {

    private val writer = ProfileWriter(scope, persister)

    private val store = configSource.profiles()

    /** 会话配置列表。所有窗口共享同一状态流。 */
    val profiles: StateFlow<List<SessionProfile>> = store.profiles

    /** 最近一次被删除的配置，供提示条撤销；无待撤销项时为 null。 */
    var undoableDelete: SessionProfile? by mutableStateOf(null)
        private set

    /** 按标识查找配置；不存在时返回 null。 */
    fun profileById(profileId: String?): SessionProfile? =
        profileId?.let { id -> profiles.value.firstOrNull { it.id == id } }

    /** 保存配置并在成功后同步标签标题。 */
    fun save(profile: SessionProfile) {
        writer.persist({ store.save(profile) }) { saved ->
            if (!saved) {
                onNotice(ShellNotice.PROFILE_SAVE_FAILED)
                return@persist
            }
            onSaved(profile)
        }
    }

    /** 删除配置，记录可撤销项并清除标签绑定。 */
    fun delete(profileId: String) {
        val removed = profileById(profileId)
        writer.persist({ store.delete(profileId) }) { deleted ->
            if (!deleted) {
                onNotice(ShellNotice.PROFILE_DELETE_FAILED)
                return@persist
            }
            onUnbound(profileId)
            undoableDelete = removed
            onNotice(ShellNotice.PROFILE_DELETED)
        }
    }

    /** 撤销最近一次删除。 */
    fun undoDelete() {
        val profile = undoableDelete ?: return
        undoableDelete = null
        save(profile)
    }
}
