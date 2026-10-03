package space.buercheng.kylintodo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState

/**
 * 无边框窗口的右下角缩放把手。
 *
 * ## 为什么无边框窗口需要自己实现 resize
 * 系统边框的窗口由操作系统处理边缘拖拽缩放；去掉系统边框
 * （`undecorated = true`）后这个能力一并消失，必须自己补上。
 *
 * ## 实现方式
 * 把手是右下角的一个小区域（约 18dp），**只有拖它才触发缩放**，
 * 与窗口内容（日历、待办等）的拖拽互不干扰。
 * 拖拽时宽度与高度同时跟随位移。
 *
 * @param minSize 最小窗口尺寸，防止缩到不可用
 */
@Composable
fun ResizeHandle(
    windowState: WindowState,
    modifier: Modifier = Modifier,
    minSize: DpSize = DpSize(640.dp, 480.dp),
    handleSize: Int = 18,
) {
    Box(
        modifier = modifier
            .size(handleSize.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val current = windowState.size
                    val density = density
                    val newWidth = (current.width + (dragAmount.x / density).dp)
                        .coerceAtLeast(minSize.width)
                    val newHeight = (current.height + (dragAmount.y / density).dp)
                        .coerceAtLeast(minSize.height)
                    windowState.size = DpSize(newWidth, newHeight)
                }
            },
        contentAlignment = Alignment.BottomEnd,
    ) {
        // 右下角斜纹提示，让用户知道这里可拖拽缩放。
        // 注意：MaterialTheme.colorScheme 是 @Composable 属性，必须在 Canvas
        // 的 DrawScope lambda 之外取值 —— DrawScope 不是 composable 上下文。
        val handleColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        Canvas(modifier = Modifier.size((handleSize - 6).dp)) {
            val stroke = 1.dp.toPx()
            val gap = 3.dp.toPx()
            val end = size.width
            repeat(3) { i ->
                val offset = (i * gap)
                drawLine(
                    color = handleColor,
                    start = Offset(end - gap, offset),
                    end = Offset(offset, end - gap),
                    strokeWidth = stroke,
                )
            }
        }
    }
}
