package space.buercheng.kylintodo

/**
 * 应用元信息。
 *
 * 名称、版本、描述集中在这里，避免窗口标题、界面标题栏、导出文件的
 * 元信息、打包配置各写一遍导致不一致 —— 实际就发生过：应用名曾同时
 * 以常量与字面量两种形式存在于三个文件里，改名时漏改了一处。
 */
object AppInfo {

    /**
     * 默认显示名称，用户可在设置里改。
     *
     * 界面左上角与窗口标题共用这一个值：用户看到两处名字不一致会很困惑。
     * 曾经窗口标题是「大智桌面日历」而界面里还是「麒麟日历」，正是这种不一致。
     */
    const val DEFAULT_DISPLAY_NAME = "大智日历"

    /** 英文名，用于日志与文件命名。 */
    const val ENGLISH_NAME = "Dazhi Desktop Calendar"

    /** 版本号，与 build.gradle.kts 的 packageVersion 保持一致。 */
    const val VERSION = "1.1.0"

    /** 一句话描述。 */
    const val DESCRIPTION = "面向银河麒麟操作系统的日历与待办应用"

    /** 名称允许的最大长度，防止用户粘贴超长文本撑破标题栏。 */
    const val MAX_NAME_LENGTH = 20

    /**
     * 校验并规范化用户自定义的名称。
     *
     * 空白名称回落到默认值 —— 否则标题栏会变成空白，用户会以为程序坏了。
     */
    fun normalizeName(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_DISPLAY_NAME
        return if (trimmed.length > MAX_NAME_LENGTH) trimmed.take(MAX_NAME_LENGTH) else trimmed
    }

    /**
     * 运行期技术信息。
     *
     * 放在「关于」里很有用：用户回报问题时能一眼看出环境，
     * 省去反复询问 JDK 版本、系统与数据目录。
     */
    fun runtimeInfo(dataDir: String): String = buildString {
        appendLine("版本：$VERSION")
        appendLine("系统：${System.getProperty("os.name")} ${System.getProperty("os.version")} " +
            "(${System.getProperty("os.arch")})")
        appendLine("运行时：Java ${System.getProperty("java.version")} " +
            "(${System.getProperty("java.vendor")})")
        appendLine("数据目录：$dataDir")
    }
}
