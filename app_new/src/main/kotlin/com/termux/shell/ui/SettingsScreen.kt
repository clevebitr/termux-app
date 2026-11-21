package com.termux.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.shell.BuildConfig
import com.termux.shell.R
import com.termux.shell.config.SettingsSpec

/**
 * 设置页。
 *
 * 目的：把配置文件中用户常改的项集中为可视化入口，并使界面与属性文件双向一致：界面修改立即写入文件，
 * 外部修改可通过「重新加载配置文件」带回界面。
 *
 * 行为：
 * - 分四组展示：外观（主题、标签栏宽度与位置）、会话（常驻会话列表）、输入（外壳快捷键、命令面板）、关于；
 * - 开关与选项均直接调用 [ShellController] 的写入方法，写入在后台线程完成；
 * - 窄屏为整页滚动，宽屏在内容两侧留出边距，避免行过长。
 *
 * 约束：本页不缓存配置值，全部取值来自控制器状态，因此外部重载后界面会同步刷新。
 */
@Composable
fun SettingsScreen(controller: ShellController) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SettingsHeader(controller)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_appearance))
            ChoiceRow(
                title = stringResource(R.string.settings_theme),
                options = listOf(
                    SettingsSpec.THEME_SYSTEM to stringResource(R.string.settings_theme_system),
                    SettingsSpec.THEME_LIGHT to stringResource(R.string.settings_theme_light),
                    SettingsSpec.THEME_DARK to stringResource(R.string.settings_theme_dark),
                ),
                selected = controller.themeMode,
                onSelect = controller::updateThemeMode,
            )
            ChoiceRow(
                title = stringResource(R.string.settings_layout_mode),
                description = stringResource(R.string.settings_layout_mode_hint),
                options = listOf(
                    SettingsSpec.LAYOUT_AUTO to stringResource(R.string.settings_layout_auto),
                    SettingsSpec.LAYOUT_PHONE to stringResource(R.string.settings_layout_phone),
                    SettingsSpec.LAYOUT_TABLET to stringResource(R.string.settings_layout_tablet),
                ),
                selected = controller.layoutMode,
                onSelect = controller::updateLayoutMode,
            )
            SwitchRow(
                title = stringResource(R.string.settings_tab_bar_visible),
                description = stringResource(R.string.settings_tab_bar_visible_hint),
                checked = controller.viewOptions.tabBarVisible,
                onCheckedChange = controller::setTabBarVisible,
            )
            ChoiceRow(
                title = stringResource(R.string.settings_tab_bar_position),
                options = listOf(
                    SettingsSpec.TAB_POSITION_TOP to stringResource(R.string.settings_position_top),
                    SettingsSpec.TAB_POSITION_BOTTOM to stringResource(R.string.settings_position_bottom),
                ),
                selected = if (controller.viewOptions.tabBarAtBottom) {
                    SettingsSpec.TAB_POSITION_BOTTOM
                } else {
                    SettingsSpec.TAB_POSITION_TOP
                },
                onSelect = controller::setTabBarPosition,
            )
            ChoiceRow(
                title = stringResource(R.string.settings_tab_width_mode),
                options = listOf(
                    SettingsSpec.TAB_WIDTH_EQUAL to stringResource(R.string.settings_tab_width_equal),
                    SettingsSpec.TAB_WIDTH_COMPACT to stringResource(R.string.settings_tab_width_compact),
                ),
                selected = if (controller.viewOptions.equalTabWidth) {
                    SettingsSpec.TAB_WIDTH_EQUAL
                } else {
                    SettingsSpec.TAB_WIDTH_COMPACT
                },
                onSelect = controller::setTabWidthMode,
            )
            SwitchRow(
                title = stringResource(R.string.settings_tab_close_button),
                checked = controller.viewOptions.showTabCloseButton,
                onCheckedChange = controller::setTabCloseButtonVisible,
            )

            SectionTitle(stringResource(R.string.settings_section_sessions))
            SwitchRow(
                title = stringResource(R.string.settings_session_rail),
                description = stringResource(R.string.settings_session_rail_hint),
                checked = controller.sessionRailVisible,
                onCheckedChange = controller::updateSessionRailVisible,
            )

            SectionTitle(stringResource(R.string.settings_section_input))
            SwitchRow(
                title = stringResource(R.string.settings_keymap_enabled),
                description = stringResource(R.string.settings_keymap_enabled_hint),
                checked = controller.keymapEnabled,
                onCheckedChange = controller::updateKeymapEnabled,
            )
            SwitchRow(
                title = stringResource(R.string.settings_command_palette),
                checked = controller.commandPaletteEnabled,
                onCheckedChange = controller::updateCommandPaletteEnabled,
            )

            SectionTitle(stringResource(R.string.settings_section_about))
            InfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
            InfoRow(stringResource(R.string.settings_config_file), controller.configFilePath)
            InfoRow(stringResource(R.string.settings_keymap_file), controller.keymapFilePath)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = controller::reloadConfig) {
                    Text(stringResource(R.string.settings_reload_config))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsHeader(controller: ShellController) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
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
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    description: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((value, label) in options) {
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
