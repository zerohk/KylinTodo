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
import space.buercheng.kylintodo.ui.AddTodoDialog
import space.buercheng.kylintodo.ui.CalendarScreen
import space.buercheng.kylintodo.ui.AppViewModel
import space.buercheng.kylintodo.ui.KylinTodoTheme
import space.buercheng.kylintodo.ui.configureFontRendering
import space.buercheng.kylintodo.ui.selectedChineseFontName

/**
 * 应用入口。
 *
 * 注意 `mainClass` 在 `build.gradle.kts` 中配置为
 * `space.buercheng.kylintodo.MainKt`，与文件位置保持一致。
 */
fun main() {
    // 必须在任何 AWT / Skia 字体对象创建之前设置，用于改善 Linux 下的
    // 中文抗锯齿表现（需求 4.1 要求解决字体发虚问题）。
    configureFontRendering()

    println("[KylinTodo] 数据目录: ${AppPaths.dataDirectory()}")
    println("[KylinTodo] 中文字体: $selectedChineseFontName")

    application {
        // 数据库在整个应用生命周期内保持打开（需求 F-06：自动保存、自动加载）
        val repository = remember { SqliteTodoRepository(AppPaths.databaseFile()) }
        val viewModel = remember { AppViewModel(repository) }

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
        ) {
            KylinTodoTheme {
                CalendarScreen(viewModel = viewModel)

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
