package com.termux.shell.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 外壳视觉主题。
 *
 * 目的：把 Compose 配色与排版集中在一处，使界面组件只引用 [MaterialTheme] 令牌，
 * 便于后续替换为自定义配色或动态取色。
 *
 * 约束：本主题只作用于 Compose 内容；窗口背景色由 XML 主题提供，用于启动首帧避免闪白。
 */
@Composable
fun ShellTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()
