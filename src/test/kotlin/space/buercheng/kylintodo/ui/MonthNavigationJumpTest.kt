package space.buercheng.kylintodo.ui

import space.buercheng.kylintodo.domain.CalendarViewMode
import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 精确重放用户报告的操作序列：
 *
 * 「从今天（2026-10-02）通过左上角月份按钮往前走，到 2027 年 1 月，
 *   单击某一天，会跳回到 2026 年 11 月的同一天。」
 *
 * ## 重要：这里验证的是正确的方向
 * 左上角左边的箭头是**上一页**（[AppViewModel.goPrevious]）。
 * 从 2026-10 出发走到 2027-01 属于**向后**，需要按右侧箭头
 * （[AppViewModel.goNext]）。两种方向都测，避免把方向搞反。
 */
class MonthNavigationJumpTest {

    private val today = LocalDate.of(2026, 10, 2)

    private fun vm() = AppViewModel(
        repository = InMemoryTodoRepository(),
        todayProvider = { today },
    )

    @Test
    fun `向后翻3个月到2027-01后点击某天不应跳回2026-11`() {
        val vm = vm()
        assertEquals(today, vm.anchorDate)

        repeat(3) { vm.goNext() }
        assertEquals(2027, vm.anchorDate.year, "翻 3 次后应到 2027 年")
        assertEquals(1, vm.anchorDate.monthValue, "翻 3 次后应到 1 月")

        // 点击 2027-01 里的某一天
        val clicked = LocalDate.of(2027, 1, 15)
        vm.selectDate(clicked)

        assertEquals(clicked, vm.selectedDate)
        assertEquals(
            2027, vm.anchorDate.year,
            "点击后锚点年份不应变化，实际 anchor=${vm.anchorDate}",
        )
        assertEquals(
            1, vm.anchorDate.monthValue,
            "点击后锚点月份不应变化，实际 anchor=${vm.anchorDate}",
        )
        assertTrue(
            vm.page.days.any { it.date == clicked },
            "网格中应仍包含被点日期，实际范围 ${vm.page.days.first().date} ~ ${vm.page.days.last().date}",
        )
    }

    @Test
    fun `向前翻3个月到2026-07后点击某天`() {
        val vm = vm()
        repeat(3) { vm.goPrevious() }
        assertEquals(2026, vm.anchorDate.year)
        assertEquals(7, vm.anchorDate.monthValue, "翻 3 次上一页应到 7 月")

        val clicked = LocalDate.of(2026, 7, 15)
        vm.selectDate(clicked)
        assertEquals(clicked, vm.selectedDate)
        assertEquals(7, vm.anchorDate.monthValue)
    }

    /**
     * 关键场景：点击**相邻月份的溢出日期**。
     *
     * 月视图网格含上月末尾与下月开头的日期。2027-01 的网格从 2026-12-28
     * 开始，末尾到 2027-02-07。点击这些格子时锚点应当切到对应月份 ——
     * 这正是 [AppViewModel.selectDate] 里有意的行为。
     */
    @Test
    fun `点击2027-01网格里的溢出日期会切到对应月份`() {
        val vm = vm()
        repeat(3) { vm.goNext() }
        assertEquals(1, vm.anchorDate.monthValue)

        val leading = vm.page.days.first().date
        println("[TEST] 2027-01 网格首日 = $leading")
        vm.selectDate(leading)

        // 首位是 2026-12-28 -> 锚点应切到 2026-12
        assertEquals(2026, vm.anchorDate.year)
        assertEquals(12, vm.anchorDate.monthValue, "点击 12 月溢出日期后应切到 12 月")
        assertEquals(leading, vm.selectedDate)
    }

    @Test
    fun `反复翻页与点击的组合不会产生意外月份`() {
        val vm = vm()
        // 来回翻页制造各种中间状态
        repeat(3) { vm.goNext() }
        repeat(1) { vm.goPrevious() }
        repeat(2) { vm.goNext() }
        println("[TEST] 组合翻页后 anchor=${vm.anchorDate} selected=${vm.selectedDate}")

        assertEquals(2027, vm.anchorDate.year)
        assertEquals(2, vm.anchorDate.monthValue, "净 +4 个月应为 2027-02")

        // 点击当月内的一天
        val clicked = vm.page.days.first { it.inCurrentPeriod }.date
        vm.selectDate(clicked)
        assertEquals(2027, vm.anchorDate.year)
        assertEquals(2, vm.anchorDate.monthValue, "点击当月内日期不应改变锚点月份")
    }

    @Test
    fun `三种视图下翻页到2027-01再点击都不跳月`() {
        CalendarViewMode.entries.forEach { mode ->
            val vm = vm()
            vm.changeViewMode(mode)
            // 月视图翻 3 个月，周/日视图翻 3 周/天 —— 都可能落在 2027-01 附近
            repeat(3) { vm.goNext() }
            val anchorBefore = vm.anchorDate

            val clicked = vm.page.days.first { it.inCurrentPeriod }.date
            vm.selectDate(clicked)

            assertEquals(clicked, vm.selectedDate, "$mode 视图选中日应为被点日期")
            assertEquals(
                anchorBefore.year, vm.anchorDate.year,
                "$mode 视图点击后年份不应改变（anchor=${vm.anchorDate}）",
            )
        }
    }
}
