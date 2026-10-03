package space.buercheng.kylintodo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoPriority

/**
 * 待办搜索弹窗。
 *
 * 支持三类过滤，三者**同时**满足才命中（"且"关系）：
 *  1. 关键词：文本包含匹配，不区分大小写
 *  2. 标签：多选，选中即"必须包含所有这些标签"
 *  3. 优先级：单选（再点一次取消）
 *
 * ## 为什么用 FilterChip 做标签/优先级
 * 它们本身就是"可选中/可取消"的语义，与过滤条件高度契合；
 * 相比下拉框，chips 让所有可选标签**一屏可见**，用户不必先点开才知道有什么。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    allTags: Set<String>,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    selectedPriority: TodoPriority?,
    onTogglePriority: (TodoPriority) -> Unit,
    results: List<TodoItem>,
    onJumpTo: (TodoItem) -> Unit,
    onToggleTodo: (TodoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(min = 420.dp, max = 520.dp)
                .fillMaxWidth(0.95f)
                .heightIn(min = 300.dp, max = 560.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "搜索待办",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // ---------------- 关键词输入 ----------------
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    placeholder = { Text("输入关键词，如「牛奶」", fontSize = 13.sp) },
                    singleLine = true,
                )

                // ---------------- 标签过滤 ----------------
                if (allTags.isNotEmpty()) {
                    Text(
                        text = "标签",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = tag in selectedTags,
                                onClick = { onToggleTag(tag) },
                                label = { Text(tag, fontSize = 12.sp) },
                            )
                        }
                    }
                }

                // ---------------- 优先级过滤 ----------------
                Text(
                    text = "优先级",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TodoPriority.entries
                        .filter { it != TodoPriority.NONE }
                        .forEach { p ->
                            FilterChip(
                                selected = p == selectedPriority,
                                onClick = { onTogglePriority(p) },
                                label = { Text(p.label, fontSize = 12.sp) },
                            )
                        }
                }

                // ---------------- 结果 ----------------
                Text(
                    text = "结果 ${results.size} 条",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
                if (results.isEmpty()) {
                    Text(
                        text = "没有匹配的待办",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(results, key = { it.id }) { item ->
                            SearchResultRow(
                                item = item,
                                onJumpTo = { onJumpTo(item) },
                                onToggle = { onToggleTodo(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 搜索结果行：完成状态 + 文本 + 日期 + 标签 + 优先级 + 跳转。 */
@Composable
private fun SearchResultRow(
    item: TodoItem,
    onJumpTo: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onJumpTo),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(
            checked = item.isCompleted,
            onCheckedChange = { onToggle() },
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = item.text,
                fontSize = 13.sp,
                color = if (item.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (item.isCompleted) {
                    androidx.compose.ui.text.style.TextDecoration.LineThrough
                } else {
                    null
                },
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(item.date.toString())
                    if (item.priority != TodoPriority.NONE) {
                        append(" · ").append(item.priority.label)
                    }
                    if (item.tags.isNotEmpty()) {
                        append(" · ").append(item.tags.joinToString(" "))
                    }
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
