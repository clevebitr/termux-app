package com.termux.shell.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.termux.shell.session.SessionState

/**
 * 单条会话记录。
 *
 * 无障碍：整行作为按钮朗读，并以状态描述说明会话处于运行、已退出还是尚未分配；
 * 当前聚焦的窗格对应记录带选中语义。
 */
@Composable
internal fun SessionRow(
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
