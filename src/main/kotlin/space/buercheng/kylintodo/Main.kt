package space.buercheng.kylintodo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.buercheng.kylintodo.AppInfo
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import space.buercheng.kylintodo.data.AppLog
import space.buercheng.kylintodo.data.AppPaths
import space.buercheng.kylintodo.data.DesktopDataExporter
import space.buercheng.kylintodo.data.DesktopHolidayTransfer
import space.buercheng.kylintodo.data.SingleInstanceGuard
import space.buercheng.kylintodo.data.SqliteTodoRepository
import space.buercheng.kylintodo.domain.TodoPriority
import space.buercheng.kylintodo.domain.CalendarViewMode
import space.buercheng.kylintodo.domain.TodoItem
import space.buercheng.kylintodo.ui.AddTodoDialog
import space.buercheng.kylintodo.ui.AppViewModel
import space.buercheng.kylintodo.data.SettingsStore
import space.buercheng.kylintodo.ui.SettingsController
import space.buercheng.kylintodo.ui.SearchDialog
import space.buercheng.kylintodo.ui.SettingsDialog
import space.buercheng.kylintodo.ui.AppIcon
import space.buercheng.kylintodo.ui.CalendarScreen
import space.buercheng.kylintodo.ui.ClickProbeSupport
import space.buercheng.kylintodo.ui.DayInfoDialog
import space.buercheng.kylintodo.ui.DesktopWidgetScreen
import space.buercheng.kylintodo.ui.rememberDebugOverlayState
import space.buercheng.kylintodo.ui.handleDebugShortcut
import androidx.compose.runtime.CompositionLocalProvider
import space.buercheng.kylintodo.ui.ActionLog
import space.buercheng.kylintodo.ui.LocalActionLog
import space.buercheng.kylintodo.ui.DebugStatusBar
import space.buercheng.kylintodo.ui.KylinTodoTheme
import space.buercheng.kylintodo.ui.configureFontRendering
import space.buercheng.kylintodo.ui.nextWindowPosition
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

/**
 * 启动阶段的状态（需求 7）。
 *
 * 三态而非布尔：失败时要能把原因显示给用户，
 * 否则用户只看到"启动失败"三个字，无从判断该做什么。
 */
private sealed interface BootState {
    data object Loading : BootState
    data class Ready(val repository: space.buercheng.kylintodo.domain.TodoRepository) : BootState
    data class Failed(val reason: String) : BootState
}

/**
 * 启动画面的内容（需求 7）。
 *
 * ## 为什么值得做
 * 数据库连接与首屏装载需要时间。此前这段时间是**没有窗口的空白** ——
 * 用户以为启动失败而重复点击图标，这正是"打开多个界面"的体验根源
 * （互斥锁只是兜底，让用户"不想再点"才是治本）。
 *
 * ## 设计说明
 * - 用**不确定进度**（[CircularProgressIndicator] 默认形态）：无法预知确切
 *   耗时，伪造百分比会在卡顿时让用户觉得"进度不动了"而更焦虑
 * - 失败时隐藏进度环、改用错误色：进度环配错误信息会让人误以为还在重试
 */
@Composable
private fun SplashWindowContent(message: String, isError: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().background(scheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = AppInfo.DEFAULT_DISPLAY_NAME,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isError) scheme.error else scheme.primary,
            )
            Spacer(modifier = Modifier.height(18.dp))
            if (!isError) {
                CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(14.dp))
            }
            Text(text = message, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

/**
 * 给窗口内容套一层不透明度（需求 4）。
 *
 * ## 为什么不用 `window.opacity`（AWT 窗口级 alpha）
 * 那是最初的实现，但用户实测**主窗口不生效**。AWT 的窗口不透明度依赖
 * 桌面合成器，在"窗口装饰由系统绘制"的普通窗口上行为不一致：
 * 有的环境抛 UnsupportedOperationException，有的静默忽略。
 *
 * 改用 Compose 的 [androidx.compose.ui.graphics.graphicsLayer] 对内容做
 * alpha 混合：
 *  - 不依赖平台合成器，Windows 与麒麟行为一致
 *  - 与项目其余 UI 走同一条渲染路径，观感统一
 *
 * 代价：系统绘制的标题栏不会被淡化。这是有意的取舍 ——
 * 「内容确实会变淡」比「整窗包括标题栏一起淡、但常常完全不生效」要好。
 */
@Composable
private fun Modifier.windowOpacity(alpha: Float): Modifier {
    val clamped = alpha.coerceIn(SettingsStore.OPACITY_MIN, 1f)
    return this.graphicsLayer { this.alpha = clamped }
}

fun main(args: Array<String>) {
    // 必须在任何 AWT / Skia 字体对象创建之前设置，用于改善 Linux 下的
    // 中文抗锯齿表现（需求 4.1 要求解决字体发虚问题）。
    configureFontRendering()

    // 日志与崩溃记录必须在一切之前装好：
    // 从桌面图标启动时没有控制台，stdout/stderr 的报错用户根本看不到，
    // 出问题时也就无从反馈。尽早初始化才能记下启动阶段的失败。
    AppLog.init()
    AppLog.installCrashHandler()

    val options = parseArgs(args)

    // 单实例守卫：必须在创建任何窗口之前完成。
    // 用户多次点击图标会起多个进程、各自开窗；更严重的是两个进程同时写
    // 同一个 SQLite 文件会有冲突风险。这里用文件锁互斥，
    // 拿不到锁说明已有实例在跑，直接退出而不是再开一个窗口。
    //
    // 允许 --widget 等调试参数绕过？不 —— 调试时也不该有多实例，
    // 否则"小窗与主窗各连一个数据库"的问题会被掩盖。
    val guard = SingleInstanceGuard.tryAcquire()
    if (guard == null) {
        println("[KylinTodo] 检测到已有实例正在运行，本次启动退出。")
        // 用 0 退出码：这不是错误，而是"已有实例"的正常情况。
        // 返回非零会让桌面环境的启动器弹出"启动失败"提示。
        return
    }
    // 注册关闭钩子，确保异常退出时也释放（操作系统本会释放，这里求稳）
    Runtime.getRuntime().addShutdownHook(Thread { guard.release() })

    println("[KylinTodo] 数据目录: ${AppPaths.dataDirectory()}")
    println("[KylinTodo] 中文字体: $selectedChineseFontName")
    options.initialView?.let { println("[KylinTodo] 启动视图: $it") }
    options.initialDate?.let { println("[KylinTodo] 锚定日期: $it") }

    application {
        // ---------- 启动阶段：异步连接数据库（需求 7） ----------
        //
        // 此前 `SqliteTodoRepository` 的构造（JDBC 连接 + 建表 + 列迁移）发生在
        // **窗口出现之前**，用户看到的是几秒空白，以为没启动成功而再点一次图标，
        // 于是开出第二个进程 —— 这是"多个界面"在体验层面的根源。
        //
        // 现在：窗口立刻出现并显示启动画面，数据库连接放到 IO 线程。
        var bootState by remember { mutableStateOf<BootState>(BootState.Loading) }

        LaunchedEffect(Unit) {
            val result = withContext(Dispatchers.IO) {
                runCatching { SqliteTodoRepository(AppPaths.databaseFile()) }
            }
            bootState = result.fold(
                onSuccess = { BootState.Ready(it) },
                onFailure = { BootState.Failed(it.message ?: "未知错误") },
            )
        }

        // 未就绪时只显示启动画面。
        //
        // **必须包在自己的 Window 里**，不能直接渲染在 application {} 中：
        // application {} 提供的是 ApplicationScope，它**不建立 Compose 的
        // 窗口级 CompositionLocal**（LocalFontFamilyResolver / LocalDensity 等）。
        // 直接放 Text 之类的组件会抛
        //   IllegalStateException: CompositionLocal LocalFontFamilyResolver not present
        // 表现为启动即崩、Windows 弹出 "Failed to launch JVM"。
        // （这正是 1.1.1 引入的回归，已由 AppLog 记录的堆栈定位。）
        val booted = bootState as? BootState.Ready
        if (booted == null) {
            Window(
                onCloseRequest = ::exitApplication,
                title = AppInfo.DEFAULT_DISPLAY_NAME,
                icon = AppIcon.painter,
                state = rememberWindowState(
                    size = DpSize(380.dp, 240.dp),
                    position = WindowPosition(androidx.compose.ui.Alignment.Center),
                ),
                resizable = false,
                // 无边框：启动画面只是短暂的过渡，带标题栏会显得突兀
                undecorated = true,
                alwaysOnTop = true,
            ) {
                SplashWindowContent(
                    message = (bootState as? BootState.Failed)
                        ?.let { "启动失败：${it.reason}" }
                        ?: "正在启动…",
                    isError = bootState is BootState.Failed,
                )
            }
            return@application
        }

        // 数据库在整个应用生命周期内保持打开（需求 F-06：自动保存、自动加载）
        val repository = remember(booted) { booted.repository }
        val viewModel = remember {
            // 端到端验证用：插入种子待办后，正常关闭再启动应能自动加载。
            // 支持多条，便于一次造出覆盖各种优先级与标签的样例数据。
            options.seedTodos.forEach { seed ->
                TodoItem.createOrNull(
                    seed.text,
                    seed.date,
                    priority = seed.priority,
                    tags = seed.tags,
                )?.let { repository.insert(it) }
            }
            AppViewModel(
                repository = repository,
                todayProvider = { options.initialDate ?: LocalDate.now() },
                initialViewMode = options.initialView ?: CalendarViewMode.MONTH,
                dataExporter = DesktopDataExporter,
                holidayTransfer = DesktopHolidayTransfer,
            ).also { vm ->
                // 命令行要求时启动即打开桌面小窗（便于验证与日常使用）
                if (options.widget) vm.changeWidgetVisibility(true)
                // 调试用：启动即打开添加弹窗，便于截图检查弹窗布局
                if (options.openAddDialog) vm.openAddTodo()
            }
        }

        // 调试用：重放"翻到未来月份后点击某天"的操作序列并打印状态轨迹。
        // 放在 LaunchedEffect 里执行且只跑一次，避免每次重组都重放。
        if (options.traceJump) {
            LaunchedEffect(Unit) {
                fun log(tag: String) = println(viewModel.debugState(tag))
                log("初始")

                repeat(3) { i ->
                    viewModel.goNext()
                    log("goNext #${i + 1}")
                }

                // 路径 A：单击格子（CalendarCell 的 onClick）
                val inPeriod = viewModel.page.days.first { it.inCurrentPeriod }.date
                log("准备单击(路径A:selectDate) target=$inPeriod")
                viewModel.selectDate(inPeriod)
                log("路径A 结果")

                // 路径 B：点击右上角「+」（openAddTodo）
                viewModel.openAddTodo(inPeriod.plusDays(1))
                log("路径B 结果(openAddTodo)")
                viewModel.dismissAddTodo()

                // 路径 C：双击格子（openDayInfo）
                viewModel.openDayInfo(inPeriod.plusDays(2))
                log("路径C 结果(openDayInfo)")
                viewModel.dismissDayInfo()

                log("全部结束")
            }
        }

        // 窗口关闭时释放数据库连接，确保数据落盘
        DisposableEffect(Unit) {
            onDispose { repository.close() }
        }

        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 820.dp),
            position = WindowPosition(androidx.compose.ui.Alignment.Center),
        )

        // 调试状态栏开关（Ctrl+Shift+D）
        val debugState = rememberDebugOverlayState(initiallyVisible = options.debugOverlay)

        // 偏好设置（皮肤 / 字号 / 启动行为），启动时从系统偏好存储读取。
        // 用 remember 持有，保证整个应用生命周期内是同一份状态。
        val settings = remember { SettingsController(SettingsStore.load()) }

        // 日志信息注入设置界面（需求：日志便于排查问题）
        remember(settings) {
            settings.logSummary = AppLog.describe()
            settings.onOpenLogFolder = {
                runCatching {
                    val dir = AppLog.currentFile()?.parent
                    if (dir == null) {
                        "日志尚未初始化"
                    } else {
                        // Desktop.open 用系统默认程序打开文件夹，
                        // 比"把路径复制给用户让他自己找"友好得多。
                        java.awt.Desktop.getDesktop().open(dir.toFile())
                        "已打开日志文件夹：$dir"
                    }
                }.getOrElse { e ->
                    "无法打开文件夹：${e.message ?: e::class.simpleName}\n" +
                        "可手动前往：${AppLog.currentFile()?.parent}"
                }
            }
            settings.onClearLog = {
                AppLog.clear()
                settings.logSummary = AppLog.describe()
                "日志已清空"
            }
            settings.onReadLogSummary = { AppLog.describe() }
            true
        }

        // 调试用操作日志。仅在打开调试栏时注入 ViewModel，避免生产开销。
        val actionLog = remember { ActionLog() }
        LaunchedEffect(debugState.visible) {
            viewModel.actionLog = if (debugState.visible) actionLog else null
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = settings.appName,
            state = windowState,
            icon = AppIcon.painter,
            // 允许用鼠标拖拽边框缩放。Compose Desktop 默认即为 true，
            // 这里显式写出以免后续误改。
            //
            // 注意：Compose 1.7.3 的 WindowState 没有 minimumSize 属性
            // （javap 确认只有 placement / minimized / position / size），
            // 因此不设最小尺寸。布局本身是全自适应的 —— 日历列用 weight(1f)
            // 吸收多余宽度，侧栏固定 300dp，行高按可用高度除以 6 计算，
            // 窗口拉大拉小都不会出现滚动条或错位。
            resizable = true,
            // 全局快捷键：Ctrl+Shift+D 开关调试状态栏。
            // 用 Preview 阶段拦截，保证任何子组件都不会先消费掉这个组合键。
            onPreviewKeyEvent = { event ->
                handleDebugShortcut(event, debugState)
            },
        ) {
            // 命令行要求时启动即打开设置弹窗。
            // 必须放在窗口的 composable 内容里 —— 外层的 application {} 不是
            // composable 上下文，放在那里 LaunchedEffect 不会执行。
            LaunchedEffect(Unit) {
                if (options.settingsOpen) viewModel.openSettings()
            }

            KylinTodoTheme(
                mode = settings.themeMode,
                fontScale = settings.scaleValue,
                titleFontFamily = settings.titleFontFamily,
                bodyFontFamily = settings.bodyFontFamily,
                backgroundColor = settings.backgroundColor,
            ) {
                // 用 Column 包住：主题的 content 是单个可组合项，
                // 直接并列两个兄弟节点会互相重叠而非上下排列。
                Column(
                    // 主窗口透明度（需求 4）：施加在最外层，让整个界面一起变淡
                    modifier = Modifier.fillMaxSize().windowOpacity(settings.mainOpacity),
                ) {
                    // 调试状态栏：把 anchor / selected / 网格范围等关键状态平铺显示，
                    // 并记录操作序列，供用户复现问题时截图。
                    if (debugState.visible) {
                        CompositionLocalProvider(LocalActionLog provides actionLog) {
                            DebugStatusBar(viewModel = viewModel)
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        CalendarScreen(viewModel = viewModel, appName = settings.appName)
                    }
                }

                // 搜索弹窗（需求：右上角改为搜索按钮）
                if (viewModel.searchVisible) {
                    SearchDialog(
                        query = viewModel.searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        allTags = viewModel.allTags,
                        selectedTags = viewModel.searchTags,
                        onToggleTag = viewModel::toggleSearchTag,
                        selectedPriority = viewModel.searchPriority,
                        onTogglePriority = viewModel::updateSearchPriority,
                        results = viewModel.searchResults,
                        onJumpTo = viewModel::jumpToSearchResult,
                        onToggleTodo = viewModel::toggleCompleted,
                        onDismiss = viewModel::dismissSearch,
                    )
                }

                // 设置弹窗（需求反馈第 5 条）
                if (viewModel.settingsVisible) {
                    SettingsDialog(
                        controller = settings,
                        onExportData = { viewModel.exportAllData() },
                        onExportHolidayTemplate = { viewModel.exportHolidayTemplate() },
                        onImportHolidays = { viewModel.importHolidays() },
                        onClearHolidays = { viewModel.clearImportedHolidays() },
                        holidaySummary = { viewModel.importedHolidaySummary() },
                        onDismiss = viewModel::dismissSettings,
                    )
                }

                // 调试用：在应用自身进程内驱动真实鼠标点击，自动重放用户的
                // 操作序列并核对状态。仅在 --simulate-clicks 时启用。
                if (options.simulateClicks) {
                    JumpSimulator(viewModel = viewModel, javaWindow = window)
                }

                // 日期详情弹窗：双击日历中的某一天弹出，
                // 显示该日已添加的待办，并可继续添加（双击弹窗或点「+」）
                viewModel.dayInfoDate?.let { infoDate ->
                    DayInfoDialog(
                        viewModel = viewModel,
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
                        dayInfo = viewModel.calendarDayOf(targetDate),
                        onDismiss = viewModel::dismissAddTodo,
                        onConfirm = { text, priority, tags ->
                            viewModel.addTodo(text, targetDate, priority, tags)
                        },
                    )
                }
            }
        }

        // ---------- 桌面小窗（需求第 5 条方案 A）----------
        // 必须是主窗口的**兄弟**窗口，不能嵌套在 Window 的内容里。
        if (viewModel.widgetVisible) {
            DesktopWidgetWindow(viewModel = viewModel, settings = settings)
        }
    }
}

/**
 * 调试用：在**应用自身的 JVM 内**用 AWT Robot 驱动真实鼠标点击，自动重放
 * 「翻页到未来月份 → 逐格点击」的操作序列。
 *
 * ## 为什么要在应用内发点击
 * 从独立进程（PowerShell / jshell）发送的合成点击会因焦点竞争而丢失 ——
 * 实测 .NET mouseEvent 与外部 Robot 都无法可靠命中 Compose 窗口。
 * 在应用内创建 Robot 时窗口本身就是前台，可稳定命中。
 *
 * ## 为什么用探针记录的坐标
 * 探针通过 `onGloballyPositioned` 记录每个构件与格子的**实际**窗口坐标，
 * 因此点击的不是"算出来的"位置，而是"界面真实画出来的"位置。这样若仍
 * 出现异常，就排除了坐标错位这一类原因。
 */
@Composable
private fun JumpSimulator(viewModel: AppViewModel, javaWindow: java.awt.Window?) {
    LaunchedEffect(Unit) {
        fun log(msg: String) = println("[SIM] $msg")

        // 等界面完全渲染，保证探针已记录坐标
        delay(3500)

        // 关键：必须让窗口成为前台并获得焦点，否则 Robot 的合成点击会被
        // 系统丢弃 —— 表现为"点击完全无效果"，而不是点错位置。
        // 上一次实测中三次翻页按钮点击均未生效，正是这个原因。
        runCatching {
            javaWindow?.isAlwaysOnTop = true
            javaWindow?.toFront()
            javaWindow?.requestFocus()
        }
        delay(1200)
        log(
            "开始，窗口屏幕位置=${javaWindow?.locationOnScreen} " +
                "可见=${javaWindow?.isVisible} 激活=${javaWindow?.isActive}",
        )

        val robot = runCatching { java.awt.Robot() }.getOrNull()
        if (robot == null) {
            log("无法创建 Robot，终止")
            return@LaunchedEffect
        }
        robot.autoDelay = 80

        fun clickKey(key: String): Boolean {
            val b = ClickProbeSupport.boundsOf(key)
            if (b == null) {
                log("找不到构件 $key 的坐标")
                return false
            }
            val origin = javaWindow?.locationOnScreen ?: java.awt.Point(0, 0)
            val localX = (b[0] + b[2]) / 2f
            val localY = (b[1] + b[3]) / 2f
            val cx = (origin.x + localX).toInt()
            val cy = (origin.y + localY).toInt()
            log("点击 $key 窗口内(${localX.toInt()},${localY.toInt()}) 屏幕($cx,$cy)")

            ClickProbeSupport.lastClickedTag = null
            robot.mouseMove(cx, cy)
            robot.delay(250)
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
            robot.delay(90)
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
            robot.delay(600)

            // 用探针的命中记录判断点击是否真的送达应用：
            // 若为 null 说明事件根本没到 Compose，属于输入投递问题而非应用 bug。
            val hit = ClickProbeSupport.lastClickedTag
            log("    点击送达=${hit ?: "否（事件未到达应用）"}")
            return true
        }

        // 先验证输入投递是否可用 —— 若鼠标点击不生效，后续结果无意义
        log("窗口激活状态=${javaWindow?.isActive}，开始测试输入投递")

        // 1. 向后翻 3 次（起点由 --date 指定，建议 2026-10-01）
        repeat(3) { i ->
            clickKey("btn:next")
            log("翻页 #${i + 1} 后 anchor=${viewModel.anchorDate} 标题='${viewModel.pageTitle}'")
        }

        val expectedMonth = java.time.YearMonth.from(viewModel.anchorDate)
        log("目标月份 = $expectedMonth")

        // 2. 逐格点击"当月内"的日期，每次核对锚点月份与选中日
        val targets = viewModel.page.days.filter { it.inCurrentPeriod }.map { it.date }
        log("将逐格点击 ${targets.size} 个当月日期")

        var failures = 0
        targets.forEach { date ->
            clickKey("cell:${date.monthValue}/${date.dayOfMonth}")
            val after = java.time.YearMonth.from(viewModel.anchorDate)
            val selected = viewModel.selectedDate
            if (after != expectedMonth || selected != date) {
                failures++
                log("!! 异常：点击 $date 后 anchor=${viewModel.anchorDate} selected=$selected（期望 $expectedMonth / $date）")
            } else {
                log("ok 点击 $date -> anchor=$after selected=$selected")
            }
        }

        log("完成：共点击 ${targets.size} 次，异常 $failures 次")
    }
}

/**
 * 无边框桌面小窗。
 *
 * ## 为什么用这种方式而不是 UKUI 面板插件
 * 银河麒麟 V10 使用 UKUI 桌面，它没有类似 Android AppWidget 的通用第三方
 * 小组件接口。真正嵌入面板需要单独编写依赖麒麟专有 API 的 applet（通常为
 * C/Python + GTK），属于另一个项目且无法在 Windows 上验证。
 * 这里采用「无边框 + 置顶 + 可拖动」的常驻小窗，跨桌面环境通用。
 *
 * ## 拖动实现
 * 无边框窗口没有系统标题栏，拖动由 [windowDrag] 把指针位移累加到窗口位置。
 * 指针事件是像素、而 WindowPosition 是 dp，因此需按 density 换算 ——
 * 否则高分屏上拖动速度会明显偏离鼠标。
 */
@Composable
private fun DesktopWidgetWindow(viewModel: AppViewModel, settings: SettingsController) {
    val density = LocalDensity.current.density

    val widgetState = rememberWindowState(
        size = DpSize(300.dp, 400.dp),
        position = WindowPosition(androidx.compose.ui.Alignment.TopEnd),
    )

    Window(
        onCloseRequest = { viewModel.changeWidgetVisibility(false) },
        state = widgetState,
        title = "${settings.appName} 小窗",
        icon = AppIcon.painter,
        // 无系统边框：小窗自带拖动把手与关闭按钮
        undecorated = true,
        // 置顶由设置驱动（需求 5），可在设置里或点小窗上的星形按钮切换
        alwaysOnTop = settings.widgetPinned,
        // 小窗尺寸固定，避免误拖边框改变布局
        resizable = false,
    ) {
        KylinTodoTheme(
            mode = settings.themeMode,
            fontScale = settings.scaleValue,
            titleFontFamily = settings.titleFontFamily,
            bodyFontFamily = settings.bodyFontFamily,
            backgroundColor = settings.backgroundColor,
        ) {
            DesktopWidgetScreen(
                day = viewModel.selectedCalendarDay,
                todos = viewModel.selectedDateTodos,
                onToggle = viewModel::toggleCompleted,
                onDelete = viewModel::deleteTodo,
                onAdd = { viewModel.openAddTodo(viewModel.selectedDate) },
                onOpenMain = { viewModel.changeWidgetVisibility(false) },
                onClose = { viewModel.changeWidgetVisibility(false) },
                // 置顶开关（需求 5）：改设置即改窗口属性，无需重启
                pinned = settings.widgetPinned,
                onTogglePin = { settings.update(pinned = !settings.widgetPinned) },
                // 小窗透明度（需求 4）
                modifier = Modifier.windowOpacity(settings.widgetOpacity),
                onDrag = { dx, dy ->
                    widgetState.position = nextWindowPosition(
                        current = widgetState.position,
                        deltaXPx = dx,
                        deltaYPx = dy,
                        density = density,
                    )
                },
            )

            // 小窗内同样可以使用添加弹窗（与主窗口共用同一份状态）
            viewModel.addTodoTargetDate?.let { targetDate ->
                AddTodoDialog(
                    date = targetDate,
                    dayInfo = viewModel.calendarDayOf(targetDate),
                    onDismiss = viewModel::dismissAddTodo,
                    onConfirm = { text, priority, tags ->
                        viewModel.addTodo(text, targetDate, priority, tags)
                    },
                )
            }
        }
    }
}

/**
 * 命令行种子待办。
 *
 * 独立数据类而非元组：现在要带优先级与标签，四元组用起来极易把顺序写错。
 */
private data class SeedTodo(
    val text: String,
    val date: LocalDate,
    val priority: TodoPriority,
    val tags: Set<String>,
)

/** 命令行选项。 */
private data class LaunchOptions(
    val initialView: CalendarViewMode? = null,
    val initialDate: LocalDate? = null,
    /**
     * 调试用：启动时插入的待办，便于验证持久化、自动加载，
     * 以及优先级与标签在界面上的呈现。
     */
    val seedTodos: List<SeedTodo> = emptyList(),
    /** 启动时即打开桌面小窗 */
    val widget: Boolean = false,
    /** 调试用：启动时即打开添加待办弹窗，便于人工/截图验证弹窗布局 */
    val openAddDialog: Boolean = false,
    /**
     * 调试用：启动后自动重放「翻到未来月份再点击某天」的操作序列，
     * 把每一步状态打到日志。用于定位"切换到 2027 年后点击日期跳回"这类
     * 只在真机出现的问题。
     */
    val traceJump: Boolean = false,
    /**
     * 调试用：在应用自身进程内用 AWT Robot 驱动真实鼠标，自动重放
     * 「翻页到未来月份 → 逐格点击」并核对每次点击后的锚点月份。
     * 需与 -Pprobe 一起使用（探针提供构件的真实坐标）。
     */
    val simulateClicks: Boolean = false,
    /** 启动时即显示调试状态栏（等价于启动后按 Ctrl+Shift+D） */
    val debugOverlay: Boolean = false,
    /** 启动时即打开设置弹窗，便于截图与人工核验 */
    val settingsOpen: Boolean = false,
)

/**
 * 解析命令行参数。
 *
 * 非法值直接忽略并回落默认行为，避免开发期笔误导致应用无法启动。
 */
private fun parseArgs(args: Array<String>): LaunchOptions {
    var view: CalendarViewMode? = null
    var date: LocalDate? = null
    val seeds = mutableListOf<SeedTodo>()
    var widget = false
    var openAddDialog = false
    var traceJump = false
    var simulateClicks = false
    var debugOverlay = false
    var settingsOpen = false

    args.forEach { arg ->
        when {
            // 无值开关
            arg == "--widget" -> widget = true
            arg == "--add" -> openAddDialog = true
            arg == "--trace-jump" -> traceJump = true
            arg == "--simulate-clicks" -> simulateClicks = true
            arg == "--debug" -> debugOverlay = true
            arg == "--settings" -> settingsOpen = true

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

            // --seed=文本@YYYY-MM-DD          （日期可省略，默认锚定日期）
            // --seed=文本@YYYY-MM-DD@H        （再加优先级 H/M/L/N）
            // --seed=文本@YYYY-MM-DD@H@工作,紧急（再加逗号分隔的标签）
            //
            // 支持优先级与标签是为了能做端到端视觉验证：只有真正把四种优先级
            // 和带标签的待办插进库里，才能确认格子与列表的着色/标签渲染正确。
            arg.startsWith("--seed=") -> {
                val parts = arg.removePrefix("--seed=").split('@')
                val text = parts.getOrNull(0).orEmpty()
                val d = parts.getOrNull(1)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val p = parts.getOrNull(2)
                    ?.let { token ->
                        TodoPriority.entries.firstOrNull {
                            it.name.startsWith(token.trim().uppercase())
                        }
                    }
                    ?: TodoPriority.NONE
                val tags = parts.getOrNull(3)
                    ?.let { TodoItem.normalizeTags(listOf(it)) }
                    ?: emptySet()

                seeds += SeedTodo(text, d ?: date ?: LocalDate.now(), p, tags)
            }
        }
    }
    // 用**具名参数**构造：LaunchOptions 的每个字段都有默认值，
    // 位置参数一旦漏传就会静默取默认值、编译器不报错。
    // 此前 settingsOpen 就是这样被漏掉的 —— 分支明明命中、赋值也执行了，
    // 却因为构造时没传而始终是 false，排查了很久。
    // 具名写法让"漏传"变成显而易见的差异。
    return LaunchOptions(
        initialView = view,
        initialDate = date,
        seedTodos = seeds,
        widget = widget,
        openAddDialog = openAddDialog,
        traceJump = traceJump,
        simulateClicks = simulateClicks,
        debugOverlay = debugOverlay,
        settingsOpen = settingsOpen,
    )
}
