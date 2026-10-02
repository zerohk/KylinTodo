package space.buercheng.kylintodo.ui

import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 复现用户截图中的确切交互序列。
 *
 * 用户第二次反馈的状态历史为：
 * ```
 * #7 anchor=2027-03-02 selected=2027-03-28 标题=2027 年 3 月
 * #8 anchor=2027-02-18 selected=2027-03-28 标题=2027 年 2 月
 * ```
 * 即**点击 3 月 28 日后，选中日正确更新，但锚点被改成了 2 月 18 日** ——
 * 一个既不是被点日期、也不是任何翻页结果的"陈旧日期"。
 *
 * 本测试按同样的交互顺序重放，并断言锚点不得离开被点日期所在月份。
 */
class StaleAnchorRegressionTest {

    private val today = LocalDate.of(2026, 10, 2)

    private fun vm() = AppViewModel(
        repository = InMemoryTodoRepository(),
        todayProvider = { today },
    )

    /**
     * 用户的实际操作链：
     * 翻到 2 月 → 点 2/18 → 点 2/19/20/21 → 点 2/28 → 翻到 3 月 → 点 3/28
     */
    @Test
    fun `依次点多个日期再翻月后点击不应让锚点变陈旧`() {
        val vm = vm()

        // 翻到 2027-02
        repeat(4) { vm.goNext() }
        assertEquals(YearMonth.of(2027, 2), YearMonth.from(vm.anchorDate))

        // 依次点击 2 月的若干天（对应历史 #2..#6）
        listOf(18, 19, 20, 21, 28).forEach { d ->
            val target = LocalDate.of(2027, 2, d)
            vm.selectDate(target)
            assertEquals(target, vm.selectedDate, "点击 $target 后选中日")
            assertEquals(
                YearMonth.of(2027, 2), YearMonth.from(vm.anchorDate),
                "点击 $target 后锚点应仍在 2027-02，实际 ${vm.anchorDate}",
            )
        }

        // 翻到 2027-03（对应历史 #7：anchor=2027-03-02）
        vm.goNext()
        assertEquals(YearMonth.of(2027, 3), YearMonth.from(vm.anchorDate))
        assertEquals(LocalDate.of(2027, 3, 2), vm.anchorDate, "翻页应保留日号 2")

        // 点击 3/28 —— 这是出现问题的关键一步
        val clicked = LocalDate.of(2027, 3, 28)
        vm.selectDate(clicked)

        assertEquals(clicked, vm.selectedDate, "选中日应为 3 月 28 日")
        assertEquals(
            YearMonth.of(2027, 3), YearMonth.from(vm.anchorDate),
            "点击同月内日期后锚点必须留在 2027-03，实际 ${vm.anchorDate}",
        )
        // 明确排除用户看到的那个错误值
        assertTrue(
            vm.anchorDate != LocalDate.of(2027, 2, 18),
            "锚点变成了陈旧值 2027-02-18",
        )
    }

    /**
     * 穷举：在 2027-03 网格内逐格点击，锚点只允许切到该格所属月份。
     * 特别检查是否会出现"上一个被点日期所在月份的某天"作为锚点。
     */
    @Test
    fun `在3月网格内逐格点击后锚点不出现陈旧日期`() {
        val problems = mutableListOf<String>()
        val cells = vm().let { base ->
            repeat(5) { base.goNext() } // 到 2027-03
            base.page.days.map { it.date }
        }
        assertEquals(42, cells.size)

        cells.forEach { cell ->
            val fresh = vm()
            // 先制造一个"陈旧日期"：点 1 月的某天（注意不要改变起始月）
            repeat(4) { fresh.goNext() }          // 到 2027-02
            fresh.selectDate(LocalDate.of(2027, 2, 18)) // 陈旧值来源
            fresh.goNext()                        // 到 2027-03

            fresh.selectDate(cell)

            val expectedAnchorMonth = YearMonth.from(cell)
            val actualAnchorMonth = YearMonth.from(fresh.anchorDate)
            if (actualAnchorMonth != expectedAnchorMonth) {
                problems += "点击 $cell 后锚点=${fresh.anchorDate}（期望属于 $expectedAnchorMonth）"
            }
            if (fresh.selectedDate != cell) {
                problems += "点击 $cell 后选中日=${fresh.selectedDate}"
            }
        }

        assertTrue(problems.isEmpty(), "发现 ${problems.size} 处异常：\n" + problems.take(12).joinToString("\n"))
    }
}
