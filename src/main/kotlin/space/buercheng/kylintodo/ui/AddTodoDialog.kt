package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import space.buercheng.kylintodo.domain.CalendarDay
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoPriority
import java.time.LocalDate

/**
 * 待办输入框的测试标签。
 *
 * 之所以用 testTag 而不是依赖 placeholder 文本：placeholder 在输入内容后
 * 就会从语义树上消失，靠它定位会使测试在"输入之后"的步骤全部失败。
 */
const val TODO_INPUT_TAG = "todo-input"

/** 标签输入框的测试标签。 */
const val TODO_TAG_INPUT_TAG = "todo-tag-input"

/**
 * 待办添加弹窗。
 *
 * 对应需求 3.2「添加待办」用例：
 *  - 内容为空或纯空格时不能保存，「添加」按钮保持禁用
 *  - 支持 Enter 直接提交（需求 4.3 的键盘快捷键）
 *
 * 对应需求反馈第 4 条，支持：
 *  - 重要程度 / 优先级（四级）
 *  - 自由标签（可多个）
 *
 * ## 视觉风格
 * 刻意不使用 `AlertDialog`：它套用默认的 Material 对话框样式，与本应用
 * 的「麒麟蓝 + 卡片」视觉语言不一致。这里改为 `Dialog` + 自定义 `Surface`，
 * 标题行、分区标题、圆角与配色都与主界面保持同一套设计。
 */
@Composable
fun AddTodoDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    /** 提交回调：文本、优先级、标签 */
    onConfirm: (String, TodoPriority, Set<String>) -> Unit,
    /** 所选日期的日历信息，用于在标题行复述农历 / 节气 / 节假日 */
    dayInfo: CalendarDay? = null,
) {
    var text by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TodoPriority.NONE) }
    var tags by remember { mutableStateOf(emptySet<String>()) }
    var tagDraft by remember { mutableStateOf("") }

    val textFocus = remember { FocusRequester() }
    val tagFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { textFocus.requestFocus() } }

    // 与领域模型共用同一套校验，避免 UI 与业务逻辑出现两套标准
    val isValid = TodoItem.createOrNull(text, date, priority, tags) != null

    fun addTagFromDraft() {
        if (tagDraft.isBlank()) return
        tags = TodoItem.normalizeTags(tags + tagDraft)
        tagDraft = ""
    }

    fun submit() {
        if (!isValid) return
        onConfirm(text.trim(), priority, tags)
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            // 宽度自适应，**不能写死 520dp**：
            // 桌面小窗只有 300dp 宽，写死会让弹窗超出窗口边界，把
            // 「取消 / 关闭」裁到窗口外而点不到 —— 用户表现为
            // "小窗里添加待办时没法退出，只能提交"。
            // 因此改成「最多 520dp，且不超过可用宽度的 94%」。
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.94f),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                DialogHeader(date = date, dayInfo = dayInfo)

                SectionDivider()

                // ---------------- 内容 ----------------
                SectionLabel("待办内容")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(textFocus)
                        .testTag(TODO_INPUT_TAG),
                    placeholder = { Text("要做什么？") },
                    singleLine = false,
                    maxLines = 3,
                    isError = text.isNotEmpty() && !isValid,
                    supportingText = {
                        // 仅在"看似有内容实则非法"（如纯空格）时提示，
                        // 初始空状态不提示，避免弹窗一打开就报错
                        if (text.isNotEmpty() && !isValid) {
                            Text("内容不能为空白", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            // 有未提交的标签草稿时先收进标签，再提交
                            addTagFromDraft()
                            submit()
                        },
                    ),
                )

                // ---------------- 重要程度 ----------------
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    SectionLabel("重要程度", modifier = Modifier.padding(start = 6.dp))
                }
                PrioritySelector(
                    selected = priority,
                    onSelect = { priority = it },
                )

                // ---------------- 标签 ----------------
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.LocalOffer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    SectionLabel("标签", modifier = Modifier.padding(start = 6.dp))
                    Text(
                        text = "（回车添加，最多 ${TodoItem.MAX_TAG_COUNT} 个）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                if (tags.isNotEmpty()) {
                    TagChipRow(
                        tags = tags,
                        onRemove = { tags = tags - it },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                OutlinedTextField(
                    value = tagDraft,
                    onValueChange = { tagDraft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .focusRequester(tagFocus)
                        .testTag(TODO_TAG_INPUT_TAG),
                    placeholder = { Text("如：工作、紧急", fontSize = 13.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTagFromDraft() }),
                )

                // ---------------- 操作按钮 ----------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = { submit() },
                        enabled = isValid,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("添加")
                    }
                }
            }
        }
    }
}

/**
 * 弹窗标题行：日期、星期，以及农历 / 节气 / 节假日。
 *
 * 复述日期信息是为了让用户确认待办归属哪一天，避免加错日期。
 */
@Composable
private fun DialogHeader(date: LocalDate, dayInfo: CalendarDay?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${date.monthValue} 月 ${date.dayOfMonth} 日",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = buildList {
                add(chineseWeekdayLabel(date.dayOfWeek.value))
                if (date.year != LocalDate.now().year) add("${date.year} 年")
                dayInfo?.solarTerm?.let { add(it) }
                dayInfo?.lunarFullText?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (dayInfo?.holidayName != null) add(dayInfo.holidayName)
            }.joinToString(" · ")
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 左侧一条主色竖条，作为弹窗的视觉锚点，呼应主界面选中态的配色
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    )
}

/**
 * 优先级选择器：四个圆角按钮，选中项以对应颜色填充。
 *
 * 颜色语义与日历格子上的高优先级标识保持一致（见 [priorityColor]）。
 */
@Composable
private fun PrioritySelector(
    selected: TodoPriority,
    onSelect: (TodoPriority) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TodoPriority.entries.forEach { p ->
            val isSelected = p == selected
            val color = priorityColor(p)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) color.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickableNoRipple { onSelect(p) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p != TodoPriority.NONE) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Box(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = p.label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 标签胶囊，带删除按钮。
 *
 * 用「按固定列数分组 + 两行 Row」实现换行，而不使用 `FlowRow` ——
 * FlowRow 在当前 Compose 版本仍标注为实验 API，且换行位置在中文标签下
 * 不易预期。标签数量上限为 6，固定每行 3 个已足够整齐。
 */
@Composable
private fun TagChipRow(
    tags: Set<String>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val perRow = 3
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.chunked(perRow).forEach { rowTags ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowTags.forEach { tag ->
                    TagChip(tag = tag, onRemove = { onRemove(tag) })
                }
            }
        }
    }
}

/** 单个标签胶囊。 */
@Composable
private fun TagChip(tag: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tag,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .clickableNoRipple(onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "移除标签 $tag",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

/** 优先级的语义色：无=灰、低=蓝、中=橙、高=红。 */
fun priorityColor(priority: TodoPriority): Color = when (priority) {
    TodoPriority.NONE -> Color(0xFF9AA3AF)
    TodoPriority.LOW -> Color(0xFF3B82F6)
    TodoPriority.MEDIUM -> Color(0xFFF59E0B)
    TodoPriority.HIGH -> Color(0xFFDC2626)
}

/** 以中文习惯格式化日期与星期，用于弹窗副标题与日详情。 */
internal fun formatDateWithWeekday(date: LocalDate): String =
    "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日  " +
        chineseWeekdayLabel(date.dayOfWeek.value)
