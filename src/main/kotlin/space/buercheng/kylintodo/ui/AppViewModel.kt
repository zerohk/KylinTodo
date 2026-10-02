package space.buercheng.kylintodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import space.buercheng.kylintodo.data.TodoExporter
import space.buercheng.kylintodo.domain.DayTodoStats
import space.buercheng.kylintodo.domain.CalendarDay
import space.buercheng.kylintodo.domain.CalendarGridBuilder
import space.buercheng.kylintodo.domain.CalendarPage
import space.buercheng.kylintodo.domain.CalendarViewMode
import space.buercheng.kylintodo.domain.DayEnrichment
import space.buercheng.kylintodo.domain.LunarJavaService
import space.buercheng.kylintodo.domain.LunarService
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoPriority
import space.buercheng.kylintodo.domain.TodoRepository
import space.buercheng.kylintodo.domain.WeekNumbering
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
    /** 初始视图模式，默认月视图（需求 F-03） */
    initialViewMode: CalendarViewMode = CalendarViewMode.MONTH,
) {

    /** 今天，用于高亮显示。 */
    val today: LocalDate get() = todayProvider()

    /** 当前视图模式，默认月视图（需求 F-03 要求主界面以月视图显示）。 */
    var viewMode: CalendarViewMode by mutableStateOf(initialViewMode)
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

    /**
     * 最近一次翻页调用的执行轨迹，供测试读取。
     *
     * 用字段而非 println：Compose 测试框架会吞掉被测组件的标准输出，
     * 日志在测试报告里看不到，断言也就无从下手。
     */
    var navigateTrace: String = ""
        private set

    /**
     * 本实例的创建序号，仅用于测试识别"读到的与界面渲染的不是同一个实例"。
     *
     * 保留它是因为这个坑值得留下痕迹：测试若忘记用 `remember` 包住
     * ViewModel，每次重组都会新建实例，断言便会看到"状态没更新"的假象，
     * 而生产代码其实完全正常。有了序号，这类问题一眼可辨。
     */
    val instanceId: Int = counter++

    /**
     * 调试用操作日志。由界面在开启调试栏时注入。
     *
     * 记录每次状态变更的**操作名与前后值**，用于定位"锚点被意料之外的
     * 路径改写"这类问题 —— 只看状态快照无法区分是哪个操作造成的。
     */
    var actionLog: ActionLog? = null

    /** 执行一次状态变更并记录到操作日志。 */
    private fun tracked(name: String, result: String = "", block: () -> Unit) {
        val log = actionLog
        if (log == null) {
            block()
            return
        }
        val a0 = anchorDate
        val s0 = selectedDate
        block()
        log.record(
            action = name,
            anchorBefore = a0,
            anchorAfter = anchorDate,
            selectedBefore = s0,
            selectedAfter = selectedDate,
            result = result,
        )
    }

    private companion object {
        /** 仅用于诊断：记录创建过多少个 AppViewModel 实例。 */
        var counter = 0
    }

    /**
     * 日期详情弹窗的目标日期；为 null 表示弹窗关闭。
     *
     * 对应需求变更：双击日历中的某一天弹出该窗口，可查看/添加该日待办。
     */
    var dayInfoDate: LocalDate? by mutableStateOf(null)
        private set

    /** 详情弹窗中当前展示那一周的日期。 */
    var dayInfoWeekDays: List<CalendarDay> by mutableStateOf(emptyList())
        private set

    /** 详情弹窗中选中日的待办。 */
    var dayInfoTodos: List<TodoItem> by mutableStateOf(emptyList())
        private set

    /** 详情弹窗中同周其余日期的待办，键为日期。 */
    var dayInfoOtherTodos: Map<LocalDate, List<TodoItem>> by mutableStateOf(emptyMap())
        private set

    /**
     * 桌面小窗是否显示。
     *
     * 对应需求第 5 条（方案 A：无边框置顶小窗）。默认关闭，由用户从主窗口开启，
     * 避免每次启动都弹出额外窗口打扰。
     */
    var widgetVisible: Boolean by mutableStateOf(false)
        private set

    /**
     * 设置桌面小窗显示状态。
     *
     * 命名为 `changeWidgetVisibility` 而非 `setWidgetVisible`：后者会与
     * [widgetVisible] 属性的私有 setter 产生 JVM 签名冲突。
     */
    fun changeWidgetVisibility(visible: Boolean) {
        widgetVisible = visible
    }

    /** 切换桌面小窗显示状态。 */
    fun toggleWidget() {
        widgetVisible = !widgetVisible
    }

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
        tracked("changeViewMode($mode)") {
            viewMode = mode
            anchorDate = selectedDate
            refresh()
        }
    }

    /** 选中某一天（点击格子）。 */
    fun selectDate(date: LocalDate) {
        tracked("selectDate($date)", result = "viewMode=$viewMode") {
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
    }

    /** 上一页：月视图减一月，周视图减一周，日视图减一天。 */
    fun goPrevious() = tracked("goPrevious") {
        navigate { when (viewMode) {
            CalendarViewMode.MONTH -> it.minusMonths(1)
            CalendarViewMode.WEEK -> it.minusWeeks(1)
            CalendarViewMode.DAY -> it.minusDays(1)
        } }
    }

    /** 下一页。 */
    fun goNext() = tracked("goNext") {
        navigate { when (viewMode) {
            CalendarViewMode.MONTH -> it.plusMonths(1)
            CalendarViewMode.WEEK -> it.plusWeeks(1)
            CalendarViewMode.DAY -> it.plusDays(1)
        } }
    }

    /**
     * 翻页。
     *
     * 翻页后选中日期一并跟随，避免出现"页面翻走了但选中日期还在上个月"
     * 导致列表区与日历区不一致。
     */
    private fun navigate(step: (LocalDate) -> LocalDate) {
        val next = step(anchorDate)
        navigateTrace = "navigate: $anchorDate -> $next"
        anchorDate = next
        navigateTrace = "$navigateTrace | 赋值后=$anchorDate"
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
    fun goToday() = tracked("goToday") {
        anchorDate = today
        selectedDate = today
        refresh()
    }

    /**
     * 当前选中日期所在的周编号显示文本，如「2026 年 第 40 周」。
     *
     * 采用 ISO-8601 规则（周一为一周之首），与日历网格的列对齐方式一致。
     */
    val currentWeekLabel: String
        get() = WeekNumbering.label(selectedDate)

    /** 当前选中日期所属周编号对应的年份。 */
    val currentWeekYear: Int
        get() = WeekNumbering.weekBasedYear(selectedDate)

    /** 当前年份的全部周选项，供下拉展示。 */
    fun weekOptionsForCurrentYear(): List<Pair<Int, String>> =
        WeekNumbering.allWeeksOfYear(currentWeekYear)

    /**
     * 跳转到指定年份的第 [week] 周。
     *
     * 跳转后锚点与选中日都落在该周的周一，保证日历页与侧栏列表一致。
     */
    fun goToWeek(year: Int, week: Int) = tracked("goToWeek($year, $week)") {
        val monday = WeekNumbering.mondayOfWeek(year, week)
        anchorDate = monday
        selectedDate = monday
        refresh()
    }

    // ---------------- 待办操作 ----------------

    /**
     * 打开添加待办弹窗。
     *
     * 会**同步把该日期设为选中日**。原因：侧栏列表只显示选中日的待办，
     * 若为"非选中日"添加待办却不切换选中，用户会以为待办没加上（界面毫无变化），
     * 反馈中"跳回原状态"的观感即由此而来。选中日跟随目标日期后，
     * 新增的待办会立刻出现在列表中。
     */
    fun openAddTodo(date: LocalDate = selectedDate) {
        if (date != selectedDate) {
            // 复用 selectDate，保证跨月时的锚点跟随逻辑一致
            selectDate(date)
        }
        addTodoTargetDate = date
    }

    /**
     * 构造**任意日期**的日历模型，供弹窗标题行复述农历 / 节气 / 节假日。
     *
     * 之所以不直接用 `selectedCalendarDay`：用户可以为"非选中日"打开添加弹窗，
     * 此时需要的是目标日期的信息，而不是当前选中日的。
     */
    fun calendarDayOf(date: LocalDate): CalendarDay {
        val info = lunarService.describe(date)
        return CalendarDay(
            date = date,
            inCurrentPeriod = true,
            lunarText = info.lunarText,
            solarTerm = info.solarTerm,
            lunarFullText = info.lunarFullText,
            dayType = info.dayType,
            holidayName = info.holidayName,
            todoCount = 0,
        )
    }

    /**
     * 调试用：把内部状态导出为一行文本，便于在真实运行时核对。
     *
     * 之所以需要它：Compose 界面无法用合成鼠标事件驱动，单元测试又只覆盖
     * 状态模型。当两者结论不一致时，需要一个能在真实进程里观察状态的出口。
     */
    fun debugState(tag: String): String =
        "[STATE] $tag | anchor=$anchorDate selected=$selectedDate mode=$viewMode | " +
            "pageRange=${page.days.firstOrNull()?.date}~${page.days.lastOrNull()?.date} | " +
            "pageAnchor=${page.anchor} | addTarget=$addTodoTargetDate"

    /** 关闭添加待办弹窗。 */
    fun dismissAddTodo() {
        addTodoTargetDate = null
    }

    // ---------------- 设置 ----------------

    /**
     * 设置弹窗是否可见。
     *
     * 放在 ViewModel 而不是界面本地状态：导出的结果提示、以及后续可能增加的
     * 导入确认等都需要与业务数据交互，集中管理更清晰。
     */
    var settingsVisible: Boolean by mutableStateOf(false)
        private set

    fun openSettings() { settingsVisible = true }

    fun dismissSettings() { settingsVisible = false }

    /**
     * 导出全部待办数据（需求反馈第 5 条）。
     *
     * 返回可直接显示给用户的文本，含导出条数与文件路径。
     * 出错时返回可读的失败原因而不抛异常 —— 导出失败不应让界面崩溃。
     */
    fun exportAllData(): String = runCatching {
        val items = repository.findAll()
        val result = TodoExporter.export(items, TodoExporter.defaultExportDir())
        TodoExporter.describe(result)
    }.getOrElse { e ->
        "导出失败：${e.message ?: e::class.simpleName}"
    }

    // ---------------- 日期详情弹窗（双击日期触发） ----------------

    /**
     * 打开日期详情弹窗。
     *
     * 对应需求变更：双击日历中的某一天 → 弹出窗口显示该日已添加的待办
     * （无则为空），并可在窗口内继续添加。
     */
    fun openDayInfo(date: LocalDate) = tracked("openDayInfo($date)") {
        selectedDate = date
        dayInfoDate = date
        // refresh() 内部会在 dayInfoDate 非空时同步刷新弹窗数据，
        // 因此这里不需要再单独调用 refreshDayInfo()。
        refresh()
    }

    /** 关闭日期详情弹窗。 */
    fun dismissDayInfo() {
        dayInfoDate = null
    }

    /**
     * 刷新详情弹窗的数据。
     *
     * 新增/勾选/删除待办后都必须调用，否则弹窗内容不会跟着变。
     */
    private fun refreshDayInfo() {
        val date = dayInfoDate ?: return
        val weekStart = date.startOfWeekMonday()
        val weekEnd = weekStart.plusDays(6)

        dayInfoWeekDays = CalendarGridBuilder.buildWeek(date).days
        dayInfoTodos = repository.findByDate(date)

        // 同周其余日期的待办，按日期分组
        dayInfoOtherTodos = repository.findByDateRange(weekStart, weekEnd)
            .filter { it.date != date }
            .groupBy { it.date }
    }

    /**
     * 新增待办。
     *
     * 复用 [TodoItem.createOrNull] 做校验并返回是否成功，
     * 保证 UI 与领域模型使用同一套规则（需求 3.2 异常流程）。
     *
     * 默认参数保证调用方（如桌面小窗、日详情弹窗）不传优先级与标签时行为不变。
     */
    fun addTodo(
        text: String,
        date: LocalDate,
        priority: TodoPriority = TodoPriority.NONE,
        tags: Collection<String> = emptyList(),
    ): Boolean {
        val item = TodoItem.createOrNull(text, date, priority, tags) ?: return false
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

        // 2. 一次性取回整页的待办统计（数量 + 最高优先级）
        val stats: Map<LocalDate, DayTodoStats> =
            if (rangeStart.isAfter(rangeEnd)) emptyMap()
            else repository.statsByDateRange(rangeStart, rangeEnd)

        // 3. 用缓存的 stats 构造格子
        val enricher: (LocalDate) -> DayEnrichment = { date ->
            val s = stats[date]
            lunarService.describe(date).copy(
                todoCount = s?.count ?: 0,
                maxPriorityLevel = s?.maxPriorityLevel ?: 0,
            )
        }

        page = when (viewMode) {
            CalendarViewMode.MONTH -> CalendarGridBuilder.buildMonth(
                month = YearMonth.from(anchorDate),
                // 传入的是**锚点日期**而不是选中日期。
                // 此前这里传 selectedDate，导致 CalendarPage.anchor 会随
                // "选中了哪一天"而变化，语义被污染 —— 页锚点应当只表示
                // 这一页代表哪个月，滚动高亮由 CalendarDay 自身表达。
                selected = anchorDate,
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

        // 5. 若日期详情弹窗处于打开状态，同步刷新其内容。
        //    放在这里而不是每个增删改方法里单独调用，避免遗漏 —— 任何
        //    数据变更都会走 refresh()，弹窗内容因此总能保持一致。
        if (dayInfoDate != null) refreshDayInfo()
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
