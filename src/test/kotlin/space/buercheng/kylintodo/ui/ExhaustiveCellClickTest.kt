package space.buercheng.kylintodo.ui

import space.buercheng.kylintodo.domain.CalendarViewMode
import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 穷举式回归测试：把日历翻到 2027-01 后，**逐个**点击网格里的 42 个格子，
 * 找出哪些点击会导致锚点跳到意外月份。
 *
 * 背景：用户报告「翻到 2027 年 1 月，单击某一天会跳回 2026 年 11 月」，
 * 而此前只测了少数几个日期，未能复现。因此这里改为穷举，不遗漏任何一种格子。
 */
class ExhaustiveCellClickTest {

    private val today = LocalDate.of(2026, 10, 2)

    private fun vm() = AppViewModel(
        repository = InMemoryTodoRepository(),
        todayProvider = { today },
    )

    @Test
    fun `穷举点击2027-01网格全部42格均不产生意外跳转`() {
        val vm = vm()
        repeat(3) { vm.goNext() }
        assertEquals(YearMonth.of(2027, 1), YearMonth.from(vm.anchorDate))

        // 快照当前网格的所有日期
        val cells = vm.page.days.map { it.date }
        assertEquals(42, cells.size)

        val problems = mutableListOf<String>()

        cells.forEach { cell ->
            // 每格都在"刚翻到 2027-01"的干净状态下测试，避免相互影响
            val fresh = vm()
            repeat(3) { fresh.goNext() }
            assertEquals(YearMonth.of(2027, 1), YearMonth.from(fresh.anchorDate))

            val anchorMonthBefore = YearMonth.from(fresh.anchorDate)
            fresh.selectDate(cell)

            // 允许的锚点变化：只应切到该格子**所属的月份**（网格含相邻月溢出日期）
            val expectedAnchor = YearMonth.from(cell)
            val actualAnchor = YearMonth.from(fresh.anchorDate)

            if (actualAnchor != expectedAnchor) {
                problems += "点击 $cell：期望锚点 $expectedAnchor，实际 $actualAnchor"
            }
            if (fresh.selectedDate != cell) {
                problems += "点击 $cell：选中日期变成 ${fresh.selectedDate}"
            }
            // 网格必须仍然包含被点日期，否则用户会看到"点完就消失了"
            if (fresh.page.days.none { it.date == cell }) {
                problems += "点击 $cell：网格中已找不到该日期"
            }
            // 回归重点：不应跳到 2026-11
            if (actualAnchor == YearMonth.of(2026, 11) && expectedAnchor != YearMonth.of(2026, 11)) {
                problems += "点击 $cell：跳到了 2026-11（用户报告的 bug）"
            }
            // 也不应出现「网格与锚点不同月」的错位
            val gridStart = fresh.page.days.first().date
            val gridEnd = fresh.page.days.last().date
            if (fresh.anchorDate.isBefore(gridStart) || fresh.anchorDate.isAfter(gridEnd)) {
                problems += "点击 $cell：锚点 ${fresh.anchorDate} 不在网格 [$gridStart, $gridEnd] 内"
            }
            // 冗余但明确：锚点月份应与 page.anchor 一致
            if (YearMonth.from(fresh.page.anchor) != expectedAnchor) {
                problems += "点击 $cell：page.anchor=${fresh.page.anchor} 与期望 $expectedAnchor 不符"
            }
        }

        assertTrue(problems.isEmpty(), "发现 ${problems.size} 个异常：\n" + problems.joinToString("\n"))
    }

    /** 逐个点击 2026-12 网格，确认也没有异常。 */
    @Test
    fun `穷举点击2026-12网格全部42格`() {
        val vm = vm()
        repeat(2) { vm.goNext() }
        assertEquals(YearMonth.of(2026, 12), YearMonth.from(vm.anchorDate))

        val cells = vm.page.days.map { it.date }
        val problems = mutableListOf<String>()

        cells.forEach { cell ->
            val fresh = vm()
            repeat(2) { fresh.goNext() }
            fresh.selectDate(cell)
            val expected = YearMonth.from(cell)
            val actual = YearMonth.from(fresh.anchorDate)
            if (actual != expected) {
                problems += "点击 $cell：期望 $expected，实际 $actual"
            }
        }
        assertTrue(problems.isEmpty(), "2026-12 异常：\n" + problems.joinToString("\n"))
    }

    /**
     * 真实鼠标点击走的是 [CalendarCell] 的 onClick -> selectDate。
     * 但双击路径先触发 onClick 再触发 onDoubleClick，需确认两步之后状态仍正确。
     */
    @Test
    fun `双击路径（onClick 后再 onDoubleClick）也不产生跳转`() {
        val problems = mutableListOf<String>()

        val vm = vm()
        repeat(3) { vm.goNext() }
        val cell = vm.page.days.first { it.inCurrentPeriod }.date

        // singleOrDoubleClick 的实现：第一次点击立即调 onClick(selectDate)，
        // 第二次在超时窗口内再调 onDoubleClick(openDayInfo)
        vm.selectDate(cell)
        vm.openDayInfo(cell)

        if (YearMonth.from(vm.anchorDate) != YearMonth.from(cell)) {
            problems += "双击 $cell 后锚点=${vm.anchorDate}"
        }
        if (vm.selectedDate != cell) {
            problems += "双击 $cell 后选中=${vm.selectedDate}"
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /** 三种视图下穷举"当前周期内"的格子。 */
    @Test
    fun `三种视图下点击周期内格子均不跳月`() {
        val problems = mutableListOf<String>()
        CalendarViewMode.entries.forEach { mode ->
            val vm = vm()
            vm.changeViewMode(mode)
            repeat(3) { vm.goNext() }
            vm.page.days.filter { it.inCurrentPeriod }.forEach { day ->
                val fresh = vm()
                fresh.changeViewMode(mode)
                repeat(3) { fresh.goNext() }
                fresh.selectDate(day.date)
                if (YearMonth.from(fresh.anchorDate) != YearMonth.from(day.date)) {
                    problems += "$mode 点击 ${day.date}：锚点=${fresh.anchorDate}"
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
