package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import space.buercheng.kylintodo.AppInfo
import space.buercheng.kylintodo.data.AutoStartManager
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
import space.buercheng.kylintodo.data.FontScale
import space.buercheng.kylintodo.data.SettingsStore
import space.buercheng.kylintodo.data.ThemeMode
import space.buercheng.kylintodo.data.TodoExporter

/**
 * 设置项的可变状态持有者。
 *
 * 由 Main 持有并驱动主题与字号的实时生效；设置弹窗只负责修改它。
 * 每次修改都会立即写回 [SettingsStore] 持久化。
 */
class SettingsController(initial: space.buercheng.kylintodo.data.AppSettings) {

    /** 界面与窗口显示的名称，用户可自定义。 */
    var appName by mutableStateOf(initial.appName)

    var themeMode by mutableStateOf(initial.themeMode)

    var fontScale by mutableStateOf(initial.fontScale)

    /**
     * 日志文件的展示文本（需求：日志便于排查问题）。
     *
     * 由外部传入而非直接调用 [AppLog]：这样设置界面不必依赖日志实现，
     * 单元测试也能传入固定字符串。
     */
    var logSummary: String = ""

    /** 打开日志所在文件夹 / 清空日志。均为副作用，由外部注入。 */
    var onOpenLogFolder: () -> String = { "" }
    var onClearLog: () -> String = { "" }

    /**
     * 重新读取日志概要。
     *
     * 单独一个回调（而不是复用自己的 logSummary）：清空日志后文件大小变了，
     * 必须重新查询真实值 —— 否则界面会一直显示清空前的旧大小。
     */
    var onReadLogSummary: () -> String = { logSummary }

    var widgetVisibleOnStart by mutableStateOf(initial.widgetVisibleOnStart)

    /** 小窗是否始终置顶（需求 5）。小窗按钮与设置界面都能改。 */
    var widgetPinned by mutableStateOf(initial.widgetPinned)

    /** 主窗口是否始终置顶。 */
    var mainWindowPinned by mutableStateOf(initial.mainWindowPinned)

    /** 开机自启动（需求 6）。改动会同时写入系统自启动项。 */
    var autoStart by mutableStateOf(initial.autoStart)

    /** 主窗口不透明度（需求 4） */
    var mainOpacity by mutableStateOf(initial.mainOpacity)

    /** 小窗不透明度（需求 4） */
    var widgetOpacity by mutableStateOf(initial.widgetOpacity)

    /** 自定义背景色（ARGB），null = 跟随主题 */
    var backgroundColor by mutableStateOf(initial.backgroundColor)

    /** 自定义背景图片路径，null = 无 */
    var backgroundImagePath by mutableStateOf(initial.backgroundImagePath)

    /** 标题字体家族名，null = 默认 */
    var titleFontFamily by mutableStateOf(initial.titleFontFamily)

    /** 正文字体家族名，null = 默认 */
    var bodyFontFamily by mutableStateOf(initial.bodyFontFamily)

    /** 标题栏是否始终纯白 */
    var titleBarAlwaysWhite by mutableStateOf(initial.titleBarAlwaysWhite)

    /**
     * 修改后立即持久化，避免用户忘记保存而丢失设置。
     *
     * 名称会先经 [AppInfo.normalizeName] 规范化：
     * 空白输入回落默认值，超长输入截断 —— 否则标题栏可能变空白或被撑破。
     */
    fun update(
        name: String = appName,
        theme: ThemeMode = themeMode,
        scale: FontScale = fontScale,
        widgetOnStart: Boolean = widgetVisibleOnStart,
        pinned: Boolean = widgetPinned,
        mainPinned: Boolean = mainWindowPinned,
        auto: Boolean = autoStart,
        mainAlpha: Float = mainOpacity,
        widgetAlpha: Float = widgetOpacity,
        bgColor: Long? = backgroundColor,
        bgImage: String? = backgroundImagePath,
        titleFont: String? = titleFontFamily,
        bodyFont: String? = bodyFontFamily,
        titleWhite: Boolean = titleBarAlwaysWhite,
    ) {
        appName = AppInfo.normalizeName(name)
        themeMode = theme
        fontScale = scale
        widgetVisibleOnStart = widgetOnStart
        widgetPinned = pinned
        mainWindowPinned = mainPinned
        autoStart = auto
        // 夹取到合法区间：滑块理论上不会越界，但配置可能被手工改坏
        mainOpacity = mainAlpha.coerceIn(SettingsStore.OPACITY_MIN, 1f)
        widgetOpacity = widgetAlpha.coerceIn(SettingsStore.OPACITY_MIN, 1f)
        backgroundColor = bgColor
        backgroundImagePath = bgImage
        titleFontFamily = titleFont
        bodyFontFamily = bodyFont
        titleBarAlwaysWhite = titleWhite
        SettingsStore.save(
            space.buercheng.kylintodo.data.AppSettings(
                appName = appName,
                themeMode = theme,
                fontScale = scale,
                widgetVisibleOnStart = widgetOnStart,
                widgetPinned = widgetPinned,
                mainWindowPinned = mainWindowPinned,
                autoStart = autoStart,
                mainOpacity = mainOpacity,
                widgetOpacity = widgetOpacity,
                backgroundColor = backgroundColor,
                backgroundImagePath = backgroundImagePath,
                titleFontFamily = titleFontFamily,
                bodyFontFamily = bodyFontFamily,
                titleBarAlwaysWhite = titleBarAlwaysWhite,
            )
        )
    }

    val scaleValue: Float get() = fontScale.scale
}

/**
 * 「标题 + 说明 + 右侧开关」的通用设置行。
 *
 * 抽出来是因为设置项越来越多（置顶、自启动…），逐处复制 Row/Switch
 * 容易出现间距与对齐不一致。
 */
@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * 透明度滑块（需求 4）。
 *
 * 显示百分比而非 0~1 的小数：用户关心的是"有多透明"，
 * 百分比一眼可读，也更便于复述给别人。
 */
@Composable
private fun OpacitySlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(value * 100).roundToInt()}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = SettingsStore.OPACITY_MIN..1f,
            // 分成 20 档（每档 2.5%）：连续拖动没有意义，
            // 分档更容易停在整数百分比上。
            steps = 19,
        )
    }
}

/**
 * 设置菜单（需求反馈第 5 条）。
 *
 * 分四组：外观（皮肤 / 字体大小）、启动行为、数据（导出）、关于。
 * 风格与添加待办弹窗保持一致 —— 用 Dialog + 自定义 Surface，
 * 而不是 AlertDialog 的默认样式。
 */
@Composable
fun SettingsDialog(
    controller: SettingsController,
    /** 执行导出，返回结果文本用于反馈 */
    onExportData: () -> String,
    /** 导出节假日导入模板（Excel），返回结果文本 */
    onExportHolidayTemplate: () -> String,
    /** 导入节假日数据，返回结果文本（含逐行错误说明） */
    onImportHolidays: () -> String,
    /** 清空已导入的节假日数据，回退到内置数据 */
    onClearHolidays: () -> String,
    /** 读取当前已导入数据的概要，用于展示 */
    holidaySummary: () -> String,
    onDismiss: () -> Unit,
) {
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var holidayMessage by remember { mutableStateOf<String?>(null) }
    /** 日志操作的反馈文本 */
    var logMessage by remember { mutableStateOf<String?>(null) }
    /** 清空节假日数据的二次确认 */
    var confirmClearHolidays by remember { mutableStateOf(false) }
    /** 清空日志的二次确认 */
    var confirmClearLog by remember { mutableStateOf(false) }
    /** 开机自启动写入失败时的提示（需求 6） */
    var autoStartMessage by remember { mutableStateOf<String?>(null) }
    // 导入/清空后需要重新读取概要，故用可变状态而不是直接调用
    var holidaySummaryText by remember { mutableStateOf(holidaySummary()) }
    var showAbout by remember { mutableStateOf(false) }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            // 宽高都自适应 + 内容可滚动（需求：窗口较小时设置项不能被裁掉）。
            // 设置项越来越多，固定 520dp 宽、无限高会导致窗口小时
            // 底部按钮（打开日志、清空日志等）被推出窗口外看不到。
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "设置",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                SectionDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 高度放宽到 560dp，让「关于」分组不必滚动即可看到标题
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // ---------------- 外观 ----------------
                    GroupTitle(Icons.Filled.Palette, "外观")

                    SettingLabel("应用名称")
                    AppNameField(
                        value = controller.appName,
                        onValueChange = { controller.update(name = it) },
                    )
                    Text(
                        text = "会同时用于界面左上角与窗口标题，最多 " +
                            "${AppInfo.MAX_NAME_LENGTH} 个字符。留空则恢复默认。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    SettingLabel("皮肤")
                    OptionRow(
                        options = ThemeMode.entries.map { it to it.label },
                        selected = controller.themeMode,
                        onSelect = { controller.update(theme = it) },
                    )

                    SettingLabel("字体大小")
                    OptionRow(
                        options = FontScale.entries.map { it to it.label },
                        selected = controller.fontScale,
                        onSelect = { controller.update(scale = it) },
                    )
                    Text(
                        text = "字号会立即生效；布局比例保持不变。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    SettingLabel("自定义背景颜色")
                    Text(
                        text = "选择后整窗背景变为该颜色（纯色）；「清除」恢复跟随主题。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BackgroundColorPicker(
                        current = controller.backgroundColor,
                        onPick = { controller.update(bgColor = it) },
                        onClear = { controller.update(bgColor = null) },
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    SettingLabel("标题字体 / 正文字体")
                    Text(
                        text = "选择系统已安装的字体；「跟随主题」使用默认字体。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FontPicker(
                        label = "标题字体",
                        current = controller.titleFontFamily,
                        onPick = { controller.update(titleFont = it) },
                    )
                    FontPicker(
                        label = "正文字体",
                        current = controller.bodyFontFamily,
                        onPick = { controller.update(bodyFont = it) },
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    SettingSwitchRow(
                        title = "标题栏始终纯白",
                        subtitle = "无论皮肤是浅色还是深色，顶部标题栏都用纯白背景",
                        checked = controller.titleBarAlwaysWhite,
                        onCheckedChange = { controller.update(titleWhite = it) },
                    )

                    // ---------------- 启动行为 ----------------
                    SectionDivider()
                    GroupTitle(Icons.Filled.Info, "启动行为")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启动时打开桌面小窗",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "开启后每次启动都会显示置顶小窗",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = controller.widgetVisibleOnStart,
                            onCheckedChange = { controller.update(widgetOnStart = it) },
                        )
                    }

                    // 小窗置顶（需求 5）：也可直接点小窗上的星形按钮切换
                    SettingSwitchRow(
                        title = "桌面小窗始终置顶",
                        subtitle = "关闭后小窗会被其他窗口遮挡（也可点小窗上的星形按钮切换）",
                        checked = controller.widgetPinned,
                        onCheckedChange = { controller.update(pinned = it) },
                    )

                    // 开机自启动（需求 6）
                    SettingSwitchRow(
                        title = "开机自动启动",
                        subtitle = if (AutoStartManager.isSupported()) {
                            "登录系统后自动启动本应用（写入系统自启动项）"
                        } else {
                            "当前环境不支持自动配置，请手动添加到系统自启动"
                        },
                        checked = controller.autoStart,
                        enabled = AutoStartManager.isSupported(),
                        onCheckedChange = { want ->
                            // 先尝试写系统自启动项，成功后才更新设置。
                            // 反过来的话，写入失败会让界面显示"已开启"而实际不生效。
                            val message = AutoStartManager.apply(want)
                            if (message == null) {
                                controller.update(auto = want)
                            } else {
                                autoStartMessage = message
                            }
                        },
                    )
                    autoStartMessage?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    // ---------------- 窗口透明度（需求 4） ----------------
                    SectionDivider()
                    GroupTitle(Icons.Filled.Settings, "窗口透明度")
                    OpacitySlider(
                        title = "主窗口",
                        value = controller.mainOpacity,
                        onValueChange = { controller.update(mainAlpha = it) },
                    )
                    OpacitySlider(
                        title = "桌面小窗",
                        value = controller.widgetOpacity,
                        onValueChange = { controller.update(widgetAlpha = it) },
                    )
                    Text(
                        text = "最低 ${(SettingsStore.OPACITY_MIN * 100).toInt()}%；" +
                            "过透明会导致内容难以辨认，因此不提供更低值",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // ---------------- 数据 ----------------
                    SectionDivider()
                    GroupTitle(Icons.Filled.Download, "数据")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "导出待办数据",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "同时导出 JSON（备份用）与 CSV（表格查看用）",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { exportMessage = onExportData() }) {
                            Text("导出", fontSize = 13.sp)
                        }
                    }
                    exportMessage?.let { msg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        ) {
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }

                    // ---------------- 节假日数据 ----------------
                    SectionDivider()
                    GroupTitle(Icons.Filled.DateRange, "节假日数据")

                    // 状态说明：让用户随时知道"现在用的是内置还是导入的"
                    Text(
                        text = "当前数据：$holidaySummaryText",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "内置数据有固定覆盖范围，超出后该日期不再显示「休 / 班」。" +
                            "可导出模板填写后导入，导入的数据优先于内置数据。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { holidayMessage = onExportHolidayTemplate() },
                        ) {
                            Text("导出模板", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                holidayMessage = onImportHolidays()
                                holidaySummaryText = holidaySummary()
                            },
                        ) {
                            Text("导入数据", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = { confirmClearHolidays = true },
                        ) {
                            Text("清空", fontSize = 12.sp)
                        }
                    }
                    if (confirmClearHolidays) {
                        ConfirmDialog(
                            title = "确认清空",
                            message = "将删除所有已导入的节假日数据，回退到内置数据。" +
                                "此操作无法撤销（但可重新导入模板恢复）。",
                            confirmText = "清空",
                            destructive = true,
                            onConfirm = {
                                confirmClearHolidays = false
                                holidayMessage = onClearHolidays()
                                holidaySummaryText = holidaySummary()
                            },
                            onDismiss = { confirmClearHolidays = false },
                        )
                    }

                    holidayMessage?.let { msg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        ) {
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }

                    // ---------------- 关于 ----------------
                    SectionDivider()
                    GroupTitle(Icons.Filled.Info, "关于")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppInfo.DEFAULT_DISPLAY_NAME,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "版本 ${AppInfo.VERSION}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showAbout = true }) { Text("查看详情") }
                    }
                }

                // ---------------- 运行日志 ----------------
                SectionDivider()
                GroupTitle(Icons.Filled.Info, "运行日志")
                Text(
                    text = controller.logSummary,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "出问题时把这个日志文件发给我们即可定位。" +
                        "日志超过 2 MB 会自动丢弃最早的内容，不会无限增长。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { logMessage = controller.onOpenLogFolder() },
                    ) {
                        Text("打开日志位置", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { confirmClearLog = true },
                    ) {
                        Text("清空日志", fontSize = 12.sp)
                    }
                }
                if (confirmClearLog) {
                    ConfirmDialog(
                        title = "确认清空日志",
                        message = "将删除当前日志文件。若正准备反馈问题，" +
                            "建议先复制或发送日志再清空。",
                        confirmText = "清空",
                        destructive = true,
                        onConfirm = {
                            confirmClearLog = false
                            logMessage = controller.onClearLog()
                            // 重新读取概要：清空后文件大小已变，不刷新会显示旧值
                            controller.logSummary = controller.onReadLogSummary()
                        },
                        onDismiss = { confirmClearLog = false },
                    )
                }
                logMessage?.let { msg ->
                    Text(
                        text = msg,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onDismiss) { Text("完成") }
                }
            }
        }
    }
}

/** 关于弹窗：显示版本、环境与数据目录，便于用户回报问题。 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val info = remember { AppInfo.runtimeInfo(space.buercheng.kylintodo.data.AppPaths.dataDirectory().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppInfo.DEFAULT_DISPLAY_NAME, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = AppInfo.DESCRIPTION,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Text(
                    text = info,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "更新与反馈",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "GitHub 项目主页：https://github.com/zerohk/KylinTodo\n" +
                        "联系邮箱：forlovelygirlfyt@gmail.com",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/**
 * 应用名称输入框。
 *
 * 输入即时生效（边输边改界面左上角与窗口标题），让用户直接看到结果，
 * 比"改完还要点保存"更直观。
 */
@Composable
private fun AppNameField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.SETTINGS_APP_NAME),
        placeholder = {
            Text(
                text = AppInfo.DEFAULT_DISPLAY_NAME,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
    )
}

/** 分组标题：图标 + 文字，与添加待办弹窗的分区标题风格一致。 */
@Composable
private fun GroupTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** 一组互斥选项，用圆角块呈现；选中项以主色描边与浅底突出。 */
@Composable
private fun <T> OptionRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val scheme = MaterialTheme.colorScheme
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) scheme.primary.copy(alpha = 0.16f)
                        else scheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) scheme.primary else scheme.outline.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickableNoRipple { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    )
}

/** 预设背景色：一行可点的色块，最后一格是「清除」。 */
@Composable
private fun BackgroundColorPicker(
    current: Long?,
    onPick: (Long) -> Unit,
    onClear: () -> Unit,
) {
    // 一组柔和的浅色背景色，兼顾浅色/深色主题下文字可读性。
    val presets = listOf(
        "默认" to null,
        "米白" to 0xFFF7F3E8,
        "浅蓝" to 0xFFEAF2FA,
        "浅绿" to 0xFFEAF6EC,
        "浅粉" to 0xFFFAECEE,
        "浅灰" to 0xFFF0F1F3,
        "纯白" to 0xFFFFFFFF,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { (label, argb) ->
            val selected = if (argb == null) current == null else current == argb
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (argb == null) Color(0xFFE5E7EB) else Color(argb))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = CircleShape,
                        )
                        .clickableNoRipple {
                            if (argb == null) onClear() else onPick(argb)
                        },
                )
                Text(text = label, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * 字体选择器：下拉框，列出**全部**系统已安装字体。
 *
 * 注意：系统字体名来自 AWT 探测（`GraphicsEnvironment`），可能与 Compose/Skia
 * 实际可渲染的字体不完全一致；解析失败会静默回退默认字体，不会崩溃。
 */
@Composable
private fun FontPicker(
    label: String,
    current: String?,
    onPick: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // 读取全部系统字体（复用 KylinTheme 暴露的探测结果），按名称排序。
    // 「跟随主题」作为第一项（null）。
    val fonts = remember { availableSystemFontFamilies.toList().sorted() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = current ?: "跟随主题",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                // 高度受限 + 可滚动，避免系统字体过多时菜单溢出屏幕
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("跟随主题", fontSize = 13.sp) },
                    onClick = { onPick(null); expanded = false },
                )
                fonts.forEach { family ->
                    DropdownMenuItem(
                        text = { Text(family, fontSize = 13.sp) },
                        onClick = { onPick(family); expanded = false },
                    )
                }
            }
        }
    }
}
