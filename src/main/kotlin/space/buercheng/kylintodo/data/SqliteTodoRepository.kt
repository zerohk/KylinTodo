package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.domain.DayTodoStats
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoPriority
import space.buercheng.kylintodo.domain.TodoRepository
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.LocalDate
import java.util.logging.Logger

/**
 * 基于 SQLite 的待办仓库实现（需求 5.2 指定 SQLite）。
 *
 * 使用 xerial `sqlite-jdbc`：它自带 Windows / Linux 的 x86_64 与 ARM64 原生库，
 * 满足需求 2.2 中麒麟系统双架构的目标运行环境。
 *
 * 线程模型：持有单个 [Connection]，所有访问以本对象为锁串行化。
 * 日历类应用的数据量与并发量都很小，串行化足以满足需求 4.2 中
 * 「操作响应不超过 100ms」的指标，同时避免引入连接池这类额外复杂度。
 *
 * ## 关于排序
 * 列表按「优先级降序 → 创建时间升序」排列，让高优先级待办置顶，
 * 同优先级内保持录入顺序。
 *
 * ## 关于 schema 演进
 * 建表用 `CREATE TABLE IF NOT EXISTS`，新增列用 [ensureColumn] 做幂等迁移，
 * 因此老版本创建的数据库文件升级后仍可直接打开，不会丢数据。
 */
class SqliteTodoRepository(private val dbPath: Path) : TodoRepository {

    private val connection: Connection
    private val lock = Any()

    init {
        dbPath.parent?.let { Files.createDirectories(it) }
        connection = DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")
        connection.autoCommit = true
        initSchema()
    }

    private fun initSchema() {
        connection.createStatement().use { st ->
            // 日期以 ISO-8601 文本存储（yyyy-MM-dd），字典序即时间序，
            // 因此 BETWEEN 范围查询与 ORDER BY 都能直接用索引。
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS todo (
                    id           TEXT    PRIMARY KEY,
                    text         TEXT    NOT NULL,
                    is_completed INTEGER NOT NULL DEFAULT 0,
                    created_at   INTEGER NOT NULL,
                    date         TEXT    NOT NULL,
                    priority     INTEGER NOT NULL DEFAULT 0,
                    tags         TEXT    NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_todo_date ON todo(date)")

            // 老库迁移：v1 的表没有 priority / tags 两列，这里补上。
            // 幂等，重复启动不会出错。
            ensureColumn(st, "priority", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(st, "tags", "TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * 若表中缺少某列则补齐。
     *
     * SQLite 不支持 `ADD COLUMN IF NOT EXISTS`，所以先查 `PRAGMA table_info`
     * 再决定是否 ALTER。
     */
    private fun ensureColumn(st: java.sql.Statement, column: String, definition: String) {
        val existing = mutableSetOf<String>()
        st.executeQuery("PRAGMA table_info(todo)").use { rs ->
            while (rs.next()) existing.add(rs.getString("name").lowercase())
        }
        if (column.lowercase() !in existing) {
            LOG.info("数据库迁移：为 todo 表补充列 $column")
            st.executeUpdate("ALTER TABLE todo ADD COLUMN $column $definition")
        }
    }

    private companion object {
        private val LOG: Logger = Logger.getLogger(SqliteTodoRepository::class.java.name)

        /** 统一的排序列：优先级降序，再按创建时间升序。 */
        private const val ORDER_BY = "ORDER BY priority DESC, created_at ASC"

        private const val COLUMNS = "id, text, is_completed, created_at, date, priority, tags"
    }

    override fun findByDate(date: LocalDate): List<TodoItem> = synchronized(lock) {
        connection.prepareStatement(
            "SELECT $COLUMNS FROM todo WHERE date = ? $ORDER_BY"
        ).use { ps ->
            ps.setString(1, date.toString())
            ps.executeQuery().use { rs -> rs.readAll() }
        }
    }

    override fun findByDateRange(start: LocalDate, end: LocalDate): List<TodoItem> =
        synchronized(lock) {
            connection.prepareStatement(
                "SELECT $COLUMNS FROM todo WHERE date BETWEEN ? AND ? " +
                    "ORDER BY date ASC, priority DESC, created_at ASC"
            ).use { ps ->
                ps.setString(1, start.toString())
                ps.setString(2, end.toString())
                ps.executeQuery().use { rs -> rs.readAll() }
            }
        }

    override fun findAll(): List<TodoItem> = synchronized(lock) {
        connection.prepareStatement(
            "SELECT $COLUMNS FROM todo ORDER BY date ASC, priority DESC, created_at ASC"
        ).use { ps ->
            ps.executeQuery().use { rs -> rs.readAll() }
        }
    }

    override fun countByDateRange(start: LocalDate, end: LocalDate): Map<LocalDate, Int> =
        synchronized(lock) {
            connection.prepareStatement(
                """
                SELECT date, COUNT(*) AS c FROM todo
                WHERE date BETWEEN ? AND ? GROUP BY date
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, start.toString())
                ps.setString(2, end.toString())
                ps.executeQuery().use { rs ->
                    buildMap {
                        while (rs.next()) {
                            put(LocalDate.parse(rs.getString("date")), rs.getInt("c"))
                        }
                    }
                }
            }
        }

    override fun statsByDateRange(
        start: LocalDate,
        end: LocalDate,
    ): Map<LocalDate, DayTodoStats> = synchronized(lock) {
        // 一次聚合查询同时拿到数量与最高优先级。
        // 只统计未完成的待办：勾掉之后格子上的高优先级标记必须随之消失，
        // 否则用户会误以为还有未完成的重要事项。
        connection.prepareStatement(
            """
            SELECT date,
                   COUNT(*) AS c,
                   MAX(priority) AS p
            FROM todo
            WHERE date BETWEEN ? AND ? AND is_completed = 0
            GROUP BY date
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, start.toString())
            ps.setString(2, end.toString())
            ps.executeQuery().use { rs ->
                buildMap {
                    while (rs.next()) {
                        put(
                            LocalDate.parse(rs.getString("date")),
                            DayTodoStats(
                                count = rs.getInt("c"),
                                maxPriorityLevel = rs.getInt("p"),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun insert(item: TodoItem): Unit = synchronized(lock) {
        connection.prepareStatement(
            """
            INSERT INTO todo (id, text, is_completed, created_at, date, priority, tags)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, item.id)
            ps.setString(2, item.text)
            ps.setInt(3, if (item.isCompleted) 1 else 0)
            ps.setLong(4, item.createdAt)
            ps.setString(5, item.date.toString())
            ps.setInt(6, item.priority.level)
            ps.setString(7, TodoItem.tagsToStorage(item.tags))
            ps.executeUpdate()
        }
    }

    override fun setCompleted(id: String, completed: Boolean): Unit = synchronized(lock) {
        connection.prepareStatement("UPDATE todo SET is_completed = ? WHERE id = ?").use { ps ->
            ps.setInt(1, if (completed) 1 else 0)
            ps.setString(2, id)
            ps.executeUpdate()
        }
    }

    override fun delete(id: String): Unit = synchronized(lock) {
        connection.prepareStatement("DELETE FROM todo WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate()
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { connection.close() }
                .onFailure { LOG.warning("关闭数据库连接失败: ${it.message}") }
        }
    }

    private fun ResultSet.readAll(): List<TodoItem> = buildList {
        while (next()) add(toTodoItem())
    }

    private fun ResultSet.toTodoItem() = TodoItem(
        id = getString("id"),
        text = getString("text"),
        isCompleted = getInt("is_completed") != 0,
        createdAt = getLong("created_at"),
        date = LocalDate.parse(getString("date")),
        priority = TodoPriority.fromLevel(getInt("priority")),
        tags = TodoItem.tagsFromStorage(getString("tags")),
    )
}
