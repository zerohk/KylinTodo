package space.buercheng.kylintodo.ui

import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoRepository
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import space.buercheng.kylintodo.data.SqliteTodoRepository

/**
 * 用**真实 SQLite** 验证：翻到未来日期后添加待办，数据究竟落在哪一天。
 *
 * 目的是排除"日期上下文被重置导致存错天"的可能 —— 若数据落在正确的
 * 未来日期，说明状态模型没有问题，用户观察到的"跳回"是别的原因。
 */
class FutureDatePersistenceTest {

    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun `用户操作序列下的真实落库日期`() {
        val dir = Files.createTempDirectory("future-date-it")
        val db = dir.resolve("t.db")
        val repo = SqliteTodoRepository(db)
        try {
            val vm = AppViewModel(repository = repo, todayProvider = { today })

            // 模拟用户：翻到 2027-01，点某天，添加待办
            repeat(3) { vm.goNext() }
            println("[TEST] 翻页后 anchor=${vm.anchorDate} selected=${vm.selectedDate}")

            val clicked = LocalDate.of(2027, 1, 15)
            vm.selectDate(clicked)
            println("[TEST] 点击 ${clicked} 后 selected=${vm.selectedDate}")

            vm.openAddTodo(clicked)
            println("[TEST] 弹窗目标=${vm.addTodoTargetDate}")

            vm.addTodo("未来的待办", clicked)
            println("[TEST] 添加后 anchor=${vm.anchorDate} selected=${vm.selectedDate}")

            // 直接查库：数据落在哪一天？
            val all = repo.findByDateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1))
            println("[TEST] 库中记录: " + all.map { "${it.text}@${it.date}" })

            assertEquals(1, all.size)
            assertEquals(clicked, all.first().date, "数据必须落在用户点的 2027-01-15")
            assertEquals(
                "未来的待办", all.first().text,
            )

            // 状态未被重置
            assertEquals(2027, vm.anchorDate.year)
            assertEquals(1, vm.anchorDate.monthValue)
            assertEquals(clicked, vm.selectedDate)
        } finally {
            repo.close()
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `添加待办后选中日应能看到该待办`() {
        val dir = Files.createTempDirectory("future-date-it2")
        val db = dir.resolve("t.db")
        val repo = SqliteTodoRepository(db)
        try {
            val vm = AppViewModel(repository = repo, todayProvider = { today })
            repeat(3) { vm.goNext() }

            val clicked = LocalDate.of(2027, 1, 15)
            vm.selectDate(clicked)
            vm.openAddTodo(clicked)
            vm.addTodo("可见性检查", clicked)

            // 选中日就是被点的那天，列表里应当能看到
            assertEquals(clicked, vm.selectedDate)
            assertEquals(1, vm.selectedDateTodos.size, "选中日应能看到刚添加的待办")
        } finally {
            repo.close()
            dir.toFile().deleteRecursively()
        }
    }
}
