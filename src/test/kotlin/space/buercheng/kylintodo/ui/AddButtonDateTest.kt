package space.buercheng.kylintodo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证格子里「+」按钮的行为 —— 这是此前**从未测试过**的路径。
 *
 * ## 为什么专门测它
 * 用户报告「2 月之后的任一天，点击都会退到 2 月的该天」。
 * 逐个排除后剩下的可能路径就是格子右上角的「+」：
 * [AppViewModel.openAddTodo] 会**同步把该日期设为选中日**，
 * 而选中日跨月时会带动锚点。若这里传入了陈旧日期，
 * 就会精确产生"点任何月份都退回 2 月那一天"的现象。
 *
 * 另外「+」按钮是 `Text`/`Icon` 上的点击，与格子整体的
 * `singleOrDoubleClick` 是两条独立路径，需要分别验证。
 */
@OptIn(ExperimentalTestApi::class)
class AddButtonDateTest {

    @get:Rule
    val rule = createComposeRule()

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

    /**
     * 关键场景：在**跨月复用后**点「+」，弹窗目标日期必须是该格子的日期，
     * 而不是首次组合时那一格的日期。
     *
     * 做法：先在 2026-10 点 18 号的「+」，再翻到 2027-03 点 18 号的「+」，
     * 断言第二次的目标日期是 2027-03-18 —— 若仍是 2026-10-18，
     * 就说明「+」按钮持有了陈旧日期，这正是用户看到的现象。
     */
    @Test
    fun `跨月后点加号应使用当前格子的日期`() {
        rule.setContent { Harness() }

        // 第一次：2026-10-18 的「+」
        rule.onNodeWithTag(UiTestTags.addButton(LocalDate.of(2026, 10, 18))).performClick()
        rule.waitForIdle()
        assertEquals(
            LocalDate.of(2026, 10, 18), vm().addTodoTargetDate,
            "首次点击 2026-10-18 的加号，目标日期应为该日",
        )
        // 关掉弹窗再继续
        vm().dismissAddTodo()
        rule.waitForIdle()

        // 翻到 2027-03（同一个格子位置，日期已变）
        repeat(5) {
            rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
            rule.waitForIdle()
        }
        assertEquals(YearMonth.of(2027, 3), YearMonth.from(vm().anchorDate))

        // 第二次：2027-03-18 的「+」
        rule.onNodeWithTag(UiTestTags.addButton(LocalDate.of(2027, 3, 18))).performClick()
        rule.waitForIdle()

        assertEquals(
            expected = LocalDate.of(2027, 3, 18),
            actual = vm().addTodoTargetDate,
            message = "跨月复用后点 2027-03-18 的加号，目标日期却成了 " +
                "${vm().addTodoTargetDate} —— 「+」按钮可能持有陈旧日期",
        )
        assertEquals(
            expected = YearMonth.of(2027, 3),
            actual = YearMonth.from(vm().anchorDate),
            message = "点 3 月格子的加号后锚点不应离开 3 月，实际 ${vm().anchorDate}",
        )
    }

    /**
     * 穷举：翻到 2027-03 后，逐个点击**当月每一天**的「+」，
     * 目标日期必须等于该天，锚点必须留在 3 月。
     */
    @Test
    fun `翻到3月后逐日点加号都应指向当天`() {
        rule.setContent { Harness() }
        repeat(5) {
            rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
            rule.waitForIdle()
        }
        assertEquals(YearMonth.of(2027, 3), YearMonth.from(vm().anchorDate))

        val problems = mutableListOf<String>()
        (1..31).forEach { d ->
            val target = LocalDate.of(2027, 3, d)
            vm().dismissAddTodo()
            rule.waitForIdle()

            rule.onNodeWithTag(UiTestTags.addButton(target)).performClick()
            rule.waitForIdle()

            if (vm().addTodoTargetDate != target) {
                problems += "点 $target 的加号后目标日期=${vm().addTodoTargetDate}"
            }
            if (YearMonth.from(vm().anchorDate) != YearMonth.of(2027, 3)) {
                problems += "点 $target 的加号后锚点=${vm().anchorDate}"
            }
        }
        assertTrue(
            problems.isEmpty(),
            "发现 ${problems.size} 处异常：\n" + problems.take(12).joinToString("\n"),
        )
    }

    /**
     * 网格内所有格子的「+」都必须在语义树中可定位且可点击 ——
     * 若某个格子的加号缺失，用户点击会落到别处。
     */
    @Test
    fun `3月网格每个格子都有可点击的加号`() {
        rule.setContent { Harness() }
        repeat(5) {
            rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
            rule.waitForIdle()
        }

        val missing = mutableListOf<String>()
        vm().page.days.forEach { day ->
            val tag = UiTestTags.addButton(day.date)
            val found = runCatching {
                rule.onNodeWithTag(tag).assertExists()
                true
            }.getOrDefault(false)
            if (!found) missing += "${day.date}"
        }
        assertTrue(missing.isEmpty(), "以下格子的加号在语义树中缺失：$missing")
    }
}
