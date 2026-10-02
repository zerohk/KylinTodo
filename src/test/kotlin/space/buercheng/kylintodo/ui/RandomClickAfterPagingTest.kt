package space.buercheng.kylintodo.ui

import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 精确复现用户的真实操作顺序。
 *
 * 用户描述：「通过左上角月份切换到 2027 年 1 月份，然后随机单击一天，
 * 发现它会往前跳到 2026 年 12 月的同一天」。
 *
 * 此前测试的关键遗漏：翻页之后点的都是**原本就选中的那天**。
 * 真实使用中用户点的是**另一天**，此时 selectedDate 与 anchorDate
 * 的关系与翻页后的默认状态不同。
 */
class RandomClickAfterPagingTest {

    /** 模拟 2026-10-01（用户截图中的日期）。 */
    private val today = LocalDate.of(2026, 10, 1)

    private fun vm() = AppViewModel(
        repository = InMemoryTodoRepository(),
        todayProvider = { today },
    )

    @Test
    fun `翻到2027-01后单击同月内的另一天不应跳到2026-12`() {
        val vm = vm()
        repeat(3) { vm.goNext() }
        assertEquals(YearMonth.of(2027, 1), YearMonth.from(vm.anchorDate))

        // 翻页后选中日默认是 2027-01-01；这里点"另一天"（如 15 号）
        val anchorBefore = vm.anchorDate
        val clicked = LocalDate.of(2027, 1, 15)
        vm.selectDate(clicked)

        assertEquals(clicked, vm.selectedDate)
        assertEquals(
            anchorBefore, vm.anchorDate,
            "点击同月内另一天不应改变锚点，实际 ${vm.anchorDate}",
        )
        assertEquals(YearMonth.of(2027, 1), YearMonth.from(vm.anchorDate))
    }

    /**
     * 穷举：翻到 2027-01 后，点击该月网格中**每一个月内日期**（1..31），
     * 锚点都必须仍在 2027-01。
     */
    @Test
    fun `翻到2027-01后逐日点击当月每一天都不跳月`() {
        val problems = mutableListOf<String>()
        (1..31).forEach { d ->
            val vm = vm()
            repeat(3) { vm.goNext() }
            val clicked = LocalDate.of(2027, 1, d)
            vm.selectDate(clicked)
            if (YearMonth.from(vm.anchorDate) != YearMonth.of(2027, 1)) {
                problems += "点击 2027-01-$d 后锚点=${vm.anchorDate}"
            }
            if (vm.selectedDate != clicked) {
                problems += "点击 2027-01-$d 后选中=${vm.selectedDate}"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /**
     * 穷举多种"翻页次数 + 点击日期"组合，检查是否出现
     * 「月份回退一个月但日期不变」这种用户描述的现象。
     */
    @Test
    fun `穷举翻页次数与点击日期不出现月份回退`() {
        val problems = mutableListOf<String>()

        (1..14).forEach { pages ->
            (1..28).forEach { day ->
                val vm = vm()
                repeat(pages) { vm.goNext() }
                val anchorMonth = YearMonth.from(vm.anchorDate)

                // 只点击当月内的日期（不点相邻月份溢出格子）
                val inMonth = vm.page.days
                    .filter { it.inCurrentPeriod }
                    .map { it.date }
                inMonth.forEach { cell ->
                    val fresh = vm()
                    repeat(pages) { fresh.goNext() }
                    fresh.selectDate(cell)

                    val after = YearMonth.from(fresh.anchorDate)
                    if (after != anchorMonth) {
                        problems += "翻 $pages 次（锚点 $anchorMonth）后点击 $cell -> 锚点变成 $after"
                    }
                }
            }
        }
        assertTrue(
            problems.isEmpty(),
            "发现 ${problems.size} 处月份回退：\n" + problems.take(15).joinToString("\n"),
        )
    }

    /**
     * 用户明确说"往前跳到 2026 年 12 月的同一天"。
     * 这个措辞暗示**日期数字被保留**（如 1 月 15 日 -> 12 月 15 日）。
     * 唯一会保留"日"的逻辑是 [AppViewModel.goPrevious] / [goNext]（navigate）。
     * 因此检查：点击操作是否可能间接触发了 navigate。
     */
    @Test
    fun `点击日期不会保留日号地改变月份`() {
        val problems = mutableListOf<String>()

        (1..14).forEach { pages ->
            listOf(1, 15, 18, 28).forEach { day ->
                val vm = vm()
                repeat(pages) { vm.goNext() }
                val targetMonth = YearMonth.from(vm.anchorDate)
                // 目标月份可能没有该日（如 2 月没有 28 号以外的），夹取
                val safeDay = day.coerceAtMost(targetMonth.lengthOfMonth())
                val clicked = targetMonth.atDay(safeDay)

                val before = vm.selectedDate
                vm.selectDate(clicked)

                // 若选中日的"日"与点击前一致但月份变了，就是 navigate 风格的行为
                if (vm.selectedDate.dayOfMonth == before.dayOfMonth &&
                    YearMonth.from(vm.selectedDate) != YearMonth.from(before) &&
                    vm.selectedDate != clicked
                ) {
                    problems += "点击 $clicked：选中日从 $before 变成 ${vm.selectedDate}（疑似 navigate）"
                }
                if (vm.selectedDate != clicked) {
                    problems += "点击 $clicked：选中日应为 $clicked，实际 ${vm.selectedDate}"
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /**
     * 覆盖"先选一个非 1 号日期，再翻页，再点击"的完整交互链。
     */
    @Test
    fun `先选非1号再翻页再点击的完整链路`() {
        val problems = mutableListOf<String>()

        listOf(2, 15, 18, 31).forEach { firstDay ->
            (1..14).forEach { pages ->
                val vm = vm()
                val startDay = firstDay.coerceAtMost(vm.anchorDate.lengthOfMonth())
                vm.selectDate(vm.anchorDate.withDayOfMonth(startDay))
                repeat(pages) { vm.goNext() }

                val targetMonth = YearMonth.from(vm.anchorDate)
                targetMonth.atDay(1).let { first ->
                    // 点击当月 1 号
                    val fresh = AppViewModel(
                        repository = InMemoryTodoRepository(),
                        todayProvider = { today },
                    )
                    fresh.selectDate(fresh.anchorDate.withDayOfMonth(startDay))
                    repeat(pages) { fresh.goNext() }
                    val expectMonth = YearMonth.from(fresh.anchorDate)
                    fresh.selectDate(first)

                    if (YearMonth.from(fresh.anchorDate) != expectMonth) {
                        problems += "先选 $firstDay 号、翻 $pages 次后点击 $first -> 锚点=${fresh.anchorDate}"
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
