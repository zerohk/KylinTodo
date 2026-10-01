package space.buercheng.kylintodo.data

import java.nio.file.Path
import java.nio.file.Paths

/**
 * 解析应用数据目录。
 *
 * 需求 2.2 的目标运行环境是银河麒麟 V10（Linux），开发环境是 Windows，
 * 因此必须按各平台惯例放置数据文件，不能写死路径：
 *
 *  - Linux：遵循 XDG 规范，`$XDG_DATA_HOME/KylinTodo`，未设置时回落到
 *    `~/.local/share/KylinTodo`。麒麟作为 Linux 发行版沿用此规范。
 *  - Windows：`%LOCALAPPDATA%\KylinTodo`（即 `~\AppData\Local\KylinTodo`）。
 *  - 其他：回落到 `~/KylinTodo`。
 */
object AppPaths {

    private const val APP_DIR_NAME = "KylinTodo"
    private const val DB_FILE_NAME = "kylintodo.db"

    /** 待办数据库文件的完整路径。 */
    fun databaseFile(): Path = dataDirectory().resolve(DB_FILE_NAME)

    /** 应用数据目录。 */
    fun dataDirectory(): Path {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home") ?: "."
        return when {
            os.contains("win") -> {
                val localAppData = System.getenv("LOCALAPPDATA")
                if (!localAppData.isNullOrBlank()) {
                    Paths.get(localAppData, APP_DIR_NAME)
                } else {
                    Paths.get(home, "AppData", "Local", APP_DIR_NAME)
                }
            }

            else -> {
                // Linux / macOS / 其他类 Unix 环境，遵循 XDG 基本目录规范
                val xdgDataHome = System.getenv("XDG_DATA_HOME")
                if (!xdgDataHome.isNullOrBlank()) {
                    Paths.get(xdgDataHome, APP_DIR_NAME)
                } else {
                    Paths.get(home, ".local", "share", APP_DIR_NAME)
                }
            }
        }
    }
}
