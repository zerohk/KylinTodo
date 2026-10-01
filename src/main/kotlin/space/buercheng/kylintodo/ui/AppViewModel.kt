package space.buercheng.kylintodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import space.buercheng.kylintodo.domain.CalendarDay
import space.buercheng.kylintodo.domain.CalendarGridBuilder
import space.buercheng.kylintodo.domain.CalendarPage
import space.buercheng.kylintodo.domain.CalendarViewMode
import space.buercheng.kylintodo.domain.DayEnrichment
import space.buercheng.kylintodo.domain.LunarJavaService
import space.buercheng.kylintodo.domain.LunarService
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoRepository
import space.buercheng.kylintodo.domain.startOfWeekMonday
import java.time.LocalDate
import java.time.YearMonth

/**
 * 应用状态与业务编排。
 *
 * 持有当前视图模式、锚定日期、选中日期与当页网格数据，并负责在数据变化时
 * 重新计算网格（例如新增待办后刷新格子上的数量角标）。
 *
 * 使用 Compose 的 [mutableStateOf] 直接驱动重组，不引入额外状态管理框架：
 * 本应用状态规模很小，保持简单更利于在麒麟系统上排查问题。
 *
 * ## 性能约定
 * 需求 4.2 要求操作响应不超过 100ms，因此 [refresh] 内部严格遵守两点：
 *  1. 网格日期范围只计算一次，不在逐格回调里重复推导；
 *  2. 整页 42 天的待办数量用**一次**区间查询取得，而非逐格查库。
 * 农历换算需要构造对象，故整页结果只在 [refresh] 时算一遍并缓存。
 */
class AppViewModel(
    private val repository: TodoRepository,
    private val lunarService: LunarService = LunarJavaService(),
    /** 供测试注入固定日期，生产环境使用系统当前日期 */
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) {

    /** 今天，用于高亮显示。 */
    val today: LocalDate get() = todayProvider()

    /** 当前视图模式，默认月视图（需求 F-03 要求主界面以月视图显示）。 */
    var viewMode: CalendarViewMode by mutableStateOf(CalendarViewMode.MONTH)
        private set

    /** 当前锚定日期，决定显示哪一月/周/日。 */
    var anchorDate: LocalDate by mutableStateOf(today)
        private set

    /** 用户选中的日期，用于展示该日待办列表。 */
    var selectedDate: LocalDate by mutableStateOf(today)
        private set

    /** 当前页网格数据。 */
    var page: CalendarPage by mutableStateOf(emptyPage())
        private set

    /** 选中日期的待办列表。 */
    var selectedDateTodos: List<TodoItem> by mutableStateOf(emptyList())
        private set

    /** 选中日期的农历/节日信息，随 [refresh] 更新，避免每次重组重算。 */
    var selectedDayEnrichment: DayEnrichment by mutableStateOf(DayEnrichment())
        private set

    /** 添加待办弹窗的目标日期；为 null 表示弹窗关闭。 */
    var addTodoTargetDate: LocalDate? by mutableStateOf(null)
        private set

    init {
        refresh()
    }

    // ---------------- 视图切换与导航 ----------------

    /**
     * 切换日/周/月视图，并把锚点对齐到当前选中日期。
     *
     * 命名为 `changeViewMode` 而非 `setViewMode`：后者会与 [viewMode] 属性的
     * 私有 setter 产生 JVM 签名冲突。
     */
    fun changeViewMode(mode: CalendarViewMode) {
        if (viewMode == mode) return
        viewMode = mode
        anchorDate = selectedDate
        refresh()
    }

    /** 选中某一天（点击格子）。 */
    fun selectDate(date: LocalDate) {
        selectedDate = date
        // 月视图下选中相邻月份溢出的日期时，把锚点跟过去，
        // 否则高亮会落在当前页之外，用户看不到反馈。
        if (viewMode == CalendarViewMode.MONTH) {
            if (YearMonth.from(date) != YearMonth.from(anchorDate)) anchorDate = date
        } else {
            anchorDate = date
        }
        refresh()
    }

    /** 上一页：月视图减一月，周视图减一周，日视图减一天。 */
    fun goPrevious() = navigate { when (viewMode) {
        CalendarViewMode.MONTH -> it.minusMonths(1)
        CalendarViewMode.WEEK -> it.minusWeeks(1)
        CalendarViewMode.DAY -> it.minusDays(1)
    } }

    /** 下一页。 */
    fun goNext() = navigate { when (viewMode) {
        CalendarViewMode.MONTH -> it.plusMonths(1)
        CalendarViewMode.WEEK -> it.plusWeeks(1)
        CalendarViewMode.DAY -> it.plusDays(1)
    } }

    /**
     * 翻页。
     *
     * 翻页后选中日期一并跟随，避免出现"页面翻走了但选中日期还在上个月"
     * 导致列表区与日历区不一致。
     */
    private fun navigate(step: (LocalDate) -> LocalDate) {
        val next = step(anchorDate)
        anchorDate = next
        selectedDate = when (viewMode) {
            // 月视图保持"日"不变（如从 10月1日 翻到 9月1日），更符合直觉
            CalendarViewMode.MONTH -> {
                val day = selectedDate.dayOfMonth.coerceAtMost(next.lengthOfMonth())
                next.withDayOfMonth(day)
            }
            CalendarViewMode.WEEK -> selectedDate
            CalendarViewMode.DAY -> next
        }
        refresh()
    }

    /** 回到今天。 */
    fun goToday() {
        anchorDate = today
        selectedDate = today
        refresh()
    }

    // ---------------- 待办操作 ----------------

    /** 打开添加待办弹窗。 */
    fun openAddTodo(date: LocalDate = selectedDate) {
        addTodoTargetDate = date
    }

    /** 关闭添加待办弹窗。 */
    fun dismissAddTodo() {
        addTodoTargetDate = null
    }

    /**
     * 新增待办。
     *
     * 复用 [TodoItem.createOrNull] 做校验并返回是否成功，
     * 保证 UI 与领域模型使用同一套规则（需求 3.2 异常流程）。
     */
    fun addTodo(text: String, date: LocalDate): Boolean {
        val item = TodoItem.createOrNull(text, date) ?: return false
        repository.insert(item)
        refresh()
        return true
    }

    /** 切换完成状态，UI 需有视觉反馈（需求 F-04）。 */
    fun toggleCompleted(item: TodoItem) {
        repository.setCompleted(item.id, !item.isCompleted)
        refresh()
    }

    /** 删除待办（需求 F-05）。 */
    fun deleteTodo(item: TodoItem) {
        repository.delete(item.id)
        refresh()
    }

    // ---------------- 派生展示数据 ----------------

    /**
     * 选中日期对应的网格模型。
     *
     * 日视图下 `page` 只含一天，此时直接取首格；月/周视图下从当前页查找，
     * 找不到时（例如刚切换视图）回落到按需构造，保证 UI 不出现空白。
     */
    val selectedCalendarDay: CalendarDay
        get() = page.days.firstOrNull { it.date == selectedDate }
            ?: CalendarDay(
                date = selectedDate,
                inCurrentPeriod = true,
                lunarText = selectedDayEnrichment.lunarText,
                solarTerm = selectedDayEnrichment.solarTerm,
                lunarFullText = selectedDayEnrichment.lunarFullText,
                dayType = selectedDayEnrichment.dayType,
                holidayName = selectedDayEnrichment.holidayName,
                todoCount = selectedDateTodos.size,
            )

    /** 当前页标题。 */
    val pageTitle: String
        get() = when (viewMode) {
            CalendarViewMode.MONTH ->
                "${YearMonth.from(anchorDate).year} 年 ${YearMonth.from(anchorDate).monthValue} 月"

            CalendarViewMode.WEEK -> {
                val days = page.days
                if (days.isEmpty()) "" else {
                    val first = days.first().date
                    val last = days.last().date
                    "${first.year}年${first.monthValue}月${first.dayOfMonth}日 - " +
                        if (first.year == last.year) {
                            "${last.monthValue}月${last.dayOfMonth}日"
                        } else {
                            "${last.year}年${last.monthValue}月${last.dayOfMonth}日"
                        }
                }
            }

            CalendarViewMode.DAY ->
                "${anchorDate.year} 年 ${anchorDate.monthValue} 月 ${anchorDate.dayOfMonth} 日"
        }

    // ---------------- 内部 ----------------

    /**
     * 重新计算网格与选中日待办。
     *
     * 实现要点：先用纯公历算法算出本页日期范围，据此**一次性**取回待办数量，
     * 再带着这份缓存去构造格子。这样 42 个格子的构造过程中不会重复查询数据库，
     * 也不会重复推导网格边界。
     */
    private fun refresh() {
        // 1. 先确定本页日期范围（不依赖待办数据）
        val (rangeStart, rangeEnd) = computeGridRange()

        // 2. 一次性取回整页的待办数量
        val counts: Map<LocalDate, Int> =
            if (rangeStart.isAfter(rangeEnd)) emptyMap()
            else repository.countByDateRange(rangeStart, rangeEnd)

        // 3. 用缓存的 counts 构造格子
        val enricher: (LocalDate) -> DayEnrichment = { date ->
            lunarService.describe(date).copy(todoCount = counts[date] ?: 0)
        }

        page = when (viewMode) {
            CalendarViewMode.MONTH -> CalendarGridBuilder.buildMonth(
                month = YearMonth.from(anchorDate),
                selected = selectedDate,
                dayEnricher = enricher,
            )

            CalendarViewMode.WEEK -> CalendarGridBuilder.buildWeek(
                anchor = anchorDate,
                dayEnricher = enricher,
            )

            CalendarViewMode.DAY -> CalendarGridBuilder.buildDay(
                date = anchorDate,
                dayEnricher = enricher,
            )
        }

        // 4. 选中日期的待办与农历信息（同样只算一次）
        selectedDateTodos = repository.findByDate(selectedDate)
        selectedDayEnrichment = lunarService.describe(selectedDate)
            .copy(todoCount = selectedDateTodos.size)
    }

    /**
     * 计算当前视图需要覆盖的日期区间。
     *
     * 月视图必须覆盖完整 42 天（含相邻月份溢出的日期），
     * 否则相邻月份格子上的待办角标会消失。
     */
    private fun computeGridRange(): Pair<LocalDate, LocalDate> = when (viewMode) {
        CalendarViewMode.MONTH -> {
            val first = YearMonth.from(anchorDate).atDay(1)
            val start = first.startOfWeekMonday()
            start to start.plusDays((CalendarGridBuilder.MONTH_CELL_COUNT - 1).toLong())
        }

        CalendarViewMode.WEEK -> {
            val start = anchorDate.startOfWeekMonday()
            start to start.plusDays((CalendarGridBuilder.WEEK_CELL_COUNT - 1).toLong())
        }

        CalendarViewMode.DAY -> anchorDate to anchorDate
    }

    private fun emptyPage() = CalendarGridBuilder.buildDay(today)
}
