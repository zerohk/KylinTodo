package space.buercheng.kylintodo.ui

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPainter
import java.awt.image.BufferedImage
import java.util.logging.Logger
import javax.imageio.ImageIO

/**
 * 应用图标加载。
 *
 * 图标由 `scripts/generate_icon.py` 程序化生成并放在
 * `src/main/resources/icon/` 下，因此同时存在于类路径与打包产物中。
 *
 * 刻意使用 `ImageIO` + 类路径流，而不是 Compose 的 `useResource` ——
 * 后者在 Compose 1.7 已标注弃用（官方建议迁移到新的资源库），
 * 而这里只需要读取普通 PNG，用 JDK 自带能力即可，依赖更少。
 *
 * 类型说明：`ImageIO.read` 返回 [BufferedImage]，而 Compose 桌面端为其提供了
 * `toPainter()` 扩展（见 DesktopImageConverters），因此无需经过
 * `toComposeImageBitmap` 再手工包装。
 */
object AppIcon {

    private val LOG: Logger = Logger.getLogger(AppIcon::class.java.name)

    /** 类路径中的图标路径，与 generate_icon.py 的输出保持一致。 */
    const val PRIMARY_RESOURCE = "/icon/dazhi-calendar.png"

    /** 构建时生成的全部尺寸。系统会按显示场景挑选合适的一张。 */
    private val ALL_SIZES = listOf(512, 256, 128, 64, 48, 32, 16)

    private fun loadImage(path: String): BufferedImage? = runCatching {
        AppIcon::class.java.getResourceAsStream(path)?.use { ImageIO.read(it) }
    }.onFailure {
        LOG.warning("读取图标失败（$path）: ${it.message}")
    }.getOrNull()

    /** 主图标（512x512），AWT 形式。 */
    val awtImage: BufferedImage? by lazy { loadImage(PRIMARY_RESOURCE) }

    /** Compose Painter 版本，供 Window(icon = ...) 使用。 */
    val painter: Painter? by lazy {
        awtImage?.let { runCatching { it.toPainter() }.getOrNull() }
    }

    /**
     * 把图标应用到 AWT 窗口。
     *
     * 同时设置多个尺寸：任务栏与窗口角标会各自挑选合适的分辨率，
     * 只设一张 512x512 会让小尺寸显示发虚。
     *
     * 注意 API 细节：`iconImages` 属于 [java.awt.Frame]，接受 **List**；
     * `java.awt.Window` 本身没有该属性，因此对非 Frame 的窗口（小窗用的
     * 无边框窗口）跳过设置，图标由操作系统按桌面项提供。
     */
    fun applyTo(window: java.awt.Window) {
        if (window !is java.awt.Frame) return
        val images = ALL_SIZES.mapNotNull { s ->
            loadImage(if (s == 512) PRIMARY_RESOURCE else "/icon/dazhi-calendar-$s.png")
        }
        if (images.isEmpty()) return
        runCatching { window.iconImages = images }
            .onFailure { LOG.warning("设置窗口图标失败: ${it.message}") }
    }

    /** 供诊断：确认图标是否真的加载到了。 */
    fun describe(): String =
        awtImage?.let { "图标已加载 ${it.width}x${it.height}" } ?: "图标未加载"
}
