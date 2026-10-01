package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.domain.TodoItem
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SQLite 仓库行为验证。
 *
 * 需求 F-06 要求「软件关闭后数据自动保存，再次打开自动加载历史数据」，
 * 因此专门覆盖「关闭后重开仍能读到」这一场景。
 */
class SqliteTodoRepositoryTest {

    private lateinit var tempDir: Path
    private lateinit var dbFile: Path
    private lateinit var repo: SqliteTodoRepository

    private val day = LocalDate.of(2026, 10, 1)

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("kylintodo-test")
        dbFile = tempDir.resolve("test.db")
        repo = SqliteTodoRepository(dbFile)
    }

    @AfterTest
    fun tearDown() {
        runCatching { repo.close() }
        runCatching { tempDir.toFile().deleteRecursively() }
    }

    @Test
    fun `数据库文件会被实际创建`() {
        assertTrue(Files.exists(dbFile), "构造仓库后应生成数据库文件")
    }

    @Test
    fun `新增后可查询到`() {
        val item = TodoItem.createOrNull("写需求文档", day)!!
        repo.insert(item)

        val loaded = repo.findByDate(day)
        assertEquals(1, loaded.size)
        assertEquals("写需求文档", loaded.first().text)
        assertEquals(item.id, loaded.first().id)
        assertEquals(day, loaded.first().date)
    }

    @Test
    fun `按日期隔离_不串到其他日期`() {
        val other = day.plusDays(1)
        repo.insert(TodoItem.createOrNull("今天的", day)!!)
        repo.insert(TodoItem.createOrNull("明天的", other)!!)

        assertEquals(listOf("今天的"), repo.findByDate(day).map { it.text })
        assertEquals(listOf("明天的"), repo.findByDate(other).map { it.text })
        assertEquals(0, repo.findByDate(day.plusDays(5)).size)
    }

    @Test
    fun `按创建时间升序返回`() {
        // 故意乱序插入，createdAt 决定顺序
        repo.insert(TodoItem(text = "第三", createdAt = 300, date = day))
        repo.insert(TodoItem(text = "第一", createdAt = 100, date = day))
        repo.insert(TodoItem(text = "第二", createdAt = 200, date = day))

        assertEquals(listOf("第一", "第二", "第三"), repo.findByDate(day).map { it.text })
    }

    @Test
    fun `切换完成状态`() {
        val item = TodoItem.createOrNull("待勾选", day)!!
        repo.insert(item)
        assertTrue(!repo.findByDate(day).first().isCompleted, "新建应为未完成")

        repo.setCompleted(item.id, true)
        assertTrue(repo.findByDate(day).first().isCompleted, "应变为已完成")

        repo.setCompleted(item.id, false)
        assertTrue(!repo.findByDate(day).first().isCompleted, "应能改回未完成")
    }

    @Test
    fun `删除待办`() {
        val item = TodoItem.createOrNull("待删除", day)!!
        repo.insert(item)
        assertEquals(1, repo.findByDate(day).size)

        repo.delete(item.id)
        assertEquals(0, repo.findByDate(day).size)
    }

    @Test
    fun `区间计数用于月视图角标`() {
        // 月视图需要一次性取得 42 天的待办数量，避免逐日查询（需求 4.2 响应指标）
        repo.insert(TodoItem(text = "a", date = day))
        repo.insert(TodoItem(text = "b", date = day))
        repo.insert(TodoItem(text = "c", date = day.plusDays(3)))

        val counts = repo.countByDateRange(day.minusDays(1), day.plusDays(40))
        assertEquals(2, counts[day])
        assertEquals(1, counts[day.plusDays(3)])
        assertEquals(null, counts[day.plusDays(1)], "无待办的日期不应出现在结果里")
    }

    @Test
    fun `区间计数包含边界日期`() {
        repo.insert(TodoItem(text = "start", date = day))
        repo.insert(TodoItem(text = "end", date = day.plusDays(10)))

        val counts = repo.countByDateRange(day, day.plusDays(10))
        assertEquals(1, counts[day], "起始边界应包含")
        assertEquals(1, counts[day.plusDays(10)], "结束边界应包含")
    }

    @Test
    fun `关闭后重开仍能读到数据`() {
        // 需求 F-06：软件关闭后数据自动保存，再次打开自动加载
        repo.insert(TodoItem.createOrNull("持久化的待办", day)!!)
        repo.close()

        val reopened = SqliteTodoRepository(dbFile)
        try {
            val loaded = reopened.findByDate(day)
            assertEquals(1, loaded.size)
            assertEquals("持久化的待办", loaded.first().text)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `重开时不会重复建表或丢字段`() {
        val item = TodoItem.createOrNull("勾选后重开", day)!!
        repo.insert(item)
        repo.setCompleted(item.id, true)
        repo.close()

        val reopened = SqliteTodoRepository(dbFile)
        try {
            val loaded = reopened.findByDate(day).first()
            assertEquals("勾选后重开", loaded.text)
            assertTrue(loaded.isCompleted, "完成状态也应持久化")
            assertEquals(item.createdAt, loaded.createdAt)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `相同ID重复插入会失败而不是产生重复行`() {
        val item = TodoItem.createOrNull("唯一", day)!!
        repo.insert(item)
        val threw = runCatching { repo.insert(item) }.isFailure
        assertTrue(threw, "主键冲突应抛异常")
        assertEquals(1, repo.findByDate(day).size, "不应产生重复行")
    }

    @Test
    fun `含特殊字符与中文的文本能正确往返`() {
        val tricky = "张三's 任务: \"写\" 100% & <标签>  换行\n第二行 emoji 🎉"
        val item = TodoItem.createOrNull(tricky, day)!!
        repo.insert(item)
        assertEquals(tricky, repo.findByDate(day).first().text)
    }
}

/**
 * 应用数据目录解析验证。
 *
 * 关键是不能在 Windows 与 Linux 上写出同一个路径 —— 麒麟是 Linux 环境。
 */
class AppPathsTest {

    @Test
    fun `数据目录位于用户主目录之下`() {
        val dir = AppPaths.dataDirectory()
        val home = System.getProperty("user.home")
        assertTrue(
            dir.toAbsolutePath().startsWith(home),
            "数据目录应位于用户主目录下，实际: $dir",
        )
    }

    @Test
    fun `数据库文件名符合预期`() {
        assertTrue(AppPaths.databaseFile().fileName.toString().endsWith(".db"))
    }

    @Test
    fun `Linux环境遵循XDG规范`() {
        // 直接验证分支逻辑：非 Windows 时应落在 .local/share 或 $XDG_DATA_HOME 之下
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) {
            val dir = AppPaths.dataDirectory().toString()
            val xdg = System.getenv("XDG_DATA_HOME")
            val expected = if (!xdg.isNullOrBlank()) xdg else ".local"
            assertTrue(dir.contains(expected), "Linux 下应遵循 XDG，实际: $dir")
        }
    }
}
