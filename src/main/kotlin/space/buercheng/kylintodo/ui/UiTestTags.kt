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

    /** 界面左上角的应用名称标题 */
    const val APP_TITLE = "app-title"

    /** 设置弹窗里的应用名称输入框 */
    const val SETTINGS_APP_NAME = "settings-app-name"

    /** 日期格子：`calendar-cell-2027-02-18` */
    fun cell(date: LocalDate): String = "calendar-cell-$date"

    /**
     * 格子右上角的「+」按钮：`calendar-add-2027-02-18`。
     *
     * 单独标记的原因：它与格子整体的点击是**两条独立路径** ——
     * 格子走 `singleOrDoubleClick` 选中该日，而「+」走 `openAddTodo`
     * 并会同步切换选中日。排查"点某天后月份回退"时必须能分别驱动它们。
     */
    fun addButton(date: LocalDate): String = "calendar-add-$date"
}

/** 便捷函数，供 [CalendarCell] 直接使用。 */
fun cellTestTag(date: LocalDate): String = UiTestTags.cell(date)
