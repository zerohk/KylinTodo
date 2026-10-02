package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoPriority
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 优先级与标签的持久化验证，并覆盖**旧库迁移**。
 *
 * 迁移测试尤其重要：已安装 v1 的用户数据库没有 priority / tags 两列，
 * 升级后必须能直接打开且不丢数据。
 */
class TodoPriorityAndTagsTest {

    private lateinit var tempDir: Path
    private lateinit var dbFile: Path
    private val day = LocalDate.of(2026, 10, 1)

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("kylintodo-prio")
        dbFile = tempDir.resolve("t.db")
    }

    @AfterTest
    fun tearDown() {
        runCatching { tempDir.toFile().deleteRecursively() }
    }

    // ---------------- 新库：优先级与标签往返 ----------------

    @Test
    fun `优先级可以正确往返`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            TodoPriority.entries.forEachIndexed { i, p ->
                repo.insert(
                    TodoItem.createOrNull("任务$i", day, priority = p)!!
                )
            }
            val loaded = repo.findByDate(day).map { it.priority }.toSet()
            assertEquals(TodoPriority.entries.toSet(), loaded, "四级优先级都应能存回")
        } finally {
            repo.close()
        }
    }

    @Test
    fun `标签集合可以正确往返`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            val tags = setOf("工作", "紧急", "电话")
            repo.insert(TodoItem.createOrNull("带标签", day, tags = tags)!!)

            val loaded = repo.findByDate(day).first()
            assertEquals(tags, loaded.tags, "标签集合应无损往返")
        } finally {
            repo.close()
        }
    }

    @Test
    fun `无标签时往返为空集而不是含空串的集合`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            repo.insert(TodoItem.createOrNull("无标签", day)!!)
            assertTrue(repo.findByDate(day).first().tags.isEmpty())
        } finally {
            repo.close()
        }
    }

    @Test
    fun `列表按优先级降序排列`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            // 故意乱序插入，并用递增的 createdAt 排除"恰好按时间有序"的假象
            repo.insert(TodoItem(text = "低", createdAt = 100, date = day, priority = TodoPriority.LOW))
            repo.insert(TodoItem(text = "高", createdAt = 200, date = day, priority = TodoPriority.HIGH))
            repo.insert(TodoItem(text = "无", createdAt = 300, date = day, priority = TodoPriority.NONE))
            repo.insert(TodoItem(text = "中", createdAt = 400, date = day, priority = TodoPriority.MEDIUM))

            assertEquals(
                listOf("高", "中", "低", "无"),
                repo.findByDate(day).map { it.text },
                "应优先按优先级降序，其次才是创建时间",
            )
        } finally {
            repo.close()
        }
    }

    // ---------------- 旧库迁移 ----------------

    /** 造一个 v1 结构（无 priority / tags 列）并写入一条数据。 */
    private fun createLegacyDatabase() {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.toAbsolutePath()}").use { c ->
            c.createStatement().use { st ->
                st.executeUpdate(
                    """
                    CREATE TABLE todo (
                        id           TEXT    PRIMARY KEY,
                        text         TEXT    NOT NULL,
                        is_completed INTEGER NOT NULL DEFAULT 0,
                        created_at   INTEGER NOT NULL,
                        date         TEXT    NOT NULL
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    "INSERT INTO todo (id, text, is_completed, created_at, date) " +
                        "VALUES ('legacy-1', '旧版本待办', 0, 1000, '$day')"
                )
            }
        }
    }

    @Test
    fun `旧版本数据库升级后数据不丢且新列可用`() {
        createLegacyDatabase()

        // 用当前实现打开旧库 —— 应自动补列
        val repo = SqliteTodoRepository(dbFile)
        try {
            val loaded = repo.findByDate(day)
            assertEquals(1, loaded.size, "旧数据必须保留")
            assertEquals("旧版本待办", loaded.first().text)
            assertEquals(
                TodoPriority.NONE, loaded.first().priority,
                "旧记录缺少优先级时应回落到 NONE",
            )
            assertTrue(loaded.first().tags.isEmpty(), "旧记录的标签应为空")

            // 补列之后，新字段应可正常写入
            repo.insert(
                TodoItem.createOrNull(
                    "升级后新增", day,
                    priority = TodoPriority.HIGH,
                    tags = setOf("迁移"),
                )!!
            )
            val high = repo.findByDate(day).first { it.text == "升级后新增" }
            assertEquals(TodoPriority.HIGH, high.priority)
            assertEquals(setOf("迁移"), high.tags)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `重复打开同一数据库不会因迁移而报错`() {
        createLegacyDatabase()
        // 连续打开三次，ensureColumn 必须幂等
        repeat(3) {
            val repo = SqliteTodoRepository(dbFile)
            repo.close()
        }
        val repo = SqliteTodoRepository(dbFile)
        try {
            assertEquals(1, repo.findByDate(day).size)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `findAll 返回按日期升序的全部待办`() {
        val repo = SqliteTodoRepository(dbFile)
        try {
            repo.insert(TodoItem.createOrNull("第三天", day.plusDays(2))!!)
            repo.insert(TodoItem.createOrNull("第一天", day)!!)
            repo.insert(TodoItem.createOrNull("第二天", day.plusDays(1))!!)

            assertEquals(
                listOf("第一天", "第二天", "第三天"),
                repo.findAll().map { it.text },
            )
        } finally {
            repo.close()
        }
    }
}

/**
 * 标签与文本的规范化规则（不依赖数据库）。
 */
class TodoItemTagNormalizationTest {

    private val day = LocalDate.of(2026, 10, 1)

    @Test
    fun `中英文逗号与分号都能作为分隔符`() {
        val item = TodoItem.createOrNull("t", day, tags = listOf("工作,紧急；电话，邮件;其他"))
        assertEquals(
            setOf("工作", "紧急", "电话", "邮件", "其他"),
            item!!.tags,
        )
    }

    @Test
    fun `标签去重且去除空白`() {
        val item = TodoItem.createOrNull("t", day, tags = listOf(" 工作 ", "工作", "", "   "))
        assertEquals(setOf("工作"), item!!.tags)
    }

    @Test
    fun `超长标签会被截断`() {
        val long = "很".repeat(50)
        val item = TodoItem.createOrNull("t", day, tags = listOf(long))
        assertEquals(TodoItem.MAX_TAG_LENGTH, item!!.tags.first().length)
    }

    @Test
    fun `标签数量上限被遵守`() {
        val many = (1..20).map { "标签$it" }
        val item = TodoItem.createOrNull("t", day, tags = many)
        assertEquals(TodoItem.MAX_TAG_COUNT, item!!.tags.size)
    }

    @Test
    fun `标签序列化与反序列化互逆`() {
        val tags = setOf("工作", "紧急")
        val stored = TodoItem.tagsToStorage(tags)
        assertEquals(tags, TodoItem.tagsFromStorage(stored))
    }

    @Test
    fun `持久化字符串为空时还原为空集`() {
        assertTrue(TodoItem.tagsFromStorage("").isEmpty())
        assertTrue(TodoItem.tagsFromStorage("   ").isEmpty())
        assertTrue(TodoItem.tagsFromStorage(null).isEmpty())
    }

    @Test
    fun `未知优先级数值回落到无优先级`() {
        assertEquals(TodoPriority.NONE, TodoPriority.fromLevel(99))
        assertEquals(TodoPriority.NONE, TodoPriority.fromLevel(-1))
        assertEquals(TodoPriority.HIGH, TodoPriority.fromLevel(3))
    }

    @Test
    fun `优先级等级可用于比较`() {
        assertTrue(TodoPriority.HIGH.level > TodoPriority.MEDIUM.level)
        assertTrue(TodoPriority.MEDIUM.level > TodoPriority.LOW.level)
        assertTrue(TodoPriority.LOW.level > TodoPriority.NONE.level)
    }
}
