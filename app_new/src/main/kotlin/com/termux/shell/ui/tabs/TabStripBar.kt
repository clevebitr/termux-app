package com.termux.shell.ui.tabs

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termux.shell.R
import com.termux.shell.tabs.TabWorkspace
import com.termux.shell.ui.ShellViewOptions
import com.termux.shell.ui.ShellViewModel

import kotlin.math.abs
import kotlin.math.roundToInt

private val TabShape = RoundedCornerShape(12.dp)
private val MinTabWidth = 96.dp
private val CompactTabWidth = 160.dp

/** 标签内容行高。触摸目标不低于 48dp，满足 Material 的最小交互尺寸。 */
private val TabHeight = 48.dp

/** 关闭按钮的布局尺寸；实际触摸目标由 IconButton 扩展到 48dp。 */
private val CloseButtonSize = 32.dp

/** 关闭图标尺寸。 */
private val CloseIconSize = 16.dp

/** 末尾「新建标签」按钮占用的宽度，用于计算平均分配模式下的标签宽度。 */
private val NewTabSlotWidth = 48.dp

/** 拖拽超过标签宽度的该比例时视为发生了移动，否则按「长按后抬手」处理为打开菜单。 */
private const val DragThresholdRatio = 0.25f

/**
 * 窗口级标签栏。
 *
 * 行为：
 * - `equal` 模式下标签平均分配可用宽度，可用宽度不足时退化为固定宽度并允许横向滚动；
 * - 点击切换、点击关闭按钮关闭、长按后拖动调整顺序、长按后原地抬手或右键弹出菜单；
 * - 选中标签变化时自动滚动到可见位置，末尾固定「新建标签」按钮；
 * - 标签列表使用 [LazyRow]，标签数量受上限约束，不会一次性组合全部条目。
 *
 * 约束：本组件不修改标签集合，全部变更通过 [ShellViewModel] 发起。
 */
@Composable
fun TabStripBar(
    workspace: TabWorkspace,
    options: ShellViewOptions,
    controller: ShellViewModel,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val listState = rememberLazyListState()
        val available = maxWidth - NewTabSlotWidth
        val tabWidth = if (options.equalTabWidth && MinTabWidth * workspace.size <= available) {
            (available / workspace.size).coerceAtLeast(MinTabWidth)
        } else {
            CompactTabWidth
        }
        val tabWidthPx = with(LocalDensity.current) { (tabWidth + TabSpacing).toPx() }

        // 选中标签不在可视区域时再滚动，避免每次切换都把标签推到最左。
        LaunchedEffect(workspace.activeIndex) {
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == workspace.activeIndex }
            val fullyVisible = visible != null &&
                visible.offset >= layoutInfo.viewportStartOffset &&
                visible.offset + visible.size <= layoutInfo.viewportEndOffset
            if (!fullyVisible) {
                listState.animateScrollToItem(workspace.activeIndex)
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(workspace.tabs, key = { _, tab -> tab.id }) { index, _ ->
                TabItem(
                    workspace = workspace,
                    index = index,
                    showCloseButton = options.showTabCloseButton,
                    controller = controller,
                    itemWidthPx = tabWidthPx,
                    modifier = Modifier.width(tabWidth),
                )
            }
            item(key = "new-tab") { NewTabButton(controller) }
        }
    }
}

/** 标签之间的间距，拖拽换算目标位置时按「标签宽度 + 间距」计算。 */
internal val TabSpacing = 4.dp

@Composable
private fun NewTabButton(controller: ShellViewModel) {
    IconButton(
        onClick = controller::newTab,
        modifier = Modifier.size(NewTabSlotWidth),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.shell_new_tab),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

