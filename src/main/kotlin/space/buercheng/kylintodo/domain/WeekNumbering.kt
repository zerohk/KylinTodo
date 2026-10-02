package space.buercheng.kylintodo.domain

import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * 周编号工具。
 *
 * 采用 **ISO-8601** 周编号规则：
 *  - 一周从**周一**开始，周日结束（与本项目日历网格的列对齐方式一致）
 *  - 第 1 周是包含该年**第一个周四**的那一周
 *  - 因此每年有 52 或 53 周，且年初/年末几天可能归属相邻年份的周
 *
 * 这与国内通行日历软件的"第 N 周"口径一致，也避免了自定义规则带来的歧义。
 */
object WeekNumbering {

    private val ISO = WeekFields.ISO

    /** 取某一天所属的 ISO 周号（1..53）。 */
    fun weekOfYear(date: LocalDate): Int = date.get(ISO.weekOfWeekBasedYear())

    /**
     * 取某一天所属周号对应的**年份**。
     *
     * 注意这与 [LocalDate.getYear] 不一定相同：例如 2027-01-01 属于
     * 2026 年的第 53 周（因为该周大部分落在 2026 年 12 月）。
     */
    fun weekBasedYear(date: LocalDate): Int = date.get(ISO.weekBasedYear())

    /** 某一年包含的 ISO 周数（52 或 53）。 */
    fun weeksInYear(year: Int): Int {
        // 取该年 12 月 28 日：它必定落在该年最后一 week（ISO 的固有性质）
        val dec28 = LocalDate.of(year, 12, 28)
        return weekOfYear(dec28)
    }

    /**
     * 取某一年第 [week] 周的**周一**日期。
     *
     * 用于下拉选择周后跳转。week 会被夹取到该年的合法范围。
     */
    fun mondayOfWeek(year: Int, week: Int): LocalDate {
        val maxWeek = weeksInYear(year)
        val safeWeek = week.coerceIn(1, maxWeek)
        // 以该年 1 月 4 日所在周的周一为第 1 周起点。
        // 1 月 4 日必定落在第 1 周内（ISO 的固有性质）。
        val firstWeekMonday = LocalDate.of(year, 1, 4).startOfWeekMonday()
        return firstWeekMonday.plusWeeks((safeWeek - 1).toLong())
    }

    /** 生成「YYYY 年 第 N 周」的显示文本。 */
    fun label(date: LocalDate): String =
        "${weekBasedYear(date)} 年 第 ${weekOfYear(date)} 周"

    /** 生成某年全部周的选项文本，供下拉使用。 */
    fun allWeeksOfYear(year: Int): List<Pair<Int, String>> =
        (1..weeksInYear(year)).map { w ->
            val monday = mondayOfWeek(year, w)
            val sunday = monday.plusDays(6)
            w to "第 ${w} 周  (${monday.monthValue}/${monday.dayOfMonth}" +
                " - ${sunday.monthValue}/${sunday.dayOfMonth})"
        }

    /** 当前系统区域下的每周首日是否为周一，用于诊断（正常情况下 ISO 恒为周一）。 */
    fun localeFirstDayOfWeek(): String =
        WeekFields.of(Locale.getDefault()).firstDayOfWeek.name
}
