package space.buercheng.kylintodo.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition

/**
 * 让无边框窗口可随鼠标拖动移动。
 *
 * 无边框窗口没有系统标题栏，必须自己实现拖动：把鼠标位移累加到窗口位置。
 */
fun Modifier.windowDrag(
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            onDrag(dragAmount.x, dragAmount.y)
        }
    }
)

/**
 * 把像素位移换算为 dp 并生成新的窗口位置。
 *
 * Compose Desktop 的 [WindowPosition] 以 dp 为单位，而指针事件给的是像素，
 * 因此必须按当前 density 换算，否则高分屏上拖动速度会明显偏离鼠标。
 *
 * 位置基点的取值：窗口初始位置可能由对齐规则（[WindowPosition.Aligned]）
 * 决定，此时尚无绝对坐标，按 (0,0) 起算；一旦用户开始拖动就转为绝对定位。
 */
fun nextWindowPosition(
    current: WindowPosition,
    deltaXPx: Float,
    deltaYPx: Float,
    density: Float,
): WindowPosition {
    val absolute = current as? WindowPosition.Absolute
    val baseX = absolute?.x?.value ?: 0f
    val baseY = absolute?.y?.value ?: 0f
    return WindowPosition.Absolute(
        x = (baseX + deltaXPx / density).dp,
        y = (baseY + deltaYPx / density).dp,
    )
}

/**
 * 计算吸附到屏幕边缘后的位置。
 *
 * 小窗贴边更符合桌面小组件的使用习惯。仅做水平吸附，垂直方向保持自由，
 * 以免用户想放在屏幕中部时被强行拉走。
 *
 * @param windowWidthDp 窗口宽度
 * @param screenWidthDp 屏幕宽度
 * @param thresholdDp 触发吸附的距离阈值
 */
fun snapToHorizontalEdge(
    position: WindowPosition,
    windowWidthDp: Dp,
    screenWidthDp: Dp,
    screenHeightDp: Dp,
    thresholdDp: Float = 24f,
): WindowPosition {
    val absolute = position as? WindowPosition.Absolute ?: return position
    val x = absolute.x.value
    val y = absolute.y.value
    val width = windowWidthDp.value

    val snappedX = when {
        x <= thresholdDp -> 0f
        x + width >= screenWidthDp.value - thresholdDp -> screenWidthDp.value - width
        else -> x
    }
    val clampedY = y.coerceIn(0f, (screenHeightDp.value - 120f).coerceAtLeast(0f))

    return WindowPosition.Absolute(snappedX.dp, clampedY.dp)
}
