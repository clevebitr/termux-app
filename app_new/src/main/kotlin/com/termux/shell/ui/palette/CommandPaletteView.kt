package com.termux.shell.ui.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.termux.shell.R
import com.termux.shell.commands.CommandFilter
import com.termux.shell.commands.ShellCommand
import com.termux.shell.ui.ShellViewModel

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
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CommandPaletteView(
    commands: List<ShellCommand>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val filtered = remember(query, commands) {
        commands.filter { CommandFilter.matches(query, it.title, it.category) }
    }
    // 分组只影响渲染，选择与执行仍以筛选结果的下标为准，避免两套索引互相换算出错。
    val rows = remember(filtered) {
        buildList {
            filtered.groupBy { it.category }.forEach { (category, group) ->
                add(PaletteRow.Header(category))
                group.forEach { command -> add(PaletteRow.Command(command)) }
            }
        }
    }
    val selectedRow = remember(rows, selectedIndex, filtered) {
        val selected = filtered.getOrNull(selectedIndex) ?: return@remember -1
        rows.indexOfFirst { row -> row is PaletteRow.Command && row.command.id == selected.id }
    }

    LaunchedEffect(filtered) {
        selectedIndex = filtered.indexOfFirst { it.enabled }.coerceAtLeast(0)
    }
    LaunchedEffect(selectedRow) {
        if (selectedRow in rows.indices) {
            listState.animateScrollToItem(selectedRow)
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

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                // 卡片消费点击，避免点击输入框或列表时被遮罩当作关闭操作。
                .pointerInput(Unit) { detectTapGestures {} }
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
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
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
                        modifier = Modifier.heightIn(max = 360.dp),
                    ) {
                        items(
                            count = rows.size,
                            key = { index ->
                                when (val row = rows[index]) {
                                    is PaletteRow.Header -> "header-${row.category}"
                                    is PaletteRow.Command -> row.command.id
                                }
                            },
                        ) { index ->
                            when (val row = rows[index]) {
                                is PaletteRow.Header -> CategoryHeader(row.category)
                                is PaletteRow.Command -> CommandRow(
                                    command = row.command,
                                    selected = filtered.getOrNull(selectedIndex)?.id == row.command.id,
                                    onClick = {
                                        selectedIndex = filtered.indexOfFirst { it.id == row.command.id }
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
