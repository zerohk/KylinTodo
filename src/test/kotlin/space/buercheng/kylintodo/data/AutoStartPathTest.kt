package space.buercheng.kylintodo.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证开机自启动的启动器路径推断。
 *
 * 锁住一个已实测确认的 bug：jpackage 在 Linux 与 Windows 上的应用布局不同，
 * `java.home`（JVM 运行时）上溯的层级也不同。混用会导致 Linux 写出不存在的
 * 路径，自启动静默失效。
 */
class AutoStartPathTest {

    @Test
    fun `Linux 布局上溯两级到安装根`() {
        // 用相对路径构造，expected 与 actual 走同一 File 解析，跨平台一致
        val javaHome = File("root/lib/runtime").absolutePath
        val launcher = AutoStartManager.resolveLauncherPath(javaHome, linux = true)
        assertEquals(
            File("root/bin/dazhi-calendar").absolutePath,
            launcher,
        )
    }

    @Test
    fun `Windows 布局上溯一级到安装根`() {
        val javaHome = File("root/runtime").absolutePath
        val launcher = AutoStartManager.resolveLauncherPath(javaHome, linux = false)
        assertEquals(
            File("root/dazhi-calendar.exe").absolutePath,
            launcher,
        )
    }
}
