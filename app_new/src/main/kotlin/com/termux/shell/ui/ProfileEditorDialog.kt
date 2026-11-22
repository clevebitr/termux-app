package com.termux.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.termux.shell.R
import com.termux.shell.session.ProfileKind
import com.termux.shell.session.SessionProfile
import java.util.UUID

/**
 * 会话配置编辑对话框。
 *
 * 目的：在不依赖会话宿主的前提下，让用户先定义「如何启动一个会话」：名称、类型、启动命令、
 * 工作目录、环境变量，以及发行版名称或 SSH 主机。
 *
 * 行为：
 * - 类型为发行版或 SSH 时，额外显示对应必填字段；
 * - 保存时校验名称与类型字段，校验失败在对话框内提示，不关闭对话框；
 * - 环境变量按「每行一条 KEY=VALUE」解析，空行与以 `#` 开头的行被忽略，缺少 `=` 的行被跳过；
 * - 新建配置使用随机标识，编辑时保留原标识。
 *
 * 约束：对话框只负责采集与校验，持久化与标签改名由 [ShellViewModel.saveProfile] 完成。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ProfileEditorDialog(
    initial: SessionProfile?,
    onDismiss: () -> Unit,
    onSave: (SessionProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: ProfileKind.SHELL) }
    var command by remember { mutableStateOf(initial?.command.orEmpty()) }
    var workingDirectory by remember { mutableStateOf(initial?.workingDirectory.orEmpty()) }
    var environment by remember {
        mutableStateOf(
            initial?.environment?.entries
                ?.joinToString("\n") { "${it.key}=${it.value}" }
                .orEmpty(),
        )
    }
    var distro by remember { mutableStateOf(initial?.distro.orEmpty()) }
    var sshHost by remember { mutableStateOf(initial?.sshHost.orEmpty()) }
    var invalid by remember { mutableStateOf(false) }

    fun buildProfile(): SessionProfile? = runCatching {
        SessionProfile(
            id = initial?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            kind = kind,
            command = command.trim(),
            workingDirectory = workingDirectory.trim(),
            environment = parseEnvironment(environment),
            distro = distro.trim(),
            sshHost = sshHost.trim(),
        )
    }.getOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (initial == null) R.string.settings_profile_add else R.string.settings_profile_edit,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            invalid = false
                        },
                        label = { Text(stringResource(R.string.settings_profile_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_profile_kind),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        KindChip(ProfileKind.SHELL, kind, R.string.settings_profile_kind_shell) { kind = it }
                        KindChip(ProfileKind.DISTRO, kind, R.string.settings_profile_kind_distro) { kind = it }
                        KindChip(ProfileKind.SSH, kind, R.string.settings_profile_kind_ssh) { kind = it }
                    }
                    when (kind) {
                        ProfileKind.DISTRO -> OutlinedTextField(
                            value = distro,
                            onValueChange = {
                                distro = it
                                invalid = false
                            },
                            label = { Text(stringResource(R.string.settings_profile_distro)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )

                        ProfileKind.SSH -> OutlinedTextField(
                            value = sshHost,
                            onValueChange = {
                                sshHost = it
                                invalid = false
                            },
                            label = { Text(stringResource(R.string.settings_profile_ssh_host)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )

                        ProfileKind.SHELL -> Unit
                    }
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text(stringResource(R.string.settings_profile_command)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    OutlinedTextField(
                        value = workingDirectory,
                        onValueChange = { workingDirectory = it },
                        label = { Text(stringResource(R.string.settings_profile_cwd)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    OutlinedTextField(
                        value = environment,
                        onValueChange = { environment = it },
                        label = { Text(stringResource(R.string.settings_profile_env)) },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    if (invalid) {
                        Text(
                            text = stringResource(R.string.settings_profile_invalid),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.settings_profile_cancel))
                        }
                        TextButton(
                            onClick = {
                                val profile = buildProfile()
                                if (profile == null) {
                                    invalid = true
                                } else {
                                    onSave(profile)
                                    onDismiss()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.settings_profile_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindChip(
    value: ProfileKind,
    selected: ProfileKind,
    labelRes: Int,
    onSelect: (ProfileKind) -> Unit,
) {
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(stringResource(labelRes)) },
    )
}

/** 解析「每行一条 KEY=VALUE」的环境变量文本；非法行被忽略。 */
private fun parseEnvironment(text: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) {
            continue
        }
        val separator = line.indexOf('=')
        if (separator <= 0) {
            continue
        }
        val key = line.substring(0, separator).trim()
        if (key.isEmpty()) {
            continue
        }
        result[key] = line.substring(separator + 1)
    }
    return result
}
