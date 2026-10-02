package space.buercheng.kylintodo.data

import space.buercheng.kylintodo.AppInfo
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
    private const val KEY_APP_NAME = "appDisplayName"
    private const val KEY_WIDGET_PINNED = "widgetPinned"
    private const val KEY_AUTO_START = "autoStart"
    private const val KEY_MAIN_OPACITY = "mainOpacity"
    private const val KEY_WIDGET_OPACITY = "widgetOpacity"

    private val prefs: Preferences? by lazy {
        runCatching { Preferences.userRoot().node(NODE) }.getOrNull()
    }

    /** 读取当前偏好；任何异常都回落到默认值。 */
    fun load(): AppSettings = AppSettings(
        appName = AppInfo.normalizeName(
            runCatching { prefs?.get(KEY_APP_NAME, null) }.getOrNull(),
        ),
        themeMode = runCatching {
            ThemeMode.valueOf(prefs?.get(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM),
        fontScale = runCatching {
            FontScale.valueOf(prefs?.get(KEY_FONT_SCALE, FontScale.NORMAL.name) ?: FontScale.NORMAL.name)
        }.getOrDefault(FontScale.NORMAL),
        widgetVisibleOnStart = runCatching {
            prefs?.getBoolean(KEY_WIDGET_VISIBLE, false) ?: false
        }.getOrDefault(false),
        widgetPinned = runCatching {
            prefs?.getBoolean(KEY_WIDGET_PINNED, true) ?: true
        }.getOrDefault(true),
        autoStart = runCatching {
            prefs?.getBoolean(KEY_AUTO_START, false) ?: false
        }.getOrDefault(false),
        // 不透明度用字符串存：Preferences 只有 double，而 Float 转 double
        // 往返会有精度噪声（0.95f -> 0.949999988...），显示时会出现
        // "94.999%"这类难看的数字。
        mainOpacity = runCatching {
            prefs?.get(KEY_MAIN_OPACITY, null)?.toFloatOrNull() ?: 1f
        }.getOrDefault(1f).coerceIn(OPACITY_MIN, 1f),
        widgetOpacity = runCatching {
            prefs?.get(KEY_WIDGET_OPACITY, null)?.toFloatOrNull() ?: DEFAULT_WIDGET_OPACITY
        }.getOrDefault(DEFAULT_WIDGET_OPACITY).coerceIn(OPACITY_MIN, 1f),
    )

    /** 保存偏好。失败时静默忽略 —— 界面仍按当前会话的设置工作。 */
    fun save(settings: AppSettings) {
        runCatching {
            prefs?.put(KEY_APP_NAME, settings.appName)
            prefs?.put(KEY_THEME, settings.themeMode.name)
            prefs?.put(KEY_FONT_SCALE, settings.fontScale.name)
            prefs?.putBoolean(KEY_WIDGET_VISIBLE, settings.widgetVisibleOnStart)
            prefs?.putBoolean(KEY_WIDGET_PINNED, settings.widgetPinned)
            prefs?.putBoolean(KEY_AUTO_START, settings.autoStart)
            prefs?.put(KEY_MAIN_OPACITY, settings.mainOpacity.toString())
            prefs?.put(KEY_WIDGET_OPACITY, settings.widgetOpacity.toString())
            prefs?.flush()
        }
    }

    /**
     * 不透明度的下限。
     *
     * 不放到 0：窗口太透明会让用户"看不见也点不到"，属于把自己关在门外。
     * 0.5 已经足够淡，同时保证内容始终可辨、可操作。
     */
    const val OPACITY_MIN = 0.5f

    /** 小窗默认略透明：它常驻桌面，全不透明会显得很突兀。 */
    const val DEFAULT_WIDGET_OPACITY = 0.95f

    /** 供诊断：偏好存储是否真的可用。 */
    fun isPersistent(): Boolean = prefs != null
}

/** 应用偏好数据。 */
data class AppSettings(
    /** 界面与窗口显示的名称，用户可自定义 */
    val appName: String = AppInfo.DEFAULT_DISPLAY_NAME,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.NORMAL,
    /** 启动时是否自动打开桌面小窗 */
    val widgetVisibleOnStart: Boolean = false,
    /** 桌面小窗是否始终置顶（需求 5） */
    val widgetPinned: Boolean = true,
    /** 开机自启动（需求 6） */
    val autoStart: Boolean = false,
    /** 主窗口不透明度（需求 4），1.0 = 完全不透明 */
    val mainOpacity: Float = 1f,
    /** 小窗不透明度（需求 4） */
    val widgetOpacity: Float = SettingsStore.DEFAULT_WIDGET_OPACITY,
)
