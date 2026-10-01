package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.domain.CalendarDay
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WeekdayHeader(page)
        GridBody(
            page = page,
            today = today,
            selectedDate = selectedDate,
            showTodoCount = showTodoCount,
            onSelectDate = onSelectDate,
            onAddTodo = onAddTodo,
        )
    }
}

/** 星期表头。 */
@Composable
private fun WeekdayHeader(page: CalendarPage) {
    if (page.mode == space.buercheng.kylintodo.domain.CalendarViewMode.DAY) return

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

/** 网格主体：按 [CalendarPage.columns] 分行渲染。 */
@Composable
private fun GridBody(
    page: CalendarPage,
    today: LocalDate,
    selectedDate: LocalDate,
    showTodoCount: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onAddTodo: (LocalDate) -> Unit,
) {
    val rows = page.days.chunked(page.columns)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        rows.forEach { rowDays ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                rowDays.forEach { day ->
                    CalendarCell(
                        day = day,
                        isToday = day.date == today,
                        isSelected = day.date == selectedDate,
                        showTodoCount = showTodoCount,
                        onSelect = onSelectDate,
                        onAddTodo = onAddTodo,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 需求要求月视图固定 6×7；若最后一行不足 7 格则补空占位，
                // 保持列宽一致，避免单元格被拉伸。
                repeat(page.columns - rowDays.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 日视图：单日大卡片。
 *
 * 复用 [CalendarCell] 的视觉语言，但放大字号并显示完整农历与节日名称。
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
