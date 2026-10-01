package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.domain.CalendarDay
import space.buercheng.kylintodo.domain.CalendarGridBuilder
import space.buercheng.kylintodo.domain.CalendarPage
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 周一至周日的表头标签。
 *
 * 中国日历惯例以周一为一周之首，与 `CalendarGridBuilder` 的网格起始保持一致。
 * 周末（六、日）用红色标注，符合国内日历习惯。
 */
private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 日历网格。
 *
 * 月视图输出 6 行 × 7 列共 42 格（需求明确要求），
 * 行高以 `weight` 均分父容器高度，因此窗口缩放时不会出现滚动条或布局跳动。
 */
@Composable
fun CalendarGrid(
    page: CalendarPage,
    today: LocalDate,
    selectedDate: LocalDate,
    showTodoCount: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onAddTodo: (LocalDate) -> Unit,
    onOpenDayInfo: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WeekdayHeader()
        GridBody(
            page = page,
            today = today,
            selectedDate = selectedDate,
            showTodoCount = showTodoCount,
            onSelectDate = onSelectDate,
            onAddTodo = onAddTodo,
            onOpenDayInfo = onOpenDayInfo,
        )
    }
}

/**
 * 星期表头。
 *
 * 三种视图都以一行七列呈现，因此**都需要表头** —— 早先这里在日视图下
 * 直接 return，导致日视图缺少星期标识，现已取消该分支。
 */
@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        WEEKDAY_LABELS.forEachIndexed { index, label ->
            val isWeekend = index >= 5
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isWeekend) {
                        HolidayRed
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * 网格主体。
 *
 * ## 行高策略（对应"日/周视图行高与月视图一致"的要求）
 * 月视图固定 6 行，每行高度 = 网格可用高度 / 6。
 * 日视图与周视图只有 1 行，若用 weight(1f) 会被拉伸到整屏高，
 * 与月视图格子尺寸明显不一致。
 *
 * 实现方式：先用 [BoxWithConstraints] 取得**确定的**可用尺寸，再以
 * `可用高度 / 6` 作为每行固定高度渲染。刻意不用自定义 Layout + weight
 * 的方案 —— 实测该组合下每行只被分到约 20px，单元格被压成细条。
 */
@Composable
private fun GridBody(
    page: CalendarPage,
    today: LocalDate,
    selectedDate: LocalDate,
    showTodoCount: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onAddTodo: (LocalDate) -> Unit,
    onOpenDayInfo: (LocalDate) -> Unit,
) {
    val rows: List<List<CalendarDay>> = page.days.chunked(page.columns)

    // 以月视图的 6 行作为基准，保证三种视图格子等高
    val baselineRows = (CalendarGridBuilder.MONTH_CELL_COUNT / page.columns).coerceAtLeast(1)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 该值在 BoxWithConstraints 作用域内是确定值，可直接参与计算
        val rowHeight = maxHeight / baselineRows
        if (PROBE_ENABLED) {
            probeLog("GridBody maxW=$maxWidth maxH=$maxHeight baseline=$baselineRows rowH=$rowHeight rows=${rows.size}")
        }

        Column(modifier = Modifier.fillMaxSize()) {
            rows.forEach { rowDays ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                ) {
                    rowDays.forEach { day ->
                        CalendarCell(
                            day = day,
                            isToday = day.date == today,
                            isSelected = day.date == selectedDate,
                            showTodoCount = showTodoCount,
                            onSelect = onSelectDate,
                            onAddTodo = onAddTodo,
                            onOpenDayInfo = onOpenDayInfo,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 最后一行不足 7 格时补空占位，保持列宽一致
                    repeat(page.columns - rowDays.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 日详情头：单日信息大卡片（完整农历、节气、节日）。
 *
 * 当前三种视图统一为「一行七列的日期网格」，因此本组件**暂未接入界面** ——
 * 选中日的详细信息改由双击弹出的 [DayInfoDialog] 呈现。
 * 保留它是因为日视图若将来需要"网格 + 详情头"的组合，这里是现成的实现，
 * 且其排版已按麒麟系统的中文显示调校过。请勿当作遗留代码删除。
 */
@Composable
fun DayDetailHeader(
    day: CalendarDay,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${day.date.year} 年 ${day.date.monthValue} 月 ${day.date.dayOfMonth} 日",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = "星期" + chineseWeekday(day.date.dayOfWeek),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (day.lunarFullText.isNotBlank()) {
                Text(
                    text = day.lunarFullText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            day.solarTerm?.let {
                Text(text = it, fontSize = 13.sp, color = SolarTermGreen)
            }
            when (day.dayType) {
                space.buercheng.kylintodo.domain.DayType.HOLIDAY -> Text(
                    text = day.holidayName ?: "法定假日",
                    fontSize = 13.sp,
                    color = HolidayRed,
                )

                space.buercheng.kylintodo.domain.DayType.WORKDAY -> Text(
                    text = "调休上班" + (day.holidayName?.let { "（$it）" } ?: ""),
                    fontSize = 13.sp,
                    color = WorkdayGray,
                )

                space.buercheng.kylintodo.domain.DayType.NORMAL -> Unit
            }
        }
    }
}

private fun chineseWeekday(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}
