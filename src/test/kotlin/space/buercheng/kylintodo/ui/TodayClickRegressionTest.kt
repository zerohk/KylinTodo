package space.buercheng.kylintodo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

/**
 * 用户报告：「点击**今天**（10 月 2 日）也会跳到 2 月」。
 *
 * ## 为什么这条最关键
 * 点击今天走的是 [AppViewModel.selectDate]（点格子）或 [AppViewModel.goToday]
 * （点「今天」按钮）。前者最多把锚点设成"今天所在月"，后者直接设为今天 ——
 * **两者都不可能产生 2 月**。因此该现象一旦成立，就说明锚点正被某条
 * 尚未发现的路径改写。
 *
 * 本测试把这两条路径在**任意前置月份**下逐一遍历并钉死：
 * 无论用户此前翻到哪个月，点今天后锚点与选中日都必须回到今天所在月。
 */
@OptIn(ExperimentalTestApi::class)
class TodayClickRegressionTest {

    @get:Rule
    val rule = createComposeRule()

    /** 固定"今天"，避免随真实日期漂移。 */
    private val today = LocalDate.of(2026, 10, 2)
    private var captured: AppViewModel? = null

    @Composable
    private fun Harness() {
        val vm = remember {
            AppViewModel(
                repository = InMemoryTodoRepository(),
                todayProvider = { today },
            ).also { captured = it }
        }
        MaterialTheme { CalendarScreen(viewModel = vm, appName = "大智日历") }
    }

    private fun vm() = captured ?: error("ViewModel 未被捕获")

    private fun clickNext(n: Int) {
        repeat(n) {
            rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
            rule.waitForIdle()
        }
    }

    /**
     * 用户报告的直接复现：先翻到 2 月，再翻回今天所在月，点今天。
     *
     * 若锚点被钉在 2 月，这里的断言会立即失败。
     */
    @Test
    fun `翻到2月后再点今天应回到10月而不是留在2月`() {
        rule.setContent { Harness() }

        // 翻到 2027-02（用户指认被"钉住"的月份）
        clickNext(4)
        assertEquals(YearMonth.of(2027, 2), YearMonth.from(vm().anchorDate))

        // 点今天的格子（需要先回到 10 月才能看到该格子）
        vm().goToday()
        rule.waitForIdle()
        assertEquals(
            expected = today,
            actual = vm().anchorDate,
            message = "点今天后锚点应为 $today，实际 ${vm().anchorDate}",
        )

        // 再点击今天的格子
        rule.onNodeWithTag(UiTestTags.cell(today)).performClick()
        rule.waitForIdle()
        assertEquals(today, vm().selectedDate, "点击今天后选中日应为今天")
        assertEquals(
            expected = YearMonth.of(2026, 10),
            actual = YearMonth.from(vm().anchorDate),
            message = "点击今天后锚点必须落在 10 月，实际 ${vm().anchorDate}",
        )
    }

    /**
     * 穷举：从任意月份出发，点今天的格子，锚点都必须回到今天所在月。
     *
     * 「今天」的格子只在今天所在月的网格里，因此每次都要先用
     * goToday 让网格覆盖今天，再点它 —— 这正是用户的真实操作。
     */
    @Test
    fun `从任意月份出发点今天都应回到10月`() {
        rule.setContent { Harness() }
        val problems = mutableListOf<String>()

        (0..14).forEach { pages ->
            // 每次回到起点再往后翻，覆盖不同的前置月份
            vm().goToday()
            rule.waitForIdle()
            clickNext(pages)

            val before = vm().anchorDate

            // 回到今天所在月，然后点击今天的格子
            vm().goToday()
            rule.waitForIdle()
            rule.onNodeWithTag(UiTestTags.cell(today)).performClick()
            rule.waitForIdle()

            if (vm().selectedDate != today) {
                problems += "从 $before 出发：点今天后选中日=${vm().selectedDate}"
            }
            if (YearMonth.from(vm().anchorDate) != YearMonth.of(2026, 10)) {
                problems += "从 $before 出发：点今天后锚点=${vm().anchorDate}"
            }
        }
        assertEquals(
            expected = emptyList(),
            actual = problems,
            message = "发现 ${problems.size} 处异常",
        )
    }

    /** 「今天」按钮本身：从任意月份点击都必须回到今天。 */
    @Test
    fun `从任意月份点今天按钮都应回到今天`() {
        rule.setContent { Harness() }
        val problems = mutableListOf<String>()

        (0..14).forEach { pages ->
            clickNext(1) // 逐月推进
            val before = vm().anchorDate
            rule.onNodeWithTag(UiTestTags.TODAY_BUTTON).performClick()
            rule.waitForIdle()

            if (vm().anchorDate != today) {
                problems += "从 $before 点今天按钮后锚点=${vm().anchorDate}"
            }
            if (vm().selectedDate != today) {
                problems += "从 $before 点今天按钮后选中日=${vm().selectedDate}"
            }
            if (YearMonth.from(vm().anchorDate) != YearMonth.of(2026, 10)) {
                problems += "从 $before 点今天按钮后锚点月份=${vm().anchorDate}"
            }
        }
        assertEquals(emptyList(), problems, "发现 ${problems.size} 处异常")
    }
}
