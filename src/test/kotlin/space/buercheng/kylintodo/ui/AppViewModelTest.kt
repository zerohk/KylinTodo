package space.buercheng.kylintodo.ui

import space.buercheng.kylintodo.domain.CalendarGridBuilder
import space.buercheng.kylintodo.domain.CalendarViewMode
import space.buercheng.kylintodo.domain.DayType
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoRepository
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 测试用内存仓库，避免 UI 状态测试依赖 SQLite。 */
private class InMemoryTodoRepository : TodoRepository {
    private val items = mutableListOf<TodoItem>()

    override fun findByDate(date: LocalDate) =
        items.filter { it.date == date }.sortedBy { it.createdAt }

    override fun findByDateRange(start: LocalDate, end: LocalDate) =
        items.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            .sortedWith(compareBy({ it.date }, { it.createdAt }))

    override fun countByDateRange(start: LocalDate, end: LocalDate) =
        items.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            .groupingBy { it.date }.eachCount()

    override fun insert(item: TodoItem) { items.add(item) }

    override fun setCompleted(id: String, completed: Boolean) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) items[idx] = items[idx].copy(isCompleted = completed)
    }

    override fun delete(id: String) { items.removeAll { it.id == id } }

    fun all() = items.toList()
}

/**
 * ViewModel 状态与编排验证。
 *
 * 重点覆盖需求里的三条硬性约定：
 *  - 月视图必须产出 42 天（6 行 × 7 列）
 *  - 每个格子必须带农历 / 节气信息
 *  - 添加待办后格子上的数量角标要刷新
 */
class AppViewModelTest {

    private val fixedToday = LocalDate.of(2026, 10, 1)

    private fun vm(repo: TodoRepository = InMemoryTodoRepository()) =
        AppViewModel(
            repository = repo,
            todayProvider = { fixedToday },
        )

    @Test
    fun `默认进入月视图并产出42天`() {
        val vm = vm()
        assertEquals(CalendarViewMode.MONTH, vm.viewMode)
        assertEquals(42, vm.page.days.size, "月视图必须是 42 天")
        assertEquals(6, vm.page.rows)
        assertEquals(7, vm.page.columns)
    }

    @Test
    fun `每个格子都带农历或节气信息`() {
        val vm = vm()
        val missing = vm.page.days.filter {
            it.lunarText.isBlank() && it.solarTerm == null
        }
        // 允许极少数情况（理论上不应发生），但绝不允许整页空白
        assertTrue(missing.isEmpty(), "有 ${missing.size} 个格子缺少农历/节气信息")
    }

    @Test
    fun `月视图覆盖国庆假期与调休`() {
        val vm = vm()
        // 2026-10-01 国庆节放假
        val nationalDay = vm.page.days.first { it.date == LocalDate.of(2026, 10, 1) }
        assertEquals(DayType.HOLIDAY, nationalDay.dayType)
        assertEquals("国庆节", nationalDay.holidayName)

        // 2026-10-10 为国庆调休上班（依据库数据 2026-10-10 班）
        val makeup = vm.page.days.firstOrNull { it.date == LocalDate.of(2026, 10, 10) }
        assertNotNull(makeup, "10月10日应在 42 天网格内")
        assertEquals(DayType.WORKDAY, makeup.dayType, "10月10日应为调休上班")
    }

    @Test
    fun `切换到周视图产出7天`() {
        val vm = vm()
        vm.changeViewMode(CalendarViewMode.WEEK)
        assertEquals(7, vm.page.days.size)
        assertEquals(1, vm.page.days.first().date.dayOfWeek.value, "周视图首列应为周一")
    }

    @Test
    fun `切换到日视图产出该日所在的一周`() {
        val vm = vm()
        vm.changeViewMode(CalendarViewMode.DAY)
        // 需求变更：日视图同样是一行七列，行高与月视图一致
        assertEquals(7, vm.page.days.size)
        assertEquals(7, vm.page.columns)
        assertEquals(1, vm.page.rows)
        assertEquals(1, vm.page.days.first().date.dayOfWeek.value, "首列应为周一")
        assert(
            vm.page.days.any { it.date == fixedToday }
        ) { "选中日应包含在周内: $fixedToday" }
    }

    @Test
    fun `翻月后标题与网格同步更新`() {
        val vm = vm()
        val before = vm.pageTitle
        assertTrue(before.contains("10"), "初始应为 10 月，实际: $before")

        vm.goNext()
        assertTrue(vm.pageTitle.contains("11"), "下一页应为 11 月，实际: ${vm.pageTitle}")
        assertEquals(42, vm.page.days.size, "翻月后仍必须保持 42 天")
        assertTrue(
            vm.page.days.any { it.date.monthValue == 11 },
            "翻月后网格应包含 11 月日期",
        )
    }

    @Test
    fun `翻月时日期溢出到短月份会被夹取`() {
        val vm = AppViewModel(
            repository = InMemoryTodoRepository(),
            todayProvider = { LocalDate.of(2026, 1, 31) },
        )
        // 1月31日 -> 2月，2月没有 31 日，应夹取到 2月28日而不是抛异常
        vm.goNext()
        assertEquals(LocalDate.of(2026, 2, 28), vm.selectedDate)
    }

    @Test
    fun `选中相邻月份日期时锚点跟随`() {
        val vm = vm() // 锚定 2026-10，网格起始为 2026-09-28
        val leadingDay = vm.page.days.first().date
        assertEquals(9, leadingDay.monthValue, "网格首个日期应来自 9 月")

        vm.selectDate(leadingDay)
        assertTrue(
            vm.pageTitle.contains("9"),
            "选中 9 月日期后应切到 9 月，实际: ${vm.pageTitle}",
        )
    }

    @Test
    fun `添加待办后列表与角标同步刷新`() {
        val repo = InMemoryTodoRepository()
        val vm = vm(repo)

        assertTrue(vm.selectedDateTodos.isEmpty())
        val ok = vm.addTodo("  写单元测试  ", vm.selectedDate)
        assertTrue(ok, "合法内容应添加成功")

        assertEquals(1, vm.selectedDateTodos.size)
        assertEquals("写单元测试", vm.selectedDateTodos.first().text, "首尾空白应被去除")

        // 网格中对应格子的角标数量也必须刷新
        val cell = vm.page.days.first { it.date == vm.selectedDate }
        assertEquals(1, cell.todoCount, "格子上的待办数量角标应刷新")
    }

    @Test
    fun `空内容不会添加且返回false`() {
        val repo = InMemoryTodoRepository()
        val vm = vm(repo)

        assertTrue(!vm.addTodo("", vm.selectedDate))
        assertTrue(!vm.addTodo("    ", vm.selectedDate))
        assertTrue(!vm.addTodo("\t\n", vm.selectedDate))

        assertTrue(vm.selectedDateTodos.isEmpty())
        assertTrue(repo.all().isEmpty(), "非法输入不应写入仓库")
    }

    @Test
    fun `邻近月份格子上的待办角标也要统计到`() {
        // 月视图网格含相邻月份溢出日期（9/28-9/30、11/1-11/8），
        // 这些格子上的待办数量同样必须显示，否则用户会看到"有事项但没角标"
        val repo = InMemoryTodoRepository()
        val vm = vm(repo)

        val leading = vm.page.days.first().date // 2026-09-28
        vm.addTodo("九月的事", leading)

        val cell = vm.page.days.first { it.date == leading }
        assertEquals(1, cell.todoCount, "相邻月份格子的角标应被统计")
    }

    @Test
    fun `切换完成状态`() {
        val vm = vm()
        vm.addTodo("待勾选", vm.selectedDate)
        val item = vm.selectedDateTodos.first()
        assertTrue(!item.isCompleted)

        vm.toggleCompleted(item)
        assertTrue(vm.selectedDateTodos.first().isCompleted)

        vm.toggleCompleted(vm.selectedDateTodos.first())
        assertTrue(!vm.selectedDateTodos.first().isCompleted)
    }

    @Test
    fun `删除待办后角标归零`() {
        val vm = vm()
        vm.addTodo("待删除", vm.selectedDate)
        assertEquals(1, vm.page.days.first { it.date == vm.selectedDate }.todoCount)

        vm.deleteTodo(vm.selectedDateTodos.first())
        assertTrue(vm.selectedDateTodos.isEmpty())
        assertEquals(0, vm.page.days.first { it.date == vm.selectedDate }.todoCount)
    }

    @Test
    fun `回到今天`() {
        val vm = vm()
        vm.goNext()
        vm.goNext()
        assertTrue(vm.pageTitle.contains("12"), "实际: ${vm.pageTitle}")

        vm.goToday()
        assertEquals(fixedToday, vm.anchorDate)
        assertEquals(fixedToday, vm.selectedDate)
        assertTrue(vm.pageTitle.contains("10"))
    }

    @Test
    fun `添加弹窗目标日期可指定为任意格子`() {
        val vm = vm()
        assertNull(vm.addTodoTargetDate)

        val target = vm.page.days[10].date
        vm.openAddTodo(target)
        assertEquals(target, vm.addTodoTargetDate)

        vm.dismissAddTodo()
        assertNull(vm.addTodoTargetDate)
    }

    @Test
    fun `pageTitle 在三种视图下均非空`() {
        val vm = vm()
        CalendarViewMode.entries.forEach { mode ->
            vm.changeViewMode(mode)
            assertTrue(vm.pageTitle.isNotBlank(), "$mode 视图标题不应为空")
        }
    }

    @Test
    fun `网格规格常量与实际输出一致`() {
        val vm = vm()
        assertEquals(CalendarGridBuilder.MONTH_CELL_COUNT, vm.page.days.size)
        vm.changeViewMode(CalendarViewMode.WEEK)
        assertEquals(CalendarGridBuilder.WEEK_CELL_COUNT, vm.page.days.size)
    }
}
