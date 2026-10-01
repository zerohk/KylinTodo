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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.domain.CalendarDay
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
            .clickable { onSelect(day.date) },
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
                AddTodoButton(onClick = { onAddTodo(day.date) })
            }

            // ---------- 底部：待办数量 + 右下角农历/节气 ----------
            Box(modifier = Modifier.fillMaxSize()) {
                if (showTodoCount && day.todoCount > 0) {
                    TodoCountBadge(
                        count = day.todoCount,
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
                    style = GregorianDayTextStyle,
                    color = scheme.onPrimary,
                )
            }
        } else {
            Text(
                text = day.gregorianDay.toString(),
                style = GregorianDayTextStyle,
                color = holidayAwareDayColor(day),
            )
        }

        DayTypeBadge(day.dayType)
    }
}

/**
 * 节假日相关的日期颜色。
 *
 * 中国日历惯例：法定放假与周末用红色，调休上班用中性色。
 */
@Composable
private fun holidayAwareDayColor(day: CalendarDay): Color = when (day.dayType) {
    DayType.HOLIDAY -> HolidayRed
    DayType.WORKDAY -> WorkdayGray
    DayType.NORMAL -> MaterialTheme.colorScheme.onSurface
}

/**
 * 「休」/「班」角标。
 *
 * 需求 F-01 要求显示中国大陆节假日「包括调休」，调休的本质就是
 * 周末需要上班，因此用一个显式的「班」字标注，避免用户误以为周末都休息。
 */
@Composable
private fun DayTypeBadge(type: DayType) {
    val (label, color) = when (type) {
        DayType.HOLIDAY -> "休" to HolidayRed
        DayType.WORKDAY -> "班" to WorkdayGray
        DayType.NORMAL -> return
    }
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 右上角「+」按钮 —— 点击弹出该日期的待办添加弹窗。 */
@Composable
private fun AddTodoButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(scheme.primary.copy(alpha = 0.12f))
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
        style = SubLabelTextStyle,
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
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(scheme.secondaryContainer.copy(alpha = 0.7f))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = scheme.onSecondaryContainer,
            modifier = Modifier.size(9.dp),
        )
        Text(
            text = count.toString(),
            fontSize = 9.sp,
            color = scheme.onSecondaryContainer,
        )
    }
}
