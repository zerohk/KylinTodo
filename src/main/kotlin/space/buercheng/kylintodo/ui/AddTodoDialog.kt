package space.buercheng.kylintodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import space.buercheng.kylintodo.domain.TodoItem
import java.time.LocalDate

/**
 * 待办添加弹窗。
 *
 * 对应需求 3.2「添加待办」用例：
 *  - 判断用户是否输入内容，为空则不能保存，并提示
 *  - 输入为空或纯空格时，「添加」按钮保持禁用状态
 *
 * 另外支持 Enter 直接提交（需求 4.3 要求键盘快捷键），
 * 这是桌面端比鼠标点击更常用的操作路径。
 */
@Composable
fun AddTodoDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // 弹窗打开即聚焦输入框，减少一次点击
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // 与领域模型保持同一套校验规则，避免 UI 与业务逻辑出现两套标准
    val isValid = TodoItem.createOrNull(text, date) != null

    fun submit() {
        if (!isValid) return
        onConfirm(text.trim())
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "添加待办",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                Text(
                    text = formatDateWithWeekday(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("输入待办内容") },
                    singleLine = false,
                    maxLines = 3,
                    isError = text.isNotEmpty() && !isValid,
                    supportingText = {
                        // 仅在用户输入了纯空格这类"看似有内容实则非法"时才提示，
                        // 初始空状态不提示，避免弹窗一打开就报错
                        if (text.isNotEmpty() && !isValid) {
                            Text(
                                text = "内容不能为空白",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { submit() },
                    enabled = isValid,
                ) {
                    Text("添加")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/** 以中文习惯格式化日期与星期，用于弹窗副标题。 */
internal fun formatDateWithWeekday(date: LocalDate): String {
    val weekday = when (date.dayOfWeek.value) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        else -> "日"
    }
    return "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日  星期$weekday"
}
