package space.buercheng.kylintodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import space.buercheng.kylintodo.data.HolidayTransfer
import space.buercheng.kylintodo.domain.HolidayTable
import space.buercheng.kylintodo.domain.OverlayLunarService
import space.buercheng.kylintodo.data.DataExporter
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
    /**
     * 农历 / 节气 / 节假日数据源。
     *
     * 声明为 `var` 而非 `val`：用户导入节假日数据后需要用
     * [OverlayLunarService] 重建一个"内置 + 导入"的叠加层，
     * 使导入的「休 / 班」立即生效（见 [rebuildHolidayOverlay]）。
     */
    private var lunarService: LunarService = LunarJavaService(),
    /** 供测试注入固定日期，生产环境使用系统当前日期 */
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    /** 初始视图模式，默认月视图（需求 F-03） */
    initialViewMode: CalendarViewMode = CalendarViewMode.MONTH,
    /**
     * 待办数据导出能力（需求 1：导出路径可选）。
     *
     * 为 null 时导出入口返回明确提示而不是崩溃 ——
     * 这样单元测试无需提供文件对话框即可构造 ViewModel。
     */
    private val dataExporter: DataExporter? = null,
    /**
     * 节假日数据的导入/模板导出能力。
     *
     * 抽成接口的原因与导出待办相同：桌面用 AWT 文件对话框选文件，
     * Android 需走 SAF；而**解析**逻辑（[HolidayImporter]）两端共用。
     */
    private val holidayTransfer: HolidayTransfer? = null,
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
        // 启动时把**上次导入的**节假日数据叠加进来，否则重启后导入的内容
        // 就不生效了（用户会以为导入丢了）。重建内部会调用 refresh()。
        rebuildHolidayOverlay()
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
        val exporter = dataExporter
            ?: return "导出不可用：当前环境未提供导出实现"
        exporter.exportAll(repository.findAll())
    }.getOrElse { e ->
        "导出失败：${e.message ?: e::class.simpleName}"
    }

    // ---------------- 导入节假日数据（需求反馈） ----------------

    /**
     * 导出节假日导入模板（Excel 格式）。
     *
     * 模板含表头与几行示例，用户填完可直接导入。
     * 文件位置由平台实现决定（桌面弹出保存对话框）。
     */
    fun exportHolidayTemplate(): String = runCatching {
        holidayTransfer?.exportTemplate() ?: "当前环境不支持导出模板"
    }.getOrElse { e ->
        "导出模板失败：${e.message ?: e::class.simpleName}"
    }

    /**
     * 导入用户的节假日数据。
     *
     * 成功后立即刷新日历，使用户马上看到「休 / 班」的变化。
     * 解析失败的行不会中断整体导入，而是随结果一起返回，
     * 由界面完整展示 —— 静默跳过会让用户以为导入成功却看不到变化。
     */
    fun importHolidays(): String {
        val transfer = holidayTransfer
            ?: return "当前环境不支持导入节假日数据"

        // 文件选择与解析都可能失败（选错文件、文件损坏），统一兜底为可读文本。
        // 注意不能用 runCatching + 非局部 return —— 那会让整个表达式变成
        // Result<String> 而不是 String。
        val result = runCatching { transfer.pickAndParse() }.getOrElse { e ->
            return "导入失败：${e.message ?: e::class.simpleName}"
        } ?: return "已取消导入"

        if (result.rules.isEmpty()) {
            return buildString {
                appendLine("未能导入任何数据。")
                result.errors.take(10).forEach { appendLine("· $it") }
            }
        }

        // 用导入数据整体替换：用户重新导入修正表时，期望"以这份为准"
        runCatching { repository.replaceAllHolidays(result.rules) }.getOrElse { e ->
            return "写入数据库失败：${e.message ?: e::class.simpleName}"
        }
        // 重建叠加层，让新数据立即生效
        rebuildHolidayOverlay()

        return buildString {
            appendLine("已导入 ${result.successCount} 条节假日数据。")
            appendLine("覆盖范围：${result.rules.first().date} ~ ${result.rules.last().date}")
            if (result.hasErrors) {
                appendLine()
                appendLine("以下 ${result.errors.size} 行未导入：")
                result.errors.take(10).forEach { appendLine("· $it") }
                if (result.errors.size > 10) {
                    appendLine("· …（其余 ${result.errors.size - 10} 行略）")
                }
            }
        }
    }

    /** 清空用户导入的节假日数据，回退到库内置数据。 */
    fun clearImportedHolidays(): String = runCatching {
        repository.replaceAllHolidays(emptyList())
        rebuildHolidayOverlay()
        "已清空导入的节假日数据，回退到内置数据。"
    }.getOrElse { e ->
        "清空失败：${e.message ?: e::class.simpleName}"
    }

    /** 当前导入数据的条数与覆盖范围，供设置界面展示。 */
    fun importedHolidaySummary(): String {
        val rules = repository.findAllHolidays()
        if (rules.isEmpty()) return "尚未导入（当前使用内置数据）"
        return "${rules.size} 条，覆盖 ${rules.first().date} ~ ${rules.last().date}"
    }

    /**
     * 按当前仓库内容重建"内置 + 导入"的叠加层。
     *
     * [lunarService] 是 `val`，无法直接替换，因此这里把它改为可变引用。
     * 重建而非增量更新：导入是低频操作（一年一两次），
     * 重新构造一张表的成本可以忽略，但能避免增量维护出错的复杂逻辑。
     */
    private fun rebuildHolidayOverlay() {
        lunarService = OverlayLunarService(
            base = LunarJavaService(),
            table = HolidayTable(repository.findAllHolidays()),
        )
        refresh()
    }

    // ---------------- 多选与批量操作（需求 3） ----------------

    /**
     * 多选模式下已选中的待办 id。
     *
     * 存 id 而非 [TodoItem] 对象：批量操作之间会 `refresh()` 重新查库并
     * 生成新对象，存对象会因引用不等而失效。
     */
    var selectedTodoIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 是否处于多选模式。选中集合为空**不代表**退出多选 —— 两者相互独立。 */
    var todoSelectionMode by mutableStateOf(false)
        private set

    /**
     * 进入/退出多选模式。退出时清空选中，避免下次进入时带着旧选择。
     *
     * 方法名刻意不用 `setTodoSelectionMode` —— 那会与 [todoSelectionMode]
     * 属性自动生成的 setter 产生 JVM 签名冲突（编译报 Platform declaration clash）。
     */
    fun changeTodoSelectionMode(enabled: Boolean) {
        todoSelectionMode = enabled
        if (!enabled) selectedTodoIds = emptySet()
    }

    /** 切换某条待办的选中状态。 */
    fun toggleTodoSelection(item: TodoItem) {
        selectedTodoIds = if (item.id in selectedTodoIds) {
            selectedTodoIds - item.id
        } else {
            selectedTodoIds + item.id
        }
    }

    /** 全选当前显示的待办；已全选时取消全选。 */
    fun toggleSelectAll() {
        val visible = selectedDateTodos.map { it.id }.toSet()
        selectedTodoIds =
            if (visible.isNotEmpty() && selectedTodoIds.containsAll(visible)) emptySet() else visible
    }

    /**
     * 批量标记完成/未完成。
     *
     * 已处于目标状态的条目会被跳过：既省一次无意义的写库，
     * 也避免反复刷新修改时间之类的字段。
     */
    fun completeSelected(completed: Boolean): String {
        val targets = selectedDateTodos.filter {
            it.id in selectedTodoIds && it.isCompleted != completed
        }
        if (targets.isEmpty()) {
            return if (selectedTodoIds.isEmpty()) "请先选择待办" else "所选待办已是该状态"
        }
        targets.forEach { repository.setCompleted(it.id, completed) }
        selectedTodoIds = emptySet()
        refresh()
        return "已将 ${targets.size} 条标记为${if (completed) "已完成" else "未完成"}"
    }

    /**
     * 批量删除。
     *
     * 不弹二次确认：用户已通过"选中 + 点删除"表达了明确意图，再问一次属于
     * 重复询问。误操作代价也有限（重新添加即可），而多选本就是为了省点击。
     */
    fun deleteSelected(): String {
        if (selectedTodoIds.isEmpty()) return "请先选择待办"
        val count = selectedTodoIds.size
        selectedTodoIds.forEach { repository.delete(it) }
        selectedTodoIds = emptySet()
        refresh()
        return "已删除 $count 条待办"
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
        // 0. 不变式守卫：月视图下，锚点必须与选中日同月。
        //
        //    用户报告「点击今天也会跳到 2 月」—— 这类现象的本质是
        //    "锚点被钉在某个旧月份，而选中日已经走到别处"，
        //    属于**不变式被破坏**的状态。逐一排查所有变更路径都没能找到
        //    破坏源（穷举测试、真实组件树 UI 测试、42 格网格复用测试
        //    全部无法复现），因此改为在此处**强制维持**该不变式：
        //    任何未知路径把两者弄到不同月份，都会在下一次刷新时被纠正，
        //    而不会表现为"越点越退回旧月份"。
        //
        //    放在刷新最开始：取消选中日所在月后，后续的范围计算与格子
        //    构造都基于修正后的锚点，不会产生"锚点与网格不同月"的中间态。
        if (viewMode == CalendarViewMode.MONTH &&
            YearMonth.from(anchorDate) != YearMonth.from(selectedDate)
        ) {
            anchorDate = selectedDate
        }

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
