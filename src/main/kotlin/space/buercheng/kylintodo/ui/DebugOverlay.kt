package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.domain.WeekNumbering
import java.time.LocalDate

/**
 * 实时调试状态栏。
 *
 * ## 用途
 * 用户报告「点击日期后月份跳到上一个月」，但状态模型的穷举测试与真实组件树
 * 的 UI 测试都无法复现。因此提供一个**可随时开关的界面状态栏**，
 * 并记录**每次变化前后**的状态 —— 用户复现问题时只需截图，
 * 就能看出到底是哪个状态在何时被改成了什么。
 *
 * 之所以需要"变化历史"而不只是当前值：当前值只能证明"现在是什么"，
 * 无法区分"点击前就是错的"与"点击后被改错了"。
 *
 * ## 开关方式
 * `Ctrl+Shift+D`。默认关闭，不影响正常使用与视觉。
 */
@Composable
fun rememberDebugOverlayState(initiallyVisible: Boolean = false): DebugOverlayState {
    var visible by remember { mutableStateOf(initiallyVisible) }
    return remember(visible) {
        DebugOverlayState(visible) { visible = it }
    }
}

/** 调试状态栏的开关状态。 */
class DebugOverlayState(
    val visible: Boolean,
    private val setVisible: (Boolean) -> Unit,
) {
    fun toggle() = setVisible(!visible)
}

/**
 * 处理调试栏的快捷键。返回 true 表示事件已被消费。
 */
fun handleDebugShortcut(event: androidx.compose.ui.input.key.KeyEvent, state: DebugOverlayState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key == Key.D && event.isCtrlPressed && event.isShiftPressed) {
        state.toggle()
        return true
    }
    return false
}

/** 状态变化记录中的一条。 */
private data class StateChange(
    val anchor: LocalDate,
    val selected: LocalDate,
    val title: String,
    val gridRange: String,
    val mode: String,
)

/**
 * 调试状态栏本体。
 *
 * 除当前状态外，还保留最近若干次**变化**的快照。这使"点击后月份变了"
 * 这类问题可以被直接观察：对比相邻两条记录即可看出点击把哪个字段改成了什么。
 */
@Composable
fun DebugStatusBar(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val page = viewModel.page
    val today: LocalDate = viewModel.today

    // 记录状态变化历史。用 List 快照而非只留当前值，
    // 因为当前值无法区分"点击前就是错的"与"点击后被改错了"。
    val history = remember { mutableListOf<StateChange>() }
    val current = StateChange(
        anchor = viewModel.anchorDate,
        selected = viewModel.selectedDate,
        title = viewModel.pageTitle,
        gridRange = "${page.days.firstOrNull()?.date}~${page.days.lastOrNull()?.date}",
        mode = viewModel.viewMode.name,
    )
    // 只在状态真正变化时追加，避免每次重组都记一条
    if (history.isEmpty() || history.last() != current) {
        history += current
        while (history.size > 8) history.removeAt(0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 关键：必须限制高度并允许内部滚动。
            // 此前没有约束，调试栏会独占约 470px，把下方日历网格压成
            // 只有几像素高的细条 —— 网格上任何点击都无法命中目标格子。
            // 那个高度下记录到的"跳月"其实是点错了控件，属于被调试栏
            // 自身破坏布局而产生的假象。
            .heightIn(max = 240.dp)
            .background(Color(0xFF1F2937))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "调试状态",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFBBF24),
            )
            Text(
                text = "   Ctrl+Shift+D 关闭 · 状态变化 ${history.size - 1} 次",
                fontSize = 10.sp,
                color = Color(0xFF9CA3AF),
            )
        }

        // 操作序列放在最前面：这是排查"点击后状态被谁改了"最关键的证据。
        // 此前它在最底部，会被 Max height 的滚动区裁掉，用户截图时看不到。
        LocalActionLog.current?.let { log ->
            Text(
                text = "操作序列（最新在最下）· anchor / selected 均为「前→后」",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6EE7B7),
            )
            if (log.entries.isEmpty()) {
                Text(
                    text = "（暂无。请按 Ctrl+Shift+D 关闭再打开以清空历史，再复现问题）",
                    fontSize = 10.sp,
                    color = Color(0xFF9CA3AF),
                )
            }
            log.entries.forEachIndexed { i, e ->
                val isLatest = i == log.entries.lastIndex
                Text(
                    text = "#${i + 1} ${e.action}\n" +
                        "     anchor ${e.anchorBefore}→${e.anchorAfter}  " +
                        "selected ${e.selectedBefore}→${e.selectedAfter} ${e.result}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isLatest) Color(0xFF6EE7B7) else Color(0xFFD1D5DB),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(1.dp)
                    .background(Color(0xFF374151)),
            )
        }
        DebugLine("anchorDate", viewModel.anchorDate.toString(), Color(0xFF93C5FD))
        DebugLine("selectedDate", viewModel.selectedDate.toString(), Color(0xFF93C5FD))
        DebugLine("pageTitle", viewModel.pageTitle, Color(0xFFFDE68A))
        DebugLine(
            "viewMode",
            viewModel.viewMode.name,
            Color(0xFFA7F3D0),
        )
        DebugLine(
            "pageRange",
            "${page.days.firstOrNull()?.date} ~ ${page.days.lastOrNull()?.date}",
            Color(0xFFFDE68A),
        )
        DebugLine("pageAnchor", page.anchor.toString(), Color(0xFFFDE68A))
        DebugLine(
            "week",
            "${WeekNumbering.weekBasedYear(viewModel.selectedDate)}" +
                " 年 第 ${WeekNumbering.weekOfYear(viewModel.selectedDate)} 周",
            Color(0xFFC4B5FD),
        )
        DebugLine(
            "today / now",
            "$today / ${LocalDate.now()}",
            Color(0xFFFCA5A5),
        )
        DebugLine(
            "addTarget",
            viewModel.addTodoTargetDate?.toString() ?: "null",
            Color(0xFFFDBA74),
        )
        DebugLine(
            "dayInfo",
            viewModel.dayInfoDate?.toString() ?: "null",
            Color(0xFFFDBA74),
        )
        DebugLine(
            "todos(selected)",
            "${viewModel.selectedDateTodos.size} 条",
            Color(0xFFA7F3D0),
        )

        // 变化历史：最近的在最下，便于对照点击前后
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(1.dp)
                .background(Color(0xFF374151)),
        )
        Text(
            text = "变化历史（旧 → 新，最近一次在最下）",
            fontSize = 10.sp,
            color = Color(0xFF9CA3AF),
        )
        history.forEachIndexed { i, h ->
            val isLatest = i == history.lastIndex
            Text(
                text = "#${i + 1} anchor=${h.anchor} selected=${h.selected} " +
                    "标题=${h.title} 网格=${h.gridRange} [${h.mode}]",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isLatest) Color(0xFFFBBF24) else Color(0xFF9CA3AF),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(scheme.outline),
    )
}

@Composable
private fun DebugLine(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(120.dp)) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color(0xFF9CA3AF),
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = value,
            fontSize = 11.sp,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}
