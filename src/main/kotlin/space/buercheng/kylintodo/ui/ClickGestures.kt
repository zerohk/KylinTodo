package space.buercheng.kylintodo.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/** 双击判定的默认时间窗口（毫秒），与系统常规设置接近。 */
const val DEFAULT_DOUBLE_CLICK_TIMEOUT_MS = 280L

/**
 * 无涟漪效果的点击。
 *
 * 用于弹窗里的小控件（优先级按钮、标签胶囊的删除叉）：这些元素很小，
 * Material 的涟漪会溢出边界并显得杂乱。语义与 `clickable` 一致，
 * 仍保留无障碍所需的 interactionSource。
 */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}

/**
 * 同时支持单击与双击的点击手势。
 *
 * Compose 没有内置的「单击 + 双击」组合修饰符：
 *  - `Modifier.clickable` 只处理单击，无法区分双击
 *  - `detectTapGestures` 同时传 onTap 与 onDoubleTap 时，会等双击超时后才触发
 *    onTap，单击会有约 300ms 的延迟
 *
 * 为避免单击选中日期时出现可感知延迟，这里用**时间差自实现**：
 * 单击立即回调 [onClick]；若两次单击间隔小于 [timeoutMs]，
 * 再额外回调 [onDoubleClick]。代价是双击时单击回调会先触发一次，
 * 对本场景（先选中该日、再打开详情）来说这与用户直觉一致。
 *
 * @param timeoutMs 判定为双击的最大时间间隔
 */
fun Modifier.singleOrDoubleClick(
    timeoutMs: Long = DEFAULT_DOUBLE_CLICK_TIMEOUT_MS,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(timeoutMs) {
        var lastClickAt = 0L
        awaitPointerEventScope {
            while (true) {
                awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation() ?: continue

                val now = System.currentTimeMillis()
                if (lastClickAt != 0L && now - lastClickAt <= timeoutMs) {
                    lastClickAt = 0L
                    onDoubleClick()
                } else {
                    lastClickAt = now
                    onClick()
                }
            }
        }
    }
)
