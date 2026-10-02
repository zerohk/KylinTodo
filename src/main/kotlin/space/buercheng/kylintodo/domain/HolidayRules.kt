package space.buercheng.kylintodo.domain

import java.time.LocalDate

/**
 * 一条用户导入的节假日规则。
 *
 * @param date 具体日期
 * @param isWork true = 调休上班（显示「班」）；false = 放假（显示「休」）
 * @param name 假期名称，如「春节」；可为空（显示时不带名称）
 */
data class HolidayRule(
    val date: LocalDate,
    val isWork: Boolean,
    val name: String,
)

/** 导入结果：成功导入的规则 + 逐行错误说明。 */
data class HolidayImportResult(
    val rules: List<HolidayRule>,
    /** 形如「第 5 行：日期格式无法识别（2027-13-45）」 */
    val errors: List<String>,
) {
    val successCount: Int get() = rules.size
    val hasErrors: Boolean get() = errors.isNotEmpty()
}

/**
 * 节假日数据表（用户导入的部分）。
 *
 * 存起来后在查表时**优先于库内置数据** —— 用户既然明确导入了，
 * 就应以导入内容为准。这样即使库的节假日数据过期（内置数据只到
 * 2026-10-10），用户也能自行补齐后续年份。
 */
class HolidayTable(entries: Collection<HolidayRule> = emptyList()) {

    private val byDate: Map<LocalDate, HolidayRule> =
        entries.associateBy { it.date }

    val size: Int get() = byDate.size

    /** 查某一天的用户导入规则；无则返回 null（此时回退到库内置数据）。 */
    fun find(date: LocalDate): HolidayRule? = byDate[date]

    /** 当前覆盖的日期区间，供界面提示用户。 */
    fun coveredRange(): ClosedRange<LocalDate>? {
        if (byDate.isEmpty()) return null
        val dates = byDate.keys
        return dates.min()..dates.max()
    }
}

/**
 * 解析用户导入的节假日表格。
 *
 * ## 为什么单独成类而不是塞进 UI
 * 用户导入的文件不可控：日期写法五花八门、可能有空行、可能有文字说明。
 * 解析必须**宽容地接受多种格式，同时严格地报出问题行** ——
 * 静默跳过会让用户以为导入成功但日历上没变化，比报错更糟。
 *
 * 因此这里返回 [HolidayImportResult]，把"成功几条"与"第几行有什么问题"
 * 一并给出，由界面完整展示给用户。
 *
 * ## 支持的日期写法
 * - `2027-02-06`（推荐，模板使用此格式）
 * - `2027/2/6`、`2027.2.6`
 * - `2027年2月6日`
 * - Excel 日期序列号（如 `46167`）—— 用户把列格式设成"日期"时可能存成数值
 *
 * ## 支持的"休/班"写法
 * `休` / `假` / `放假` / `H` / `holiday` → 放假
 * `班` / `上班` / `调休` / `W` / `work` → 调休上班
 */
object HolidayImporter {

    /** 表头常见写法 → 标准字段。用包含匹配以容忍「日期(必填)」这类写法。 */
    private val DATE_HEADERS = listOf("日期", "date")
    private val TYPE_HEADERS = listOf("类型", "休班", "放假", "type")
    private val NAME_HEADERS = listOf("名称", "名称", "假期", "name", "备注")

    private val HOLIDAY_WORDS = listOf("休", "假", "放假", "holiday", "h", "off")
    private val WORK_WORDS = listOf("班", "上班", "调休", "work", "w", "on")

    /**
     * 从「行号 → 单元格」的原始表数据解析。
     *
     * @param rows 来自 `XlsxReader.readFirstSheet`
     */
    fun parse(rows: Map<Int, Map<String, String>>): HolidayImportResult {
        if (rows.isEmpty()) {
            return HolidayImportResult(emptyList(), listOf("文件里没有读到任何数据行"))
        }

        val header = detectHeader(rows)
            ?: return HolidayImportResult(
                emptyList(),
                listOf(
                    "找不到表头行。请确保第一行包含「日期」「类型」两列" +
                        "（可参考模板文件）",
                ),
            )

        val rules = mutableListOf<HolidayRule>()
        val errors = mutableListOf<String>()
        val seen = mutableSetOf<LocalDate>()

        rows.filterKeys { it > header.rowNumber }.toSortedMap().forEach { (rowNo, cells) ->
            // 整行为空则跳过 —— 模板里常有大量空白行，报错会很吵
            val rawDate = cells[header.dateCol].orEmpty().trim()
            val rawType = cells[header.typeCol].orEmpty().trim()
            val rawName = header.nameCol?.let { cells[it].orEmpty().trim() }.orEmpty()

            if (rawDate.isEmpty() && rawType.isEmpty() && rawName.isEmpty()) return@forEach

            val parsedDate = if (rawDate.isEmpty()) null else parseDate(rawDate)

            // 跳过说明行：日期列不是日期、且类型列为空 → 不是数据行。
            //
            // 模板自带填写说明（如「1. 日期支持 2027-02-06 / …」），
            // 用户也常在表格里留注释。把它当数据行处理会报"日期无法识别"，
            // 让用户误以为导入失败。这类行**静默忽略**。
            //
            // 注意：只有在类型列也空时才忽略。若用户填了「休/班」却没写对日期，
            // 那是真的填错了，必须报出来。
            if (parsedDate == null && rawType.isEmpty()) return@forEach

            if (rawDate.isEmpty()) {
                errors += "第 $rowNo 行：填了类型「$rawType」但日期为空"
                return@forEach
            }

            if (parsedDate == null) {
                errors += "第 $rowNo 行：日期无法识别（$rawDate）"
                return@forEach
            }

            if (rawType.isEmpty()) {
                errors += "第 $rowNo 行：日期 $parsedDate 未填类型，请填「休」或「班」"
                return@forEach
            }

            val isWork = parseType(rawType)
            if (isWork == null) {
                errors += "第 $rowNo 行：类型无法识别（$rawType），请填「休」或「班」"
                return@forEach
            }

            // 同一天重复出现时保留最后一条，并提示 —— 用户改了主意时
            // 通常是在文件末尾追加修正行，取最后一条更符合直觉。
            if (!seen.add(parsedDate)) {
                errors += "第 $rowNo 行：日期 $parsedDate 重复，已以本行为准"
                rules.removeAll { it.date == parsedDate }
            }

            rules += HolidayRule(date = parsedDate, isWork = isWork, name = rawName)
        }

        if (rules.isEmpty() && errors.isEmpty()) {
            errors += "没有解析出任何有效数据行"
        }

        return HolidayImportResult(rules.sortedBy { it.date }, errors)
    }

    // -------------------------------------------------------------- 表头

    private data class Header(
        val rowNumber: Int,
        val dateCol: String,
        val typeCol: String,
        val nameCol: String?,
    )

    /**
     * 在前 10 行内寻找表头。
     *
     * 不假设一定在第一行：用户常在顶部留标题或说明文字。
     */
    private fun detectHeader(rows: Map<Int, Map<String, String>>): Header? {
        rows.entries.sortedBy { it.key }.take(10).forEach { (rowNo, cells) ->
            var dateCol: String? = null
            var typeCol: String? = null
            var nameCol: String? = null
            cells.forEach { (col, value) ->
                val v = value.trim().lowercase()
                if (v.isEmpty()) return@forEach
                if (dateCol == null && DATE_HEADERS.any { v.contains(it) }) dateCol = col
                else if (typeCol == null && TYPE_HEADERS.any { v.contains(it) }) typeCol = col
                else if (nameCol == null && NAME_HEADERS.any { v.contains(it) }) nameCol = col
            }
            if (dateCol != null && typeCol != null) {
                return Header(rowNo, dateCol, typeCol, nameCol)
            }
        }
        return null
    }

    // -------------------------------------------------------------- 解析

    /**
     * 判断是否"说明行"（非数据行）。
     *
     * 模板自带填写说明，用户也常在表格里留注释。这类行的"日期"列是文字，
     * 若按数据行处理会报"日期无法识别"，让用户误以为导入失败。
     *
     * ## 判定规则
     * 1. 以「填写说明 / 注 / 备注 / #」等前缀开头，且类型列为空
     * 2. **以中文或字母开头** —— 日期必然以数字开头（`2027-02-06`、
     *    `2027年2月6日`、Excel 序列号 `46167`），所以中文/字母开头
     *    一定不是日期。这条是主规则，能覆盖「1. 日期支持…」这类
     *    带序号的说明行（它们以数字开头，无法靠前缀识别）。
     */
    private fun isNoteRow(rawDate: String): Boolean {
        if (rawDate.isEmpty()) return false
        val notePrefixes = listOf(
            "填写说明", "说明", "注", "备注", "#", "填", "示例", "格式",
        )
        if (notePrefixes.any { rawDate.startsWith(it) }) return true
        // 日期不可能以中文或字母开头
        val first = rawDate.first()
        return first.isLetter()
    }

    private val ISO = Regex("""^(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?$""")

    /** Excel 日期序列号的下界：约 1970 年。小于它就不像日期了。 */
    private const val EXCEL_SERIAL_MIN = 25569

    /**
     * 解析日期。返回 null 表示无法识别（调用方负责报错）。
     */
    fun parseDate(raw: String): LocalDate? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        ISO.matchEntire(text)?.let { m ->
            val (y, mo, d) = m.destructured
            return runCatching {
                LocalDate.of(y.toInt(), mo.toInt(), d.toInt())
            }.getOrNull()
        }

        // Excel 日期序列号：以 1899-12-30 为原点（Excel 的闰年 bug 已被广泛沿用）
        text.toDoubleOrNull()?.let { serial ->
            val days = serial.toLong()
            if (days >= EXCEL_SERIAL_MIN) {
                return runCatching {
                    LocalDate.of(1899, 12, 30).plusDays(days)
                }.getOrNull()
            }
        }

        return null
    }

    /** 解析「休/班」。返回 null 表示无法识别。 */
    fun parseType(raw: String): Boolean? {
        val t = raw.trim().lowercase()
        if (t.isEmpty()) return null
        // 先判"班"：'调休' 同时含"休"字，若先判休会误判为放假
        if (WORK_WORDS.any { t.contains(it) }) return true
        if (HOLIDAY_WORDS.any { t.contains(it) }) return false
        return null
    }
}
