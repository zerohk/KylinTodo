package space.buercheng.kylintodo.data

import java.util.prefs.Preferences

/**
 * 界面外观模式（需求反馈第 5 条的「皮肤」）。
 */
enum class ThemeMode(val label: String) {
    /** 跟随系统（Compose 的 isSystemInDarkTheme） */
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/**
 * 字号档位。
 *
 * 用倍率而非具体字号：既能整体放大方便阅读，也不会因逐个控件调字号
 * 而破坏既有布局比例。
 */
enum class FontScale(val label: String, val scale: Float) {
    SMALL("小", 0.90f),
    NORMAL("标准", 1.00f),
    LARGE("大", 1.15f),
    EXTRA_LARGE("特大", 1.30f),
}

/**
 * 应用偏好设置，持久化到系统偏好存储。
 *
 * 使用 JDK 自带的 [Preferences]（Windows 写入注册表，Linux 写入
 * `~/.java/.userPrefs` 或 `~/.config`）：跨平台、无需新增依赖，
 * 且与业务数据（SQLite）分离 —— 用户导出待办时不会把界面偏好一起带出去。
 *
 * 所有读写都做了异常兜底：偏好存储在某些受限环境下可能不可用，
 * 此时回落到内存值，保证功能可用而不崩溃。
 */
object SettingsStore {

    private const val NODE = "space/buercheng/kylintodo"

    private const val KEY_THEME = "themeMode"
    private const val KEY_FONT_SCALE = "fontScale"
    private const val KEY_WIDGET_VISIBLE = "widgetVisibleOnStart"

    private val prefs: Preferences? by lazy {
        runCatching { Preferences.userRoot().node(NODE) }.getOrNull()
    }

    /** 读取当前偏好；任何异常都回落到默认值。 */
    fun load(): AppSettings = AppSettings(
        themeMode = runCatching {
            ThemeMode.valueOf(prefs?.get(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM),
        fontScale = runCatching {
            FontScale.valueOf(prefs?.get(KEY_FONT_SCALE, FontScale.NORMAL.name) ?: FontScale.NORMAL.name)
        }.getOrDefault(FontScale.NORMAL),
        widgetVisibleOnStart = runCatching {
            prefs?.getBoolean(KEY_WIDGET_VISIBLE, false) ?: false
        }.getOrDefault(false),
    )

    /** 保存偏好。失败时静默忽略 —— 界面仍按当前会话的设置工作。 */
    fun save(settings: AppSettings) {
        runCatching {
            prefs?.put(KEY_THEME, settings.themeMode.name)
            prefs?.put(KEY_FONT_SCALE, settings.fontScale.name)
            prefs?.putBoolean(KEY_WIDGET_VISIBLE, settings.widgetVisibleOnStart)
            prefs?.flush()
        }
    }

    /** 供诊断：偏好存储是否真的可用。 */
    fun isPersistent(): Boolean = prefs != null
}

/** 应用偏好数据。 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.NORMAL,
    /** 启动时是否自动打开桌面小窗 */
    val widgetVisibleOnStart: Boolean = false,
)
