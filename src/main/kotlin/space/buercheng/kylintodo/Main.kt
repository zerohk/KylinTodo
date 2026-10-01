package space.buercheng.kylintodo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import space.buercheng.kylintodo.data.AppPaths
import space.buercheng.kylintodo.data.SqliteTodoRepository
import space.buercheng.kylintodo.domain.CalendarViewMode
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.ui.AddTodoDialog
import space.buercheng.kylintodo.ui.CalendarScreen
import space.buercheng.kylintodo.ui.DayInfoDialog
import space.buercheng.kylintodo.ui.AppViewModel
import space.buercheng.kylintodo.ui.KylinTodoTheme
import space.buercheng.kylintodo.ui.configureFontRendering
import space.buercheng.kylintodo.ui.selectedChineseFontName
import java.time.LocalDate

/**
 * 应用入口。
 *
 * 注意 `mainClass` 在 `build.gradle.kts` 中配置为
 * `space.buercheng.kylintodo.MainKt`，与文件位置保持一致。
 *
 * 支持的命令行参数（用于开发期验证，不影响正常使用）：
 *  - `--view=day|week|month`：指定启动时的视图模式
 *  - `--date=YYYY-MM-DD`：指定启动时的锚定日期
 */
fun main(args: Array<String>) {
    // 必须在任何 AWT / Skia 字体对象创建之前设置，用于改善 Linux 下的
    // 中文抗锯齿表现（需求 4.1 要求解决字体发虚问题）。
    configureFontRendering()

    val options = parseArgs(args)

    println("[KylinTodo] 数据目录: ${AppPaths.dataDirectory()}")
    println("[KylinTodo] 中文字体: $selectedChineseFontName")
    options.initialView?.let { println("[KylinTodo] 启动视图: $it") }
    options.initialDate?.let { println("[KylinTodo] 锚定日期: $it") }

    application {
        // 数据库在整个应用生命周期内保持打开（需求 F-06：自动保存、自动加载）
        val repository = remember { SqliteTodoRepository(AppPaths.databaseFile()) }
        val viewModel = remember {
            // 端到端验证用：插入一条种子待办后，正常关闭再启动应能自动加载
            options.seedTodo?.let { (text, date) ->
                TodoItem.createOrNull(text, date)?.let { repository.insert(it) }
            }
            AppViewModel(
                repository = repository,
                todayProvider = { options.initialDate ?: LocalDate.now() },
                initialViewMode = options.initialView ?: CalendarViewMode.MONTH,
            )
        }

        // 窗口关闭时释放数据库连接，确保数据落盘
        DisposableEffect(Unit) {
            onDispose { repository.close() }
        }

        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 820.dp),
            position = WindowPosition(androidx.compose.ui.Alignment.Center),
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "麒麟日历 · KylinTodo",
            state = windowState,
            // 允许用鼠标拖拽边框缩放。Compose Desktop 默认即为 true，
            // 这里显式写出以免后续误改。
            //
            // 注意：Compose 1.7.3 的 WindowState 没有 minimumSize 属性
            // （javap 确认只有 placement / minimized / position / size），
            // 因此不设最小尺寸。布局本身是全自适应的 —— 日历列用 weight(1f)
            // 吸收多余宽度，侧栏固定 300dp，行高按可用高度除以 6 计算，
            // 窗口拉大拉小都不会出现滚动条或错位。
            resizable = true,
        ) {
            KylinTodoTheme {
                CalendarScreen(viewModel = viewModel)

                // 日期详情弹窗：双击日历中的某一天弹出，
                // 显示该日已添加的待办，并可继续添加（双击弹窗或点「+」）
                viewModel.dayInfoDate?.let { infoDate ->
                    DayInfoDialog(
                        selectedDate = infoDate,
                        weekDays = viewModel.dayInfoWeekDays,
                        todosOfSelectedDate = viewModel.dayInfoTodos,
                        todosOfOtherDays = viewModel.dayInfoOtherTodos,
                        onDismiss = viewModel::dismissDayInfo,
                        onToggleTodo = viewModel::toggleCompleted,
                        onDeleteTodo = viewModel::deleteTodo,
                        onAddTodoForDate = viewModel::openAddTodo,
                    )
                }

                // 添加待办弹窗：目标日期来自格子的「+」、工具栏或侧栏入口
                viewModel.addTodoTargetDate?.let { targetDate ->
                    AddTodoDialog(
                        date = targetDate,
                        onDismiss = viewModel::dismissAddTodo,
                        onConfirm = { text -> viewModel.addTodo(text, targetDate) },
                    )
                }
            }
        }
    }
}

/** 命令行选项。 */
private data class LaunchOptions(
    val initialView: CalendarViewMode? = null,
    val initialDate: LocalDate? = null,
    /** 调试用：启动时插入一条待办，便于验证持久化与自动加载 */
    val seedTodo: Pair<String, LocalDate>? = null,
)

/**
 * 解析命令行参数。
 *
 * 非法值直接忽略并回落默认行为，避免开发期笔误导致应用无法启动。
 */
private fun parseArgs(args: Array<String>): LaunchOptions {
    var view: CalendarViewMode? = null
    var date: LocalDate? = null
    var seed: Pair<String, LocalDate>? = null

    args.forEach { arg ->
        when {
            arg.startsWith("--view=") -> {
                view = when (arg.removePrefix("--view=").lowercase()) {
                    "day", "d" -> CalendarViewMode.DAY
                    "week", "w" -> CalendarViewMode.WEEK
                    "month", "m" -> CalendarViewMode.MONTH
                    else -> null
                }
            }

            arg.startsWith("--date=") -> {
                date = runCatching { LocalDate.parse(arg.removePrefix("--date=")) }.getOrNull()
            }

            // --seed=文本@YYYY-MM-DD   （日期可省略，默认锚定日期）
            arg.startsWith("--seed=") -> {
                val body = arg.removePrefix("--seed=")
                val at = body.lastIndexOf('@')
                val text = if (at >= 0) body.substring(0, at) else body
                val d = if (at >= 0) {
                    runCatching { LocalDate.parse(body.substring(at + 1)) }.getOrNull()
                } else {
                    null
                }
                seed = text to (d ?: date ?: LocalDate.now())
            }
        }
    }
    return LaunchOptions(view, date, seed)
}
