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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import space.buercheng.kylintodo.AppInfo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.domain.CalendarViewMode
import space.buercheng.kylintodo.domain.WeekNumbering

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
fun CalendarScreen(
    viewModel: AppViewModel,
    /** 界面左上角显示的应用名称，由设置提供 */
    appName: String = AppInfo.DEFAULT_DISPLAY_NAME,
) {
    // 周选择弹窗的显隐由本地状态管理 —— 它纯属视图层的瞬时交互，
    // 不必进入 ViewModel，也就不污染可测试的状态模型。
    var showWeekPicker by remember { mutableStateOf(false) }

    if (showWeekPicker) {
        WeekPickerDialog(
            year = viewModel.currentWeekYear,
            selectedWeek = WeekNumbering.weekOfYear(viewModel.selectedDate),
            onSelect = { week ->
                viewModel.goToWeek(viewModel.currentWeekYear, week)
                showWeekPicker = false
            },
            onDismiss = { showWeekPicker = false },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().probe("根 Column")) {
            CalendarToolbar(
                viewModel = viewModel,
                appName = appName,
                onOpenWeekPicker = { showWeekPicker = true },
                modifier = Modifier.probe("工具栏"),
            )

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

/** 顶部工具栏：标题、翻页、回今天、周数、视图切换、新增。 */
@Composable
private fun CalendarToolbar(
    viewModel: AppViewModel,
    appName: String,
    onOpenWeekPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = appName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 名称可自定义，超长时靠 ellipsis 截断而不是把工具栏挤变形
            modifier = Modifier
                .widthIn(max = 180.dp)
                .testTag(UiTestTags.APP_TITLE),
        )

        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = viewModel::goPrevious,
                modifier = Modifier
                    .recordBounds("btn:prev")
                    .testTag(UiTestTags.PREV_BUTTON),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一页")
            }
            Text(
                text = viewModel.pageTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .recordBounds("title"),
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(
                onClick = viewModel::goNext,
                modifier = Modifier
                    .recordBounds("btn:next")
                    .testTag(UiTestTags.NEXT_BUTTON),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一页")
            }
            OutlinedButton(
                onClick = viewModel::goToday,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .testTag(UiTestTags.TODAY_BUTTON),
            ) {
                Text("今天", fontSize = 13.sp)
            }
        }

        Box(modifier = Modifier.weight(1f))

        // 周数指示器：显示当前选中日期所属的 ISO 周，点击可选择周
        WeekIndicatorButton(
            label = viewModel.currentWeekLabel,
            onClick = onOpenWeekPicker,
            modifier = Modifier.padding(end = 10.dp),
        )

        // 桌面小窗开关（需求第 5 条方案 A）
        OutlinedButton(
            onClick = viewModel::toggleWidget,
            modifier = Modifier.padding(end = 8.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = if (viewModel.widgetVisible) "关闭小窗" else "桌面小窗",
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // 设置入口（需求反馈第 5 条）
        // 齿轮只有图形，悬浮提示是必要的自解释手段
        TooltipIconButton(
            icon = Icons.Filled.Settings,
            tooltip = "设置（皮肤 / 字号 / 透明度 / 自启动 / 节假日 / 日志）",
            onClick = viewModel::openSettings,
            modifier = Modifier.padding(end = 4.dp),
            iconSize = 20.dp,
            buttonSize = 34.dp,
        )

        ViewModeSwitcher(
            current = viewModel.viewMode,
            onChange = viewModel::changeViewMode,
        )

        Button(
            onClick = { viewModel.openSearch() },
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text("搜索", fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
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
            // 多选开关（需求 3）。有未完成或已完成的待办时才显示 ——
            // 列表为空时进入多选没有意义，按钮只会占位置。
            if (viewModel.selectedDateTodos.isNotEmpty()) {
                TooltipIconButton(
                    icon = if (viewModel.todoSelectionMode) {
                        Icons.Filled.Close
                    } else {
                        Icons.Filled.Check
                    },
                    tooltip = if (viewModel.todoSelectionMode) {
                        "退出多选"
                    } else {
                        "多选：可批量完成或删除"
                    },
                    onClick = {
                        viewModel.changeTodoSelectionMode(!viewModel.todoSelectionMode)
                    },
                    highlighted = viewModel.todoSelectionMode,
                    buttonSize = 28.dp,
                    iconSize = 18.dp,
                )
            }
            TooltipIconButton(
                icon = Icons.Filled.Add,
                tooltip = "为选中日期添加待办",
                onClick = { viewModel.openAddTodo(viewModel.selectedDate) },
                tint = MaterialTheme.colorScheme.primary,
                buttonSize = 28.dp,
                iconSize = 18.dp,
            )
        }

        // 多选操作栏（需求 3）：仅在多选模式下占位，显示已选数量与批量动作
        if (viewModel.todoSelectionMode) {
            val visibleIds = viewModel.selectedDateTodos.map { it.id }.toSet()
            BatchActionBar(
                selectedCount = viewModel.selectedTodoIds.size,
                totalCount = visibleIds.size,
                allSelected = visibleIds.isNotEmpty() &&
                    viewModel.selectedTodoIds.containsAll(visibleIds),
                onToggleSelectAll = viewModel::toggleSelectAll,
                onComplete = { viewModel.completeSelected(true) },
                onUncomplete = { viewModel.completeSelected(false) },
                onDelete = { viewModel.deleteSelected() },
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        TodoList(
            todos = viewModel.selectedDateTodos,
            onToggle = viewModel::toggleCompleted,
            onDelete = viewModel::deleteTodo,
            onAdd = { viewModel.openAddTodo(viewModel.selectedDate) },
            modifier = Modifier.weight(1f),
            selectionMode = viewModel.todoSelectionMode,
            selectedIds = viewModel.selectedTodoIds,
            onToggleSelection = viewModel::toggleTodoSelection,
        )
    }
}

/**
 * 批量操作栏（需求 3）。
 *
 * ## 为什么分两行（曾出现按钮涨成竖排）
 * 第一版把「已选 N 条 / 全选 / 完成 / 取消完成 / 删除」**全塞进一行**。
 * 侧栏只有 300dp 宽，装不下 5 个控件，Material 的 TextButton 只能把
 * 「删除」二字竖排换行 —— 截图上就是「删/除」上下两个字。
 *
 * 现在拆成两行：第一行只放状态与全选（不需要频繁点击），
 * 第二行放三个动作按钮，各自有足够宽度，也不会再换行。
 *
 * ## 为什么删除要二次确认
 * 多选模式下误触删除会**整批丢失**（待办没有回收站）。
 * 用户明确要求加确认，这里照做。
 */
@Composable
fun BatchActionBar(
    selectedCount: Int,
    totalCount: Int,
    /** 是否已全选当前列表 */
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onComplete: () -> String,
    onUncomplete: () -> String,
    onDelete: () -> String,
    modifier: Modifier = Modifier,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            // ---------- 第一行：状态 + 全选 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已选 $selectedCount 条",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onToggleSelectAll) {
                    Text(text = if (allSelected) "取消全选" else "全选", fontSize = 11.sp)
                }
            }

            // ---------- 第二行：动作按钮 ----------
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton("完成", onClick = { message = onComplete() })
                ActionButton("取消完成", onClick = { message = onUncomplete() })
                ActionButton(
                    text = "删除",
                    isDestructive = true,
                    onClick = {
                        // 选了 0 条时不必弹确认 —— 直接给出提示更省一步
                        if (selectedCount == 0) "请先选择待办" else {
                            confirmDelete = true
                            null
                        }.also { if (it != null) message = it }
                    },
                )
            }

            message?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "确认删除",
            message = "将删除选中的 $selectedCount 条待办，此操作无法撤销。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                message = onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * 操作栏里的单个动作按钮。
 *
 * 与 `TextButton` 的区别：固定最小宽度。默认的 TextButton 在窄容器里
 * 会把文字竖排换行（这正是「删除」曾显示成两行的原因），
 * 给定宽度下限可以稳定避免。
 */
@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            color = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}
