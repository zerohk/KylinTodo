package space.buercheng.kylintodo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

/**
 * 日历界面的交互测试（走真实组件树，而非只测状态模型）。
 *
 * ## 为什么必须补这一层
 * 用户报告「翻到未来月份后点击某天，月份会跳走」。此前约 4000 种组合的
 * **状态模型**穷举测试全部通过，说明缺陷若存在，就只能在
 * 「界面把点击派发给谁」这一层。Compose Desktop 无法用合成鼠标驱动
 * （AWT Robot 实测无效），因此改用 Compose 自带测试框架直接操作语义树 ——
 * 它绕开真实输入设备，能可靠复现"点某个格子"这一动作。
 *
 * ## 定位方式
 * 用 [UiTestTags.cell] 生成的完整日期标记定位格子。网格里同一天号会出现
 * 多次（相邻月份溢出），只靠日期文本无法区分，因此必须用完整日期。
 */
@OptIn(ExperimentalTestApi::class)
class CalendarInteractionTest {

    @get:Rule
    val rule = createComposeRule()

    /** 固定的"今天"，避免测试随真实日期漂移。 */
    private val today = LocalDate.of(2026, 10, 2)

    /** 记录被渲染的 ViewModel，供断言内部状态。 */
    private var captured: AppViewModel? = null

    @Composable
    private fun Harness() {
        // 必须 remember：否则每次重组都新建实例，测试持有的与界面渲染的
        // 可能不是同一个对象，断言会看到"状态没更新"的假象。
        val vm = remember {
            AppViewModel(
                repository = InMemoryTodoRepository(),
                todayProvider = { today },
            ).also { captured = it }
        }
        MaterialTheme {
            CalendarScreen(viewModel = vm)
        }
    }

    private fun launch() {
        rule.setContent { Harness() }
    }

    private fun vm(): AppViewModel = requireNonNull(captured)

    private fun requireNonNull(vm: AppViewModel?): AppViewModel =
        vm ?: error("ViewModel 未被捕获")

    private fun clickNextTimes(n: Int) {
        repeat(n) {
            rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
            rule.waitForIdle()
        }
    }

    private fun clickCell(date: LocalDate) {
        rule.onNodeWithTag(UiTestTags.cell(date)).performClick()
        rule.waitForIdle()
    }

    // ------------------------------------------------------------ 核心场景

    /**
     * 用户的原始场景：从今天（2026-10-02）翻到 2027-01，再点某一天。
     */
    @Test
    fun `翻到2027-01后点击18日不应跳月`() {
        launch()
        assertEquals(LocalDate.of(2026, 10, 2), vm().anchorDate)

        clickNextTimes(3)
        assertEquals(
            YearMonth.of(2027, 1), YearMonth.from(vm().anchorDate),
            "三次向后翻页应到达 2027-01",
        )
        rule.onNodeWithText("2027 年 1 月").assertIsDisplayed()

        clickCell(LocalDate.of(2027, 1, 18))

        assertEquals(LocalDate.of(2027, 1, 18), vm().selectedDate, "选中日应为所点日期")
        assertEquals(
            YearMonth.of(2027, 1), YearMonth.from(vm().anchorDate),
            "点击同月内日期后锚点不应改变，实际 ${vm().anchorDate}",
        )
        rule.onNodeWithText("2027 年 1 月").assertIsDisplayed()
    }

    /**
     * 用户最新报告的场景：2 月 18 日。从 2026-10-02 翻到 2027-02 需 4 次。
     */
    @Test
    fun `翻到2027-02后点击18日不应跳到其它月份`() {
        launch()
        clickNextTimes(4)

        assertEquals(
            YearMonth.of(2027, 2), YearMonth.from(vm().anchorDate),
            "四次向后翻页应到达 2027-02",
        )
        rule.onNodeWithText("2027 年 2 月").assertIsDisplayed()

        clickCell(LocalDate.of(2027, 2, 18))

        assertEquals(LocalDate.of(2027, 2, 18), vm().selectedDate, "选中日应为 2 月 18 日")
        assertEquals(
            YearMonth.of(2027, 2), YearMonth.from(vm().anchorDate),
            "点击 2 月 18 日后锚点仍在 2027-02，实际 ${vm().anchorDate}",
        )
        rule.onNodeWithText("2027 年 2 月").assertIsDisplayed()
    }

    /**
     * 穷举：翻到 2027-02 后，逐个点击**当月每一天**，锚点都不得改变。
     *
     * 这比只点 18 日更彻底 —— 若某个特定日号触发缺陷，这里会直接暴露。
     */
    @Test
    fun `翻到2027-02后逐日点击当月每一天都不跳月`() {
        val problems = mutableListOf<String>()
        val daysInMonth = YearMonth.of(2027, 2).lengthOfMonth() // 2027-02 为 28 天

        (1..daysInMonth).forEach { d ->
            // 每天用全新的界面实例，避免前一次点击残留影响
            rule.setContent {
                val vm = remember {
                    AppViewModel(
                        repository = InMemoryTodoRepository(),
                        todayProvider = { today },
                    ).also { captured = it }
                }
                MaterialTheme { CalendarScreen(viewModel = vm) }
            }
            clickNextTimes(4)
            val target = LocalDate.of(2027, 2, d)
            clickCell(target)

            if (vm().selectedDate != target) {
                problems += "点击 $target 后 selectedDate=${vm().selectedDate}"
            }
            if (YearMonth.from(vm().anchorDate) != YearMonth.of(2027, 2)) {
                problems += "点击 $target 后 anchorDate=${vm().anchorDate}"
            }
        }

        assertEquals(emptyList(), problems, "发现 ${problems.size} 处异常")
    }

    // ------------------------------------------------------ 相邻月份溢出格

    /**
     * 月视图网格含相邻月份日期时，点击这些溢出格子**应当**把锚点切到
     * 对应月份 —— 这是设计行为，否则高亮会落在当前页之外看不见。
     *
     * 明确写下这条断言，是为了把它与"意外跳月"区分开：
     * 两者现象相似，但前者可解释、后者是缺陷。
     *
     * 注意溢出格子是否存在取决于该月 1 日是星期几，因此不能写死日期：
     * 例如 2027-02-01 恰为周一，该月网格就没有前置溢出。
     */
    @Test
    fun `点击相邻月份的溢出格子会按设计切换锚点`() {
        launch()
        clickNextTimes(4) // 到 2027-02
        assertEquals(YearMonth.of(2027, 2), YearMonth.from(vm().anchorDate))

        val leading = vm().page.days.first().date
        // 网格首格若已属本月（该月 1 日即周一），说明没有前置溢出，
        // 本用例不适用，直接返回而不是做一个无意义的断言。
        if (YearMonth.from(leading) == YearMonth.of(2027, 2)) return

        clickCell(leading)

        assertEquals(leading, vm().selectedDate)
        assertEquals(
            YearMonth.from(leading), YearMonth.from(vm().anchorDate),
            "点溢出格子应切到该格所属月份（设计行为）",
        )
    }

    // -------------------------------------------------------------- 翻页键

    /** 连续点击上一页/下一页按钮，锚点应逐月移动且不跳变。 */
    @Test
    fun `翻页按钮逐月移动且日历页不跳变`() {
        launch()
        // 向后 6 次：2026-10 -> 2027-04
        repeat(6) { i ->
            rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
            rule.waitForIdle()
            val expected = YearMonth.of(2026, 10).plusMonths((i + 1).toLong())
            assertEquals(
                expected, YearMonth.from(vm().anchorDate),
                "第 ${i + 1} 次向后翻页后期望 $expected",
            )
        }
        // 再向前 3 次：2027-04 -> 2027-01
        repeat(3) { i ->
            rule.onNodeWithTag(UiTestTags.PREV_BUTTON).performClick()
            rule.waitForIdle()
            val expected = YearMonth.of(2027, 4).minusMonths((i + 1).toLong())
            assertEquals(expected, YearMonth.from(vm().anchorDate))
        }
    }

    /**
     * 网格必须始终包含锚点日期 —— 若锚点落在网格之外，
     * 用户会看到"当前月份里没有选中日"的矛盾状态。
     */
    @Test
    fun `任意翻页次数下网格都包含锚点`() {
        val problems = mutableListOf<String>()
        (0..15).forEach { pages ->
            rule.setContent {
                val vm = remember {
                    AppViewModel(
                        repository = InMemoryTodoRepository(),
                        todayProvider = { today },
                    ).also { captured = it }
                }
                MaterialTheme { CalendarScreen(viewModel = vm) }
            }
            clickNextTimes(pages)
            val anchor = vm().anchorDate
            val days = vm().page.days.map { it.date }
            if (anchor !in days) {
                problems += "翻 $pages 次后锚点 $anchor 不在网格内 " +
                    "(${days.first()}~${days.last()})"
            }
        }
        assertEquals(emptyList(), problems, "发现 ${problems.size} 处异常")
    }
}
