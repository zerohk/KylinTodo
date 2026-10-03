package space.buercheng.kylintodo

import space.buercheng.kylintodo.data.AppLog
import space.buercheng.kylintodo.data.AutoStartManager
import space.buercheng.kylintodo.data.SettingsStore
import java.io.File

/**
 * 渲染模式引导：按用户设置切换到软件渲染（省内存）。
 *
 * ## 为什么需要"重启自己"
 * Skiko 在**初始化时**读取渲染模式，运行时无法切换。而 jpackage 生成的
 * 应用把 JVM 参数固定在 `app/<name>.cfg` 里（安装目录通常只读），
 * 程序也改不了它。
 *
 * 好消息是 Skiko 同时认**环境变量** `SKIKO_RENDER_API` ——
 * 环境变量可以由父进程在启动子进程时指定。因此做法是：
 * 用打包好的启动器**再启动一个自己**，带上正确的环境变量，然后当前进程退出。
 *
 * ## 为什么不能直接用 runtime 里的 java
 * jlink 裁剪后的运行时**不含 `java.exe`**（只有 `jvm.dll`），
 * 无法用它 `-cp` 方式拉起应用；只能复用 jpackage 的启动器。
 *
 * ## 防重启循环
 * 子进程带上 [RELAUNCH_FLAG] 环境变量。子进程若发现该标记已存在，
 * 就不再重启 —— 即便模式判断出现意外，也最多多启动一次，不会无限循环。
 */
object RenderModeBootstrap {

    /** Skiko 读取渲染模式的系统属性名 */
    private const val RENDER_API_PROPERTY = "skiko.renderApi"

    /** Skiko 读取渲染模式的环境变量名 */
    private const val RENDER_API_ENV = "SKIKO_RENDER_API"

    /** 防循环标记：子进程看到它就绝不再重启 */
    private const val RELAUNCH_FLAG = "DAZHI_RELAUNCHED"

    /**
     * 检查并按需重启。
     *
     * 必须在**获取单实例锁之前**调用：重启发生在持锁前，
     * 就不存在"旧进程还握着锁、新进程抢不到"的问题。
     *
     * @return true 表示已经拉起了新进程并应当立即退出当前进程
     */
    fun relaunchIfNeeded(args: Array<String>): Boolean {
        val wantSoftware = runCatching { SettingsStore.load().softwareRendering }
            .getOrDefault(false)

        val currentEnv = System.getenv(RENDER_API_ENV)
        val currentProperty = System.getProperty(RENDER_API_PROPERTY)
        val isSoftwareNow = currentEnv.equals("SOFTWARE", ignoreCase = true) ||
            currentProperty.equals("SOFTWARE", ignoreCase = true)

        if (wantSoftware == isSoftwareNow) return false

        // 已经在一次重启后的进程里：不再重启，避免循环
        if (System.getenv(RELAUNCH_FLAG) != null) {
            AppLog.warn(
                "RenderMode",
                "渲染模式与设置仍不一致，但已重启过一次，放弃以避免循环" +
                    "（期望软件渲染=$wantSoftware，当前=$isSoftwareNow）",
            )
            return false
        }

        return runCatching {
            val launcher = AutoStartManager.resolveLauncherPath(
                javaHome = System.getProperty("java.home") ?: return false,
                linux = System.getProperty("os.name").orEmpty()
                    .lowercase().contains("linux"),
            )
            if (!File(launcher).exists()) {
                AppLog.warn("RenderMode", "找不到启动器（$launcher），不重启")
                return false
            }

            AppLog.info(
                "RenderMode",
                "渲染模式变更（软件渲染=$wantSoftware），正在重启应用…",
            )

            val pb = ProcessBuilder(launcher)
            // 把原始命令行参数一并传下去，避免丢失 --view / --date 等
            pb.command().addAll(args.toList())
            pb.directory(File(System.getProperty("user.dir") ?: "."))
            pb.environment().apply {
                if (wantSoftware) {
                    put(RENDER_API_ENV, "SOFTWARE")
                } else {
                    // 硬件渲染：必须显式移除，否则子进程会继承 SOFTWARE
                    remove(RENDER_API_ENV)
                }
                put(RELAUNCH_FLAG, "1")
            }
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            pb.start()
            true
        }.getOrElse { e ->
            AppLog.error("RenderMode", "重启失败，继续以当前模式运行", e)
            false
        }
    }
}
