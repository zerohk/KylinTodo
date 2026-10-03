package space.buercheng.kylintodo.data

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * 开机自启动的系统集成（需求 6）。
 *
 * ## 两端机制完全不同
 * - **Linux（麒麟）**：写 XDG 自启动项 `~/.config/autostart/` 下的 .desktop 文件。
 *   这是 freedesktop 标准，UKUI / GNOME / KDE 都识别。
 * - **Windows**：写当前用户的注册表 Run 项
 *   （`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`）。
 *   只用 HKCU 而非 HKLM：无需管理员权限，且"是否开机启动"本是用户级偏好。
 *
 * ## 设计要点
 * [apply] 返回 `null` 表示成功，否则返回**给用户看的失败原因**。
 * 调用方必须先确认成功再更新设置 —— 否则界面显示"已开启"而实际没生效，
 * 用户重启后才发现，且无从判断哪里出了问题。
 */
object AutoStartManager {

    private const val DESKTOP_FILE_NAME = "dazhi-calendar.desktop"
    private const val WINDOWS_RUN_KEY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val WINDOWS_VALUE_NAME = "DazhiCalendar"

    /** 当前平台是否支持自动配置自启动。 */
    fun isSupported(): Boolean = isWindows() || isLinux()

    /**
     * 设置或取消开机自启动。
     *
     * @param enabled true = 开启，false = 关闭
     * @return null 表示成功；否则为失败原因（可直接展示给用户）
     */
    fun apply(enabled: Boolean): String? = runCatching {
        when {
            isWindows() -> applyWindows(enabled)
            isLinux() -> applyLinux(enabled)
            else -> "当前系统不支持自动配置开机自启动，请手动添加"
        }
    }.getOrElse { e ->
        "配置开机自启动失败：${e.message ?: e::class.simpleName}"
    }

    // ------------------------------------------------------------- Linux

    /**
     * 写 / 删 XDG 自启动项。
     *
     * `Exec` 必须用**绝对路径**：自启动时 `PATH` 与用户 shell 不同，
     * 只写程序名会找不到可执行文件。安装后应用位于
     * `/opt/dazhi-calendar/bin/dazhi-calendar`（见 .deb 的安装布局）。
     */
    private fun applyLinux(enabled: Boolean): String? {
        val configHome = System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.config")
        val autostartDir = Paths.get(configHome, "autostart")
        val target = autostartDir.resolve(DESKTOP_FILE_NAME)

        return if (enabled) {
            Files.createDirectories(autostartDir)

            // 写入前先校验启动器路径真实存在。
            // 之前这里只静默写文件：路径推断错了也照写，桌面环境找不到
            // 可执行文件就静默跳过 —— 用户完全无从判断哪里出了问题。
            val exec = currentExecutablePath()
            if (exec == null || !File(exec).exists()) {
                val msg = "无法确定程序启动路径（推断为 $exec），已跳过。" +
                    "请改用手动方式添加自启动。"
                AppLog.warn("AutoStart", msg)
                return msg
            }

            Files.writeString(target, desktopEntry(exec))
            // 部分桌面环境（MATE / 旧版 GNOME）要求 .desktop 有执行位才视为
            // "受信任"并执行；没有执行位会静默忽略。加上更稳妥。
            runCatching { target.toFile().setExecutable(true) }
            AppLog.info("AutoStart", "已写入自启动项：$target\n${desktopEntry(exec)}")
            null
        } else {
            // 不存在也算成功：用户的意图是"不要自启动"，已是该状态
            runCatching { Files.deleteIfExists(target) }
            AppLog.info("AutoStart", "已移除自启动项：$target")
            null
        }
    }

    /**
     * 生成自启动项内容。
     *
     * 各键的作用（freedesktop 桌面项规范）：
     *  - `Type=Application` / `Exec`：必需
     *  - `TryExec`：桌面环境用它**校验可执行文件是否存在**，不存在就跳过该项
     *  - `Terminal=false`：缺省时个别实现会尝试在终端里运行
     *  - `Hidden=false`：显式声明未被隐藏
     *  - `X-GNOME-Autostart-enabled=true`：GNOME 系的启用标记，对其它实现无害
     *
     * ## ⚠️ `NoDisplay` 必须是 `false`，不要改成 `true`
     *
     * 这是**实测确认过的根因**，不是猜测。麒麟（UKUI）上曾出现开机自启动
     * 完全静默失效，最终通过跨版本对照定位到这里：
     *
     * | 版本 | `Exec` | `NoDisplay` | 结果 |
     * | --- | --- | --- | --- |
     * | 1.1.5 | 无引号 | `true` | 未生效 |
     * | 1.1.6 | 带引号 | `true` | 未生效 |
     * | 1.1.7 | 无引号 | `false` | **生效** |
     *
     * 前两版 `Exec` 写法不同却都失效，共同点是 `NoDisplay=true`；
     * 改为 `false` 后立即生效。
     *
     * 按规范，`NoDisplay` 的语义**只是"不在应用菜单里显示"**，
     * autostart 项本不受影响。推测麒麟的会话实现复用了"收集可见应用"的
     * 同一套过滤逻辑，于是 `NoDisplay=true` 的项在第一步就被排除了。
     *
     * 因此：**即使规范允许，也不要设成 `true`** —— 它会让自启动静默失效，
     * 而且完全没有任何错误提示，极难排查。autostart 目录里的文件本就不会
     * 出现在应用菜单（菜单项由 .deb 单独安装），设 `false` 没有任何副作用。
     *
     * ## 关于 `Exec` 为什么**不加引号**
     * 规范说 `Exec` 按 shell 风格分词、双引号会被剥离，因此
     * `Exec="/path/app"` 与 `Exec=/path/app` 等价。但实现未必都遵守：
     * 若某个环境只做简单切分而不剥离引号，就会去执行
     * `/path/app"`（把引号当成路径的一部分）从而失败。
     * 我们的安装路径 `/opt/dazhi-calendar/bin/dazhi-calendar` 不含空格，
     * 无需引号 —— 因此选择**最保守的写法**。
     *
     * （实测表明引号并非麒麟那次失效的根因，但它确实是应当消除的隐患。）
     */
    internal fun desktopEntry(exec: String): String {
        return buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Name=大智日历")
            appendLine("Comment=日历与待办事项")
            appendLine("Exec=$exec")
            appendLine("TryExec=$exec")
            appendLine("Terminal=false")
            // 必须是 false —— 详见上方说明，true 会导致自启动静默失效
            appendLine("NoDisplay=false")
            appendLine("Hidden=false")
            appendLine("X-GNOME-Autostart-enabled=true")
        }
    }

    /** 当前自启动项的状态描述（供日志与用户反馈问题使用）。 */
    fun describeCurrentState(): String {
        if (!isLinux()) return describeLocation()
        val configHome = System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.config")
        val target = Paths.get(configHome, "autostart", DESKTOP_FILE_NAME)
        return if (Files.exists(target)) {
            "已启用（$target）：\n${runCatching { Files.readString(target) }.getOrDefault("")}"
        } else {
            "未启用（$target 不存在）"
        }
    }

    /**
     * 当前可执行文件的绝对路径。
     *
     * ## 为什么按平台区分上溯层级
     * jpackage 在两端生成的应用布局不同，`java.home`（JVM 运行时）的位置也不同：
     *
     *  - **Linux**：`/opt/dazhi-calendar/bin/dazhi-calendar`（启动器）
     *    + `/opt/dazhi-calendar/lib/runtime`（JVM）→ java.home 上溯**两级**才是安装根
     *  - **Windows**：`<root>/dazhi-calendar.exe`（启动器）
     *    + `<root>/runtime`（JVM）→ java.home 上溯**一级**就是安装根
     *
     * 上一版两个平台都用「上溯一级」，在 Linux 上会得到
     * `<root>/lib/bin/dazhi-calendar` 这个**不存在的路径**，导致自启动静默失效。
     * 已实测确认 deb 布局（WSL 解包）：runtime 在 `lib/runtime`。
     */
    private fun currentExecutablePath(): String? {
        val home = System.getProperty("java.home") ?: return null
        return resolveLauncherPath(home, isLinux())
    }

    /**
     * 从 java.home 推断启动器路径（纯函数，便于单元测试）。
     *
     * jpackage 布局（已用 WSL 解包 deb 实测）：
     *  - Linux：`<root>/bin/dazhi-calendar`（启动器）+ `<root>/lib/runtime`（JVM）
     *    → java.home 上溯**两级**
     *  - Windows：`<root>/dazhi-calendar.exe`（启动器）+ `<root>/runtime`（JVM）
     *    → java.home 上溯**一级**
     */
    internal fun resolveLauncherPath(javaHome: String, linux: Boolean): String {
        val home = File(javaHome)
        return if (linux) {
            val root = home.parentFile?.parentFile
            File(root, "bin/dazhi-calendar").absolutePath
        } else {
            val root = home.parentFile
            File(root, "dazhi-calendar.exe").absolutePath
        }
    }

    // ----------------------------------------------------------- Windows

    private fun applyWindows(enabled: Boolean): String? {
        val executable = currentExecutablePath() ?: return "无法确定程序路径，请手动添加自启动"
        val command = if (enabled) {
            val escaped = executable.replace("\"", "\\\"")
            "reg add \"$WINDOWS_RUN_KEY\" /v $WINDOWS_VALUE_NAME /t REG_SZ /d \"\\\"$escaped\\\"\" /f"
        } else {
            "reg delete \"$WINDOWS_RUN_KEY\" /v $WINDOWS_VALUE_NAME /f"
        }

        val process = ProcessBuilder("cmd", "/c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(15, TimeUnit.SECONDS)

        if (!finished) {
            process.destroyForcibly()
            return "写入注册表超时，请手动配置"
        }
        // 删除不存在的值时 reg 返回非 0，但用户的意图（不要自启动）已达成，
        // 因此这里只对"开启"失败报错。
        if (process.exitValue() != 0 && enabled) {
            return "写入注册表失败：${output.trim().take(160)}"
        }
        return null
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun isLinux(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    /** 供「关于」页诊断用：自启动项的实际位置。 */
    fun describeLocation(): String = when {
        isWindows() -> "注册表 $WINDOWS_RUN_KEY\\$WINDOWS_VALUE_NAME"
        isLinux() -> {
            val configHome = System.getenv("XDG_CONFIG_HOME")
                ?.takeIf { it.isNotBlank() }
                ?: (System.getProperty("user.home") + "/.config")
            "$configHome/autostart/$DESKTOP_FILE_NAME"
        }
        else -> "当前系统不支持"
    }
}
