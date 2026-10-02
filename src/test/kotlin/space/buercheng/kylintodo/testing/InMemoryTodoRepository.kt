package space.buercheng.kylintodo.testing

import space.buercheng.kylintodo.domain.DayTodoStats
import space.buercheng.kylintodo.domain.HolidayRule
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

    /**
     * 与 SQLite 实现保持同一语义：只统计**未完成**待办，
     * 并给出其中最高优先级等级。两者行为必须一致，
     * 否则测试通过而真机表现不同。
     */
    override fun statsByDateRange(
        start: LocalDate,
        end: LocalDate,
    ): Map<LocalDate, DayTodoStats> =
        items.filter {
            !it.date.isBefore(start) && !it.date.isAfter(end) && !it.isCompleted
        }
            .groupBy { it.date }
            .mapValues { (_, list) ->
                DayTodoStats(
                    count = list.size,
                    maxPriorityLevel = list.maxOf { it.priority.level },
                )
            }

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

    // ---------------- 用户导入的节假日 ----------------

    private var holidays: List<HolidayRule> = emptyList()

    override fun findAllHolidays(): List<HolidayRule> = holidays

    override fun replaceAllHolidays(rules: List<HolidayRule>) {
        holidays = rules.toList()
    }

    /** 仅供断言使用：当前全部记录（含排序前的原始顺序）。 */
    fun all(): List<TodoItem> = items.toList()
}
