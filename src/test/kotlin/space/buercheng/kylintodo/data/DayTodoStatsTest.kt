package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.domain.DayTodoStats
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoPriority
import space.buercheng.kylintodo.testing.InMemoryTodoRepository
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 每日待办聚合统计测试（日历格子的计数与优先级着色依赖它）。
 *
 * 关键语义：**只统计未完成待办**。若把已完成的也算进去，
 * 用户勾掉重要事项后格子上的红色标记仍会留着，属于误导性显示。
 */
class DayTodoStatsTest {

    private lateinit var tempDir: Path
    private lateinit var dbFile: Path
    private val day = LocalDate.of(2026, 10, 1)
    private val nextDay = LocalDate.of(2026, 10, 2)

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("kylintodo-stats")
        dbFile = tempDir.resolve("t.db")
    }

    @AfterTest
    fun tearDown() {
        runCatching { tempDir.toFile().deleteRecursively() }
    }

    // ------------------------------------------------------------ SQLite

    @Test
    fun `空库返回空统计`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            assertTrue(repo.statsByDateRange(day, nextDay).isEmpty())
        } finally {
            repo.close()
        }
    }

    @Test
    fun `统计数量与最高优先级`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            repo.insert(TodoItem.createOrNull("低", day, priority = TodoPriority.LOW)!!)
            repo.insert(TodoItem.createOrNull("高", day, priority = TodoPriority.HIGH)!!)
            repo.insert(TodoItem.createOrNull("中", day, priority = TodoPriority.MEDIUM)!!)

            val stats = repo.statsByDateRange(day, day)
            assertEquals(1, stats.size)
            assertEquals(3, stats[day]!!.count, "当天共 3 条")
            assertEquals(
                TodoPriority.HIGH.level, stats[day]!!.maxPriorityLevel,
                "最高优先级应为 HIGH，与插入顺序无关",
            )
        } finally {
            repo.close()
        }
    }

    @Test
    fun `已完成待办不计入统计`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            val high = TodoItem.createOrNull("重要但已完成", day, priority = TodoPriority.HIGH)!!
            val low = TodoItem.createOrNull("未完成", day, priority = TodoPriority.LOW)!!
            repo.insert(high)
            repo.insert(low)
            repo.setCompleted(high.id, true)

            val stats = repo.statsByDateRange(day, day)
            assertEquals(
                1, stats[day]!!.count,
                "已完成的不应计入数量，否则格子上的提示不会消失",
            )
            assertEquals(
                TodoPriority.LOW.level, stats[day]!!.maxPriorityLevel,
                "最高优先级应只从未完成项中取 —— 否则勾掉高优先级后红色标记仍在",
            )
        } finally {
            repo.close()
        }
    }

    @Test
    fun `全部完成时该日不出现在统计中`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            val item = TodoItem.createOrNull("唯一", day, priority = TodoPriority.HIGH)!!
            repo.insert(item)
            repo.setCompleted(item.id, true)

            val stats = repo.statsByDateRange(day, day)
            assertTrue(
                stats[day] == null || stats[day]!!.count == 0,
                "全部完成后不应再提示有未办事项，实际 $stats",
            )
        } finally {
            repo.close()
        }
    }

    @Test
    fun `无优先级待办的最高等级为0`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            repo.insert(TodoItem.createOrNull("普通", day)!!)
            val stats = repo.statsByDateRange(day, day)
            assertEquals(1, stats[day]!!.count)
            assertEquals(0, stats[day]!!.maxPriorityLevel)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `按日期分组互不干扰`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            repo.insert(TodoItem.createOrNull("今天高", day, priority = TodoPriority.HIGH)!!)
            repo.insert(TodoItem.createOrNull("明天低", nextDay, priority = TodoPriority.LOW)!!)

            val stats = repo.statsByDateRange(day, nextDay)
            assertEquals(2, stats.size)
            assertEquals(TodoPriority.HIGH.level, stats[day]!!.maxPriorityLevel)
            assertEquals(TodoPriority.LOW.level, stats[nextDay]!!.maxPriorityLevel)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `区间外的日期不返回`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            repo.insert(
                TodoItem.createOrNull("区间外", day.plusDays(10), priority = TodoPriority.HIGH)!!,
            )
            assertTrue(
                repo.statsByDateRange(day, nextDay).isEmpty(),
                "超出区间的日期不应出现在结果里",
            )
        } finally {
            repo.close()
        }
    }

    @Test
    fun `单日区间与多日区间结果一致`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            repo.insert(TodoItem.createOrNull("A", day, priority = TodoPriority.MEDIUM)!!)
            repo.insert(TodoItem.createOrNull("B", nextDay, priority = TodoPriority.HIGH)!!)

            val single = repo.statsByDateRange(day, day)
            val multi = repo.statsByDateRange(day, nextDay)
            assertEquals(single[day], multi[day], "同一日期在两个区间下的统计必须一致")
        } finally {
            repo.close()
        }
    }

    // --------------------------------------------------------- 内存实现

    /**
     * 内存实现与 SQLite 实现必须同语义 —— 否则测试通过而真机表现不同。
     */
    @Test
    fun `内存实现与SQLite语义一致`() {
        val memory = InMemoryTodoRepository()
        val sqlite = SqliteTodoRepository(dbFile)
        try {
            val items = listOf(
                TodoItem.createOrNull("A", day, priority = TodoPriority.LOW)!!,
                TodoItem.createOrNull("B", day, priority = TodoPriority.HIGH)!!,
                TodoItem.createOrNull("C", nextDay, priority = TodoPriority.NONE)!!,
            )
            items.forEach { memory.insert(it); sqlite.insert(it) }
            // 把 B 标记完成，验证两边都排除它
            memory.setCompleted(items[1].id, true)
            sqlite.setCompleted(items[1].id, true)

            val memStats = memory.statsByDateRange(day, nextDay)
            val dbStats = sqlite.statsByDateRange(day, nextDay)

            assertEquals(dbStats.keys, memStats.keys, "两边返回的日期集合应一致")
            dbStats.forEach { (date, dbStat) ->
                assertEquals(dbStat, memStats[date], "$date 的统计在两种实现下应一致")
            }
        } finally {
            sqlite.close()
        }
    }

    @Test
    fun `内存实现排除已完成项`() {
        val memory = InMemoryTodoRepository()
        val high = TodoItem.createOrNull("重要", day, priority = TodoPriority.HIGH)!!
        memory.insert(high)
        memory.insert(TodoItem.createOrNull("次要", day, priority = TodoPriority.LOW)!!)
        memory.setCompleted(high.id, true)

        val stats = memory.statsByDateRange(day, day)
        assertEquals(1, stats[day]!!.count)
        assertEquals(TodoPriority.LOW.level, stats[day]!!.maxPriorityLevel)
    }

    /**
     * 日历格子的着色依赖 CalendarDay.maxPriorityLevel 被正确透传。
     */
    @Test
    fun `CalendarDay 透传最高优先级`() {
        val memory = InMemoryTodoRepository()
        memory.insert(TodoItem.createOrNull("高", day, priority = TodoPriority.HIGH)!!)
        val stats = memory.statsByDateRange(day, day)

        val page = space.buercheng.kylintodo.domain.CalendarGridBuilder.buildMonth(
            month = java.time.YearMonth.of(2026, 10),
            selected = day,
            dayEnricher = { d ->
                val s = stats[d]
                space.buercheng.kylintodo.domain.DayEnrichment(
                    todoCount = s?.count ?: 0,
                    maxPriorityLevel = s?.maxPriorityLevel ?: 0,
                )
            },
        )
        val cell = page.days.first { it.date == day }
        assertEquals(1, cell.todoCount)
        assertEquals(
            TodoPriority.HIGH.level, cell.maxPriorityLevel,
            "网格绘制时必须能拿到最高优先级，否则格子无法着色",
        )
    }
}
