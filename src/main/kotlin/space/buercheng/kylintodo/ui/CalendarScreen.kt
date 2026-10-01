package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.domain.CalendarViewMode

/** 布局探针开关：仅在 -Dkylintodo.probe=true 时输出测量结果，用于排查布局问题。 */
internal val PROBE_ENABLED: Boolean = System.getProperty("kylintodo.probe") == "true"

internal fun probeLog(message: String) {
    if (PROBE_ENABLED) println("[PROBE] $message")
}

private fun Modifier.probe(tag: String): Modifier =
    if (PROBE_ENABLED) onSizeChanged { probeLog("$tag = ${it.width} x ${it.height}") } else this

/** 分隔线统一颜色。 */
@Composable
private fun dividerColor() = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

/**
 * 主界面：顶部工具栏 + 日历区 + 右侧待办面板。
 *
 * 对应需求 F-01（日历视图，含农历与节假日）、F-03（月视图下日节点显示待办）。
 *
 * ## 布局实现说明（重要）
 * 分隔线刻意**不使用** `Divider` + `fillMaxHeight()`：实测该组合会导致同一
 * `Row` 内**所有**子项测量宽度回退为 0（Row 宽 1264，而日历列、分隔线、
 * 固定 300dp 的侧栏全部测到 0），表现为整个日历被压成一条竖线。
 * 因此这里改用显式尺寸的 `Box`（宽 1dp、填满高度）作为竖直分隔线。
 */
@Composable
fun CalendarScreen(viewModel: AppViewModel) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().probe("根 Column")) {
            CalendarToolbar(viewModel = viewModel, modifier = Modifier.probe("工具栏"))

            // 水平分隔线：显式高度，避免依赖 Divider 的固有尺寸行为
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor()),
            )

            Row(modifier = Modifier.fillMaxWidth().weight(1f).probe("内容区 Row")) {
                // ---------- 左：日历区 ----------
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(6.dp)
                        .probe("日历列"),
                ) {
                    // 三种视图统一为「一行七列的日期网格」：
                    // 日/周视图 1 行，月视图 6 行，行高一致。
                    // 原先日视图在此处插入 DayDetailHeader，会把网格挤成半屏，
                    // 与"日视图也显示七天"的要求冲突，故移除；
                    // 选中日的详细信息改由双击弹出的日期详情窗口呈现。
                    CalendarGrid(
                        page = viewModel.page,
                        today = viewModel.today,
                        selectedDate = viewModel.selectedDate,
                        showTodoCount = true,
                        onSelectDate = viewModel::selectDate,
                        onAddTodo = viewModel::openAddTodo,
                        onOpenDayInfo = viewModel::openDayInfo,
                        modifier = Modifier.weight(1f).probe("日历网格"),
                    )
                }

                // 竖直分隔线：固定 1dp 宽并显式填满高度
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(dividerColor())
                        .probe("竖分隔线"),
                )

                // ---------- 右：选中日期的待办 ----------
                TodoSidePanel(
                    viewModel = viewModel,
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .probe("侧栏"),
                )
            }
        }
    }
}

/** 顶部工具栏：标题、翻页、回今天、视图切换、新增。 */
@Composable
private fun CalendarToolbar(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "麒麟日历",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::goPrevious) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一页")
            }
            Text(
                text = viewModel.pageTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = viewModel::goNext) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一页")
            }
            OutlinedButton(
                onClick = viewModel::goToday,
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text("今天", fontSize = 13.sp)
            }
        }

        Box(modifier = Modifier.weight(1f))

        ViewModeSwitcher(
            current = viewModel.viewMode,
            onChange = viewModel::changeViewMode,
        )

        Button(
            onClick = { viewModel.openAddTodo() },
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text("新增待办", fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** 日 / 周 / 月视图切换（需求 F-01）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeSwitcher(
    current: CalendarViewMode,
    onChange: (CalendarViewMode) -> Unit,
) {
    val modes = listOf(
        CalendarViewMode.DAY to "日",
        CalendarViewMode.WEEK to "周",
        CalendarViewMode.MONTH to "月",
    )
    SingleChoiceSegmentedButtonRow {
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            ) {
                Text(label, fontSize = 13.sp)
            }
        }
    }
}

/**
 * 右侧待办面板。
 *
 * 展示选中日期的农历、节日与待办列表。需求 F-01 要求日历显示农历与节假日，
 * 这里在列表上方复述一遍选中日的信息，便于用户确认自己点的是哪一天。
 */
@Composable
private fun TodoSidePanel(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val day = viewModel.selectedCalendarDay
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
    ) {
        // ---------- 日期信息 ----------
        Text(
            text = "${day.date.monthValue} 月 ${day.date.dayOfMonth} 日",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (day.lunarFullText.isNotBlank()) {
                Text(
                    text = day.lunarFullText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            day.solarTerm?.let {
                Text(text = it, fontSize = 12.sp, color = SolarTermGreen)
            }
        }
        day.holidayName?.let { name ->
            val label = when (day.dayType) {
                space.buercheng.kylintodo.domain.DayType.WORKDAY -> "$name（调休上班）"
                else -> name
            }
            Text(
                text = label,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
                color = if (day.dayType == space.buercheng.kylintodo.domain.DayType.WORKDAY) {
                    WorkdayGray
                } else {
                    HolidayRed
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
                .background(dividerColor()),
        )

        // ---------- 待办列表 ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "待办事项",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = " ${viewModel.selectedDateTodos.size}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { viewModel.openAddTodo(viewModel.selectedDate) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "为选中日期添加待办",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        TodoList(
            todos = viewModel.selectedDateTodos,
            onToggle = viewModel::toggleCompleted,
            onDelete = viewModel::deleteTodo,
            onAdd = { viewModel.openAddTodo(viewModel.selectedDate) },
            modifier = Modifier.weight(1f),
        )
    }
}
