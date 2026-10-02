package space.buercheng.kylintodo

/**
 * 应用元信息，供「关于」页与打包配置共用。
 *
 * 集中定义避免各处硬编码字符串导致不一致。
 */
object AppInfo {

    /** 对外显示名称。 */
    const val DISPLAY_NAME = "大智桌面日历"

    /** 英文名，用于日志、包标识与文件命名。 */
    const val ENGLISH_NAME = "Dazhi Desktop Calendar"

    /** 版本号，与 build.gradle.kts 的 packageVersion 保持一致。 */
    const val VERSION = "1.0.0"

    /** 一句话描述。 */
    const val DESCRIPTION = "面向银河麒麟操作系统的日历与待办应用"

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
