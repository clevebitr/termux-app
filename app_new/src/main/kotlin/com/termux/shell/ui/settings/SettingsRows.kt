package com.termux.shell.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 设置分组卡片的圆角。与 MD3 的 large 形状档位一致。 */
private val SectionShape = RoundedCornerShape(20.dp)

/** 行内水平留白。 */
private val RowHorizontalPadding = 16.dp

/**
 * 设置分组卡片。
 *
 * 目的：以 MD3 的「分组标题 + 填充卡片」结构呈现一组设置，替代以分割线切分的长列表，
 * 使各分组在视觉上自成一块。
 *
 * 约束：分组标题带 heading 语义，供读屏按标题跳转。
 */
@Composable
internal fun SettingsSectionCard(
    titleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp, bottom = 8.dp)
                .semantics { heading() },
        )
        Surface(
            shape = SectionShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

/** 按项目类型分派渲染。 */
@Composable
internal fun SettingsItemRow(item: SettingsItem) {
    when (item) {
        is ToggleItem -> SettingsToggleRow(item)
        is ChoiceItem -> SettingsChoiceRow(item)
        is ActionItem -> SettingsActionRow(item)
        is InfoItem -> SettingsInfoRow(item)
    }
}

/**
 * 开关行。
 *
 * 行为：整行可点，点击任意位置切换取值；开关本身只呈现状态，不接收独立点击，避免出现两个语义焦点。
 */
@Composable
private fun SettingsToggleRow(item: ToggleItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = item.checked,
                role = Role.Switch,
                onValueChange = item.onCheckedChange,
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = RowHorizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowTitle(item.titleRes, item.descriptionRes, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = item.checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** 选项行：以 filter chip 呈现互斥取值，选中项带对勾图标。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsChoiceRow(item: ChoiceItem) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = RowHorizontalPadding, vertical = 12.dp),
    ) {
        SettingsRowTitle(item.titleRes, item.descriptionRes)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in item.options) {
                val selected = option.value == item.selected
                FilterChip(
                    selected = selected,
                    onClick = { item.onSelect(option.value) },
                    label = {
                        Text(
                            text = stringResource(option.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** 动作行：整行可点，右侧箭头提示会执行一次性操作（如重新加载配置文件）。 */
@Composable
private fun SettingsActionRow(item: ActionItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = item.onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = RowHorizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowTitle(item.titleRes, item.descriptionRes, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 信息行：只读展示，文本可选中复制，便于用户把路径带到文件管理器或脚本中。 */
@Composable
private fun SettingsInfoRow(item: InfoItem) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = RowHorizontalPadding, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(item.titleRes),
            style = MaterialTheme.typography.bodyMedium,
        )
        SelectionContainer {
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 标题与说明的公共排版。 */
@Composable
private fun SettingsRowTitle(titleRes: Int, descriptionRes: Int?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (descriptionRes != null) {
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
