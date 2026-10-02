package space.buercheng.kylintodo.ui

import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 检查"点击相邻月份溢出格子"这一**设计行为**能否解释用户看到的现象。
 *
 * ## 为什么单独写这个
 * 用户先后报告「翻到 2027-01 后点击某天跳到 2026-12 / 2026-11」、
 * 「翻到 2027-02 后点 2/18 跳到 2027-01-15」、
 * 「翻到 2027-03 后点 3/28 锚点变成 2027-02-18」。
 *
 * 月视图网格**包含相邻月份的日期**（例如 2027-01 的网格覆盖
 * 2026-12-28 ~ 2027-02-07），点击这些浅色格子时锚点会**有意**切到
 * 该格所属月份 —— 否则高亮会落在当前页之外、用户看不到反馈。
 *
 * 本测试把这三种"看起来像跳月"的情况定量列出来，用于区分
 * 「设计行为被误认为缺陷」与「真正的状态错乱」：
 *  - 设计行为：锚点切到**被点格子所属的月**，且选中日 == 被点日期
 *  - 状态错乱：锚点落到别的日期（如某个陈旧值），或选中日与点击不符
 */
class AdjacentMonthCellBehaviorTest {

    private val today = LocalDate.of(2026, 10, 2)

    private fun vm() = AppViewModel(
        repository = InMemoryTodoRepository(),
        todayProvider = { today },
    )

    /** 记录某个网格里"属于相邻月份"的格子，以及点击后锚点会变成什么。 */
    private fun overflowCells(targetMonth: YearMonth): List<Triple<LocalDate, YearMonth, LocalDate>> {
        val vm = vm()
        // 从 2026-10 翻到目标月
        val steps = java.time.temporal.ChronoUnit.MONTHS
            .between(YearMonth.of(2026, 10), targetMonth).toInt()
        repeat(steps) { vm.goNext() }
        assertEquals(targetMonth, YearMonth.from(vm.anchorDate), "应已到达 $targetMonth")

        return vm.page.days
            .filter { YearMonth.from(it.date) != targetMonth }
            .map { day ->
                val fresh = vm()
                repeat(steps) { fresh.goNext() }
                fresh.selectDate(day.date)
                Triple(day.date, targetMonth, fresh.anchorDate)
            }
    }

    @Test
    fun `2027-01网格的相邻月格子点击后锚点切到该格所属月`() {
        val overflows = overflowCells(YearMonth.of(2027, 1))
        assertTrue(overflows.isNotEmpty(), "2027-01 的网格应含相邻月份日期")

        val problems = overflows.filter { (clicked, _, anchorAfter) ->
            YearMonth.from(anchorAfter) != YearMonth.from(clicked)
        }
        assertTrue(
            problems.isEmpty(),
            "溢出格点击后锚点必须属于被点格子所在月：$problems",
        )

        // 列出实际会被切到的月份，便于与用户描述对照
        val months = overflows.map { YearMonth.from(it.first) }.distinct().sorted()
        println("[ADJ] 2027-01 网格含相邻月格子 ${overflows.size} 个，点击会切到：$months")
    }

    /**
     * 2027-02 的网格**不含**前置溢出（2027-02-01 恰为周一），
     * 但含后置溢出（3 月初）。
     */
    @Test
    fun `2027-02网格含后置溢出且行为一致`() {
        val overflows = overflowCells(YearMonth.of(2027, 2))
        val months = overflows.map { YearMonth.from(it.first) }.distinct().sorted()
        println("[ADJ] 2027-02 网格含相邻月格子 ${overflows.size} 个，点击会切到：$months")

        val problems = overflows.filter { (clicked, _, anchorAfter) ->
            YearMonth.from(anchorAfter) != YearMonth.from(clicked)
        }
        assertTrue(problems.isEmpty(), "行为应一致：$problems")
    }

    /**
     * 2027-03 的网格范围是 2027-03-01 ~ 2027-04-11。
     *
     * 关键事实：2027-03-01 **恰好是周一**，因此该月网格**没有**前置（2 月）
     * 溢出，只有后置（4 月）溢出。
     *
     * 这一点很重要 —— 用户报告"翻到 3 月后点 3/28，锚点变成 2027-02-18"，
     * 而 2027-02-18 **根本不在这个网格里**。因此那个锚点值不可能来自
     * "点到了 2 月的溢出格子"，只能来自别处（陈旧状态或渲染不同步）。
     * 把该结论固定成断言，避免以后又误判成溢出格点击。
     */
    @Test
    fun `2027-03网格不含2月格子`() {
        val vm = vm()
        val steps = java.time.temporal.ChronoUnit.MONTHS
            .between(YearMonth.of(2026, 10), YearMonth.of(2027, 3)).toInt()
        repeat(steps) { vm.goNext() }

        val days = vm.page.days.map { it.date }
        val range = "${days.first()} ~ ${days.last()}"
        val februaryCells = days.filter { YearMonth.from(it) == YearMonth.of(2027, 2) }

        assertTrue(
            februaryCells.isEmpty(),
            "2027-03-01 是周一，该月网格不应有 2 月溢出格子。" +
                "网格范围 $range，却出现 2 月格子 $februaryCells",
        )
        assertTrue(
            !days.contains(LocalDate.of(2027, 2, 18)),
            "2027-02-18 不应出现在 2027-03 的网格中（范围 $range）。" +
                "若用户看到锚点为该值，则不可能由点击本网格内的格子造成。",
        )
        // 后置溢出应存在：4 月格子（用于补齐 42 格）
        val aprilCells = days.filter { YearMonth.from(it) == YearMonth.of(2027, 4) }
        assertTrue(
            aprilCells.isNotEmpty(),
            "2027-03 的网格应含 4 月溢出格子。范围 $range",
        )
    }
}
