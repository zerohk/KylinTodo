package space.buercheng.kylintodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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

    var widgetVisibleOnStart by mutableStateOf(initial.widgetVisibleOnStart)

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
    ) {
        appName = AppInfo.normalizeName(name)
        themeMode = theme
        fontScale = scale
        widgetVisibleOnStart = widgetOnStart
        SettingsStore.save(
            space.buercheng.kylintodo.data.AppSettings(
                appName = appName,
                themeMode = theme,
                fontScale = scale,
                widgetVisibleOnStart = widgetOnStart,
            )
        )
    }

    val scaleValue: Float get() = fontScale.scale
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
    // 导入/清空后需要重新读取概要，故用可变状态而不是直接调用
    var holidaySummaryText by remember { mutableStateOf(holidaySummary()) }
    var showAbout by remember { mutableStateOf(false) }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(520.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
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
                            onClick = {
                                holidayMessage = onClearHolidays()
                                holidaySummaryText = holidaySummary()
                            },
                        ) {
                            Text("清空", fontSize = 12.sp)
                        }
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
