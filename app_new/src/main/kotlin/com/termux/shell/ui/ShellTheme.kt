package com.termux.shell.ui

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * 外壳视觉主题。
 *
 * 目的：把 Compose 配色与排版集中在一处，使界面组件只引用 [MaterialTheme] 令牌，
 * 从而在一个位置切换配色来源。
 *
 * 行为：Android 12（API 31）及以上优先使用系统取色（dynamic color），使外壳与系统其他应用的
 * 配色一致；低版本与取色不可用时回退到 Material 3 的默认浅色与深色方案。
 *
 * 约束：
 * - 必须提供 [Surface] 作为内容色来源，否则 `LocalContentColor` 保持默认黑色，深色主题下文字不可见；
 * - 窗口背景色另由 XML 主题提供，用于启动首帧避免闪白；
 * - 终端视图自绘配色不受本主题影响，终端配色由终端层负责。
 */
@Composable
fun ShellTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme) {
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
