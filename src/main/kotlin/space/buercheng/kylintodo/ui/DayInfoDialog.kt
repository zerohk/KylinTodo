package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.domain.CalendarDay
import space.buercheng.kylintodo.domain.DayType
import space.buercheng.kylintodo.domain.TodoItem
import java.time.LocalDate
import kotlin.math.roundToInt

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
    viewModel: AppViewModel,
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
    val density = LocalDensity.current.density

    // 拖动偏移（像素）。初始 (0,0) 即居中 —— 见下方 Box 的 align(Alignment.Center)。
    //
    // 为什么不"跟随日期格子"定位：那需要把格子的屏幕坐标从 CalendarGrid
    // 一层层传到 Main 的弹窗调用处，改动面很大；而且网格会随翻页/换视图
    // 移动，坐标会失效。**居中 + 可拖动**用很小的改动达成同样目的：
    // 用户想让它贴着某天，拖过去即可。
    var dragX by remember(selectedDate) { mutableStateOf(0f) }
    var dragY by remember(selectedDate) { mutableStateOf(0f) }

    // 铺满整个内容区，让弹窗可以在窗口内任意拖动而不被裁剪
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                // 居中作为初始位置；offset 施加拖动位移
                .align(Alignment.Center)
                .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                .width(460.dp)
                .heightIn(min = 320.dp, max = 560.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                // 双击内容区任意空白处继续添加待办（需求明确要求）。
                //
                // 放在 Column 而不是 Surface 上：标题行已占用拖动的手势，
                // 若再在 Surface 上叠加 tap 检测会与标题行的手势竞争。
                // 这里只作用于标题行以下的内容区，与拖动互不干扰。
                .pointerInput(selectedDate) {
                    detectTapGestures(
                        onDoubleTap = { onAddTodoForDate(selectedDate) },
                    )
                },
        ) {
            // ---------------- 标题行（同时是拖动把手） ----------------
            //
            // 整个标题行都可拖动，而不只是某个小图标：把手越大越好点中，
            // 这是无边框弹窗的通行做法。
            //
            // 注意 pointerInput 的 key 用 Unit：若用 selectedDate，
            // 翻到另一天时会重建手势协程，正在进行的拖动会被打断。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            dragX += dragAmount.x
                            dragY += dragAmount.y
                        }
                    },
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
            //
            // 多选状态直接用 viewModel 的全局选中集合（与右侧栏是同一份），
            // 这样两处的勾选互不冲突，用户的行为预期一致。
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("当天待办 ${todosOfSelectedDate.size}")
                Spacer(modifier = Modifier.weight(1f))
                if (todosOfSelectedDate.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            viewModel.changeTodoSelectionMode(!viewModel.todoSelectionMode)
                        },
                    ) {
                        Text(
                            text = if (viewModel.todoSelectionMode) "退出多选" else "多选",
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            if (viewModel.todoSelectionMode) {
                val visibleIds = todosOfSelectedDate.map { it.id }.toSet()
                BatchActionBar(
                    selectedCount = viewModel.selectedTodoIds.size,
                    totalCount = visibleIds.size,
                    allSelected = visibleIds.isNotEmpty() &&
                        viewModel.selectedTodoIds.containsAll(visibleIds),
                    onToggleSelectAll = viewModel::toggleSelectAll,
                    onComplete = { viewModel.completeSelected(true) },
                    onUncomplete = { viewModel.completeSelected(false) },
                    onDelete = { viewModel.deleteSelected() },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            if (todosOfSelectedDate.isEmpty()) {
                EmptyHint("这一天还没有待办")
            } else {
                TodoRows(
                    items = todosOfSelectedDate,
                    onToggleTodo = onToggleTodo,
                    onDeleteTodo = onDeleteTodo,
                    selectionMode = viewModel.todoSelectionMode,
                    selectedIds = viewModel.selectedTodoIds,
                    onToggleSelection = viewModel::toggleTodoSelection,
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
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: (TodoItem) -> Unit = {},
) {
    // 单条删除前先确认（用户要求：所有删除操作都要确认）。
    // 用「待确认的条目」而非布尔值：未来若同时存在多处删除入口，
    // 布尔值无法表达"要删哪一条"。
    var pendingDelete by remember { mutableStateOf<TodoItem?>(null) }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items, key = { it.id }) { item ->
            CompactTodoRow(
                item = item,
                onToggle = { onToggleTodo(item) },
                onDelete = { pendingDelete = item },
                selectionMode = selectionMode,
                selected = item.id in selectedIds,
                onToggleSelection = { onToggleSelection(item) },
            )
        }
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "确认删除",
            message = "将删除待办「${target.text.take(40)}」，此操作无法撤销。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                onDeleteTodo(target)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** 弹窗内的紧凑待办行：复选框 + 文本（完成后删除线）+ 删除。 */
@Composable
private fun CompactTodoRow(
    item: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selectionMode && selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .then(
                if (selectionMode) {
                    Modifier.clickable(onClick = onToggleSelection)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = if (selectionMode) selected else item.isCompleted,
            onCheckedChange = { if (selectionMode) onToggleSelection() else onToggle() },
        )
        Text(
            text = item.text,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            textDecoration = if (item.isCompleted && !selectionMode) {
                TextDecoration.LineThrough
            } else {
                null
            },
            color = if (item.isCompleted && !selectionMode) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // 多选模式下隐藏单条删除：此时删除是批量操作，留着一排小垃圾桶
        // 既占位置又容易误触（本想勾选却删掉了一条）
        if (!selectionMode) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
