package space.buercheng.kylintodo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ISO-8601 周编号验证。
 *
 * 周编号最容易出错的地方是**跨年边界**：年初年末几天可能归属相邻年份的周，
 * 且每年可能有 53 周。这里重点覆盖这些边界。
 */
class WeekNumberingTest {

    @Test
    fun `已知日期的ISO周号`() {
        // 与 ISO-8601 及主流日历软件一致的参考值
        assertEquals(1, WeekNumbering.weekOfYear(LocalDate.of(2026, 1, 1)))
        assertEquals(40, WeekNumbering.weekOfYear(LocalDate.of(2026, 10, 1)))
        assertEquals(41, WeekNumbering.weekOfYear(LocalDate.of(2026, 10, 8)))
        assertEquals(53, WeekNumbering.weekOfYear(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `同一周内所有日期的周号相同`() {
        // 2026-10-01 是周四，其所在周为周一 2026-09-28 ~ 周日 2026-10-04
        val monday = LocalDate.of(2026, 9, 28)
        val week = WeekNumbering.weekOfYear(monday)
        (0..6).forEach { offset ->
            val d = monday.plusDays(offset.toLong())
            assertEquals(
                week, WeekNumbering.weekOfYear(d),
                "$d 应与周一 $monday 同周",
            )
        }
    }

    @Test
    fun `跨年边界时周号所属年份可能与日历年不同`() {
        // 2027-01-01 是周五，该周大部分在 2026 年 12 月，故属于 2026 年第 53 周
        val newYear = LocalDate.of(2027, 1, 1)
        assertEquals(2026, WeekNumbering.weekBasedYear(newYear))
        assertEquals(53, WeekNumbering.weekOfYear(newYear))
        assertEquals(
            2027, newYear.year,
            "日历年仍是 2027，说明两者确实可以不同",
        )
    }

    @Test
    fun `周号年份与日历年一致是常见情况`() {
        val d = LocalDate.of(2026, 10, 1)
        assertEquals(2026, WeekNumbering.weekBasedYear(d))
        assertEquals(d.year, WeekNumbering.weekBasedYear(d))
    }

    @Test
    fun `每年周数为52或53且取值合理`() {
        (2020..2032).forEach { y ->
            val n = WeekNumbering.weeksInYear(y)
            assertTrue(n == 52 || n == 53, "$y 年的周数应为 52 或 53，实际 $n")
        }
        // 已知有 53 周的年份
        assertEquals(53, WeekNumbering.weeksInYear(2026))
        assertEquals(52, WeekNumbering.weeksInYear(2025))
    }

    @Test
    fun `周一起点计算正确且为周一`() {
        (2020..2030).forEach { y ->
            val maxWeek = WeekNumbering.weeksInYear(y)
            listOf(1, 2, maxWeek / 2, maxWeek).forEach { w ->
                val monday = WeekNumbering.mondayOfWeek(y, w)
                assertEquals(
                    DayOfWeek.MONDAY, monday.dayOfWeek,
                    "$y 年第 $w 周的起点应为周一，实际 $monday",
                )
                assertEquals(
                    w, WeekNumbering.weekOfYear(monday),
                    "$y 年第 $w 周的周一反查周号应回到 $w",
                )
            }
        }
    }

    @Test
    fun `weekOfYear 与 mondayOfWeek 互为逆运算`() {
        // 遍历若干年的所有周，验证双向一致
        (2024..2028).forEach { y ->
            (1..WeekNumbering.weeksInYear(y)).forEach { w ->
                val monday = WeekNumbering.mondayOfWeek(y, w)
                assertEquals(w, WeekNumbering.weekOfYear(monday), "$y 年第 $w 周往返不一致")
                assertEquals(y, WeekNumbering.weekBasedYear(monday), "$y 年第 $w 周年份往返不一致")
                // 该周内每一天都应回到同一周号
                (0..6).forEach { off ->
                    assertEquals(
                        w, WeekNumbering.weekOfYear(monday.plusDays(off.toLong())),
                        "$y 年第 $w 周的第 $off 天周号不一致",
                    )
                }
            }
        }
    }

    @Test
    fun `越界的周号会被夹取到合法范围`() {
        val maxWeek = WeekNumbering.weeksInYear(2026)
        assertEquals(1, WeekNumbering.weekOfYear(WeekNumbering.mondayOfWeek(2026, 0)))
        assertEquals(1, WeekNumbering.weekOfYear(WeekNumbering.mondayOfWeek(2026, -5)))
        assertEquals(
            maxWeek,
            WeekNumbering.weekOfYear(WeekNumbering.mondayOfWeek(2026, 999)),
        )
    }

    @Test
    fun `显示文本格式符合预期`() {
        assertEquals("2026 年 第 40 周", WeekNumbering.label(LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun `下拉选项覆盖全年且带日期区间`() {
        val options = WeekNumbering.allWeeksOfYear(2026)
        assertEquals(WeekNumbering.weeksInYear(2026), options.size)
        assertEquals(1, options.first().first)
        // 第一项应包含日期区间，便于用户判断选了哪一周
        assertTrue(options.first().second.contains(" - "), "选项应带日期区间")
        // 选项中的周号应连续且无重复
        assertEquals((1..options.size).toList(), options.map { it.first })
    }

    @Test
    fun `网格起点与周编号起点一致`() {
        // 周编号以周一为起点，日历网格也必须如此，否则"第 N 周"会与列错位
        val d = LocalDate.of(2026, 10, 1)
        assertEquals(d.startOfWeekMonday(), LocalDate.of(2026, 9, 28))
    }
}
