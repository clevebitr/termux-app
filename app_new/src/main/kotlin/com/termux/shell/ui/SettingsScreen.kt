package com.termux.shell.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.shell.R
import com.termux.shell.session.ProfileKind
import com.termux.shell.session.SessionProfile
import com.termux.shell.ui.settings.ProfilesBlock
import com.termux.shell.ui.settings.SectionBlock
import com.termux.shell.ui.settings.SettingsBlock
import com.termux.shell.ui.settings.SettingsItem
import com.termux.shell.ui.settings.SettingsItemRow
import com.termux.shell.ui.settings.SettingsSectionCard
import com.termux.shell.ui.settings.ShellSettingsCatalog

/** 宽屏下设置页改为左右两栏的宽度阈值。 */
private val WideSettingsBreakpoint = 840.dp

/** 两栏模式下的最大内容宽度，避免超宽窗口中出现过长的行。 */
private val SettingsColumnMaxWidth = 720.dp

/**
 * 设置页。
 *
 * 目的：把配置文件中的界面与输入选项呈现为可搜索、可分组的 MD3 设置页，并保留会话配置的增删改入口。
 *
 * 行为：
 * - 条目来自 [ShellSettingsCatalog]，界面只负责渲染、搜索与本地对话框状态；
 * - 搜索按标题与说明做不区分大小写的子串匹配，命中后只保留包含命中项的分组；会话配置属于操作型区块，
 *   搜索期间隐藏，避免与命中项混排；
 * - 宽度不小于 840dp 时按显示块顺序交替排入左右两栏，窄屏保持单栏滚动；
 * - 删除会话配置前弹出确认对话框，确认后才调用控制器；
 * - 页面作为主界面之上的覆盖层渲染，因此不改动主界面与终端视图的生命周期。
 *
 * 约束：本页不缓存配置值，全部取值来自控制器状态，外部重载配置后界面随之刷新。
 */
@Composable
fun SettingsScreen(controller: ShellViewModel) {
    val profiles by controller.profiles.collectAsState()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var editingProfile by remember { mutableStateOf<SessionProfile?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SessionProfile?>(null) }

    val blocks = ShellSettingsCatalog.blocks(controller, profiles)
    // 显示块与查询一起决定渲染结果；条目数量很小，不做记忆化，避免状态变化时遗漏刷新。
    val visibleBlocks = filterBlocks(context, blocks, query)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SettingsHeader(controller)
        SettingsSearchField(query = query, onQueryChange = { query = it })
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (visibleBlocks.isEmpty()) {
                EmptySearchResult()
            } else {
                val wide = maxWidth >= WideSettingsBreakpoint
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        SettingsColumn(
                            blocks = visibleBlocks.filterIndexed { index, _ -> index % 2 == 0 },
                            profiles = profiles,
                            onAddProfile = {
                                editingProfile = null
                                editorVisible = true
                            },
                            onEditProfile = {
                                editingProfile = it
                                editorVisible = true
                            },
                            onDeleteProfile = { pendingDelete = it },
                            modifier = Modifier.weight(1f),
                        )
                        SettingsColumn(
                            blocks = visibleBlocks.filterIndexed { index, _ -> index % 2 == 1 },
                            profiles = profiles,
                            onAddProfile = {
                                editingProfile = null
                                editorVisible = true
                            },
                            onEditProfile = {
                                editingProfile = it
                                editorVisible = true
                            },
                            onDeleteProfile = { pendingDelete = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    SettingsColumn(
                        blocks = visibleBlocks,
                        profiles = profiles,
                        onAddProfile = {
                            editingProfile = null
                            editorVisible = true
                        },
                        onEditProfile = {
                            editingProfile = it
                            editorVisible = true
                        },
                        onDeleteProfile = { pendingDelete = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (editorVisible) {
        ProfileEditorDialog(
            initial = editingProfile,
            onDismiss = { editorVisible = false },
            onSave = controller::saveProfile,
        )
    }

    val deleting = pendingDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.settings_profile_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.settings_profile_delete_confirm_message, deleting.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.deleteProfile(deleting.id)
                        pendingDelete = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_profile_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

/** 设置页内容列。宽屏两栏与窄屏单栏共用同一渲染逻辑。 */
@Composable
private fun SettingsColumn(
    blocks: List<SettingsBlock>,
    profiles: List<SessionProfile>,
    onAddProfile: () -> Unit,
    onEditProfile: (SessionProfile) -> Unit,
    onDeleteProfile: (SessionProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = SettingsColumnMaxWidth)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(blocks, key = { block -> block.key }) { block ->
                when (block) {
                    is SectionBlock -> SettingsSectionCard(block.section.titleRes) {
                        block.section.items.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            SettingsItemRow(item)
                        }
                    }

                    is ProfilesBlock -> ProfileSection(
                        profiles = profiles,
                        onAdd = onAddProfile,
                        onEdit = onEditProfile,
                        onDelete = onDeleteProfile,
                    )
                }
            }
        }
    }
}

/** 设置页标题行。返回按钮同时承担退出设置页的职责。 */
@Composable
private fun SettingsHeader(controller: ShellViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = controller::closeSettings) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
            )
        }
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 搜索框。查询为空时不显示清除按钮。 */
@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        placeholder = { Text(stringResource(R.string.settings_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
            )
        },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.settings_search_clear),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** 搜索无结果时的空态。 */
@Composable
private fun EmptySearchResult() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Text(
            text = stringResource(R.string.settings_search_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}

/** 会话配置区块：标题、数量、新增入口与逐条记录。 */
@Composable
private fun ProfileSection(
    profiles: List<SessionProfile>,
    onAdd: () -> Unit,
    onEdit: (SessionProfile) -> Unit,
    onDelete: (SessionProfile) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_section_profiles),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.settings_profiles_count, profiles.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_profile_add))
            }
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_profile_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                Column(Modifier.padding(vertical = 4.dp)) {
                    profiles.forEachIndexed { index, profile ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        ProfileRow(
                            profile = profile,
                            onEdit = { onEdit(profile) },
                            onDelete = { onDelete(profile) },
                        )
                    }
                }
            }
        }
    }
}

/** 单条会话配置：名称、类型摘要与编辑、删除入口。 */
@Composable
private fun ProfileRow(
    profile: SessionProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = profileSummary(profile),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.settings_profile_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onDelete,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.settings_profile_delete),
            )
        }
    }
}

/** 会话配置摘要文案。三种类型各用一条资源，避免在代码中拼接分隔符。 */
@Composable
private fun profileSummary(profile: SessionProfile): String = when (profile.kind) {
    ProfileKind.SHELL -> profile.command.ifBlank {
        stringResource(R.string.settings_profile_kind_shell)
    }

    ProfileKind.DISTRO -> stringResource(R.string.settings_profile_summary_distro, profile.distro)
    ProfileKind.SSH -> stringResource(R.string.settings_profile_summary_ssh, profile.sshHost)
}

/**
 * 按查询过滤显示块。
 *
 * 行为：查询为空时原样返回；非空时只保留包含命中项的配置分组，并隐藏会话配置区块。
 * 约束：纯函数，只读取传入的块与查询，不访问控制器。
 */
private fun filterBlocks(
    context: Context,
    blocks: List<SettingsBlock>,
    query: String,
): List<SettingsBlock> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
        return blocks
    }
    return blocks.mapNotNull { block ->
        when (block) {
            is SectionBlock -> {
                val items = block.section.items.filter { item -> matches(context, item, trimmed) }
                if (items.isEmpty()) null else SectionBlock(block.section.copy(items = items))
            }

            is ProfilesBlock -> null
        }
    }
}

/** 判断单个设置项是否命中查询。 */
private fun matches(context: Context, item: SettingsItem, query: String): Boolean {
    if (context.getString(item.titleRes).contains(query, ignoreCase = true)) {
        return true
    }
    val description = item.descriptionRes?.let { context.getString(it) }.orEmpty()
    return description.contains(query, ignoreCase = true)
}
