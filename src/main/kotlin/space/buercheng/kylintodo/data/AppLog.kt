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
            logRuntimeInfo()
        }
    }

    /**
     * 记录 JVM 与内存信息。
     *
     * 放在启动日志里有两个用处：
     *  1. 验证打包时设置的 `jvmArgs`（-Xmx 等）是否真的生效 ——
     *     jpackage 把这些参数写在 `app/<name>.cfg` 里，从外部（如 jcmd）
     *     看不到，只能从进程内部读。
     *  2. 用户反馈"内存占用高 / 卡顿"时，日志里直接有堆上限与实际使用量。
     */
    private fun logRuntimeInfo() {
        runCatching {
            val rt = Runtime.getRuntime()
            val mb = 1024L * 1024L
            info(
                "JVM",
                "Java ${System.getProperty("java.version")} | " +
                    "最大堆=${rt.maxMemory() / mb}MB | " +
                    "当前堆=${(rt.totalMemory() - rt.freeMemory()) / mb}MB",
            )
            val args = java.lang.management.ManagementFactory
                .getRuntimeMXBean().inputArguments
                .filter { it.startsWith("-X") }
            if (args.isNotEmpty()) {
                info("JVM", "启动参数: ${args.joinToString(" ")}")
            }
            logMemoryBreakdown()
        }
    }

    /**
     * 记录内存构成。
     *
     * 排查"占用高"时必须先看清结构：实测本应用的**堆只占约 20MB**，
     * 而进程整体 RSS 有 300MB+ —— 说明大头在堆外（Metaspace / Code Cache /
     * Skia native）。只看 -Xmx 是找不到问题的。
     */
    fun logMemoryBreakdown() {
        runCatching {
            val mb = 1024L * 1024L
            val pools = java.lang.management.ManagementFactory.getMemoryPoolMXBeans()
            fun used(nameFragment: String): Long =
                pools.filter { it.name.contains(nameFragment, ignoreCase = true) }
                    .sumOf { it.usage?.used ?: 0L }

            val metaspace = used("Metaspace")
            val codeCache = used("Code Cache")
            val threads = java.lang.management.ManagementFactory.getThreadMXBean().threadCount
            val nonHeap = java.lang.management.ManagementFactory
                .getMemoryMXBean().nonHeapMemoryUsage.used

            info(
                "JVM",
                "内存构成：非堆=${nonHeap / mb}MB（其中 Metaspace=${metaspace / mb}MB、" +
                    "CodeCache=${codeCache / mb}MB）| 线程数=$threads",
            )
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

    /**
     * 读取日志内容（供**应用内**查看）。
     *
     * 这是"打开日志文件夹"的兜底方案：麒麟等桌面环境下外部文件管理器
     * 未必能被拉起，内置查看器则完全绕开这个问题 ——
     * 用户可以直接在应用里把内容复制给我们。
     *
     * @param maxLines 只取最后若干行：日志可达 2MB，全量塞进对话框既慢也没必要
     */
    fun readContent(maxLines: Int = 500): String {
        val file = logFile ?: return "日志未启用"
        return runCatching {
            if (!Files.exists(file)) return "日志文件不存在：$file"
            val lines = Files.readAllLines(file, Charsets.UTF_8)
            if (lines.size <= maxLines) {
                lines.joinToString("\n")
            } else {
                val omitted = lines.size - maxLines
                "…（已省略最早的 $omitted 行，共 ${lines.size} 行）\n" +
                    lines.takeLast(maxLines).joinToString("\n")
            }
        }.getOrElse { e ->
            "读取日志失败：${e.message ?: e::class.simpleName}"
        }
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
