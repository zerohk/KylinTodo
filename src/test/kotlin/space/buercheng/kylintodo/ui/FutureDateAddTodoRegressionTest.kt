package space.buercheng.kylintodo.ui

import space.buercheng.kylintodo.domain.CalendarViewMode
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoRepository
import space.buercheng.kylintodo.domain.startOfWeekMonday
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 内存仓库替身，供回归测试使用。 */
private class MemRepo : TodoRepository {
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
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = items[i].copy(isCompleted = completed)
    }
    override fun delete(id: String) { items.removeAll { it.id == id } }
}

/**
 * 把日历翻到未来年份后添加待办，验证选中日期**不会**被重置回"今天"。
 *
 * 复现用户反馈：「切到 2027 年 1 月，随便点一天添加待办，会跳回当前日期」。
 */
class FutureDateAddTodoRegressionTest {

    private val today = LocalDate.of(2026, 10, 1)

    private fun vm() = AppViewModel(
        repository = MemRepo(),
        todayProvider = { today },
    )

    @Test
    fun `翻到2027年1月后添加待办不会跳回今天`() {
        val vm = vm()
        assertEquals(today, vm.selectedDate)

        // 翻 3 个月到 2027-01
        repeat(3) { vm.goNext() }
        assertEquals(2027, vm.anchorDate.year)
        assertEquals(1, vm.anchorDate.monthValue)
        val anchorBefore = vm.anchorDate

        // 随便点一天并添加待办
        val target = LocalDate.of(2027, 1, 15)
        vm.openAddTodo(target)
        assertEquals(target, vm.addTodoTargetDate, "弹窗目标日期应为所点的那天")

        val ok = vm.addTodo("2027 的待办", target)
        assertTrue(ok)

        // ---------- 关键断言：不应跳回今天 ----------
        assertEquals(anchorBefore, vm.anchorDate, "锚定月份不应被重置")
        assertEquals(2027, vm.anchorDate.year, "年份应仍为 2027")
        assertEquals(1, vm.anchorDate.monthValue, "月份应仍为 1")
        // 选中日跟随目标日期（openAddTodo 的有意设计，使侧栏能立刻看到新待办）
        assertEquals(target, vm.selectedDate, "选中日应跟随所点日期")
        assertTrue(
            vm.page.days.any { it.date == target },
            "网格里应仍能找到 2027-01-15，实际网格范围: " +
                "${vm.page.days.first().date} ~ ${vm.page.days.last().date}",
        )
        assertEquals(1, vm.selectedDateTodos.size, "新增的待办应在选中日列表中")
    }

    @Test
    fun `翻到未来年份后添加待办数据落在正确的日期上`() {
        val vm = vm()
        repeat(3) { vm.goNext() }
        val target = LocalDate.of(2027, 1, 15)

        vm.openAddTodo(target)
        vm.addTodo("归属日期校验", target)

        // 切到该日期，待办必须出现在这里
        vm.selectDate(target)
        assertEquals(target, vm.selectedDate)
        assertTrue(vm.selectedDateTodos.any { it.text == "归属日期校验" })

        // 回到今天，不应看到这条待办
        vm.goToday()
        assertTrue(
            vm.selectedDateTodos.none { it.text == "归属日期校验" },
            "今天的列表里不应出现 2027 年的待办",
        )
    }

    @Test
    fun `翻到上一年度添加待办同样不跳回`() {
        val vm = vm()
        repeat(12) { vm.goPrevious() }
        assertEquals(2025, vm.anchorDate.year)
        val anchorBefore = vm.anchorDate

        val target = LocalDate.of(2025, 10, 20)
        vm.openAddTodo(target)
        vm.addTodo("2025 的待办", target)

        assertEquals(anchorBefore, vm.anchorDate)
        assertEquals(2025, vm.anchorDate.year)
    }

    @Test
    fun `周视图与日视图下翻到未来也不跳回`() {
        CalendarViewMode.entries.forEach { mode ->
            val vm = vm()
            vm.changeViewMode(mode)
            repeat(3) { vm.goNext() }

            // 取网格中"当前周期内"的一天：月视图的 days[3] 往往是相邻月份的
            // 溢出日期，选中它会让锚点按设计切到相邻月，干扰本测试意图。
            val target = vm.page.days.first { it.inCurrentPeriod }.date
            vm.openAddTodo(target)
            vm.addTodo("$mode 的待办", target)

            assertEquals(target, vm.selectedDate, "$mode 视图下选中日应跟随所点日期")
            assertTrue(
                vm.anchorDate.startOfWeekMonday() == target.startOfWeekMonday(),
                "$mode 视图下锚点应仍在目标日所在周，实际 anchor=${vm.anchorDate} target=$target",
            )
            assertEquals(
                target.year, vm.anchorDate.year,
                "$mode 视图下年份应与目标日一致，实际 anchor=${vm.anchorDate} target=$target",
            )
        }
    }

    @Test
    fun `反复开关添加弹窗不会改变日期`() {
        val vm = vm()
        repeat(3) { vm.goNext() }
        val anchorBefore = vm.anchorDate
        // 用与当前选中日相同的目标，验证开关弹窗本身不产生任何偏移
        val sameDay = vm.selectedDate

        repeat(5) {
            vm.openAddTodo(sameDay)
            vm.dismissAddTodo()
        }

        assertEquals(anchorBefore, vm.anchorDate, "锚点不应改变")
        assertEquals(sameDay, vm.selectedDate, "选中日不应改变")
        assertTrue(vm.addTodoTargetDate == null, "关闭后弹窗目标应为 null")
    }

    @Test
    fun `打开弹窗会选中目标日从而让新待办立刻可见`() {
        // 这条锁定 openAddTodo 的有意行为：侧栏只显示选中日的待办，
        // 若不切换选中，用户为别日添加待办后界面毫无变化，会误以为失败。
        val vm = vm()
        repeat(3) { vm.goNext() }

        val target = LocalDate.of(2027, 1, 20)
        vm.openAddTodo(target)
        assertEquals(target, vm.selectedDate, "打开弹窗应同时选中该日")

        vm.addTodo("立刻可见", target)
        assertEquals(
            1, vm.selectedDateTodos.size,
            "添加后侧栏应立刻能看到该待办",
        )
    }
}
