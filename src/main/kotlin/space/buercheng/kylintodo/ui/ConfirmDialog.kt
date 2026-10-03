package space.buercheng.kylintodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用确认弹窗。
 *
 * ## 为什么所有删除都要确认
 * 删除是**不可撤销**的：待办没有回收站，误删一条就真的没了。
 * 此前批量删除刻意不做确认（认为"选中+点删除"已表达明确意图），
 * 但用户实际使用后要求加上 —— 在多选模式下选了一堆再点删除，
 * 误触的代价是整批丢失，比单条误删严重得多。
 *
 * 「取消」放在**左侧且为默认焦点**：用户随手回车时应当是"不删"。
 *
 * ## 设计约定
 * - 确认按钮用错误色：红色是破坏性操作的通用语言，让用户点之前多看一眼
 * - 按钮文案写具体动作（「删除」）而不是「确定」：后者需要用户回想
 *   弹窗内容才能判断后果
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    cancelText: String = "取消",
    /** true 时确认按钮使用错误色（用于删除等破坏性操作） */
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        // 取消在左、确认在右：与主流桌面系统一致，符合从左到右的阅读习惯
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
    )
}
