package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import space.buercheng.kylintodo.domain.WeekNumbering

/**
 * 工具栏上的周数按钮。
 *
 * 显示当前选中日期所属的 ISO 周，点击后打开周选择弹窗。
 * 把这个信息常驻在顶部，符合国内日历软件的使用习惯 ——
 * 用户经常需要按周安排工作，需要一眼看到"现在是第几周"。
 */
@Composable
fun WeekIndicatorButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickableNoRipple(onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "选择周",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 周选择弹窗。
 *
 * 为什么不用下拉菜单：一年有 52~53 周，纵向菜单会超出一屏高度且难以扫视。
 * 这里改为按年分组的网格，每行 6 个，一屏内即可看全并快速定位。
 */
@Composable
fun WeekPickerDialog(
    year: Int,
    selectedWeek: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val weeks = WeekNumbering.allWeeksOfYear(year)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(520.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$year 年",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "共 ${weeks.size} 周 · 周一为一周之首",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 每行 6 个，按周号顺序排列
                    items(weeks.chunked(6), key = { it.first().first }) { rowWeeks ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowWeeks.forEach { (week, _) ->
                                // weight 是 RowScope 的扩展，WeekCell 内部拿不到，
                                // 因此由这里构造后传入
                                WeekCell(
                                    year = year,
                                    week = week,
                                    selected = week == selectedWeek,
                                    onClick = { onSelect(week) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // 补足最后一行的占位，保持列宽一致
                            repeat(6 - rowWeeks.size) {
                                Box(modifier = Modifier.weight(1f).height(50.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单个周选项。第一行显示周号，第二行显示起止日期。 */
@Composable
private fun WeekCell(
    year: Int,
    week: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // 由年份 + 周号反推起止日期，让用户能判断这一周具体覆盖哪几天
    val monday = WeekNumbering.mondayOfWeek(year, week)
    val sunday = monday.plusDays(6)
    val range = "${monday.monthValue}/${monday.dayOfMonth}" +
        "-${sunday.monthValue}/${sunday.dayOfMonth}"

    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) scheme.primary.copy(alpha = 0.18f)
                else scheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "第 $week 周",
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) scheme.primary else scheme.onSurface,
            )
            Text(
                text = range,
                fontSize = 9.sp,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
