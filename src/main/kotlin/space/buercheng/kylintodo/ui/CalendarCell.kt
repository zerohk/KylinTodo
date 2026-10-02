package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.domain.CalendarDay
import space.buercheng.kylintodo.domain.TodoPriority
import space.buercheng.kylintodo.domain.DayType
import java.time.LocalDate

/**
 * 单个日历格子。
 *
 * 布局严格对应需求约定：
 *  - 左上角：阿拉伯数字日期
 *  - 右上角：「+」号按钮，点击弹出待办添加弹窗
 *  - 右下角：农历文本或节气
 *
 * 另外按中国日历惯例补充：
 *  - 左上角日期旁显示「休」/「班」角标，对应法定放假与调休上班
 *  - 底部显示待办数量角标，对应需求 F-03「日节点显示当天待办」
 */
@Composable
fun CalendarCell(
    day: CalendarDay,
    isToday: Boolean,
    isSelected: Boolean,
    showTodoCount: Boolean,
    onSelect: (LocalDate) -> Unit,
    onAddTodo: (LocalDate) -> Unit,
    /** 双击格子弹出日期详情（对应需求变更） */
    onOpenDayInfo: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val outOfPeriodAlpha = if (day.isOutOfPeriod) 0.35f else 1f

    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = when {
                    isSelected -> scheme.primary
                    else -> scheme.outline.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(6.dp),
            )
            // 单击选中该日；双击弹出日期详情窗口
            .singleOrDoubleClick(
                onClick = { onSelect(day.date) },
                onDoubleClick = { onOpenDayInfo(day.date) },
            )
            // 调试探针（仅 -Dkylintodo.probe=true 生效）：
            // 记录该格子的实际窗口坐标与点击命中，用于排查"点到的格子
            // 与预期不一致"这类只在 GUI 层出现的问题。
            .recordCellBounds(day.date)
            .tapProbe("${day.date}")
            // 可测性标记：Compose 测试框架靠它精确定位到"哪一天"的格子。
            // 网格里同一天号可能出现多次（相邻月份溢出），只靠日期文本无法区分，
            // 因此测试与生产都用「日期 → tag」这一个统一规则。
            .testTag(cellTestTag(day.date)),
        color = when {
            isToday -> scheme.primaryContainer.copy(alpha = 0.45f)
            else -> scheme.surface
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 4.dp),
        ) {
            // ---------- 顶行：左上角日期 + 右上角「+」----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                DayNumber(
                    day = day,
                    isToday = isToday,
                    modifier = Modifier.alpha(outOfPeriodAlpha),
                )
                AddTodoButton(date = day.date, onClick = { onAddTodo(day.date) })
            }

            // ---------- 底部：待办数量 + 右下角农历/节气 ----------
            Box(modifier = Modifier.fillMaxSize()) {
                if (showTodoCount && day.todoCount > 0) {
                    TodoCountBadge(
                        count = day.todoCount,
                        maxPriorityLevel = day.maxPriorityLevel,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
                SubLabel(
                    day = day,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .alpha(outOfPeriodAlpha),
                )
            }
        }
    }
}

/**
 * 左上角：阿拉伯数字日期，可选「休」/「班」角标，今天以圆形高亮。
 */
@Composable
private fun DayNumber(
    day: CalendarDay,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (isToday) {
            // 今天：填充圆形，白字
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.gregorianDay.toString(),
                    style = gregorianDayTextStyle(),
                    color = scheme.onPrimary,
                )
            }
        } else {
            Text(
                text = day.gregorianDay.toString(),
                style = gregorianDayTextStyle(),
                color = holidayAwareDayColor(day),
            )
        }

        DayTypeBadge(day.dayType)
    }
}

/**
 * 节假日相关的日期颜色。
 *
 * 中国日历惯例：法定放假用红色，调休上班用绿色（表示这天要上班）。
 */
@Composable
private fun holidayAwareDayColor(day: CalendarDay): Color = when (day.dayType) {
    DayType.HOLIDAY -> HolidayRed
    DayType.WORKDAY -> WorkdayGreen
    DayType.NORMAL -> MaterialTheme.colorScheme.onSurface
}

/**
 * 「休」/「班」角标。
 *
 * 需求 F-01 要求显示中国大陆节假日「包括调休」，调休的本质就是
 * 周末需要上班，因此用绿色「班」字明确标注；法定放假用红色「休」字。
 *
 * ## 为什么不用固定尺寸的方框
 * 早先的实现是 `Box(Modifier.size(14.dp))` 里塞一个 9sp 的汉字。这类做法
 * 在麒麟（Noto Sans CJK）与 Windows（微软雅黑）上表现不同 —— 中文字形的
 * 实际墨迹高度与字体度量里的行高并不一致，某些字体下字身会超出方框，
 * 被 `clip(RoundedCornerShape(...))` 裁掉一角，看起来"字没显示完整"。
 *
 * 放大方框只是把问题推后（换个字体或字号仍会复现）。这里改为**让文字
 * 自己决定尺寸**：先用 `wrapContentSize(unbounded = true)` 解除父级传入的
 * 最大宽度/高度约束，再对文字加 padding 形成底色块。这样无论系统字体
 * 度量如何，字形都不会被裁剪。
 */
@Composable
private fun DayTypeBadge(type: DayType) {
    val (label, color) = when (type) {
        DayType.HOLIDAY -> "休" to HolidayRed
        DayType.WORKDAY -> "班" to WorkdayGreen
        DayType.NORMAL -> return
    }
    Box(
        modifier = Modifier
            // 解除父级约束，避免文字被压到方框尺寸以下
            .wrapContentSize(unbounded = true, align = Alignment.CenterStart)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
            .padding(horizontal = 2.5.dp, vertical = 0.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            // 不写 lineHeight：显式把 lineHeight 设成等于 fontSize 会在部分
            // 字体上裁掉字形的上下留白。交给字体自身度量更安全。
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 右上角「+」按钮 —— 点击弹出该日期的待办添加弹窗。 */
@Composable
private fun AddTodoButton(date: LocalDate, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(scheme.primary.copy(alpha = 0.12f))
            // 用 testTag 单独标记：它与格子整体的点击是两条独立路径，
            // 排查"点某天后月份回退"时必须能分别驱动。
            .testTag(UiTestTags.addButton(date))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "添加待办",
            tint = scheme.primary,
            modifier = Modifier.size(13.dp),
        )
    }
}

/**
 * 右下角：节气优先，其次农历。
 */
@Composable
private fun SubLabel(
    day: CalendarDay,
    modifier: Modifier = Modifier,
) {
    val text = day.subLabel
    if (text.isBlank()) return
    Text(
        text = text,
        style = subLabelTextStyle(),
        color = if (day.solarTerm != null) {
            SolarTermGreen
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * 待办数量角标。
 *
 * 已完成全部待办时显示对勾而非数字，给出「当天事情已清空」的正向反馈。
 */
@Composable
private fun TodoCountBadge(
    count: Int,
    maxPriorityLevel: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // 优先级由角标的背景色表达：高=红、中=琥珀、低=蓝、无=中性色。
    // 格子空间极小，只够传达一个信号，因此用当天最高优先级而非逐条着色。
    val priority = TodoPriority.fromLevel(maxPriorityLevel)
    val badgeColor = when (priority) {
        TodoPriority.HIGH -> priorityColor(TodoPriority.HIGH).copy(alpha = 0.22f)
        TodoPriority.MEDIUM -> priorityColor(TodoPriority.MEDIUM).copy(alpha = 0.22f)
        TodoPriority.LOW -> priorityColor(TodoPriority.LOW).copy(alpha = 0.22f)
        TodoPriority.NONE -> scheme.secondaryContainer.copy(alpha = 0.7f)
    }
    val contentColor = when (priority) {
        TodoPriority.HIGH -> priorityColor(TodoPriority.HIGH)
        TodoPriority.MEDIUM -> priorityColor(TodoPriority.MEDIUM)
        TodoPriority.LOW -> priorityColor(TodoPriority.LOW)
        TodoPriority.NONE -> scheme.onSecondaryContainer
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(badgeColor)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(9.dp),
        )
        Text(
            text = count.toString(),
            fontSize = 9.sp,
            color = contentColor,
        )
    }
}
