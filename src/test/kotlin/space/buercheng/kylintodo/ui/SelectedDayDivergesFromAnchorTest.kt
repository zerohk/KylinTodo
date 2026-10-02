package space.buercheng.kylintodo.ui

import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 复现用户截图揭示的状态：选中日停在「某月 18 日」。
 *
 * 此前所有测试的选中日都是"跟着翻页走"的每月 2 号，而真实使用中用户会
 * 先点一个中间日期（如 18 号）再继续翻页，选中日的"日"与锚点的"日"
 * 从此分离。这条路径此前未被覆盖。
 */
class SelectedDayDivergesFromAnchorTest {

    private val today = LocalDate.of(2026, 10, 2)

    private fun vm() = AppViewModel(
        repository = InMemoryTodoRepository(),
        todayProvider = { today },
    )

    @Test
    fun `选中18号后翻到2027-01再点击不应跳到2026-11或12`() {
        val vm = vm()

        // 1. 先点一个 18 号（模拟用户先选了个中间日期）
        val first18 = LocalDate.of(2026, 10, 18)
        vm.selectDate(first18)
        assertEquals(first18, vm.selectedDate)

        // 2. 向后翻 3 个月到 2027-01，选中日应跟着变成 2027-01-18
        repeat(3) { vm.goNext() }
        assertEquals(YearMonth.of(2027, 1), YearMonth.from(vm.anchorDate))
        println("[TEST] 翻页后 anchor=${vm.anchorDate} selected=${vm.selectedDate}")
        assertEquals(LocalDate.of(2027, 1, 18), vm.selectedDate)

        // 3. 单击该月的某一天
        val clicked = LocalDate.of(2027, 1, 18)
        vm.selectDate(clicked)

        assertEquals(clicked, vm.selectedDate)
        assertEquals(2027, vm.anchorDate.year, "不应跳到 2026，实际 ${vm.anchorDate}")
        assertEquals(1, vm.anchorDate.monthValue, "不应跳到 11/12 月，实际 ${vm.anchorDate}")
    }

    @Test
    fun `选中18号后向前翻也不应异常跳月`() {
        val vm = vm()
        vm.selectDate(LocalDate.of(2026, 10, 18))
        repeat(3) { vm.goPrevious() }
        assertEquals(YearMonth.of(2026, 7), YearMonth.from(vm.anchorDate))
        assertEquals(LocalDate.of(2026, 7, 18), vm.selectedDate)

        vm.selectDate(LocalDate.of(2026, 7, 18))
        assertEquals(7, vm.anchorDate.monthValue)
    }

    @Test
    fun `选择31号再翻到短月份时被夹取且不跨界`() {
        val vm = vm()
        vm.selectDate(LocalDate.of(2026, 10, 31))

        // 10-31 -> 11-30（11 月没有 31 号）
        vm.goNext()
        assertEquals(YearMonth.of(2026, 11), YearMonth.from(vm.anchorDate))
        assertEquals(LocalDate.of(2026, 11, 30), vm.selectedDate)
        println("[TEST] 10-31 翻到 11 月后 selected=${vm.selectedDate}")

        // 再翻到 12 月，应恢复为 12-30（沿用被夹取后的 30）
        vm.goNext()
        assertEquals(YearMonth.of(2026, 12), YearMonth.from(vm.anchorDate))
        assertEquals(LocalDate.of(2026, 12, 30), vm.selectedDate)
    }

    /**
     * 用户截图中出现「标题 2026 年 12 月 / 侧栏 12 月 18 日」。
     * 这个组合本身是自洽的，但需要确认：从任意出发月走到 12 月时，
     * 网格是否包含 12-18，且锚点月份与标题一致。
     */
    @Test
    fun `任意出发月走到2026-12时标题与网格与选中日自洽`() {
        val problems = mutableListOf<String>()

        // 从 2025-01 到 2027-12 每个月的 1/15/18/28/31 号出发，向后走到 2026-12
        listOf(1, 15, 18, 28, 31).forEach { dayOfMonth ->
            (0..35).forEach { startOffset ->
                val startMonth = YearMonth.of(2025, 1).plusMonths(startOffset.toLong())
                val startDay = dayOfMonth.coerceAtMost(startMonth.lengthOfMonth())
                val start = startMonth.atDay(startDay)

                val vm = AppViewModel(
                    repository = InMemoryTodoRepository(),
                    todayProvider = { start },
                )
                // 走到 2026-12
                val target = YearMonth.of(2026, 12)
                var guard = 0
                while (YearMonth.from(vm.anchorDate) != target && guard < 60) {
                    if (YearMonth.from(vm.anchorDate) < target) vm.goNext() else vm.goPrevious()
                    guard++
                }
                // 点击 12-18
                vm.selectDate(LocalDate.of(2026, 12, 18))

                val anchorMonth = YearMonth.from(vm.anchorDate)
                if (anchorMonth != target) {
                    problems += "从 $start 走到 12 月后点击 12-18：锚点变成 $anchorMonth"
                }
                if (!vm.page.days.any { it.date == LocalDate.of(2026, 12, 18) }) {
                    problems += "从 $start 出发：网格不含 12-18（范围 ${vm.page.days.first().date}~${vm.page.days.last().date}）"
                }
                if (vm.selectedDate != LocalDate.of(2026, 12, 18)) {
                    problems += "从 $start 出发：选中日变成 ${vm.selectedDate}"
                }
            }
        }
        assertTrue(problems.isEmpty(), "发现 ${problems.size} 处不自洽：\n" + problems.take(20).joinToString("\n"))
    }

    /**
     * 穷举：任意月份 + 任意日期翻页后点击，锚点必须只切到被点日期的月份。
     */
    @Test
    fun `穷举翻页后点击的锚点月份正确性`() {
        val problems = mutableListOf<String>()

        (0..23).forEach { offset ->
            val targetMonth = YearMonth.of(2026, 1).plusMonths(offset.toLong())
            val anchorDay = listOf(1, 15, 18, 28).map { it.coerceAtMost(targetMonth.lengthOfMonth()) }

            anchorDay.forEach { d ->
                val anchor = targetMonth.atDay(d)
                val vm = AppViewModel(
                    repository = InMemoryTodoRepository(),
                    todayProvider = { anchor },
                )
                // 逐格点击该月网格
                vm.page.days.map { it.date }.forEach { cell ->
                    val fresh = AppViewModel(
                        repository = InMemoryTodoRepository(),
                        todayProvider = { anchor },
                    )
                    fresh.selectDate(cell)
                    val expected = YearMonth.from(cell)
                    val actual = YearMonth.from(fresh.anchorDate)
                    if (actual != expected) {
                        problems += "$anchor 的网格点击 $cell：期望 $expected 实际 $actual"
                    }
                }
            }
        }
        assertTrue(
            problems.isEmpty(),
            "发现 ${problems.size} 处锚点月份错误：\n" + problems.take(15).joinToString("\n"),
        )
    }
}
