package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.domain.TodoPriority
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 导出功能测试。
 *
 * 重点在 **CSV 转义**与 **JSON 转义**：待办内容来自用户自由输入，
 * 可能包含逗号、双引号、换行等字符。一旦转义出错，
 * CSV 会让整张表错位，JSON 会直接无法解析 —— 而且这类错误在
 * 内容"看起来正常"的待办上完全不会暴露。
 */
class TodoExporterTest {

    private fun todo(
        text: String,
        date: String = "2026-10-02",
        completed: Boolean = false,
        priority: TodoPriority = TodoPriority.NONE,
        tags: Set<String> = emptySet(),
    ) = TodoItem(
        id = "id-$text".take(20),
        text = text,
        isCompleted = completed,
        createdAt = 1000L,
        date = LocalDate.parse(date),
        priority = priority,
        tags = tags,
    )

    // ------------------------------------------------------------- CSV

    @Test
    fun `CSV 含表头与正确行数`() {
        val csv = TodoExporter.toCsv(listOf(todo("甲"), todo("乙")))
        val lines = csv.trimEnd('\n').split("\n")
        assertEquals("日期,完成,优先级,内容,标签", lines[0])
        assertEquals(3, lines.size, "1 行表头 + 2 行数据")
    }

    @Test
    fun `CSV 内容含逗号时必须整体加引号`() {
        val csv = TodoExporter.toCsv(listOf(todo("买牛奶,鸡蛋")))
        assertTrue(csv.contains("\"买牛奶,鸡蛋\""), "含逗号的字段应被引号包裹，实际：$csv")
    }

    @Test
    fun `CSV 内容含双引号时按 RFC4180 转义为两个引号`() {
        val csv = TodoExporter.toCsv(listOf(todo("""说"你好"""")))
        // 原值 说"你好"（内部两个引号）；转义后字段整体加引号、内部引号翻倍
        assertTrue(
            csv.contains("\"说\"\"你好\"\"\""),
            "双引号应翻倍且字段整体加引号，实际：$csv",
        )
        // 再用引号感知的解析器确认能还原出原值
        val rows = parseCsv(csv)
        assertEquals("""说"你好"""", rows[1][3], "解析回来应与原值一致")
    }

    @Test
    fun `CSV 内容含换行时不会破坏行结构`() {
        val items = listOf(todo("第一行\n第二行"), todo("正常项"))
        val csv = TodoExporter.toCsv(items)

        // 用与实现相同的引号感知方式计数，确认数据行数仍是 2
        assertEquals(
            items.size, TodoExporter.csvDataRowCount(csv),
            "含换行的字段必须被引号包裹，否则会被误判为多行",
        )
    }

    @Test
    fun `CSV 优先级与完成状态使用中文标签`() {
        val csv = TodoExporter.toCsv(
            listOf(todo("事", completed = true, priority = TodoPriority.HIGH)),
        )
        assertTrue(csv.contains("是"), "完成状态应为「是」")
        assertTrue(csv.contains(TodoPriority.HIGH.label), "应含优先级中文标签")
    }

    @Test
    fun `CSV 标签以竖线连接`() {
        val csv = TodoExporter.toCsv(listOf(todo("事", tags = setOf("工作", "紧急"))))
        assertTrue(csv.contains("工作|紧急"), "标签应以 | 连接，实际：$csv")
    }

    @Test
    fun `CSV 空列表只有表头`() {
        val csv = TodoExporter.toCsv(emptyList())
        assertEquals(1, csv.trimEnd('\n').split("\n").size)
        assertEquals(0, TodoExporter.csvDataRowCount(csv))
    }

    @Test
    fun `CSV 每一行字段数一致`() {
        // 字段数不一致是 CSV 最常见的隐性错误
        val items = listOf(
            todo("普通"),
            todo("含,逗号"),
            todo("含\"引号\""),
            todo("含\n换行"),
            todo("全都有,\"和\n换行"),
        )
        val csv = TodoExporter.toCsv(items)
        val rows = parseCsv(csv)
        assertEquals(items.size + 1, rows.size, "表头 + ${items.size} 行")
        rows.forEachIndexed { i, row ->
            assertEquals(5, row.size, "第 $i 行应有 5 个字段，实际 ${row.size}：$row")
        }
    }

    // ------------------------------------------------------------ JSON

    @Test
    fun `JSON 结构包含元信息与条目数组`() {
        val json = TodoExporter.toJson(listOf(todo("甲")))
        assertTrue(json.contains("\"application\""))
        assertTrue(json.contains("\"exportedAt\""))
        assertTrue(json.contains("\"itemCount\": 1"))
        assertTrue(json.contains("\"todos\""))
    }

    @Test
    fun `JSON 转义双引号与反斜杠`() {
        val json = TodoExporter.toJson(listOf(todo("""路径 C:\temp "引号"""")))
        assertTrue(json.contains("""\\temp"""), "反斜杠应转义，实际：$json")
        assertTrue(json.contains("""\"引号\""""), "双引号应转义，实际：$json")
    }

    @Test
    fun `JSON 转义换行与制表符`() {
        val json = TodoExporter.toJson(listOf(todo("上\n下\t制表")))
        assertTrue(json.contains("""上\n下\t制表"""), "换行与制表符应转义为 \\n \\t")
        // 转义后字符串字面量内不应出现真实换行
        val textLine = json.lines().first { it.contains("上") }
        assertTrue(!textLine.contains('\t'), "转义后不应残留真实制表符")
    }

    @Test
    fun `JSON 转义各种控制字符`() {
        val json = TodoExporter.toJson(listOf(todo("控制\u0001字符")))
        assertTrue(json.contains("""\u0001"""), "控制字符应转义为 \\u 形式，实际：$json")
    }

    @Test
    fun `JSON 保留中文原样不转义`() {
        val json = TodoExporter.toJson(listOf(todo("买牛奶")))
        assertTrue(json.contains("买牛奶"), "中文应原样输出而非 \\u 转义，便于人工查看")
    }

    @Test
    fun `JSON 含标签数组与优先级`() {
        val json = TodoExporter.toJson(
            listOf(todo("事", priority = TodoPriority.MEDIUM, tags = setOf("A", "B"))),
        )
        assertTrue(json.contains("\"tags\": [\"A\", \"B\"]"), "标签应为数组，实际：$json")
        assertTrue(json.contains("\"priority\": \"MEDIUM\""))
        assertTrue(json.contains("\"priorityLabel\""))
    }

    @Test
    fun `JSON 空列表也是合法结构`() {
        val json = TodoExporter.toJson(emptyList())
        assertTrue(json.contains("\"itemCount\": 0"))
        assertTrue(json.contains("\"todos\": ["))
    }

    @Test
    fun `JSON 条数正确且末尾逗号不残留`() {
        val json = TodoExporter.toJson(listOf(todo("A"), todo("B"), todo("C")))
        // 每个对象以 } 结尾，最后一项后面不能有逗号（严格解析器会报错）
        val objectEnds = Regex("\\}\\s*,?").findAll(json).count()
        assertTrue(objectEnds >= 3)
        assertTrue(!json.contains("},\n  ]"), "最后一项后不应有逗号")
    }

    // ---------------------------------------------------------- 文件导出

    @Test
    fun `导出会生成两个文件且内容可读`() {
        val dir = java.nio.file.Files.createTempDirectory("kylintodo-export-test")
        try {
            val items = listOf(todo("甲"), todo("乙", completed = true))
            val result = TodoExporter.export(items, dir)

            assertEquals(2, result.itemCount)
            assertTrue(java.nio.file.Files.exists(result.jsonFile))
            assertTrue(java.nio.file.Files.exists(result.csvFile))

            val csvText = java.nio.file.Files.readString(result.csvFile)
            assertTrue(csvText.contains("甲"))
            val jsonText = java.nio.file.Files.readString(result.jsonFile)
            assertTrue(jsonText.contains("\"itemCount\": 2"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `导出文件名带时间戳避免覆盖`() {
        val dir = java.nio.file.Files.createTempDirectory("kylintodo-export-test")
        try {
            val a = TodoExporter.export(listOf(todo("甲")), dir)
            Thread.sleep(1100) // 时间戳精度到秒
            val b = TodoExporter.export(listOf(todo("乙")), dir)
            assertTrue(
                a.jsonFile != b.jsonFile,
                "两次导出不应写同一文件，否则会静默覆盖用户数据",
            )
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `describe 输出包含条数与两个路径`() {
        val dir = java.nio.file.Files.createTempDirectory("kylintodo-export-test")
        try {
            val result = TodoExporter.export(listOf(todo("甲")), dir)
            val desc = TodoExporter.describe(result)
            assertTrue(desc.contains("1 条"))
            assertTrue(desc.contains("JSON"))
            assertTrue(desc.contains("CSV"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    /** 简易 CSV 解析器（引号感知），仅用于测试断言字段数。 */
    private fun parseCsv(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < csv.length) {
            val ch = csv[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < csv.length && csv[i + 1] == '"') {
                        field.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    row.add(field.toString()); field = StringBuilder()
                }
                ch == '\n' && !inQuotes -> {
                    row.add(field.toString()); field = StringBuilder()
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString()); rows.add(row)
        }
        return rows
    }
}
