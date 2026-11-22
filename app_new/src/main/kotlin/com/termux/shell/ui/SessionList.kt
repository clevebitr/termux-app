package com.termux.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.shell.R
import com.termux.shell.session.SessionRecord
import com.termux.shell.session.SessionRegistry
import com.termux.shell.session.SessionState

/**
 * 会话列表。
 *
 * 目的：以「会话」为单位展示进程与附着位置，替代只显示标签与窗格计数的侧栏，
 * 使标签栏负责布局、会话列表负责进程，两者不再重复（职责划分见 `docs/UPGRADE.md` 第 13.4 节）。
 *
 * 行为：
 * - 先列已附着会话，再列已脱离会话；每条记录显示状态点、标题与附着位置；
 * - 点击记录聚焦其窗格（切换标签并移动窗格焦点）；
 * - 记录标题与状态来自会话宿主上报的描述，未绑定时显示占位说明，已退出时显示退出状态；
 * - 列表使用 [LazyColumn] 按需组合，会话数量较多时不构造全部条目。
 *
 * 约束：本组件不修改会话集合，全部操作通过回调交由 [ShellViewModel] 发起。
 *
 * @param compact 紧凑密度用于中等宽度档的常驻侧栏；完整密度用于展开档与手机端抽屉。
 * @param headerActions 会话栏顶部的操作入口（新建标签、设置、窗口菜单），由调用方提供。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionList(
    registry: SessionRegistry,
    focusedLeafId: String?,
    compact: Boolean,
    onFocus: (SessionRecord) -> Unit,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
) {
    val attached = registry.attached()
    val detached = registry.detached()

    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 6.dp else 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.shell_rail_title),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!compact) {
                    Text(
                        text = registry.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 新建、设置与窗口操作随会话列表一起展示；紧凑侧栏由 FlowRow 自动换行。
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = headerActions,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (registry.size == 0) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.session_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (attached.isNotEmpty()) {
                item(key = "attached-label") {
                    GroupLabel(stringResource(R.string.session_group_attached))
                }
                items(attached, key = { record -> record.id }) { record ->
                    SessionRow(
                        record = record,
                        focused = record.leafId != null && record.leafId == focusedLeafId,
                        compact = compact,
                        onClick = { onFocus(record) },
                    )
                }
            }
            if (detached.isNotEmpty()) {
                item(key = "detached-label") {
                    GroupLabel(stringResource(R.string.session_group_detached))
                }
                items(detached, key = { record -> record.id }) { record ->
                    SessionRow(
                        record = record,
                        focused = false,
                        compact = compact,
                        onClick = { onFocus(record) },
                    )
                }
            }
            if (registry.records.any { it.isPending }) {
                item(key = "pending-note") {
                    Text(
                        text = stringResource(R.string.session_action_unavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                    )
                }
            }
            item(key = "footer-space") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * 单条会话记录。
 *
 * 无障碍：整行作为按钮朗读，并以状态描述说明会话处于运行、已退出还是尚未分配；
 * 当前聚焦的窗格对应记录带选中语义。
 */
@Composable
private fun SessionRow(
    record: SessionRecord,
    focused: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        focused -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val stateText = stateLabel(record.state)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                selected = focused
                stateDescription = stateText
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(stateColor(record.state)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = sessionTitle(record),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (compact) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                Text(
                    text = if (record.isAttached) {
                        stringResource(R.string.session_attached_to, record.tabTitle)
                    } else {
                        stateText
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!compact) {
            Text(
                text = stateText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * 会话标题。
 *
 * 行为：优先使用宿主上报的前台进程标题；尚未分配会话时说明窗格标识，已结束时说明会话已退出，
 * 运行中但标题为空的会话使用通用文案，避免出现与真实状态矛盾的「无会话」。
 */
@Composable
private fun sessionTitle(record: SessionRecord): String = record.title ?: when (record.state) {
    SessionState.PENDING -> stringResource(R.string.session_pending_title, record.leafId.orEmpty())
    SessionState.EXITED -> stringResource(R.string.session_exited_title, record.leafId.orEmpty())
    SessionState.RUNNING -> stringResource(R.string.session_untitled)
}

@Composable
private fun stateLabel(state: SessionState): String = when (state) {
    SessionState.PENDING -> stringResource(R.string.session_state_pending)
    SessionState.RUNNING -> stringResource(R.string.session_state_running)
    SessionState.EXITED -> stringResource(R.string.session_state_exited)
}

@Composable
private fun stateColor(state: SessionState) = when (state) {
    SessionState.PENDING -> MaterialTheme.colorScheme.outline
    SessionState.RUNNING -> MaterialTheme.colorScheme.primary
    SessionState.EXITED -> MaterialTheme.colorScheme.error
}

/** 紧凑侧栏的固定宽度：仅容纳状态点与两行短标题。 */
internal val CompactRailWidth = 96.dp

/** 展开档侧栏宽度：容纳标题、附着位置与状态。 */
internal val ExpandedRailWidth = 240.dp

/** 手机端抽屉的内容宽度上限，避免横屏时占满窗口。 */
internal val DrawerWidth = 300.dp
