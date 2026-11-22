package com.termux.shell.config

import com.termux.shell.session.ProfileKind
import com.termux.shell.session.SessionProfile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 会话配置的 JSON 编解码器。
 *
 * 格式：`{"version":1,"profiles":[{"id":"…","name":"…","kind":"SHELL","command":"…",
 * "workingDirectory":"…","environment":{"KEY":"VALUE"},"distro":"…","sshHost":"…"}]}`。
 *
 * 约束：解码失败（文本非法、版本不符、任一条目校验失败）统一返回 null，由调用方保留旧列表；
 * 未知字段被忽略，便于后续扩展。
 */
object ProfileCodec {

    const val CURRENT_VERSION = 1

    private const val KEY_VERSION = "version"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_KIND = "kind"
    private const val KEY_COMMAND = "command"
    private const val KEY_WORKING_DIRECTORY = "workingDirectory"
    private const val KEY_ENVIRONMENT = "environment"
    private const val KEY_DISTRO = "distro"
    private const val KEY_SSH_HOST = "sshHost"

    fun encode(profiles: List<SessionProfile>): String {
        val document = JSONObject()
        document.put(KEY_VERSION, CURRENT_VERSION)
        val array = JSONArray()
        for (profile in profiles) {
            val item = JSONObject()
            item.put(KEY_ID, profile.id)
            item.put(KEY_NAME, profile.name)
            item.put(KEY_KIND, profile.kind.name)
            item.put(KEY_COMMAND, profile.command)
            item.put(KEY_WORKING_DIRECTORY, profile.workingDirectory)
            val environment = JSONObject()
            for ((key, value) in profile.environment) {
                environment.put(key, value)
            }
            item.put(KEY_ENVIRONMENT, environment)
            item.put(KEY_DISTRO, profile.distro)
            item.put(KEY_SSH_HOST, profile.sshHost)
            array.put(item)
        }
        document.put(KEY_PROFILES, array)
        return document.toString()
    }

    fun decode(document: String?): List<SessionProfile>? {
        if (document.isNullOrBlank()) {
            return null
        }
        return try {
            val json = JSONObject(document)
            if (json.optInt(KEY_VERSION, -1) != CURRENT_VERSION) {
                return null
            }
            val array = json.optJSONArray(KEY_PROFILES) ?: return null
            val profiles = ArrayList<SessionProfile>(array.length())
            val ids = HashSet<String>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: return null
                val profile = decodeProfile(item) ?: return null
                if (!ids.add(profile.id)) {
                    return null
                }
                profiles += profile
            }
            profiles
        } catch (e: JSONException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun decodeProfile(item: JSONObject): SessionProfile? {
        val kind = when (item.optString(KEY_KIND, ProfileKind.SHELL.name)) {
            ProfileKind.SHELL.name -> ProfileKind.SHELL
            ProfileKind.DISTRO.name -> ProfileKind.DISTRO
            ProfileKind.SSH.name -> ProfileKind.SSH
            else -> return null
        }
        val environmentJson = item.optJSONObject(KEY_ENVIRONMENT)
        val environment = LinkedHashMap<String, String>()
        if (environmentJson != null) {
            for (key in environmentJson.keys()) {
                environment[key] = environmentJson.optString(key, "")
            }
        }
        return SessionProfile(
            id = item.optString(KEY_ID, ""),
            name = item.optString(KEY_NAME, ""),
            kind = kind,
            command = item.optString(KEY_COMMAND, ""),
            workingDirectory = item.optString(KEY_WORKING_DIRECTORY, ""),
            environment = environment,
            distro = item.optString(KEY_DISTRO, ""),
            sshHost = item.optString(KEY_SSH_HOST, ""),
        )
    }
}
