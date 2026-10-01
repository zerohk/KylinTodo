package space.buercheng.kylintodo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 麒麟蓝主色。
 *
 * 需求 4.3 要求「主色调建议适配 Kylin 系统的麒麟蓝」。
 * 银河麒麟 V10 的品牌蓝约为 #1B6FC4 一系，这里取一组同色相变体
 * 以构成完整的 Material 3 色调。
 */
private val KylinBlue = Color(0xFF1B6FC4)
private val KylinBlueDark = Color(0xFF0B4C8C)
private val KylinBlueLight = Color(0xFFD3E4F7)
private val KylinBlueContainer = Color(0xFFA8CCF0)

/** 节假日「休」角标颜色 —— 中国日历惯例用红色表示休息日。 */
val HolidayRed = Color(0xFFD32F2F)

/** 调休「班」角标颜色 —— 用中性灰蓝区别于放假。 */
val WorkdayGray = Color(0xFF5F6B7A)

/** 节气文字颜色。 */
val SolarTermGreen = Color(0xFF2E7D32)

/** 农历文字颜色（弱化处理，避免与公历日期抢视觉层级）。 */
private val LightLunarText = Color(0xFF6B7280)
private val DarkLunarText = Color(0xFF9CA3AF)

val LunarTextLight: Color = LightLunarText
val LunarTextDark: Color = DarkLunarText

private val LightColors = lightColorScheme(
    primary = KylinBlue,
    onPrimary = Color.White,
    primaryContainer = KylinBlueLight,
    onPrimaryContainer = KylinBlueDark,
    secondary = KylinBlueDark,
    surface = Color.White,
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF4B5563),
    background = Color(0xFFFAFBFC),
    outline = Color(0xFFD1D5DB),
    error = HolidayRed,
)

private val DarkColors = darkColorScheme(
    primary = KylinBlueContainer,
    onPrimary = KylinBlueDark,
    primaryContainer = KylinBlueDark,
    onPrimaryContainer = KylinBlueLight,
    surface = Color(0xFF1F2937),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = Color(0xFFD1D5DB),
    background = Color(0xFF111827),
    outline = Color(0xFF4B5563),
    error = Color(0xFFEF5350),
)

/**
 * 中文字体族。
 *
 * 需求 4.1 明确要求「解决 Linux 环境下 Java/Compose 默认的字体发虚问题」。
 * Compose Desktop 在 Linux 下若拿到点阵或位图回退字体会发虚，因此优先
 * 指定各平台上成熟的中文矢量字体：
 *
 *  - 麒麟 / 银河麒麟 V10：`Noto Sans CJK SC` 或 `Source Han Sans CN`（系统自带）
 *  - Windows：`Microsoft YaHei UI` / `Microsoft YaHei`
 *
 * 若系统均无匹配字体，Compose 会回落到平台默认字体，此时至少保证不崩溃。
 * 真机字体清晰度仍需在麒麟系统上实测确认，见 docs 中的验收清单。
 */
private val chineseFontCandidates = listOf(
    "Noto Sans CJK SC",
    "Source Han Sans CN",
    "Source Han Sans SC",
    "WenQuanYi Micro Hei",
    "Microsoft YaHei UI",
    "Microsoft YaHei",
    "PingFang SC",
    "SimHei",
)

/**
 * 探测系统中实际可用的中文字体名。
 *
 * 注意：Compose Multiplatform 并未提供「按系统字体名构造 FontFamily」的公开 API
 * （`FontFamily(String)` 是实验性的内部构造器，不应依赖）。因此这里探测出的
 * 字体名**仅用于诊断输出**，实际渲染使用 [FontFamily.SansSerif]。
 *
 * 在 Windows 与麒麟系统上，SansSerif 会映射到系统无衬线字体（微软雅黑 /
 * Noto Sans CJK / 文泉驿），Skia 会自动完成 CJK 字形回退。
 */
private val availableFontFamilies: Set<String> by lazy {
    runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
            .availableFontFamilyNames
            .toSet()
    }.getOrDefault(emptySet())
}

/** 当前探测到的中文字体名，便于在麒麟真机上排查字体问题。 */
val selectedChineseFontName: String by lazy {
    chineseFontCandidates.firstOrNull { it in availableFontFamilies }
        ?: "（系统默认无衬线字体）"
}

/**
 * 中文字体族。
 *
 * 需求 4.1 要求解决 Linux 下 Compose 默认字体发虚问题。该问题的根因在
 * Skia 的字体栅格化与字形回退，而非字体族选择，因此通过通用族
 * [FontFamily.SansSerif] 交由系统解析，并在应用启动时设置渲染属性
 * （见 `configureFontRendering`）。
 */
private val chineseFontFamily: FontFamily = FontFamily.SansSerif

/**
 * 设置字体渲染相关的 JVM 属性。
 *
 * 在 Linux（含银河麒麟）上，Java2D 默认可能关闭抗锯齿，导致中文发虚。
 * 必须在任何 AWT/Skia 字体对象创建**之前**调用，因此于 `main` 最开始执行。
 */
fun configureFontRendering() {
    System.setProperty("awt.useSystemAAFontSettings", "on")
    System.setProperty("swing.aatext", "true")
    System.setProperty("sun.java2d.uiScale.enabled", "true")
}

private fun buildTypography(): Typography {
    val family = chineseFontFamily
    // Material 3 的 Typography() 构造器标注为实验性 API，需显式 opt-in
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

private val AppTypography: Typography by lazy { buildTypography() }

/** 日历格子专用字号：公历日期稍大，农历/节气更小以避免挤压。 */
val GregorianDayTextStyle = TextStyle(
    fontFamily = chineseFontFamily,
    fontSize = 15.sp,
    fontWeight = FontWeight.Medium,
)

val SubLabelTextStyle = TextStyle(
    fontFamily = chineseFontFamily,
    fontSize = 10.sp,
    fontWeight = FontWeight.Normal,
)

@Composable
fun KylinTodoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
