package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import space.buercheng.kylintodo.domain.TodoPriority
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.domain.TodoItem

/**
 * 待办列表。
 *
 * 对应需求 F-03（日节点显示当天待办）、F-04（标记完成，需有视觉反馈如删除线）、
 * F-05（删除待办）。
 */
@Composable
fun TodoList(
    todos: List<TodoItem>,
    onToggle: (TodoItem) -> Unit,
    onDelete: (TodoItem) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    /** 空列表时是否显示引导文案与添加入口 */
    showEmptyState: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (todos.isEmpty()) {
            if (showEmptyState) EmptyTodoState(onAdd = onAdd)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(todos, key = { it.id }) { item ->
                    TodoRow(
                        item = item,
                        onToggle = { onToggle(item) },
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
    }
}

/** 单条待办：优先级色条 + 复选框 + 文本（含标签）+ 删除按钮。 */
@Composable
private fun TodoRow(
    item: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 优先级色条：左侧 3dp 竖条。
        // 用颜色而非文字表达优先级 —— 列表里逐条写「高/中/低」会很吵，
        // 色条扫视时一眼可辨，也不占用横向空间。
        // 无优先级时留同宽空位，保证所有行文字左边界对齐。
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (item.priority == TodoPriority.NONE) {
                        Color.Transparent
                    } else {
                        priorityColor(item.priority)
                    }
                ),
        )
        Checkbox(
            checked = item.isCompleted,
            onCheckedChange = { onToggle() },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                // 需求 F-04 要求完成状态有视觉反馈，删除线是最直观的表达
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                color = if (item.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            // 标签：仅非空时占位，避免空行把列表撑高
            if (item.tags.isNotEmpty()) {
                TagRow(item.tags)
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除待办",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 待办行下方的标签小片。标签数量上限由领域模型保证（最多 6 个）。 */
@Composable
private fun TagRow(tags: Set<String>) {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 按固定顺序展示：Set 的迭代顺序不稳定，排序后避免每次重组位置乱跳
        tags.sorted().forEach { tag ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = tag,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 空状态：提示暂无待办并提供添加入口。 */
@Composable
private fun EmptyTodoState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = "这一天还没有待办",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAdd) {
            Text("添加一条", fontSize = 13.sp)
        }
    }
}

/**
 * 周视图下单日格子里显示的待办摘要。
 *
 * 只显示前几条，避免撑破格子高度；完整列表在选中该日后的详情区查看。
 */
@Composable
fun TodoSummary(
    todos: List<TodoItem>,
    maxLines: Int = 2,
    modifier: Modifier = Modifier,
) {
    if (todos.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        todos.take(maxLines).forEach { item ->
            Text(
                text = if (item.isCompleted) "✓ ${item.text}" else "· ${item.text}",
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (item.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
            )
        }
        if (todos.size > maxLines) {
            Text(
                text = "还有 ${todos.size - maxLines} 条",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
