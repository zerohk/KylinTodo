package space.buercheng.kylintodo.data

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * 与桌面环境交互的小工具（打开文件夹、复制到剪贴板）。
 *
 * ## 为什么不能只用 `java.awt.Desktop`
 * `Desktop.getDesktop().open(dir)` 在 Linux 上依赖桌面环境正确注册了
 * MIME 关联与 D-Bus 服务。实测**麒麟（UKUI）上经常不工作** ——
 * 要么 `isDesktopSupported()` 为 false，要么调用后静默无声。
 * 因此必须准备多级回退。
 *
 * ## 回退顺序
 * 1. AWT `Desktop`（Windows / 配置良好的 Linux 桌面）
 * 2. 平台命令：Linux 用 `xdg-open`（freedesktop 标准，几乎必有），
 *    失败再依次尝试常见文件管理器；Windows 用 `explorer`
 * 3. 都失败 → **把路径复制到剪贴板**并明确告知用户，
 *    让用户能粘贴到文件管理器地址栏自行前往
 *
 * 第 3 步很关键：报"打不开"而没有出路，用户就只能干瞪眼。
 */
object DesktopIntegration {

    /**
     * 打开目录。
     *
     * @return 可直接展示给用户的结果说明（成功与否都会说明清楚）
     */
    fun openDirectory(dir: Path): String {
        if (!dir.toFile().exists()) {
            return "目录不存在：$dir"
        }

        // 1) AWT Desktop
        if (openWithAwt(dir)) return "已打开文件夹：$dir"

        // 2) 平台命令
        val command = firstWorkingCommand(dir)
        if (command != null) return "已打开文件夹：$dir（通过 $command）"

        // 3) 兜底：复制路径
        val copied = copyToClipboard(dir.toString())
        return if (copied) {
            "当前桌面环境无法自动打开文件夹，路径已复制到剪贴板：\n$dir\n" +
                "可打开文件管理器，在地址栏粘贴后回车。"
        } else {
            "当前桌面环境无法自动打开文件夹，请手动前往：\n$dir"
        }
    }

    private fun openWithAwt(dir: Path): Boolean = runCatching {
        val desktop = java.awt.Desktop.getDesktop()
        if (!java.awt.Desktop.isDesktopSupported()) return false
        if (!desktop.isSupported(java.awt.Desktop.Action.OPEN)) return false
        desktop.open(dir.toFile())
        true
    }.getOrDefault(false)

    /**
     * 按平台依次尝试打开目录的命令。
     *
     * Linux 顺序：xdg-open（标准）→ peony（麒麟 UKUI 的文件管理器）
     * → nautilus / dolphin / thunar（其它常见桌面）
     */
    private fun firstWorkingCommand(dir: Path): String? {
        val path = dir.toAbsolutePath().toString()
        val candidates: List<List<String>> = when {
            isWindows() -> listOf(listOf("explorer", path))
            isMac() -> listOf(listOf("open", path))
            else -> listOf(
                listOf("xdg-open", path),
                listOf("peony", path),
                listOf("nautilus", path),
                listOf("dolphin", path),
                listOf("thunar", path),
            )
        }

        candidates.forEach { cmd ->
            if (runCommand(cmd)) return cmd.first()
        }
        return null
    }

    /**
     * 执行命令并判断是否成功启动。
     *
     * 等待最多 3 秒：`xdg-open` 这类工具通常 fork 后立即返回，
     * 能立刻拿到退出码；文件管理器若一直在前台运行，则超时后
     * 视为"已成功拉起"而不是失败。
     */
    private fun runCommand(command: List<String>): Boolean = runCatching {
        val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
            // 仍在运行：说明程序已被拉起（例如文件管理器驻留）
            return true
        }
        process.exitValue() == 0
    }.getOrDefault(false)

    /** 复制文本到系统剪贴板。 */
    fun copyToClipboard(text: String): Boolean = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard
            .setContents(StringSelection(text), null)
        true
    }.getOrDefault(false)

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun isMac(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")
}
