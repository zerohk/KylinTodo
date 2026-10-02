package space.buercheng.kylintodo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 应用名称规范化测试。
 *
 * 名称可由用户在设置里自由输入，因此必须处理空白与超长输入 ——
 * 否则标题栏可能变成空白（用户以为程序坏了）或被撑破布局。
 */
class AppInfoTest {

    @Test
    fun `默认名称为大智日历`() {
        assertEquals("大智日历", AppInfo.DEFAULT_DISPLAY_NAME)
    }

    @Test
    fun `空白输入回落到默认名称`() {
        assertEquals(AppInfo.DEFAULT_DISPLAY_NAME, AppInfo.normalizeName(null))
        assertEquals(AppInfo.DEFAULT_DISPLAY_NAME, AppInfo.normalizeName(""))
        assertEquals(AppInfo.DEFAULT_DISPLAY_NAME, AppInfo.normalizeName("   "))
        assertEquals(AppInfo.DEFAULT_DISPLAY_NAME, AppInfo.normalizeName("\t\n "))
        // 全角空格同样应视为空白
        assertEquals(AppInfo.DEFAULT_DISPLAY_NAME, AppInfo.normalizeName("\u3000"))
    }

    @Test
    fun `去掉首尾空白但保留内部空格`() {
        assertEquals("我的 日历", AppInfo.normalizeName("  我的 日历  "))
    }

    @Test
    fun `超长名称被截断到上限`() {
        val long = "日".repeat(AppInfo.MAX_NAME_LENGTH + 10)
        val result = AppInfo.normalizeName(long)
        assertEquals(AppInfo.MAX_NAME_LENGTH, result.length)
        assertTrue(long.startsWith(result), "截断应保留前缀")
    }

    @Test
    fun `恰好等于上限的名称不被截断`() {
        val exact = "日".repeat(AppInfo.MAX_NAME_LENGTH)
        assertEquals(exact, AppInfo.normalizeName(exact))
    }

    @Test
    fun `正常名称原样保留`() {
        assertEquals("我的日历", AppInfo.normalizeName("我的日历"))
        assertEquals("My Calendar", AppInfo.normalizeName("My Calendar"))
    }

    @Test
    fun `运行期信息包含版本与数据目录`() {
        val info = AppInfo.runtimeInfo("/tmp/data")
        assertTrue(info.contains("版本：${AppInfo.VERSION}"))
        assertTrue(info.contains("数据目录：/tmp/data"))
        assertTrue(info.contains("Java"))
    }
}
