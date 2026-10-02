package space.buercheng.kylintodo.ui

import java.time.LocalDate

/**
 * 测试与生产共用的语义标记规则。
 *
 * 日历网格里同一个「日号」会出现在多个格子上（相邻月份的溢出日期），
 * 因此测试无法靠日期文本定位到唯一格子。统一用完整日期做标记，
 * 生产与测试引用同一个函数可避免两边写法漂移。
 */
object UiTestTags {

    /** 顶部工具栏的「上一页」「下一页」按钮 */
    const val PREV_BUTTON = "toolbar-prev"
    const val NEXT_BUTTON = "toolbar-next"
    const val TODAY_BUTTON = "toolbar-today"

    /** 日期格子：`calendar-cell-2027-02-18` */
    fun cell(date: LocalDate): String = "calendar-cell-$date"
}

/** 便捷函数，供 [CalendarCell] 直接使用。 */
fun cellTestTag(date: LocalDate): String = UiTestTags.cell(date)
