package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.AppInfo
import space.buercheng.kylintodo.domain.TodoItem
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 待办数据导出（需求反馈第 5 条的「导出数据」）。
 *
 * 支持两种格式，用途不同：
 *  - **JSON**：字段完整，适合备份；结构稳定，便于将来做导入恢复
 *  - **CSV**：适合在 Excel / WPS 中查看与统计，因此把中文列名放在首行
 *
 * 刻意手写序列化而不引入 JSON 库：字段数量少、结构固定，
 * 少一个依赖就少一处跨平台风险（本项目面向麒麟，依赖越少越好）。
 * 但 CSV 的转义规则必须严格遵守，否则含逗号或换行的待办会破坏表格结构。
 */
object TodoExporter {

    private val TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** 导出结果，供界面提示用户文件位置。 */
    data class Result(
        val jsonFile: Path,
        val csvFile: Path,
        val itemCount: Int,
    )

    /**
     * 导出到 [targetDir]（默认应用数据目录下的 export 子目录）。
     *
     * 文件名带时间戳，避免多次导出相互覆盖 —— 用户导出后若想再导一次
     * 对比，不会被静默替换。
     */
    fun export(items: List<TodoItem>, targetDir: Path): Result {
        Files.createDirectories(targetDir)
        val stamp = ZonedDateTime.now().format(TIMESTAMP)

        val jsonFile = targetDir.resolve("todos-$stamp.json")
        val csvFile = targetDir.resolve("todos-$stamp.csv")

        Files.writeString(jsonFile, toJson(items))
        Files.writeString(csvFile, toCsv(items))

        return Result(jsonFile, csvFile, items.size)
    }

    // ---------------------------------------------------------------- JSON

    /** 生成带元信息的 JSON。手写而非反射，输出结构完全可控。 */
    fun toJson(items: List<TodoItem>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"application\": ").append(jsonString(AppInfo.DEFAULT_DISPLAY_NAME)).append(",\n")
        sb.append("  \"exportedAt\": \"").append(ZonedDateTime.now()).append("\",\n")
        sb.append("  \"itemCount\": ").append(items.size).append(",\n")
        sb.append("  \"todos\": [\n")
        items.forEachIndexed { index, item ->
            sb.append("    {\n")
            sb.append("      \"id\": ").append(jsonString(item.id)).append(",\n")
            sb.append("      \"text\": ").append(jsonString(item.text)).append(",\n")
            sb.append("      \"date\": ").append(jsonString(item.date.toString())).append(",\n")
            sb.append("      \"isCompleted\": ").append(item.isCompleted).append(",\n")
            sb.append("      \"createdAt\": ").append(item.createdAt).append(",\n")
            sb.append("      \"priority\": ").append(jsonString(item.priority.name)).append(",\n")
            sb.append("      \"priorityLabel\": ").append(jsonString(item.priority.label)).append(",\n")
            sb.append("      \"tags\": [")
            sb.append(item.tags.joinToString(", ") { jsonString(it) })
            sb.append("]\n")
            sb.append("    }")
            if (index != items.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    /**
     * JSON 字符串字面量转义。
     *
     * 除常规字符外，还必须处理 U+2028 / U+2029（部分解析器视作换行）
     * 与控制字符 —— 待办内容来自用户输入，可能包含任意字符。
     */
    private fun jsonString(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\u2028' -> sb.append("\\u2028")
                '\u2029' -> sb.append("\\u2029")
                else ->
                    if (ch < ' ') sb.append("\\u%04x".format(ch.code))
                    else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // ----------------------------------------------------------------- CSV

    /**
     * 生成 CSV（含中文表头）。
     *
     * 字段顺序固定，便于用户在表格软件里做数据透视。
     * 标签以 `|` 连接 —— 标签本身禁止逗号（见 TodoItem.normalizeTags），
     * 因此在 CSV 中不会与分隔符冲突。
     */
    fun toCsv(items: List<TodoItem>): String {
        val sb = StringBuilder()
        sb.append("日期,完成,优先级,内容,标签\n")
        items.forEach { item ->
            sb.append(csvField(item.date.toString())).append(',')
            sb.append(if (item.isCompleted) "是" else "否").append(',')
            sb.append(csvField(item.priority.label)).append(',')
            sb.append(csvField(item.text)).append(',')
            sb.append(csvField(item.tags.joinToString("|")))
            sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * CSV 字段转义（RFC 4180）。
     *
     * 含逗号、双引号或换行的字段必须整体用双引号包裹，
     * 且字段内的双引号要写成两个双引号。这是最容易被忽略、
     * 一旦出错就会让整张表错位的规则。
     */
    private fun csvField(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return value
        return '"' + value.replace("\"", "\"\"") + '"'
    }

    /** 建议的导出目录：应用数据目录下的 export 子目录。 */
    fun defaultExportDir(): Path = AppPaths.dataDirectory().resolve("export")

    /** 供界面显示：把导出结果整理为一行可读文本。 */
    fun describe(result: Result): String =
        "已导出 ${result.itemCount} 条待办\nJSON：${result.jsonFile}\nCSV：${result.csvFile}"

    /**
     * 校验导出内容的内部一致性（供测试使用）。
     *
     * 主要检查 CSV 的行数是否与条目数匹配 —— 若某条待办含换行而未被转义，
     * 行数就会多出来。
     */
    fun csvDataRowCount(csv: String): Int {
        var count = 0
        var inQuotes = false
        csv.forEach { ch ->
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == '\n' && !inQuotes -> count++
            }
        }
        // 减去表头行
        return (count - 1).coerceAtLeast(0)
    }

    /** 解析日期用的格式，导出与将来的导入共用。 */
    val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** 供导入时解析日期。 */
    fun parseDate(text: String): LocalDate? =
        runCatching { LocalDate.parse(text, DATE_FORMAT) }.getOrNull()
}
