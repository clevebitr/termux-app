package com.termux.shell.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 外壳视觉主题。
 *
 * 目的：把 Compose 配色与排版集中在一处，使界面组件只引用 [MaterialTheme] 令牌，
 * 便于后续替换为自定义配色或动态取色。
 *
 * 行为：按深色开关选择配色方案，并用 [Surface] 提供窗口底色与默认内容色。
 *
 * 约束：不包裹 [Surface] 时 `LocalContentColor` 会保持默认的黑色，深色主题下的文字与图标将不可见，
 * 因此这里必须提供内容色；窗口背景色另由 XML 主题提供，用于启动首帧避免闪白。
 */
@Composable
fun ShellTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()
