package com.termux.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.termux.shell.R
import com.termux.shell.commands.CommandFilter
import com.termux.shell.commands.ShellCommand

/**
 * 命令面板。
 *
 * 目的：提供与 Windows Terminal 命令面板一致的入口体验：输入即筛选、方向键选择、回车执行、Esc 关闭。
 *
 * 行为：
 * - 以顶部浮动卡片显示，背景遮罩由 [Dialog] 提供；卡片最大宽度 560dp，距顶部 72dp；
 * - 查询按 [CommandFilter] 规则筛选，不可执行的命令仍然显示但置灰且不可触发；
 * - 选中项随筛选结果自动回到首个可执行项，并滚动到可见区域；
 * - 执行时先关闭面板再运行命令，避免命令触发的窗口重建与面板收尾相互干扰。
 *
 * 约束：仅允许在主线程组合；面板状态由 [ShellController] 持有。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CommandPaletteView(commands: List<ShellCommand>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val filtered = remember(query, commands) {
        commands.filter { CommandFilter.matches(query, it.title, it.category) }
    }

    LaunchedEffect(filtered) {
        selectedIndex = filtered.indexOfFirst { it.enabled }.coerceAtLeast(0)
    }
    LaunchedEffect(selectedIndex, filtered) {
        if (selectedIndex in filtered.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    fun executeSelected() {
        val command = filtered.getOrNull(selectedIndex) ?: return
        if (!command.enabled) {
            return
        }
        onDismiss()
        command.execute()
    }

    fun moveSelection(offset: Int) {
        if (filtered.isEmpty()) {
            return
        }
        var index = selectedIndex
        repeat(filtered.size) {
            index = ((index + offset) % filtered.size + filtered.size) % filtered.size
            if (filtered[index].enabled) {
                selectedIndex = index
                return
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 72.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            Key.DirectionDown -> {
                                moveSelection(1)
                                true
                            }

                            Key.DirectionUp -> {
                                moveSelection(-1)
                                true
                            }

                            Key.Enter, Key.NumPadEnter -> {
                                executeSelected()
                                true
                            }

                            Key.Escape -> {
                                onDismiss()
                                true
                            }

                            else -> false
                        }
                    },
            ) {
                Column {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.palette_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { executeSelected() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(R.string.palette_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.heightIn(max = 320.dp),
                        ) {
                            itemsIndexed(filtered) { index, command ->
                                CommandRow(
                                    command = command,
                                    selected = index == selectedIndex,
                                    onClick = {
                                        selectedIndex = index
                                        executeSelected()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(command: ShellCommand, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(background, RoundedCornerShape(8.dp))
            .clickable(enabled = command.enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = command.title,
            style = MaterialTheme.typography.bodyMedium,
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
