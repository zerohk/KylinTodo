package space.buercheng.kylintodo.ui

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * 「陈旧闭包」假设的**证伪**测试，兼作测试方法有效性的自查。
 *
 * ## 它推翻了一个我曾深信不疑的判断
 * 排查「2 月之后任一天点击都退回 2 月」时，我怀疑是
 * `Modifier.pointerInput(key)` 的协程持有首次组合时的旧回调 ——
 * 毕竟日历格子跨月会被 Compose 按位置复用。我据此改了实现，
 * 并写出「修复前应失败」的断言。
 *
 * **但那些断言本身是错的**：Compose 测试里连续 `performClick()` 的间隔
 * 远小于 280ms 双击阈值，第二次点击其实走的是 `onDoubleClick` 分支，
 * 于是"onClick 读到旧值"的结论完全站不住。
 *
 * 本文件用一个**故意保留旧写法**的对照组来正面回答：
 * 旧写法到底能不能读到最新值？结论是**能** —— 于是整个假设被否定，
 * 那个"修复"也一并撤销。
 *
 * ## 为什么把这段失败记录留在仓库里
 * 排查此 bug 的过程中我被自己的测试误导过三次（忘写 `remember`、
 * 双击时序、把假阳性当成证据）。凡是"修复前应当失败"的断言，
 * 只有配上会失败的对照组才可信。这个文件就是那个对照组的模板。
 */
@OptIn(ExperimentalTestApi::class)
class StaleClosureTestMetaTest {

    @get:Rule
    val rule = createComposeRule()

    private class Recorder {
        var single: String? = null
        var double: String? = null
        val latest: String? get() = single ?: double
    }

    /**
     * 对照组：**故意**沿用有缺陷的写法 —— 回调直接闭包捕获，
     * 且用 `composed`（会缓存修饰符工厂）。
     */
    private fun Modifier.legacyStaleGesture(
        onClick: () -> Unit,
        onDoubleClick: () -> Unit,
    ): Modifier = composed {
        pointerInput(Unit) {
            var lastClickAt = 0L
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    waitForUpOrCancellation() ?: continue
                    val now = System.currentTimeMillis()
                    if (lastClickAt != 0L && now - lastClickAt <= 280L) {
                        lastClickAt = 0L
                        onDoubleClick()
                    } else {
                        lastClickAt = now
                        onClick()
                    }
                }
            }
        }
    }

    /** 修复后的写法：回调经 rememberUpdatedState，协程每次点击重新读取。 */
    @Composable
    private fun Modifier.fixedGesture(
        onClick: () -> Unit,
        onDoubleClick: () -> Unit,
    ): Modifier {
        val clickState = rememberUpdatedState(onClick)
        val doubleState = rememberUpdatedState(onDoubleClick)
        return this.pointerInput(Unit) {
            var lastClickAt = 0L
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    waitForUpOrCancellation() ?: continue
                    val now = System.currentTimeMillis()
                    if (lastClickAt != 0L && now - lastClickAt <= 280L) {
                        lastClickAt = 0L
                        doubleState.value.invoke()
                    } else {
                        lastClickAt = now
                        clickState.value.invoke()
                    }
                }
            }
        }
    }

    @Composable
    private fun SwapButton(onSwap: () -> Unit) {
        Text(
            text = "换",
            modifier = Modifier.testTag("swap").clickableNoRipple(onSwap),
        )
    }

    /** 对照组宿主。 */
    @Composable
    private fun StaleHarness(recorder: Recorder) {
        var label by remember { mutableStateOf("OLD") }
        Column {
            SwapButton { label = "NEW" }
            Text(
                text = label,
                modifier = Modifier
                    .testTag("target")
                    .legacyStaleGesture(
                        onClick = { recorder.single = label },
                        onDoubleClick = { recorder.double = label },
                    ),
            )
        }
    }

    /** 修复版宿主。 */
    @Composable
    private fun FixedHarness(recorder: Recorder) {
        var label by remember { mutableStateOf("OLD") }
        Column {
            SwapButton { label = "NEW" }
            Text(
                text = label,
                modifier = Modifier
                    .testTag("target")
                    .fixedGesture(
                        onClick = { recorder.single = label },
                        onDoubleClick = { recorder.double = label },
                    ),
            )
        }
    }

    private fun run(useFixed: Boolean): Recorder {
        val recorder = Recorder()
        rule.setContent {
            MaterialTheme {
                if (useFixed) FixedHarness(recorder) else StaleHarness(recorder)
            }
        }

        rule.onNodeWithTag("target").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("swap").performClick()
        rule.waitForIdle()

        recorder.single = null
        recorder.double = null
        rule.onNodeWithTag("target").performClick()
        rule.waitForIdle()
        return recorder
    }

    /**
     * 关键结论：**旧写法同样读到最新值**。
     *
     * 这条断言表明旧实现并无陈旧闭包问题 ——
     * "越点越退回旧月份"不能由该原因解释，需另寻根因。
     */
    @Test
    fun `旧写法同样读到最新值（证伪陈旧闭包假设）`() {
        val recorder = run(useFixed = false)
        assertEquals(
            expected = "NEW",
            actual = recorder.latest,
            message = "旧写法也读到了最新值，说明 pointerInput 协程并未持有陈旧回调 —— " +
                "「陈旧闭包」假设不成立",
        )
    }

    /** 修复后的写法必须观测到新值。 */
    @Test
    fun `修复后写法应观测到新值`() {
        val recorder = run(useFixed = true)
        assertEquals(
            expected = "NEW",
            actual = recorder.latest,
            message = "修复后写法应观测到新值 NEW",
        )
    }
}
