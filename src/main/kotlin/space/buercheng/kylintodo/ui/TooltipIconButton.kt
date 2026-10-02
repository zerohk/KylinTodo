package space.buercheng.kylintodo.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 带悬浮提示的图标按钮。
 *
 * ## 为什么需要
 * 界面里的图标按钮只有图形，用户（尤其第一次使用）未必能看出它做什么 ——
 * 例如小窗标题栏上的星形按钮是"置顶"，齿轮是"设置"。
 * 悬浮提示是零成本的自解释手段：不占布局空间，鼠标停一下就能看到。
 *
 * ## 为什么抽成组件而不是每处写一遍
 * 项目里图标按钮有十几处，逐个包 TooltipBox 会产生大量重复的
 * `TooltipBox(positionProvider, tooltip, state)` 样板；出错时也不好统一改。
 *
 * 抽出来后还能集中处理两个细节：
 *  - `contentDescription` 与提示文字**默认共用**同一个字符串，
 *    避免一处改了另一处忘记改，导致无障碍朗读与实际提示不一致
 *  - 尺寸统一：按钮与图标大小的搭配在十几处保持一致
 *
 * ## 关于 @OptIn
 * Compose Material3 1.7 的 Tooltip API 仍标注为 `@ExperimentalMaterial3Api`。
 * 这里显式 OptIn 而不是改用 `Box + hoverable` 自己实现 ——
 * 官方的 TooltipBox 已处理好定位、显示延迟、触摸屏长按触发等细节，
 * 自行实现会引入一堆边界问题。若日后 API 变更，改动也只集中在本文件。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 是否高亮显示（如"已置顶"状态）。 */
    highlighted: Boolean = false,
    enabled: Boolean = true,
    /** 图标着色；不传则按 [highlighted] 与主题自动决定。 */
    tint: Color? = null,
    buttonSize: Dp = 28.dp,
    iconSize: Dp = 18.dp,
    /** 无障碍描述，默认与提示文字相同。 */
    contentDescription: String = tooltip,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val actualTint = tint ?: if (highlighted) scheme.primary else scheme.onSurfaceVariant

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = tooltip, fontSize = 11.sp)
            }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(buttonSize),
            enabled = enabled,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = actualTint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
