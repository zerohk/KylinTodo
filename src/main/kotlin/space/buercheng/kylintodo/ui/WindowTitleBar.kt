package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState

/**
 * 无边框窗口的自绘标题栏。
 *
 * ## 为什么需要自绘
 * 无边框窗口（`undecorated = true`）没有系统标题栏，但用户仍需要：
 *  1. 看到窗口标题与应用名
 *  2. 拖拽标题栏移动窗口
 *  3. 最小化 / 关闭 / 置顶
 *
 * ## 设计
 * - 背景与前景**跟随主题色**（`MaterialTheme.colorScheme.surface / onSurface`）：
 *   用户自定义背景色后，标题栏一并跟随，不再强制白色。
 *   文字可读性由主题层的"背景亮度决定前景色"逻辑自动保证。
 * - 整条可拖拽移动（复用 [windowDrag]）
 * - 按钮固定在右侧：置顶 / 最小化 / 关闭
 */
@Composable
fun WindowTitleBar(
    title: String,
    windowState: WindowState,
    onClose: () -> Unit,
    /** 是否置顶（高亮图钉） */
    pinned: Boolean,
    /** 切换置顶 */
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(scheme.surface)
            .windowDrag { dx, dy ->
                // 无边框窗口没有系统标题栏，拖动由我们自己实现。
                // density 已在 composable 上下文取值（回调不是 composable）。
                windowState.position = nextWindowPosition(
                    current = windowState.position,
                    deltaXPx = dx,
                    deltaYPx = dy,
                    density = density,
                )
            }
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        // 置顶：高亮表示当前置顶
        IconButton(
            onClick = onTogglePin,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = if (pinned) "取消置顶" else "置顶显示",
                tint = if (pinned) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        // 最小化
        IconButton(
            onClick = { windowState.isMinimized = true },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = "最小化",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        // 关闭
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
