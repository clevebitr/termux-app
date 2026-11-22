package com.termux.shell.ui.palette

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.termux.shell.commands.ShellCommand

internal sealed interface PaletteRow {

    data class Header(val category: String) : PaletteRow

    data class Command(val command: ShellCommand) : PaletteRow
}

/**
 * 命令面板覆盖层。
 *
 * 目的：提供与主流终端一致的命令面板体验：输入即筛选、方向键选择、回车执行、Esc 关闭。
 *
 * 行为：
 * - 以窗口内覆盖层呈现（非独立对话框窗口），因此可以与主界面共享淡入与缩放动画；
 * - 遮罩点击或返回键关闭，卡片自身消费点击，避免误关闭；
 * - 查询按 [CommandFilter] 规则筛选，不可执行的命令仍然显示但置灰且不可触发；
 * - 结果按命令分组（标签、窗格、窗口）呈现，分组标题带标题语义，便于读屏按组跳转；
 * - 选中项随筛选结果回到首个可执行项并滚动到可见位置，选中背景带颜色过渡；
 * - 执行时先关闭面板再运行命令，避免命令触发的界面切换与面板收尾相互干扰。
 *
 * 约束：仅允许在主线程组合；面板可见性由 [ShellViewModel] 负责，进入与退出动画由调用方提供。
 */

@Composable
internal fun CategoryHeader(category: String) {
    Text(
        text = category,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** 单条命令：标题、快捷键与选中背景。 */
@Composable
internal fun CommandRow(command: ShellCommand, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "commandRowBackground",
    )
    val contentColor = when {
        !command.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = command.enabled, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = command.title,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        command.shortcut?.let { shortcut ->
            Text(
                text = shortcut,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

