package space.buercheng.kylintodo.integration

import space.buercheng.kylintodo.data.SqliteTodoRepository
import space.buercheng.kylintodo.domain.CalendarViewMode
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.ui.AppViewModel
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 持久化集成测试：ViewModel + 真实 SQLite 文件。
 *
 * 单元测试 `AppViewModelTest` 用的是内存仓库，只能证明状态流转正确；
 * 这里用**真实数据库文件**，目的是证明 F-04（标记完成）与 F-05（删除）
 * 的效果确实落盘，而不是仅停留在内存里 —— 即"关掉再开还在/不在"。
 *
 * 之所以需要这层测试：Compose Desktop 的界面点击无法在当前环境下用合成
 * 输入驱动（见项目记录），因此通过 ViewModel 走完整条数据链路，
 * 用数据库内容作为可验证的证据。
 */
class TodoPersistenceIntegrationTest {

    private lateinit var tempDir: Path
    private lateinit var dbFile: Path
    private val today = LocalDate.of(2026, 10, 1)

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("kylintodo-it")
        dbFile = tempDir.resolve("kylintodo.db")
    }

    @AfterTest
    fun tearDown() {
        runCatching { tempDir.toFile().deleteRecursively() }
    }

    /** 打开一个"应用实例"：真实 SQLite 仓库 + ViewModel。 */
    private fun openApp(): Pair<AppViewModel, SqliteTodoRepository> {
        val repo = SqliteTodoRepository(dbFile)
        val vm = AppViewModel(repository = repo, todayProvider = { today })
        return vm to repo
    }

    @Test
    fun `新增待办会写入数据库并可被新实例读取`() {
        val (vm, repo) = openApp()
        try {
            assertTrue(vm.addTodo("集成测试任务", today))
            assertEquals(1, vm.selectedDateTodos.size)
        } finally {
            repo.close()
        }

        // 模拟重启：新开一个实例，读同一个数据库文件
        val (vm2, repo2) = openApp()
        try {
            val loaded = vm2.selectedDateTodos
            assertEquals(1, loaded.size, "重启后应自动加载")
            assertEquals("集成测试任务", loaded.first().text)
            assertTrue(!loaded.first().isCompleted, "默认未完成")
        } finally {
            repo2.close()
        }
    }

    @Test
    fun `标记完成会持久化而非仅改内存`() {
        val (vm, repo) = openApp()
        try {
            vm.addTodo("要被勾选的任务", today)
            val item = vm.selectedDateTodos.first()
            assertTrue(!item.isCompleted)

            vm.toggleCompleted(item)
            assertTrue(vm.selectedDateTodos.first().isCompleted, "内存状态应已切换")
        } finally {
            repo.close()
        }

        // 关键断言：重开后仍是已完成，说明 UPDATE 真的落盘了
        val (vm2, repo2) = openApp()
        try {
            assertTrue(
                vm2.selectedDateTodos.first().isCompleted,
                "重启后完成状态应保留",
            )
        } finally {
            repo2.close()
        }
    }

    @Test
    fun `取消完成状态同样持久化`() {
        val (vm, repo) = openApp()
        val item = try {
            vm.addTodo("来回切换", today)
            val created = vm.selectedDateTodos.first()
            vm.toggleCompleted(created)                 // -> 完成
            vm.toggleCompleted(vm.selectedDateTodos.first())  // -> 未完成
            assertTrue(!vm.selectedDateTodos.first().isCompleted)
            vm.selectedDateTodos.first()
        } finally {
            repo.close()
        }

        val (vm2, repo2) = openApp()
        try {
            assertTrue(
                !vm2.selectedDateTodos.first().isCompleted,
                "重启后应为未完成（false 也要正确落盘，不能因默认值而掩盖问题）",
            )
            assertEquals(item.id, vm2.selectedDateTodos.first().id)
        } finally {
            repo2.close()
        }
    }

    @Test
    fun `删除会从数据库真正移除`() {
        val (vm, repo) = openApp()
        try {
            vm.addTodo("待删除任务", today)
            assertEquals(1, vm.selectedDateTodos.size)
            vm.deleteTodo(vm.selectedDateTodos.first())
            assertTrue(vm.selectedDateTodos.isEmpty())
        } finally {
            repo.close()
        }

        val (vm2, repo2) = openApp()
        try {
            assertTrue(
                vm2.selectedDateTodos.isEmpty(),
                "重启后不应再出现已删除的待办",
            )
        } finally {
            repo2.close()
        }
    }

    @Test
    fun `多日多任务互不干扰且角标计数正确`() {
        val (vm, repo) = openApp()
        try {
            val day1 = today
            val day2 = today.plusDays(1)
            val day3 = today.plusDays(2)

            vm.addTodo("d1-a", day1)
            vm.addTodo("d1-b", day1)
            vm.addTodo("d2-a", day2)
            vm.addTodo("d3-a", day3)
            vm.addTodo("d3-b", day3)
            vm.addTodo("d3-c", day3)

            // 月视图网格中的角标必须按日分别统计
            fun count(date: LocalDate) = vm.page.days.first { it.date == date }.todoCount
            assertEquals(2, count(day1))
            assertEquals(1, count(day2))
            assertEquals(3, count(day3))

            // 删除其中一条后计数应下降
            vm.selectDate(day3)
            vm.deleteTodo(vm.selectedDateTodos.first())
            assertEquals(2, vm.page.days.first { it.date == day3 }.todoCount)
        } finally {
            repo.close()
        }

        val (vm2, repo2) = openApp()
        try {
            assertEquals(2, vm2.selectedDateTodos.size, "选中日重启后应剩 2 条")
        } finally {
            repo2.close()
        }
    }

    @Test
    fun `空白内容不会写入数据库`() {
        val (vm, repo) = openApp()
        try {
            assertTrue(!vm.addTodo("", today))
            assertTrue(!vm.addTodo("   ", today))
            assertTrue(!vm.addTodo("\t\n", today))
            assertTrue(vm.selectedDateTodos.isEmpty())
        } finally {
            repo.close()
        }

        val (vm2, repo2) = openApp()
        try {
            assertTrue(vm2.selectedDateTodos.isEmpty(), "非法输入不应留下任何行")
        } finally {
            repo2.close()
        }
    }

    @Test
    fun `农历与节气在真实数据链路上仍然正确`() {
        // 走完整链路（ViewModel -> LunarJavaService）确认节假日信息未被破坏
        val (vm, repo) = openApp()
        try {
            val oct1 = vm.page.days.first { it.date == LocalDate.of(2026, 10, 1) }
            assertEquals("国庆节", oct1.holidayName)
            assertEquals(space.buercheng.kylintodo.domain.DayType.HOLIDAY, oct1.dayType)

            val oct8 = vm.page.days.first { it.date == LocalDate.of(2026, 10, 8) }
            assertEquals("寒露", oct8.solarTerm, "10月8日应为寒露")

            // 调休上班日
            val oct10 = vm.page.days.first { it.date == LocalDate.of(2026, 10, 10) }
            assertEquals(space.buercheng.kylintodo.domain.DayType.WORKDAY, oct10.dayType)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `大量待办下列表与计数保持正确`() {
        // 验证不存在 N+1 查询导致的计数遗漏（refresh 用一次区间查询取全部计数）
        val (vm, repo) = openApp()
        try {
            val dates = (0 until 42).map { today.plusDays(it.toLong()) }
            dates.forEachIndexed { i, d ->
                repeat(i % 5) { vm.addTodo("task-$i-$it", d) }
            }

            dates.forEachIndexed { i, d ->
                val expected = i % 5
                val actual = vm.page.days.firstOrNull { it.date == d }?.todoCount
                if (actual != null) {
                    assertEquals(expected, actual, "$d 的角标计数应为 $expected")
                }
            }
        } finally {
            repo.close()
        }
    }

    @Test
    fun `切换视图与翻页不会丢失已加载的数据`() {
        val (vm, repo) = openApp()
        try {
            vm.addTodo("跨视图任务", today)

            CalendarViewMode.entries.forEach { mode ->
                vm.changeViewMode(mode)
                assertTrue(vm.selectedDateTodos.isNotEmpty(), "$mode 视图下待办不应消失")
            }

            vm.changeViewMode(CalendarViewMode.MONTH)
            vm.goNext()
            vm.goPrevious()
            assertEquals(1, vm.selectedDateTodos.size, "翻页往返后数据应完好")
        } finally {
            repo.close()
        }
    }
}
