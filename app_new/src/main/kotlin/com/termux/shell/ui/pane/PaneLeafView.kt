package com.termux.shell.ui.pane

import android.view.ViewGroup
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.shell.R
import com.termux.shell.pane.PaneLeaf
import com.termux.shell.terminal.TerminalViewPool
import com.termux.shell.ui.ShellViewModel

@Composable
internal fun PaneLeafView(
    leaf: PaneLeaf,
    focused: Boolean,
    controller: ShellViewModel,
    pool: TerminalViewPool?,
) {
    val sessionId = leaf.sessionId
    val terminalView = remember(sessionId, pool) {
        if (sessionId != null && pool != null) {
            runCatching { pool.obtain(sessionId) }.getOrNull()
        } else {
            null
        }
    }
    LaunchedEffect(focused, terminalView) {
        if (focused && terminalView != null && !terminalView.isFocused) {
            terminalView.requestFocus()
        }
    }

    val shape = RoundedCornerShape(8.dp)
    // 取消常驻边框：只有聚焦窗格显示强调色描边，未聚焦窗格不加线，避免分割线隐藏后仍有两道细线。
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 140),
        label = "paneBorderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 1.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 140),
    )
    val containerModifier = Modifier
        .fillMaxSize()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant, shape)
        .border(borderWidth, borderColor, shape)

    if (terminalView != null) {
        Box(containerModifier) {
            AndroidView(
                factory = {
                    // 视图由池复用，重新挂载前必须先与原父容器解绑，否则会抛「已有父容器」。
                    (terminalView.parent as? ViewGroup)?.removeView(terminalView)
                    terminalView
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view -> (view.parent as? ViewGroup)?.removeView(view) },
            )
        }
    } else {
        Box(
            containerModifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                controller.focusLeaf(leaf.id)
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.shell_pane_placeholder, leaf.id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.shell_pane_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

