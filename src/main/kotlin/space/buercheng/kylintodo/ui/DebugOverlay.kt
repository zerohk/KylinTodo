package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * 用户报告「点击日期后月份跳到上一个月」，但状态模型的穷举测试与运行时
 * 插桩都无法复现，且 Compose Desktop 无法用合成输入自动化点击。
 * 因此提供一个**可随时开关的界面状态栏**：用户按下快捷键后，界面顶部会
 * 显示锚点、选中日、网格范围、周号等全部关键状态。
 *
 * 这样用户复现问题时只需截图，就能看到点击**实际发生了什么**，
 * 无需翻日志、也无需我远程猜测。
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

/**
 * 调试状态栏本体：把 ViewModel 的关键状态平铺出来。
 *
 * 字段选择针对当前排查的问题：
 *  - anchor / selected：锚点与选中日是否如预期
 *  - page：网格实际覆盖范围（月视图为 42 天）
 *  - pageAnchor：CalendarPage 自己的锚点，应为锚点日期
 *  - 周号：与周数显示联动
 *  - today：判断是否发生了"跳回今天"
 */
@Composable
fun DebugStatusBar(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val page = viewModel.page
    val today: LocalDate = viewModel.today

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1F2937))
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
                text = "   Ctrl+Shift+D 关闭",
                fontSize = 10.sp,
                color = Color(0xFF9CA3AF),
            )
        }
        DebugLine("anchorDate", viewModel.anchorDate.toString(), Color(0xFF93C5FD))
        DebugLine("selectedDate", viewModel.selectedDate.toString(), Color(0xFF93C5FD))
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
        DebugLine("pageTitle", viewModel.pageTitle, Color(0xFFFDE68A))
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
