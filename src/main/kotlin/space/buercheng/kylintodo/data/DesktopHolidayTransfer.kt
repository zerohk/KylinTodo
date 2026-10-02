package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.domain.HolidayImportResult
import space.buercheng.kylintodo.domain.HolidayImporter
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * 桌面端的节假日导入 / 模板导出实现。
 *
 * 用 AWT 的 [FileDialog] 而非 Swing 的 `JFileChooser`：
 * FileDialog 是系统原生对话框，在麒麟（UKUI）与 Windows 上外观与
 * 行为都与系统一致；且 Compose Desktop 底层就是 AWT，无需引入 Swing 依赖。
 */
object DesktopHolidayTransfer : HolidayTransfer {

    /** 模板文件名。带中文便于用户识别，扩展名必须是 .xlsx。 */
    private const val TEMPLATE_NAME = "节假日导入模板.xlsx"

    override fun exportTemplate(): String = runCatching {
        // 默认放到应用数据目录下的 export 子目录，与待办导出保持一致；
        // 用户可在对话框里改到别处（如桌面、U 盘）。
        val defaultDir = TodoExporter.defaultExportDir()
        Files.createDirectories(defaultDir)

        val target = chooseSaveTarget(defaultDir, TEMPLATE_NAME)
            ?: return "已取消导出模板"

        Files.write(target, XlsxWriter.writeSheet(templateRows()))
        "模板已导出：\n$target"
    }.getOrElse { e ->
        "导出模板失败：${e.message ?: e::class.simpleName}"
    }

    override fun pickAndParse(): HolidayImportResult? {
        val file = chooseOpenTarget() ?: return null
        val bytes = runCatching { Files.readAllBytes(file.toPath()) }.getOrElse { e ->
            return HolidayImportResult(
                emptyList(),
                listOf("无法读取文件：${e.message ?: e::class.simpleName}"),
            )
        }

        // 解析失败（如文件损坏、不是 xlsx）也要以结果形式返回，
        // 而不是抛异常 —— 用户选错文件是很常见的情况。
        val rows = runCatching { XlsxReader.readFirstSheet(bytes) }.getOrElse { e ->
            return HolidayImportResult(
                emptyList(),
                listOf(
                    "无法解析该文件：${e.message ?: e::class.simpleName}",
                    "请确认选择的是 .xlsx 文件（不支持 .xls 旧格式），" +
                        "或先用模板另存一份再填写。",
                ),
            )
        }

        return HolidayImporter.parse(rows)
    }

    // ------------------------------------------------------------ 文件对话框

    private fun chooseSaveTarget(defaultDir: Path, suggestedName: String): Path? {
        val dialog = FileDialog(null as Frame?, "导出节假日导入模板", FileDialog.SAVE).apply {
            directory = defaultDir.toString()
            file = suggestedName
            isVisible = true
        }
        val name = dialog.file ?: return null
        val dir = dialog.directory ?: defaultDir.toString()
        // 用户可能删掉扩展名，这里补上，否则 Excel 无法识别
        val fileName = if (name.endsWith(".xlsx", ignoreCase = true)) name else "$name.xlsx"
        return File(dir, fileName).toPath()
    }

    private fun chooseOpenTarget(): File? {
        val dialog = FileDialog(null as Frame?, "选择节假日数据文件", FileDialog.LOAD).apply {
            // 限定文件名模式，减少选错文件的概率
            file = "*.xlsx"
            isVisible = true
        }
        val name = dialog.file ?: return null
        val dir = dialog.directory ?: return null
        return File(dir, name)
    }

    // ---------------------------------------------------------------- 模板

    /**
     * 模板内容：表头 + 示例行 + 使用说明。
     *
     * 示例行刻意给出**两个真实日期**（一个放假、一个调休），
     * 让用户一眼看出「类型」列该填什么，而不是只看到"示例1/示例2"。
     */
    private fun templateRows(): List<List<String>> = listOf(
        listOf("日期", "类型", "名称"),
        listOf("2027-02-06", "休", "春节"),
        listOf("2027-02-07", "休", "春节"),
        listOf("2027-02-13", "班", "调休上班"),
        listOf("", "", ""),
        listOf("填写说明：", "", ""),
        listOf("1. 日期支持 2027-02-06 / 2027/2/6 / 2027年2月6日 三种写法", "", ""),
        listOf("2. 类型填「休」表示放假，填「班」表示调休上班", "", ""),
        listOf("3. 名称可留空；留空时若内置数据有名称会自动沿用", "", ""),
        listOf("4. 空行会被自动忽略，可自行增删行", "", ""),
        listOf("5. 同一天重复出现时，以文件中靠后的那行为准", "", ""),
        listOf("6. 导入后会覆盖上一次导入的数据，不会与内置数据冲突", "", ""),
    )
}
