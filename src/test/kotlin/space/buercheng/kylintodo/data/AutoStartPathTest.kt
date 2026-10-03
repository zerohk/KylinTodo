package space.buercheng.kylintodo.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证开机自启动的启动器路径推断与自启动项内容。
 *
 * 这里的断言都对应**实测踩过的坑**，用测试锁住，防止日后被"优化"回去。
 */
class AutoStartPathTest {

    // ---------------- 路径推断 ----------------

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

    // ---------------- 自启动项内容 ----------------

    private val exec = "/opt/dazhi-calendar/bin/dazhi-calendar"

    /**
     * `NoDisplay` 必须是 `false`。
     *
     * 这是麒麟上"开机自启动静默失效"的**实测根因**：1.1.5 与 1.1.6
     * （Exec 写法不同）都设了 `NoDisplay=true`，均未生效；1.1.7 改为
     * `false` 后立即生效。
     *
     * 规范里 `NoDisplay` 只影响菜单显示，但麒麟的会话实现疑似复用了
     * "收集可见应用"的过滤逻辑，把该项直接排除了。
     * **失败时没有任何报错**，因此必须用测试守住。
     */
    @Test
    fun `自启动项必须使用 NoDisplay=false`() {
        val entry = AutoStartManager.desktopEntry(exec)
        assertTrue(
            entry.contains("NoDisplay=false"),
            "NoDisplay 必须为 false，实际内容：\n$entry",
        )
        assertFalse(
            entry.contains("NoDisplay=true"),
            "NoDisplay=true 会让自启动在麒麟上静默失效（实测确认），不能出现",
        )
    }

    /**
     * `Exec` 不加引号。
     *
     * 规范说引号会被剥离，但实现未必遵守；写了引号可能被当成路径的一部分。
     * 我们的安装路径不含空格，无需引号。
     */
    @Test
    fun `自启动项 Exec 不加引号`() {
        val entry = AutoStartManager.desktopEntry(exec)
        assertTrue(
            entry.contains("Exec=$exec"),
            "Exec 应为不带引号的绝对路径，实际内容：\n$entry",
        )
        assertFalse(
            entry.contains("Exec=\""),
            "Exec 不应加引号：实现未必剥离引号，可能被当成路径的一部分",
        )
    }

    /** 自启动项应包含规范要求的基本键。 */
    @Test
    fun `自启动项包含必要字段`() {
        val entry = AutoStartManager.desktopEntry(exec)
        listOf(
            "[Desktop Entry]",
            "Type=Application",
            "Exec=$exec",
            "TryExec=$exec",
            "Hidden=false",
        ).forEach { expected ->
            assertTrue(entry.contains(expected), "自启动项缺少「$expected」，实际：\n$entry")
        }
    }
}
