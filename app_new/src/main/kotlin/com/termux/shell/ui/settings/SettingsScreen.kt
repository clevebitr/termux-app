package com.termux.shell.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.termux.shell.R
import com.termux.shell.session.SessionProfile
import com.termux.shell.ui.ProfileEditorDialog
import com.termux.shell.ui.ShellViewModel

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
    val visibleBlocks = SettingsSearch.filter(blocks, query, context::getString)

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

