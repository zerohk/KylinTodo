package space.buercheng.kylintodo.testing

import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoRepository
import java.time.LocalDate

/**
 * 测试用内存仓库。
 *
 * 与 [space.buercheng.kylintodo.data.SqliteTodoRepository] 保持同样的排序约定
 * （优先级降序 → 创建时间升序），这样基于内存仓库的状态测试能反映真实行为。
 *
 * 集中放在这里，避免每个测试文件各写一份导致行为漂移。
 */
class InMemoryTodoRepository : TodoRepository {

    private val items = mutableListOf<TodoItem>()

    override fun findByDate(date: LocalDate): List<TodoItem> =
        items.filter { it.date == date }
            .sortedWith(compareByDescending<TodoItem> { it.priority.level }.thenBy { it.createdAt })

    override fun findByDateRange(start: LocalDate, end: LocalDate): List<TodoItem> =
        items.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            .sortedWith(
                compareBy<TodoItem> { it.date }
                    .thenByDescending { it.priority.level }
                    .thenBy { it.createdAt },
            )

    override fun findAll(): List<TodoItem> =
        items.sortedWith(compareBy<TodoItem> { it.date }.thenBy { it.createdAt })

    override fun countByDateRange(start: LocalDate, end: LocalDate): Map<LocalDate, Int> =
        items.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            .groupingBy { it.date }
            .eachCount()

    override fun insert(item: TodoItem) {
        items.add(item)
    }

    override fun setCompleted(id: String, completed: Boolean) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = items[index].copy(isCompleted = completed)
    }

    override fun delete(id: String) {
        items.removeAll { it.id == id }
    }

    /** 仅供断言使用：当前全部记录（含排序前的原始顺序）。 */
    fun all(): List<TodoItem> = items.toList()
}
