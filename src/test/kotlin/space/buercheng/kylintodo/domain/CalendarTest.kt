package space.buercheng.kylintodo.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 农历 / 节气 / 节假日行为验证。
 *
 * 这些断言复验 `docs/技术选型-农历日期库.md` 中通过 `javap` + 实际执行得出的事实，
 * 确保库升级或替换时能立刻发现行为漂移。
 */
class LunarServiceTest {

    private val service = LunarJavaService()

    @Test
    fun `春节当天农历为正月初一`() {
        val d = service.describe(LocalDate.of(2024, 2, 10))
        assertEquals("正月初一", d.lunarFullText.drop(5))
        assertTrue(d.lunarFullText.startsWith("二〇二四年"), "实际: ${d.lunarFullText}")
    }

    @Test
    fun `闰月名称不应出现重复的闰字`() {
        // 2023-03-22 是闰二月初一。库的 monthInChinese 已自带"闰"，
        // 若实现里再拼一个"闰"就会得到"闰闰二月初一"。
        val d = service.describe(LocalDate.of(2023, 3, 22))
        assertTrue(
            d.lunarFullText.contains("闰二月初一"),
            "应为闰二月初一，实际: ${d.lunarFullText}",
        )
        assertTrue(
            !d.lunarFullText.contains("闰闰"),
            "出现重复闰字: ${d.lunarFullText}",
        )
    }

    @Test
    fun `节气只在节气当天显示`() {
        assertEquals("立春", service.describe(LocalDate.of(2024, 2, 4)).solarTerm)
        assertNull(service.describe(LocalDate.of(2024, 2, 5)).solarTerm, "2月5日不是节气日")
        assertEquals("冬至", service.describe(LocalDate.of(2024, 12, 21)).solarTerm)
        assertEquals("清明", service.describe(LocalDate.of(2025, 4, 4)).solarTerm)
    }

    @Test
    fun `节假日与调休正确区分`() {
        // 2024 春节假期：2月10日放假，2月4日为调休上班
        assertEquals(DayType.HOLIDAY, service.describe(LocalDate.of(2024, 2, 10)).dayType)
        assertEquals(DayType.WORKDAY, service.describe(LocalDate.of(2024, 2, 4)).dayType)
        assertEquals(DayType.WORKDAY, service.describe(LocalDate.of(2024, 2, 18)).dayType)
        // 普通工作日
        assertEquals(DayType.NORMAL, service.describe(LocalDate.of(2024, 2, 6)).dayType)
    }

    @Test
    fun `超出数据范围的年份安全降级为普通日`() {
        // 节假日数据止于 2026-10-10，2027 年起无数据，
        // 此时应返回 NORMAL 而不是抛异常。
        val future = service.describe(LocalDate.of(2027, 10, 1))
        assertEquals(DayType.NORMAL, future.dayType)
        assertNotNull(future.lunarFullText, "农历换算仍应可用")
        assertTrue(future.lunarFullText.isNotBlank())
    }

    @Test
    fun `初一显示月名而非初一`() {
        // 2024-02-10 是正月初一，格子右下角应显示"正月"
        assertEquals("正月", service.describe(LocalDate.of(2024, 2, 10)).lunarText)
        // 非初一日显示日名
        assertEquals("初二", service.describe(LocalDate.of(2024, 2, 11)).lunarText)
    }
}

/**
 * 公历网格计算验证。
 *
 * 重点覆盖需求里最容易算错的两处：月视图固定 42 天、周视图 7 天且以周一起始。
 */
class CalendarGridBuilderTest {

    @Test
    fun `月视图固定输出42天即6行7列`() {
        // 遍历多个年份的所有月份，确保任何月份都稳定输出 42 格
        for (year in intArrayOf(2024, 2025, 2026)) {
            for (month in 1..12) {
                val page = CalendarGridBuilder.buildMonth(YearMonth.of(year, month))
                assertEquals(
                    CalendarGridBuilder.MONTH_CELL_COUNT, page.days.size,
                    "$year-$month 应输出 42 天",
                )
                assertEquals(6, page.rows, "$year-$month 应为 6 行")
                assertEquals(7, page.columns)
            }
        }
    }

    @Test
    fun `月视图从周一开始且包含当月全部日期`() {
        val page = CalendarGridBuilder.buildMonth(YearMonth.of(2026, 10))
        assertTrue(page.days.first().date.dayOfWeek.value == 1, "首格应为周一")
        assertTrue(page.days.last().date.dayOfWeek.value == 7, "末格应为周日")

        // 当月 1 号到月末必须全部出现在网格里
        val monthDates = page.days.filter { it.inCurrentPeriod }.map { it.date.dayOfMonth }
        assertEquals((1..31).toList(), monthDates, "10月应有 1..31 且全部在当月区间内")
    }

    @Test
    fun `月视图标记相邻月份的溢出日期`() {
        // 2026-10-01 是周四，因此网格起始是 2026-09-28
        val page = CalendarGridBuilder.buildMonth(YearMonth.of(2026, 10))
        assertEquals(LocalDate.of(2026, 9, 28), page.days.first().date)
        assertTrue(page.days.first().isOutOfPeriod, "9月28日应标记为非本月")
        assertTrue(!page.days[3].isOutOfPeriod, "10月1日应属于本月")
    }

    @Test
    fun `周视图输出7天且以周一起始`() {
        val page = CalendarGridBuilder.buildWeek(LocalDate.of(2026, 10, 1)) // 周四
        assertEquals(7, page.days.size)
        assertEquals(LocalDate.of(2026, 9, 28), page.days.first().date) // 周一
        assertEquals(LocalDate.of(2026, 10, 4), page.days.last().date)   // 周日
        assertTrue(page.days.all { !it.isOutOfPeriod }, "周视图内所有日期均属本周")
    }

    @Test
    fun `周视图在周日锚点时不应跳到下一周`() {
        // 2026-10-04 是周日，应属于 9/28 起的那一周
        val page = CalendarGridBuilder.buildWeek(LocalDate.of(2026, 10, 4))
        assertEquals(LocalDate.of(2026, 9, 28), page.days.first().date)
    }

    @Test
    fun `日视图仅输出一天`() {
        val page = CalendarGridBuilder.buildDay(LocalDate.of(2026, 10, 1))
        assertEquals(1, page.days.size)
        assertEquals(LocalDate.of(2026, 10, 1), page.days.first().date)
    }

    @Test
    fun `二月与闰年边界`() {
        // 2024 是闰年，2 月有 29 天
        val feb2024 = CalendarGridBuilder.buildMonth(YearMonth.of(2024, 2))
        assertEquals(42, feb2024.days.size)
        assertTrue(feb2024.days.any { it.date == LocalDate.of(2024, 2, 29) }, "应包含 2月29日")

        // 2025 非闰年，不应出现 2月29日。
        // 注意不能写 LocalDate.of(2025, 2, 29) —— 那会直接抛 DateTimeException，
        // 必须按 月/日 匹配。
        val feb2025 = CalendarGridBuilder.buildMonth(YearMonth.of(2025, 2))
        assertEquals(42, feb2025.days.size)
        assertTrue(
            feb2025.days.none { it.date.monthValue == 2 && it.date.dayOfMonth == 29 },
            "2025 年不应出现 2月29日",
        )
    }

    @Test
    fun `网格计算可注入外部农历信息`() {
        val page = CalendarGridBuilder.buildMonth(YearMonth.of(2026, 10)) { date ->
            DayEnrichment(lunarText = "L${date.dayOfMonth}", dayType = DayType.HOLIDAY)
        }
        val first = page.days.first()
        assertEquals("L${first.date.dayOfMonth}", first.lunarText)
        assertEquals(DayType.HOLIDAY, first.dayType)
        // 节气优先于农历作为副标签
        assertEquals("L${first.date.dayOfMonth}", first.subLabel)
    }
}

/**
 * 待办模型的输入校验。
 *
 * 对应需求 3.2 异常流程：内容为空或纯空格时不允许保存。
 */
class TodoItemTest {

    private val date = LocalDate.of(2026, 10, 1)

    @Test
    fun `纯空格或空内容不允许创建`() {
        assertNull(TodoItem.createOrNull("", date))
        assertNull(TodoItem.createOrNull("   ", date))
        assertNull(TodoItem.createOrNull("\t\n ", date))
    }

    @Test
    fun `正常内容会去除首尾空白`() {
        val item = TodoItem.createOrNull("  写需求文档  ", date)
        assertNotNull(item)
        assertEquals("写需求文档", item.text)
        assertEquals(date, item.date)
        assertTrue(!item.isCompleted, "新建待办默认未完成")
    }

    @Test
    fun `新建待办拥有唯一ID`() {
        val a = TodoItem.createOrNull("a", date)!!
        val b = TodoItem.createOrNull("b", date)!!
        assertTrue(a.id != b.id)
    }
}
