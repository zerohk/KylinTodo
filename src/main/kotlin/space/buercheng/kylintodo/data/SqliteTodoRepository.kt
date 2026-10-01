package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoRepository
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
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
                    date         TEXT    NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_todo_date ON todo(date)")
        }
    }

    override fun findByDate(date: LocalDate): List<TodoItem> = synchronized(lock) {
        connection.prepareStatement(
            """
            SELECT id, text, is_completed, created_at, date
            FROM todo WHERE date = ? ORDER BY created_at ASC
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, date.toString())
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(rs.toTodoItem())
                }
            }
        }
    }

    override fun findByDateRange(start: LocalDate, end: LocalDate): List<TodoItem> =
        synchronized(lock) {
            connection.prepareStatement(
                """
                SELECT id, text, is_completed, created_at, date
                FROM todo WHERE date BETWEEN ? AND ?
                ORDER BY date ASC, created_at ASC
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, start.toString())
                ps.setString(2, end.toString())
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(rs.toTodoItem())
                    }
                }
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

    override fun insert(item: TodoItem): Unit = synchronized(lock) {
        connection.prepareStatement(
            """
            INSERT INTO todo (id, text, is_completed, created_at, date)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, item.id)
            ps.setString(2, item.text)
            ps.setInt(3, if (item.isCompleted) 1 else 0)
            ps.setLong(4, item.createdAt)
            ps.setString(5, item.date.toString())
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

    private fun java.sql.ResultSet.toTodoItem() = TodoItem(
        id = getString("id"),
        text = getString("text"),
        isCompleted = getInt("is_completed") != 0,
        createdAt = getLong("created_at"),
        date = LocalDate.parse(getString("date")),
    )

    private companion object {
        private val LOG: Logger = Logger.getLogger(SqliteTodoRepository::class.java.name)
    }
}
