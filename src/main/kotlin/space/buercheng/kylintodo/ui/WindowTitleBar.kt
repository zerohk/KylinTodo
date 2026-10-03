package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState

/**
 * 无边框窗口的自绘标题栏。
 *
 * ## 为什么需要自绘
 * 无边框窗口（`undecorated = true`）没有系统标题栏，但用户仍需要：
 *  1. 看到窗口标题与应用名
 *  2. 拖拽标题栏移动窗口
 *  3. 最小化 / 关闭
 *
 * ## 设计
 * - 背景**始终纯白**、前景深灰：满足"标题栏无论主题如何都是白色"的需求，
 *   也让标题栏与内容区在深色主题下形成清晰边界
 * - 整条可拖拽移动（复用 [windowDrag]），把手足够大
 * - 最小化 / 关闭按钮固定在右侧
 */
@Composable
fun WindowTitleBar(
    title: String,
    windowState: WindowState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color.White)
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
            color = Color(0xFF1F2937),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        // 最小化
        IconButton(
            onClick = { windowState.isMinimized = true },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = "最小化",
                tint = Color(0xFF4B5563),
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
                tint = Color(0xFF4B5563),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
