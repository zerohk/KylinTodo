package space.buercheng.kylintodo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 日历视图模式。需求：主界面可切换日 / 周 / 月。 */
enum class CalendarViewMode {
    /** 日视图：单日详情 */
    DAY,

    /** 周视图：显示最近 7 天 */
    WEEK,

    /** 月视图：显示 42 天（6 行 × 7 列） */
    MONTH,
}

/**
 * 节假日类型。
 *
 * 中国大陆的法定节假日以"调休"方式凑出连续假期，因此某一天有两种特殊状态：
 *  - [HOLIDAY]：法定放假日（休）
 *  - [WORKDAY]：调休上班日（班），通常是周末但要上班
 */
enum class DayType {
    /** 普通工作日 */
    NORMAL,

    /** 法定放假日（含周末连休） */
    HOLIDAY,

    /** 调休上班日 —— 周末但需上班 */
    WORKDAY,
}

/**
 * 日历格子里需要展示的全部信息。
 *
 * 需求约定每个日期的布局：
 *  - 左上角：阿拉伯数字日期
 *  - 右下角：农历文本或节气
 *  - 右上角：一个"+"号按钮
 */
data class CalendarDay(
    /** 该格对应的公历日期 */
    val date: LocalDate,
    /** 是否属于当前正在浏览的月份 / 周，用于淡化为相邻月份的溢出日期 */
    val inCurrentPeriod: Boolean,
    /** 公历日，显示在左上角 */
    val gregorianDay: Int = date.dayOfMonth,
    /**
     * 农历文本，显示在右下角（如"正月初一"、"廿三"）。
     * 若当天恰逢节气，则由 [solarTerm] 覆盖显示节气名。
     */
    val lunarText: String = "",
    /** 当天节气名（如"立春"、"冬至"），无节气则为 null */
    val solarTerm: String? = null,
    /** 农历日的中文写法，供详情页使用 */
    val lunarFullText: String = "",
    /** 节假日状态，决定是否显示"休"/"班"角标 */
    val dayType: DayType = DayType.NORMAL,
    /** 节日或假日名称（如"春节"、"国庆节"），用于提示 */
    val holidayName: String? = null,
    /** 该日期已存在的待办数量，用于在格子上显示计数 */
    val todoCount: Int = 0,
) {
    /** 右下角最终显示内容：节气优先于农历。 */
    val subLabel: String get() = solarTerm ?: lunarText

    /** 是否应当以"非本月"样式渲染。 */
    val isOutOfPeriod: Boolean get() = !inCurrentPeriod
}

/**
 * 一个完整的日历页。
 *
 * 三种视图统一为**一行七列**的日期网格：
 *  - 月视图：6 行 × 7 列 = 42 天
 *  - 周视图：1 行 × 7 列 = 7 天
 *  - 日视图：1 行 × 7 列 = 7 天（该日所在自然周，选中日高亮）
 */
data class CalendarPage(
    val mode: CalendarViewMode,
    /** 该页锚定的日期，用于标题与前后翻页 */
    val anchor: LocalDate,
    /** 按行优先排列的格子。月视图 42 个，周/日视图 7 个 */
    val days: List<CalendarDay>,
) {
    /**
     * 每行显示的天数。
     *
     * 恒为 7：早先这里对日视图返回 1（"日视图只显示一天"的旧设计），
     * 会导致行数被算成 42，进而使行高被压成十几 dp，格子变成细条。
     * 需求已明确三种视图都以一行七列呈现，故不再区分。
     */
    val columns: Int get() = 7

    /** 行数：月视图 6 行，周/日视图 1 行。 */
    val rows: Int get() = if (days.isEmpty()) 0 else (days.size + columns - 1) / columns
}

/**
 * 中国日历惯例：以周一为一周之首。
 *
 * 该规则被网格计算与「本页日期范围」推导共同依赖，因此抽成共享扩展，
 * 避免两处实现漂移导致待办角标或高亮落在错误的日期上。
 */
fun LocalDate.startOfWeekMonday(): LocalDate =
    minusDays(((dayOfWeek.value - DayOfWeek.MONDAY.value) + 7) % 7L)

/**
 * 纯公历的日历网格计算。
 *
 * 刻意不依赖任何农历库：农历 / 节气 / 节假日的信息由外部以 [dayEnricher]
 * 回调注入，这样网格本身可以独立测试，也便于替换农历数据源。
 */
object CalendarGridBuilder {

    /** 月视图总天数：6 行 × 7 列。需求明确要求 42 天。 */
    const val MONTH_CELL_COUNT = 42

    /** 周视图总天数：最近 7 天。 */
    const val WEEK_CELL_COUNT = 7

    /**
     * 构建月视图：固定 [MONTH_CELL_COUNT] 天，从包含 [month] 1 号的那一周的周一开始。
     *
     * 说明：中国日历习惯以周一为一周之首，因此网格从周一开始对齐，
     * 始终输出 6 行整，避免月份切换时布局高度跳动。
     */
    fun buildMonth(
        month: YearMonth,
        selected: LocalDate? = null,
        dayEnricher: (LocalDate) -> DayEnrichment = { DayEnrichment() },
    ): CalendarPage {
        val firstOfMonth = month.atDay(1)
        val gridStart = firstOfMonth.startOfWeekMonday()
        val days = (0 until MONTH_CELL_COUNT).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            buildDay(date, inCurrentPeriod = YearMonth.from(date) == month, dayEnricher)
        }
        return CalendarPage(CalendarViewMode.MONTH, selected ?: firstOfMonth, days)
    }

    /**
     * 构建周视图：显示 [anchor] 所在周的 7 天（周一至周日）。
     *
     * 与月视图的列对齐方式保持一致，因此周视图的第 1 列恒为周一。
     */
    fun buildWeek(
        anchor: LocalDate,
        dayEnricher: (LocalDate) -> DayEnrichment = { DayEnrichment() },
    ): CalendarPage {
        val weekStart = anchor.startOfWeekMonday()
        val days = (0 until WEEK_CELL_COUNT).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            buildDay(date, inCurrentPeriod = true, dayEnricher)
        }
        return CalendarPage(CalendarViewMode.WEEK, anchor, days)
    }

    /**
     * 构建日视图：仍然输出 7 天（[date] 所在自然周）。
     *
     * 之所以不是"仅一天"：需求要求日视图与周视图都以一行七列呈现，
     * 且行高与月视图保持一致。若只输出一天，单格会被拉伸填满整个高度，
     * 与其余视图的格子尺寸不一致。选中态仍由 [date] 高亮体现。
     */
    fun buildDay(
        date: LocalDate,
        dayEnricher: (LocalDate) -> DayEnrichment = { DayEnrichment() },
    ): CalendarPage {
        val weekStart = date.startOfWeekMonday()
        val days = (0 until WEEK_CELL_COUNT).map { offset ->
            val d = weekStart.plusDays(offset.toLong())
            buildDay(d, inCurrentPeriod = true, dayEnricher)
        }
        return CalendarPage(CalendarViewMode.DAY, date, days)
    }

    private fun buildDay(
        date: LocalDate,
        inCurrentPeriod: Boolean,
        dayEnricher: (LocalDate) -> DayEnrichment,
    ): CalendarDay {
        val info = dayEnricher(date)
        return CalendarDay(
            date = date,
            inCurrentPeriod = inCurrentPeriod,
            gregorianDay = date.dayOfMonth,
            lunarText = info.lunarText,
            solarTerm = info.solarTerm,
            lunarFullText = info.lunarFullText,
            dayType = info.dayType,
            holidayName = info.holidayName,
            todoCount = info.todoCount,
        )
    }
}

/**
 * 由外部数据源（农历库 / 节假日数据 / 数据库）提供的单日附加信息。
 *
 * 作为网格计算与具体实现之间的边界，便于替换数据源与单元测试。
 */
data class DayEnrichment(
    val lunarText: String = "",
    val solarTerm: String? = null,
    val lunarFullText: String = "",
    val dayType: DayType = DayType.NORMAL,
    val holidayName: String? = null,
    val todoCount: Int = 0,
)
