package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.domain.CalendarDay
import space.buercheng.kylintodo.domain.DayType
import space.buercheng.kylintodo.domain.TodoItem

/**
 * 桌面小窗（"小组件"）。
 *
 * 对应需求第 5 条，采用方案 A：一个无系统边框、可拖动、可置顶的常驻小窗，
 * 显示选中日期的待办。选择该方案而非 UKUI 面板插件，原因见 README ——
 * 麒麟 V10 的 UKUI 没有通用的第三方小组件接口，写面板插件会是另一个
 * 依赖麒麟专有 API 的独立项目，且无法在 Windows 上开发验证。
 *
 * 交互约定：
 *  - 顶部标题条为拖动把手（无边框窗口没有系统标题栏可拖）
 *  - 列表项可勾选完成、可删除
 *  - 底部「添加」在弹窗内新增待办
 *  - 右上角两个按钮：回到主窗口 / 关闭小窗
 */
@Composable
fun DesktopWidgetScreen(
    day: CalendarDay,
    todos: List<TodoItem>,
    onToggle: (TodoItem) -> Unit,
    onDelete: (TodoItem) -> Unit,
    onAdd: () -> Unit,
    onOpenMain: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    /** 当前是否置顶（需求 5）。置顶时星形按钮以主色高亮。 */
    pinned: Boolean,
    /** 切换置顶状态（需求 5） */
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val doneCount = todos.count { it.isCompleted }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.primary.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---------- 拖动把手 + 标题 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.primary.copy(alpha = 0.12f))
                    .windowDrag(onDrag)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${day.date.monthValue}月${day.date.dayOfMonth}日 " +
                            chineseWeekdayLabel(day.date.dayOfWeek.value),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    )
                    // 农历 / 节气 / 节日，三者取有值的部分拼接
                    val subtitle = buildList {
                        day.solarTerm?.let { add(it) }
                        if (day.lunarFullText.isNotBlank()) add(day.lunarFullText)
                        if (day.dayType == DayType.HOLIDAY) day.holidayName?.let { add(it) }
                    }.joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 10.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // 置顶开关（需求 5）：点击在「始终置顶」与「不置顶」间切换。
                // 置顶时图标用主色高亮，一眼可辨当前状态。
                // 三个按钮都带悬浮提示：图标本身不自解释（星形尤其容易被误认为收藏）
                TooltipIconButton(
                    icon = Icons.Filled.Star,
                    tooltip = if (pinned) "取消置顶（当前已置顶）" else "置顶显示",
                    onClick = onTogglePin,
                    highlighted = pinned,
                    buttonSize = 26.dp,
                    iconSize = 15.dp,
                )
                TooltipIconButton(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    tooltip = "打开主窗口",
                    onClick = onOpenMain,
                    tint = scheme.primary,
                    buttonSize = 26.dp,
                    iconSize = 15.dp,
                )
                TooltipIconButton(
                    icon = Icons.Filled.Close,
                    tooltip = "关闭小窗",
                    onClick = onClose,
                    buttonSize = 26.dp,
                    iconSize = 15.dp,
                )
            }

            // ---------- 进度 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (todos.isEmpty()) "今日待办" else "已完成 $doneCount / ${todos.size}",
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.weight(1f))
                // 不要给 TextButton 设固定 height：Material3 的 TextButton 自带
                // 上下各约 8dp 的 contentPadding，固定成 26dp 后留给文字的空间
                // 不足 10dp，11sp 的中文字形会被裁掉。改为用紧凑的内边距让按钮
                // 按内容自适应高度。
                TextButton(
                    onClick = onAdd,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                    Text("添加", fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp))
                }
            }

            // ---------- 待办列表 ----------
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (todos.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "这一天还没有待办",
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onAdd) {
                            Text("添加一条", fontSize = 11.sp)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(todos, key = { it.id }) { item ->
                            WidgetTodoRow(
                                item = item,
                                onToggle = { onToggle(item) },
                                onDelete = { onDelete(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 小窗内的紧凑待办行。 */
@Composable
private fun WidgetTodoRow(
    item: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.isCompleted,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = item.text,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
            color = if (item.isCompleted) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/** 星期中文标签，供小窗标题复用。 */
internal fun chineseWeekdayLabel(dayOfWeekValue: Int): String = when (dayOfWeekValue) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
