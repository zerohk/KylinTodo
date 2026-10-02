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
import kotlin.test.assertTrue

/**
 * 验证「标题 / 网格 / 锚点」三者始终自洽，并复现用户描述的
 * **"2 月之后的任一天点击都会退到 2 月的该天"**。
 *
 * ## 用户描述的现象
 * 「2 月之后的任一天，点击都会退到 2 月的该天」—— 这不是"跳一个月"，
 * 而是锚点被**钉在 2 月**：无论走到哪个月、点哪一天，都退回 2 月的那一天。
 *
 * 该行为可由一条不变式被破坏来解释：
 * 若 `goNext()` 之后锚点仍停留在上一个月（而网格已显示新月份），
 * 那么 `selectDate` 会认为"点击月份 ≠ 锚点月份"，把锚点切回旧月份。
 * 于是每个月点击都退回，且选中日的"日"号跟随点击 —— 完全吻合描述。
 *
 * 本测试把该不变式固定为断言：
 * **显示出来的月份 == 锚点所在月份 == 网格主体所在月份**。
 */
@OptIn(ExperimentalTestApi::class)
class AnchorMonthInvariantTest {

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

    private fun clickNext(n: Int) {
        repeat(n) {
            rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
            rule.waitForIdle()
        }
    }

    private fun clickCell(date: LocalDate) {
        rule.onNodeWithTag(UiTestTags.cell(date)).performClick()
        rule.waitForIdle()
    }

    /**
     * 用户描述的核心场景：翻到 2 月，再逐月往后走到每个月，
     * 每月点一天，断言**锚点月份始终等于被点日期所在月份**。
     */
    @Test
    fun `从2月起逐月往后每月点一天都不应退回2月`() {
        rule.setContent { Harness() }

        // 先到 2027-02（2 月被用户指为"钉住"的月份）
        clickNext(4)
        assertEquals(YearMonth.of(2027, 2), YearMonth.from(vm().anchorDate))

        val problems = mutableListOf<String>()

        // 从 2 月继续往后 12 个月
        (0..11).forEach { offset ->
            val month = YearMonth.of(2027, 2).plusMonths(offset.toLong())
            if (offset > 0) clickNext(1)

            val anchorMonth = YearMonth.from(vm().anchorDate)
            if (anchorMonth != month) {
                problems += "走到 $month 时锚点却停在 $anchorMonth"
            }
            // 标题必须与锚点月份一致
            val expectedTitle = "${month.year} 年 ${month.monthValue} 月"
            if (vm().pageTitle != expectedTitle) {
                problems += "走到 $month 时标题为「${vm().pageTitle}」，期望「$expectedTitle」"
            }

            // 点该月 15 日（若不存在则退到 13 日）
            val day = 15.coerceAtMost(month.lengthOfMonth())
            val target = month.atDay(day)
            clickCell(target)

            if (vm().selectedDate != target) {
                problems += "点击 $target 后选中日为 ${vm().selectedDate}"
            }
            if (YearMonth.from(vm().anchorDate) != month) {
                problems += "点击 $target 后锚点退到 ${vm().anchorDate}（应留在 $month）"
            }
        }

        assertTrue(
            problems.isEmpty(),
            "发现 ${problems.size} 处异常：\n" + problems.joinToString("\n"),
        )
    }

    /**
     * 更直接地验证用户那句话："2 月之后的任一天"。
     *
     * 翻到 2027-03 后，逐个点击 3 月的**每一天**，
     * 锚点月份必须始终是 2027-03 —— 绝不允许退到 2027-02。
     */
    @Test
    fun `翻到3月后点击3月每一天都不退到2月`() {
        rule.setContent { Harness() }
        clickNext(5) // 2026-10 -> 2027-03
        assertEquals(YearMonth.of(2027, 3), YearMonth.from(vm().anchorDate))

        val problems = mutableListOf<String>()
        (1..31).forEach { d ->
            val target = LocalDate.of(2027, 3, d)
            clickCell(target)
            val anchorMonth = YearMonth.from(vm().anchorDate)
            if (anchorMonth != YearMonth.of(2027, 3)) {
                problems += "点击 $target 后锚点=${vm().anchorDate}"
            }
            if (vm().selectedDate != target) {
                problems += "点击 $target 后选中日=${vm().selectedDate}"
            }
        }
        assertTrue(problems.isEmpty(), "发现 ${problems.size} 处异常：\n" + problems.take(10).joinToString("\n"))
    }

    /**
     * 关键不变式：显示出来的月份 == 锚点所在月份。
     *
     * 用户看到的格子里的日期必须与锚点同月（或其相邻溢出），
     * 否则 `selectDate` 的"跨月才切锚点"判断会误判。
     */
    @Test
    fun `翻页后标题与锚点始终同月`() {
        rule.setContent { Harness() }
        val problems = mutableListOf<String>()

        (0..23).forEach { i ->
            if (i > 0) {
                rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
                rule.waitForIdle()
            }
            val anchorMonth = YearMonth.from(vm().anchorDate)
            val expectedTitle = "${anchorMonth.year} 年 ${anchorMonth.monthValue} 月"
            if (vm().pageTitle != expectedTitle) {
                problems += "第 $i 次翻页：标题「${vm().pageTitle}」≠ 锚点月份 $anchorMonth"
            }
            // 标题文本必须真的渲染出来
            rule.onNodeWithText(expectedTitle).assertIsDisplayed()
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /**
     * 锚点必须落在当前网格内 —— 否则"锚点与网格不同月"，
     * 点击网格内日期就会被误判为跨月。
     */
    @Test
    fun `锚点始终落在当前网格范围内`() {
        rule.setContent { Harness() }
        val problems = mutableListOf<String>()
        (0..23).forEach { i ->
            if (i > 0) {
                rule.onNodeWithTag(UiTestTags.NEXT_BUTTON).performClick()
                rule.waitForIdle()
            }
            val days = vm().page.days.map { it.date }
            if (vm().anchorDate !in days) {
                problems += "第 $i 次翻页：锚点 ${vm().anchorDate} 不在网格 " +
                    "${days.first()}~${days.last()}"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
