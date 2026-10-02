package space.buercheng.kylintodo.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * 极简 .xlsx 读取器（零第三方依赖）。
 *
 * ## 为什么不引入 Apache POI
 * POI 及其传递依赖（poi / poi-ooxml / xmlbeans / commons-compress 等）
 * 合计约 **8.8 MB**，而本项目的 `.deb` 已经 99.04 MB，
 * 距离 GitHub Release 的 100 MiB 单文件上限只剩不到 1 MB —— 引入即超限。
 *
 * ## 为什么手写可行
 * `.xlsx` 本质上是一个 **ZIP 包**，内部是若干 XML 部件：
 * ```
 * xl/sharedStrings.xml   共享字符串表（单元格 t="s" 时按索引引用）
 * xl/worksheets/sheet1.xml  实际单元格数据
 * ```
 * 解析本场景所需的子集（读出「单元格 → 文本」）用 JDK 自带的
 * [ZipInputStream] 加少量正则即可完成，无需通用电子表格引擎。
 *
 * ## 明确的适用范围（有意为之的限制）
 * 只支持导入节假日表这一个用途，因此**不实现**：
 *  - 公式求值（只取公式的缓存值 `<v>`）
 *  - 复杂数字格式 / 时区（日期只认常见格式与 Excel 序列号）
 *  - 多工作表合并（只读第一张表）
 *  - .xls 旧二进制格式（只支持 .xlsx）
 *
 * 这些限制会在导入失败时给出明确提示，而不是静默读错数据。
 */
/** 一行数据：列字母 → 单元格原始文本。 */
typealias XlsxRow = Map<String, String>

object XlsxReader {



    /**
     * 读取第一张工作表，返回「行号 → 该行单元格」。
     *
     * 行号从 1 开始，与 Excel 显示一致，便于报错时直接告诉用户"第几行有问题"。
     * 完全空的行不会出现在结果里 —— 模板里常有大量空白行，
     * 保留它们只会让调用方多做无意义的判空。
     */
    fun readFirstSheet(bytes: ByteArray): Map<Int, XlsxRow> {
        val parts = readZipParts(bytes)

        val sharedStrings = parts["xl/sharedStrings.xml"]
            ?.let { parseSharedStrings(decodeUtf8(it)) }
            ?: emptyList()

        // 工作表文件名不固定（sheet1.xml / sheet2.xml…），取第一张。
        // workbook.xml.rels 里才有顺序信息，但本场景只需第一张表，
        // 按名称排序后取第一个已足够稳定。
        val sheetKey = parts.keys
            .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .minOrNull()
            ?: error("这不是有效的 .xlsx 文件：找不到工作表（xl/worksheets/*.xml）")

        return parseSheet(decodeUtf8(parts.getValue(sheetKey)), sharedStrings)
    }

    // ------------------------------------------------------------ ZIP 读取

    private fun readZipParts(bytes: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                // 只读需要的部件，避免把整个包解到内存
                val name = entry.name
                if (name == "xl/sharedStrings.xml" ||
                    name.startsWith("xl/worksheets/")
                ) {
                    result[name] = zip.readBytes()
                }
            }
        }
        return result
    }

    /** xlsx 内部 XML 统一为 UTF-8。 */
    private fun decodeUtf8(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    // -------------------------------------------------------- sharedStrings

    private val SI_BLOCK = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
    private val T_ELEMENT = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)

    /**
     * 解析共享字符串表。
     *
     * 一个 `<si>` 可能含多个 `<t>`（富文本分段），需拼接而非只取第一个，
     * 否则"放 假"这类被拆成两段的文本会读成"放"。
     */
    private fun parseSharedStrings(xml: String): List<String> =
        SI_BLOCK.findAll(xml).map { si ->
            T_ELEMENT.findAll(si.groupValues[1])
                .joinToString("") { unescapeXml(it.groupValues[1]) }
        }.toList()

    // ------------------------------------------------------------- 工作表

    /** 匹配 `<c r="B3" t="s"><v>12</v></c>` 或自闭合形式。 */
    private val CELL = Regex(
        "<c\\s+([^>]*?)(?:/>|>(.*?)</c>)",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val ATTR = Regex("""(\w+)="([^"]*)"""")
    private val ROW = Regex("""<row[^>]*\sr="(\d+)"""")

    private fun parseSheet(xml: String, sharedStrings: List<String>): Map<Int, XlsxRow> {
        val rows = sortedMapOf<Int, XlsxRow>()
        // 先按 <row> 切分，行号优先取 r 属性；缺失时按出现顺序累加。
        var positionalRow = 0
        ROW_SPLIT.split(xml).forEach { chunk ->
            if (!chunk.contains("<c")) return@forEach
            positionalRow++
            val rowMatch = ROW.find(chunk)
            val rowNumber = rowMatch?.groupValues?.get(1)?.toIntOrNull() ?: positionalRow

            val cells = LinkedHashMap<String, String>()
            CELL.findAll(chunk).forEach { m ->
                val attrs = ATTR.findAll(m.groupValues[1])
                    .associate { it.groupValues[1] to it.groupValues[2] }
                val ref = attrs["r"] ?: return@forEach
                val col = ref.takeWhile { it.isLetter() }
                if (col.isEmpty()) return@forEach

                val body = m.groupValues[2]
                val type = attrs["t"]

                val raw = when (type) {
                    // t="s"：值是 sharedStrings 的下标
                    "s" -> body.firstTagText("v")?.toIntOrNull()
                        ?.let { sharedStrings.getOrNull(it) } ?: ""
                    // t="inlineStr"：值直接内联在 <is><t> 里
                    "inlineStr" -> body.allTagTexts("t").joinToString("")
                    // t="str"：公式结果字符串
                    "str" -> body.firstTagText("v").orEmpty()
                    // 其余（含无 t 的数值、日期序列号）都取 <v>
                    else -> body.firstTagText("v").orEmpty()
                }
                cells[col] = unescapeXml(raw)
            }

            if (cells.isNotEmpty()) rows[rowNumber] = cells
        }
        return rows
    }

    private val ROW_SPLIT = Regex("""<row[\s>]""")

    private fun String.firstTagText(tag: String): String? =
        Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
            .find(this)?.groupValues?.get(1)

    private fun String.allTagTexts(tag: String): List<String> =
        Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
            .findAll(this).map { it.groupValues[1] }.toList()

    // --------------------------------------------------------------- 转义

    /**
     * 反转义 XML 实体。
     *
     * 数字实体（`&#x8282;`）必须处理：中文文本在 xlsx 里可能以数字实体出现，
     * 不还原会导致节假日名称显示成乱码。
     */
    private fun unescapeXml(s: String): String {
        if (!s.contains('&')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch != '&') { sb.append(ch); i++; continue }
            val end = s.indexOf(';', i)
            if (end < 0) { sb.append(ch); i++; continue }
            val entity = s.substring(i + 1, end)
            val replacement = when {
                entity == "amp" -> "&"
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "quot" -> "\""
                entity == "apos" -> "'"
                entity.startsWith("#x") || entity.startsWith("#X") ->
                    entity.drop(2).toIntOrNull(16)?.let { codePointToString(it) }
                entity.startsWith("#") ->
                    entity.drop(1).toIntOrNull()?.let { codePointToString(it) }
                else -> null
            }
            if (replacement != null) { sb.append(replacement); i = end + 1 }
            else { sb.append(ch); i++ }
        }
        return sb.toString()
    }

    private fun codePointToString(codePoint: Int): String? =
        runCatching { String(Character.toChars(codePoint)) }.getOrNull()
}

/**
 * 极简 .xlsx 写出器（零第三方依赖），用于生成导入模板。
 *
 * 只写单工作表、纯文本单元格 —— 足以让模板在 Excel / WPS / LibreOffice
 * 中正常打开并填写。刻意不实现样式与公式：模板只需列名与示例行。
 */
object XlsxWriter {

    /** 表头 + 数据行，全部按文本写入（避免 Excel 自作主张做类型转换）。 */
    fun writeSheet(rows: List<List<String>>): ByteArray {
        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { rowIndex, cells ->
                val r = rowIndex + 1
                append("""<row r="$r">""")
                cells.forEachIndexed { colIndex, value ->
                    val ref = columnLetter(colIndex) + r
                    // 一律用 inlineStr：模板不需要共享字符串表，写出来更简单，
                    // 且避免 Excel 把 "2027-02-06" 自动转成日期序列号。
                    append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">""")
                    append(escapeXml(value))
                    append("""</t></is></c>""")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val parts = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="节假日" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheetXml,
        )

        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            parts.forEach { (name, content) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** 0 → A, 25 → Z, 26 → AA。 */
    fun columnLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
            if (i < 0) break
        }
        return sb.toString()
    }

    private fun escapeXml(s: String): String = buildString(s.length) {
        s.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
