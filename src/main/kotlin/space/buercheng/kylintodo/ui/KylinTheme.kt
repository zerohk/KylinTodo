package space.buercheng.kylintodo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import space.buercheng.kylintodo.data.ThemeMode

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

/**
 * 调休「班」角标颜色 —— 绿色。
 *
 * 原先用中性灰蓝，但灰色与禁用态视觉上难以区分，
 * 绿色能明确表达"这天虽然看着像周末，实际要上班"。
 */
val WorkdayGreen = Color(0xFF2E7D32)

/** 兼容旧命名，避免遗漏引用点。 */
val WorkdayGray = WorkdayGreen

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

/**
 * 按字号倍率构建 Typography。
 *
 * 不用 `by lazy` 缓存：字号可在设置里切换，必须随倍率重建。
 * 倍率只作用于字号，行高随之按比例放大，保持既有布局比例不变。
 */
private fun buildTypographyScaled(family: FontFamily, scale: Float): Typography {
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    val base = Typography()

    fun TextStyle.scaled(): TextStyle = copy(
        fontFamily = family,
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )

    return Typography(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled(),
    )
}

/**
 * 当前字号倍率。
 *
 * 日历格子等处使用的是硬编码字号的 [TextStyle]（而非 Material 的
 * typography），它们需要自己读取倍率才能跟随设置缩放，因此通过
 * CompositionLocal 下发。
 */
val LocalFontScale = staticCompositionLocalOf { 1.0f }

/**
 * 日历格子专用字号：公历日期稍大，农历/节气更小以避免挤压。
 *
 * 是 `@Composable` 的取值函数而非常量：字号需随设置里的字号档位缩放。
 */
@Composable
fun gregorianDayTextStyle(): TextStyle = TextStyle(
    fontFamily = chineseFontFamily,
    fontSize = 15.sp * LocalFontScale.current,
    fontWeight = FontWeight.Medium,
)

@Composable
fun subLabelTextStyle(): TextStyle = TextStyle(
    fontFamily = chineseFontFamily,
    fontSize = 10.sp * LocalFontScale.current,
    fontWeight = FontWeight.Normal,
)

/**
 * 应用主题。
 *
 * @param mode 外观模式（跟随系统 / 浅色 / 深色），对应设置里的「皮肤」
 * @param fontScale 字号倍率，对应设置里的「字体大小」
 */
@Composable
@OptIn(ExperimentalTextApi::class)
fun KylinTodoTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    fontScale: Float = 1.0f,
    titleFontFamily: String? = null,
    bodyFontFamily: String? = null,
    backgroundColor: Long? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 解析用户选择的字体名。FontFamily(String) 是实验性 API（@ExperimentalTextApi），
    // 但它是"按系统字体名构造"的唯一公开途径；解析失败时回退默认无衬线。
    val titleFamily = remember(titleFontFamily) {
        titleFontFamily?.let { FontFamily(it) } ?: chineseFontFamily
    }
    val bodyFamily = remember(bodyFontFamily) {
        bodyFontFamily?.let { FontFamily(it) } ?: chineseFontFamily
    }

    // 标题用较大字号 + Medium 字重；正文用常规字号。
    // 两者共用同一 Typography，但标题族与正文族分别注入，
    // 由 buildTypographyScaled 生成两套并合并。
    val typography = remember(fontScale, titleFamily, bodyFamily) {
        buildTypographyScaled(bodyFamily, fontScale).let { body ->
            body.copy(
                headlineMedium = body.headlineMedium.copy(fontFamily = titleFamily),
                titleLarge = body.titleLarge.copy(fontFamily = titleFamily),
                titleMedium = body.titleMedium.copy(fontFamily = titleFamily),
            )
        }
    }

    val baseColors = if (dark) DarkColors else LightColors
    // 自定义背景色：仅替换 background 与 surface（背景层），
    // 保留 onSurface 等前景色不变，保证文字始终可读。
    // 存储的 Long 是完整 ARGB 值，直接交给 Color(Long) 构造器即可。
    val colors = remember(backgroundColor, dark) {
        backgroundColor?.let { argb -> baseColors.copy(background = Color(argb), surface = Color(argb)) }
            ?: baseColors
    }

    CompositionLocalProvider(LocalFontScale provides fontScale) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            content = content,
        )
    }
}
