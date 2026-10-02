package space.buercheng.kylintodo.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import java.time.LocalDate

/**
 * 调试用：记录每个日历格子在**窗口坐标系**中的实际范围，以及每次点击命中的格子。
 *
 * ## 为什么需要它
 * 用户报告「翻到 2027 年 1 月后单击某天，月份跳到上一个月」，而状态模型的
 * 穷举测试（约 4000 种组合）与运行时状态插桩都通过。剩余的可能原因是
 * GUI 层面的：**用户点的那一格**与**代码以为被点的那一格**不一致 ——
 * 这只能通过比对实际渲染位置与点击命中来验证。
 *
 * 仅在 `-Dkylintodo.probe=true` 时工作，生产构建零开销。
 */
object ClickProbeSupport {

    /** 构件名 -> 窗口内坐标 [left, top, right, bottom]。 */
    private val bounds = linkedMapOf<String, FloatArray>()

    @Volatile
    var lastClickedTag: String? = null

    fun record(key: String, left: Float, top: Float, right: Float, bottom: Float) {
        if (!PROBE_ENABLED) return
        bounds[key] = floatArrayOf(left, top, right, bottom)
    }

    fun boundsOf(key: String): FloatArray? = bounds[key]

    fun clear() = bounds.clear()

    /** 打印记录到的构件位置，便于核对渲染是否符合预期。 */
    fun dump(title: String) {
        if (!PROBE_ENABLED) return
        probeLog("=== $title：共 ${bounds.size} 个构件 ===")
        bounds.entries.toList().chunked(7).forEach { row ->
            probeLog(row.joinToString("  ") { (k, r) -> "$k@${r[0].toInt()},${r[1].toInt()}" })
        }
    }
}

/** 记录某个日期的格子窗口坐标（仅探针模式）。 */
fun Modifier.recordCellBounds(date: LocalDate): Modifier =
    recordBounds("cell:${date.monthValue}/${date.dayOfMonth}")

/**
 * 记录任意构件的窗口内坐标（仅探针模式）。
 *
 * 供翻页按钮等非格子构件使用：要与点击命中比对，就必须先知道它画在哪。
 */
fun Modifier.recordBounds(key: String): Modifier = composed {
    if (!PROBE_ENABLED) return@composed this
    onGloballyPositioned { coords ->
        val r = coords.boundsInWindow()
        ClickProbeSupport.record(key, r.left, r.top, r.right, r.bottom)
    }
}

/**
 * 记录点击命中的坐标（仅探针模式）。
 *
 * 只观察不消费事件：用 `awaitPointerEventScope` 读取初始 pass 的事件，
 * 不调用 consume，因此不会干扰正常的手势识别。
 */
fun Modifier.tapProbe(tag: String): Modifier = composed {
    if (!PROBE_ENABLED) return@composed this
    pointerInput(tag) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                if (change.pressed && !change.previousPressed) {
                    ClickProbeSupport.lastClickedTag = tag
                    probeLog(
                        "TAP-PROBE tag=$tag at window=${change.position.x.toInt()},${change.position.y.toInt()}",
                    )
                }
            }
        }
    }
}
