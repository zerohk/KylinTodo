package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.domain.TodoItem
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * 桌面端的数据导出实现。
 *
 * 需求 1（导出数据可自定义路径）：弹原生目录选择对话框让用户指定位置。
 *
 * ## 为什么用「选目录」而不是「选文件名」
 * 一次导出会生成**两个**文件（JSON 与 CSV），用系统的"保存文件"对话框
 * 只能填一个名字，语义上说不通。让用户选一个目录，两个文件都写进去，
 * 与用户心智一致。
 *
 * ## 取消时回落默认目录
 * 用户在对话框里点取消并不一定是"不想导出"，更可能是"用默认位置就行"。
 * 因此取消时回落到应用数据目录下的 export，而不是报"已取消"让用户白点一次。
 * 对话框里会明确显示默认位置，用户能看到文件去了哪里。
 */
object DesktopDataExporter : DataExporter {

    override fun exportAll(items: List<TodoItem>): String = runCatching {
        val defaultDir = TodoExporter.defaultExportDir()
        Files.createDirectories(defaultDir)

        val targetDir = chooseDirectory(defaultDir) ?: defaultDir
        val result = TodoExporter.export(items, targetDir)
        TodoExporter.describe(result)
    }.getOrElse { e ->
        "导出失败：${e.message ?: e::class.simpleName}"
    }

    /**
     * 让用户选择导出目录。
     *
     * Java 的 [FileDialog] 没有"仅选择目录"模式：设 `SAVE` 并把 `file` 置空，
     * 用户进入某个目录后直接确认即可，返回的 `directory` 就是目标目录。
     * 用原生对话框而非 Swing 的 `JFileChooser`，在麒麟（UKUI）与 Windows 上
     * 外观与行为都与系统一致，且 Compose Desktop 底层就是 AWT。
     *
     * @return 用户确认的目录；取消时返回 null
     */
    private fun chooseDirectory(defaultDir: Path): Path? {
        val dialog = FileDialog(
            null as Frame?,
            "选择导出位置（点「保存」即把文件写入当前打开的文件夹）",
            FileDialog.SAVE,
        ).apply {
            directory = defaultDir.toString()
            // 不预设文件名：用户只需导航到目标文件夹后确认
            file = ""
            isVisible = true
        }

        val dir = dialog.directory ?: return null
        // 某些平台上 file 为空的确认会被当作取消，此时 directory 仍有效，
        // 因此以 directory 为准。
        return runCatching { File(dir).toPath() }.getOrNull()
    }
}
