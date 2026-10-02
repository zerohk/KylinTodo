package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * 用**接近真实的 42 格网格**验证"跨月复用后点击是否使用新日期"。
 *
 * ## 为什么需要它
 * 此前的陈旧闭包测试只用了单个可组合项（1 个格子），
 * 而真实的日历网格有 42 个同类型兄弟节点，靠位置被 Compose 复用。
 * 复用路径可能与本测试无关，因此必须用同样规模的网格来验证。
 *
 * 网格在两个"月份"之间切换，日期整体平移一个月；
 * 切换前后点击**同一个位置**，断言回调收到的是新日期。
 */
@OptIn(ExperimentalTestApi::class)
class GridReuseClickTest {

    @get:Rule
    val rule = createComposeRule()

    private class Recorder {
        var lastClicked: LocalDate? = null
        var clickCount = 0
    }

    /** 一个简化但结构相同的网格：6 行 × 7 列，共 42 格。 */
    @Composable
    private fun SimpleGrid(
        startDate: LocalDate,
        recorder: Recorder,
    ) {
        Column {
            (0 until 6).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { col ->
                        val date = startDate.plusDays((row * 7 + col).toLong())
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.Transparent)
                                .testTag("cell-$date")
                                .singleOrDoubleClick(
                                    onClick = {
                                        recorder.lastClicked = date
                                        recorder.clickCount++
                                    },
                                    onDoubleClick = {},
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "${date.monthValue}/${date.dayOfMonth}")
                        }
                    }
                }
            }
        }
    }


    /**
     * 核心断言：网格整体平移一个月（模拟翻页）后，
     * 点击**同一位置**必须报告新日期。
     */
    @Test
    fun `网格平移后点击同一位置应报告新日期`() {
        val recorder = Recorder()
        var start by mutableStateOf(LocalDate.of(2027, 2, 1))

        rule.setContent {
            MaterialTheme {
                Column {
                    Text(
                        text = "翻页",
                        modifier = Modifier
                            .testTag("page")
                            .clickableNoRipple { start = LocalDate.of(2027, 3, 1) },
                    )
                    SimpleGrid(startDate = start, recorder = recorder)
                }
            }
        }

        // 先在 2 月网格里点 2/18（第 3 行第 4 列，索引 17）
        val feb18 = LocalDate.of(2027, 2, 18)
        rule.onNodeWithTag("cell-$feb18").performClick()
        rule.waitForIdle()
        assertEquals(feb18, recorder.lastClicked, "首次点击应报告 2027-02-18")

        // 翻页：整个网格平移到 3 月
        rule.onNodeWithTag("page").performClick()
        rule.waitForIdle()

        // 点击**同一位置**（队列中同样是索引 17，现在对应 3/18）
        val mar18 = LocalDate.of(2027, 3, 18)
        recorder.lastClicked = null
        // 等待超过双击窗口，确保这一次走单击分支
        Thread.sleep(DEFAULT_DOUBLE_CLICK_TIMEOUT_MS + 150)
        rule.onNodeWithTag("cell-$mar18").performClick()
        rule.waitForIdle()

        assertEquals(
            expected = mar18,
            actual = recorder.lastClicked,
            message = "网格平移后点击同一位置应报告 2027-03-18，" +
                "实际报告 ${recorder.lastClicked} —— 存在跨月复用的陈旧闭包",
        )
    }

    /**
     * 穷举：平移后逐个点击新月份的每一格，都必须报告该格的新日期。
     */
    @Test
    fun `网格平移后逐格点击都应报告新日期`() {
        val recorder = Recorder()
        var start by mutableStateOf(LocalDate.of(2027, 2, 1))

        rule.setContent {
            MaterialTheme {
                Column {
                    Text(
                        text = "翻页",
                        modifier = Modifier
                            .testTag("page")
                            .clickableNoRipple { start = LocalDate.of(2027, 3, 1) },
                    )
                    SimpleGrid(startDate = start, recorder = recorder)
                }
            }
        }

        rule.onNodeWithTag("page").performClick()
        rule.waitForIdle()

        val problems = mutableListOf<String>()
        (0 until 42).forEach { i ->
            val date = LocalDate.of(2027, 3, 1).plusDays(i.toLong())
            recorder.lastClicked = null
            // 每次点击前等待，确保不会落入双击分支
            Thread.sleep(DEFAULT_DOUBLE_CLICK_TIMEOUT_MS + 60)
            rule.onNodeWithTag("cell-$date").performClick()
            rule.waitForIdle()
            if (recorder.lastClicked != date) {
                problems += "点击 $date 报告了 ${recorder.lastClicked}"
            }
        }
        assertEquals(
            expected = emptyList(),
            actual = problems,
            message = "发现 ${problems.size} 处陈旧日期",
        )
    }
}
