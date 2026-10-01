package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.domain.CalendarDay
import space.buercheng.kylintodo.domain.DayType
import space.buercheng.kylintodo.domain.TodoItem
import java.time.LocalDate

/**
 * 日期详情弹窗。
 *
 * 对应需求变更：双击日历中的某一天弹出该窗口，用于
 *  1. 查看该日已添加的待办（无则为空并给出引导）
 *  2. 在窗口内继续添加待办（双击窗口任意空白处，或点右上角「+」）
 *
 * 之所以不止展示"当天"，还列出同周其余日期：用户双击某天往往是想安排
 * 这一周的行程，一并显示可减少反复开关弹窗。
 */
@Composable
fun DayInfoDialog(
    selectedDate: LocalDate,
    /** 同周的日期（用于展示每周的行高与月视图一致） */
    weekDays: List<CalendarDay>,
    /** 选中日的待办 */
    todosOfSelectedDate: List<TodoItem>,
    /** 同周其余日期的待办，键为日期 */
    todosOfOtherDays: Map<LocalDate, List<TodoItem>>,
    onDismiss: () -> Unit,
    onToggleTodo: (TodoItem) -> Unit,
    onDeleteTodo: (TodoItem) -> Unit,
    onAddTodoForDate: (LocalDate) -> Unit,
) {
    val selectedDay = weekDays.firstOrNull { it.date == selectedDate }

    Surface(
        modifier = Modifier
            .width(460.dp)
            .heightIn(min = 320.dp, max = 560.dp)
            // 双击弹窗空白处继续添加待办（需求明确要求）
            .pointerInput(selectedDate) {
                detectTapGestures(onDoubleTap = { onAddTodoForDate(selectedDate) })
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ---------------- 标题行 ----------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDateWithWeekday(selectedDate),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        selectedDay?.lunarFullText?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        selectedDay?.solarTerm?.let {
                            Text(text = it, fontSize = 12.sp, color = SolarTermGreen)
                        }
                        selectedDay?.holidayName?.let { name ->
                            val isWork = selectedDay.dayType == DayType.WORKDAY
                            Text(
                                text = if (isWork) "$name（调休上班）" else name,
                                fontSize = 12.sp,
                                color = if (isWork) WorkdayGreen else HolidayRed,
                            )
                        }
                    }
                }
                IconButton(onClick = { onAddTodoForDate(selectedDate) }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "添加待办",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            Text(
                text = "双击此处可继续添加待办",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            )

            // ---------------- 选中日的待办 ----------------
            SectionTitle("当天待办 ${todosOfSelectedDate.size}")

            if (todosOfSelectedDate.isEmpty()) {
                EmptyHint("这一天还没有待办")
            } else {
                TodoRows(
                    items = todosOfSelectedDate,
                    onToggleTodo = onToggleTodo,
                    onDeleteTodo = onDeleteTodo,
                )
            }

            // ---------------- 同周其余日期 ----------------
            val others = weekDays
                .filter { it.date != selectedDate }
                .mapNotNull { d ->
                    val list = todosOfOtherDays[d.date].orEmpty()
                    if (list.isEmpty()) null else d.date to list
                }

            if (others.isNotEmpty()) {
                Box(modifier = Modifier.padding(top = 12.dp))
                SectionTitle("本周其余安排")
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    others.forEach { (date, list) ->
                        item(key = "h-$date") {
                            Text(
                                text = "${date.monthValue}月${date.dayOfMonth}日",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                        items(list, key = { it.id }) { item ->
                            CompactTodoRow(
                                item = item,
                                onToggle = { onToggleTodo(item) },
                                onDelete = { onDeleteTodo(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun TodoRows(
    items: List<TodoItem>,
    onToggleTodo: (TodoItem) -> Unit,
    onDeleteTodo: (TodoItem) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items, key = { it.id }) { item ->
            CompactTodoRow(
                item = item,
                onToggle = { onToggleTodo(item) },
                onDelete = { onDeleteTodo(item) },
            )
        }
    }
}

/** 弹窗内的紧凑待办行：复选框 + 文本（完成后删除线）+ 删除。 */
@Composable
private fun CompactTodoRow(
    item: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.isCompleted, onCheckedChange = { onToggle() })
        Text(
            text = item.text,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
            color = if (item.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
