package space.buercheng.kylintodo.data

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 轻量文件日志（需求：便于排查问题，需控制大小并定期清理）。
 *
 * ## 为什么不用 java.util.logging / Log4j
 * 项目里已有少量 `Logger` 用法，但那些日志默认只输出到控制台 ——
 * 从桌面图标启动时**根本没有控制台**，日志等于丢失。
 * 排查用户问题时，最需要的是"能拿到一个日志文件"。
 *
 * 因此这里直接写文件：实现简单、无依赖、行为可预期。
 *
 * ## 大小控制与清理
 * 采用**单文件 + 滚动截断**，而不是多文件轮转：
 *  - 上限 [MAX_BYTES]（2 MB）。超过后保留后半部分，丢弃最早的内容。
 *    日历应用的日志价值随时间快速衰减 —— 用户报问题时关心的是最近几分钟，
 *    而不是上周的启动记录。
 *  - 单文件也让用户"发给我看看"变得简单：只有一个文件要发。
 *    多文件轮转会出现"该发哪一个"的问题。
 *  - 写入在超过阈值时**同步截断**，不做后台任务：写入本就低频，
 *    省掉一个常驻线程。
 *
 * ## 线程安全
 * 所有写入经 [lock] 串行化。日志本身不应成为竞态来源。
 */
object AppLog {

    private const val MAX_BYTES = 2L * 1024 * 1024
    private const val FILE_NAME = "app.log"

    /** 截断后保留的比例。留 60% 而非 50%，减少截断频率。 */
    private const val KEEP_RATIO = 0.6

    private val TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private val lock = Any()

    @Volatile
    private var logFile: Path? = null

    /** 是否已初始化。未初始化时所有写入静默丢弃，不影响功能。 */
    val isReady: Boolean get() = logFile != null

    /** 日志文件的当前路径，供界面显示（便于用户找到并发给我们）。 */
    fun currentFile(): Path? = logFile

    /** 日志文件当前大小。 */
    fun currentSize(): Long = logFile?.let { runCatching { Files.size(it) }.getOrNull() } ?: 0L

    /**
     * 初始化。应在应用启动最早期调用。
     *
     * 会立即清理超限的旧日志，避免"上次遗留的大文件"一直占着空间。
     */
    fun init() {
        runCatching {
            val dir = AppPaths.dataDirectory().resolve("logs")
            Files.createDirectories(dir)
            val file = dir.resolve(FILE_NAME)
            logFile = file
            rotateIfNeeded(file)
            info("AppLog", "日志已启动，文件：$file（上限 ${MAX_BYTES / 1024 / 1024} MB）")
        }
    }

    fun info(tag: String, message: String) = write("INFO", tag, message, null)

    fun warn(tag: String, message: String, error: Throwable? = null) =
        write("WARN", tag, message, error)

    fun error(tag: String, message: String, error: Throwable? = null) =
        write("ERROR", tag, message, error)

    /**
     * 安装全局未捕获异常记录。
     *
     * 这是排查"程序突然消失"这类问题的关键 —— 崩溃栈若只打印到 stderr，
     * 从图标启动时用户根本看不到，也就无从反馈。
     */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            error("Crash", "线程 ${thread.name} 未捕获异常", throwable)
            // 交回原有处理器（通常是打印到控制台），不吞掉异常
            previous?.uncaughtException(thread, throwable)
        }
    }

    // -------------------------------------------------------------- 内部

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val file = logFile ?: return
        synchronized(lock) {
            runCatching {
                val line = buildString {
                    append(LocalDateTime.now().format(TIMESTAMP))
                    append(' ').append(level.padEnd(5))
                    append(" [").append(tag).append("] ")
                    append(message)
                    if (error != null) {
                        append('\n').append(error.stackTraceToString().trimEnd())
                    }
                    append('\n')
                }
                // CREATE + APPEND：首次创建，之后追加
                Files.write(
                    file,
                    line.toByteArray(Charsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                )
                rotateIfNeeded(file)
            }
            // 日志失败绝不影响主流程
        }
    }

    /**
     * 超限时保留后半部分。
     *
     * 按**行**截断而非按字节：按字节会把一行切成两半，
     * 日志文件开头出现半行乱码，反而增加困惑。
     */
    private fun rotateIfNeeded(file: Path) {
        val size = runCatching { Files.size(file) }.getOrNull() ?: return
        if (size <= MAX_BYTES) return

        val lines = runCatching { Files.readAllLines(file, Charsets.UTF_8) }.getOrNull() ?: return
        val keepCount = (lines.size * KEEP_RATIO).toInt().coerceAtLeast(1)
        val kept = lines.takeLast(keepCount)

        val header = listOf(
            "=== 日志超过 ${MAX_BYTES / 1024 / 1024} MB，已丢弃最早的 " +
                "${lines.size - kept.size} 行（共 ${lines.size} 行） ===",
        )
        runCatching {
            Files.write(file, (header + kept).joinToString("\n").toByteArray(Charsets.UTF_8))
        }
    }

    /** 供设置界面展示：日志位置与大小。 */
    fun describe(): String {
        val file = logFile ?: return "日志未启用"
        val kb = currentSize() / 1024
        return "$file（$kb KB，上限 ${MAX_BYTES / 1024 / 1024} MB）"
    }

    /** 供测试与"清理"入口使用：删除日志文件。 */
    fun clear() {
        val file = logFile ?: return
        synchronized(lock) {
            runCatching { File(file.toString()).delete() }
            runCatching { info("AppLog", "日志已被用户清空") }
        }
    }
}
